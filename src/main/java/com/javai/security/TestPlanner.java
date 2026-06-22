package com.javai.security;

import com.javai.memory.MemoryEngine;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonNode;

import java.io.File;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

public class TestPlanner {

    public void initializePlaybookForTarget(int targetId, String playbookName, MemoryEngine memoryEngine) throws Exception {
        // Check if playbook is already initialized for this target
        String checkSql = "SELECT id FROM target_playbooks WHERE target_id = ? AND playbook_name = ?";
        int playbookId = -1;
        try (Connection conn = memoryEngine.getConnection();
             PreparedStatement stmt = conn.prepareStatement(checkSql)) {
            stmt.setInt(1, targetId);
            stmt.setString(2, playbookName);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    playbookId = rs.getInt("id");
                }
            }
        }

        if (playbookId != -1) {
            return; // Already initialized
        }

        // Read playbook definition
        File file = new File("playbooks/" + playbookName + ".json");
        if (!file.exists()) {
            // Fallback default playbook steps
            file = new File("playbooks/API.json");
        }

        ObjectMapper mapper = new ObjectMapper();
        JsonNode root = mapper.readTree(file);
        String name = root.has("name") ? root.get("name").asText() : playbookName;
        
        // Insert target playbook
        String insertPlaybook = "INSERT INTO target_playbooks (target_id, playbook_name, status, created_at) VALUES (?, ?, ?, ?)";
        try (Connection conn = memoryEngine.getConnection();
             PreparedStatement stmt = conn.prepareStatement(insertPlaybook, Statement.RETURN_GENERATED_KEYS)) {
            stmt.setInt(1, targetId);
            stmt.setString(2, name);
            stmt.setString(3, "In Progress");
            stmt.setLong(4, System.currentTimeMillis());
            stmt.executeUpdate();
            try (ResultSet generatedKeys = stmt.getGeneratedKeys()) {
                if (generatedKeys.next()) {
                    playbookId = generatedKeys.getInt(1);
                }
            }
        }

        if (playbookId == -1) return;

        // Insert steps
        String insertStep = "INSERT INTO target_playbook_steps (target_playbook_id, step_number, step_name, status, notes) VALUES (?, ?, ?, ?, ?)";
        try (Connection conn = memoryEngine.getConnection();
             PreparedStatement stmt = conn.prepareStatement(insertStep)) {
            if (root.has("steps") && root.get("steps").isArray()) {
                int number = 1;
                for (JsonNode stepNode : root.get("steps")) {
                    stmt.setInt(1, playbookId);
                    stmt.setInt(2, number++);
                    stmt.setString(3, stepNode.asText());
                    stmt.setString(4, "Pending");
                    stmt.setString(5, "");
                    stmt.executeUpdate();
                }
            }
        }
        syncCoverage(targetId, playbookName, memoryEngine);
    }

    public List<PlaybookStatus> getTargetPlaybookCoverage(int targetId, MemoryEngine memoryEngine) throws Exception {
        List<PlaybookStatus> list = new ArrayList<>();
        String sql = "SELECT id, playbook_name, status FROM target_playbooks WHERE target_id = ?";
        try (Connection conn = memoryEngine.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, targetId);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    int playbookId = rs.getInt("id");
                    String name = rs.getString("playbook_name");
                    String status = rs.getString("status");
                    
                    List<PlaybookStepStatus> steps = new ArrayList<>();
                    String stepSql = "SELECT step_number, step_name, status, notes FROM target_playbook_steps WHERE target_playbook_id = ? ORDER BY step_number ASC";
                    try (PreparedStatement stepStmt = conn.prepareStatement(stepSql)) {
                        stepStmt.setInt(1, playbookId);
                        try (ResultSet stepRs = stepStmt.executeQuery()) {
                            while (stepRs.next()) {
                                steps.add(new PlaybookStepStatus(
                                        stepRs.getInt("step_number"),
                                        stepRs.getString("step_name"),
                                        stepRs.getString("status"),
                                        stepRs.getString("notes")
                                ));
                            }
                        }
                    }
                    list.add(new PlaybookStatus(playbookId, name, status, steps));
                }
            }
        }
        return list;
    }

    public void updatePlaybookStep(int targetId, String playbookName, int stepNumber, String status, String notes, MemoryEngine memoryEngine) throws Exception {
        String findPlaybook = "SELECT id FROM target_playbooks WHERE target_id = ? AND playbook_name = ?";
        int playbookId = -1;
        try (Connection conn = memoryEngine.getConnection();
             PreparedStatement stmt = conn.prepareStatement(findPlaybook)) {
            stmt.setInt(1, targetId);
            stmt.setString(2, playbookName);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    playbookId = rs.getInt("id");
                }
            }
        }

        if (playbookId == -1) return;

        String updateSql = "UPDATE target_playbook_steps SET status = ?, notes = ? WHERE target_playbook_id = ? AND step_number = ?";
        try (Connection conn = memoryEngine.getConnection();
             PreparedStatement stmt = conn.prepareStatement(updateSql)) {
            stmt.setString(1, status);
            stmt.setString(2, notes);
            stmt.setInt(3, playbookId);
            stmt.setInt(4, stepNumber);
            stmt.executeUpdate();
        }

        // Auto update playbook overall status if all steps complete
        String checkComplete = "SELECT COUNT(*) FROM target_playbook_steps WHERE target_playbook_id = ? AND status != 'Completed'";
        try (Connection conn = memoryEngine.getConnection();
             PreparedStatement stmt = conn.prepareStatement(checkComplete)) {
            stmt.setInt(1, playbookId);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next() && rs.getInt(1) == 0) {
                    try (PreparedStatement updatePb = conn.prepareStatement("UPDATE target_playbooks SET status = 'Completed' WHERE id = ?")) {
                        updatePb.setInt(1, playbookId);
                        updatePb.executeUpdate();
                    }
                }
            }
        }
        syncCoverage(targetId, playbookName, memoryEngine);
    }

    public void syncCoverage(int targetId, String playbookName, MemoryEngine memoryEngine) throws Exception {
        int projectId = memoryEngine.getActiveProjectId();
        com.javai.storage.DatabaseManager db = memoryEngine.getDatabaseManager();
        
        int completed = 0;
        int total = 0;
        
        String countSql = "SELECT COUNT(*), SUM(CASE WHEN tps.status = 'Completed' THEN 1 ELSE 0 END) " +
                          "FROM target_playbook_steps tps " +
                          "JOIN target_playbooks tp ON tps.target_playbook_id = tp.id " +
                          "WHERE tp.target_id = ? AND tp.playbook_name = ?";
                          
        try (Connection conn = db.getConnection();
             PreparedStatement stmt = conn.prepareStatement(countSql)) {
            stmt.setInt(1, targetId);
            stmt.setString(2, playbookName);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    total = rs.getInt(1);
                    completed = rs.getInt(2);
                }
            }
        }
        
        double percent = total > 0 ? ((double) completed / total) * 100.0 : 0.0;
        
        String insertCoverage = "INSERT OR REPLACE INTO coverage (project_id, target_id, playbook_name, completed_steps, total_steps, coverage_percent, updated_at) " +
                                "VALUES (?, ?, ?, ?, ?, ?, ?)";
                                
        int coverageId = -1;
        try (Connection conn = db.getConnection();
             PreparedStatement stmt = conn.prepareStatement(insertCoverage, Statement.RETURN_GENERATED_KEYS)) {
            stmt.setInt(1, projectId);
            stmt.setInt(2, targetId);
            stmt.setString(3, playbookName);
            stmt.setInt(4, completed);
            stmt.setInt(5, total);
            stmt.setDouble(6, percent);
            stmt.setLong(7, System.currentTimeMillis());
            stmt.executeUpdate();
            try (ResultSet gk = stmt.getGeneratedKeys()) {
                if (gk.next()) {
                    coverageId = gk.getInt(1);
                }
            }
        }
        
        if (coverageId == -1) {
            String queryCov = "SELECT id FROM coverage WHERE target_id = ? AND playbook_name = ?";
            try (Connection conn = db.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(queryCov)) {
                stmt.setInt(1, targetId);
                stmt.setString(2, playbookName);
                try (ResultSet rs = stmt.executeQuery()) {
                    if (rs.next()) {
                        coverageId = rs.getInt("id");
                    }
                }
            }
        }
        
        if (coverageId == -1) return;
        
        String clearSteps = "DELETE FROM coverage_steps WHERE coverage_id = ?";
        try (Connection conn = db.getConnection();
             PreparedStatement stmt = conn.prepareStatement(clearSteps)) {
            stmt.setInt(1, coverageId);
            stmt.executeUpdate();
        }
        
        String copySteps = "INSERT INTO coverage_steps (coverage_id, step_number, step_name, status, notes) " +
                           "SELECT ?, tps.step_number, tps.step_name, tps.status, tps.notes " +
                           "FROM target_playbook_steps tps " +
                           "JOIN target_playbooks tp ON tps.target_playbook_id = tp.id " +
                           "WHERE tp.target_id = ? AND tp.playbook_name = ?";
                           
        try (Connection conn = db.getConnection();
             PreparedStatement stmt = conn.prepareStatement(copySteps)) {
            stmt.setInt(1, coverageId);
            stmt.setInt(2, targetId);
            stmt.setString(3, playbookName);
            stmt.executeUpdate();
        }
        
        String clearGaps = "DELETE FROM coverage_gaps WHERE target_id = ? AND gap_description LIKE ?";
        try (Connection conn = db.getConnection();
             PreparedStatement stmt = conn.prepareStatement(clearGaps)) {
            stmt.setInt(1, targetId);
            stmt.setString(2, playbookName + " Playbook Gap:%");
            stmt.executeUpdate();
        }
        
        String selectGaps = "SELECT step_name FROM target_playbook_steps tps " +
                            "JOIN target_playbooks tp ON tps.target_playbook_id = tp.id " +
                            "WHERE tp.target_id = ? AND tp.playbook_name = ? AND tps.status != 'Completed'";
                            
        try (Connection conn = db.getConnection();
             PreparedStatement stmt = conn.prepareStatement(selectGaps)) {
            stmt.setInt(1, targetId);
            stmt.setString(2, playbookName);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    String gapDesc = playbookName + " Playbook Gap: Untested step '" + rs.getString("step_name") + "'";
                    String insertGap = "INSERT INTO coverage_gaps (project_id, target_id, gap_description, severity, created_at) " +
                                       "VALUES (?, ?, ?, 'Medium', ?)";
                    try (PreparedStatement igStmt = conn.prepareStatement(insertGap)) {
                        igStmt.setInt(1, projectId);
                        igStmt.setInt(2, targetId);
                        igStmt.setString(3, gapDesc);
                        igStmt.setLong(4, System.currentTimeMillis());
                        igStmt.executeUpdate();
                    }
                }
            }
        }
    }

    public static class PlaybookStatus {
        private final int id;
        private final String playbookName;
        private final String status;
        private final List<PlaybookStepStatus> steps;

        public PlaybookStatus(int id, String playbookName, String status, List<PlaybookStepStatus> steps) {
            this.id = id;
            this.playbookName = playbookName;
            this.status = status;
            this.steps = steps;
        }

        public int getId() {
            return id;
        }

        public String getPlaybookName() {
            return playbookName;
        }

        public String getStatus() {
            return status;
        }

        public List<PlaybookStepStatus> getSteps() {
            return steps;
        }
    }

    public static class PlaybookStepStatus {
        private final int stepNumber;
        private final String stepName;
        private final String status;
        private final String notes;

        public PlaybookStepStatus(int stepNumber, String stepName, String status, String notes) {
            this.stepNumber = stepNumber;
            this.stepName = stepName;
            this.status = status;
            this.notes = notes;
        }

        public int getStepNumber() {
            return stepNumber;
        }

        public String getStepName() {
            return stepName;
        }

        public String getStatus() {
            return status;
        }

        public String getNotes() {
            return notes;
        }
    }
}
