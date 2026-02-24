package com.roadrats.demo.service.testtools;

import com.roadrats.demo.config.TestToolsConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.sql.*;
import java.util.*;

@Service
public class OrderActionService {

    private static final Logger logger = LoggerFactory.getLogger(OrderActionService.class);

    private final TestToolsConfig config;

    public OrderActionService(TestToolsConfig config) {
        this.config = config;
    }

    /**
     * Quick lookup of pick_container + pick_detail for an order on AAD.
     * Resolves order from container_id, order_number, or oms_order_number.
     */
    public Map<String, Object> resolveOrder(String searchType, String searchValue, String warehouseId) {
        Map<String, Object> result = new LinkedHashMap<>();
        String jdbcUrl = config.buildAadJdbcUrl();
        logger.info("resolveOrder: type={}, value={}, wh={}, url={}", searchType, searchValue, warehouseId, jdbcUrl);

        try {
            Class.forName(config.getDriverClassName());
        } catch (ClassNotFoundException e) {
            result.put("success", false);
            result.put("error", "JDBC driver not found: " + e.getMessage());
            return result;
        }

        try (Connection conn = DriverManager.getConnection(jdbcUrl)) {
            String orderNumber = null;
            String containerId = null;
            String resolvedWhId = warehouseId;

            // Step 1: Resolve identifiers
            if ("oms".equals(searchType)) {
                String sql = "SELECT TOP 1 order_number, wh_id FROM t_order_detail WHERE oms_order_number = ?";
                if (resolvedWhId != null && !resolvedWhId.isEmpty()) {
                    sql += " AND wh_id = ?";
                }
                try (PreparedStatement ps = conn.prepareStatement(sql)) {
                    ps.setString(1, searchValue);
                    if (resolvedWhId != null && !resolvedWhId.isEmpty()) {
                        ps.setString(2, resolvedWhId);
                    }
                    try (ResultSet rs = ps.executeQuery()) {
                        if (rs.next()) {
                            orderNumber = rs.getString("order_number");
                            resolvedWhId = rs.getString("wh_id");
                        }
                    }
                }
            } else if ("container".equals(searchType)) {
                orderNumber = null;
                containerId = searchValue;
                String sql = "SELECT TOP 1 order_number, wh_id FROM t_pick_container WHERE container_id = ?";
                if (resolvedWhId != null && !resolvedWhId.isEmpty()) {
                    sql += " AND wh_id = ?";
                }
                try (PreparedStatement ps = conn.prepareStatement(sql)) {
                    ps.setString(1, searchValue);
                    if (resolvedWhId != null && !resolvedWhId.isEmpty()) {
                        ps.setString(2, resolvedWhId);
                    }
                    try (ResultSet rs = ps.executeQuery()) {
                        if (rs.next()) {
                            orderNumber = rs.getString("order_number");
                            resolvedWhId = rs.getString("wh_id");
                        }
                    }
                }
            } else {
                orderNumber = searchValue;
            }

            if (orderNumber == null && containerId == null) {
                result.put("success", false);
                result.put("error", "Could not resolve order from " + searchType + " = " + searchValue);
                return result;
            }

            // Step 2: Get container_id if we only have order_number
            if (containerId == null && orderNumber != null) {
                String sql = "SELECT TOP 1 container_id FROM t_pick_container WHERE order_number = ? AND wh_id = ?";
                try (PreparedStatement ps = conn.prepareStatement(sql)) {
                    ps.setString(1, orderNumber);
                    ps.setString(2, resolvedWhId);
                    try (ResultSet rs = ps.executeQuery()) {
                        if (rs.next()) containerId = rs.getString("container_id");
                    }
                }
            }

            result.put("success", true);
            result.put("orderNumber", orderNumber);
            result.put("containerId", containerId);
            result.put("warehouseId", resolvedWhId);
            result.put("connection", config.getAadServer() + " / " + config.getAadDatabase());

            // Step 3: Fetch pick_container rows
            if (orderNumber != null) {
                List<Map<String, Object>> pickContainers = queryTable(conn,
                    "SELECT * FROM t_pick_container WHERE wh_id = ? AND order_number = ?",
                    resolvedWhId, orderNumber);
                result.put("pickContainers", pickContainers);
            }

            // Step 4: Fetch pick_detail rows
            if (orderNumber != null) {
                List<Map<String, Object>> pickDetails = queryTable(conn,
                    "SELECT * FROM t_pick_detail WHERE wh_id = ? AND order_number = ?",
                    resolvedWhId, orderNumber);
                result.put("pickDetails", pickDetails);
            }

            // Step 5: Fetch t_order info
            if (orderNumber != null) {
                List<Map<String, Object>> orders = queryTable(conn,
                    "SELECT * FROM t_order WHERE wh_id = ? AND order_number = ?",
                    resolvedWhId, orderNumber);
                result.put("orders", orders);
            }

            // Step 6: Check existing setup (t_hu_master, t_stored_item)
            if (containerId != null) {
                List<Map<String, Object>> huMaster = queryTable(conn,
                    "SELECT * FROM t_hu_master WHERE wh_id = ? AND hu_id = ?",
                    resolvedWhId, containerId);
                result.put("huMaster", huMaster);

                List<Map<String, Object>> storedItems = queryTable(conn,
                    "SELECT * FROM t_stored_item WHERE wh_id = ? AND hu_id = ?",
                    resolvedWhId, containerId);
                result.put("storedItems", storedItems);
            }

        } catch (SQLException e) {
            logger.error("Error resolving order", e);
            result.put("success", false);
            result.put("error", "Database error: " + e.getMessage());
        }

        return result;
    }

    /**
     * Insert t_hu_master and t_stored_item records to set up an order for shipping/floor deny/short ship.
     * @param setupType "normal", "short_ship", or "floor_deny"
     * @param containerId if non-null, only set up this specific container
     * @param itemOverride if non-null, only set up this specific item
     * @param quantityOverride if non-null, use this qty instead of planned_quantity
     */
    public Map<String, Object> setupOrderData(String warehouseId, String orderNumber,
                                               String setupType, String containerId,
                                               String itemOverride, Integer quantityOverride) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("warehouseId", warehouseId);
        result.put("orderNumber", orderNumber);
        result.put("setupType", setupType);
        if (containerId != null && !containerId.isEmpty()) result.put("containerId", containerId);
        result.put("executedAt", new java.util.Date().toString());

        String jdbcUrl = config.buildAadJdbcUrl();
        logger.info("setupOrderData: wh={}, order={}, type={}, container={}, itemOverride={}, qtyOverride={}",
            warehouseId, orderNumber, setupType, containerId, itemOverride, quantityOverride);

        try {
            Class.forName(config.getDriverClassName());
        } catch (ClassNotFoundException e) {
            result.put("success", false);
            result.put("error", "JDBC driver not found: " + e.getMessage());
            return result;
        }

        try (Connection conn = DriverManager.getConnection(jdbcUrl)) {
            conn.setAutoCommit(false);

            // Get pick_container data (filtered to specific container if provided)
            List<Map<String, Object>> containers;
            if (containerId != null && !containerId.isEmpty()) {
                containers = queryTable(conn,
                    "SELECT container_id, order_number, wh_id, container_type FROM t_pick_container WHERE wh_id = ? AND order_number = ? AND container_id = ?",
                    warehouseId, orderNumber, containerId);
            } else {
                containers = queryTable(conn,
                    "SELECT container_id, order_number, wh_id, container_type FROM t_pick_container WHERE wh_id = ? AND order_number = ?",
                    warehouseId, orderNumber);
            }

            if (containers.isEmpty()) {
                result.put("success", false);
                result.put("error", "No pick_container rows found" + (containerId != null ? " for container " + containerId : ""));
                return result;
            }

            // Get pick_detail data (filtered to specific container if provided)
            List<Map<String, Object>> details;
            if (containerId != null && !containerId.isEmpty()) {
                details = queryTable(conn,
                    "SELECT pick_id, item_number, planned_quantity, container_id, order_number, wh_id FROM t_pick_detail WHERE wh_id = ? AND order_number = ? AND container_id = ?",
                    warehouseId, orderNumber, containerId);
            } else {
                details = queryTable(conn,
                    "SELECT pick_id, item_number, planned_quantity, container_id, order_number, wh_id FROM t_pick_detail WHERE wh_id = ? AND order_number = ?",
                    warehouseId, orderNumber);
            }

            int huInserted = 0;
            int huSkipped = 0;
            int stoInserted = 0;
            int stoSkipped = 0;
            List<String> debugLog = new ArrayList<>();

            // Insert t_hu_master for each container (if not exists)
            for (Map<String, Object> pkc : containers) {
                String cid = String.valueOf(pkc.get("container_id"));
                String cType = pkc.get("container_type") != null ? String.valueOf(pkc.get("container_type")) : "BOX04";

                String checkSql = "SELECT 1 FROM t_hu_master WHERE wh_id = ? AND hu_id = ? AND type = 'SO' AND control_number = ?";
                boolean exists = false;
                try (PreparedStatement ps = conn.prepareStatement(checkSql)) {
                    ps.setString(1, warehouseId);
                    ps.setString(2, cid);
                    ps.setString(3, orderNumber);
                    try (ResultSet rs = ps.executeQuery()) {
                        exists = rs.next();
                    }
                }

                if (exists) {
                    huSkipped++;
                    debugLog.add("HUM skip (exists): hu_id=" + cid);
                    logger.info("t_hu_master already exists for wh={}, hu_id={}, skipping", warehouseId, cid);
                } else {
                    String insertSql = "INSERT INTO t_hu_master (hu_id, type, control_number, location_id, subtype, status, fifo_date, wh_id, container_type) " +
                        "VALUES (?, 'SO', ?, 'PACKING1', 'T', 'A', CONVERT(DATE, GETDATE()), ?, ?)";
                    try (PreparedStatement ps = conn.prepareStatement(insertSql)) {
                        ps.setString(1, cid);
                        ps.setString(2, orderNumber);
                        ps.setString(3, warehouseId);
                        ps.setString(4, cType);
                        int rows = ps.executeUpdate();
                        huInserted += rows;
                        debugLog.add("HUM insert: hu_id=" + cid + ", ctype=" + cType + ", rows=" + rows);
                        logger.info("t_hu_master inserted for hu_id={}, container_type={}, rows={}", cid, cType, rows);
                    }
                }
            }

            // Insert t_stored_item for each pick_detail (if not exists)
            for (Map<String, Object> pkd : details) {
                String pickId = String.valueOf(pkd.get("pick_id"));
                String itemNumber = String.valueOf(pkd.get("item_number"));

                if (itemOverride != null && !itemOverride.isEmpty() && !itemNumber.equals(itemOverride)) {
                    continue;
                }

                Object qtyObj = pkd.get("planned_quantity");
                int plannedQty = qtyObj instanceof Number ? ((Number) qtyObj).intValue() : 0;
                int qty;
                if (quantityOverride != null) {
                    qty = quantityOverride;
                } else if ("short_ship".equals(setupType) && plannedQty > 1) {
                    qty = plannedQty - 1;
                } else if ("floor_deny".equals(setupType)) {
                    qty = 0;
                } else {
                    qty = plannedQty;
                }
                String cid = String.valueOf(pkd.get("container_id"));

                String checkSql = "SELECT 1 FROM t_stored_item WHERE type = ? AND wh_id = ? AND hu_id = ?";
                boolean exists = false;
                try (PreparedStatement ps = conn.prepareStatement(checkSql)) {
                    ps.setString(1, pickId);
                    ps.setString(2, warehouseId);
                    ps.setString(3, cid);
                    try (ResultSet rs = ps.executeQuery()) {
                        exists = rs.next();
                    }
                }

                if (exists) {
                    stoSkipped++;
                    debugLog.add("STO skip (exists): pick_id=" + pickId + ", item=" + itemNumber);
                    logger.info("t_stored_item already exists for pick_id={}, skipping", pickId);
                } else {
                    String insertSql = "INSERT INTO t_stored_item (item_number, actual_qty, status, wh_id, location_id, fifo_date, type, hu_id, lot_number) " +
                        "VALUES (?, ?, 'A', ?, 'PACKING1', CONVERT(DATE, GETDATE()), ?, ?, '1')";
                    try (PreparedStatement ps = conn.prepareStatement(insertSql)) {
                        ps.setString(1, itemNumber);
                        ps.setInt(2, qty);
                        ps.setString(3, warehouseId);
                        ps.setString(4, pickId);
                        ps.setString(5, cid);
                        int rows = ps.executeUpdate();
                        stoInserted += rows;
                        debugLog.add("STO insert: pick_id=" + pickId + ", item=" + itemNumber + ", qty=" + qty + " (planned=" + plannedQty + "), rows=" + rows);
                        logger.info("t_stored_item inserted: pick_id={}, item={}, actual_qty={} (planned={}), hu_id={}, rows={}",
                            pickId, itemNumber, qty, plannedQty, cid, rows);
                    }
                }
            }

            conn.commit();
            logger.info("Setup committed: {} HUM inserted, {} HUM skipped, {} STO inserted, {} STO skipped",
                huInserted, huSkipped, stoInserted, stoSkipped);

            // Query back the full state for this order
            String verifyCid = (containerId != null && !containerId.isEmpty()) ? containerId : "";
            List<Map<String, Object>> verifyHum = queryTable(conn,
                "SELECT TOP 100 * FROM t_hu_master WHERE wh_id = ? AND (hu_id = ? OR control_number = ?)",
                warehouseId, verifyCid, orderNumber);
            List<Map<String, Object>> verifySto = queryTable(conn,
                "SELECT TOP 100 * FROM t_stored_item WHERE wh_id = ? AND hu_id IN " +
                "(SELECT hu_id FROM t_hu_master WHERE wh_id = ? AND (hu_id = ? OR control_number = ?))",
                warehouseId, warehouseId, verifyCid, orderNumber);
            debugLog.add("POST-SETUP: " + verifyHum.size() + " HUM rows, " + verifySto.size() + " STO rows");

            String typeLabel = "normal".equals(setupType) ? "Normal" : "short_ship".equals(setupType) ? "Short Ship" : "Floor Deny";
            result.put("success", true);
            result.put("message", String.format("%s setup complete: %d HU Master rows, %d Stored Item rows inserted (%d skipped existing)",
                typeLabel, huInserted, stoInserted, huSkipped + stoSkipped));
            result.put("huMasterInserted", huInserted);
            result.put("huMasterSkipped", huSkipped);
            result.put("storedItemInserted", stoInserted);
            result.put("storedItemSkipped", stoSkipped);
            result.put("containersProcessed", containers.size());
            result.put("detailsProcessed", details.size());
            result.put("debugLog", debugLog);
            result.put("verifyHuMaster", verifyHum);
            result.put("verifyStoredItem", verifySto);
            if (quantityOverride != null) result.put("quantityUsed", quantityOverride);
            if (itemOverride != null && !itemOverride.isEmpty()) result.put("itemFiltered", itemOverride);

        } catch (SQLException e) {
            logger.error("Error setting up order data wh={}, order={}", warehouseId, orderNumber, e);
            result.put("success", false);
            result.put("error", "Database error: " + e.getMessage());
        }

        return result;
    }

    /**
     * Send fulfillment status event for a container (Wizmo events).
     * Calls usp_pick_container_fulfillment_status_update with the given status code.
     */
    public Map<String, Object> sendFulfillmentEvent(String warehouseId, String containerId, String statusCode) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("warehouseId", warehouseId);
        result.put("containerId", containerId);
        result.put("statusCode", statusCode);
        result.put("executedAt", new java.util.Date().toString());

        String jdbcUrl = config.buildAadJdbcUrl();
        logger.info("sendFulfillmentEvent: wh={}, container={}, status={}", warehouseId, containerId, statusCode);

        try {
            Class.forName(config.getDriverClassName());
        } catch (ClassNotFoundException e) {
            result.put("success", false);
            result.put("error", "JDBC driver not found: " + e.getMessage());
            return result;
        }

        try (Connection conn = DriverManager.getConnection(jdbcUrl)) {
            // Build and execute the fulfillment status update via dynamic SQL
            // since we need to declare a table variable and call the proc
            String sql =
                "DECLARE @v_tvContainers dbo.udtt_container_status_update; " +
                "DECLARE @v_vchMsg dbo.uddt_output_msg; " +
                "INSERT INTO @v_tvContainers(container_id, wh_id, fulfillment_status, fulfillment_status_update_date, profile_name) " +
                "SELECT TOP 1 pkc.container_id, pkc.wh_id, ?, GETDATE(), NULL " +
                "FROM dbo.t_pick_container pkc WHERE pkc.wh_id = ? AND pkc.container_id = ?; " +
                "EXEC dbo.usp_pick_container_fulfillment_status_update " +
                "@in_vchCaller = 'roadrats-test-tools', " +
                "@in_tvContainerStatus = @v_tvContainers, " +
                "@in_nLogLevel = 3, " +
                "@out_vchMessage = @v_vchMsg OUTPUT; " +
                "SELECT @v_vchMsg AS outputMessage;";

            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, statusCode);
                ps.setString(2, warehouseId);
                ps.setString(3, containerId);
                ps.setQueryTimeout(config.getConnectionTimeout());

                boolean hasResults = ps.execute();
                String outputMessage = null;

                // Walk through result sets to find the output message
                while (true) {
                    if (hasResults) {
                        try (ResultSet rs = ps.getResultSet()) {
                            if (rs.next()) {
                                try { outputMessage = rs.getString("outputMessage"); } catch (Exception ignored) {}
                            }
                        }
                    }
                    if (!ps.getMoreResults() && ps.getUpdateCount() == -1) break;
                    hasResults = ps.getMoreResults();
                }

                result.put("success", true);
                result.put("message", "Fulfillment event " + statusCode + " sent for container " + containerId);
                if (outputMessage != null) result.put("outputMessage", outputMessage);
            }

        } catch (SQLException e) {
            logger.error("Error sending fulfillment event wh={}, container={}, status={}", warehouseId, containerId, statusCode, e);
            result.put("success", false);
            result.put("error", "Database error: " + e.getMessage());
        }

        return result;
    }

    private List<Map<String, Object>> queryTable(Connection conn, String sql, String... params) throws SQLException {
        List<Map<String, Object>> rows = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            for (int i = 0; i < params.length; i++) {
                ps.setString(i + 1, params[i]);
            }
            try (ResultSet rs = ps.executeQuery()) {
                ResultSetMetaData meta = rs.getMetaData();
                int colCount = meta.getColumnCount();
                while (rs.next()) {
                    Map<String, Object> row = new LinkedHashMap<>();
                    for (int i = 1; i <= colCount; i++) {
                        row.put(meta.getColumnLabel(i), rs.getObject(i));
                    }
                    rows.add(row);
                }
            }
        }
        return rows;
    }
}
