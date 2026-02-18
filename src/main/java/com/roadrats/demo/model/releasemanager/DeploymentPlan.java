package com.roadrats.demo.model.releasemanager;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * Structured deployment plan for a CHG release.
 * Mirrors the output of Get-DeploymentPlan from Global.psm1,
 * grouping tickets by component type with risk flags.
 */
public class DeploymentPlan {

    private String chgNumber;
    private LocalDateTime generatedAt;
    private int totalTickets;
    private boolean downtimeRequired;
    private String plannedDeploymentDate;

    // Tickets grouped by component type
    private List<ComponentGroup> architectComponents;
    private List<ComponentGroup> ddlComponents;
    private List<ComponentGroup> dmlComponents;
    private List<ComponentGroup> webComponents;
    private List<ComponentGroup> gatewayComponents;
    private List<ComponentGroup> fitnesseComponents;
    private List<ComponentGroup> nonStandardComponents;

    // All tickets in this CHG
    private List<JiraTicket> allTickets;

    // Risk flags
    private List<RiskFlag> riskFlags;

    // Summary stats
    private Map<String, Integer> teamBreakdown;
    private Map<String, Integer> statusBreakdown;

    public DeploymentPlan() {}

    // --- Getters and Setters ---

    public String getChgNumber() { return chgNumber; }
    public void setChgNumber(String chgNumber) { this.chgNumber = chgNumber; }

    public LocalDateTime getGeneratedAt() { return generatedAt; }
    public void setGeneratedAt(LocalDateTime generatedAt) { this.generatedAt = generatedAt; }

    public int getTotalTickets() { return totalTickets; }
    public void setTotalTickets(int totalTickets) { this.totalTickets = totalTickets; }

    public boolean isDowntimeRequired() { return downtimeRequired; }
    public void setDowntimeRequired(boolean downtimeRequired) { this.downtimeRequired = downtimeRequired; }

    public String getPlannedDeploymentDate() { return plannedDeploymentDate; }
    public void setPlannedDeploymentDate(String plannedDeploymentDate) { this.plannedDeploymentDate = plannedDeploymentDate; }

    public List<ComponentGroup> getArchitectComponents() { return architectComponents; }
    public void setArchitectComponents(List<ComponentGroup> architectComponents) { this.architectComponents = architectComponents; }

    public List<ComponentGroup> getDdlComponents() { return ddlComponents; }
    public void setDdlComponents(List<ComponentGroup> ddlComponents) { this.ddlComponents = ddlComponents; }

    public List<ComponentGroup> getDmlComponents() { return dmlComponents; }
    public void setDmlComponents(List<ComponentGroup> dmlComponents) { this.dmlComponents = dmlComponents; }

    public List<ComponentGroup> getWebComponents() { return webComponents; }
    public void setWebComponents(List<ComponentGroup> webComponents) { this.webComponents = webComponents; }

    public List<ComponentGroup> getGatewayComponents() { return gatewayComponents; }
    public void setGatewayComponents(List<ComponentGroup> gatewayComponents) { this.gatewayComponents = gatewayComponents; }

    public List<ComponentGroup> getFitnesseComponents() { return fitnesseComponents; }
    public void setFitnesseComponents(List<ComponentGroup> fitnesseComponents) { this.fitnesseComponents = fitnesseComponents; }

    public List<ComponentGroup> getNonStandardComponents() { return nonStandardComponents; }
    public void setNonStandardComponents(List<ComponentGroup> nonStandardComponents) { this.nonStandardComponents = nonStandardComponents; }

    public List<JiraTicket> getAllTickets() { return allTickets; }
    public void setAllTickets(List<JiraTicket> allTickets) { this.allTickets = allTickets; }

    public List<RiskFlag> getRiskFlags() { return riskFlags; }
    public void setRiskFlags(List<RiskFlag> riskFlags) { this.riskFlags = riskFlags; }

    public Map<String, Integer> getTeamBreakdown() { return teamBreakdown; }
    public void setTeamBreakdown(Map<String, Integer> teamBreakdown) { this.teamBreakdown = teamBreakdown; }

    public Map<String, Integer> getStatusBreakdown() { return statusBreakdown; }
    public void setStatusBreakdown(Map<String, Integer> statusBreakdown) { this.statusBreakdown = statusBreakdown; }

    /**
     * A group of Jira tickets under a specific component name.
     * e.g. component = "Autobatching", tickets = [WMS-51570, WMS-51661]
     */
    public static class ComponentGroup {
        private String componentName;
        private List<JiraTicket> tickets;
        private List<LinkedIssueWarning> linkedIssueWarnings;

        public ComponentGroup() {}

        public ComponentGroup(String componentName, List<JiraTicket> tickets, List<LinkedIssueWarning> linkedIssueWarnings) {
            this.componentName = componentName;
            this.tickets = tickets;
            this.linkedIssueWarnings = linkedIssueWarnings;
        }

        public String getComponentName() { return componentName; }
        public void setComponentName(String componentName) { this.componentName = componentName; }

        public List<JiraTicket> getTickets() { return tickets; }
        public void setTickets(List<JiraTicket> tickets) { this.tickets = tickets; }

        public List<LinkedIssueWarning> getLinkedIssueWarnings() { return linkedIssueWarnings; }
        public void setLinkedIssueWarnings(List<LinkedIssueWarning> linkedIssueWarnings) { this.linkedIssueWarnings = linkedIssueWarnings; }
    }

    /**
     * Warning when a linked issue is NOT in the CHG.
     * Mirrors the red-background Write-Host in Get-DeploymentPlan.
     */
    public static class LinkedIssueWarning {
        private String sourceJira;
        private String linkedJira;
        private String relationship;
        private boolean inChg;

        public LinkedIssueWarning() {}

        public LinkedIssueWarning(String sourceJira, String linkedJira, String relationship, boolean inChg) {
            this.sourceJira = sourceJira;
            this.linkedJira = linkedJira;
            this.relationship = relationship;
            this.inChg = inChg;
        }

        public String getSourceJira() { return sourceJira; }
        public void setSourceJira(String sourceJira) { this.sourceJira = sourceJira; }

        public String getLinkedJira() { return linkedJira; }
        public void setLinkedJira(String linkedJira) { this.linkedJira = linkedJira; }

        public String getRelationship() { return relationship; }
        public void setRelationship(String relationship) { this.relationship = relationship; }

        public boolean isInChg() { return inChg; }
        public void setInChg(boolean inChg) { this.inChg = inChg; }
    }

    /**
     * A risk flag identified by the analyzer.
     */
    public static class RiskFlag {
        private String severity; // "high", "medium", "low"
        private String category; // "downtime", "linked-issue", "non-standard", "security", etc.
        private String message;
        private String relatedJira; // optional

        public RiskFlag() {}

        public RiskFlag(String severity, String category, String message, String relatedJira) {
            this.severity = severity;
            this.category = category;
            this.message = message;
            this.relatedJira = relatedJira;
        }

        public String getSeverity() { return severity; }
        public void setSeverity(String severity) { this.severity = severity; }

        public String getCategory() { return category; }
        public void setCategory(String category) { this.category = category; }

        public String getMessage() { return message; }
        public void setMessage(String message) { this.message = message; }

        public String getRelatedJira() { return relatedJira; }
        public void setRelatedJira(String relatedJira) { this.relatedJira = relatedJira; }
    }
}

