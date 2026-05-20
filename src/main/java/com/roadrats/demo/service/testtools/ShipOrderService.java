package com.roadrats.demo.service.testtools;

import com.roadrats.demo.config.Wms360Config;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.sql.*;
import java.util.*;

@Service
public class ShipOrderService {

    private static final Logger logger = LoggerFactory.getLogger(ShipOrderService.class);

    private final Wms360Config config;

    public ShipOrderService(Wms360Config config) {
        this.config = config;
    }

    public Map<String, Object> shipOrder(String warehouseId, String orderNumber, String env) {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("warehouseId", warehouseId);
        response.put("orderNumber", orderNumber);
        response.put("environment", env);
        response.put("executedAt", new java.util.Date().toString());

        if (Wms360Config.isProd(env)) {
            response.put("success", false);
            response.put("message", "Ship order is not allowed in production environment");
            return response;
        }

        try {
            Class.forName(config.getDriverClassName());
        } catch (ClassNotFoundException e) {
            response.put("success", false);
            response.put("message", "JDBC driver not found: " + e.getMessage());
            return response;
        }

        String jdbcUrl = config.buildAadJdbcUrl(env);
        logger.info("Ship order: wh={}, order={}, env={}, url={}", warehouseId, orderNumber, env, jdbcUrl);

        try (Connection conn = DriverManager.getConnection(jdbcUrl)) {
            try (CallableStatement cs = conn.prepareCall("{? = call dbo.usp_nonprod_order_ship(?, ?)}")) {
                cs.registerOutParameter(1, Types.INTEGER);
                cs.setString(2, warehouseId);
                cs.setString(3, orderNumber);
                cs.setQueryTimeout(config.getConnectionTimeout());
                cs.execute();

                int returnValue = cs.getInt(1);
                response.put("returnValue", returnValue);
                response.put("success", returnValue == 0);
                response.put("message", returnValue == 0
                    ? "Order shipped successfully"
                    : "Stored procedure returned " + returnValue);
            }
        } catch (SQLException e) {
            logger.error("Error shipping order wh={}, order={}", warehouseId, orderNumber, e);
            response.put("success", false);
            response.put("message", "Database error: " + e.getMessage());
        }

        return response;
    }

    public Map<String, Object> shipContainer(String warehouseId, String containerId, String env) {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("warehouseId", warehouseId);
        response.put("containerId", containerId);
        response.put("environment", env);
        response.put("executedAt", new java.util.Date().toString());

        if (Wms360Config.isProd(env)) {
            response.put("success", false);
            response.put("message", "Ship container is not allowed in production environment");
            return response;
        }

        try {
            Class.forName(config.getDriverClassName());
        } catch (ClassNotFoundException e) {
            response.put("success", false);
            response.put("message", "JDBC driver not found: " + e.getMessage());
            return response;
        }

        String jdbcUrl = config.buildAadJdbcUrl(env);
        logger.info("Ship container: wh={}, container={}, env={}, url={}", warehouseId, containerId, env, jdbcUrl);

        try (Connection conn = DriverManager.getConnection(jdbcUrl)) {
            try (CallableStatement cs = conn.prepareCall("{? = call dbo.usp_nonprod_container_ship(?, ?)}")) {
                cs.registerOutParameter(1, Types.INTEGER);
                cs.setString(2, warehouseId);
                cs.setString(3, containerId);
                cs.setQueryTimeout(config.getConnectionTimeout());
                cs.execute();

                int returnValue = cs.getInt(1);
                response.put("returnValue", returnValue);
                response.put("success", returnValue == 0);
                response.put("message", returnValue == 0
                    ? "Container shipped successfully"
                    : "Stored procedure returned " + returnValue);
            }
        } catch (SQLException e) {
            logger.error("Error shipping container wh={}, container={}", warehouseId, containerId, e);
            response.put("success", false);
            response.put("message", "Database error: " + e.getMessage());
        }

        return response;
    }
}
