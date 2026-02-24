package com.roadrats.demo.service.testtools;

import com.roadrats.demo.config.TestToolsConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.sql.*;
import java.time.Duration;
import java.util.*;

@Service
public class ItemImportService {

    private static final Logger logger = LoggerFactory.getLogger(ItemImportService.class);

    private final TestToolsConfig config;
    private final HttpClient httpClient;

    public ItemImportService(TestToolsConfig config) {
        this.config = config;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(15))
                .build();
    }

    /**
     * Look up an item in t_item_master on AAD for one or more warehouses.
     * If warehouseId is blank, searches across all warehouses.
     */
    public Map<String, Object> lookupItem(String itemNumber, String warehouseId) {
        Map<String, Object> result = new LinkedHashMap<>();
        String jdbcUrl = config.buildAadJdbcUrl();
        logger.info("lookupItem: item={}, wh={}", itemNumber, warehouseId);

        try {
            Class.forName(config.getDriverClassName());
        } catch (ClassNotFoundException e) {
            result.put("success", false);
            result.put("error", "JDBC driver not found: " + e.getMessage());
            return result;
        }

        try (Connection conn = DriverManager.getConnection(jdbcUrl)) {
            result.put("connection", config.getAadServer() + " / " + config.getAadDatabase());

            boolean hasWh = warehouseId != null && !warehouseId.trim().isEmpty();

            // Query t_item_master
            String itemSql = hasWh
                    ? "SELECT TOP 100 * FROM t_item_master WHERE item_number = ? AND wh_id = ?"
                    : "SELECT TOP 100 * FROM t_item_master WHERE item_number = ?";

            List<Map<String, Object>> itemRows = new ArrayList<>();
            try (PreparedStatement ps = conn.prepareStatement(itemSql)) {
                ps.setString(1, itemNumber.trim());
                if (hasWh) ps.setString(2, warehouseId.trim());
                try (ResultSet rs = ps.executeQuery()) {
                    ResultSetMetaData meta = rs.getMetaData();
                    int cols = meta.getColumnCount();
                    while (rs.next()) {
                        Map<String, Object> row = new LinkedHashMap<>();
                        for (int i = 1; i <= cols; i++) {
                            row.put(meta.getColumnLabel(i), rs.getObject(i));
                        }
                        itemRows.add(row);
                    }
                }
            }
            result.put("itemMaster", itemRows);

            // Query t_item_uom for this item
            String uomSql = hasWh
                    ? "SELECT TOP 100 * FROM t_item_uom WHERE item_number = ? AND wh_id = ?"
                    : "SELECT TOP 100 * FROM t_item_uom WHERE item_number = ?";

            List<Map<String, Object>> uomRows = new ArrayList<>();
            try (PreparedStatement ps = conn.prepareStatement(uomSql)) {
                ps.setString(1, itemNumber.trim());
                if (hasWh) ps.setString(2, warehouseId.trim());
                try (ResultSet rs = ps.executeQuery()) {
                    ResultSetMetaData meta = rs.getMetaData();
                    int cols = meta.getColumnCount();
                    while (rs.next()) {
                        Map<String, Object> row = new LinkedHashMap<>();
                        for (int i = 1; i <= cols; i++) {
                            row.put(meta.getColumnLabel(i), rs.getObject(i));
                        }
                        uomRows.add(row);
                    }
                }
            }
            result.put("itemUom", uomRows);

            result.put("found", !itemRows.isEmpty());
            result.put("success", true);
            if (itemRows.isEmpty()) {
                result.put("message", hasWh
                        ? "Item " + itemNumber + " not found in warehouse " + warehouseId
                        : "Item " + itemNumber + " not found in any warehouse");
            } else {
                Set<String> warehouses = new LinkedHashSet<>();
                for (Map<String, Object> row : itemRows) {
                    Object wh = row.get("wh_id");
                    if (wh != null) warehouses.add(wh.toString().trim());
                }
                result.put("warehouses", warehouses);
                result.put("message", "Item " + itemNumber + " found in " + warehouses.size() + " warehouse(s): " + String.join(", ", warehouses));
            }
        } catch (Exception e) {
            logger.error("Error looking up item", e);
            result.put("success", false);
            result.put("error", e.getMessage());
        }

        return result;
    }

    /**
     * Build the import XML and POST to the XML gateway for each warehouse.
     */
    public Map<String, Object> importItem(Map<String, Object> params) {
        Map<String, Object> result = new LinkedHashMap<>();
        List<Map<String, Object>> importResults = new ArrayList<>();

        String itemNumber = getStr(params, "itemNumber");
        @SuppressWarnings("unchecked")
        List<String> warehouses = (List<String>) params.get("warehouses");
        String description = getStr(params, "description", "Imported Item " + itemNumber);
        String weight = getStr(params, "weight", "1.0");
        String length = getStr(params, "length", "10.0");
        String width = getStr(params, "width", "10.0");
        String height = getStr(params, "height", "10.0");
        String uom = getStr(params, "uom", "EA");
        String inventoryType = getStr(params, "inventoryType", "FG");
        String frozen = getStr(params, "frozen", "N");
        String fresh = getStr(params, "fresh", "N");
        String hazmat = getStr(params, "hazmat", "No");

        if (itemNumber == null || itemNumber.isEmpty()) {
            result.put("success", false);
            result.put("error", "itemNumber is required");
            return result;
        }
        if (warehouses == null || warehouses.isEmpty()) {
            result.put("success", false);
            result.put("error", "At least one warehouse is required");
            return result;
        }

        String gatewayUrl = config.getXmlGatewayUrl();
        logger.info("importItem: item={}, warehouses={}, gateway={}", itemNumber, warehouses, gatewayUrl);

        for (String wh : warehouses) {
            Map<String, Object> whResult = new LinkedHashMap<>();
            whResult.put("warehouseId", wh);

            String xml = buildImportXml(itemNumber, wh, description,
                    weight, length, width, height, uom, inventoryType,
                    frozen, fresh, hazmat);
            whResult.put("xmlSent", xml);

            try {
                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(gatewayUrl))
                        .timeout(Duration.ofSeconds(30))
                        .header("Content-Type", "text/xml")
                        .POST(HttpRequest.BodyPublishers.ofString(xml))
                        .build();

                HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
                whResult.put("statusCode", response.statusCode());
                whResult.put("response", response.body());
                whResult.put("success", response.statusCode() >= 200 && response.statusCode() < 300);
                logger.info("Import item {} to {}: HTTP {}", itemNumber, wh, response.statusCode());
            } catch (Exception e) {
                logger.error("Error importing item {} to {}", itemNumber, wh, e);
                whResult.put("success", false);
                whResult.put("error", e.getMessage());
            }

            importResults.add(whResult);
        }

        long successCount = importResults.stream().filter(r -> Boolean.TRUE.equals(r.get("success"))).count();
        result.put("success", successCount > 0);
        result.put("totalWarehouses", warehouses.size());
        result.put("successCount", successCount);
        result.put("failCount", warehouses.size() - successCount);
        result.put("results", importResults);
        return result;
    }

    private String buildImportXml(String itemNumber, String warehouseId, String description,
                                   String weight, String length, String width, String height,
                                   String uom, String inventoryType,
                                   String frozen, String fresh, String hazmat) {
        StringBuilder sb = new StringBuilder();
        sb.append("<?xml version=\"1.0\"?>\n");
        sb.append("<import_item>\n");
        sb.append("\t<item_master>\n");
        sb.append("\t\t<ItemNumber>").append(esc(itemNumber)).append("</ItemNumber>\n");
        sb.append("\t\t<DisplayItemNumber>").append(esc(itemNumber)).append("</DisplayItemNumber>\n");
        sb.append("\t\t<WarehouseID>").append(esc(warehouseId)).append("</WarehouseID>\n");
        sb.append("\t\t<item_info>\n");
        sb.append("\t\t\t<TransactionCode>NEW</TransactionCode>\n");
        sb.append("\t\t\t<Description>").append(esc(description)).append("</Description>\n");
        sb.append("\t\t\t<DefaultBaseUOM>").append(esc(uom)).append("</DefaultBaseUOM>\n");
        sb.append("\t\t\t<InventoryType>").append(esc(inventoryType)).append("</InventoryType>\n");
        sb.append("\t\t\t<Price/>\n");
        sb.append("\t\t\t<AltItemNumber></AltItemNumber>\n");
        sb.append("\t\t\t<UPC>").append(esc(itemNumber)).append("</UPC>\n");
        sb.append("\t\t\t<HazMatIndicator>").append(esc(hazmat)).append("</HazMatIndicator>\n");
        sb.append("\t\t\t<UnitVolume/>\n");
        sb.append("\t\t\t<Frozen>").append(esc(frozen)).append("</Frozen>\n");
        sb.append("\t\t\t<Fresh>").append(esc(fresh)).append("</Fresh>\n");
        sb.append("\t\t\t<VelocityCode/>\n");
        sb.append("\t\t\t<SpeciesApplicable></SpeciesApplicable>\n");
        sb.append("\t\t\t<TherapeuticClass></TherapeuticClass>\n");
        sb.append("\t\t</item_info>\n");
        sb.append("\t\t<item_uoms>\n");
        sb.append("\t\t\t<item_uom>\n");
        sb.append("\t\t\t\t<TransactionCode>NEW</TransactionCode>\n");
        sb.append("\t\t\t\t<UOM>").append(esc(uom)).append("</UOM>\n");
        sb.append("\t\t\t\t<ConversionFactor>1</ConversionFactor>\n");
        sb.append("\t\t\t\t<Weight>").append(esc(weight)).append("</Weight>\n");
        sb.append("\t\t\t\t<Length>").append(esc(length)).append("</Length>\n");
        sb.append("\t\t\t\t<Width>").append(esc(width)).append("</Width>\n");
        sb.append("\t\t\t\t<Height>").append(esc(height)).append("</Height>\n");
        sb.append("\t\t\t\t<Pattern>STANDARD</Pattern>\n");
        sb.append("\t\t\t\t<ExcludeFromMailers>NO</ExcludeFromMailers>\n");
        sb.append("\t\t\t</item_uom>\n");
        sb.append("\t\t</item_uoms>\n");
        sb.append("\t\t<item_orientation>\n");
        sb.append("\t\t\t<TransactionCode>NEW</TransactionCode>\n");
        sb.append("\t\t</item_orientation>\n");
        sb.append("\t</item_master>\n");
        sb.append("</import_item>");
        return sb.toString();
    }

    private static String esc(String val) {
        if (val == null) return "";
        return val.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
                .replace("\"", "&quot;").replace("'", "&apos;");
    }

    private static String getStr(Map<String, Object> map, String key) {
        Object v = map.get(key);
        return v != null ? v.toString().trim() : null;
    }

    private static String getStr(Map<String, Object> map, String key, String def) {
        String v = getStr(map, key);
        return (v != null && !v.isEmpty()) ? v : def;
    }
}
