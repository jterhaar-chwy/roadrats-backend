package com.roadrats.demo.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.roadrats.demo.config.OpenAiConfig;
import com.roadrats.demo.model.chatbot.ChatMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.*;

@Service
public class OpenAiService {

    private static final Logger logger = LoggerFactory.getLogger(OpenAiService.class);
    private final OpenAiConfig config;
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    @Autowired
    public OpenAiService(OpenAiConfig config, RestTemplate restTemplate) {
        this.config = config;
        this.restTemplate = restTemplate;
        this.objectMapper = new ObjectMapper();
    }

    public String analyzePageData(String pageType, Map<String, Object> pageData, String userQuery) {
        if (config.getApiKey() == null || config.getApiKey().isEmpty()) {
            throw new IllegalStateException("OpenAI API key is not configured. Please set OPENAI_API_KEY environment variable.");
        }

        String systemPrompt = buildSystemPrompt(pageType);
        String userPrompt = buildAnalysisPrompt(pageType, pageData, userQuery);

        return callOpenAi(systemPrompt, userPrompt);
    }

    public String summarizeData(String pageType, Map<String, Object> pageData) {
        if (config.getApiKey() == null || config.getApiKey().isEmpty()) {
            throw new IllegalStateException("OpenAI API key is not configured. Please set OPENAI_API_KEY environment variable.");
        }

        String systemPrompt = buildSystemPrompt(pageType);
        String userPrompt = buildSummaryPrompt(pageType, pageData);

        return callOpenAi(systemPrompt, userPrompt);
    }

    public String chatWithContext(String pageType, Map<String, Object> pageData, 
                                   List<ChatMessage> conversationHistory, String userMessage) {
        if (config.getApiKey() == null || config.getApiKey().isEmpty()) {
            throw new IllegalStateException("OpenAI API key is not configured. Please set OPENAI_API_KEY environment variable.");
        }

        String systemPrompt = buildSystemPrompt(pageType);
        String contextPrompt = buildContextPrompt(pageType, pageData);

        List<Map<String, String>> messages = new ArrayList<>();
        
        // Add system message with context
        Map<String, String> systemMsg = new HashMap<>();
        systemMsg.put("role", "system");
        systemMsg.put("content", systemPrompt + "\n\n" + contextPrompt);
        messages.add(systemMsg);

        // Add conversation history
        if (conversationHistory != null) {
            for (ChatMessage msg : conversationHistory) {
                Map<String, String> msgMap = new HashMap<>();
                msgMap.put("role", msg.getRole());
                msgMap.put("content", msg.getContent());
                messages.add(msgMap);
            }
        }

        // Add current user message
        Map<String, String> userMsg = new HashMap<>();
        userMsg.put("role", "user");
        userMsg.put("content", userMessage);
        messages.add(userMsg);

        return callOpenAiWithMessages(messages);
    }

    private String callOpenAi(String systemPrompt, String userPrompt) {
        List<Map<String, String>> messages = new ArrayList<>();
        
        Map<String, String> systemMsg = new HashMap<>();
        systemMsg.put("role", "system");
        systemMsg.put("content", systemPrompt);
        messages.add(systemMsg);

        Map<String, String> userMsg = new HashMap<>();
        userMsg.put("role", "user");
        userMsg.put("content", userPrompt);
        messages.add(userMsg);

        return callOpenAiWithMessages(messages);
    }

    private String callOpenAiWithMessages(List<Map<String, String>> messages) {
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setBearerAuth(config.getApiKey());

            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("model", config.getModel());
            requestBody.put("messages", messages);
            requestBody.put("max_tokens", config.getMaxTokens());
            requestBody.put("temperature", config.getTemperature());

            HttpEntity<Map<String, Object>> request = new HttpEntity<>(requestBody, headers);

            logger.debug("Calling OpenAI API with model: {}", config.getModel());
            ResponseEntity<String> response = restTemplate.exchange(
                    config.getApiUrl(),
                    HttpMethod.POST,
                    request,
                    String.class
            );

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                JsonNode jsonNode = objectMapper.readTree(response.getBody());
                JsonNode choices = jsonNode.get("choices");
                if (choices != null && choices.isArray() && choices.size() > 0) {
                    JsonNode message = choices.get(0).get("message");
                    if (message != null && message.has("content")) {
                        return message.get("content").asText();
                    }
                }
                throw new RuntimeException("Unexpected response format from OpenAI API");
            } else {
                throw new RuntimeException("OpenAI API returned error: " + response.getStatusCode());
            }
        } catch (Exception e) {
            logger.error("Error calling OpenAI API", e);
            throw new RuntimeException("Failed to call OpenAI API: " + e.getMessage(), e);
        }
    }

    private String buildSystemPrompt(String pageType) {
        switch (pageType) {
            case "srm-download":
                return "You are an expert in SRM (Supply Route Management) file validation and route management. " +
                       "You help analyze SRM route files, identify validation errors, and provide insights about route data.";
            
            case "database-errors":
                return "You are an expert in SQL Server error analysis and troubleshooting. " +
                       "You help analyze database errors, identify patterns, and suggest solutions for database issues.";
            
            case "cls-management":
                return "You are an expert in WMS (Warehouse Management System) CLS (Carrier Load Selection) queue management and routing. " +
                       "You help analyze CLS queue data, identify stuck orders, and provide insights about routing issues.";
            
            case "release-manager":
                return "You are an expert in deployment planning and Jira ticket management. " +
                       "You help analyze deployment plans, summarize release information, and provide insights about deployment schedules.";
            
            default:
                return "You are a helpful assistant that analyzes data and provides insights.";
        }
    }

    private String buildContextPrompt(String pageType, Map<String, Object> pageData) {
        try {
            String dataJson = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(pageData);
            
            // Limit context size to prevent token limit issues (approximately 8000 characters = ~2000 tokens)
            int maxContextLength = 8000;
            if (dataJson.length() > maxContextLength) {
                logger.warn("Page data JSON is {} characters, truncating to {}", dataJson.length(), maxContextLength);
                dataJson = dataJson.substring(0, maxContextLength) + "\n\n... (data truncated due to size limits)";
            }
            
            return String.format("Here is the current page data for %s:\n\n%s\n\nUse this data to answer questions and provide context-aware responses.", 
                               pageType, dataJson);
        } catch (Exception e) {
            logger.error("Error serializing page data", e);
            return String.format("Page data for %s is available but could not be serialized.", pageType);
        }
    }

    private String buildAnalysisPrompt(String pageType, Map<String, Object> pageData, String userQuery) {
        String context = buildContextPrompt(pageType, pageData);
        return context + "\n\nUser query: " + userQuery + "\n\nPlease analyze the data and provide a detailed response to the user's query.";
    }

    private String buildSummaryPrompt(String pageType, Map<String, Object> pageData) {
        String context = buildContextPrompt(pageType, pageData);
        return context + "\n\nPlease provide a concise summary of the key information in this data. " +
               "Focus on the most important points, statistics, and any notable issues or patterns.";
    }
}
