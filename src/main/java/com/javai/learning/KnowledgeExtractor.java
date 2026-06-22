package com.javai.learning;

import java.util.ArrayList;
import java.util.List;

public class KnowledgeExtractor {

    public List<String> extractConcepts(String content) {
        List<String> concepts = new ArrayList<>();
        String[] lines = content.split("\n");
        for (String line : lines) {
            line = line.trim();
            if (line.startsWith("#") || line.contains("vulnerability") || line.contains("exploit") || line.contains("bypass")) {
                concepts.add(line);
            }
        }
        return concepts;
    }
}
