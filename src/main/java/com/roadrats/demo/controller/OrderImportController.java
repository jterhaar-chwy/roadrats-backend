package com.roadrats.demo.controller;

import com.roadrats.demo.model.io.OrderImportResult;
import com.roadrats.demo.service.OrderImportService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/io")
@CrossOrigin(origins = "http://localhost:3000")
public class OrderImportController {

    private static final Logger logger = LoggerFactory.getLogger(OrderImportController.class);

    @Autowired
    private OrderImportService orderImportService;

    @GetMapping("/rate-query")
    public ResponseEntity<?> getRateQueryResults() {
        try {
            logger.info("Fetching rate query results...");
            long startTime = System.currentTimeMillis();
            List<OrderImportResult> results = orderImportService.getRateQueryResults();
            long duration = System.currentTimeMillis() - startTime;
            logger.info("Successfully retrieved {} results in {}ms", results.size(), duration);
            return ResponseEntity.ok(results);
        } catch (Exception e) {
            logger.error("Error fetching rate query results", e);
            Map<String, Object> error = new HashMap<>();
            error.put("error", "Failed to fetch rate query results");
            error.put("message", e.getMessage());
            error.put("cause", e.getCause() != null ? e.getCause().getMessage() : "Unknown");
            
            // Add more detailed error info
            Throwable rootCause = e;
            while (rootCause.getCause() != null) {
                rootCause = rootCause.getCause();
            }
            error.put("rootCause", rootCause.getClass().getSimpleName() + ": " + rootCause.getMessage());
            
            // Check if it's a connection timeout
            if (e.getMessage() != null && e.getMessage().contains("Connection is not available")) {
                error.put("hint", "Database connection pool timed out. Check if database is accessible and credentials are correct.");
            }
            
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(error);
        }
    }

    @GetMapping("/rate-hold-query")
    public ResponseEntity<?> getRateHoldQueryResults() {
        try {
            logger.info("Fetching rate hold query results...");
            List<OrderImportResult> results = orderImportService.getRateHoldQueryResults();
            logger.info("Successfully retrieved {} results", results.size());
            return ResponseEntity.ok(results);
        } catch (Exception e) {
            logger.error("Error fetching rate hold query results", e);
            Map<String, String> error = new HashMap<>();
            error.put("error", "Failed to fetch rate hold query results");
            error.put("message", e.getMessage());
            error.put("cause", e.getCause() != null ? e.getCause().getMessage() : "Unknown");
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(error);
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
}

