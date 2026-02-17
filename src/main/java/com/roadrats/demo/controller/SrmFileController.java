package com.roadrats.demo.controller;

import com.roadrats.demo.service.SrmFileService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/srm")
@CrossOrigin(origins = "http://localhost:3000")
public class SrmFileController {

    private static final Logger logger = LoggerFactory.getLogger(SrmFileController.class);

    @Autowired
    private SrmFileService srmFileService;

    @GetMapping("/version")
    public ResponseEntity<Map<String, Object>> getSrmVersion() {
        try {
            logger.info("Getting SRM version number...");
            String version = srmFileService.getSrmVersion();
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("version", version);
            logger.info("SRM version retrieved: {}", version);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Error getting SRM version", e);
            Map<String, Object> error = new HashMap<>();
            error.put("success", false);
            error.put("error", e.getMessage());
            error.put("errorType", e.getClass().getSimpleName());
            if (e.getCause() != null) {
                error.put("cause", e.getCause().getMessage());
            }
            return ResponseEntity.status(500).body(error);
        }
    }

    @PostMapping("/download")
    public ResponseEntity<Map<String, Object>> downloadSrmFiles(@RequestBody(required = false) Map<String, String> request) {
        try {
            logger.info("Starting SRM file download...");
            String version = request != null && request.containsKey("version") 
                ? request.get("version") 
                : srmFileService.getSrmVersion();
            
            logger.info("Downloading SRM files for version: {}", version);
            Map<String, Object> result = srmFileService.downloadSrmFiles(version);
            
            if (Boolean.FALSE.equals(result.get("success"))) {
                logger.error("Download failed: {}", result.get("error"));
                return ResponseEntity.status(500).body(result);
            }
            
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            logger.error("Error downloading SRM files", e);
            Map<String, Object> error = new HashMap<>();
            error.put("success", false);
            error.put("error", e.getMessage());
            error.put("errorType", e.getClass().getSimpleName());
            if (e.getCause() != null) {
                error.put("cause", e.getCause().getMessage());
            }
            // Include stack trace for debugging
            java.io.StringWriter sw = new java.io.StringWriter();
            java.io.PrintWriter pw = new java.io.PrintWriter(sw);
            e.printStackTrace(pw);
            error.put("stackTrace", sw.toString());
            return ResponseEntity.status(500).body(error);
        }
    }

    @PostMapping("/copy-to-local")
    public ResponseEntity<Map<String, Object>> copySrmFilesToLocal() {
        try {
            logger.info("Verifying SRM files in local directory...");
            Map<String, Object> result = srmFileService.copySrmFilesToLocal();
            
            if (Boolean.FALSE.equals(result.get("success"))) {
                logger.error("File verification failed: {}", result.get("error"));
                return ResponseEntity.status(500).body(result);
            }
            
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            logger.error("Error verifying SRM files in local directory", e);
            Map<String, Object> error = new HashMap<>();
            error.put("success", false);
            error.put("error", e.getMessage());
            error.put("errorType", e.getClass().getSimpleName());
            if (e.getCause() != null) {
                error.put("cause", e.getCause().getMessage());
            }
            return ResponseEntity.status(500).body(error);
        }
    }

    @PostMapping("/execute-full-process")
    public ResponseEntity<Map<String, Object>> executeFullProcess(@RequestBody(required = false) Map<String, String> request) {
        try {
            logger.info("Executing full SRM download and copy process...");
            String version = request != null && request.containsKey("version") && !request.get("version").trim().isEmpty()
                ? request.get("version").trim()
                : null;
            Map<String, Object> result = srmFileService.executeSrmDownloadProcess(version);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            logger.error("Error executing full SRM process", e);
            Map<String, Object> error = new HashMap<>();
            error.put("success", false);
            error.put("error", e.getMessage());
            return ResponseEntity.status(500).body(error);
        }
    }

    @GetMapping("/routes")
    public ResponseEntity<List<Map<String, Object>>> getRouteList() {
        try {
            logger.info("Getting route list...");
            List<Map<String, Object>> routes = srmFileService.getRouteList();
            return ResponseEntity.ok(routes);
        } catch (Exception e) {
            logger.error("Error getting route list", e);
            return ResponseEntity.status(500).build();
        }
    }

    @GetMapping("/routes/{routeName}/contents")
    public ResponseEntity<Map<String, Object>> getRouteContents(@PathVariable String routeName) {
        try {
            logger.info("Getting contents for route: {}", routeName);
            Map<String, Object> contents = srmFileService.getRouteFileContents(routeName);
            if (contents.containsKey("error")) {
                return ResponseEntity.status(404).body(contents);
            }
            return ResponseEntity.ok(contents);
        } catch (Exception e) {
            logger.error("Error getting route contents", e);
            Map<String, Object> error = new HashMap<>();
            error.put("error", e.getMessage());
            return ResponseEntity.status(500).body(error);
        }
    }
}
