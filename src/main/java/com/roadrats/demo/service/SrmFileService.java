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
import java.util.*;
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
     * Download all SRM files to WMSSQL-IS StagedRouteFiles folder via PowerShell script
     * Calls DownloadSRMRouteData-Backend.ps1 which matches the working standalone script
     */
    public Map<String, Object> downloadSrmFiles(String versionNumber) {
        Map<String, Object> result = new HashMap<>();
        try {
            logger.info("Starting SRM file download for version: {} using PowerShell script", versionNumber);
            
            // Get the PowerShell script path
            Path scriptPath = Paths.get(scriptsPath, "DownloadSRMRouteData-Backend.ps1");
            if (!Files.exists(scriptPath)) {
                throw new RuntimeException("PowerShell script not found: " + scriptPath);
            }
            
            // Build command to execute the PowerShell script
            // Use -File parameter for better script execution
            String scriptPathStr = scriptPath.toAbsolutePath().toString();
            String scriptCommand = String.format(
                "-File \"%s\" -VersionNumber \"%s\" -LocalDestinationPath \"%s\"",
                scriptPathStr,
                versionNumber,
                localSrmPath
            );
            
            // Execute PowerShell script
            String output = executePowerShellScript(scriptCommand);
            logger.info("PowerShell script output: {}", output);
            
            // Parse JSON output from script
            try {
                // Find JSON in output (may have other text before/after)
                int jsonStart = output.indexOf("{");
                int jsonEnd = output.lastIndexOf("}") + 1;
                if (jsonStart >= 0 && jsonEnd > jsonStart) {
                    String jsonOutput = output.substring(jsonStart, jsonEnd);
                    ObjectMapper mapper = new ObjectMapper();
                    @SuppressWarnings("unchecked")
                    Map<String, Object> scriptResult = mapper.readValue(jsonOutput, Map.class);
                    
                    if (Boolean.TRUE.equals(scriptResult.get("success"))) {
                        result.putAll(scriptResult);
                        result.put("message", "SRM files downloaded successfully");
                    } else {
                        throw new RuntimeException("PowerShell script failed: " + scriptResult.get("error"));
                    }
                } else {
                    // If no JSON found, check for error indicators
                    if (output.toLowerCase().contains("error") || output.toLowerCase().contains("failed")) {
                        throw new RuntimeException("PowerShell script may have failed. Output: " + output);
                    }
                    // Assume success if no errors
                    result.put("success", true);
                    result.put("version", versionNumber);
                    result.put("message", "SRM files downloaded");
                }
            } catch (Exception e) {
                logger.warn("Could not parse JSON output: {}", e.getMessage());
                // Check if it's a JSON parsing error or actual failure
                if (output.toLowerCase().contains("error") || output.toLowerCase().contains("failed")) {
                    throw new RuntimeException("PowerShell script failed. Output: " + output, e);
                }
                result.put("success", true);
                result.put("version", versionNumber);
                result.put("message", "SRM files downloaded (unable to parse detailed output)");
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
     */
    public Map<String, Object> copySrmFilesToLocal() {
        Map<String, Object> result = new HashMap<>();
        try {
            logger.info("Verifying SRM files in local directory: {}", localSrmPath);
            
            // Create local SRM directory if it doesn't exist
            Path localPath = Paths.get(localSrmPath);
            if (!Files.exists(localPath)) {
                Files.createDirectories(localPath);
                logger.info("Created local SRM directory: {}", localSrmPath);
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
     * Execute the complete SRM download and copy process
     * Uses PowerShell script which handles download, copy, and extraction
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
            
            // Step 2: Execute PowerShell script (handles download, copy, and extraction)
            Map<String, Object> downloadResult = downloadSrmFiles(version);
            result.put("download", downloadResult);
            
            // Step 3: Verify files are present locally
            Map<String, Object> copyResult = copySrmFilesToLocal();
            result.put("copy", copyResult);
            
            result.put("success", true);
            result.put("message", "SRM download and copy process completed successfully");
            
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
