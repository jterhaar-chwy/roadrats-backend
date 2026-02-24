package com.roadrats.demo.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.sql.DataSource;
import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.*;
import java.util.LinkedHashMap;
import java.util.concurrent.TimeUnit;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import com.fasterxml.jackson.databind.ObjectMapper;

@Service
public class SrmFileService {

    private static final Logger logger = LoggerFactory.getLogger(SrmFileService.class);

    @Autowired
    @Qualifier("ioDataSource")
    private DataSource ioDataSource;

    @Value("${roadrats.scripts.path:${user.dir}/../scripts}")
    private String scriptsPath;

    @Value("${roadrats.srm.local.path:${user.dir}/SRM}")
    private String localSrmPath;

    @Value("${roadrats.srm.webservice.server:WMSAPP-IS.chewy.local}")
    private String webServiceServer;

    @Value("${roadrats.srm.remote.base.path:E:\\ProgramData\\Koerber\\IMPORTS\\CLSRouteFile}")
    private String remoteBasePath;

    @Value("${roadrats.srm.remote.staging.folder:StagedRouteFiles}")
    private String stagingFolder;

    /**
     * Get the local SRM path (for external access)
     */
    public String getLocalSrmPath() {
        return localSrmPath;
    }

    /**
     * Get route calendar versions from the SRM download history API.
     * Calls /v1/srm/ui/download/history/version/all — returns the top 20 versions.
     * Uses Java native HttpClient to avoid PowerShell escaping issues with Bearer tokens.
     */
    public Map<String, Object> getScheduledRouteCalendarVersion() {
        Map<String, Object> result = new HashMap<>();
        try {
            logger.info("Fetching route calendar versions from SRM API...");

            Map<String, String> srmInfo = getSrmWebServiceInfo();
            String token = srmInfo.get("token");
            String versionApiUrl = "https://srm-api.use1.scff.prd.aws.chewy.cloud/v1/srm/ui/download/history/version/all";

            logger.debug("Calling SRM version API: {}", versionApiUrl);

            // Force HTTP/1.1 — some API gateways 301 on HTTP/2
            // Manual redirect following to re-attach Authorization header
            HttpClient client = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .connectTimeout(java.time.Duration.ofSeconds(30))
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();

            String currentUrl = versionApiUrl;
            HttpResponse<String> response = null;
            int maxRedirects = 5;

            for (int redirectCount = 0; redirectCount <= maxRedirects; redirectCount++) {
                logger.info("SRM version API request #{} -> {}", redirectCount, currentUrl);

                HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(currentUrl))
                    .header("Authorization", "Bearer " + token)
                    .header("Accept", "*/*")
                    .header("User-Agent", "Mozilla/5.0 (Windows NT; Windows NT 10.0) PowerShell/7.0")
                    .GET()
                    .timeout(java.time.Duration.ofSeconds(30))
                    .build();

                response = client.send(request, HttpResponse.BodyHandlers.ofString());
                
                logger.info("SRM API response: status={}, headers={}", 
                    response.statusCode(), response.headers().map());

                if (response.statusCode() == 301 || response.statusCode() == 302 || response.statusCode() == 307 || response.statusCode() == 308) {
                    String location = response.headers().firstValue("location").orElse(
                        response.headers().firstValue("Location").orElse(null)
                    );
                    if (location == null || location.isEmpty()) {
                        throw new RuntimeException("SRM API returned HTTP " + response.statusCode() 
                            + " but no Location header. All headers: " + response.headers().map());
                    }
                    // Handle relative redirects
                    if (location.startsWith("/")) {
                        URI original = URI.create(currentUrl);
                        location = original.getScheme() + "://" + original.getHost() + location;
                    }
                    logger.info("Following redirect {} -> {}", response.statusCode(), location);
                    currentUrl = location;
                    continue;
                }
                break;
            }

            if (response.statusCode() != 200) {
                throw new RuntimeException("SRM API returned HTTP " + response.statusCode() + ": " + response.body());
            }

            String rawJson = response.body();
            if (rawJson == null || rawJson.trim().isEmpty()) {
                throw new RuntimeException("Empty response from SRM version API");
            }

            ObjectMapper mapper = new ObjectMapper();
            @SuppressWarnings("unchecked")
            Map<String, Object> parsed = mapper.readValue(rawJson.trim(), Map.class);

            // data is an array of version objects, each with { id, type, attributes }
            Object dataObj = parsed.get("data");
            if (dataObj instanceof java.util.List) {
                @SuppressWarnings("unchecked")
                java.util.List<Map<String, Object>> dataList = (java.util.List<Map<String, Object>>) dataObj;

                // Return top 20 versions
                int limit = Math.min(dataList.size(), 20);
                java.util.List<Map<String, Object>> versions = new java.util.ArrayList<>();

                for (int i = 0; i < limit; i++) {
                    Map<String, Object> item = dataList.get(i);
                    Map<String, Object> version = new HashMap<>();
                    version.put("id", item.get("id"));
                    version.put("type", item.get("type"));

                    @SuppressWarnings("unchecked")
                    Map<String, Object> attributes = (Map<String, Object>) item.get("attributes");
                    if (attributes != null) {
                        version.put("routeCalendarVersionId", attributes.get("routeCalendarVersionId"));
                        version.put("status", attributes.get("status"));
                        version.put("uploadTime", attributes.get("uploadTime"));
                        version.put("uploadUser", attributes.get("uploadUser"));
                        version.put("scheduledTime", attributes.get("scheduledTime"));
                        version.put("scheduleUser", attributes.get("scheduleUser"));
                        version.put("locked", attributes.get("locked"));
                    }
                    versions.add(version);
                }

                result.put("versions", versions);
                result.put("totalCount", dataList.size());

                // Also set the first SCHEDULED or most recent version as scheduledVersion
                for (Map<String, Object> v : versions) {
                    if ("SCHEDULED".equals(v.get("status"))) {
                        result.put("scheduledVersion", String.valueOf(v.get("id")));
                        break;
                    }
                }
                if (!result.containsKey("scheduledVersion") && !versions.isEmpty()) {
                    result.put("scheduledVersion", String.valueOf(versions.get(0).get("id")));
                }
            }

            result.put("success", true);
            logger.info("Retrieved {} versions, scheduled version: {}", 
                result.get("totalCount"), result.get("scheduledVersion"));
        } catch (Exception e) {
            logger.error("Error fetching route calendar versions", e);
            result.put("success", false);
            result.put("error", e.getMessage());
        }
        return result;
    }

    /**
     * Get delta-summary for a given route calendar version from the SRM API.
     * Calls /v1/srm/ui/delta-summary/getTables?versionId={versionId}
     */
    public Map<String, Object> getDeltaSummary(int versionId) {
        Map<String, Object> result = new HashMap<>();
        try {
            logger.info("Fetching delta summary for version {}...", versionId);

            Map<String, String> srmInfo = getSrmWebServiceInfo();
            String token = srmInfo.get("token");
            String apiUrl = "https://srm-api.use1.scff.prd.aws.chewy.cloud/v1/srm/ui/delta-summary/getTables?versionId=" + versionId;

            HttpClient client = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .connectTimeout(java.time.Duration.ofSeconds(30))
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();

            String currentUrl = apiUrl;
            HttpResponse<String> response = null;
            int maxRedirects = 5;

            for (int redirectCount = 0; redirectCount <= maxRedirects; redirectCount++) {
                logger.info("Delta summary API request #{} -> {}", redirectCount, currentUrl);

                HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(currentUrl))
                    .header("Authorization", "Bearer " + token)
                    .header("Accept", "*/*")
                    .header("User-Agent", "Mozilla/5.0 (Windows NT; Windows NT 10.0) PowerShell/7.0")
                    .GET()
                    .timeout(java.time.Duration.ofSeconds(30))
                    .build();

                response = client.send(request, HttpResponse.BodyHandlers.ofString());
                logger.info("Delta summary API response: status={}", response.statusCode());

                if (response.statusCode() == 301 || response.statusCode() == 302 || response.statusCode() == 307 || response.statusCode() == 308) {
                    String location = response.headers().firstValue("location").orElse(
                        response.headers().firstValue("Location").orElse(null)
                    );
                    if (location == null || location.isEmpty()) {
                        throw new RuntimeException("SRM API returned HTTP " + response.statusCode() + " with no Location header");
                    }
                    if (location.startsWith("/")) {
                        URI original = URI.create(currentUrl);
                        location = original.getScheme() + "://" + original.getHost() + location;
                    }
                    logger.info("Following redirect {} -> {}", response.statusCode(), location);
                    currentUrl = location;
                    continue;
                }
                break;
            }

            if (response.statusCode() != 200) {
                throw new RuntimeException("SRM API returned HTTP " + response.statusCode() + ": " + response.body());
            }

            String rawJson = response.body();
            if (rawJson == null || rawJson.trim().isEmpty()) {
                throw new RuntimeException("Empty response from delta summary API");
            }

            ObjectMapper mapper = new ObjectMapper();
            @SuppressWarnings("unchecked")
            Map<String, Object> parsed = mapper.readValue(rawJson.trim(), Map.class);

            // Build a summary from the raw delta data
            Map<String, Object> summary = new LinkedHashMap<>();
            int totalChanges = 0;
            int totalZips = 0;
            Map<String, Integer> changeTypeCounts = new LinkedHashMap<>();
            Map<String, Integer> fcCounts = new LinkedHashMap<>();

            for (Map.Entry<String, Object> entry : parsed.entrySet()) {
                if (entry.getValue() instanceof List) {
                    @SuppressWarnings("unchecked")
                    List<Map<String, Object>> changes = (List<Map<String, Object>>) entry.getValue();
                    totalChanges += changes.size();

                    for (Map<String, Object> change : changes) {
                        String changeType = (String) change.getOrDefault("changeType", "UNKNOWN");
                        changeTypeCounts.merge(changeType, 1, Integer::sum);

                        String fc = (String) change.getOrDefault("fulfillmentCenter", "");
                        if (fc != null && !fc.isEmpty()) {
                            fcCounts.merge(fc, 1, Integer::sum);
                        }

                        Object numZipsObj = change.get("numZips");
                        if (numZipsObj instanceof Number) {
                            totalZips += ((Number) numZipsObj).intValue();
                        }
                    }
                }
            }

            summary.put("totalChanges", totalChanges);
            summary.put("totalZipsAffected", totalZips);
            summary.put("changeTypeCounts", changeTypeCounts);
            summary.put("fulfillmentCenterCounts", fcCounts);

            result.put("success", true);
            result.put("versionId", versionId);
            result.put("raw", parsed);
            result.put("summary", summary);
            logger.info("Delta summary: {} changes, {} zips affected for version {}", totalChanges, totalZips, versionId);

        } catch (Exception e) {
            logger.error("Error fetching delta summary for version {}", versionId, e);
            result.put("success", false);
            result.put("error", e.getMessage());
        }
        return result;
    }

    /**
     * Get SRM version number from the scheduled route calendar API
     */
    public String getSrmVersion() {
        try {
            logger.info("Fetching SRM version number...");
            
            // Get SRM token and URL from database
            Map<String, String> srmInfo = getSrmWebServiceInfo();
            String token = srmInfo.get("token");
            String versionApiUrl = "https://srm-api.scff.prd.aws.chewy.cloud/v1/srm/routecalendar/version/scheduled";
            
            // Execute PowerShell to get version
            String psScript = String.format(
                "$Headers = @{\"Authorization\" = \"Bearer %s\"}; " +
                "$response = Invoke-WebRequest -Uri '%s' -Headers $Headers -Method GET -UseBasicParsing; " +
                "$raw = $response.RawContent; " +
                "$startIndex = $raw.IndexOf('{'); " +
                "$jsonText = $raw.Substring($startIndex); " +
                "$parsed = $jsonText | ConvertFrom-Json; " +
                "$parsed.data.attributes.routeCalendarVersionId",
                token, versionApiUrl
            );
            
            String version = executePowerShell(psScript);
            if (version != null && !version.trim().isEmpty()) {
                logger.info("SRM version retrieved: {}", version.trim());
                return version.trim();
            } else {
                throw new RuntimeException("Could not retrieve SRM version from API");
            }
        } catch (Exception e) {
            logger.error("Error getting SRM version", e);
            throw new RuntimeException("Failed to get SRM version: " + e.getMessage(), e);
        }
    }

    /**
     * Download all SRM files via PSSession to WMSAPP-IS.
     * Calls SRM-DownloadPrimer.ps1 locally, which opens a PSSession to WMSAPP-IS
     * and invokes SRM-DownloadParallel.ps1 remotely for parallel FC downloads.
     */
    public Map<String, Object> downloadSrmFiles(String versionNumber) {
        Map<String, Object> result = new HashMap<>();
        try {
            logger.info("Starting SRM file download for version: {} using SRM-DownloadPrimer.ps1", versionNumber);
            
            // Get the PowerShell script path
            Path scriptPath = Paths.get(scriptsPath, "SRM-DownloadPrimer.ps1");
            if (!Files.exists(scriptPath)) {
                throw new RuntimeException("PowerShell script not found: " + scriptPath);
            }
            
            // Build command to execute the PowerShell script
            // Use -File parameter for better script execution
            String scriptPathStr = scriptPath.toAbsolutePath().toString();
            String scriptCommand = String.format(
                "-File \"%s\" -VersionNumber \"%s\" -DestinationPath \"%s\"",
                scriptPathStr,
                versionNumber,
                localSrmPath
            );
            
            // Execute PowerShell script
            logger.info("Executing: powershell.exe {}", scriptCommand);
            String output = executePowerShellScript(scriptCommand);
            logger.info("PowerShell script raw output:\n{}", output);
            
            // Parse JSON output from script
            if (output == null || output.trim().isEmpty()) {
                throw new RuntimeException("PowerShell script returned no output. Script may not have executed. Path: " + scriptPathStr);
            }
            
            int jsonStart = output.indexOf("{");
            int jsonEnd = output.lastIndexOf("}") + 1;
            if (jsonStart >= 0 && jsonEnd > jsonStart) {
                String jsonOutput = output.substring(jsonStart, jsonEnd);
                logger.info("Parsed JSON from script: {}", jsonOutput);
                ObjectMapper mapper = new ObjectMapper();
                @SuppressWarnings("unchecked")
                Map<String, Object> scriptResult = mapper.readValue(jsonOutput, Map.class);
                
                if (Boolean.TRUE.equals(scriptResult.get("success"))) {
                    result.putAll(scriptResult);
                    result.put("message", "SRM files downloaded successfully");
                } else {
                    String scriptError = scriptResult.getOrDefault("error", "unknown error").toString();
                    String scriptDetails = scriptResult.getOrDefault("errorDetails", "").toString();
                    throw new RuntimeException("Script failed: " + scriptError + 
                        (scriptDetails.isEmpty() ? "" : " | Details: " + scriptDetails));
                }
            } else {
                // No JSON found — treat as failure and include raw output for debugging
                throw new RuntimeException("No JSON in script output. Raw output: " + output);
            }
            
        } catch (Exception e) {
            logger.error("Error downloading SRM files", e);
            result.put("success", false);
            result.put("error", e.getMessage());
            throw new RuntimeException("Failed to download SRM files: " + e.getMessage(), e);
        }
        
        return result;
    }

    /**
     * Copy SRM files from WMSSQL-IS StagedRouteFiles folder to localhost SRM folder and extract them
     * This is now handled by the PowerShell script, but we verify files are present
     * @param deleteExisting If true, delete all existing files in the directory before copying new ones
     */
    public Map<String, Object> copySrmFilesToLocal(boolean deleteExisting) {
        Map<String, Object> result = new HashMap<>();
        try {
            logger.info("Verifying SRM files in local directory: {}", localSrmPath);
            
            // Create local SRM directory if it doesn't exist
            Path localPath = Paths.get(localSrmPath);
            if (!Files.exists(localPath)) {
                Files.createDirectories(localPath);
                logger.info("Created local SRM directory: {}", localSrmPath);
            } else if (deleteExisting) {
                // Delete all existing files in the directory before copying new ones
                logger.info("Clearing existing files from local SRM directory: {}", localSrmPath);
                File localDir = new File(localSrmPath);
                File[] existingFiles = localDir.listFiles();
                if (existingFiles != null) {
                    int deletedCount = 0;
                    for (File file : existingFiles) {
                        try {
                            if (file.isDirectory()) {
                                deleteDirectory(file);
                            } else {
                                file.delete();
                            }
                            deletedCount++;
                        } catch (Exception e) {
                            logger.warn("Failed to delete file {}: {}", file.getName(), e.getMessage());
                        }
                    }
                    logger.info("Deleted {} existing files from local SRM directory", deletedCount);
                }
            }
            
            // Count files in local directory
            File localDir = new File(localSrmPath);
            File[] files = localDir.listFiles();
            int fileCount = files != null ? files.length : 0;
            
            // Count CSV files specifically
            File[] csvFiles = localDir.listFiles((dir, name) -> 
                name.toLowerCase().endsWith("_clsroute.csv") || 
                (name.toLowerCase().endsWith(".csv") && !name.toLowerCase().contains("_clsroute"))
            );
            int csvCount = csvFiles != null ? csvFiles.length : 0;
            
            result.put("success", true);
            result.put("message", "SRM files available in " + localSrmPath);
            result.put("fileCount", fileCount);
            result.put("csvFileCount", csvCount);
            result.put("localPath", localSrmPath);
            
        } catch (Exception e) {
            logger.error("Error verifying SRM files in local directory", e);
            result.put("success", false);
            result.put("error", e.getMessage());
            throw new RuntimeException("Failed to verify SRM files: " + e.getMessage(), e);
        }
        
        return result;
    }

    /**
     * Extract ZIP files and parse CSV route files
     */
    private void extractAndParseRouteFiles() {
        try {
            File localDir = new File(localSrmPath);
            if (!localDir.exists()) {
                return;
            }
            
            File[] zipFiles = localDir.listFiles((dir, name) -> name.toLowerCase().endsWith(".zip"));
            if (zipFiles == null) {
                return;
            }
            
            for (File zipFile : zipFiles) {
                try {
                    // Extract ZIP file
                    String routeName = zipFile.getName().replace(".zip", "");
                    String extractPath = new File(localDir, routeName).getAbsolutePath();
                    
                    // Use PowerShell to extract
                    String extractScript = String.format(
                        "Expand-Archive -Path '%s' -DestinationPath '%s' -Force",
                        zipFile.getAbsolutePath(), extractPath
                    );
                    executePowerShell(extractScript);
                    
                    // Find CSV file in extracted directory
                    File extractDir = new File(extractPath);
                    File[] csvFiles = extractDir.listFiles((dir, name) -> name.toLowerCase().endsWith(".csv"));
                    if (csvFiles != null && csvFiles.length > 0) {
                        // Copy CSV to main directory with route name
                        File csvFile = csvFiles[0];
                        File destCsv = new File(localDir, routeName + "_CLSRoute.csv");
                        Files.copy(csvFile.toPath(), destCsv.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                    }
                    
                    // Clean up extracted directory
                    deleteDirectory(extractDir);
                    
                } catch (Exception e) {
                    logger.warn("Error extracting ZIP file {}: {}", zipFile.getName(), e.getMessage());
                }
            }
        } catch (Exception e) {
            logger.error("Error extracting route files", e);
        }
    }

    /**
     * Clear all files from the local SRM directory
     */
    private void clearLocalSrmDirectory() {
        try {
            Path localPath = Paths.get(localSrmPath);
            if (Files.exists(localPath) && Files.isDirectory(localPath)) {
                logger.info("Clearing existing files from local SRM directory: {}", localSrmPath);
                File localDir = new File(localSrmPath);
                File[] existingFiles = localDir.listFiles();
                if (existingFiles != null) {
                    int deletedCount = 0;
                    for (File file : existingFiles) {
                        try {
                            if (file.isDirectory()) {
                                deleteDirectory(file);
                            } else {
                                file.delete();
                            }
                            deletedCount++;
                        } catch (Exception e) {
                            logger.warn("Failed to delete file {}: {}", file.getName(), e.getMessage());
                        }
                    }
                    logger.info("Deleted {} existing files from local SRM directory", deletedCount);
                }
            }
        } catch (Exception e) {
            logger.error("Error clearing local SRM directory", e);
            // Don't throw - allow process to continue even if cleanup fails
        }
    }

    /**
     * Delete directory recursively
     */
    private void deleteDirectory(File directory) {
        if (directory.exists()) {
            File[] files = directory.listFiles();
            if (files != null) {
                for (File file : files) {
                    if (file.isDirectory()) {
                        deleteDirectory(file);
                    } else {
                        file.delete();
                    }
                }
            }
            directory.delete();
        }
    }

    /**
     * Get list of available routes from local SRM directory
     */
    public List<Map<String, Object>> getRouteList() {
        List<Map<String, Object>> routes = new ArrayList<>();
        try {
            File localDir = new File(localSrmPath);
            if (!localDir.exists()) {
                return routes;
            }
            
            // Get all CSV files (extracted route files)
            File[] csvFiles = localDir.listFiles((dir, name) -> 
                name.toLowerCase().endsWith("_clsroute.csv") || 
                (name.toLowerCase().endsWith(".csv") && !name.toLowerCase().contains("_clsroute"))
            );
            
            if (csvFiles != null) {
                for (File csvFile : csvFiles) {
                    Map<String, Object> route = new HashMap<>();
                    String fileName = csvFile.getName();
                    String routeName = fileName.replace("_CLSRoute.csv", "").replace(".csv", "");
                    
                    route.put("routeName", routeName);
                    route.put("fileName", fileName);
                    route.put("fileSize", csvFile.length());
                    route.put("lastModified", new Date(csvFile.lastModified()).toString());
                    
                    // Count lines in CSV file
                    try (BufferedReader reader = new BufferedReader(new FileReader(csvFile))) {
                        long lineCount = reader.lines().count();
                        route.put("rowCount", lineCount - 1); // Subtract header
                    } catch (Exception e) {
                        route.put("rowCount", 0);
                    }
                    
                    routes.add(route);
                }
            }
            
            // Sort by route name
            routes.sort((a, b) -> ((String) a.get("routeName")).compareTo((String) b.get("routeName")));
            
        } catch (Exception e) {
            logger.error("Error getting route list", e);
        }
        
        return routes;
    }

    /**
     * Get contents of a specific route file
     */
    public Map<String, Object> getRouteFileContents(String routeName) {
        Map<String, Object> result = new HashMap<>();
        try {
            File localDir = new File(localSrmPath);
            File routeFile = null;
            
            // Try to find the CSV file
            File[] csvFiles = localDir.listFiles((dir, name) -> 
                name.equalsIgnoreCase(routeName + "_CLSRoute.csv") ||
                name.equalsIgnoreCase(routeName + ".csv") ||
                name.toLowerCase().startsWith(routeName.toLowerCase() + "_") && name.toLowerCase().endsWith(".csv")
            );
            
            if (csvFiles != null && csvFiles.length > 0) {
                routeFile = csvFiles[0];
            } else {
                // Try ZIP file
                File[] zipFiles = localDir.listFiles((dir, name) -> 
                    name.equalsIgnoreCase(routeName + ".zip")
                );
                if (zipFiles != null && zipFiles.length > 0) {
                    // Extract and read
                    String extractPath = new File(localDir, routeName + "_temp").getAbsolutePath();
                    String extractScript = String.format(
                        "Expand-Archive -Path '%s' -DestinationPath '%s' -Force",
                        zipFiles[0].getAbsolutePath(), extractPath
                    );
                    executePowerShell(extractScript);
                    
                    File extractDir = new File(extractPath);
                    File[] extractedCsv = extractDir.listFiles((dir, name) -> name.toLowerCase().endsWith(".csv"));
                    if (extractedCsv != null && extractedCsv.length > 0) {
                        routeFile = extractedCsv[0];
                    }
                }
            }
            
            if (routeFile == null || !routeFile.exists()) {
                result.put("error", "Route file not found: " + routeName);
                return result;
            }
            
            // Parse CSV file
            List<Map<String, String>> rows = new ArrayList<>();
            List<String> headers = new ArrayList<>();
            
            try (BufferedReader reader = new BufferedReader(new FileReader(routeFile))) {
                String line;
                boolean isFirstLine = true;
                
                while ((line = reader.readLine()) != null) {
                    if (isFirstLine) {
                        // Parse headers
                        headers = parseCsvLine(line);
                        isFirstLine = false;
                    } else {
                        // Parse data row
                        List<String> values = parseCsvLine(line);
                        Map<String, String> row = new HashMap<>();
                        for (int i = 0; i < headers.size() && i < values.size(); i++) {
                            row.put(headers.get(i), values.get(i));
                        }
                        rows.add(row);
                    }
                }
            }
            
            result.put("routeName", routeName);
            result.put("headers", headers);
            result.put("rows", rows);
            result.put("rowCount", rows.size());
            result.put("columnCount", headers.size());
            
        } catch (Exception e) {
            logger.error("Error reading route file contents", e);
            result.put("error", "Failed to read route file: " + e.getMessage());
        }
        
        return result;
    }

    /**
     * Parse a CSV line, handling quoted fields
     */
    private List<String> parseCsvLine(String line) {
        List<String> fields = new ArrayList<>();
        boolean inQuotes = false;
        StringBuilder currentField = new StringBuilder();
        
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            
            if (c == '"') {
                if (inQuotes && i + 1 < line.length() && line.charAt(i + 1) == '"') {
                    // Escaped quote
                    currentField.append('"');
                    i++;
                } else {
                    // Toggle quote state
                    inQuotes = !inQuotes;
                }
            } else if (c == ',' && !inQuotes) {
                // End of field
                fields.add(currentField.toString().trim());
                currentField = new StringBuilder();
            } else {
                currentField.append(c);
            }
        }
        
        // Add last field
        fields.add(currentField.toString().trim());
        
        return fields;
    }

    /**
     * Check if SRM files already exist in the local directory
     */
    public boolean hasExistingSrmFiles() {
        try {
            File localDir = new File(localSrmPath);
            if (!localDir.exists() || !localDir.isDirectory()) {
                return false;
            }
            
            // Check for CSV files ending with _CLSRoute.csv
            File[] csvFiles = localDir.listFiles((dir, name) -> 
                name.toLowerCase().endsWith("_clsroute.csv")
            );
            
            return csvFiles != null && csvFiles.length > 0;
        } catch (Exception e) {
            logger.error("Error checking for existing SRM files", e);
            return false;
        }
    }

    /**
     * Load existing SRM files from local directory without downloading
     */
    public Map<String, Object> loadExistingSrmFiles() {
        Map<String, Object> result = new HashMap<>();
        try {
            logger.info("Loading existing SRM files from local directory...");
            
            if (!hasExistingSrmFiles()) {
                result.put("success", false);
                result.put("error", "No existing SRM files found in local directory");
                return result;
            }
            
            // Verify files are present locally (don't delete existing when loading existing files)
            Map<String, Object> copyResult = copySrmFilesToLocal(false);
            
            result.put("version", "existing");
            result.put("download", Map.of(
                "success", true,
                "message", "Using existing SRM files",
                "skipped", true,
                "routeCount", copyResult.getOrDefault("csvFileCount", 0)
            ));
            result.put("copy", copyResult);
            result.put("success", true);
            result.put("message", "Existing SRM files loaded successfully");
            result.put("usedExistingFiles", true);
            
        } catch (Exception e) {
            logger.error("Error loading existing SRM files", e);
            result.put("success", false);
            result.put("error", e.getMessage());
        }
        
        return result;
    }

    /**
     * Execute the complete SRM download and copy process
     * Uses PowerShell script which handles download, copy, and extraction
     * Always downloads, overwriting any existing files
     */
    public Map<String, Object> executeSrmDownloadProcess(String versionNumber) {
        Map<String, Object> result = new HashMap<>();
        try {
            logger.info("Starting complete SRM download process...");
            
            // Step 1: Get version number (if not provided)
            String version = versionNumber;
            if (version == null || version.trim().isEmpty()) {
                version = getSrmVersion();
            }
            result.put("version", version);
            
            // Step 2: Delete existing files before downloading new ones
            clearLocalSrmDirectory();
            
            // Step 3: Execute PowerShell script (handles download, copy, and extraction)
            Map<String, Object> downloadResult = downloadSrmFiles(version);
            result.put("download", downloadResult);
            
            // Step 4: Verify files are present locally after download
            Map<String, Object> copyResult = copySrmFilesToLocal(false);
            result.put("copy", copyResult);
            
            result.put("success", true);
            result.put("message", "SRM download and copy process completed successfully");
            result.put("usedExistingFiles", false);
            
        } catch (Exception e) {
            logger.error("Error in SRM download process", e);
            result.put("success", false);
            result.put("error", e.getMessage());
        }
        
        return result;
    }

    /**
     * Get SRM web service info from database
     */
    private Map<String, String> getSrmWebServiceInfo() {
        Map<String, String> info = new HashMap<>();
        String sql = "SELECT token, http_url FROM dbo.t_webservice_info WHERE webservice_name = 'SRM Download'";
        
        try (Connection conn = ioDataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {
            
            if (rs.next()) {
                info.put("token", rs.getString("token"));
                info.put("url", rs.getString("http_url"));
            } else {
                throw new RuntimeException("Could not find SRM WebService data in database");
            }
        } catch (Exception e) {
            logger.error("Error getting SRM web service info", e);
            throw new RuntimeException("Failed to get SRM web service info: " + e.getMessage(), e);
        }
        
        return info;
    }

    /**
     * Get route file list from database
     */
    private String[] getRouteFileList() {
        String sql = "SELECT text AS route_file FROM dbo.t_lookup WHERE source = 'usp_cls_update_routes' AND lookup_type = 'ROUTE_UPLD'";
        
        try (Connection conn = ioDataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {
            
            java.util.List<String> routes = new java.util.ArrayList<>();
            while (rs.next()) {
                routes.add(rs.getString("route_file"));
            }
            
            if (routes.isEmpty()) {
                throw new RuntimeException("Could not find route file warehouse IDs in t_lookup");
            }
            
            return routes.toArray(new String[0]);
        } catch (Exception e) {
            logger.error("Error getting route file list", e);
            throw new RuntimeException("Failed to get route file list: " + e.getMessage(), e);
        }
    }

    /**
     * Execute PowerShell command
     */
    private String executePowerShell(String command) {
        try {
            ProcessBuilder pb = new ProcessBuilder(
                "powershell.exe",
                "-NoProfile",
                "-NonInteractive",
                "-Command",
                command
            );
            
            pb.redirectErrorStream(true);
            Process process = pb.start();
            
            StringBuilder output = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    output.append(line).append("\n");
                }
            }
            
            boolean finished = process.waitFor(5, TimeUnit.MINUTES);
            if (!finished) {
                process.destroyForcibly();
                throw new RuntimeException("PowerShell command timed out");
            }
            
            int exitCode = process.exitValue();
            if (exitCode != 0) {
                logger.warn("PowerShell command exited with code: {}", exitCode);
            }
            
            return output.toString();
        } catch (Exception e) {
            logger.error("Error executing PowerShell command", e);
            throw new RuntimeException("Failed to execute PowerShell command: " + e.getMessage(), e);
        }
    }

    /**
     * Execute PowerShell script file
     */
    private String executePowerShellScript(String arguments) {
        try {
            List<String> command = new ArrayList<>();
            command.add("powershell.exe");
            command.add("-NoProfile");
            command.add("-NonInteractive");
            command.add("-ExecutionPolicy");
            command.add("Bypass");
            
            // Parse arguments - split by spaces but preserve quoted strings
            List<String> args = parseArguments(arguments);
            command.addAll(args);
            
            ProcessBuilder pb = new ProcessBuilder(command);
            pb.redirectErrorStream(true);
            Process process = pb.start();
            
            StringBuilder output = new StringBuilder();
            StringBuilder errorOutput = new StringBuilder();
            
            // Read stdout
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    output.append(line).append("\n");
                    // Also capture error-like output
                    if (line.toLowerCase().contains("error") || 
                        line.toLowerCase().contains("exception") ||
                        line.toLowerCase().contains("failed")) {
                        errorOutput.append(line).append("\n");
                    }
                }
            }
            
            // Wait for process with longer timeout (10 minutes for full download)
            boolean finished = process.waitFor(10, TimeUnit.MINUTES);
            if (!finished) {
                process.destroyForcibly();
                throw new RuntimeException("PowerShell script timed out after 10 minutes");
            }
            
            int exitCode = process.exitValue();
            if (exitCode != 0) {
                String errorMsg = errorOutput.length() > 0 ? errorOutput.toString() : output.toString();
                logger.error("PowerShell script exited with code: {}. Output: {}", exitCode, errorMsg);
                throw new RuntimeException("PowerShell script failed with exit code " + exitCode + ": " + errorMsg);
            }
            
            return output.toString();
        } catch (Exception e) {
            logger.error("Error executing PowerShell script", e);
            throw new RuntimeException("Failed to execute PowerShell script: " + e.getMessage(), e);
        }
    }

    /**
     * Parse command line arguments, handling quoted strings
     */
    private List<String> parseArguments(String arguments) {
        List<String> result = new ArrayList<>();
        if (arguments == null || arguments.trim().isEmpty()) {
            return result;
        }
        
        boolean inQuotes = false;
        StringBuilder current = new StringBuilder();
        
        for (int i = 0; i < arguments.length(); i++) {
            char c = arguments.charAt(i);
            
            if (c == '"') {
                inQuotes = !inQuotes;
            } else if (c == ' ' && !inQuotes) {
                if (current.length() > 0) {
                    result.add(current.toString());
                    current = new StringBuilder();
                }
            } else {
                current.append(c);
            }
        }
        
        if (current.length() > 0) {
            result.add(current.toString());
        }
        
        return result;
    }
}
