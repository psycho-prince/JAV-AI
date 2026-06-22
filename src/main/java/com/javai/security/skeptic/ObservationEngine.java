package com.javai.security.skeptic;

import com.javai.models.Observation;
import com.javai.storage.DatabaseManager;

import java.util.List;

public class ObservationEngine {
    private final DatabaseManager databaseManager;
    private final ObservationStore store;

    public ObservationEngine(DatabaseManager databaseManager) {
        this.databaseManager = databaseManager;
        this.store = new ObservationStore(databaseManager);
    }

    public ObservationStore getStore() {
        return store;
    }

    public int recordObservation(int projectId, int targetId, String description, String source) throws Exception {
        // High fidelity observations are 1.0 (100%) confidence by default
        int obsId = store.addObservation(projectId, targetId, description, source, 1.0);

        // Log action to journal table
        String logSql = "INSERT INTO journal (project_id, action_type, description, created_at) VALUES (?, 'Observation Added', ?, ?)";
        try (java.sql.Connection conn = databaseManager.getConnection();
             java.sql.PreparedStatement stmt = conn.prepareStatement(logSql)) {
            stmt.setInt(1, projectId);
            stmt.setString(2, "Observed: '" + description + "' via source: " + source);
            stmt.setLong(3, System.currentTimeMillis());
            stmt.executeUpdate();
        }

        // Also write to log file
        java.io.File dir = new java.io.File("workspace/journal");
        if (!dir.exists()) {
            dir.mkdirs();
        }
        java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("yyyy-MM-dd");
        String filename = "journal_" + sdf.format(new java.util.Date()) + ".txt";
        java.io.File journalFile = new java.io.File(dir, filename);

        java.text.SimpleDateFormat fullSdf = new java.text.SimpleDateFormat("HH:mm:ss");
        String logLine = String.format("[%s] [Observation Added] Observed: '%s' via source: %s\n", fullSdf.format(new java.util.Date()), description, source);
        try (java.io.FileWriter fw = new java.io.FileWriter(journalFile, true)) {
            fw.write(logLine);
        }

        return obsId;
    }

    public boolean hasObservations(int targetId) throws Exception {
        List<Observation> obs = store.getObservationsByTarget(targetId);
        return !obs.isEmpty();
    }
}
