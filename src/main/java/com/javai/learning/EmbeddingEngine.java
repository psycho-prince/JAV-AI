package com.javai.learning;

import java.util.HashMap;
import java.util.Map;

public class EmbeddingEngine {
    
    public Map<String, Double> computeTFIDF(String text) {
        Map<String, Double> tf = new HashMap<>();
        String[] words = text.toLowerCase().split("\\W+");
        for (String w : words) {
            if (w.length() > 3) {
                tf.put(w, tf.getOrDefault(w, 0.0) + 1.0);
            }
        }
        double total = words.length;
        for (Map.Entry<String, Double> entry : tf.entrySet()) {
            entry.setValue(entry.getValue() / total);
        }
        return tf;
    }
}
