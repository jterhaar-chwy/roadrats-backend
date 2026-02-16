package com.roadrats.demo.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/database")
@CrossOrigin(origins = "http://localhost:3000")
public class DatabaseTestController {

    private static final Logger logger = LoggerFactory.getLogger(DatabaseTestController.class);

    @Autowired
    @Qualifier("clsDataSource")
    private DataSource clsDataSource;

    @Autowired
    @Qualifier("ioDataSource")
    private DataSource ioDataSource;

    @GetMapping("/test/cls")
    public ResponseEntity<Map<String, Object>> testClsConnection() {
        return testConnection(clsDataSource, "CLS");
    }

    @GetMapping("/test/io")
    public ResponseEntity<Map<String, Object>> testIoConnection() {
        return testConnection(ioDataSource, "IO");
    }

    private ResponseEntity<Map<String, Object>> testConnection(DataSource dataSource, String dbName) {
        Map<String, Object> response = new HashMap<>();
        
        try {
            logger.info("Testing {} database connection...", dbName);
            long startTime = System.currentTimeMillis();
            
            try (Connection conn = dataSource.getConnection()) {
                long duration = System.currentTimeMillis() - startTime;
                
                DatabaseMetaData metaData = conn.getMetaData();
                
                response.put("connected", true);
                response.put("message", dbName + " database connection successful");
                response.put("connectionTime", duration + "ms");
                response.put("databaseProductName", metaData.getDatabaseProductName());
                response.put("databaseProductVersion", metaData.getDatabaseProductVersion());
                response.put("driverName", metaData.getDriverName());
                response.put("driverVersion", metaData.getDriverVersion());
                response.put("url", metaData.getURL());
                response.put("username", metaData.getUserName());
                
                logger.info("{} database connection successful in {}ms", dbName, duration);
                return ResponseEntity.ok(response);
            }
        } catch (Exception e) {
            logger.error("{} database connection test failed", dbName, e);
            response.put("connected", false);
            response.put("message", dbName + " database connection error: " + e.getMessage());
            response.put("error", e.getClass().getSimpleName());
            response.put("errorMessage", e.getMessage());
            
            if (e.getCause() != null) {
                response.put("cause", e.getCause().getMessage());
                response.put("causeClass", e.getCause().getClass().getSimpleName());
            }
            
            // Get root cause
            Throwable rootCause = e;
            while (rootCause.getCause() != null) {
                rootCause = rootCause.getCause();
            }
            response.put("rootCause", rootCause.getClass().getSimpleName() + ": " + rootCause.getMessage());
            
            return ResponseEntity.status(500).body(response);
        }
    }
}

