package com.roadrats.demo.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

@Configuration
public class Wms360Config {

    // Test environment
    @Value("${roadrats.wms360.test.io-server:WMSSQL-IO-TEST}")
    private String testIoServer;

    @Value("${roadrats.wms360.test.io-database:AAD_IMPORT_ORDER}")
    private String testIoDatabase;

    @Value("${roadrats.wms360.test.aad-server:WMSSQL-TEST}")
    private String testAadServer;

    @Value("${roadrats.wms360.test.aad-database:AAD}")
    private String testAadDatabase;

    // Prod environment
    @Value("${roadrats.wms360.prod.io-server:WMSSQL-IO}")
    private String prodIoServer;

    @Value("${roadrats.wms360.prod.io-database:AAD_IMPORT_ORDER}")
    private String prodIoDatabase;

    @Value("${roadrats.wms360.prod.aad-server:WMSSQL}")
    private String prodAadServer;

    @Value("${roadrats.wms360.prod.aad-database:AAD}")
    private String prodAadDatabase;

    @Value("${roadrats.wms360.driver-class-name:com.microsoft.sqlserver.jdbc.SQLServerDriver}")
    private String driverClassName;

    @Value("${roadrats.wms360.connection-timeout:30}")
    private int connectionTimeout;

    @Value("${roadrats.wms360.xml-gateway-url:http://wmsapp-is-test/XMLLinkGateway/AlXmlGw.asp}")
    private String xmlGatewayUrl;

    public String getDriverClassName() { return driverClassName; }
    public int getConnectionTimeout() { return connectionTimeout; }
    public String getXmlGatewayUrl() { return xmlGatewayUrl; }

    public String getIoServer(String env) { return isProd(env) ? prodIoServer : testIoServer; }
    public String getIoDatabase(String env) { return isProd(env) ? prodIoDatabase : testIoDatabase; }
    public String getAadServer(String env) { return isProd(env) ? prodAadServer : testAadServer; }
    public String getAadDatabase(String env) { return isProd(env) ? prodAadDatabase : testAadDatabase; }

    // Legacy no-arg getters default to test
    public String getIoServer() { return testIoServer; }
    public String getIoDatabase() { return testIoDatabase; }
    public String getAadServer() { return testAadServer; }
    public String getAadDatabase() { return testAadDatabase; }

    public String buildIoJdbcUrl(String env) {
        return buildJdbcUrl(getIoServer(env), getIoDatabase(env));
    }

    public String buildAadJdbcUrl(String env) {
        return buildJdbcUrl(getAadServer(env), getAadDatabase(env));
    }

    public String buildIoJdbcUrl() { return buildIoJdbcUrl("test"); }
    public String buildAadJdbcUrl() { return buildAadJdbcUrl("test"); }

    private String buildJdbcUrl(String server, String database) {
        return String.format(
            "jdbc:sqlserver://%s;databaseName=%s;encrypt=true;trustServerCertificate=true;integratedSecurity=true",
            server, database
        );
    }

    public static boolean isProd(String env) {
        return "prod".equalsIgnoreCase(env);
    }
}
