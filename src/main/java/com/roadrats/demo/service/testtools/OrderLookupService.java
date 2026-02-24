package com.roadrats.demo.service.testtools;

import com.roadrats.demo.config.TestToolsConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.sql.*;
import java.util.*;

@Service
public class OrderLookupService {

    private static final Logger logger = LoggerFactory.getLogger(OrderLookupService.class);

    private final TestToolsConfig config;

    public OrderLookupService(TestToolsConfig config) {
        this.config = config;
    }

    /**
     * @param stack "aad", "io", or "both"
     */
    @SuppressWarnings("unchecked")
    public Map<String, Object> lookupOrder(String searchType, String searchValue, String warehouseId, String stack) {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("searchType", searchType);
        response.put("searchValue", searchValue);
        response.put("stack", stack);
        response.put("queriedAt", new java.util.Date().toString());

        try {
            Class.forName(config.getDriverClassName());
        } catch (ClassNotFoundException e) {
            response.put("error", "JDBC driver not found: " + e.getMessage());
            return response;
        }

        boolean queryAad = "aad".equals(stack) || "both".equals(stack);
        boolean queryIo = "io".equals(stack) || "both".equals(stack);

        // Include connection details in response for verification
        if (queryAad) {
            response.put("aadConnection", config.getAadServer() + " / " + config.getAadDatabase());
            logger.info("AAD JDBC URL: {}", config.buildAadJdbcUrl());
        }
        if (queryIo) {
            response.put("ioConnection", config.getIoServer() + " / " + config.getIoDatabase());
            logger.info("IO JDBC URL: {}", config.buildIoJdbcUrl());
        }

        // Resolve identifiers on the primary stack
        String resolveUrl = queryAad ? config.buildAadJdbcUrl() : config.buildIoJdbcUrl();
        String orderNumber = null;
        String containerId = null;
        String resolvedWhId = warehouseId;

        try (Connection conn = DriverManager.getConnection(resolveUrl)) {
            conn.setTransactionIsolation(Connection.TRANSACTION_READ_UNCOMMITTED);

            switch (searchType) {
                case "oms":
                    Map<String, Object> omsResult = executeQuery(conn,
                        "SELECT TOP 1 order_number, wh_id FROM t_order_detail WHERE oms_order_number = ?",
                        searchValue);
                    List<Map<String, Object>> omsRows = (List<Map<String, Object>>) omsResult.get("rows");
                    if (!omsRows.isEmpty()) {
                        orderNumber = String.valueOf(omsRows.get(0).get("order_number"));
                        resolvedWhId = String.valueOf(omsRows.get(0).get("wh_id"));
                    }
                    break;

                case "container":
                    containerId = searchValue;
                    Map<String, Object> cResult = executeQuery(conn,
                        "SELECT TOP 1 order_number FROM t_pick_container WHERE wh_id = ? AND container_id = ?",
                        warehouseId, searchValue);
                    List<Map<String, Object>> cRows = (List<Map<String, Object>>) cResult.get("rows");
                    if (!cRows.isEmpty()) {
                        orderNumber = String.valueOf(cRows.get(0).get("order_number"));
                    }
                    break;

                case "order":
                default:
                    orderNumber = searchValue;
                    Map<String, Object> oResult = executeQuery(conn,
                        "SELECT TOP 1 container_id FROM t_pick_container WHERE wh_id = ? AND order_number = ?",
                        warehouseId, searchValue);
                    List<Map<String, Object>> oRows = (List<Map<String, Object>>) oResult.get("rows");
                    if (!oRows.isEmpty()) {
                        containerId = String.valueOf(oRows.get(0).get("container_id"));
                    }
                    break;
            }
        } catch (SQLException e) {
            response.put("error", "Failed to resolve identifiers: " + e.getMessage());
            return response;
        }

        response.put("warehouseId", resolvedWhId);
        response.put("orderNumber", orderNumber);
        response.put("containerId", containerId);

        if (orderNumber == null && containerId == null) {
            response.put("error", "Could not resolve order/container from search. No matching records found.");
            return response;
        }

        logger.info("Resolved: wh={}, order={}, container={}, stack={}", resolvedWhId, orderNumber, containerId, stack);

        List<Map<String, Object>> tables = new ArrayList<>();

        if (queryAad) {
            try (Connection conn = DriverManager.getConnection(config.buildAadJdbcUrl())) {
                conn.setTransactionIsolation(Connection.TRANSACTION_READ_UNCOMMITTED);
                tables.addAll(buildAadTables(conn, orderNumber, containerId, resolvedWhId));
            } catch (SQLException e) {
                logger.error("AAD connection failed", e);
                tables.add(errorTable("aad_connection", "AAD Connection Error", "AAD", "Connection",
                    "Failed to connect to " + config.getAadServer() + ": " + e.getMessage()));
            }
        }

        if (queryIo) {
            try (Connection conn = DriverManager.getConnection(config.buildIoJdbcUrl())) {
                conn.setTransactionIsolation(Connection.TRANSACTION_READ_UNCOMMITTED);
                tables.addAll(buildIoTables(conn, orderNumber, containerId, resolvedWhId));
            } catch (SQLException e) {
                logger.error("IO connection failed", e);
                tables.add(errorTable("io_connection", "IO Connection Error", "IO", "Connection",
                    "Failed to connect to " + config.getIoServer() + ": " + e.getMessage()));
            }
        }

        response.put("tables", tables);
        logger.info("Order lookup complete: {} tables returned for stack={}", tables.size(), stack);
        return response;
    }

    // ======================== AAD Stack (WMSSQL-TEST / AAD) ========================

    private List<Map<String, Object>> buildAadTables(Connection conn, String orderNumber, String containerId, String whId) {
        List<Map<String, Object>> tables = new ArrayList<>();

        if (orderNumber != null && whId != null) {
            tables.add(namedQuery(conn, "aad_pick_container", "Pick Container", "AAD", "Pick & Container",
                "SELECT TOP 100 * FROM t_pick_container WHERE wh_id = ? AND order_number = ?", whId, orderNumber));

            tables.add(namedQuery(conn, "aad_pick_detail", "Pick Detail", "AAD", "Pick & Container",
                "SELECT TOP 100 * FROM t_pick_detail WHERE wh_id = ? AND order_number = ?", whId, orderNumber));

            tables.add(namedQuery(conn, "aad_al_host_order_master", "Import Order Master", "AAD", "Import",
                "SELECT TOP 100 * FROM t_al_host_order_master WHERE wh_id = ? AND order_number = ?", whId, orderNumber));

            tables.add(namedQuery(conn, "aad_al_host_order_detail", "Import Order Detail", "AAD", "Import",
                "SELECT TOP 100 import_notes, * FROM t_al_host_order_detail WHERE wh_id = ? AND order_number = ?", whId, orderNumber));

            tables.add(namedQuery(conn, "aad_order", "Order", "AAD", "Order",
                "SELECT TOP 100 * FROM t_order WHERE wh_id = ? AND order_number = ?", whId, orderNumber));

            tables.add(namedQuery(conn, "aad_order_detail", "Order Detail", "AAD", "Order",
                "SELECT TOP 100 oms_order_number, * FROM t_order_detail WHERE wh_id = ? AND order_number = ?", whId, orderNumber));

            tables.add(namedQuery(conn, "aad_order_cancel", "Order Cancel", "AAD", "Order",
                "SELECT TOP 100 * FROM t_order_cancel WHERE wh_id = ? AND order_number = ?", whId, orderNumber));

            tables.add(namedQuery(conn, "aad_order_late_cancel", "Order Late Cancel", "AAD", "Order",
                "SELECT TOP 100 * FROM t_order_late_cancel WHERE wh_id = ? AND order_number = ?", whId, orderNumber));

            tables.add(namedQuery(conn, "aad_tran_log", "Transaction Log", "AAD", "Logs",
                "SELECT TOP 100 * FROM t_tran_log WHERE wh_id = ? AND control_number = ? ORDER BY tran_log_id DESC", whId, orderNumber));
        }

        if (containerId != null && whId != null) {
            tables.add(namedQuery(conn, "aad_pick_container_status_log", "Container Status Log", "AAD", "Pick & Container",
                "SELECT TOP 100 * FROM t_pick_container_status_log WHERE wh_id = ? AND container_id = ? ORDER BY unique_id DESC", whId, containerId));

            tables.add(namedQuery(conn, "aad_hu_master", "HU Master", "AAD", "Shipping",
                "SELECT TOP 100 * FROM t_hu_master WHERE wh_id = ? AND hu_id = ?", whId, containerId));

            tables.add(namedQuery(conn, "aad_stored_item", "Stored Item", "AAD", "Shipping",
                "SELECT TOP 100 * FROM t_stored_item WHERE wh_id = ? AND hu_id = ?", whId, containerId));

            tables.add(namedQuery(conn, "aad_ship_confirm_queue", "Ship Confirm Queue", "AAD", "Shipping",
                "SELECT TOP 100 * FROM t_pick_container_ship_confirm_queue WHERE wh_id = ? AND container_id = ?", whId, containerId));

            tables.add(namedQuery(conn, "aad_ship_confirm_queue_log", "Ship Confirm Queue Log", "AAD", "Shipping",
                "SELECT TOP 100 * FROM t_pick_container_ship_confirm_queue_log WHERE wh_id = ? AND container_id = ?", whId, containerId));

            tables.add(namedQuery(conn, "aad_ship_confirm_log", "Ship Confirm Log", "AAD", "Shipping",
                "SELECT TOP 100 * FROM t_pick_container_ship_confirm_log WHERE wh_id = ? AND container_id = ?", whId, containerId));

            tables.add(namedQuery(conn, "aad_divert_reason", "Divert Reason", "AAD", "Shipping",
                "SELECT TOP 100 * FROM t_pick_container_divert_reason WHERE wh_id = ? AND container_id = ?", whId, containerId));
        }

        // Exception log uses both identifiers
        if (whId != null) {
            if (orderNumber != null && containerId != null) {
                tables.add(namedQuery(conn, "aad_exception_log", "Exception Log", "AAD", "Logs",
                    "SELECT TOP 100 * FROM t_exception_log WHERE wh_id = ? AND (control_number = ? OR hu_id = ?) ORDER BY exception_id DESC",
                    whId, orderNumber, containerId));
            } else if (orderNumber != null) {
                tables.add(namedQuery(conn, "aad_exception_log", "Exception Log", "AAD", "Logs",
                    "SELECT TOP 100 * FROM t_exception_log WHERE wh_id = ? AND control_number = ? ORDER BY exception_id DESC",
                    whId, orderNumber));
            } else {
                tables.add(namedQuery(conn, "aad_exception_log", "Exception Log", "AAD", "Logs",
                    "SELECT TOP 100 * FROM t_exception_log WHERE wh_id = ? AND hu_id = ? ORDER BY exception_id DESC",
                    whId, containerId));
            }
        }

        return tables;
    }

    // ======================== IO Stack (WMSSQL-IO-TEST / AAD_IMPORT_ORDER) ========================

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> buildIoTables(Connection conn, String orderNumber, String containerId, String whId) {
        List<Map<String, Object>> tables = new ArrayList<>();

        if (orderNumber == null || whId == null) return tables;

        // --- Pre-Processing ---
        tables.add(namedQuery(conn, "io_event_queue", "Event Queue (Pre-Processing)", "IO", "Pre-Processing",
            "SELECT TOP 50 * FROM ADV..t_event_queue WHERE event_data LIKE '%' + ? + '%'", orderNumber));

        tables.add(namedQuery(conn, "io_xml_imp_oo_master", "XML Import OO Master", "IO", "Pre-Processing",
            "SELECT TOP 50 * FROM t_xml_imp_oo_master WHERE OrderNumber = ?", orderNumber));

        tables.add(namedQuery(conn, "io_xml_imp_oo_info", "XML Import OO Info", "IO", "Pre-Processing",
            "SELECT TOP 50 * FROM t_xml_imp_oo_info WHERE hjs_parent_id = (SELECT TOP 1 hjs_node_id FROM t_xml_imp_oo_master WHERE OrderNumber = ?)",
            orderNumber));

        tables.add(namedQuery(conn, "io_xml_imp_oo_details", "XML Import OO Details", "IO", "Pre-Processing",
            "SELECT TOP 50 * FROM t_xml_imp_oo_details WHERE hjs_parent_id = (SELECT TOP 1 hjs_node_id FROM t_xml_imp_oo_master WHERE OrderNumber = ?)",
            orderNumber));

        tables.add(namedQuery(conn, "io_link_work_queue", "Link Work Queue", "IO", "Pre-Processing",
            "SELECT TOP 50 * FROM t_link_work_queue WHERE event_type = 1 AND event_data = (SELECT TOP 1 hjs_parent_id FROM t_xml_imp_oo_master WHERE OrderNumber = ?) AND date_added > GETDATE() - 7",
            orderNumber));

        tables.add(namedQuery(conn, "io_event_queue_cls", "Event Queue (Class 6)", "IO", "Pre-Processing",
            "SELECT TOP 50 * FROM ADV..t_event_queue WHERE event_class = 6 AND event_data = " +
            "(SELECT CONCAT(N'SYS_EVENT_ID|', CONVERT(NVARCHAR(50), l.event_id)) " +
            "FROM t_link_work_queue l WHERE event_type = 1 " +
            "AND event_data = (SELECT TOP 1 hjs_parent_id FROM t_xml_imp_oo_master WHERE OrderNumber = ?) " +
            "AND date_added > GETDATE() - 7)", orderNumber));

        // --- Queues ---
        tables.add(namedQuery(conn, "io_cls_xml_log", "CLS XML Log", "IO", "Queues",
            "SELECT TOP 50 * FROM WMS_LOG..t_cls_xml_log WHERE order_number = ? AND wh_id = ? ORDER BY 2", orderNumber, whId));

        tables.add(namedQuery(conn, "io_cls_rate_queue", "CLS Rate Queue", "IO", "Queues",
            "SELECT TOP 50 * FROM t_cls_rate_queue WHERE order_number = ? AND wh_id = ? ORDER BY 2", orderNumber, whId));

        tables.add(namedQuery(conn, "io_al_order_import_queue", "Order Import Queue", "IO", "Queues",
            "SELECT TOP 50 * FROM t_al_order_import_queue WHERE order_number = ? AND wh_id = ?", orderNumber, whId));

        tables.add(namedQuery(conn, "io_container_optimize_queue", "Container Optimize Queue", "IO", "Queues",
            "SELECT TOP 50 * FROM t_container_optimize_queue WHERE order_number = ? AND wh_id = ?", orderNumber, whId));

        tables.add(namedQuery(conn, "io_cls_rate_order_queue", "CLS Rate Order Queue", "IO", "Queues",
            "SELECT TOP 50 * FROM t_cls_rate_order_queue WHERE order_number = ? AND wh_id = ?", orderNumber, whId));

        tables.add(namedQuery(conn, "io_cls_manifest_queue", "CLS Manifest Queue", "IO", "Queues",
            "SELECT TOP 50 * FROM t_cls_manifest_queue WHERE order_number = ? AND wh_id = ?", orderNumber, whId));

        tables.add(namedQuery(conn, "io_cls_rate_hold_queue", "CLS Rate Hold Queue", "IO", "Queues",
            "SELECT TOP 50 * FROM t_cls_rate_hold_queue WHERE order_number = ? AND wh_id = ?", orderNumber, whId));

        tables.add(namedQuery(conn, "io_export_order_queue", "Export Order Queue", "IO", "Queues",
            "SELECT TOP 50 * FROM t_export_order_queue WHERE order_number = ? AND wh_id = ?", orderNumber, whId));

        // --- Order Tables (IO) ---
        tables.add(namedQuery(conn, "io_al_host_order_master", "Import Order Master", "IO", "Order (IO)",
            "SELECT TOP 100 import_notes, * FROM t_al_host_order_master WHERE order_number = ? AND wh_id = ?", orderNumber, whId));

        tables.add(namedQuery(conn, "io_al_host_order_detail", "Import Order Detail", "IO", "Order (IO)",
            "SELECT TOP 100 import_notes, * FROM t_al_host_order_detail WHERE order_number = ? AND wh_id = ?", orderNumber, whId));

        tables.add(namedQuery(conn, "io_order", "Order", "IO", "Order (IO)",
            "SELECT TOP 100 cold_profile, express_eligible, * FROM t_order WHERE order_number = ? AND wh_id = ?", orderNumber, whId));

        tables.add(namedQuery(conn, "io_order_detail", "Order Detail", "IO", "Order (IO)",
            "SELECT TOP 100 * FROM t_order_detail WHERE order_number = ? AND wh_id = ?", orderNumber, whId));

        tables.add(namedQuery(conn, "io_pick_detail", "Pick Detail (with UOM)", "IO", "Pick & Container (IO)",
            "SELECT TOP 100 * FROM t_pick_detail pkd INNER JOIN t_item_uom uom ON uom.wh_id = pkd.wh_id AND uom.item_number = pkd.item_number " +
            "WHERE pkd.order_number = ? AND pkd.wh_id = ?", orderNumber, whId));

        tables.add(namedQuery(conn, "io_pick_container", "Pick Container", "IO", "Pick & Container (IO)",
            "SELECT TOP 100 service_level, transit_days, sat_delivery_flag, * FROM t_pick_container WHERE order_number = ? AND wh_id = ?", orderNumber, whId));

        if (containerId != null) {
            tables.add(namedQuery(conn, "io_pick_container_label", "Pick Container Label", "IO", "Pick & Container (IO)",
                "SELECT TOP 100 * FROM t_pick_container_label WHERE container_id = ? AND wh_id = ?", containerId, whId));
        }

        tables.add(namedQuery(conn, "io_work_queue", "Work Queue", "IO", "Pick & Container (IO)",
            "SELECT TOP 100 wkq.* FROM t_work_q wkq INNER JOIN t_pick_detail pkd ON pkd.work_q_id = wkq.work_q_id AND pkd.wh_id = wkq.wh_id " +
            "WHERE pkd.order_number = ? AND pkd.wh_id = ?", orderNumber, whId));

        return tables;
    }

    // ======================== Helpers ========================

    private Map<String, Object> namedQuery(Connection conn, String key, String displayName, String source, String group, String sql, Object... params) {
        Map<String, Object> result = executeQuery(conn, sql, params);
        result.put("name", key);
        result.put("displayName", displayName);
        result.put("source", source);
        result.put("group", group);
        return result;
    }

    private Map<String, Object> errorTable(String key, String displayName, String source, String group, String errorMsg) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("name", key);
        result.put("displayName", displayName);
        result.put("source", source);
        result.put("group", group);
        result.put("columns", List.of());
        result.put("rows", List.of());
        result.put("rowCount", 0);
        result.put("error", errorMsg);
        return result;
    }

    private Map<String, Object> executeQuery(Connection conn, String sql, Object... params) {
        Map<String, Object> result = new LinkedHashMap<>();

        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            for (int i = 0; i < params.length; i++) {
                stmt.setObject(i + 1, params[i]);
            }
            stmt.setQueryTimeout(config.getConnectionTimeout());

            try (ResultSet rs = stmt.executeQuery()) {
                ResultSetMetaData meta = rs.getMetaData();
                int colCount = meta.getColumnCount();

                List<String> columns = new ArrayList<>();
                for (int i = 1; i <= colCount; i++) {
                    String colName = meta.getColumnName(i);
                    if (columns.contains(colName)) {
                        colName = colName + "_" + i;
                    }
                    columns.add(colName);
                }
                result.put("columns", columns);

                List<Map<String, Object>> rows = new ArrayList<>();
                while (rs.next()) {
                    Map<String, Object> row = new LinkedHashMap<>();
                    for (int i = 1; i <= colCount; i++) {
                        Object value = rs.getObject(i);
                        if (value instanceof java.sql.Timestamp) {
                            row.put(columns.get(i - 1), value.toString());
                        } else if (value instanceof java.sql.Date) {
                            row.put(columns.get(i - 1), value.toString());
                        } else if (value instanceof byte[]) {
                            row.put(columns.get(i - 1), "[binary]");
                        } else if (value instanceof java.math.BigDecimal) {
                            row.put(columns.get(i - 1), ((java.math.BigDecimal) value).toPlainString());
                        } else {
                            row.put(columns.get(i - 1), value);
                        }
                    }
                    rows.add(row);
                }
                result.put("rows", rows);
                result.put("rowCount", rows.size());
            }
        } catch (SQLException e) {
            logger.warn("Query failed: {} - {}", sql.substring(0, Math.min(80, sql.length())), e.getMessage());
            result.put("columns", List.of());
            result.put("rows", List.of());
            result.put("rowCount", 0);
            result.put("error", e.getMessage());
        }

        return result;
    }

    public Map<String, Object> testConnection() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("ioServer", config.getIoServer());
        result.put("ioDatabase", config.getIoDatabase());
        result.put("aadServer", config.getAadServer());
        result.put("aadDatabase", config.getAadDatabase());

        try {
            Class.forName(config.getDriverClassName());
            try (Connection conn = DriverManager.getConnection(config.buildIoJdbcUrl())) {
                result.put("ioStatus", "Connected");
            }
        } catch (Exception e) {
            result.put("ioStatus", "Failed: " + e.getMessage());
        }

        try {
            try (Connection conn = DriverManager.getConnection(config.buildAadJdbcUrl())) {
                result.put("aadStatus", "Connected");
            }
        } catch (Exception e) {
            result.put("aadStatus", "Failed: " + e.getMessage());
        }

        return result;
    }
}
