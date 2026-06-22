package com.javai.llm;

import com.javai.llm.providers.OpenAICompatibleProvider;
import com.javai.llm.providers.QwenProvider;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

public class ModelRouter implements LLMProvider {
    private final Map<String, LLMProvider> providers = new HashMap<>();
    private String activeModelName;
    private final LocalModelConfig config;

    public ModelRouter(LocalModelConfig config) {
        this.config = config;
        this.activeModelName = config.getActiveModel();
    }

    @Override
    public void initialize() throws Exception {
        // Initialize OpenAI compatible provider
        OpenAICompatibleProvider openAiProvider = new OpenAICompatibleProvider(config);
        openAiProvider.initialize();
        providers.put("openai", openAiProvider);

        // Initialize Qwen provider
        QwenProvider qwenProvider = new QwenProvider(config);
        qwenProvider.initialize();
        providers.put("qwen", qwenProvider);

        // Make sure the active model is registered, fallback to openai if not
        if (!providers.containsKey(activeModelName.toLowerCase())) {
            activeModelName = "openai";
        }
    }

    @Override
    public LLMResponse complete(LLMRequest request) throws Exception {
        LLMProvider provider = providers.get(activeModelName.toLowerCase());
        if (provider == null) {
            provider = providers.get("openai");
        }
        return provider.complete(request);
    }

    public void registerProvider(String name, LLMProvider provider) {
        providers.put(name.toLowerCase(), provider);
    }

    public boolean setActiveModel(String name) {
        if (providers.containsKey(name.toLowerCase())) {
            this.activeModelName = name.toLowerCase();
            config.setActiveModel(this.activeModelName);
            return true;
        }
        return false;
    }

    public String getActiveModelName() {
        return activeModelName;
    }

    public Set<String> getAvailableModels() {
        return providers.keySet();
    }

    public LLMProvider getActiveProvider() {
        return providers.get(activeModelName.toLowerCase());
    }
}
