package com.roadrats.demo.controller.releasemanager;

import com.roadrats.demo.config.ReleaseManagerConfig;
import com.roadrats.demo.config.ReleaseManagerConfig.PresetFilter;
import com.roadrats.demo.model.releasemanager.DeploymentPlan;
import com.roadrats.demo.model.releasemanager.JiraTicket;
import com.roadrats.demo.service.releasemanager.DeploymentFolderService;
import com.roadrats.demo.service.releasemanager.GitHubActionsService;
import com.roadrats.demo.service.releasemanager.JiraService;
import com.roadrats.demo.service.releasemanager.ReleaseManagerService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/release-manager")
public class ReleaseManagerController {

    private static final Logger log = LoggerFactory.getLogger(ReleaseManagerController.class);

    private final ReleaseManagerService releaseManagerService;
    private final JiraService jiraService;
    private final ReleaseManagerConfig config;
    private final DeploymentFolderService deploymentFolderService;
    private final GitHubActionsService gitHubActionsService;

    public ReleaseManagerController(
            ReleaseManagerService releaseManagerService,
            JiraService jiraService,
            ReleaseManagerConfig config,
            DeploymentFolderService deploymentFolderService,
            GitHubActionsService gitHubActionsService) {
        this.releaseManagerService = releaseManagerService;
        this.jiraService = jiraService;
        this.config = config;
        this.deploymentFolderService = deploymentFolderService;
        this.gitHubActionsService = gitHubActionsService;
    }

    /**
     * GET /api/release-manager/config-status
     * Check if Jira credentials are configured.
     */
    @GetMapping("/config-status")
    public ResponseEntity<Map<String, Object>> getConfigStatus() {
        Map<String, Object> status = new LinkedHashMap<>();
        status.put("configured", config.isConfigured());
        status.put("jiraBaseUrl", config.getJiraBaseUrl());
        status.put("userConfigured", config.getJiraUser() != null && !config.getJiraUser().isEmpty());
        status.put("tokenConfigured", config.getJiraToken() != null && !config.getJiraToken().isEmpty());
        return ResponseEntity.ok(status);
    }

    // ======================================================================
    // Query Mode: CHG Label
    // Equivalent to: Get-Jira-Issues -Filter CHG -Key "CHG0261540"
    // ======================================================================

    /**
     * GET /api/release-manager/deployment-plan?chg=CHG0261540
     */
    @GetMapping("/deployment-plan")
    public ResponseEntity<?> getDeploymentPlan(@RequestParam("chg") String chgNumber) {
        try {
            log.info("Deployment plan requested for CHG: {}", chgNumber);
            String normalized = normalizeChg(chgNumber);
            DeploymentPlan plan = releaseManagerService.buildDeploymentPlan(normalized);
            return ResponseEntity.ok(plan);
        } catch (IllegalStateException e) {
            return configError(e);
        } catch (Exception e) {
            return serverError("Failed to build deployment plan", chgNumber, e);
        }
    }

    /**
     * GET /api/release-manager/tickets?chg=CHG0261540
     * Raw ticket list without deployment plan grouping.
     */
    @GetMapping("/tickets")
    public ResponseEntity<?> getTickets(@RequestParam("chg") String chgNumber) {
        try {
            String normalized = normalizeChg(chgNumber);
            List<JiraTicket> tickets = jiraService.getTicketsForChg(normalized);
            return ResponseEntity.ok(buildTicketResponse(normalized, tickets));
        } catch (IllegalStateException e) {
            return configError(e);
        } catch (Exception e) {
            return serverError("Failed to fetch tickets", chgNumber, e);
        }
    }

    // ======================================================================
    // Query Mode: Standard Release (date-driven)
    // Equivalent to: standard-release.ps1 with dynamic JQL
    // ======================================================================

    /**
     * GET /api/release-manager/release-plan?date=2026-02-19
     * Build a deployment plan using fix-version + deployment-date JQL.
     * If no date is provided, calculates the next deployment Thursday.
     */
    @GetMapping("/release-plan")
    public ResponseEntity<?> getReleasePlan(
            @RequestParam(value = "date", required = false) String dateStr,
            @RequestParam(value = "offset", required = false, defaultValue = "0") int offset) {
        try {
            LocalDate deploymentDate;
            if (dateStr != null && !dateStr.isEmpty()) {
                deploymentDate = LocalDate.parse(dateStr);
            } else {
                deploymentDate = config.getDeploymentThursday(offset);
            }

            String jql = config.buildReleaseJql(deploymentDate);
            String label = "Release " + deploymentDate.format(DateTimeFormatter.ofPattern("MM-dd"));

            log.info("Release plan requested for: {} - JQL: {}", label, jql);

            DeploymentPlan plan = releaseManagerService.buildDeploymentPlanFromJql(jql, label);

            // Add release metadata
            Map<String, Object> response = new LinkedHashMap<>();
            response.put("deploymentDate", deploymentDate.toString());
            response.put("fixVersionSunday", config.getFixVersionSunday(deploymentDate).toString());
            response.put("jql", jql);
            response.put("plan", plan);
            return ResponseEntity.ok(response);
        } catch (IllegalStateException e) {
            return configError(e);
        } catch (Exception e) {
            return serverError("Failed to build release plan", dateStr, e);
        }
    }

    // ======================================================================
    // Query Mode: Preset Filters
    // Equivalent to: dev-filter.ps1, qa-filter.ps1, etc.
    // ======================================================================

    /**
     * GET /api/release-manager/presets
     * List all available preset JQL filters.
     */
    @GetMapping("/presets")
    public ResponseEntity<Map<String, Object>> getPresets() {
        Map<String, Object> result = new LinkedHashMap<>();
        for (Map.Entry<String, PresetFilter> entry : config.getPresetFilters().entrySet()) {
            Map<String, String> preset = new LinkedHashMap<>();
            preset.put("name", entry.getValue().getName());
            preset.put("description", entry.getValue().getDescription());
            preset.put("jql", entry.getValue().getJql());
            result.put(entry.getKey(), preset);
        }
        return ResponseEntity.ok(result);
    }

    /**
     * GET /api/release-manager/preset-plan?preset=dev
     * Execute a preset filter and build a deployment plan.
     */
    @GetMapping("/preset-plan")
    public ResponseEntity<?> getPresetPlan(@RequestParam("preset") String presetKey) {
        try {
            PresetFilter preset = config.getPresetFilters().get(presetKey.toLowerCase());
            if (preset == null) {
                Map<String, Object> error = new LinkedHashMap<>();
                error.put("error", "Unknown preset");
                error.put("message", "Preset '" + presetKey + "' not found. Available: " +
                        String.join(", ", config.getPresetFilters().keySet()));
                return ResponseEntity.badRequest().body(error);
            }

            log.info("Preset plan requested: {} - {}", presetKey, preset.getName());

            DeploymentPlan plan = releaseManagerService.buildDeploymentPlanFromJql(
                    preset.getJql(), "Preset:" + preset.getName());

            Map<String, Object> response = new LinkedHashMap<>();
            response.put("preset", presetKey);
            response.put("name", preset.getName());
            response.put("description", preset.getDescription());
            response.put("jql", preset.getJql());
            response.put("plan", plan);
            return ResponseEntity.ok(response);
        } catch (IllegalStateException e) {
            return configError(e);
        } catch (Exception e) {
            return serverError("Failed to execute preset filter", presetKey, e);
        }
    }

    // ======================================================================
    // Query Mode: Custom JQL
    // Equivalent to: Get-Jira-Issues -Filter JQL -Key $JQL
    // ======================================================================

    /**
     * POST /api/release-manager/custom-plan
     * Execute arbitrary JQL and build a deployment plan.
     * Body: { "jql": "project = WMS AND ...", "label": "My Query" }
     */
    @PostMapping("/custom-plan")
    public ResponseEntity<?> getCustomPlan(@RequestBody Map<String, String> body) {
        try {
            String jql = body.get("jql");
            String label = body.getOrDefault("label", "Custom JQL");

            if (jql == null || jql.trim().isEmpty()) {
                Map<String, String> error = new LinkedHashMap<>();
                error.put("error", "Missing JQL");
                error.put("message", "Provide a 'jql' field in the request body");
                return ResponseEntity.badRequest().body(error);
            }

            log.info("Custom JQL plan requested [{}]: {}", label, jql);

            DeploymentPlan plan = releaseManagerService.buildDeploymentPlanFromJql(jql.trim(), label);

            Map<String, Object> response = new LinkedHashMap<>();
            response.put("label", label);
            response.put("jql", jql.trim());
            response.put("plan", plan);
            return ResponseEntity.ok(response);
        } catch (IllegalStateException e) {
            return configError(e);
        } catch (Exception e) {
            return serverError("Failed to execute custom JQL", "custom", e);
        }
    }

    // ======================================================================
    // Deployment Date Calculator
    // ======================================================================

    /**
     * GET /api/release-manager/deployment-dates?count=4
     * Get upcoming deployment Thursdays with fix version info.
     */
    @GetMapping("/deployment-dates")
    public ResponseEntity<Map<String, Object>> getDeploymentDates(
            @RequestParam(value = "count", defaultValue = "4") int count) {
        Map<String, Object> dates = config.getUpcomingDeployments(Math.min(count, 12));
        return ResponseEntity.ok(dates);
    }

    // ======================================================================
    // Deployment Folder Browser
    // ======================================================================

    /**
     * GET /api/release-manager/deployments/years
     * List available year folders under the deployments root.
     */
    @GetMapping("/deployments/years")
    public ResponseEntity<?> getDeploymentYears() {
        try {
            return ResponseEntity.ok(deploymentFolderService.listYears());
        } catch (IllegalStateException e) {
            return configError(e);
        } catch (Exception e) {
            return serverError("Failed to list deployment years", "", e);
        }
    }

    /**
     * GET /api/release-manager/deployments/months?year=2026
     * List month folders under a year.
     */
    @GetMapping("/deployments/months")
    public ResponseEntity<?> getDeploymentMonths(@RequestParam("year") String year) {
        try {
            return ResponseEntity.ok(deploymentFolderService.listMonths(year));
        } catch (Exception e) {
            return serverError("Failed to list months", year, e);
        }
    }

    /**
     * GET /api/release-manager/deployments/releases?year=2026&month=02-FEB
     * List release/CHG folders under a month.
     */
    @GetMapping("/deployments/releases")
    public ResponseEntity<?> getDeploymentReleases(
            @RequestParam("year") String year,
            @RequestParam("month") String month) {
        try {
            return ResponseEntity.ok(deploymentFolderService.listReleases(year, month));
        } catch (Exception e) {
            return serverError("Failed to list releases", year + "/" + month, e);
        }
    }

    /**
     * GET /api/release-manager/deployments/contents?path=2026/02-FEB/release-03-05
     * Get folder contents including CHG summaries.
     */
    @GetMapping("/deployments/contents")
    public ResponseEntity<?> getFolderContents(@RequestParam("path") String path) {
        try {
            return ResponseEntity.ok(deploymentFolderService.getFolderContents(path));
        } catch (Exception e) {
            return serverError("Failed to read folder contents", path, e);
        }
    }

    /**
     * GET /api/release-manager/deployments/file?path=2026/02-FEB/release-03-05/CHG.../somefile.log
     * Read a specific file's content (text files, size-limited).
     */
    @GetMapping("/deployments/file")
    public ResponseEntity<?> readDeploymentFile(@RequestParam("path") String path) {
        try {
            return ResponseEntity.ok(deploymentFolderService.readFileContent(path));
        } catch (Exception e) {
            return serverError("Failed to read file", path, e);
        }
    }

    // ======================================================================
    // GitHub Actions Integration
    // ======================================================================

    /**
     * GET /api/release-manager/actions/runs?workflow=...&limit=20
     * List recent GitHub Actions workflow runs.
     */
    @GetMapping("/actions/runs")
    public ResponseEntity<?> getActionRuns(
            @RequestParam(value = "workflow", required = false) String workflow,
            @RequestParam(value = "limit", defaultValue = "20") int limit) {
        try {
            String wf = (workflow != null && !workflow.isEmpty()) ? workflow : config.getGithubWorkflow();
            return ResponseEntity.ok(gitHubActionsService.listRuns(wf, limit));
        } catch (IllegalStateException e) {
            return configError(e);
        } catch (Exception e) {
            return serverError("Failed to list GitHub Actions runs", workflow, e);
        }
    }

    /**
     * GET /api/release-manager/actions/run/{runId}
     * Get details for a specific workflow run including jobs.
     */
    @GetMapping("/actions/run/{runId}")
    public ResponseEntity<?> getActionRunDetails(@PathVariable("runId") long runId) {
        try {
            return ResponseEntity.ok(gitHubActionsService.getRunDetails(runId));
        } catch (Exception e) {
            return serverError("Failed to get run details", String.valueOf(runId), e);
        }
    }

    /**
     * GET /api/release-manager/actions/run/{runId}/jobs
     * Get jobs for a specific workflow run.
     */
    @GetMapping("/actions/run/{runId}/jobs")
    public ResponseEntity<?> getActionRunJobs(@PathVariable("runId") long runId) {
        try {
            return ResponseEntity.ok(gitHubActionsService.getRunJobs(runId));
        } catch (Exception e) {
            return serverError("Failed to get run jobs", String.valueOf(runId), e);
        }
    }

    /**
     * GET /api/release-manager/actions/workflows
     * List available workflows in the configured repo.
     */
    @GetMapping("/actions/workflows")
    public ResponseEntity<?> getWorkflows() {
        try {
            return ResponseEntity.ok(gitHubActionsService.listWorkflows());
        } catch (IllegalStateException e) {
            return configError(e);
        } catch (Exception e) {
            return serverError("Failed to list workflows", "", e);
        }
    }

    /**
     * POST /api/release-manager/actions/trigger
     * Trigger a workflow dispatch.
     * Body: { "workflow": "build-stage-deploy-package-by-CHG.yaml", "inputs": { "CHGNumber": "CHG...", "Environment": "prod" } }
     */
    @PostMapping("/actions/trigger")
    public ResponseEntity<?> triggerWorkflow(@RequestBody Map<String, Object> body) {
        try {
            String workflow = (String) body.getOrDefault("workflow", config.getGithubWorkflow());
            @SuppressWarnings("unchecked")
            Map<String, String> inputs = (Map<String, String>) body.getOrDefault("inputs", Map.of());
            return ResponseEntity.ok(gitHubActionsService.triggerWorkflow(workflow, inputs));
        } catch (IllegalStateException e) {
            return configError(e);
        } catch (Exception e) {
            return serverError("Failed to trigger workflow", "", e);
        }
    }

    // ======================================================================
    // Helpers
    // ======================================================================

    private String normalizeChg(String chgNumber) {
        String normalized = chgNumber.toUpperCase().trim();
        if (!normalized.startsWith("CHG")) {
            normalized = "CHG" + normalized;
        }
        return normalized;
    }

    private Map<String, Object> buildTicketResponse(String label, List<JiraTicket> tickets) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("label", label);
        result.put("total", tickets.size());
        result.put("tickets", tickets);
        return result;
    }

    private ResponseEntity<?> configError(IllegalStateException e) {
        log.error("Configuration error: {}", e.getMessage());
        Map<String, String> error = new LinkedHashMap<>();
        error.put("error", "Configuration Error");
        error.put("message", e.getMessage());
        return ResponseEntity.status(503).body(error);
    }

    private ResponseEntity<?> serverError(String context, String detail, Exception e) {
        log.error("{} [{}]: {}", context, detail, e.getMessage(), e);
        Map<String, String> error = new LinkedHashMap<>();
        error.put("error", context);
        error.put("message", e.getMessage());
        return ResponseEntity.status(500).body(error);
    }
}
