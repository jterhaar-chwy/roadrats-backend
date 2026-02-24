package com.roadrats.demo.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.Map;

@Configuration
public class ReleaseManagerConfig {

    @Value("${roadrats.jira.base-url:https://chewyinc.atlassian.net}")
    private String jiraBaseUrl;

    @Value("${roadrats.jira.user:}")
    private String jiraUser;

    @Value("${roadrats.jira.token:}")
    private String jiraToken;

    @Value("${roadrats.jira.max-results:500}")
    private int maxResults;

    @Value("${roadrats.releases.logs-path:#{null}}")
    private String logsPath;

    @Value("${roadrats.releases.deployments-path:#{null}}")
    private String deploymentsPath;

    @Value("${roadrats.releases.github-repo:#{null}}")
    private String githubRepo;

    @Value("${roadrats.releases.github-workflow:build-stage-deploy-package-by-CHG.yaml}")
    private String githubWorkflow;

    // Baseline deployment Thursday for the bi-weekly cycle calculation
    // Matches: $BaselineDeployment = [datetime]"2026-02-19" in standard-release.ps1
    @Value("${roadrats.jira.baseline-deployment:2026-02-19}")
    private String baselineDeployment;

    public String getJiraBaseUrl() { return jiraBaseUrl; }
    public String getJiraUser() { return jiraUser; }
    public String getJiraToken() { return jiraToken; }
    public int getMaxResults() { return maxResults; }
    public String getLogsPath() { return logsPath; }
    public String getDeploymentsPath() { return deploymentsPath; }
    public String getGithubRepo() { return githubRepo; }
    public String getGithubWorkflow() { return githubWorkflow; }

    // -----------------------------------------------------------------
    // JQL Builders
    // -----------------------------------------------------------------

    /**
     * Build JQL for a CHG label query.
     * Mirrors: Get-Jira-Issues -Filter CHG -Key "CHG0261540"
     * Uses filter: (project = "WMS Development" OR project = "WMS Rx")
     *   AND (issuetype = Bug OR issuetype = Story OR issuetype = Task)
     *   AND labels = {CHG} ORDER BY key ASC
     */
    public String buildChgJql(String chgNumber) {
        return String.format(
            "(project = \"WMS Development\" OR project = \"WMS Rx\") " +
            "AND (issuetype = Bug OR issuetype = Story OR issuetype = Task) " +
            "AND labels = %s " +
            "AND labels != ExcludeFromBuild ORDER BY key ASC",
            chgNumber
        );
    }

    /**
     * Build JQL for a standard release query.
     * Mirrors the dynamic JQL from standard-release.ps1:
     *   (project = 'WMS Development' OR project = 'WMS Rx')
     *   AND issuetype in standardIssueTypes()
     *   AND fixVersion in ('WMS Week of {date}', 'WMSRx Week of {date}')
     *   AND 'Planned Deployment Date[Date]' = '{date}'
     */
    public String buildReleaseJql(LocalDate deploymentDate) {
        LocalDate fixVersionSunday = deploymentDate.minusDays(4);
        String deploymentDateStr = deploymentDate.format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));
        // FixVersion uses M/d/yyyy format like PowerShell: $FixVersionSunday.ToString("M/d/yyyy")
        String fixVersionDateStr = fixVersionSunday.format(DateTimeFormatter.ofPattern("M/d/yyyy"));

        String fixVersionWMS = "WMS Week of " + fixVersionDateStr;
        String fixVersionWMSRx = "WMSRx Week of " + fixVersionDateStr;

        return String.format(
            "(project = 'WMS Development' OR project = 'WMS Rx') " +
            "AND issuetype in standardIssueTypes() " +
            "AND fixVersion in ('%s', '%s') " +
            "AND 'Planned Deployment Date[Date]' = '%s'",
            fixVersionWMS, fixVersionWMSRx, deploymentDateStr
        );
    }

    // -----------------------------------------------------------------
    // Preset JQL Filters (from jql-monitors directory)
    // -----------------------------------------------------------------

    /**
     * Get all available preset filters.
     * Mirrors the .ps1 files in \\wmsdev-dev\wmsdev\Development Work\JTerhaar\jql-monitors
     */
    public Map<String, PresetFilter> getPresetFilters() {
        Map<String, PresetFilter> presets = new LinkedHashMap<>();

        presets.put("dev", new PresetFilter(
            "Dev",
            "Issues currently in Dev status",
            "project in (WMSRX, WMS) AND (labels not in (WMS_DONOTCOMPILE) OR labels in (EMPTY)) " +
            "AND issuetype not in (Epic, subTaskIssueTypes(), Task) " +
            "AND resolution in (EMPTY, Done) " +
            "AND fixVersion in (EMPTY, unreleasedVersions()) " +
            "AND status in (\"Dev\")"
        ));

        presets.put("qa", new PresetFilter(
            "QA",
            "Issues in QA, Stakeholder Review, or Integration Testing",
            "project in (WMSRX, WMS) AND (labels not in (WMS_DONOTCOMPILE) OR labels in (EMPTY)) " +
            "AND issuetype not in (Epic, subTaskIssueTypes(), Task) " +
            "AND resolution in (EMPTY, Done) " +
            "AND fixVersion in (EMPTY, unreleasedVersions()) " +
            "AND status in (\"QA\", \"Stakeholder Review\", \"Integration Testing\")"
        ));

        presets.put("qa-plus-complete", new PresetFilter(
            "QA + Complete",
            "Issues in QA, Stakeholder Review, Integration Testing, or Complete",
            "project in (WMSRX, WMS) AND (labels not in (WMS_DONOTCOMPILE) OR labels in (EMPTY)) " +
            "AND issuetype not in (Epic, subTaskIssueTypes(), Task) " +
            "AND resolution in (EMPTY, Done) " +
            "AND fixVersion in (EMPTY, unreleasedVersions()) " +
            "AND status in (\"QA\", \"Stakeholder Review\", \"Integration Testing\", Complete)"
        ));

        presets.put("review", new PresetFilter(
            "Review",
            "Issues currently in Review status",
            "project in (WMSRX, WMS) AND (labels not in (WMS_DONOTCOMPILE) OR labels in (EMPTY)) " +
            "AND issuetype not in (Epic, subTaskIssueTypes(), Task) " +
            "AND resolution in (EMPTY, Done) " +
            "AND fixVersion in (EMPTY, unreleasedVersions()) " +
            "AND status in (\"Review\")"
        ));

        presets.put("complete", new PresetFilter(
            "Complete",
            "Issues in Complete status ready for release",
            "project in (WMSRX, WMS) AND (labels not in (WMS_DONOTCOMPILE) OR labels in (EMPTY)) " +
            "AND issuetype not in (Epic, subTaskIssueTypes(), Task) " +
            "AND resolution in (EMPTY, Done) " +
            "AND fixVersion in (EMPTY, unreleasedVersions()) " +
            "AND status in (\"Complete\")"
        ));

        presets.put("blocked", new PresetFilter(
            "Blocked",
            "Issues currently in Blocked status",
            "project in (WMSRX, WMS) " +
            "AND issuetype not in (Epic, subTaskIssueTypes()) " +
            "AND resolution = EMPTY " +
            "AND status in (\"Blocked\", \"Impediment\")"
        ));

        presets.put("no-fixversion", new PresetFilter(
            "Work with No Fix Version",
            "Done/Complete/UAT issues missing a fix version",
            "project in (\"WMS\", \"WMS Rx\") " +
            "AND issuetype not in (subTaskIssueTypes(), Epic) " +
            "AND status in (\"Integration Testing\", \"Stakeholder Review\", UAT, Done, Complete) " +
            "AND (Resolution is EMPTY OR resolution = Done) " +
            "AND (fixversion is EMPTY) " +
            "ORDER BY parent ASC, fixVersion, status"
        ));

        presets.put("downtime", new PresetFilter(
            "Downtime Required",
            "Issues with downtime required flag set",
            "project in (WMSRX, WMS) " +
            "AND issuetype not in (Epic, subTaskIssueTypes()) " +
            "AND resolution = EMPTY " +
            "AND 'Downtime Required' is not EMPTY " +
            "AND fixVersion in (unreleasedVersions())"
        ));

        return presets;
    }

    // -----------------------------------------------------------------
    // Deployment Date Calculation
    // Mirrors Get-DeploymentThursday from standard-release.ps1
    // -----------------------------------------------------------------

    /**
     * Calculate the next deployment Thursday based on a bi-weekly cycle.
     * Offset 0 = next upcoming, 1 = one cycle after, -1 = previous, etc.
     */
    public LocalDate getDeploymentThursday(int offset) {
        LocalDate baseline = LocalDate.parse(baselineDeployment);
        LocalDate today = LocalDate.now();
        int cycleLength = 14;

        // Find next Thursday from today
        int daysUntilThursday = (DayOfWeek.THURSDAY.getValue() - today.getDayOfWeek().getValue() + 7) % 7;
        LocalDate nextThursday = today.plusDays(daysUntilThursday);

        // Calculate how many 14-day cycles since baseline
        long daysSinceBaseline = ChronoUnit.DAYS.between(baseline, nextThursday);
        long cycles = (long) Math.floor((double) daysSinceBaseline / cycleLength);

        LocalDate candidate = baseline.plusDays(cycles * cycleLength);

        // If candidate is behind nextThursday, advance one cycle
        if (candidate.isBefore(nextThursday)) {
            candidate = candidate.plusDays(cycleLength);
        }

        return candidate.plusDays((long) offset * cycleLength);
    }

    /**
     * Get the fix version Sunday (4 days before deployment Thursday).
     */
    public LocalDate getFixVersionSunday(LocalDate deploymentDate) {
        return deploymentDate.minusDays(4);
    }

    /**
     * Get a map of upcoming deployment dates for display.
     */
    public Map<String, Object> getUpcomingDeployments(int count) {
        Map<String, Object> result = new LinkedHashMap<>();
        for (int i = 0; i < count; i++) {
            LocalDate deployDate = getDeploymentThursday(i);
            LocalDate fixSunday = getFixVersionSunday(deployDate);
            String label = i == 0 ? "Next" : (i == 1 ? "Next +1" : "Next +" + i);

            Map<String, String> info = new LinkedHashMap<>();
            info.put("deploymentDate", deployDate.toString());
            info.put("fixVersionSunday", fixSunday.toString());
            info.put("fixVersionWMS", "WMS Week of " + fixSunday.format(DateTimeFormatter.ofPattern("M/d/yyyy")));
            info.put("fixVersionWMSRx", "WMSRx Week of " + fixSunday.format(DateTimeFormatter.ofPattern("M/d/yyyy")));
            info.put("releaseBranch", "release-" + deployDate.format(DateTimeFormatter.ofPattern("MM-dd")));
            result.put(label, info);
        }
        return result;
    }

    // -----------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------

    public String buildBrowseUrl(String jiraKey) {
        return String.format("%s/browse/%s", jiraBaseUrl, jiraKey);
    }

    public boolean isConfigured() {
        return jiraUser != null && !jiraUser.isEmpty()
            && jiraToken != null && !jiraToken.isEmpty();
    }

    /**
     * Simple holder for preset filter definitions.
     */
    public static class PresetFilter {
        private final String name;
        private final String description;
        private final String jql;

        public PresetFilter(String name, String description, String jql) {
            this.name = name;
            this.description = description;
            this.jql = jql;
        }

        public String getName() { return name; }
        public String getDescription() { return description; }
        public String getJql() { return jql; }
    }
}
