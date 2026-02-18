package com.roadrats.demo.model.releasemanager;

import java.util.List;
import java.util.Map;

/**
 * Mirrors the PSCustomObject returned by Get-Jira-Issues in Jira.psm1.
 * Each field maps to a Jira issue with its components, metadata, and links.
 */
public class JiraTicket {

    private String jira;
    private String url;
    private String assignee;
    private String devTeam;
    private String productManager;
    private String downtimeRequired;
    private String status;
    private String title;
    private String plannedDeploymentDate;
    private String resolution;
    private String labels;

    // Component flags (comma-separated component names, empty string if none)
    private String architect;
    private String ddl;
    private String dml;
    private String web;
    private String chewyWmsGateway;
    private String fitnesse;
    private String nonStandard;

    // Linked issues: key = relationship type (e.g. "created by", "blocks"), value = list of Jira keys
    private Map<String, List<String>> linkedIssues;

    // Description (raw text extracted from Atlassian Document Format)
    private String description;

    // Implementation plan (raw text)
    private String implementationPlan;

    public JiraTicket() {}

    // --- Getters and Setters ---

    public String getJira() { return jira; }
    public void setJira(String jira) { this.jira = jira; }

    public String getUrl() { return url; }
    public void setUrl(String url) { this.url = url; }

    public String getAssignee() { return assignee; }
    public void setAssignee(String assignee) { this.assignee = assignee; }

    public String getDevTeam() { return devTeam; }
    public void setDevTeam(String devTeam) { this.devTeam = devTeam; }

    public String getProductManager() { return productManager; }
    public void setProductManager(String productManager) { this.productManager = productManager; }

    public String getDowntimeRequired() { return downtimeRequired; }
    public void setDowntimeRequired(String downtimeRequired) { this.downtimeRequired = downtimeRequired; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getPlannedDeploymentDate() { return plannedDeploymentDate; }
    public void setPlannedDeploymentDate(String plannedDeploymentDate) { this.plannedDeploymentDate = plannedDeploymentDate; }

    public String getResolution() { return resolution; }
    public void setResolution(String resolution) { this.resolution = resolution; }

    public String getLabels() { return labels; }
    public void setLabels(String labels) { this.labels = labels; }

    public String getArchitect() { return architect; }
    public void setArchitect(String architect) { this.architect = architect; }

    public String getDdl() { return ddl; }
    public void setDdl(String ddl) { this.ddl = ddl; }

    public String getDml() { return dml; }
    public void setDml(String dml) { this.dml = dml; }

    public String getWeb() { return web; }
    public void setWeb(String web) { this.web = web; }

    public String getChewyWmsGateway() { return chewyWmsGateway; }
    public void setChewyWmsGateway(String chewyWmsGateway) { this.chewyWmsGateway = chewyWmsGateway; }

    public String getFitnesse() { return fitnesse; }
    public void setFitnesse(String fitnesse) { this.fitnesse = fitnesse; }

    public String getNonStandard() { return nonStandard; }
    public void setNonStandard(String nonStandard) { this.nonStandard = nonStandard; }

    public Map<String, List<String>> getLinkedIssues() { return linkedIssues; }
    public void setLinkedIssues(Map<String, List<String>> linkedIssues) { this.linkedIssues = linkedIssues; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public String getImplementationPlan() { return implementationPlan; }
    public void setImplementationPlan(String implementationPlan) { this.implementationPlan = implementationPlan; }

    /**
     * Check if this ticket has any component assigned.
     */
    public boolean hasComponents() {
        return isNotEmpty(architect) || isNotEmpty(ddl) || isNotEmpty(dml)
            || isNotEmpty(web) || isNotEmpty(chewyWmsGateway)
            || isNotEmpty(fitnesse) || isNotEmpty(nonStandard);
    }

    /**
     * Check if this ticket requires downtime.
     */
    public boolean requiresDowntime() {
        return downtimeRequired != null
            && !downtimeRequired.isEmpty()
            && !"No Downtime".equalsIgnoreCase(downtimeRequired);
    }

    private boolean isNotEmpty(String s) {
        return s != null && !s.trim().isEmpty();
    }
}

