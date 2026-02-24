package com.roadrats.demo.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.sql.DataSource;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.*;
import java.util.LinkedHashMap;
import java.util.Comparator;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Service
public class SrmValidationService {

    private static final Logger logger = LoggerFactory.getLogger(SrmValidationService.class);

    @Autowired
    @Qualifier("clsDataSource")
    private DataSource clsDataSource;

    @Value("${roadrats.srm.local.path:${user.dir}/SRM}")
    private String localSrmPath;

    /**
     * Validate SRM files by comparing with production data
     */
    public Map<String, Object> validateSrmFiles() {
        Map<String, Object> result = new HashMap<>();
        try {
            logger.info("Starting SRM validation...");

            // Step 1: Read all SRM CSV files
            List<SrmRouteData> srmData = readSrmFiles();
            if (srmData.isEmpty()) {
                Path srmDirPath = Paths.get(localSrmPath);
                if (!srmDirPath.isAbsolute()) {
                    srmDirPath = srmDirPath.toAbsolutePath();
                }
                srmDirPath = srmDirPath.normalize();
                result.put("success", false);
                result.put("error", "No SRM files found in " + srmDirPath);
                return result;
            }

            logger.info("Read {} route records from SRM files", srmData.size());

            // Step 2: Get carrier translations
            Map<String, CarrierInfo> carrierMap = getCarrierTranslations();

            // Step 3: Group SRM data by shipper
            Map<String, List<SrmRouteData>> srmByShipper = groupByShipper(srmData);

            // Step 4: For each shipper, compare with production
            // Group results by Shipper -> Route -> Service
            Map<String, Map<String, Map<String, Map<String, Object>>>> shipperRouteServiceMap = new HashMap<>();
            Set<String> shippersValidated = new HashSet<>();

            for (Map.Entry<String, List<SrmRouteData>> entry : srmByShipper.entrySet()) {
                String shipper = entry.getKey();
                List<SrmRouteData> shipperSrmData = entry.getValue();

                // Get origin for this shipper
                String origin = getOriginForShipper(shipper);
                if (origin == null) {
                    logger.warn("No origin found for shipper: {}", shipper);
                    continue;
                }

                // Check if shipper should be skipped
                if (shouldSkipShipper(shipper)) {
                    logger.info("Skipping shipper: {}", shipper);
                    continue;
                }

                // Read production data
                List<ProductionRouteData> productionData = readProductionData(shipper, origin);

                // Map SRM data using carrier translations
                List<ProductionRouteData> mappedSrmData = mapSrmToProduction(shipperSrmData, carrierMap);

                // Compare and find differences
                List<RouteDifference> differences = compareRoutes(mappedSrmData, productionData);

                // Group by Shipper -> Route -> Service
                for (RouteDifference diff : differences) {
                    String route = diff.defaultRoute;
                    String service = diff.service;
                    
                    // Initialize nested maps if needed
                    shipperRouteServiceMap.computeIfAbsent(shipper, k -> new HashMap<>())
                        .computeIfAbsent(route, k -> new HashMap<>())
                        .computeIfAbsent(service, k -> {
                            Map<String, Object> serviceSummary = new HashMap<>();
                            serviceSummary.put("shipper", shipper);
                            serviceSummary.put("route", route);
                            serviceSummary.put("service", service);
                            serviceSummary.put("postalCodeCount", 0);
                            serviceSummary.put("differences", new ArrayList<Map<String, Object>>());
                            return serviceSummary;
                        });
                    
                    // Update postal code count and add difference
                    Map<String, Object> serviceSummary = shipperRouteServiceMap.get(shipper).get(route).get(service);
                    serviceSummary.put("postalCodeCount", (Integer) serviceSummary.get("postalCodeCount") + 1);
                    
                    @SuppressWarnings("unchecked")
                    List<Map<String, Object>> diffList = (List<Map<String, Object>>) serviceSummary.get("differences");
                    diffList.add(convertDifferenceToMap(diff));
                }
                
                shippersValidated.add(shipper);
            }

            // Step 5: Flatten the nested structure into a list and sort
            List<Map<String, Object>> allSummaries = new ArrayList<>();
            for (Map.Entry<String, Map<String, Map<String, Map<String, Object>>>> shipperEntry : shipperRouteServiceMap.entrySet()) {
                for (Map.Entry<String, Map<String, Map<String, Object>>> routeEntry : shipperEntry.getValue().entrySet()) {
                    for (Map.Entry<String, Map<String, Object>> serviceEntry : routeEntry.getValue().entrySet()) {
                        allSummaries.add(serviceEntry.getValue());
                    }
                }
            }

            // Sort alphabetically by Shipper, then by Route
            allSummaries.sort((a, b) -> {
                String shipperA = (String) a.getOrDefault("shipper", "");
                String shipperB = (String) b.getOrDefault("shipper", "");
                int shipperCompare = shipperA.compareToIgnoreCase(shipperB);
                if (shipperCompare != 0) {
                    return shipperCompare;
                }
                // If shippers are equal, compare by route
                String routeA = (String) a.getOrDefault("route", a.getOrDefault("defaultRoute", ""));
                String routeB = (String) b.getOrDefault("route", b.getOrDefault("defaultRoute", ""));
                return routeA.compareToIgnoreCase(routeB);
            });

            // Step 6: Build delta-comparable rollup (grouped by shipper + route + changeType)
            Map<String, Map<String, Object>> deltaComparableMap = new LinkedHashMap<>();
            for (Map<String, Object> summary : allSummaries) {
                String shipper = (String) summary.getOrDefault("shipper", "");
                String route = (String) summary.getOrDefault("route", "");
                @SuppressWarnings("unchecked")
                List<Map<String, Object>> diffs = (List<Map<String, Object>>) summary.get("differences");
                if (diffs == null) continue;

                // Count zips per changeType within this shipper+route
                Map<String, Integer> changeTypeCounts = new HashMap<>();
                for (Map<String, Object> diff : diffs) {
                    String ct = (String) diff.getOrDefault("changeType", "UNKNOWN");
                    changeTypeCounts.merge(ct, 1, Integer::sum);
                }

                for (Map.Entry<String, Integer> ctEntry : changeTypeCounts.entrySet()) {
                    String compositeKey = shipper + "|" + route + "|" + ctEntry.getKey();
                    Map<String, Object> existing = deltaComparableMap.get(compositeKey);
                    if (existing != null) {
                        existing.put("numZips", (Integer) existing.get("numZips") + ctEntry.getValue());
                    } else {
                        Map<String, Object> entry = new LinkedHashMap<>();
                        entry.put("fulfillmentCenter", shipper);
                        entry.put("routeName", route);
                        entry.put("changeType", ctEntry.getKey());
                        entry.put("numZips", ctEntry.getValue());
                        deltaComparableMap.put(compositeKey, entry);
                    }
                }
            }
            List<Map<String, Object>> deltaComparable = new ArrayList<>(deltaComparableMap.values());
            deltaComparable.sort(Comparator.comparing((Map<String, Object> m) -> (String) m.getOrDefault("fulfillmentCenter", ""))
                .thenComparing(m -> (String) m.getOrDefault("routeName", ""))
                .thenComparing(m -> (String) m.getOrDefault("changeType", "")));

            // Step 7: Build result
            int totalPostalCodesChanged = allSummaries.stream()
                .mapToInt(s -> (Integer) s.getOrDefault("postalCodeCount", 0))
                .sum();
            
            result.put("success", true);
            result.put("validationResults", allSummaries);
            result.put("deltaComparable", deltaComparable);
            result.put("summary", Map.of(
                "totalRoutesAffected", allSummaries.size(),
                "totalPostalCodesChanged", totalPostalCodesChanged,
                "shippersValidated", new ArrayList<>(shippersValidated)
            ));

            logger.info("Validation complete. Found {} routes with changes affecting {} postal codes",
                allSummaries.size(),
                totalPostalCodesChanged);

        } catch (Exception e) {
            logger.error("Error validating SRM files", e);
            result.put("success", false);
            // Normalize path in error message if it contains the path
            String errorMsg = e.getMessage();
            if (errorMsg != null && errorMsg.contains(localSrmPath)) {
                String normalizedPath = localSrmPath.replace('/', File.separatorChar);
                Path srmDirPath = Paths.get(normalizedPath).toAbsolutePath().normalize();
                errorMsg = errorMsg.replace(localSrmPath, srmDirPath.toString());
            }
            result.put("error", errorMsg);
        }

        return result;
    }

    /**
     * Read all SRM CSV files from local directory
     * Files are expected to be named: ROUTE_VERSION_CLSRoute.csv (e.g., AVP1_12101_CLSRoute.csv)
     */
    private List<SrmRouteData> readSrmFiles() throws Exception {
        List<SrmRouteData> routes = new ArrayList<>();
        
        // Use Paths.get() which handles path normalization automatically
        Path srmDirPath = Paths.get(localSrmPath);
        if (!srmDirPath.isAbsolute()) {
            srmDirPath = srmDirPath.toAbsolutePath();
        }
        srmDirPath = srmDirPath.normalize();
        
        logger.info("=== SRM Validation Path Check ===");
        logger.info("Configured localSrmPath: {}", localSrmPath);
        logger.info("Resolved absolute path: {}", srmDirPath);
        logger.info("Current working directory (user.dir): {}", System.getProperty("user.dir"));
        logger.info("Path exists: {}", Files.exists(srmDirPath));
        logger.info("Path is directory: {}", Files.isDirectory(srmDirPath));

        if (!Files.exists(srmDirPath)) {
            logger.warn("SRM directory does not exist: {}", srmDirPath);
            throw new RuntimeException("SRM directory does not exist: " + srmDirPath);
        }
        
        if (!Files.isDirectory(srmDirPath)) {
            logger.warn("SRM path is not a directory: {}", srmDirPath);
            throw new RuntimeException("SRM path is not a directory: " + srmDirPath);
        }

        // Find all CSV files matching pattern: ROUTE_VERSION_CLSRoute.csv
        List<Path> csvFilePaths = new ArrayList<>();
        try (Stream<Path> paths = Files.list(srmDirPath)) {
            for (Path path : paths.collect(Collectors.toList())) {
                if (!Files.isRegularFile(path)) {
                    continue;
                }
                
                String fileName = path.getFileName().toString();
                // Match pattern: ROUTE_VERSION_CLSRoute.csv (case-insensitive)
                if (fileName.toLowerCase().endsWith("_clsroute.csv")) {
                    csvFilePaths.add(path);
                    logger.debug("Found SRM file: {}", fileName);
                }
            }
        }

        if (csvFilePaths.isEmpty()) {
            logger.warn("No SRM CSV files found in {} (searched for files matching pattern: ROUTE_VERSION_CLSRoute.csv)", srmDirPath);
            // List what files are actually there for debugging
            try (Stream<Path> paths = Files.list(srmDirPath)) {
                List<Path> allPaths = paths.collect(Collectors.toList());
                if (!allPaths.isEmpty()) {
                    logger.info("Found {} items in directory:", allPaths.size());
                    for (Path path : allPaths) {
                        logger.info("  - {} (file: {})", path.getFileName(), Files.isRegularFile(path));
                    }
                }
            }
            throw new RuntimeException("No SRM CSV files found in " + srmDirPath + ". Files must match pattern: ROUTE_VERSION_CLSRoute.csv");
        }
        
        logger.info("Found {} CSV files matching pattern ROUTE_VERSION_CLSRoute.csv", csvFilePaths.size());

        for (Path csvFilePath : csvFilePaths) {
            File csvFile = csvFilePath.toFile();
            // Extract shipper (ROUTE) from filename
            // Files are named: ROUTE_VERSION_CLSRoute.csv (e.g., AVP1_12101_CLSRoute.csv)
            // Extract the ROUTE part (before the version number)
            String fileName = csvFile.getName();
            String shipper;
            
            // Remove "_CLSRoute.csv" suffix (case-insensitive)
            String baseName = fileName;
            if (baseName.toLowerCase().endsWith("_clsroute.csv")) {
                baseName = baseName.substring(0, baseName.length() - "_CLSRoute.csv".length());
            }
            
            // Pattern: ROUTE_VERSION -> extract ROUTE
            // Find the last underscore and check if what follows is a version number (all digits)
            int lastUnderscoreIndex = baseName.lastIndexOf('_');
            if (lastUnderscoreIndex > 0) {
                String afterUnderscore = baseName.substring(lastUnderscoreIndex + 1);
                // Check if it's all digits (version number)
                if (afterUnderscore.matches("^\\d+$")) {
                    // Extract ROUTE part (everything before the version)
                    shipper = baseName.substring(0, lastUnderscoreIndex);
                } else {
                    // No version number pattern, use whole base name as shipper
                    shipper = baseName;
                }
            } else {
                // No underscore found, use whole base name as shipper
                shipper = baseName;
            }
            
            logger.debug("Extracted shipper '{}' from filename '{}' (pattern: ROUTE_VERSION_CLSRoute.csv)", shipper, fileName);

            try (BufferedReader reader = new BufferedReader(new FileReader(csvFile))) {
                String line;
                List<String> headers = null;
                boolean isFirstLine = true;
                int lineNumber = 0;

                while ((line = reader.readLine()) != null) {
                    lineNumber++;
                    // Skip empty lines
                    if (line.trim().isEmpty()) {
                        continue;
                    }
                    
                    if (isFirstLine) {
                        headers = parseCsvLine(line);
                        logger.debug("File {} has {} headers: {}", fileName, headers.size(), headers);
                        isFirstLine = false;
                    } else {
                        List<String> values = parseCsvLine(line);
                        if (values.size() != headers.size()) {
                            logger.warn("File {} line {}: Row has {} values but {} headers, skipping. Line content: {}", 
                                fileName, lineNumber, values.size(), headers.size(), line.length() > 100 ? line.substring(0, 100) + "..." : line);
                            continue;
                        }

                        Map<String, String> row = new HashMap<>();
                        for (int i = 0; i < headers.size(); i++) {
                            row.put(headers.get(i), values.get(i));
                        }

                        // Extract route data
                        // CSV columns: DESTINATION_ZIP, LOCATION, SHIPPING_METHOD, TRANSIT_DAYS, FREIGHT_ZONE, DEFAULT_ROUTE, CUT_TIME, PULL_TIME
                        SrmRouteData route = new SrmRouteData();
                        route.shipper = shipper;
                        // Map DESTINATION_ZIP to postalCode
                        route.postalCode = row.getOrDefault("DESTINATION_ZIP", row.getOrDefault("POSTALCODE", "")).trim();
                        // Map SHIPPING_METHOD to code
                        route.code = row.getOrDefault("SHIPPING_METHOD", row.getOrDefault("CODE", "")).trim();
                        route.transitDays = parseFloat(row.getOrDefault("TRANSIT_DAYS", "0"));
                        route.defaultRoute = row.getOrDefault("DEFAULT_ROUTE", "").trim();
                        // Map FREIGHT_ZONE to zoneSkipService (or use empty if not present)
                        route.zoneSkipService = row.getOrDefault("FREIGHT_ZONE", row.getOrDefault("ZONE_SKIP_SERVICE", "")).trim();

                        if (!route.postalCode.isEmpty() && !route.code.isEmpty()) {
                            routes.add(route);
                        }
                    }
                }
            }
        }

        return routes;
    }

    /**
     * Get carrier translations from database
     */
    private Map<String, CarrierInfo> getCarrierTranslations() throws Exception {
        Map<String, CarrierInfo> carrierMap = new HashMap<>();
        String sql = "SELECT CODE, CARRIER, SERVICE FROM dbo.t_carrier_translation";

        try (Connection conn = clsDataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {

            while (rs.next()) {
                String code = rs.getString("CODE");
                CarrierInfo info = new CarrierInfo();
                info.carrier = rs.getString("CARRIER");
                info.service = rs.getString("SERVICE");
                carrierMap.put(code, info);
            }
        }

        return carrierMap;
    }

    /**
     * Get origin for a shipper
     */
    private String getOriginForShipper(String shipper) throws Exception {
        String sql = "SELECT ORIGIN FROM dbo.ps_SHIPPER_ORIGIN WHERE SHIPPER = ?";

        try (Connection conn = clsDataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, shipper);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getString("ORIGIN");
                }
            }
        }

        return null;
    }

    /**
     * Check if shipper should be skipped
     */
    private boolean shouldSkipShipper(String shipper) throws Exception {
        String sql = "SELECT 1 FROM dbo.t_skip_shippers WHERE SHIPPER = ?";

        try (Connection conn = clsDataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, shipper);
            try (ResultSet rs = stmt.executeQuery()) {
                return rs.next();
            }
        }
    }

    /**
     * Read production data for a shipper
     */
    private List<ProductionRouteData> readProductionData(String shipper, String origin) throws Exception {
        List<ProductionRouteData> routes = new ArrayList<>();
        
        // Validate origin to prevent SQL injection (alphanumeric and underscore only)
        if (origin == null || !origin.matches("^[A-Za-z0-9_]+$")) {
            logger.warn("Invalid origin format: {}", origin);
            return routes;
        }
        
        // Build table name dynamically
        String tableName = "dbo.ps_PRIMARY_ROUTING_GUIDE_" + origin;
        
        // Verify table exists (check both dbo schema and current schema)
        String checkTableSql = "SELECT 1 FROM INFORMATION_SCHEMA.TABLES WHERE TABLE_SCHEMA = 'dbo' AND TABLE_NAME = ?";
        boolean tableExists = false;
        
        try (Connection conn = clsDataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(checkTableSql)) {
            
            stmt.setString(1, "ps_PRIMARY_ROUTING_GUIDE_" + origin);
            try (ResultSet rs = stmt.executeQuery()) {
                tableExists = rs.next();
            }
        }

        if (!tableExists) {
            logger.warn("Production table does not exist for origin: {}", origin);
            return routes;
        }

        // The production table is already origin-specific, so all rows belong to this origin/shipper
        // No need to filter by SHIPPER column (which doesn't exist in these tables)
        String sql = String.format(
            "SELECT CARRIER, POSTALCODE, TRANSIT_DAYS, DEFAULT_ROUTE, SERVICE " +
            "FROM %s",
            tableName
        );

        logger.debug("Querying production table {} for shipper {}", tableName, shipper);

        try (Connection conn = clsDataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {
            
            while (rs.next()) {
                ProductionRouteData route = new ProductionRouteData();
                route.carrier = rs.getString("CARRIER");
                route.postalCode = rs.getString("POSTALCODE");
                route.transitDays = rs.getFloat("TRANSIT_DAYS");
                route.defaultRoute = rs.getString("DEFAULT_ROUTE");
                route.service = rs.getString("SERVICE");
                routes.add(route);
            }
            
            logger.debug("Retrieved {} production routes from table {}", routes.size(), tableName);
        }

        return routes;
    }

    /**
     * Map SRM data to production format using carrier translations
     */
    private List<ProductionRouteData> mapSrmToProduction(
            List<SrmRouteData> srmData,
            Map<String, CarrierInfo> carrierMap) {
        
        List<ProductionRouteData> mapped = new ArrayList<>();
        
        for (SrmRouteData srm : srmData) {
            CarrierInfo carrierInfo = carrierMap.get(srm.code);
            if (carrierInfo == null) {
                logger.warn("No carrier translation found for CODE: {}", srm.code);
                continue;
            }

            ProductionRouteData prod = new ProductionRouteData();
            prod.carrier = carrierInfo.carrier;
            prod.postalCode = srm.postalCode;
            prod.transitDays = srm.transitDays;
            prod.defaultRoute = srm.defaultRoute;
            prod.service = carrierInfo.service;
            mapped.add(prod);
        }

        return mapped;
    }

    /**
     * Compare SRM data with production data and find differences
     */
    private List<RouteDifference> compareRoutes(
            List<ProductionRouteData> srmData,
            List<ProductionRouteData> productionData) {
        
        List<RouteDifference> differences = new ArrayList<>();

        // Create maps for quick lookup
        Map<String, ProductionRouteData> productionMap = new HashMap<>();
        for (ProductionRouteData prod : productionData) {
            String key = prod.carrier + "|" + prod.service + "|" + prod.postalCode;
            productionMap.put(key, prod);
        }

        Map<String, ProductionRouteData> srmMap = new HashMap<>();
        for (ProductionRouteData srm : srmData) {
            String key = srm.carrier + "|" + srm.service + "|" + srm.postalCode;
            srmMap.put(key, srm);
        }

        // Find NEW routes (in SRM but not in production)
        for (ProductionRouteData srm : srmData) {
            String key = srm.carrier + "|" + srm.service + "|" + srm.postalCode;
            if (!productionMap.containsKey(key)) {
                RouteDifference diff = new RouteDifference();
                diff.carrier = srm.carrier;
                diff.postalCode = srm.postalCode;
                diff.transitDays = srm.transitDays;
                diff.defaultRoute = srm.defaultRoute;
                diff.service = srm.service;
                diff.changeType = "NEW";
                diff.newValue = srm;
                differences.add(diff);
            }
        }

        // Find DELETED routes (in production but not in SRM)
        for (ProductionRouteData prod : productionData) {
            String key = prod.carrier + "|" + prod.service + "|" + prod.postalCode;
            if (!srmMap.containsKey(key)) {
                RouteDifference diff = new RouteDifference();
                diff.carrier = prod.carrier;
                diff.postalCode = prod.postalCode;
                diff.transitDays = prod.transitDays;
                diff.defaultRoute = prod.defaultRoute;
                diff.service = prod.service;
                diff.changeType = "DELETED";
                diff.oldValue = prod;
                differences.add(diff);
            }
        }

        // Find UPDATED routes (exist in both but values differ)
        for (ProductionRouteData srm : srmData) {
            String key = srm.carrier + "|" + srm.service + "|" + srm.postalCode;
            ProductionRouteData prod = productionMap.get(key);
            if (prod != null) {
                boolean transitDaysChanged = Math.abs(srm.transitDays - prod.transitDays) > 0.01;
                boolean defaultRouteChanged = !srm.defaultRoute.equals(prod.defaultRoute);

                if (transitDaysChanged || defaultRouteChanged) {
                    RouteDifference diff = new RouteDifference();
                    diff.carrier = srm.carrier;
                    diff.postalCode = srm.postalCode;
                    diff.transitDays = srm.transitDays;
                    diff.defaultRoute = srm.defaultRoute;
                    diff.service = srm.service;
                    diff.changeType = "UPDATED";
                    diff.oldValue = prod;
                    diff.newValue = srm;
                    differences.add(diff);
                }
            }
        }

        return differences;
    }

    /**
     * Group differences by DEFAULT_ROUTE and count postal codes
     */
    private List<Map<String, Object>> summarizeDifferences(List<RouteDifference> differences) {
        Map<String, Map<String, Object>> summaryMap = new HashMap<>();

        for (RouteDifference diff : differences) {
            String key = diff.defaultRoute + "|" + diff.carrier + "|" + diff.service;
            
            Map<String, Object> summary = summaryMap.computeIfAbsent(key, k -> {
                Map<String, Object> s = new HashMap<>();
                s.put("carrier", diff.carrier);
                s.put("defaultRoute", diff.defaultRoute);
                s.put("service", diff.service);
                s.put("postalCodeCount", 0);
                s.put("differences", new ArrayList<Map<String, Object>>());
                return s;
            });

            summary.put("postalCodeCount", (Integer) summary.get("postalCodeCount") + 1);
            
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> diffList = (List<Map<String, Object>>) summary.get("differences");
            diffList.add(convertDifferenceToMap(diff));
        }

        return new ArrayList<>(summaryMap.values());
    }

    /**
     * Convert RouteDifference to Map for JSON serialization
     * Note: Carrier is excluded as it's redundant
     */
    private Map<String, Object> convertDifferenceToMap(RouteDifference diff) {
        Map<String, Object> map = new HashMap<>();
        map.put("postalCode", diff.postalCode);
        map.put("transitDays", diff.transitDays);
        map.put("defaultRoute", diff.defaultRoute);
        map.put("service", diff.service);
        map.put("changeType", diff.changeType);
        
        if (diff.oldValue != null) {
            Map<String, Object> oldVal = new HashMap<>();
            oldVal.put("transitDays", diff.oldValue.transitDays);
            oldVal.put("defaultRoute", diff.oldValue.defaultRoute);
            map.put("oldValue", oldVal);
        }
        
        if (diff.newValue != null) {
            Map<String, Object> newVal = new HashMap<>();
            newVal.put("transitDays", diff.newValue.transitDays);
            newVal.put("defaultRoute", diff.newValue.defaultRoute);
            map.put("newValue", newVal);
        }
        
        return map;
    }

    /**
     * Group SRM data by shipper
     */
    private Map<String, List<SrmRouteData>> groupByShipper(List<SrmRouteData> routes) {
        Map<String, List<SrmRouteData>> grouped = new HashMap<>();
        for (SrmRouteData route : routes) {
            grouped.computeIfAbsent(route.shipper, k -> new ArrayList<>()).add(route);
        }
        return grouped;
    }

    /**
     * Parse CSV line
     */
    private List<String> parseCsvLine(String line) {
        List<String> fields = new ArrayList<>();
        boolean inQuotes = false;
        StringBuilder currentField = new StringBuilder();

        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);

            if (c == '"') {
                if (inQuotes && i + 1 < line.length() && line.charAt(i + 1) == '"') {
                    currentField.append('"');
                    i++;
                } else {
                    inQuotes = !inQuotes;
                }
            } else if (c == ',' && !inQuotes) {
                fields.add(currentField.toString().trim());
                currentField = new StringBuilder();
            } else {
                currentField.append(c);
            }
        }

        fields.add(currentField.toString().trim());
        return fields;
    }

    /**
     * Parse float with error handling
     */
    private float parseFloat(String value) {
        try {
            return Float.parseFloat(value);
        } catch (NumberFormatException e) {
            return 0.0f;
        }
    }

    // Data classes
    static class SrmRouteData {
        String shipper;
        String postalCode;
        String code;
        float transitDays;
        String defaultRoute;
        String zoneSkipService;
    }

    static class CarrierInfo {
        String carrier;
        String service;
    }

    static class ProductionRouteData {
        String carrier;
        String postalCode;
        float transitDays;
        String defaultRoute;
        String service;
    }

    static class RouteDifference {
        String carrier;
        String postalCode;
        float transitDays;
        String defaultRoute;
        String service;
        String changeType; // NEW, UPDATED, DELETED
        ProductionRouteData oldValue;
        ProductionRouteData newValue;
    }

}
