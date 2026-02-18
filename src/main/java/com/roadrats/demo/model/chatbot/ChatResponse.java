package com.roadrats.demo.model.chatbot;

import java.time.LocalDateTime;

public class ChatResponse {
    private boolean success;
    private String response;
    private String analysis;
    private String summary;
    private String error;
    private LocalDateTime timestamp;

    public ChatResponse() {
        this.timestamp = LocalDateTime.now();
    }

    public static ChatResponse success(String response) {
        ChatResponse res = new ChatResponse();
        res.setSuccess(true);
        res.setResponse(response);
        return res;
    }

    public static ChatResponse successWithAnalysis(String analysis) {
        ChatResponse res = new ChatResponse();
        res.setSuccess(true);
        res.setAnalysis(analysis);
        return res;
    }

    public static ChatResponse successWithSummary(String summary) {
        ChatResponse res = new ChatResponse();
        res.setSuccess(true);
        res.setSummary(summary);
        return res;
    }

    public static ChatResponse error(String error) {
        ChatResponse res = new ChatResponse();
        res.setSuccess(false);
        res.setError(error);
        return res;
    }

    public boolean isSuccess() {
        return success;
    }

    public void setSuccess(boolean success) {
        this.success = success;
    }

    public String getResponse() {
        return response;
    }

    public void setResponse(String response) {
        this.response = response;
    }

    public String getAnalysis() {
        return analysis;
    }

    public void setAnalysis(String analysis) {
        this.analysis = analysis;
    }

    public String getSummary() {
        return summary;
    }

    public void setSummary(String summary) {
        this.summary = summary;
    }

    public String getError() {
        return error;
    }

    public void setError(String error) {
        this.error = error;
    }

    public LocalDateTime getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(LocalDateTime timestamp) {
        this.timestamp = timestamp;
    }
}
