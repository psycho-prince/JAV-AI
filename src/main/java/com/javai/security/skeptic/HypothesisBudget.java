package com.javai.security.skeptic;

import com.javai.storage.DatabaseManager;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;

public class HypothesisBudget {
    private final DatabaseManager databaseManager;
    private final ObservationEngine observationEngine;
    private static final int MAX_ACTIVE_HYPOTHESES = 3;

    public HypothesisBudget(DatabaseManager databaseManager) {
        this.databaseManager = databaseManager;
        this.observationEngine = new ObservationEngine(databaseManager);
    }

    public boolean isBudgetExceeded(int projectId, int targetId) throws Exception {
        // Count active hypotheses for the project (findings in HYPOTHESIS state)
        String countSql = "SELECT COUNT(*) FROM findings WHERE project_id = ? AND state = 'HYPOTHESIS'";
        int activeCount = 0;
        try (Connection conn = databaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(countSql)) {
            stmt.setInt(1, projectId);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    activeCount = rs.getInt(1);
                }
            }
        }

        if (activeCount < MAX_ACTIVE_HYPOTHESES) {
            return false;
        }

        // If active count >= 3, require evidence OR explicit observations to exist for the target area
        boolean hasEvidenceOrObservation = false;
        
        // Check for observations
        if (observationEngine.hasObservations(targetId)) {
            hasEvidenceOrObservation = true;
        }
        
        // Check for evidence count > 0 on any finding in the active project
        if (!hasEvidenceOrObservation) {
            String evidenceSql = "SELECT COUNT(*) FROM evidence e JOIN findings f ON e.finding_id = f.id WHERE f.project_id = ?";
            try (Connection conn = databaseManager.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(evidenceSql)) {
                stmt.setInt(1, projectId);
                try (ResultSet rs = stmt.executeQuery()) {
                    if (rs.next()) {
                        hasEvidenceOrObservation = rs.getInt(1) > 0;
                    }
                }
            }
        }

        return !hasEvidenceOrObservation;
    }
}
