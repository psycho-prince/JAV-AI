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

public class GeminiProvider implements LLMProvider {
    private final LocalModelConfig config;
    private HttpClient httpClient;
    private ObjectMapper objectMapper;

    public GeminiProvider(LocalModelConfig config) {
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
            return new LLMResponse("[Simulation Gemini] Gemini API Key is not configured. Feed API key using `/model configure key <api_key>`");
        }

        String model = config.getModelName();
        if (!model.contains("gemini")) {
            model = "gemini-1.5-flash";
        }

        String uriStr = "https://generativelanguage.googleapis.com/v1beta/models/" + model + ":generateContent?key=" + apiKey;

        String requestBody = "";
        try {
            ObjectNode rootNode = objectMapper.createObjectNode();
            ArrayNode contentsArray = objectMapper.createArrayNode();
            
            // Convert messages to Gemini format
            for (Message msg : request.getMessages()) {
                ObjectNode contentNode = objectMapper.createObjectNode();
                String role = msg.getRole().equals("assistant") ? "model" : "user";
                contentNode.put("role", role);
                
                ArrayNode partsArray = objectMapper.createArrayNode();
                ObjectNode textNode = objectMapper.createObjectNode();
                textNode.put("text", msg.getContent());
                partsArray.add(textNode);
                
                contentNode.set("parts", partsArray);
                contentsArray.add(contentNode);
            }
            rootNode.set("contents", contentsArray);

            // Optional settings
            ObjectNode configNode = objectMapper.createObjectNode();
            configNode.put("temperature", request.getTemperature());
            rootNode.set("generationConfig", configNode);

            requestBody = objectMapper.writeValueAsString(rootNode);
        } catch (Exception e) {
            throw new Exception("Gemini serialization error: " + e.getMessage());
        }

        try {
            HttpRequest httpRequest = HttpRequest.newBuilder()
                    .uri(URI.create(uriStr))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                    .timeout(Duration.ofSeconds(config.getTimeoutSeconds()))
                    .build();

            HttpResponse<String> httpResponse = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString());

            if (httpResponse.statusCode() >= 200 && httpResponse.statusCode() < 300) {
                JsonNode responseJson = objectMapper.readTree(httpResponse.body());
                String content = responseJson.path("candidates")
                        .path(0)
                        .path("content")
                        .path("parts")
                        .path(0)
                        .path("text")
                        .asText();
                return new LLMResponse(content);
            } else {
                throw new Exception("HTTP " + httpResponse.statusCode() + " - " + httpResponse.body());
            }
        } catch (Exception e) {
            return new LLMResponse("[Simulation Gemini] Failed to query Google Gemini: " + e.getMessage()
                    + ". Fallback query content was: \"" + request.getMessages().get(request.getMessages().size() - 1).getContent() + "\"",
                    true,
                    e.getMessage());
        }
    }
}
