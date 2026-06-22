package com.javai.llm;

public class LLMResponse {
    private String content;

    public LLMResponse() {}

    public LLMResponse(String content) {
        this.content = content;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }
}
