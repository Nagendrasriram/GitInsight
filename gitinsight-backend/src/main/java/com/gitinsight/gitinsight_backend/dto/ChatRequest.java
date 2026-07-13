package com.gitinsight.gitinsight_backend.dto;

public class ChatRequest {
    private String question;
    private String url;

    // MANDATORY: Default constructor for JSON deserialization
    public ChatRequest() {}

    // Constructor with fields (optional, but nice to have)
    public ChatRequest(String question, String url) {
        this.question = question;
        this.url = url;
    }

    public String getQuestion() { return question; }
    public void setQuestion(String question) { this.question = question; }

    public String getUrl() { return url; }
    public void setUrl(String url) { this.url = url; }
}