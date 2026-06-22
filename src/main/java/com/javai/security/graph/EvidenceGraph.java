package com.javai.security.graph;

import com.javai.models.Observation;
import com.javai.models.Asset;
import com.javai.models.Evidence;
import com.javai.models.Finding;
import com.javai.models.Target;
import com.javai.storage.DatabaseManager;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;

public class EvidenceGraph {
    private final DatabaseManager databaseManager;

    public EvidenceGraph(DatabaseManager databaseManager) {
        this.databaseManager = databaseManager;
    }

    public List<String> traceHypothesisLineage(int findingId) throws Exception {
        List<String> trace = new ArrayList<>();
        
        // 1. Get Finding / Hypothesis info
        Finding finding = null;
        String findSql = "SELECT id, title, severity, state, confidence, description FROM findings WHERE id = ?";
        try (Connection conn = databaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(findSql)) {
            stmt.setInt(1, findingId);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    finding = new Finding(
                            rs.getInt("id"),
                            1, // projectId placeholder
                            rs.getString("title"),
                            rs.getString("description"),
                            rs.getString("severity"),
                            com.javai.models.FindingState.valueOf(rs.getString("state")),
                            rs.getDouble("confidence"),
                            0, // evidence count placeholder
                            0 // createdAt placeholder
                    );
                }
            }
        }

        if (finding == null) {
            trace.add("Finding ID " + findingId + " not found.");
            return trace;
        }

        trace.add(String.format("[Finding/Hypothesis] ID: %d | Title: '%s' | State: %s | Confidence: %.0f%%", 
                finding.getId(), finding.getTitle(), finding.getState(), finding.getConfidence() * 100));
        
        // 2. Fetch Evidence supporting this finding
        String evSql = "SELECT id, title, content FROM evidence WHERE finding_id = ?";
        try (Connection conn = databaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(evSql)) {
            stmt.setInt(1, findingId);
            try (ResultSet rs = stmt.executeQuery()) {
                int count = 0;
                while (rs.next()) {
                    count++;
                    String rawContent = rs.getString("content");
                    String contentSnippet = rawContent.replace("\n", " ").trim();
                    contentSnippet = contentSnippet.substring(0, Math.min(contentSnippet.length(), 60)) + "...";
                    trace.add(String.format("  ↳ [Evidence Node] ID: %d | Title: '%s' | Content: '%s'",
                            rs.getInt("id"), rs.getString("title"), contentSnippet));
                }
                if (count == 0) {
                    trace.add("  ↳ [Evidence Node] (None collected yet)");
                }
            }
        }

        // 3. Match Observations by description keywords or target link
        String obsSql = "SELECT id, description, source, confidence FROM observations ORDER BY created_at DESC LIMIT 5";
        try (Connection conn = databaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(obsSql);
             ResultSet rs = stmt.executeQuery()) {
            int count = 0;
            while (rs.next()) {
                String desc = rs.getString("description");
                String titleWords = finding.getTitle().toLowerCase();
                boolean match = false;
                for (String word : titleWords.split("\\s+")) {
                    if (word.length() > 3 && desc.toLowerCase().contains(word)) {
                        match = true;
                        break;
                    }
                }
                if (match || count == 0) {
                    count++;
                    trace.add(String.format("    ↳ [Observation Node] ID: %d | Source: %s | Description: '%s' | Confidence: %.0f%%",
                            rs.getInt("id"), rs.getString("source"), desc, rs.getDouble("confidence") * 100));
                }
            }
            if (count == 0) {
                trace.add("    ↳ [Observation Node] (No explicit matching observations mapped in registry)");
            }
        }

        // 4. Fetch Assets associated
        String assetSql = "SELECT id, type, value FROM assets ORDER BY created_at DESC LIMIT 3";
        try (Connection conn = databaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(assetSql);
             ResultSet rs = stmt.executeQuery()) {
            int count = 0;
            while (rs.next()) {
                count++;
                trace.add(String.format("      ↳ [Asset Node] ID: %d | Type: %s | Value: '%s'",
                        rs.getInt("id"), rs.getString("type"), rs.getString("value")));
            }
        }

        return trace;
    }
}
