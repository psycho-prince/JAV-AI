package com.javai.learning;

import java.util.Map;

public class SimilaritySearch {

    public double calculateCosineSimilarity(Map<String, Double> doc1, Map<String, Double> doc2) {
        double dotProduct = 0.0;
        double normA = 0.0;
        double normB = 0.0;
        
        for (Map.Entry<String, Double> entry : doc1.entrySet()) {
            String key = entry.getKey();
            double val = entry.getValue();
            if (doc2.containsKey(key)) {
                dotProduct += val * doc2.get(key);
            }
            normA += Math.pow(val, 2);
        }
        
        for (double val : doc2.values()) {
            normB += Math.pow(val, 2);
        }
        
        if (normA == 0.0 || normB == 0.0) {
            return 0.0;
        }
        
        return dotProduct / (Math.sqrt(normA) * Math.sqrt(normB));
    }
}
