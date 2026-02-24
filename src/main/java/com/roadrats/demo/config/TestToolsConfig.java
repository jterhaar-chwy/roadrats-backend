package com.roadrats.demo.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

@Configuration
public class TestToolsConfig {

    @Value("${roadrats.testtools.io-server:WMSSQL-IO-TEST}")
    private String ioServer;

    @Value("${roadrats.testtools.io-database:AAD_IMPORT_ORDER}")
    private String ioDatabase;

    @Value("${roadrats.testtools.aad-server:WMSSQL-TEST}")
    private String aadServer;

    @Value("${roadrats.testtools.aad-database:AAD}")
    private String aadDatabase;

    @Value("${roadrats.testtools.driver-class-name:com.microsoft.sqlserver.jdbc.SQLServerDriver}")
    private String driverClassName;

    @Value("${roadrats.testtools.connection-timeout:30}")
    private int connectionTimeout;

    @Value("${roadrats.testtools.xml-gateway-url:http://wmsapp-is-test/XMLLinkGateway/AlXmlGw.asp}")
    private String xmlGatewayUrl;

    public String getIoServer() { return ioServer; }
    public String getIoDatabase() { return ioDatabase; }
    public String getAadServer() { return aadServer; }
    public String getAadDatabase() { return aadDatabase; }
    public String getDriverClassName() { return driverClassName; }
    public int getConnectionTimeout() { return connectionTimeout; }
    public String getXmlGatewayUrl() { return xmlGatewayUrl; }

    public String buildIoJdbcUrl() {
        return String.format(
            "jdbc:sqlserver://%s;databaseName=%s;encrypt=true;trustServerCertificate=true;integratedSecurity=true",
            ioServer, ioDatabase
        );
    }

    public String buildAadJdbcUrl() {
        return String.format(
            "jdbc:sqlserver://%s;databaseName=%s;encrypt=true;trustServerCertificate=true;integratedSecurity=true",
            aadServer, aadDatabase
        );
    }
}
