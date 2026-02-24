package com.roadrats.demo.controller;

import com.roadrats.demo.model.chatbot.ChatRequest;
import com.roadrats.demo.model.chatbot.ChatResponse;
import com.roadrats.demo.service.OpenAiService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/chatbot")
public class ChatbotController {

    private static final Logger logger = LoggerFactory.getLogger(ChatbotController.class);

    @Autowired
    private OpenAiService openAiService;

    @PostMapping("/analyze")
    public ResponseEntity<ChatResponse> analyze(@RequestBody ChatRequest request) {
        try {
            logger.info("Analyze request for page type: {}", request.getPageType());
            
            if (request.getPageType() == null || request.getPageData() == null || request.getQuery() == null) {
                return ResponseEntity.badRequest()
                    .body(ChatResponse.error("Missing required fields: pageType, pageData, or query"));
            }

            String analysis = openAiService.analyzePageData(
                request.getPageType(),
                request.getPageData(),
                request.getQuery()
            );

            return ResponseEntity.ok(ChatResponse.successWithAnalysis(analysis));
        } catch (IllegalStateException e) {
            logger.error("OpenAI configuration error", e);
            return ResponseEntity.status(500)
                .body(ChatResponse.error("OpenAI API is not configured: " + e.getMessage()));
        } catch (Exception e) {
            logger.error("Error analyzing page data", e);
            return ResponseEntity.status(500)
                .body(ChatResponse.error("Failed to analyze data: " + e.getMessage()));
        }
    }

    @PostMapping("/summarize")
    public ResponseEntity<ChatResponse> summarize(@RequestBody ChatRequest request) {
        try {
            logger.info("Summarize request for page type: {}", request.getPageType());
            
            if (request.getPageType() == null || request.getPageData() == null) {
                return ResponseEntity.badRequest()
                    .body(ChatResponse.error("Missing required fields: pageType or pageData"));
            }

            String summary = openAiService.summarizeData(
                request.getPageType(),
                request.getPageData()
            );

            return ResponseEntity.ok(ChatResponse.successWithSummary(summary));
        } catch (IllegalStateException e) {
            logger.error("OpenAI configuration error", e);
            return ResponseEntity.status(500)
                .body(ChatResponse.error("OpenAI API is not configured: " + e.getMessage()));
        } catch (Exception e) {
            logger.error("Error summarizing page data", e);
            return ResponseEntity.status(500)
                .body(ChatResponse.error("Failed to summarize data: " + e.getMessage()));
        }
    }

    @PostMapping("/chat")
    public ResponseEntity<ChatResponse> chat(@RequestBody ChatRequest request) {
        try {
            logger.info("Chat request for page type: {}", request.getPageType());
            
            if (request.getPageType() == null || request.getPageData() == null || request.getMessage() == null) {
                return ResponseEntity.badRequest()
                    .body(ChatResponse.error("Missing required fields: pageType, pageData, or message"));
            }

            String response = openAiService.chatWithContext(
                request.getPageType(),
                request.getPageData(),
                request.getConversationHistory(),
                request.getMessage()
            );

            return ResponseEntity.ok(ChatResponse.success(response));
        } catch (IllegalStateException e) {
            logger.error("OpenAI configuration error", e);
            return ResponseEntity.status(500)
                .body(ChatResponse.error("OpenAI API is not configured: " + e.getMessage()));
        } catch (Exception e) {
            logger.error("Error processing chat message", e);
            return ResponseEntity.status(500)
                .body(ChatResponse.error("Failed to process chat message: " + e.getMessage()));
        }
    }
}
