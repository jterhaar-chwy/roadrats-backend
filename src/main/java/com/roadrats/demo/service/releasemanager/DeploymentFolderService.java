package com.roadrats.demo.service.releasemanager;

import com.roadrats.demo.config.ReleaseManagerConfig;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Service
public class DeploymentFolderService {

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
                entry.put("type", name.startsWith("release-") ? "release" : name.startsWith("CHG") ? "chg" : "other");
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
                    child.put("type", name.startsWith("CHG") ? "chg" : "folder");
                    child.put("path", relativePath + "/" + name);

                    if (name.startsWith("CHG")) {
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
        result.put("chgCount", children.stream().filter(c -> "chg".equals(c.get("type"))).count());
        result.put("totalFiles", files.size());

        // If this folder itself is a CHG folder, include a deep summary
        String folderName = folder.getFileName().toString();
        if (folderName.startsWith("CHG")) {
            result.put("isChg", true);
            result.put("chgSummary", buildChgSummary(folder));
        } else {
            result.put("isChg", false);
        }

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
