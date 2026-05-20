package com.roadrats.demo.service.releasemanager;

import com.roadrats.demo.config.ReleaseManagerConfig;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Service
public class DeploymentFolderService {

    private static final int LOG_TAIL_MAX_BYTES = 256 * 1024;
    private static final int LOG_FILES_SUMMARIZE_MAX = 6;
    private static final int LOG_ERROR_SAMPLES_PER_FILE = 10;
    private static final int LOG_FILES_FIND_MAX = 28;
    private static final long LOG_READ_FULL_MAX_BYTES = 2_000_000;

    /**
     * Lines that look like failures (PowerShell, GitHub Actions, generic logs).
     */
    private static final Pattern LOG_ERROR_LINE = Pattern.compile(
        "(?i).*(\\b(error|exception|failed|fatal)\\b|##\\[error\\]|\\[err\\]|terminating\\s+error|"
            + "fullyqualifiederrorid|traceback|unhandled\\s+exception).*");

    private final ReleaseManagerConfig config;

    public DeploymentFolderService(ReleaseManagerConfig config) {
        this.config = config;
    }

    /**
     * List year folders under the deployments root.
     * e.g. \\chewy\dbs\WMS\Deployments\ -> [2024, 2025, 2026]
     */
    public List<String> listYears() throws IOException {
        Path root = getDeploymentsRoot();
        try (Stream<Path> dirs = Files.list(root)) {
            return dirs
                .filter(Files::isDirectory)
                .map(p -> p.getFileName().toString())
                .sorted(Comparator.reverseOrder())
                .collect(Collectors.toList());
        }
    }

    /**
     * List month folders under a year.
     * e.g. 2026 -> [02-FEB, 01-JAN]
     */
    public List<String> listMonths(String year) throws IOException {
        Path yearDir = getDeploymentsRoot().resolve(year);
        validatePath(yearDir);
        try (Stream<Path> dirs = Files.list(yearDir)) {
            return dirs
                .filter(Files::isDirectory)
                .map(p -> p.getFileName().toString())
                .sorted(Comparator.reverseOrder())
                .collect(Collectors.toList());
        }
    }

    /**
     * List release/CHG folders under a month.
     * e.g. 2026/02-FEB -> [release-03-05, release-02-19, CHG0261540, ...]
     */
    public List<Map<String, Object>> listReleases(String year, String month) throws IOException {
        Path monthDir = getDeploymentsRoot().resolve(year).resolve(month);
        validatePath(monthDir);
        List<Map<String, Object>> results = new ArrayList<>();
        try (Stream<Path> dirs = Files.list(monthDir)) {
            List<Path> folders = dirs.filter(Files::isDirectory)
                .sorted(Comparator.comparing(p -> p.getFileName().toString(), Comparator.reverseOrder()))
                .collect(Collectors.toList());

            for (Path folder : folders) {
                Map<String, Object> entry = new LinkedHashMap<>();
                String name = folder.getFileName().toString();
                entry.put("name", name);
                String type = "other";
                if (name.startsWith("release-")) {
                    type = "release";
                } else if (isChgFolderName(name)) {
                    type = "chg";
                } else if (isRollingFolderName(name)) {
                    type = "rolling";
                }
                entry.put("type", type);
                entry.put("path", year + "/" + month + "/" + name);

                try {
                    long childCount = Files.list(folder).count();
                    entry.put("childCount", childCount);
                } catch (IOException e) {
                    entry.put("childCount", -1);
                }

                results.add(entry);
            }
        }
        return results;
    }

    /**
     * Get the full contents of a release or CHG folder, including file listings
     * and CHG subfolder summaries.
     */
    public Map<String, Object> getFolderContents(String relativePath) throws IOException {
        Path folder = getDeploymentsRoot().resolve(relativePath.replace("/", FileSystems.getDefault().getSeparator()));
        validatePath(folder);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("path", relativePath);
        result.put("name", folder.getFileName().toString());
        result.put("fullPath", folder.toString());

        List<Map<String, Object>> children = new ArrayList<>();
        List<Map<String, Object>> files = new ArrayList<>();

        try (Stream<Path> entries = Files.list(folder)) {
            List<Path> sorted = entries
                .sorted(Comparator.comparing(p -> p.getFileName().toString()))
                .collect(Collectors.toList());

            for (Path entry : sorted) {
                if (Files.isDirectory(entry)) {
                    Map<String, Object> child = new LinkedHashMap<>();
                    String name = entry.getFileName().toString();
                    child.put("name", name);
                    if (isChgFolderName(name)) {
                        child.put("type", "chg");
                    } else if (isRollingFolderName(name)) {
                        child.put("type", "rolling");
                    } else {
                        child.put("type", "folder");
                    }
                    child.put("path", relativePath + "/" + name);

                    if (isArtifactPackageFolderName(name)) {
                        child.put("summary", buildChgSummary(entry));
                    } else {
                        try {
                            child.put("fileCount", Files.list(entry).count());
                        } catch (IOException e) {
                            child.put("fileCount", -1);
                        }
                    }
                    children.add(child);
                } else {
                    files.add(buildFileInfo(entry, relativePath));
                }
            }
        }

        result.put("folders", children);
        result.put("files", files);
        result.put("chgCount", children.stream()
            .filter(c -> "chg".equals(c.get("type")) || "rolling".equals(c.get("type")))
            .count());
        result.put("totalFiles", files.size());

        String folderName = folder.getFileName().toString();
        boolean artifactPackage = isArtifactPackageFolderName(folderName);
        result.put("isChg", isChgFolderName(folderName));
        result.put("isArtifactPackageFolder", artifactPackage);
        if (artifactPackage) {
            result.put("chgSummary", buildChgSummary(folder));
        }

        result.put("deploymentLogs", buildDeploymentLogsSection(relativePath));

        return result;
    }

    /**
     * Build a summary of a CHG deployment folder, detecting artifact types,
     * logs, scripts, and data files.
     */
    public Map<String, Object> buildChgSummary(Path chgDir) throws IOException {
        Map<String, Object> summary = new LinkedHashMap<>();
        String chgName = chgDir.getFileName().toString();
        summary.put("chgNumber", chgName);

        List<Map<String, Object>> allFiles = new ArrayList<>();
        Map<String, List<String>> categorized = new LinkedHashMap<>();

        categorized.put("scripts", new ArrayList<>());
        categorized.put("logs", new ArrayList<>());
        categorized.put("data", new ArrayList<>());
        categorized.put("sql", new ArrayList<>());
        categorized.put("config", new ArrayList<>());
        categorized.put("other", new ArrayList<>());

        Set<String> artifactTypes = new LinkedHashSet<>();

        try (Stream<Path> walk = Files.walk(chgDir, 3)) {
            List<Path> fileList = walk.filter(Files::isRegularFile).collect(Collectors.toList());

            for (Path file : fileList) {
                String fileName = file.getFileName().toString().toLowerCase();
                String relativeName = chgDir.relativize(file).toString();
                Map<String, Object> fileInfo = new LinkedHashMap<>();
                fileInfo.put("name", relativeName);
                try {
                    fileInfo.put("size", Files.size(file));
                    fileInfo.put("modified", Files.getLastModifiedTime(file).toMillis());
                } catch (IOException e) {
                    fileInfo.put("size", -1);
                }
                allFiles.add(fileInfo);

                // Detect artifact types from directory structure
                String parentDir = file.getParent().getFileName().toString().toLowerCase();
                if (parentDir.equals("ddl") || relativeName.toLowerCase().contains("ddl")) artifactTypes.add("DDL");
                if (parentDir.equals("dml") || relativeName.toLowerCase().contains("dml")) artifactTypes.add("DML");
                if (parentDir.equals("web") || relativeName.toLowerCase().contains("web")) artifactTypes.add("Web");
                if (parentDir.equals("architect") || relativeName.toLowerCase().contains("architect")) artifactTypes.add("Architect");
                if (parentDir.equals("gateway") || relativeName.toLowerCase().contains("gateway")) artifactTypes.add("Gateway");
                if (parentDir.equals("fitnesse") || relativeName.toLowerCase().contains("fitnesse")) artifactTypes.add("Fitnesse");

                // Categorize by extension
                if (fileName.endsWith(".ps1") || fileName.endsWith(".psm1") || fileName.endsWith(".bat") || fileName.endsWith(".cmd")) {
                    categorized.get("scripts").add(relativeName);
                } else if (fileName.endsWith(".log") || fileName.endsWith(".txt")) {
                    categorized.get("logs").add(relativeName);
                } else if (fileName.endsWith(".xml") || fileName.endsWith(".json") || fileName.endsWith(".csv")) {
                    categorized.get("data").add(relativeName);
                } else if (fileName.endsWith(".sql")) {
                    categorized.get("sql").add(relativeName);
                } else if (fileName.endsWith(".config") || fileName.endsWith(".yml") || fileName.endsWith(".yaml")) {
                    categorized.get("config").add(relativeName);
                } else {
                    categorized.get("other").add(relativeName);
                }
            }
        }

        summary.put("artifactTypes", new ArrayList<>(artifactTypes));
        summary.put("fileCount", allFiles.size());
        summary.put("files", allFiles);
        summary.put("categorized", categorized);

        // Check for deploy/rollback scripts
        boolean hasDeployScripts = allFiles.stream()
            .anyMatch(f -> f.get("name").toString().toLowerCase().contains("deploy"));
        boolean hasRollbackScripts = allFiles.stream()
            .anyMatch(f -> f.get("name").toString().toLowerCase().contains("rollback"));
        summary.put("hasDeployScripts", hasDeployScripts);
        summary.put("hasRollbackScripts", hasRollbackScripts);

        return summary;
    }

    /**
     * Read a specific file's content (text files only, with size limit).
     */
    public Map<String, Object> readFileContent(String relativePath) throws IOException {
        Path file = getDeploymentsRoot().resolve(relativePath.replace("/", FileSystems.getDefault().getSeparator()));
        validatePath(file);

        if (!Files.isRegularFile(file)) {
            throw new IllegalArgumentException("Not a regular file: " + relativePath);
        }

        long size = Files.size(file);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("path", relativePath);
        result.put("name", file.getFileName().toString());
        result.put("size", size);

        // Limit reading to 500KB for safety
        if (size > 512_000) {
            result.put("truncated", true);
            result.put("content", new String(Files.readAllBytes(file), 0, 512_000));
        } else {
            result.put("truncated", false);
            result.put("content", Files.readString(file));
        }

        return result;
    }

    /**
     * Summarize related pipeline logs under {@code roadrats.releases.logs-path}.
     * Matches mirror path first (same relative path as deployments), then searches by folder name.
     */
    public Map<String, Object> buildDeploymentLogsSection(String deploymentRelativePath) {
        Map<String, Object> section = new LinkedHashMap<>();
        String logsPathStr = config.getLogsPath();
        if (logsPathStr == null || logsPathStr.isBlank()) {
            section.put("configured", false);
            section.put("message", "Set roadrats.releases.logs-path to enable log hints.");
            return section;
        }
        Path logsRoot = Paths.get(logsPathStr);
        section.put("configured", true);
        section.put("logsRoot", logsRoot.toString());

        if (!Files.isDirectory(logsRoot)) {
            section.put("available", false);
            section.put("message", "Logs path is not reachable or not a directory from this host.");
            return section;
        }

        try {
            List<Path> logFiles = findRelatedLogFiles(logsRoot, deploymentRelativePath);
            section.put("available", true);
            section.put("fileCount", logFiles.size());
            section.put("matchMode", logFiles.isEmpty() ? "none" : guessMatchMode(logsRoot, deploymentRelativePath, logFiles.get(0)));

            List<Map<String, Object>> summaries = new ArrayList<>();
            int n = Math.min(logFiles.size(), LOG_FILES_SUMMARIZE_MAX);
            for (int i = 0; i < n; i++) {
                summaries.add(summarizeLogFile(logFiles.get(i), logsRoot));
            }
            section.put("files", summaries);

            if (logFiles.size() > n) {
                List<Map<String, Object>> extra = new ArrayList<>();
                for (int i = n; i < logFiles.size(); i++) {
                    Path p = logFiles.get(i);
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("relativePath", relativizeToLogsRoot(p, logsRoot));
                    m.put("name", p.getFileName().toString());
                    m.put("size", safeSize(p));
                    m.put("modified", safeModified(p));
                    m.put("summarized", false);
                    extra.add(m);
                }
                section.put("additionalFiles", extra);
            }
        } catch (Exception e) {
            section.put("available", false);
            section.put("message", "Could not read logs: " + e.getMessage());
        }
        return section;
    }

    /**
     * Read a log file under the configured logs root (path relative to logs root, forward slashes).
     */
    public Map<String, Object> readLogFileContent(String relativeToLogsRoot) throws IOException {
        String logsPathStr = config.getLogsPath();
        if (logsPathStr == null || logsPathStr.isBlank()) {
            throw new IllegalStateException("Logs path not configured");
        }
        Path logsRoot = Paths.get(logsPathStr).toAbsolutePath().normalize();
        if (!Files.isDirectory(logsRoot)) {
            throw new NoSuchFileException(logsRoot.toString());
        }
        Path file = resolveUnderLogsRoot(logsRoot, relativeToLogsRoot);
        if (!Files.isRegularFile(file)) {
            throw new IllegalArgumentException("Not a regular file: " + relativeToLogsRoot);
        }
        long size = Files.size(file);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("path", relativizeToLogsRoot(file, logsRoot));
        result.put("name", file.getFileName().toString());
        result.put("size", size);
        int cap = 768_000;
        if (size > cap) {
            result.put("truncated", true);
            result.put("content", readTailUtf8(file, cap));
            result.put("note", "Showing last " + cap + " bytes; file is larger.");
        } else {
            result.put("truncated", false);
            result.put("content", Files.readString(file, StandardCharsets.UTF_8));
        }
        return result;
    }

    private static String guessMatchMode(Path logsRoot, String deploymentRelativePath, Path firstHit) {
        Path mirror = mirrorLogsPath(logsRoot, deploymentRelativePath);
        if (!Files.isDirectory(mirror)) {
            return "search";
        }
        Path m = mirror.toAbsolutePath().normalize();
        Path f = firstHit.toAbsolutePath().normalize();
        return f.startsWith(m) ? "mirror" : "search";
    }

    private static Path mirrorLogsPath(Path logsRoot, String deploymentRelativePath) {
        Path p = logsRoot;
        for (String part : splitPathParts(deploymentRelativePath)) {
            p = p.resolve(part);
        }
        return p;
    }

    private static List<String> splitPathParts(String relative) {
        String n = relative.replace('\\', '/').trim();
        if (n.startsWith("/")) {
            n = n.substring(1);
        }
        if (n.isEmpty()) {
            return List.of();
        }
        List<String> out = new ArrayList<>();
        for (String part : n.split("/")) {
            if (!part.isEmpty() && !".".equals(part)) {
                if ("..".equals(part)) {
                    throw new IllegalArgumentException("Invalid path");
                }
                out.add(part);
            }
        }
        return out;
    }

    private List<Path> findRelatedLogFiles(Path logsRoot, String deploymentRelativePath) throws IOException {
        Path mirror = mirrorLogsPath(logsRoot, deploymentRelativePath);
        List<Path> fromMirror = new ArrayList<>();
        if (Files.isDirectory(mirror)) {
            try (Stream<Path> walk = Files.walk(mirror, 4)) {
                walk.filter(Files::isRegularFile)
                    .filter(DeploymentFolderService::isLogExtension)
                    .forEach(fromMirror::add);
            }
        }
        if (!fromMirror.isEmpty()) {
            return sortByModifiedDesc(fromMirror);
        }

        List<String> parts = splitPathParts(deploymentRelativePath);
        if (parts.isEmpty()) {
            return List.of();
        }
        String leaf = parts.get(parts.size() - 1);
        String leafLc = leaf.toLowerCase(Locale.ROOT);
        List<Path> found = new ArrayList<>();
        try (Stream<Path> walk = Files.walk(logsRoot, 12)) {
            walk.filter(Files::isRegularFile)
                .filter(DeploymentFolderService::isLogExtension)
                .filter(p -> p.toString().toLowerCase(Locale.ROOT).contains(leafLc))
                .limit(LOG_FILES_FIND_MAX * 2L)
                .forEach(found::add);
        }
        return sortByModifiedDesc(found).stream().limit(LOG_FILES_FIND_MAX).collect(Collectors.toList());
    }

    private static boolean isLogExtension(Path p) {
        String n = p.getFileName().toString().toLowerCase(Locale.ROOT);
        return n.endsWith(".log") || n.endsWith(".txt");
    }

    private static List<Path> sortByModifiedDesc(List<Path> paths) {
        return paths.stream()
            .sorted(Comparator.comparingLong(DeploymentFolderService::safeModified).reversed())
            .collect(Collectors.toList());
    }

    private Map<String, Object> summarizeLogFile(Path file, Path logsRoot) throws IOException {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("relativePath", relativizeToLogsRoot(file, logsRoot));
        m.put("name", file.getFileName().toString());
        m.put("size", safeSize(file));
        m.put("modified", safeModified(file));
        m.put("summarized", true);

        long size = Files.size(file);
        String text;
        int tailBytes;
        if (size <= LOG_READ_FULL_MAX_BYTES) {
            text = Files.readString(file, StandardCharsets.UTF_8);
            tailBytes = (int) Math.min(size, Integer.MAX_VALUE);
            m.put("scanMode", "full");
        } else {
            text = readTailUtf8(file, LOG_TAIL_MAX_BYTES);
            tailBytes = LOG_TAIL_MAX_BYTES;
            m.put("scanMode", "tail");
        }
        m.put("tailScannedBytes", tailBytes);

        List<Map<String, Object>> errors = extractErrorSamples(text);
        m.put("errorSamples", errors);
        m.put("errorSampleCount", errors.size());
        long totalErr = countErrorLines(text);
        m.put("errorLineCountInScan", totalErr);
        return m;
    }

    private static long countErrorLines(String text) {
        long c = 0;
        try (BufferedReader br = new BufferedReader(new StringReader(text))) {
            String line;
            while ((line = br.readLine()) != null) {
                if (looksLikeErrorLine(line)) {
                    c++;
                }
            }
        } catch (IOException ignored) {
            return c;
        }
        return c;
    }

    private static List<Map<String, Object>> extractErrorSamples(String text) {
        List<Map<String, Object>> samples = new ArrayList<>();
        int lineNo = 0;
        try (BufferedReader br = new BufferedReader(new StringReader(text))) {
            String line;
            while ((line = br.readLine()) != null) {
                lineNo++;
                if (looksLikeErrorLine(line)) {
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("line", lineNo);
                    row.put("text", truncateLine(line, 280));
                    samples.add(row);
                    if (samples.size() >= LOG_ERROR_SAMPLES_PER_FILE) {
                        break;
                    }
                }
            }
        } catch (IOException ignored) {
            // StringReader does not throw
        }
        return samples;
    }

    private static boolean looksLikeErrorLine(String line) {
        if (line == null || line.isBlank()) {
            return false;
        }
        String t = line.trim();
        if (t.length() < 6) {
            return false;
        }
        return LOG_ERROR_LINE.matcher(t).matches();
    }

    private static String truncateLine(String line, int max) {
        String t = line.trim();
        return t.length() <= max ? t : t.substring(0, max) + "...";
    }

    private static String readTailUtf8(Path file, int maxBytes) throws IOException {
        long len = Files.size(file);
        if (len <= maxBytes) {
            return Files.readString(file, StandardCharsets.UTF_8);
        }
        try (RandomAccessFile raf = new RandomAccessFile(file.toFile(), "r")) {
            raf.seek(len - maxBytes);
            byte[] buf = new byte[maxBytes];
            int read = raf.read(buf);
            if (read <= 0) {
                return "";
            }
            int start = 0;
            while (start < read && buf[start] != '\n') {
                start++;
            }
            if (start < read) {
                start++;
            }
            return new String(buf, start, read - start, StandardCharsets.UTF_8);
        }
    }

    private static String relativizeToLogsRoot(Path file, Path logsRoot) {
        Path rel = logsRoot.relativize(file.toAbsolutePath().normalize());
        return rel.toString().replace('\\', '/');
    }

    private Path resolveUnderLogsRoot(Path logsRoot, String relativeToLogsRoot) throws IOException {
        Path logsAbs = logsRoot.toAbsolutePath().normalize();
        Path resolved = logsAbs;
        for (String part : splitPathParts(relativeToLogsRoot)) {
            resolved = resolved.resolve(part);
        }
        Path norm = resolved.normalize().toAbsolutePath();
        if (!norm.startsWith(logsAbs)) {
            throw new IllegalArgumentException("Path escapes logs root");
        }
        return norm;
    }

    private static long safeModified(Path p) {
        try {
            return Files.getLastModifiedTime(p).toMillis();
        } catch (IOException e) {
            return 0L;
        }
    }

    private static long safeSize(Path p) {
        try {
            return Files.size(p);
        } catch (IOException e) {
            return -1L;
        }
    }

    private Map<String, Object> buildFileInfo(Path file, String parentRelPath) {
        Map<String, Object> info = new LinkedHashMap<>();
        String name = file.getFileName().toString();
        info.put("name", name);
        info.put("path", parentRelPath + "/" + name);

        String ext = name.contains(".") ? name.substring(name.lastIndexOf('.') + 1).toLowerCase() : "";
        info.put("extension", ext);

        try {
            info.put("size", Files.size(file));
            info.put("modified", Files.getLastModifiedTime(file).toMillis());
        } catch (IOException e) {
            info.put("size", -1);
        }

        return info;
    }

    private static boolean isChgFolderName(String name) {
        return name.startsWith("CHG");
    }

    private static boolean isRollingFolderName(String name) {
        return name.length() >= 7 && name.substring(0, 7).equalsIgnoreCase("rolling");
    }

    private static boolean isArtifactPackageFolderName(String name) {
        return isChgFolderName(name) || isRollingFolderName(name);
    }

    private Path getDeploymentsRoot() {
        String deploymentsPath = config.getDeploymentsPath();
        if (deploymentsPath == null || deploymentsPath.isEmpty()) {
            throw new IllegalStateException("Deployments path not configured. Set roadrats.releases.deployments-path in application.properties");
        }
        return Paths.get(deploymentsPath);
    }

    private void validatePath(Path path) throws IOException {
        if (!Files.exists(path)) {
            throw new NoSuchFileException(path.toString());
        }
        if (!Files.isDirectory(path)) {
            throw new NotDirectoryException(path.toString());
        }
    }
}
