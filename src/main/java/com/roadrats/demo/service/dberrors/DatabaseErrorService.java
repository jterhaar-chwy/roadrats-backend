package com.roadrats.demo.service.dberrors;

import com.roadrats.demo.config.DatabaseErrorsConfig;
import com.roadrats.demo.model.dberrors.DatabaseErrorEntry;
import com.roadrats.demo.repository.dberrors.DatabaseErrorRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.*;

/**
 * Service that orchestrates querying multiple SQL Server instances
 * in parallel for database errors, then aggregates and sorts results.
 */
@Service
public class DatabaseErrorService {

    private static final Logger logger = LoggerFactory.getLogger(DatabaseErrorService.class);

    private final DatabaseErrorsConfig config;
    private final DatabaseErrorRepository repository;
    private final ExecutorService executorService;

    public DatabaseErrorService(DatabaseErrorsConfig config, DatabaseErrorRepository repository) {
        this.config = config;
        this.repository = repository;
        // Thread pool sized to the number of servers we'll query in parallel
        this.executorService = Executors.newFixedThreadPool(
                Math.max(1, Runtime.getRuntime().availableProcessors())
        );
    }

    /**
     * Query all configured servers in parallel for database errors.
     *
     * @param days number of days to look back (1-7, clamped)
     * @return aggregated and sorted list of errors from all servers
     */
    public List<DatabaseErrorEntry> queryAllServers(int days) {
        // Clamp days to 1-7
        int clampedDays = Math.max(1, Math.min(7, days));
        List<String> servers = config.getServers();

        logger.info("Querying {} server(s) for database errors (days={}): {}", servers.size(), clampedDays, servers);

        // Submit parallel queries
        Map<String, Future<List<DatabaseErrorEntry>>> futures = new LinkedHashMap<>();
        for (String server : servers) {
            futures.put(server, executorService.submit(() -> repository.queryServer(server, clampedDays)));
        }

        // Collect results
        List<DatabaseErrorEntry> allResults = new ArrayList<>();
        Map<String, String> serverStatuses = new LinkedHashMap<>();

        for (Map.Entry<String, Future<List<DatabaseErrorEntry>>> entry : futures.entrySet()) {
            String server = entry.getKey();
            try {
                List<DatabaseErrorEntry> serverResults = entry.getValue().get(60, TimeUnit.SECONDS);
                allResults.addAll(serverResults);
                serverStatuses.put(server, "OK (" + serverResults.size() + " rows)");
            } catch (TimeoutException e) {
                logger.error("Timeout querying server {}", server);
                serverStatuses.put(server, "TIMEOUT");
            } catch (Exception e) {
                logger.error("Error querying server {}: {}", server, e.getMessage(), e);
                serverStatuses.put(server, "ERROR: " + e.getMessage());
            }
        }

        // Log summary
        logger.info("Query complete. Total errors: {}. Server statuses: {}", allResults.size(), serverStatuses);

        // Sort by logged_on_local DESC (most recent first)
        allResults.sort((a, b) -> {
            if (a.getLoggedOnLocal() == null && b.getLoggedOnLocal() == null) return 0;
            if (a.getLoggedOnLocal() == null) return 1;
            if (b.getLoggedOnLocal() == null) return -1;
            return b.getLoggedOnLocal().compareTo(a.getLoggedOnLocal());
        });

        return allResults;
    }

    /**
     * Query a single specific server.
     */
    public List<DatabaseErrorEntry> queryServer(String server, int days) {
        int clampedDays = Math.max(1, Math.min(7, days));
        return repository.queryServer(server, clampedDays);
    }

    /**
     * Get the list of configured servers.
     */
    public List<String> getConfiguredServers() {
        return config.getServers();
    }

    /**
     * Test connectivity to all configured servers.
     */
    public Map<String, String> testAllConnections() {
        Map<String, String> results = new LinkedHashMap<>();
        for (String server : config.getServers()) {
            results.put(server, repository.testConnection(server));
        }
        return results;
    }

    /**
     * Generate CSV content from a list of database error entries.
     */
    public String generateCsv(List<DatabaseErrorEntry> entries) {
        StringBuilder sb = new StringBuilder();
        // Header
        sb.append("Server,Time,Machine,User,Resource,Details,CallStack,Arguments\n");
        // Rows
        for (DatabaseErrorEntry entry : entries) {
            sb.append(escapeCsv(entry.getServerName())).append(',');
            sb.append(escapeCsv(entry.getLoggedOnLocal() != null ? entry.getLoggedOnLocal().toString() : "")).append(',');
            sb.append(escapeCsv(entry.getMachineId())).append(',');
            sb.append(escapeCsv(entry.getUserId())).append(',');
            sb.append(escapeCsv(entry.getResourceName())).append(',');
            sb.append(escapeCsv(entry.getDetails())).append(',');
            sb.append(escapeCsv(entry.getCallStack())).append(',');
            sb.append(escapeCsv(entry.getArguments())).append('\n');
        }
        return sb.toString();
    }

    private String escapeCsv(String value) {
        if (value == null) return "";
        // If value contains comma, quote, or newline, wrap in quotes and escape quotes
        if (value.contains(",") || value.contains("\"") || value.contains("\n") || value.contains("\r")) {
            return "\"" + value.replace("\"", "\"\"") + "\"";
        }
        return value;
    }
}

