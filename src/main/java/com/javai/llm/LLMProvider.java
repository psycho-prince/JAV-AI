package com.javai.llm;

public interface LLMProvider {
    void initialize() throws Exception;
    LLMResponse complete(LLMRequest request) throws Exception;
}
