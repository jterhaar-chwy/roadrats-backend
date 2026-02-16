package com.roadrats.demo.controller;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api")
public class HealthController {
    
    @Value("${spring.datasource.io.url:not-set}")
    private String ioDbUrl;
    
    @Value("${spring.datasource.io.username:not-set}")
    private String ioDbUsername;
    
    @Value("${spring.datasource.cls.url:not-set}")
    private String clsDbUrl;
    
    @Value("${spring.datasource.cls.username:not-set}")
    private String clsDbUsername;
    
    @GetMapping("/health")
    public ResponseEntity<Map<String, String>> health() {
        Map<String, String> response = new HashMap<>();
        response.put("status", "UP");
        response.put("message", "Road Rats Backend is running");
        return ResponseEntity.ok(response);
    }
    
    @GetMapping("/config")
    public ResponseEntity<Map<String, Object>> config() {
        Map<String, Object> response = new HashMap<>();
        Map<String, String> io = new HashMap<>();
        io.put("url", ioDbUrl);
        io.put("username", ioDbUsername);
        io.put("password", "***");
        
        Map<String, String> cls = new HashMap<>();
        cls.put("url", clsDbUrl);
        cls.put("username", clsDbUsername);
        cls.put("password", "***");
        
        response.put("io", io);
        response.put("cls", cls);
        return ResponseEntity.ok(response);
    }
}

