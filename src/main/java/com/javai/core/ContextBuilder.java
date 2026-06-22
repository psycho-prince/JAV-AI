package com.javai.core;

import com.javai.memory.MemoryEngine;
import com.javai.models.context.FindingContext;
import com.javai.models.context.KnowledgeContext;
import com.javai.models.context.ProjectContext;
import com.javai.storage.DatabaseManager;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;

public class ContextBuilder {
    private final DatabaseManager databaseManager;
    private final MemoryEngine memoryEngine;

    public ContextBuilder(DatabaseManager databaseManager, MemoryEngine memoryEngine) {
        this.databaseManager = databaseManager;
        this.memoryEngine = memoryEngine;
    }

    public MemoryEngine getMemoryEngine() {
        return memoryEngine;
    }

    public ProjectContext buildProjectContext() throws Exception {
        int activeProjectId = memoryEngine.getActiveProjectId();
        String sql = "SELECT id, name, description FROM projects WHERE id = ?";
        try (Connection conn = databaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, activeProjectId);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return new ProjectContext(
                            rs.getInt("id"),
                            rs.getString("name"),
                            rs.getString("description")
                    );
                }
            }
        }
        return new ProjectContext(1, "default", "Default security research project");
    }

    public List<FindingContext> buildFindingContexts() throws Exception {
        List<FindingContext> list = new ArrayList<>();
        int activeProjectId = memoryEngine.getActiveProjectId();
        String sql = "SELECT id, title, severity, description, state, confidence, evidence_count FROM findings WHERE project_id = ? ORDER BY created_at DESC LIMIT 10";
        try (Connection conn = databaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, activeProjectId);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    String stateStr = rs.getString("state");
                    com.javai.models.FindingState state = com.javai.models.FindingState.HYPOTHESIS;
                    if (stateStr != null) {
                        try {
                            state = com.javai.models.FindingState.valueOf(stateStr);
                        } catch (IllegalArgumentException ignored) {}
                    }
                    list.add(new FindingContext(
                            rs.getInt("id"),
                            rs.getString("title"),
                            rs.getString("severity"),
                            rs.getString("description"),
                            state,
                            rs.getDouble("confidence"),
                            rs.getInt("evidence_count")
                    ));
                }
            }
        }
        return list;
    }

    public List<KnowledgeContext> buildKnowledgeContexts() throws Exception {
        List<KnowledgeContext> list = new ArrayList<>();
        String sql = "SELECT key, value, category FROM knowledge ORDER BY created_at DESC LIMIT 10";
        try (Connection conn = databaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {
            while (rs.next()) {
                list.add(new KnowledgeContext(
                        rs.getString("key"),
                        rs.getString("value"),
                        rs.getString("category")
                ));
            }
        }
        return list;
    }

    public int getTargetCount() throws Exception {
        return memoryEngine.getTargetCount();
    }

    public int getAssetCount() throws Exception {
        return memoryEngine.getAssetCount();
    }

    public int getScanCount() throws Exception {
        return memoryEngine.getScanCount();
    }

    public int getTaskCount() throws Exception {
        return memoryEngine.getTaskCount();
    }

    public int getFindingCount() throws Exception {
        return memoryEngine.getFindingCount();
    }
}
