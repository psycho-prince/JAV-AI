package com.javai.llm.providers;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.javai.llm.LLMProvider;
import com.javai.llm.LLMRequest;
import com.javai.llm.LLMResponse;
import com.javai.llm.LocalModelConfig;
import com.javai.models.Message;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

public class ClaudeProvider implements LLMProvider {
    private final LocalModelConfig config;
    private HttpClient httpClient;
    private ObjectMapper objectMapper;

    public ClaudeProvider(LocalModelConfig config) {
        this.config = config;
    }

    @Override
    public void initialize() throws Exception {
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(config.getTimeoutSeconds()))
                .build();
        this.objectMapper = new ObjectMapper();
    }

    @Override
    public LLMResponse complete(LLMRequest request) throws Exception {
        String apiKey = config.getApiKey();
        if (apiKey == null || apiKey.isEmpty() || apiKey.equals("na")) {
            return new LLMResponse("[Simulation Claude] Claude API Key is not configured. Feed key using `/model configure key <api_key>`");
        }

        String model = config.getModelName();
        if (!model.contains("claude")) {
            model = "claude-3-5-sonnet-20241022";
        }

        String requestBody = "";
        try {
            ObjectNode rootNode = objectMapper.createObjectNode();
            rootNode.put("model", model);
            rootNode.put("max_tokens", 2048);
            rootNode.put("temperature", request.getTemperature());

            ArrayNode messagesArray = objectMapper.createArrayNode();
            String systemPrompt = "";
            
            for (Message msg : request.getMessages()) {
                if (msg.getRole().equals("system")) {
                    systemPrompt = msg.getContent();
                } else {
                    ObjectNode msgNode = objectMapper.createObjectNode();
                    msgNode.put("role", msg.getRole());
                    msgNode.put("content", msg.getContent());
                    messagesArray.add(msgNode);
                }
            }
            rootNode.set("messages", messagesArray);
            
            if (!systemPrompt.isEmpty()) {
                rootNode.put("system", systemPrompt);
            }

            requestBody = objectMapper.writeValueAsString(rootNode);
        } catch (Exception e) {
            throw new Exception("Claude serialization error: " + e.getMessage());
        }

        try {
            HttpRequest httpRequest = HttpRequest.newBuilder()
                    .uri(URI.create("https://api.anthropic.com/v1/messages"))
                    .header("Content-Type", "application/json")
                    .header("x-api-key", apiKey)
                    .header("anthropic-version", "2023-06-01")
                    .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                    .timeout(Duration.ofSeconds(config.getTimeoutSeconds()))
                    .build();

            HttpResponse<String> httpResponse = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString());

            if (httpResponse.statusCode() >= 200 && httpResponse.statusCode() < 300) {
                JsonNode responseJson = objectMapper.readTree(httpResponse.body());
                String content = responseJson.path("content")
                        .path(0)
                        .path("text")
                        .asText();
                return new LLMResponse(content);
            } else {
                throw new Exception("HTTP " + httpResponse.statusCode() + " - " + httpResponse.body());
            }
        } catch (Exception e) {
            return new LLMResponse("[Simulation Claude] Failed to query Anthropic Claude: " + e.getMessage()
                    + ". Fallback query content was: \"" + request.getMessages().get(request.getMessages().size() - 1).getContent() + "\"",
                    true,
                    e.getMessage());
        }
    }
}
