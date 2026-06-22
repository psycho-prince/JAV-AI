package com.javai.models.context;

public class KnowledgeContext {
    private final String key;
    private final String value;
    private final String category;

    public KnowledgeContext(String key, String value, String category) {
        this.key = key;
        this.value = value;
        this.category = category;
    }

    public String getKey() {
        return key;
    }

    public String getValue() {
        return value;
    }

    public String getCategory() {
        return category;
    }

    @Override
    public String toString() {
        return String.format("- Category: [%s] %s => %s", category, key, value);
    }
}
