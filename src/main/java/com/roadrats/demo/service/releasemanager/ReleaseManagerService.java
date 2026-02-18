package com.roadrats.demo.service.releasemanager;

import com.roadrats.demo.model.releasemanager.DeploymentPlan;
import com.roadrats.demo.model.releasemanager.DeploymentPlan.ComponentGroup;
import com.roadrats.demo.model.releasemanager.DeploymentPlan.LinkedIssueWarning;
import com.roadrats.demo.model.releasemanager.DeploymentPlan.RiskFlag;
import com.roadrats.demo.model.releasemanager.JiraTicket;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Translates Get-DeploymentPlan from Global.psm1 into Java.
 * Groups tickets by component type and flags linked issues not in the CHG.
 */
@Service
public class ReleaseManagerService {

    private static final Logger log = LoggerFactory.getLogger(ReleaseManagerService.class);

    private final JiraService jiraService;

    public ReleaseManagerService(JiraService jiraService) {
        this.jiraService = jiraService;
    }

    /**
     * Build a complete deployment plan for a CHG number.
     * Flow: Jira API -> parse tickets -> group by component -> flag risks
     */
    public DeploymentPlan buildDeploymentPlan(String chgNumber) throws Exception {
        log.info("Building deployment plan for {}", chgNumber);

        List<JiraTicket> tickets = jiraService.getTicketsForChg(chgNumber);
        return buildPlanFromTickets(tickets, chgNumber);
    }

    /**
     * Build a deployment plan from an arbitrary JQL query.
     * Equivalent to: Get-Jira-Issues -Filter JQL -Key $JQL | Get-DeploymentPlan
     */
    public DeploymentPlan buildDeploymentPlanFromJql(String jql, String label) throws Exception {
        log.info("Building deployment plan for JQL [{}]", label);

        List<JiraTicket> tickets = jiraService.executeJql(jql, label);
        return buildPlanFromTickets(tickets, label);
    }

    /**
     * Core plan builder — works with any ticket list regardless of how they were queried.
     */
    private DeploymentPlan buildPlanFromTickets(List<JiraTicket> tickets, String label) {

        // All Jira keys in this set (used for linked-issue validation)
        Set<String> allJiraKeys = tickets.stream()
                .map(JiraTicket::getJira)
                .collect(Collectors.toSet());

        DeploymentPlan plan = new DeploymentPlan();
        plan.setChgNumber(label);
        plan.setGeneratedAt(LocalDateTime.now());
        plan.setTotalTickets(tickets.size());
        plan.setAllTickets(tickets);

        // Detect downtime
        plan.setDowntimeRequired(tickets.stream().anyMatch(JiraTicket::requiresDowntime));

        // Detect planned deployment date (use first non-empty value found)
        tickets.stream()
                .map(JiraTicket::getPlannedDeploymentDate)
                .filter(d -> d != null && !d.isEmpty())
                .findFirst()
                .ifPresent(plan::setPlannedDeploymentDate);

        // Group by component type (mirrors Get-DeploymentPlan loop structure)
        plan.setArchitectComponents(groupByComponent(tickets, JiraTicket::getArchitect, allJiraKeys));
        plan.setDdlComponents(groupByComponent(tickets, JiraTicket::getDdl, allJiraKeys));
        plan.setDmlComponents(groupByComponent(tickets, JiraTicket::getDml, allJiraKeys));
        plan.setWebComponents(groupByComponent(tickets, JiraTicket::getWeb, allJiraKeys));
        plan.setGatewayComponents(groupByComponent(tickets, JiraTicket::getChewyWmsGateway, allJiraKeys));
        plan.setFitnesseComponents(groupByComponent(tickets, JiraTicket::getFitnesse, allJiraKeys));
        plan.setNonStandardComponents(groupByComponent(tickets, JiraTicket::getNonStandard, allJiraKeys));

        // Breakdowns
        plan.setTeamBreakdown(buildBreakdown(tickets, JiraTicket::getDevTeam));
        plan.setStatusBreakdown(buildBreakdown(tickets, JiraTicket::getStatus));

        // Risk flags
        plan.setRiskFlags(analyzeRisks(plan, allJiraKeys));

        log.info("Deployment plan for [{}] complete: {} tickets, {} risk flags",
                label, tickets.size(), plan.getRiskFlags().size());

        return plan;
    }

    /**
     * Group tickets by a component field.
     * A ticket can have multiple comma-separated component values (e.g. "API,Autobatching").
     * This mirrors the Group-Object -Property Architect | Where Name -ne "" pattern in PowerShell.
     */
    private List<ComponentGroup> groupByComponent(
            List<JiraTicket> tickets,
            Function<JiraTicket, String> componentGetter,
            Set<String> chgJiraKeys) {

        // Build a map: component name -> list of tickets
        Map<String, List<JiraTicket>> groupMap = new LinkedHashMap<>();

        for (JiraTicket ticket : tickets) {
            String compValue = componentGetter.apply(ticket);
            if (compValue == null || compValue.trim().isEmpty()) continue;

            // Split on comma since multiple components can be assigned
            for (String comp : compValue.split(",")) {
                String trimmed = comp.trim();
                if (!trimmed.isEmpty()) {
                    groupMap.computeIfAbsent(trimmed, k -> new ArrayList<>()).add(ticket);
                }
            }
        }

        // Convert to ComponentGroup list with linked issue warnings
        List<ComponentGroup> groups = new ArrayList<>();
        for (Map.Entry<String, List<JiraTicket>> entry : groupMap.entrySet()) {
            List<LinkedIssueWarning> warnings = checkLinkedIssues(entry.getValue(), chgJiraKeys);
            groups.add(new ComponentGroup(entry.getKey(), entry.getValue(), warnings));
        }

        return groups;
    }

    /**
     * Check linked issues for a set of tickets and flag any that are NOT in the CHG.
     * Mirrors the red-background Write-Host logic in Get-DeploymentPlan.
     */
    private List<LinkedIssueWarning> checkLinkedIssues(List<JiraTicket> tickets, Set<String> chgJiraKeys) {
        List<LinkedIssueWarning> warnings = new ArrayList<>();

        for (JiraTicket ticket : tickets) {
            if (ticket.getLinkedIssues() == null || ticket.getLinkedIssues().isEmpty()) continue;

            for (Map.Entry<String, List<String>> linkEntry : ticket.getLinkedIssues().entrySet()) {
                String relationship = linkEntry.getKey();
                for (String linkedKey : linkEntry.getValue()) {
                    boolean inChg = chgJiraKeys.contains(linkedKey);
                    warnings.add(new LinkedIssueWarning(
                            ticket.getJira(), linkedKey, relationship, inChg));
                }
            }
        }

        return warnings;
    }

    /**
     * Build a count breakdown (team -> count, status -> count, etc.)
     */
    private Map<String, Integer> buildBreakdown(List<JiraTicket> tickets, Function<JiraTicket, String> getter) {
        Map<String, Integer> breakdown = new LinkedHashMap<>();
        for (JiraTicket ticket : tickets) {
            String value = getter.apply(ticket);
            if (value == null || value.isEmpty()) value = "(unset)";
            breakdown.merge(value, 1, Integer::sum);
        }
        return breakdown;
    }

    /**
     * Analyze the plan and surface risk flags (similar to the red-highlight logic in PS).
     */
    private List<RiskFlag> analyzeRisks(DeploymentPlan plan, Set<String> chgJiraKeys) {
        List<RiskFlag> flags = new ArrayList<>();

        // Downtime
        if (plan.isDowntimeRequired()) {
            List<String> downtimeJiras = plan.getAllTickets().stream()
                    .filter(JiraTicket::requiresDowntime)
                    .map(t -> t.getJira() + " (" + t.getDowntimeRequired() + ")")
                    .collect(Collectors.toList());
            flags.add(new RiskFlag("high", "downtime",
                    "Downtime required by: " + String.join(", ", downtimeJiras), null));
        }

        // Linked issues not in CHG
        for (JiraTicket ticket : plan.getAllTickets()) {
            if (ticket.getLinkedIssues() == null) continue;
            for (Map.Entry<String, List<String>> entry : ticket.getLinkedIssues().entrySet()) {
                for (String linked : entry.getValue()) {
                    if (!chgJiraKeys.contains(linked)) {
                        flags.add(new RiskFlag("medium", "linked-issue",
                                ticket.getJira() + " " + entry.getKey() + " " + linked + " - NOT in CHG",
                                ticket.getJira()));
                    }
                }
            }
        }

        // Non-standard components
        if (!plan.getNonStandardComponents().isEmpty()) {
            int totalNs = plan.getNonStandardComponents().stream()
                    .mapToInt(g -> g.getTickets().size())
                    .sum();
            flags.add(new RiskFlag("low", "non-standard",
                    totalNs + " ticket(s) have non-standard components requiring manual steps", null));
        }

        // Tickets with no components at all
        long noComponents = plan.getAllTickets().stream()
                .filter(t -> !t.hasComponents())
                .count();
        if (noComponents > 0) {
            flags.add(new RiskFlag("low", "no-components",
                    noComponents + " ticket(s) have no components assigned", null));
        }

        return flags;
    }
}

