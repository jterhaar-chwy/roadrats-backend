package com.roadrats.demo.controller.dberrors;

import com.roadrats.demo.model.dberrors.DatabaseErrorEntry;
import com.roadrats.demo.service.dberrors.DatabaseErrorService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

@RestController
@RequestMapping("/api/database-errors")
@CrossOrigin(origins = "http://localhost:3000")
public class DatabaseErrorController {

    private static final Logger logger = LoggerFactory.getLogger(DatabaseErrorController.class);

    private final DatabaseErrorService service;

    public DatabaseErrorController(DatabaseErrorService service) {
        this.service = service;
    }

    /**
     * GET /api/database-errors?days=1
     * Query all configured servers for database errors.
     */
    @GetMapping
    public ResponseEntity<?> getErrors(@RequestParam(defaultValue = "1") int days) {
        logger.info("GET /api/database-errors?days={}", days);
        try {
            List<DatabaseErrorEntry> results = service.queryAllServers(days);

            Map<String, Object> response = new LinkedHashMap<>();
            response.put("totalErrors", results.size());
            response.put("days", Math.max(1, Math.min(7, days)));
            response.put("servers", service.getConfiguredServers());
            response.put("queriedAt", LocalDateTime.now().toString());
            response.put("errors", results);

            logger.info("Returning {} database errors", results.size());
            return ResponseEntity.ok(response);

        } catch (Exception e) {
            logger.error("Error fetching database errors", e);
            Map<String, Object> error = new LinkedHashMap<>();
            error.put("error", "Failed to fetch database errors");
            error.put("message", e.getMessage());
            return ResponseEntity.internalServerError().body(error);
        }
    }

    /**
     * GET /api/database-errors/server/{server}?days=1
     * Query a specific server for database errors.
     */
    @GetMapping("/server/{server}")
    public ResponseEntity<?> getErrorsByServer(
            @PathVariable String server,
            @RequestParam(defaultValue = "1") int days) {
        logger.info("GET /api/database-errors/server/{}?days={}", server, days);
        try {
            List<DatabaseErrorEntry> results = service.queryServer(server, days);

            Map<String, Object> response = new LinkedHashMap<>();
            response.put("totalErrors", results.size());
            response.put("days", Math.max(1, Math.min(7, days)));
            response.put("server", server);
            response.put("queriedAt", LocalDateTime.now().toString());
            response.put("errors", results);

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            logger.error("Error fetching database errors from server {}", server, e);
            Map<String, Object> error = new LinkedHashMap<>();
            error.put("error", "Failed to fetch database errors from " + server);
            error.put("message", e.getMessage());
            return ResponseEntity.internalServerError().body(error);
        }
    }

    /**
     * GET /api/database-errors/export?days=1
     * Export all errors as CSV download.
     */
    @GetMapping("/export")
    public ResponseEntity<byte[]> exportCsv(@RequestParam(defaultValue = "1") int days) {
        logger.info("GET /api/database-errors/export?days={}", days);
        try {
            List<DatabaseErrorEntry> results = service.queryAllServers(days);
            String csv = service.generateCsv(results);
            String filename = String.format("database-errors_%s.csv",
                    LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss")));

            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                    .contentType(MediaType.parseMediaType("text/csv"))
                    .body(csv.getBytes());

        } catch (Exception e) {
            logger.error("Error exporting database errors", e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * GET /api/database-errors/servers
     * Get the list of configured servers and their connection status.
     */
    @GetMapping("/servers")
    public ResponseEntity<?> getServers() {
        logger.info("GET /api/database-errors/servers");
        try {
            Map<String, Object> response = new LinkedHashMap<>();
            response.put("servers", service.getConfiguredServers());
            response.put("connectionTests", service.testAllConnections());
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Error testing server connections", e);
            Map<String, Object> error = new LinkedHashMap<>();
            error.put("error", "Failed to test server connections");
            error.put("message", e.getMessage());
            return ResponseEntity.internalServerError().body(error);
        }
    }
}

