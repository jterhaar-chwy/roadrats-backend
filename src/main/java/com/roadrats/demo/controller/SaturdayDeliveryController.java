package com.roadrats.demo.controller;

import com.roadrats.demo.service.SaturdayDeliveryService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/cls")
public class SaturdayDeliveryController {

    private static final Logger logger = LoggerFactory.getLogger(SaturdayDeliveryController.class);

    @Autowired
    private SaturdayDeliveryService saturdayDeliveryService;

    @GetMapping("/saturday-delivery")
    public ResponseEntity<?> checkSaturdayDeliveries() {
        try {
            logger.info("Saturday delivery check requested");
            long startTime = System.currentTimeMillis();
            Map<String, Object> results = saturdayDeliveryService.checkSaturdayDeliveries();
            long duration = System.currentTimeMillis() - startTime;
            results.put("queryTimeMs", duration);
            logger.info("Saturday delivery check completed in {}ms", duration);
            return ResponseEntity.ok(results);
        } catch (Exception e) {
            logger.error("Error checking Saturday deliveries", e);
            Map<String, String> error = new HashMap<>();
            error.put("error", "Failed to check Saturday deliveries");
            error.put("message", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(error);
        }
    }
}
