package com.javai.security.skeptic;

import com.javai.models.Observation;
import com.javai.storage.DatabaseManager;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

public class ObservationStore {
    private final DatabaseManager databaseManager;

    public ObservationStore(DatabaseManager databaseManager) {
        this.databaseManager = databaseManager;
    }

    public int addObservation(int projectId, int targetId, String description, String source, double confidence) throws Exception {
        String sql = "INSERT INTO observations (project_id, target_id, description, source, confidence, created_at) VALUES (?, ?, ?, ?, ?, ?)";
        try (Connection conn = databaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            stmt.setInt(1, projectId);
            stmt.setInt(2, targetId);
            stmt.setString(3, description);
            stmt.setString(4, source);
            stmt.setDouble(5, confidence);
            stmt.setLong(6, System.currentTimeMillis());
            stmt.executeUpdate();
            try (ResultSet gk = stmt.getGeneratedKeys()) {
                if (gk.next()) {
                    return gk.getInt(1);
                }
            }
        }
        return -1;
    }

    public List<Observation> getObservationsByTarget(int targetId) throws Exception {
        List<Observation> list = new ArrayList<>();
        String sql = "SELECT id, project_id, target_id, description, source, confidence, created_at FROM observations WHERE target_id = ? ORDER BY created_at DESC";
        try (Connection conn = databaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, targetId);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    list.add(new Observation(
                            rs.getInt("id"),
                            rs.getInt("project_id"),
                            rs.getInt("target_id"),
                            rs.getString("description"),
                            rs.getString("source"),
                            rs.getDouble("confidence"),
                            rs.getLong("created_at")
                    ));
                }
            }
        }
        return list;
    }
}
