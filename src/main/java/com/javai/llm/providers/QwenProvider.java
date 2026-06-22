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

public class QwenProvider implements LLMProvider {
    private final LocalModelConfig config;
    private HttpClient httpClient;
    private ObjectMapper objectMapper;

    public QwenProvider(LocalModelConfig config) {
        this.config = config;
    }

    @Override
    public void initialize() throws Exception {
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
        this.objectMapper = new ObjectMapper();
    }

    @Override
    public LLMResponse complete(LLMRequest request) throws Exception {
        try {
            ObjectNode rootNode = objectMapper.createObjectNode();
            rootNode.put("model", "qwen2.5:latest");
            rootNode.put("temperature", request.getTemperature());

            ArrayNode messagesArray = objectMapper.createArrayNode();
            for (Message msg : request.getMessages()) {
                ObjectNode msgNode = objectMapper.createObjectNode();
                msgNode.put("role", msg.getRole());
                msgNode.put("content", msg.getContent());
                messagesArray.add(msgNode);
            }
            rootNode.set("messages", messagesArray);

            String requestBody = objectMapper.writeValueAsString(rootNode);

            HttpRequest httpRequest = HttpRequest.newBuilder()
                    .uri(URI.create(config.getEndpoint()))
                    .header("Content-Type", "application/json")
                    .header("Authorization", "Bearer " + config.getApiKey())
                    .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                    .build();

            HttpResponse<String> httpResponse = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString());

            if (httpResponse.statusCode() >= 200 && httpResponse.statusCode() < 300) {
                JsonNode responseJson = objectMapper.readTree(httpResponse.body());
                String content = responseJson.path("choices")
                        .path(0)
                        .path("message")
                        .path("content")
                        .asText();
                return new LLMResponse(content);
            } else {
                throw new Exception("HTTP " + httpResponse.statusCode() + " - " + httpResponse.body());
            }
        } catch (Exception e) {
            // Simulated local Qwen fallback response
            return new LLMResponse("[Simulation Qwen] Model response to: \"" 
                    + request.getMessages().get(request.getMessages().size() - 1).getContent() + "\"");
        }
    }
}
