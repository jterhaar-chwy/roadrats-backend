package com.roadrats.demo.controller;

import com.roadrats.demo.model.io.EnrichedOrderResult;
import com.roadrats.demo.model.io.OrderImportResult;
import com.roadrats.demo.model.io.QueueStatusResult;
import com.roadrats.demo.model.io.XmlLogResult;
import com.roadrats.demo.repository.io.QueueStatusRepository;
import com.roadrats.demo.repository.io.XmlLogRepository;
import com.roadrats.demo.service.OrderImportService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/io")
@CrossOrigin(origins = "http://localhost:3000")
public class OrderImportController {

    private static final Logger logger = LoggerFactory.getLogger(OrderImportController.class);

    @Autowired
    private OrderImportService orderImportService;

    @Autowired
    private QueueStatusRepository queueStatusRepository;

    @Autowired
    private XmlLogRepository xmlLogRepository;

    @GetMapping("/xml-logs")
    public ResponseEntity<?> getXmlLogs(@RequestParam String orderNumber, @RequestParam String whId) {
        try {
            logger.info("Fetching XML logs for order={}, wh={}", orderNumber, whId);
            List<XmlLogResult> results = xmlLogRepository.getXmlLogs(orderNumber, whId);
            logger.info("Found {} XML log entries", results.size());
            return ResponseEntity.ok(results);
        } catch (Exception e) {
            logger.error("Error fetching XML logs", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(buildErrorResponse(e, "Failed to fetch XML logs"));
        }
    }

    @GetMapping("/rate-query")
    public ResponseEntity<?> getRateQueryResults() {
        try {
            logger.info("Fetching enriched rate query results...");
            long startTime = System.currentTimeMillis();
            List<EnrichedOrderResult> results = orderImportService.getEnrichedRateQueryResults();
            long duration = System.currentTimeMillis() - startTime;
            logger.info("Successfully retrieved {} enriched results in {}ms", results.size(), duration);
            return ResponseEntity.ok(results);
        } catch (Exception e) {
            logger.error("Error fetching rate query results", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(buildErrorResponse(e, "Failed to fetch rate query results"));
        }
    }

    @GetMapping("/rate-hold-query")
    public ResponseEntity<?> getRateHoldQueryResults() {
        try {
            logger.info("Fetching enriched rate hold query results...");
            long startTime = System.currentTimeMillis();
            List<EnrichedOrderResult> results = orderImportService.getEnrichedRateHoldQueryResults();
            long duration = System.currentTimeMillis() - startTime;
            logger.info("Successfully retrieved {} enriched results in {}ms", results.size(), duration);
            return ResponseEntity.ok(results);
        } catch (Exception e) {
            logger.error("Error fetching rate hold query results", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(buildErrorResponse(e, "Failed to fetch rate hold query results"));
        }
    }

    @GetMapping("/rate-query/raw")
    public ResponseEntity<?> getRawRateQueryResults() {
        try {
            List<OrderImportResult> results = orderImportService.getRateQueryResults();
            return ResponseEntity.ok(results);
        } catch (Exception e) {
            logger.error("Error fetching raw rate query results", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(buildErrorResponse(e, "Failed to fetch raw rate query results"));
        }
    }

    @GetMapping("/rate-query/summary")
    public ResponseEntity<?> getRateQuerySummary() {
        try {
            logger.info("Fetching rate query summary...");
            List<EnrichedOrderResult> results = orderImportService.getEnrichedRateQueryResults();
            Map<String, Object> summary = buildErrorSummary(results);
            return ResponseEntity.ok(summary);
        } catch (Exception e) {
            logger.error("Error fetching rate query summary", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(buildErrorResponse(e, "Failed to fetch rate query summary"));
        }
    }

    @GetMapping("/rate-hold-query/summary")
    public ResponseEntity<?> getRateHoldQuerySummary() {
        try {
            logger.info("Fetching rate hold query summary...");
            List<EnrichedOrderResult> results = orderImportService.getEnrichedRateHoldQueryResults();
            Map<String, Object> summary = buildErrorSummary(results);
            return ResponseEntity.ok(summary);
        } catch (Exception e) {
            logger.error("Error fetching rate hold query summary", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(buildErrorResponse(e, "Failed to fetch rate hold query summary"));
        }
    }

    @GetMapping("/rate-query/export")
    public ResponseEntity<?> exportRateQueryToCsv() {
        try {
            logger.info("Exporting rate query results to CSV...");
            List<EnrichedOrderResult> results = orderImportService.getEnrichedRateQueryResults();
            String csv = buildCsv(results);
            String filename = "cls_debugger_export_" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd_HHmmss")) + ".csv";

            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=" + filename)
                    .contentType(MediaType.parseMediaType("text/csv"))
                    .body(csv);
        } catch (Exception e) {
            logger.error("Error exporting rate query results", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(buildErrorResponse(e, "Failed to export rate query results"));
        }
    }

    @GetMapping("/rate-hold-query/export")
    public ResponseEntity<?> exportRateHoldQueryToCsv() {
        try {
            logger.info("Exporting rate hold query results to CSV...");
            List<EnrichedOrderResult> results = orderImportService.getEnrichedRateHoldQueryResults();
            String csv = buildCsv(results);
            String filename = "cls_debugger_hold_export_" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd_HHmmss")) + ".csv";

            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=" + filename)
                    .contentType(MediaType.parseMediaType("text/csv"))
                    .body(csv);
        } catch (Exception e) {
            logger.error("Error exporting rate hold query results", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(buildErrorResponse(e, "Failed to export rate hold query results"));
        }
    }
    
    @GetMapping("/queue-status")
    public ResponseEntity<?> getQueueStatus() {
        try {
            logger.info("Fetching CLS queue status...");
            long startTime = System.currentTimeMillis();
            Map<String, List<QueueStatusResult>> queues = queueStatusRepository.getAllQueueStatuses();
            long duration = System.currentTimeMillis() - startTime;

            Map<String, Object> response = new LinkedHashMap<>();
            response.put("queryTimeMs", duration);

            // Build summary counts
            Map<String, Integer> counts = new LinkedHashMap<>();
            int totalStuck = 0;
            for (Map.Entry<String, List<QueueStatusResult>> entry : queues.entrySet()) {
                counts.put(entry.getKey(), entry.getValue().size());
                totalStuck += entry.getValue().size();
            }
            response.put("totalStuck", totalStuck);
            response.put("counts", counts);
            response.put("queues", queues);

            logger.info("Queue status: {} total stuck orders across {} queues in {}ms", totalStuck, queues.size(), duration);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Error fetching queue status", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(buildErrorResponse(e, "Failed to fetch queue status"));
        }
    }

    @GetMapping("/database/test")
    public ResponseEntity<Map<String, Object>> testIoConnection() {
        Map<String, Object> response = new java.util.HashMap<>();
        try {
            logger.info("Testing IO database connection...");
            long startTime = System.currentTimeMillis();
            
            // Try to execute a simple query
            List<OrderImportResult> results = orderImportService.getRateQueryResults();
            long duration = System.currentTimeMillis() - startTime;
            
            response.put("connected", true);
            response.put("message", "IO database connection successful");
            response.put("queryTime", duration + "ms");
            response.put("testQueryResultCount", results.size());
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("IO database connection test failed", e);
            response.put("connected", false);
            response.put("message", "IO database connection error: " + e.getMessage());
            response.put("error", e.getClass().getSimpleName());
            if (e.getCause() != null) {
                response.put("cause", e.getCause().getMessage());
            }
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }

    /**
     * Build error summary grouped by error text and warehouse.
     * Mirrors Python summarize_results() in CLS_Debugger.py.
     */
    private Map<String, Object> buildErrorSummary(List<EnrichedOrderResult> results) {
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("totalOrders", results.size());

        // Split comma-separated error texts and count by (errorText, whId)
        List<Map<String, Object>> errorBreakdown = new ArrayList<>();
        Map<String, Map<String, Integer>> errorsByTextAndWarehouse = new LinkedHashMap<>();

        for (EnrichedOrderResult result : results) {
            String errorText = result.getErrorText();
            if (errorText == null || errorText.isBlank()) continue;

            String[] splitErrors = errorText.split(",");
            for (String singleError : splitErrors) {
                String trimmed = singleError.trim();
                if (trimmed.isEmpty()) continue;

                errorsByTextAndWarehouse
                        .computeIfAbsent(trimmed, k -> new LinkedHashMap<>())
                        .merge(result.getWhId() != null ? result.getWhId() : "UNKNOWN", 1, Integer::sum);
            }
        }

        for (Map.Entry<String, Map<String, Integer>> entry : errorsByTextAndWarehouse.entrySet()) {
            Map<String, Object> errorEntry = new LinkedHashMap<>();
            errorEntry.put("errorText", entry.getKey());
            int totalCount = entry.getValue().values().stream().mapToInt(Integer::intValue).sum();
            errorEntry.put("totalCount", totalCount);
            errorEntry.put("warehouseBreakdown", entry.getValue());
            errorBreakdown.add(errorEntry);
        }

        // Sort by total count descending
        errorBreakdown.sort((a, b) -> Integer.compare((int) b.get("totalCount"), (int) a.get("totalCount")));
        summary.put("errors", errorBreakdown);

        // Unique warehouses
        Set<String> warehouses = results.stream()
                .map(EnrichedOrderResult::getWhId)
                .filter(Objects::nonNull)
                .collect(Collectors.toCollection(TreeSet::new));
        summary.put("warehouses", warehouses);

        // Count with errors
        long withErrors = results.stream()
                .filter(r -> r.getErrorText() != null && !r.getErrorText().isBlank())
                .count();
        summary.put("ordersWithErrors", withErrors);

        return summary;
    }

    /**
     * Build CSV string from enriched results.
     * Mirrors Python export_to_csv() in CLS_Debugger.py.
     */
    private String buildCsv(List<EnrichedOrderResult> results) {
        StringBuilder sb = new StringBuilder();
        sb.append("Warehouse,Order Number,Item Number,Error Text,Import Status,");
        sb.append("Ship Date,Arrival Date,TNT,Travel Time,Service Level,");
        sb.append("City,State,Postal Code,Route\n");

        for (EnrichedOrderResult r : results) {
            sb.append(escapeCsv(r.getWhId())).append(",");
            sb.append(escapeCsv(r.getOrderNumber())).append(",");
            sb.append(escapeCsv(r.getItemNumber())).append(",");
            sb.append(escapeCsv(r.getErrorText())).append(",");
            sb.append(escapeCsv(r.getImportStatus())).append(",");
            sb.append(escapeCsv(r.getShipDate())).append(",");
            sb.append(escapeCsv(r.getArriveDate())).append(",");
            sb.append(escapeCsv(r.getTravelDays())).append(",");
            sb.append(r.getDaysBetween() != null ? r.getDaysBetween() : "").append(",");
            sb.append(escapeCsv(r.getServiceLevel())).append(",");
            sb.append(escapeCsv(r.getCity())).append(",");
            sb.append(escapeCsv(r.getState())).append(",");
            sb.append(escapeCsv(r.getPostalCode())).append(",");
            sb.append(escapeCsv(r.getRoute())).append("\n");
        }
        return sb.toString();
    }

    private String escapeCsv(String value) {
        if (value == null) return "";
        if (value.contains(",") || value.contains("\"") || value.contains("\n")) {
            return "\"" + value.replace("\"", "\"\"") + "\"";
        }
        return value;
    }

    private Map<String, Object> buildErrorResponse(Exception e, String message) {
        Map<String, Object> error = new HashMap<>();
        error.put("error", message);
        error.put("message", e.getMessage());
        error.put("cause", e.getCause() != null ? e.getCause().getMessage() : "Unknown");

        Throwable rootCause = e;
        while (rootCause.getCause() != null) {
            rootCause = rootCause.getCause();
        }
        error.put("rootCause", rootCause.getClass().getSimpleName() + ": " + rootCause.getMessage());

        if (e.getMessage() != null && e.getMessage().contains("Connection is not available")) {
            error.put("hint", "Database connection pool timed out. Check if database is accessible and credentials are correct.");
        }
        return error;
    }
}

