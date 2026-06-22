package com.javai.security.skeptic;

import com.javai.storage.DatabaseManager;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;

public class EvidenceChecker {
    private final DatabaseManager databaseManager;

    public EvidenceChecker(DatabaseManager databaseManager) {
        this.databaseManager = databaseManager;
    }

    public int getEvidenceCount(int findingId) throws Exception {
        String sql = "SELECT COUNT(*) FROM evidence WHERE finding_id = ?";
        try (Connection conn = databaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, findingId);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt(1);
                }
            }
        }
        return 0;
    }

    public boolean hasRequestResponseEvidence(int findingId) throws Exception {
        String sql = "SELECT content FROM evidence WHERE finding_id = ?";
        try (Connection conn = databaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, findingId);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    String content = rs.getString("content").toLowerCase();
                    if (content.contains("http") || content.contains("request") || 
                        content.contains("response") || content.contains("get /") || 
                        content.contains("post /") || content.contains("curl")) {
                        return true;
                    }
                }
            }
        }
        return false;
    }
}
