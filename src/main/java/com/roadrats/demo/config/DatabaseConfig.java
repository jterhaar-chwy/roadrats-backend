package com.roadrats.demo.config;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.transaction.annotation.EnableTransactionManagement;

import javax.sql.DataSource;

@Configuration
@EnableTransactionManagement
public class DatabaseConfig {

    // CLS Database Configuration (Primary)
    @Primary
    @Bean(name = "clsDataSource")
    public DataSource clsDataSource(
            @Value("${spring.datasource.cls.url}") String url,
            @Value("${spring.datasource.cls.username}") String username,
            @Value("${spring.datasource.cls.password}") String password,
            @Value("${spring.datasource.cls.driver-class-name}") String driverClassName) {
        org.slf4j.Logger logger = org.slf4j.LoggerFactory.getLogger(DatabaseConfig.class);
        
        // Log connection info (without password)
        logger.info("Configuring CLS DataSource:");
        logger.info("  URL: {}", url);
        logger.info("  Username: {}", username);
        logger.info("  Password: {}", password.isEmpty() ? "[EMPTY]" : "[SET]");
        logger.info("  Driver: {}", driverClassName);
        
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(url);
        
        // For Windows Authentication (integratedSecurity or JavaKerberos), don't set username/password
        // JavaKerberos is pure Java and doesn't require native DLLs
        boolean useWindowsAuth = url.contains("integratedSecurity=true") || url.contains("authenticationScheme=JavaKerberos");
        
        if (useWindowsAuth) {
            logger.info("  Using Windows Authentication - skipping username/password");
        } else {
            // Only set username/password if they're not empty and not using Windows Auth
            if (username != null && !username.trim().isEmpty()) {
                config.setUsername(username);
            }
            if (password != null && !password.trim().isEmpty()) {
                config.setPassword(password);
            }
        }
        
        config.setDriverClassName(driverClassName);
        config.setMaximumPoolSize(10);
        config.setMinimumIdle(1);  // Start with just 1 connection
        config.setConnectionTimeout(60000);  // Increase to 60 seconds
        config.setIdleTimeout(600000);
        config.setMaxLifetime(1800000);
        config.setValidationTimeout(5000);
        config.setConnectionTestQuery("SELECT 1");  // Test query to validate connections
        
        // Don't fail fast - allow app to start even if DB is temporarily unavailable
        config.setInitializationFailTimeout(-1);
        
        // Lazy initialization - don't create connections until needed
        config.setAutoCommit(true);
        
        HikariDataSource dataSource = new HikariDataSource(config);
        
        // Try to get a connection immediately to test it
        try {
            logger.info("Testing CLS database connection...");
            java.sql.Connection testConn = dataSource.getConnection();
            logger.info("CLS database connection successful!");
            testConn.close();
        } catch (Exception e) {
            logger.error("CLS database connection test failed: {}", e.getMessage(), e);
        }
        
        return dataSource;
    }

    // IO Database Configuration
    @Bean(name = "ioDataSource")
    public DataSource ioDataSource(
            @Value("${spring.datasource.io.url}") String url,
            @Value("${spring.datasource.io.username}") String username,
            @Value("${spring.datasource.io.password}") String password,
            @Value("${spring.datasource.io.driver-class-name}") String driverClassName) {
        org.slf4j.Logger logger = org.slf4j.LoggerFactory.getLogger(DatabaseConfig.class);
        
        // Log connection info (without password)
        logger.info("Configuring IO DataSource:");
        logger.info("  URL: {}", url);
        logger.info("  Username: {}", username);
        logger.info("  Password: {}", password.isEmpty() ? "[EMPTY]" : "[SET]");
        logger.info("  Driver: {}", driverClassName);
        
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(url);
        
        // For Windows Authentication (integratedSecurity or JavaKerberos), don't set username/password
        // JavaKerberos is pure Java and doesn't require native DLLs
        boolean useWindowsAuth = url.contains("integratedSecurity=true") || url.contains("authenticationScheme=JavaKerberos");
        
        if (useWindowsAuth) {
            logger.info("  Using Windows Authentication - skipping username/password");
        } else {
            // Only set username/password if they're not empty and not using Windows Auth
            if (username != null && !username.trim().isEmpty()) {
                config.setUsername(username);
            }
            if (password != null && !password.trim().isEmpty()) {
                config.setPassword(password);
            }
        }
        
        config.setDriverClassName(driverClassName);
        config.setMaximumPoolSize(10);
        config.setMinimumIdle(1);  // Start with just 1 connection
        config.setConnectionTimeout(60000);  // Increase to 60 seconds
        config.setIdleTimeout(600000);
        config.setMaxLifetime(1800000);
        config.setValidationTimeout(5000);
        config.setConnectionTestQuery("SELECT 1");  // Test query to validate connections
        
        // Don't fail fast - allow app to start even if DB is temporarily unavailable
        config.setInitializationFailTimeout(-1);
        
        // Lazy initialization - don't create connections until needed
        config.setAutoCommit(true);
        
        HikariDataSource dataSource = new HikariDataSource(config);
        
        // Try to get a connection immediately to test it
        try {
            logger.info("Testing IO database connection...");
            java.sql.Connection testConn = dataSource.getConnection();
            logger.info("IO database connection successful!");
            testConn.close();
        } catch (Exception e) {
            logger.error("IO database connection test failed: {}", e.getMessage(), e);
        }
        
        return dataSource;
    }
}

