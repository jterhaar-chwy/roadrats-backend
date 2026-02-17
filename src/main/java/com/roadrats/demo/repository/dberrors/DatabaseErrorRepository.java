package com.roadrats.demo.repository.dberrors;

import com.roadrats.demo.config.DatabaseErrorsConfig;
import com.roadrats.demo.model.dberrors.DatabaseErrorEntry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Repository;

import java.sql.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Repository that uses dynamic JDBC connections to query t_log_message
 * across multiple SQL Server instances. Does NOT use JPA/EntityManager
 * because we need to connect to N servers with the same schema dynamically.
 */
@Repository
public class DatabaseErrorRepository {

    private static final Logger logger = LoggerFactory.getLogger(DatabaseErrorRepository.class);

    private final DatabaseErrorsConfig config;

    private static final String ERROR_QUERY = """
        SELECT TOP 10000
            logged_on_local,
            machine_id,
            user_id,
            resource_name,
            details,
            call_stack,
            arguments
        FROM dbo.t_log_message WITH (NOLOCK)
        WHERE logged_on_utc >= DATEADD(day, ?, GETUTCDATE())
        AND resource_name LIKE 'CANT_EXE_DB%'
        AND call_stack <> '1: Process Exacta Divert Confirmation:32'
        ORDER BY logged_on_utc DESC
        """;

    public DatabaseErrorRepository(DatabaseErrorsConfig config) {
        this.config = config;
    }

    /**
     * Query a single server for database errors within the given lookback window.
     *
     * @param server the SQL Server hostname
     * @param days   number of days to look back (1-7)
     * @return list of error entries from this server
     */
    public List<DatabaseErrorEntry> queryServer(String server, int days) {
        String jdbcUrl = config.buildJdbcUrl(server);
        List<DatabaseErrorEntry> results = new ArrayList<>();

        logger.info("Querying server {} (database: {}, days: {})", server, config.getDatabase(), days);

        try {
            // Ensure driver is loaded
            Class.forName(config.getDriverClassName());
        } catch (ClassNotFoundException e) {
            logger.error("JDBC driver not found: {}", config.getDriverClassName(), e);
            throw new RuntimeException("JDBC driver not found: " + config.getDriverClassName(), e);
        }

        try (Connection conn = DriverManager.getConnection(jdbcUrl)) {
            // Set READ UNCOMMITTED isolation level (matches Python's behavior)
            conn.setTransactionIsolation(Connection.TRANSACTION_READ_UNCOMMITTED);

            try (PreparedStatement stmt = conn.prepareStatement(ERROR_QUERY)) {
                // Negative days for DATEADD lookback
                stmt.setInt(1, -Math.abs(days));
                stmt.setQueryTimeout(config.getConnectionTimeout());

                try (ResultSet rs = stmt.executeQuery()) {
                    while (rs.next()) {
                        DatabaseErrorEntry entry = new DatabaseErrorEntry();
                        entry.setServerName(server);

                        Timestamp ts = rs.getTimestamp("logged_on_local");
                        if (ts != null) {
                            entry.setLoggedOnLocal(ts.toLocalDateTime());
                        }

                        entry.setMachineId(rs.getString("machine_id"));
                        entry.setUserId(rs.getString("user_id"));
                        entry.setResourceName(rs.getString("resource_name"));
                        entry.setDetails(rs.getString("details"));
                        entry.setCallStack(rs.getString("call_stack"));
                        entry.setArguments(rs.getString("arguments"));

                        results.add(entry);
                    }
                }
            }

            logger.info("Server {} returned {} errors", server, results.size());

        } catch (SQLException e) {
            logger.error("Error querying server {}: {}", server, e.getMessage(), e);
            // Don't throw — return empty list so other servers can still be queried
        }

        return results;
    }

    /**
     * Test connectivity to a specific server.
     *
     * @param server the SQL Server hostname
     * @return connection metadata or error message
     */
    public String testConnection(String server) {
        String jdbcUrl = config.buildJdbcUrl(server);

        try {
            Class.forName(config.getDriverClassName());
        } catch (ClassNotFoundException e) {
            return "Driver not found: " + config.getDriverClassName();
        }

        try (Connection conn = DriverManager.getConnection(jdbcUrl)) {
            DatabaseMetaData meta = conn.getMetaData();
            return String.format("Connected to %s - %s %s (Driver: %s %s)",
                    server,
                    meta.getDatabaseProductName(),
                    meta.getDatabaseProductVersion(),
                    meta.getDriverName(),
                    meta.getDriverVersion());
        } catch (SQLException e) {
            return String.format("Failed to connect to %s: %s", server, e.getMessage());
        }
    }
}

