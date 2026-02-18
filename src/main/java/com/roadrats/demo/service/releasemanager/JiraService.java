package com.roadrats.demo.service.releasemanager;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.roadrats.demo.config.ReleaseManagerConfig;
import com.roadrats.demo.model.releasemanager.JiraTicket;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;

/**
 * Translates the PowerShell Get-Jira-Issues + Get-Component-Mapping logic into Java.
 * Calls the Jira REST API, parses the response, and maps components exactly
 * as the Jira.psm1 module does.
 */
@Service
public class JiraService {

    private static final Logger log = LoggerFactory.getLogger(JiraService.class);

    private final ReleaseManagerConfig config;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    // Component mappings mirrored from Jira.psm1 Get-Component-Mapping
    private static final Map<String, String> ARCHITECT_MAPPING = new LinkedHashMap<>();
    private static final Map<String, String> DDL_MAPPING = new LinkedHashMap<>();
    private static final Map<String, String> DML_MAPPING = new LinkedHashMap<>();
    private static final Map<String, String> WEB_MAPPING = new LinkedHashMap<>();
    private static final Map<String, String> GATEWAY_MAPPING = new LinkedHashMap<>();
    private static final Map<String, String> FITNESSE_MAPPING = new LinkedHashMap<>();
    private static final Map<String, String> NON_STANDARD_MAPPING = new LinkedHashMap<>();

    static {
        // Architect components
        ARCHITECT_MAPPING.put("Advantage Platform - Architect", "Advantage Platform");
        ARCHITECT_MAPPING.put("AdvLinkFlatFile - Architect", "AdvLinkFlatFile");
        ARCHITECT_MAPPING.put("AdvLinkXMLHost - Architect", "AdvLinkXMLHost");
        ARCHITECT_MAPPING.put("API - Architect", "API");
        ARCHITECT_MAPPING.put("Autobatching - Architect", "Autobatching");
        ARCHITECT_MAPPING.put("CFS - Architect", "CFS");
        ARCHITECT_MAPPING.put("ChewyLinkForIntegrationService - Architect", "ChewyLinkForIntegrationService");
        ARCHITECT_MAPPING.put("ChewyLinkForJSON - Architect", "ChewyLinkForJSON");
        ARCHITECT_MAPPING.put("ChewyLinkForSNS - Architect", "ChewyLinkForSNS");
        ARCHITECT_MAPPING.put("ChewyLinkForSockets - Architect", "ChewyLinkForSockets");
        ARCHITECT_MAPPING.put("ChewyLinkForXML - Architect", "ChewyLinkForXML");
        ARCHITECT_MAPPING.put("ChewyPlatformExt - Architect", "Chewy Platform Ext");
        ARCHITECT_MAPPING.put("ContainerAdv - Architect", "ContainerAdv");
        ARCHITECT_MAPPING.put("Create Counts - Architect", "CreateCounts");
        ARCHITECT_MAPPING.put("CVP - Architect", "CVP");
        ARCHITECT_MAPPING.put("Deploy Manager - Architect", "DeployManager");
        ARCHITECT_MAPPING.put("Exacta - Architect", "Exacta");
        ARCHITECT_MAPPING.put("Fetch - Architect", "Fetch");
        ARCHITECT_MAPPING.put("GPS - Architect", "GPS");
        ARCHITECT_MAPPING.put("RF Andon - Architect", "RF Andon");
        ARCHITECT_MAPPING.put("SendEmail - Architect", "SendEmail");
        ARCHITECT_MAPPING.put("System Monitor - Architect", "System Monitor");
        ARCHITECT_MAPPING.put("UnitSorter - Architect", "UnitSorter");
        ARCHITECT_MAPPING.put("WA 2G - Architect", "WA 2G");
        ARCHITECT_MAPPING.put("WA - Architect", "WA");
        ARCHITECT_MAPPING.put("WA Processors - Architect", "WA Processors");
        ARCHITECT_MAPPING.put("WA Processors IO - Architect", "WA Processors IO");
        ARCHITECT_MAPPING.put("WA Rx - Architect", "WA Rx");

        // DDL components
        DDL_MAPPING.put("AAD - Database", "AAD");
        DDL_MAPPING.put("AAD_IMPORT_ORDER - Database", "AAD_IMPORT_ORDER");
        DDL_MAPPING.put("AAD_MASTER - Database", "AAD_MASTER");
        DDL_MAPPING.put("ADV - Database", "ADV");
        DDL_MAPPING.put("ADV IO - Database", "ADV_IMPORT_ORDER");
        DDL_MAPPING.put("ADV Master - Database", "ADV_MASTER");
        DDL_MAPPING.put("ARCH - Database", "ARCH");
        DDL_MAPPING.put("CLS - Database", "CLS Database");
        DDL_MAPPING.put("KoerberOne - Database", "KoerberOne - Database");
        DDL_MAPPING.put("WMS_LOG - Database", "WMS_LOG");
        DDL_MAPPING.put("WMS_LOG IO - Database", "WMS_LOG IO");
        DDL_MAPPING.put("WMS_LOG Master - Database", "WMS_LOG Master");

        // DML
        DML_MAPPING.put("DML - Database", "DML");

        // Web components
        WEB_MAPPING.put("Advantage Commander - Web", "Advantage Commander");
        WEB_MAPPING.put("Advantage Dashboard - Web", "Advantage Dashboard");
        WEB_MAPPING.put("Advantage Link Admin - Web", "Advantage Link Admin");
        WEB_MAPPING.put("Chewy Commander Ext - Web", "Chewy Commander Ext");
        WEB_MAPPING.put("Chewy Platform Ext - Web", "Chewy Platform Ext");
        WEB_MAPPING.put("Container Advantage - Web", "Container Advantage");
        WEB_MAPPING.put("Data Upload", "Data Upload");
        WEB_MAPPING.put("Deploy Manager - Web", "DeployManager");
        WEB_MAPPING.put("Email Notification - Web", "Email Notification");
        WEB_MAPPING.put("Extended VAS - Web", "Extended VAS");
        WEB_MAPPING.put("RF Menu Manager - Web", "RF Menu Manager");
        WEB_MAPPING.put("Self Service - Web", "Self Service");
        WEB_MAPPING.put("Send Email - Web", "Send Email");
        WEB_MAPPING.put("System Monitor - Web", "System Monitor");
        WEB_MAPPING.put("WA - Web", "WA");
        WEB_MAPPING.put("WA Appointment - Web", "WA - Appointment");

        // Gateway
        GATEWAY_MAPPING.put("ChewyWMSGateway", "ChewyWMSGateway");

        // Fitnesse
        FITNESSE_MAPPING.put("FitNesse", "Fitnesse");

        // Non-standard
        NON_STANDARD_MAPPING.put("CLS Script", "CLS Script");
        NON_STANDARD_MAPPING.put("Datahub", "Datahub");
        NON_STANDARD_MAPPING.put("Data Upload", "Data Upload");
        NON_STANDARD_MAPPING.put("Dynatrace", "Dynatrace");
        NON_STANDARD_MAPPING.put("Splunk", "Splunk");
        NON_STANDARD_MAPPING.put("WMS Server Config", "WMS Server Config");
        NON_STANDARD_MAPPING.put("AWS", "AWS");
        NON_STANDARD_MAPPING.put("Scheduled Task", "Scheduled Task");
        NON_STANDARD_MAPPING.put("CONTROL.INI", "CONTROL.INI");
        NON_STANDARD_MAPPING.put("Telnet", "Telnet");
    }

    public JiraService(ReleaseManagerConfig config) {
        this.config = config;
        this.objectMapper = new ObjectMapper();
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(30))
                .build();
    }

    /**
     * Fetch all Jira tickets for a given CHG number.
     * Equivalent to: Get-Jira-Issues -Filter CHG -Key "CHG0261540"
     */
    public List<JiraTicket> getTicketsForChg(String chgNumber) throws Exception {
        String jql = config.buildChgJql(chgNumber);
        return executeJql(jql, "CHG:" + chgNumber);
    }

    /**
     * Execute an arbitrary JQL query and return parsed tickets.
     * Equivalent to: Get-Jira-Issues -Filter JQL -Key $JQL
     * This is the generic entry point that all query modes ultimately call.
     */
    public List<JiraTicket> executeJql(String jql, String logLabel) throws Exception {
        if (!config.isConfigured()) {
            throw new IllegalStateException(
                "Jira credentials not configured. Set roadrats.jira.user and roadrats.jira.token in application.properties or .env");
        }

        String encodedJql = URLEncoder.encode(jql, StandardCharsets.UTF_8);
        String url = config.getJiraBaseUrl() + "/rest/api/3/search/jql?fields=*all&jql="
                + encodedJql + "&maxResults=" + config.getMaxResults();

        log.info("Querying Jira [{}] - JQL length: {}, URL length: {}", logLabel, jql.length(), url.length());
        log.debug("JQL: {}", jql);

        // Build auth header (Basic auth with user:token)
        String credentials = config.getJiraUser() + ":" + config.getJiraToken();
        String encodedCreds = Base64.getEncoder().encodeToString(credentials.getBytes(StandardCharsets.UTF_8));

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Authorization", "Basic " + encodedCreds)
                .header("Accept", "application/json")
                .timeout(Duration.ofSeconds(60))
                .GET()
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() != 200) {
            log.error("Jira API returned status {}: {}", response.statusCode(), response.body());
            // Try to extract a meaningful error message
            String errorMsg = "Jira API returned status " + response.statusCode();
            try {
                JsonNode errorNode = objectMapper.readTree(response.body());
                if (errorNode.has("errorMessages")) {
                    List<String> msgs = new ArrayList<>();
                    errorNode.get("errorMessages").forEach(m -> msgs.add(m.asText()));
                    if (!msgs.isEmpty()) errorMsg += ": " + String.join("; ", msgs);
                }
            } catch (Exception ignored) {}
            throw new RuntimeException(errorMsg);
        }

        JsonNode root = objectMapper.readTree(response.body());
        JsonNode issues = root.get("issues");

        if (issues == null || !issues.isArray()) {
            log.warn("No issues array in Jira response for [{}]", logLabel);
            return Collections.emptyList();
        }

        List<JiraTicket> tickets = new ArrayList<>();
        for (JsonNode issue : issues) {
            tickets.add(parseIssue(issue));
        }

        log.info("Fetched {} tickets for [{}]", tickets.size(), logLabel);
        return tickets;
    }

    /**
     * Parse a single Jira issue JSON node into a JiraTicket.
     * Maps components using the same hash maps as Get-Component-Mapping in Jira.psm1.
     */
    private JiraTicket parseIssue(JsonNode issue) {
        JiraTicket ticket = new JiraTicket();
        String key = textOrEmpty(issue, "key");
        JsonNode fields = issue.get("fields");

        ticket.setJira(key);
        ticket.setUrl(config.buildBrowseUrl(key));
        ticket.setTitle(textOrEmpty(fields, "summary"));
        ticket.setStatus(nestedText(fields, "status", "name"));
        ticket.setResolution(nestedText(fields, "resolution", "name"));

        // Assignee
        ticket.setAssignee(nestedText(fields, "assignee", "displayName"));

        // Custom fields
        ticket.setDevTeam(nestedText(fields, "customfield_12901", "value"));
        ticket.setProductManager(nestedText(fields, "customfield_11700", "displayName"));
        ticket.setDowntimeRequired(nestedText(fields, "customfield_11707", "value"));
        ticket.setPlannedDeploymentDate(textOrEmpty(fields, "customfield_13594"));

        // Labels
        JsonNode labelsNode = fields.get("labels");
        if (labelsNode != null && labelsNode.isArray()) {
            List<String> labels = new ArrayList<>();
            labelsNode.forEach(l -> labels.add(l.asText()));
            ticket.setLabels(String.join(", ", labels));
        }

        // Components -> map to categories
        mapComponents(fields, ticket);

        // Linked issues
        mapLinkedIssues(fields, ticket);

        // Description (extract text from Atlassian Document Format)
        ticket.setDescription(extractDocumentText(fields.get("description")));

        // Implementation plan
        ticket.setImplementationPlan(extractDocumentText(fields.get("customfield_11507")));

        return ticket;
    }

    /**
     * Map Jira components to categories (Architect, DDL, DML, Web, etc.)
     * Mirrors Get-Component-Mapping from Jira.psm1
     */
    private void mapComponents(JsonNode fields, JiraTicket ticket) {
        JsonNode components = fields.get("components");
        if (components == null || !components.isArray()) {
            ticket.setArchitect("");
            ticket.setDdl("");
            ticket.setDml("");
            ticket.setWeb("");
            ticket.setChewyWmsGateway("");
            ticket.setFitnesse("");
            ticket.setNonStandard("");
            return;
        }

        List<String> architect = new ArrayList<>();
        List<String> ddl = new ArrayList<>();
        List<String> dml = new ArrayList<>();
        List<String> web = new ArrayList<>();
        List<String> gateway = new ArrayList<>();
        List<String> fitnesse = new ArrayList<>();
        List<String> nonStandard = new ArrayList<>();

        for (JsonNode comp : components) {
            String name = comp.has("name") ? comp.get("name").asText() : "";

            if (ARCHITECT_MAPPING.containsKey(name)) architect.add(ARCHITECT_MAPPING.get(name));
            if (DDL_MAPPING.containsKey(name)) ddl.add(DDL_MAPPING.get(name));
            if (DML_MAPPING.containsKey(name)) dml.add(DML_MAPPING.get(name));
            if (WEB_MAPPING.containsKey(name)) web.add(WEB_MAPPING.get(name));
            if (GATEWAY_MAPPING.containsKey(name)) gateway.add(GATEWAY_MAPPING.get(name));
            if (FITNESSE_MAPPING.containsKey(name)) fitnesse.add(FITNESSE_MAPPING.get(name));
            if (NON_STANDARD_MAPPING.containsKey(name)) nonStandard.add(NON_STANDARD_MAPPING.get(name));
        }

        ticket.setArchitect(String.join(",", architect));
        ticket.setDdl(String.join(",", ddl));
        ticket.setDml(String.join(",", dml));
        ticket.setWeb(String.join(",", web));
        ticket.setChewyWmsGateway(String.join(",", gateway));
        ticket.setFitnesse(String.join(",", fitnesse));
        ticket.setNonStandard(String.join(",", nonStandard));
    }

    /**
     * Map linked issues from issueLinks array.
     * Mirrors Get-LinkedIssues from Jira.psm1
     */
    private void mapLinkedIssues(JsonNode fields, JiraTicket ticket) {
        JsonNode links = fields.get("issueLinks");
        if (links == null || !links.isArray() || links.isEmpty()) {
            ticket.setLinkedIssues(Collections.emptyMap());
            return;
        }

        Map<String, List<String>> linkedMap = new LinkedHashMap<>();

        for (JsonNode link : links) {
            JsonNode type = link.get("type");

            // Inward issue
            if (link.has("inwardIssue") && link.get("inwardIssue") != null) {
                String typeName = type.has("inward") ? type.get("inward").asText() : "linked";
                String linkedKey = link.get("inwardIssue").has("key")
                        ? link.get("inwardIssue").get("key").asText() : "";
                if (!linkedKey.isEmpty()) {
                    linkedMap.computeIfAbsent(typeName, k -> new ArrayList<>()).add(linkedKey);
                }
            }

            // Outward issue
            if (link.has("outwardIssue") && link.get("outwardIssue") != null) {
                String typeName = type.has("outward") ? type.get("outward").asText() : "linked";
                String linkedKey = link.get("outwardIssue").has("key")
                        ? link.get("outwardIssue").get("key").asText() : "";
                if (!linkedKey.isEmpty()) {
                    linkedMap.computeIfAbsent(typeName, k -> new ArrayList<>()).add(linkedKey);
                }
            }
        }

        ticket.setLinkedIssues(linkedMap);
    }

    /**
     * Extract plain text from Atlassian Document Format (ADF) JSON.
     * Recursively walks the content tree and concatenates all text nodes.
     */
    private String extractDocumentText(JsonNode doc) {
        if (doc == null || doc.isNull()) return "";
        StringBuilder sb = new StringBuilder();
        extractTextRecursive(doc, sb);
        return sb.toString().trim();
    }

    private void extractTextRecursive(JsonNode node, StringBuilder sb) {
        if (node == null || node.isNull()) return;

        if (node.has("text")) {
            sb.append(node.get("text").asText());
        }

        if (node.has("content") && node.get("content").isArray()) {
            for (JsonNode child : node.get("content")) {
                extractTextRecursive(child, sb);
            }
            // Add newline after block-level elements
            String type = node.has("type") ? node.get("type").asText() : "";
            if ("paragraph".equals(type) || "listItem".equals(type) || "heading".equals(type)) {
                sb.append("\n");
            }
        }
    }

    // --- JSON helpers ---

    private String textOrEmpty(JsonNode node, String field) {
        if (node == null || !node.has(field) || node.get(field).isNull()) return "";
        return node.get(field).asText("");
    }

    private String nestedText(JsonNode node, String field, String subField) {
        if (node == null || !node.has(field) || node.get(field).isNull()) return "";
        JsonNode child = node.get(field);
        if (!child.has(subField) || child.get(subField).isNull()) return "";
        return child.get(subField).asText("");
    }
}

