package com.javai.learning;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.javai.storage.DatabaseManager;
import java.io.File;
import java.nio.file.Files;
import java.sql.Connection;
import java.sql.PreparedStatement;

public class LearningEngine {
    private final DatabaseManager databaseManager;
    private final ObjectMapper objectMapper;

    public LearningEngine(DatabaseManager databaseManager) {
        this.databaseManager = databaseManager;
        this.objectMapper = new ObjectMapper();
    }

    public String learnFromFile(File file) throws Exception {
        if (!file.exists()) {
            return "Error: File " + file.getName() + " does not exist.";
        }
        
        String content = Files.readString(file.toPath()).trim();
        String name = file.getName();
        
        int learnedRules = 0;
        int learnedDefinitions = 0;
        int jsonRecords = 0;

        // Try JSON parsing first
        if (content.startsWith("{") || content.startsWith("[")) {
            try {
                JsonNode rootNode = objectMapper.readTree(content);
                String sql = "INSERT OR REPLACE INTO knowledge (key, value, category, created_at, accepted_reports, duplicates, informational_items, rewards, severity) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)";
                
                if (rootNode.isArray()) {
                    for (JsonNode node : rootNode) {
                        processJsonNode(node, name, sql);
                        jsonRecords++;
                    }
                } else {
                    processJsonNode(rootNode, name, sql);
                    jsonRecords++;
                }
                
                return String.format("Successfully parsed and learned JSON data from '%s'. Extracted and indexed %d records with quality metrics into the knowledge base.",
                        name, jsonRecords);
            } catch (Exception e) {
                // Fallback to text line parser if JSON parsing failed
                System.err.println("JSON parse failed during /learn, falling back to text parsing: " + e.getMessage());
            }
        }

        // Text parsing fallback
        String baseSql = "INSERT OR REPLACE INTO knowledge (key, value, category, created_at, accepted_reports, duplicates, informational_items, rewards, severity) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)";
        String key = "learned_" + name.replace(".", "_") + "_" + System.currentTimeMillis();
        
        // Try parsing metrics from the entire text file if they are in standard format like "Acceptance: 5"
        int txtAccepted = parseFieldInt(content, "Acceptance");
        int txtDuplicates = parseFieldInt(content, "Duplicate");
        int txtInformational = parseFieldInt(content, "Informational");
        double txtReward = parseFieldDouble(content, "Reward");
        String txtSeverity = parseFieldString(content, "Severity");

        // Store the raw file import in the knowledge base
        try (Connection conn = databaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(baseSql)) {
            stmt.setString(1, key);
            stmt.setString(2, content);
            stmt.setString(3, "Learned Source");
            stmt.setLong(4, System.currentTimeMillis());
            stmt.setInt(5, txtAccepted);
            stmt.setInt(6, txtDuplicates);
            stmt.setInt(7, txtInformational);
            stmt.setDouble(8, txtReward);
            if (txtSeverity != null) {
                stmt.setString(9, txtSeverity);
            } else {
                stmt.setNull(9, java.sql.Types.VARCHAR);
            }
            stmt.executeUpdate();
        }

        // Process line by line to extract playbooks, focus areas, rules
        String[] lines = content.split("\n");
        for (String line : lines) {
            line = line.trim();
            if (line.isEmpty()) continue;
            
            if (line.toLowerCase().contains("rule:") || line.toLowerCase().contains("focus:")) {
                String learnedKey = "rule_" + System.currentTimeMillis() + "_" + learnedRules;
                try (Connection conn = databaseManager.getConnection();
                     PreparedStatement stmt = conn.prepareStatement(baseSql)) {
                    stmt.setString(1, learnedKey);
                    stmt.setString(2, line);
                    stmt.setString(3, "Program Rules");
                    stmt.setLong(4, System.currentTimeMillis());
                    stmt.setInt(5, 0);
                    stmt.setInt(6, 0);
                    stmt.setInt(7, 0);
                    stmt.setDouble(8, 0.0);
                    stmt.setNull(9, java.sql.Types.VARCHAR);
                    stmt.executeUpdate();
                }
                learnedRules++;
            } else if (line.toLowerCase().contains("vulnerability") || line.toLowerCase().contains("exploit")) {
                String learnedKey = "def_" + System.currentTimeMillis() + "_" + learnedDefinitions;
                try (Connection conn = databaseManager.getConnection();
                     PreparedStatement stmt = conn.prepareStatement(baseSql)) {
                    stmt.setString(1, learnedKey);
                    stmt.setString(2, line);
                    stmt.setString(3, "Vulnerability Playbook");
                    stmt.setLong(4, System.currentTimeMillis());
                    stmt.setInt(5, 0);
                    stmt.setInt(6, 0);
                    stmt.setInt(7, 0);
                    stmt.setDouble(8, 0.0);
                    stmt.setNull(9, java.sql.Types.VARCHAR);
                    stmt.executeUpdate();
                }
                learnedDefinitions++;
            }
        }

        return String.format("Successfully parsed and learned from '%s'. Stored raw data. Extracted %d rule mappings and %d playbook definitions into the knowledge base.",
                name, learnedRules, learnedDefinitions);
    }

    private void processJsonNode(JsonNode node, String fileName, String sql) throws Exception {
        String key = node.has("key") ? node.get("key").asText() : ("learned_" + fileName.replace(".", "_") + "_" + System.nanoTime());
        String value = node.has("value") ? node.get("value").asText() : node.toString();
        String category = node.has("category") ? node.get("category").asText() : "Learned Ingestion";
        
        int acceptedReports = 0;
        if (node.has("accepted_reports")) acceptedReports = node.get("accepted_reports").asInt();
        else if (node.has("acceptedReports")) acceptedReports = node.get("acceptedReports").asInt();
        else if (node.has("Acceptance")) acceptedReports = node.get("Acceptance").asInt();

        int duplicates = 0;
        if (node.has("duplicates")) duplicates = node.get("duplicates").asInt();
        else if (node.has("Duplicate")) duplicates = node.get("Duplicate").asInt();

        int informationalItems = 0;
        if (node.has("informational_items")) informationalItems = node.get("informational_items").asInt();
        else if (node.has("informational")) informationalItems = node.get("informational").asInt();
        else if (node.has("Informational")) informationalItems = node.get("Informational").asInt();

        double rewards = 0.0;
        if (node.has("rewards")) rewards = node.get("rewards").asDouble();
        else if (node.has("reward")) rewards = node.get("reward").asDouble();
        else if (node.has("Reward")) rewards = node.get("Reward").asDouble();

        String severity = null;
        if (node.has("severity")) severity = node.get("severity").asText();
        else if (node.has("Severity")) severity = node.get("Severity").asText();

        try (Connection conn = databaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, key);
            stmt.setString(2, value);
            stmt.setString(3, category);
            stmt.setLong(4, System.currentTimeMillis());
            stmt.setInt(5, acceptedReports);
            stmt.setInt(6, duplicates);
            stmt.setInt(7, informationalItems);
            stmt.setDouble(8, rewards);
            if (severity != null) {
                stmt.setString(9, severity);
            } else {
                stmt.setNull(9, java.sql.Types.VARCHAR);
            }
            stmt.executeUpdate();
        }
    }

    private int parseFieldInt(String content, String label) {
        java.util.regex.Pattern p = java.util.regex.Pattern.compile("(?i)\\b" + label + "\\s*:\\s*(\\d+)");
        java.util.regex.Matcher m = p.matcher(content);
        if (m.find()) {
            return Integer.parseInt(m.group(1));
        }
        return 0;
    }

    private double parseFieldDouble(String content, String label) {
        java.util.regex.Pattern p = java.util.regex.Pattern.compile("(?i)\\b" + label + "\\s*:\\s*([0-9.]+)");
        java.util.regex.Matcher m = p.matcher(content);
        if (m.find()) {
            return Double.parseDouble(m.group(1));
        }
        return 0.0;
    }

    private String parseFieldString(String content, String label) {
        java.util.regex.Pattern p = java.util.regex.Pattern.compile("(?i)\\b" + label + "\\s*:\\s*([A-Za-z0-9_-]+)");
        java.util.regex.Matcher m = p.matcher(content);
        if (m.find()) {
            return m.group(1);
        }
        return null;
    }
}
