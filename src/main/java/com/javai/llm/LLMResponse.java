package com.javai.llm;

public class LLMResponse {
    private String content;
    private boolean fallback;
    private String error;

    public LLMResponse() {}

    public LLMResponse(String content) {
        this.content = content;
    }

    public LLMResponse(String content, boolean fallback, String error) {
        this.content = content;
        this.fallback = fallback;
        this.error = error;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public boolean isFallback() {
        return fallback;
    }

    public void setFallback(boolean fallback) {
        this.fallback = fallback;
    }

    public String getError() {
        return error;
    }

    public void setError(String error) {
        this.error = error;
    }
}
