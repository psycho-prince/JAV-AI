package com.javai.security.skeptic;

import com.javai.storage.DatabaseManager;
import java.io.File;
import java.io.FileWriter;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.text.SimpleDateFormat;
import java.util.Date;

public class DecisionEngine {
    private final DatabaseManager databaseManager;

    public DecisionEngine(DatabaseManager databaseManager) {
        this.databaseManager = databaseManager;
    }

    public void recordDecision(int projectId, Integer findingId, String decisionType, String rationale) throws Exception {
        // 1. Save to SQLite database
        String sql = "INSERT INTO decisions (project_id, finding_id, decision_type, rationale, created_at) VALUES (?, ?, ?, ?, ?)";
        try (Connection conn = databaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, projectId);
            if (findingId != null) {
                stmt.setInt(2, findingId);
            } else {
                stmt.setNull(2, java.sql.Types.INTEGER);
            }
            stmt.setString(3, decisionType);
            stmt.setString(4, rationale);
            stmt.setLong(5, System.currentTimeMillis());
            stmt.executeUpdate();
        }

        // 2. Log to file system under workspace/decisions/ folder
        File dir = new File("workspace/decisions");
        if (!dir.exists()) {
            dir.mkdirs();
        }
        
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd");
        String filename = "decisions_" + sdf.format(new Date()) + ".txt";
        File decisionFile = new File(dir, filename);

        SimpleDateFormat fullSdf = new SimpleDateFormat("HH:mm:ss");
        String logLine = String.format("[%s] [Decision: %s] [Finding ID: %s] %s\n",
                fullSdf.format(new Date()),
                decisionType,
                findingId != null ? String.valueOf(findingId) : "N/A",
                rationale);

        try (FileWriter fw = new FileWriter(decisionFile, true)) {
            fw.write(logLine);
        }
    }
}
