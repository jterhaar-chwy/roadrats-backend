package com.roadrats.demo.service.releasemanager;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.roadrats.demo.config.ReleaseManagerConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Service
public class GitHubActionsService {

    private static final Logger log = LoggerFactory.getLogger(GitHubActionsService.class);
    private static final ObjectMapper mapper = new ObjectMapper();

    private final ReleaseManagerConfig config;

    public GitHubActionsService(ReleaseManagerConfig config) {
        this.config = config;
    }

    /**
     * List recent workflow runs, optionally filtered by workflow name.
     */
    public List<Map<String, Object>> listRuns(String workflow, int limit) throws Exception {
        String repo = getRepo();
        List<String> cmd = new ArrayList<>(List.of(
            "gh", "run", "list",
            "--repo", repo,
            "--limit", String.valueOf(Math.min(limit, 50)),
            "--json", "databaseId,displayTitle,status,conclusion,event,headBranch,createdAt,updatedAt,url,workflowName"
        ));
        if (workflow != null && !workflow.isEmpty()) {
            cmd.add("--workflow");
            cmd.add(workflow);
        }

        String json = executeGhCommand(cmd);
        return mapper.readValue(json, new TypeReference<List<Map<String, Object>>>() {});
    }

    /**
     * Get details for a specific workflow run.
     */
    public Map<String, Object> getRunDetails(long runId) throws Exception {
        String repo = getRepo();
        List<String> cmd = List.of(
            "gh", "run", "view",
            "--repo", repo,
            String.valueOf(runId),
            "--json", "databaseId,displayTitle,status,conclusion,event,headBranch,createdAt,updatedAt,url,workflowName,jobs"
        );

        String json = executeGhCommand(cmd);
        return mapper.readValue(json, new TypeReference<Map<String, Object>>() {});
    }

    /**
     * Get jobs for a specific workflow run.
     */
    public List<Map<String, Object>> getRunJobs(long runId) throws Exception {
        Map<String, Object> details = getRunDetails(runId);
        Object jobs = details.get("jobs");
        if (jobs instanceof List) {
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> jobList = (List<Map<String, Object>>) jobs;
            return jobList;
        }
        return Collections.emptyList();
    }

    /**
     * List available workflows in the repo.
     */
    public List<Map<String, Object>> listWorkflows() throws Exception {
        String repo = getRepo();
        List<String> cmd = List.of(
            "gh", "workflow", "list",
            "--repo", repo,
            "--json", "id,name,state"
        );

        String json = executeGhCommand(cmd);
        return mapper.readValue(json, new TypeReference<List<Map<String, Object>>>() {});
    }

    /**
     * Trigger a workflow dispatch for a specific workflow.
     */
    public Map<String, Object> triggerWorkflow(String workflow, Map<String, String> inputs) throws Exception {
        String repo = getRepo();
        List<String> cmd = new ArrayList<>(List.of(
            "gh", "workflow", "run", workflow,
            "--repo", repo
        ));

        for (Map.Entry<String, String> input : inputs.entrySet()) {
            cmd.add("-f");
            cmd.add(input.getKey() + "=" + input.getValue());
        }

        executeGhCommand(cmd);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("triggered", true);
        result.put("workflow", workflow);
        result.put("inputs", inputs);
        return result;
    }

    private String getRepo() {
        String repo = config.getGithubRepo();
        if (repo == null || repo.isEmpty()) {
            throw new IllegalStateException(
                "GitHub repo not configured. Set roadrats.releases.github-repo in application.properties (e.g. org/wms-deployments)");
        }
        return repo;
    }

    private String executeGhCommand(List<String> command) throws Exception {
        log.info("Executing: {}", String.join(" ", command));

        ProcessBuilder pb = new ProcessBuilder(command);
        pb.redirectErrorStream(false);
        Process process = pb.start();

        String stdout;
        String stderr;
        try (BufferedReader outReader = new BufferedReader(new InputStreamReader(process.getInputStream()));
             BufferedReader errReader = new BufferedReader(new InputStreamReader(process.getErrorStream()))) {
            stdout = outReader.lines().collect(Collectors.joining("\n"));
            stderr = errReader.lines().collect(Collectors.joining("\n"));
        }

        boolean finished = process.waitFor(30, TimeUnit.SECONDS);
        if (!finished) {
            process.destroyForcibly();
            throw new RuntimeException("gh command timed out after 30 seconds");
        }

        int exitCode = process.exitValue();
        if (exitCode != 0) {
            log.error("gh command failed (exit {}): {}", exitCode, stderr);
            throw new RuntimeException("gh command failed: " + (stderr.isEmpty() ? "exit code " + exitCode : stderr));
        }

        return stdout;
    }
}
