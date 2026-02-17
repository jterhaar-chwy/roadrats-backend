package com.roadrats.demo.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

@Configuration
public class DatabaseErrorsConfig {

    @Value("${roadrats.dberrors.servers:WMSSQL-TEST,WMSSQL-IO-TEST,WMSSQL-INTEGRATION-TEST}")
    private String serversStr;

    @Value("${roadrats.dberrors.database:ADV}")
    private String database;

    @Value("${roadrats.dberrors.driver-class-name:com.microsoft.sqlserver.jdbc.SQLServerDriver}")
    private String driverClassName;

    @Value("${roadrats.dberrors.connection-timeout:30}")
    private int connectionTimeout;

    public List<String> getServers() {
        return Arrays.stream(serversStr.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toList());
    }

    public String getDatabase() {
        return database;
    }

    public String getDriverClassName() {
        return driverClassName;
    }

    public int getConnectionTimeout() {
        return connectionTimeout;
    }

    /**
     * Build a JDBC URL for a given server using Windows Integrated Authentication.
     */
    public String buildJdbcUrl(String server) {
        return String.format(
            "jdbc:sqlserver://%s;databaseName=%s;encrypt=true;trustServerCertificate=true;integratedSecurity=true",
            server, database
        );
    }
}

