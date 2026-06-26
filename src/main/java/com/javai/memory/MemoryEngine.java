package com.javai.memory;

import com.javai.models.Message;
import com.javai.storage.DatabaseManager;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonNode;

import java.io.File;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

public class MemoryEngine {
    private final DatabaseManager databaseManager;
    private String activeConversationId = "default-session";
    private int activeProjectId = 1;
    private String activeProjectName = "default";
    private String activeProgramName = null;

    public MemoryEngine(DatabaseManager databaseManager) {
        this.databaseManager = databaseManager;
    }

    public void initialize() throws Exception {
        // Seed programs from JSON files
        seedPrograms();

        // Load active project program if it exists
        String loadProjSql = "SELECT name, program_name FROM projects WHERE id = ?";
        try (Connection conn = databaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(loadProjSql)) {
            stmt.setInt(1, activeProjectId);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    this.activeProjectName = rs.getString("name");
                    this.activeProgramName = rs.getString("program_name");
                }
            }
        }
        // Ensure default session entry is created in conversations table
        try (Connection conn = databaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(
                     "INSERT OR IGNORE INTO conversations (id, title, created_at) VALUES (?, ?, ?)")) {
            stmt.setString(1, activeConversationId);
            stmt.setString(2, "Default Research Session");
            stmt.setLong(3, System.currentTimeMillis());
            stmt.executeUpdate();
        }
    }

    public List<Message> getActiveConversationHistory() throws Exception {
        List<Message> history = new ArrayList<>();
        String sql = "SELECT role, content, timestamp FROM messages WHERE conversation_id = ? ORDER BY timestamp ASC";
        
        try (Connection conn = databaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, activeConversationId);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    Message msg = new Message(rs.getString("role"), rs.getString("content"));
                    msg.setTimestamp(rs.getLong("timestamp"));
                    history.add(msg);
                }
            }
        }
        return history;
    }

    public void saveMessage(Message message) throws Exception {
        String sql = "INSERT INTO messages (conversation_id, role, content, timestamp) VALUES (?, ?, ?, ?)";
        try (Connection conn = databaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, activeConversationId);
            stmt.setString(2, message.getRole());
            stmt.setString(3, message.getContent());
            stmt.setLong(4, message.getTimestamp());
            stmt.executeUpdate();
        }
    }

    public void clearActiveConversation() throws Exception {
        String sql = "DELETE FROM messages WHERE conversation_id = ?";
        try (Connection conn = databaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, activeConversationId);
            stmt.executeUpdate();
        }
    }

    public String getActiveConversationId() {
        return activeConversationId;
    }

    public void setActiveConversationId(String id) {
        this.activeConversationId = id;
    }

    public int getActiveProjectId() {
        return activeProjectId;
    }

    public String getActiveProjectName() {
        return activeProjectName;
    }

    public boolean switchProject(String name) throws Exception {
        String sql = "SELECT id, program_name FROM projects WHERE name = ?";
        try (Connection conn = databaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, name);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    this.activeProjectId = rs.getInt("id");
                    this.activeProjectName = name;
                    this.activeProgramName = rs.getString("program_name");
                    return true;
                }
            }
        }

        // Check if name corresponds to a seeded program (e.g. K2Cloud -> "K2 Cloud")
        String normalizedInput = name.replaceAll("\\s+", "").toLowerCase();
        String matchingProgram = null;
        try (Connection conn = databaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement("SELECT name FROM programs");
             ResultSet rs = stmt.executeQuery()) {
            while (rs.next()) {
                String prog = rs.getString("name");
                if (prog.replaceAll("\\s+", "").toLowerCase().equals(normalizedInput)) {
                    matchingProgram = prog;
                    break;
                }
            }
        }

        if (matchingProgram != null) {
            // Check if any project with this program_name exists already
            String findProjSql = "SELECT id, name, program_name FROM projects WHERE name = ? OR program_name = ?";
            try (Connection conn = databaseManager.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(findProjSql)) {
                stmt.setString(1, name);
                stmt.setString(2, matchingProgram);
                try (ResultSet rs = stmt.executeQuery()) {
                    if (rs.next()) {
                        this.activeProjectId = rs.getInt("id");
                        this.activeProjectName = rs.getString("name");
                        this.activeProgramName = rs.getString("program_name");
                        return true;
                    }
                }
            }

            // Create project automatically
            int newProjId = createProject(name, "Automated project for program " + matchingProgram);
            if (newProjId != -1) {
                try (Connection conn = databaseManager.getConnection();
                     PreparedStatement stmt = conn.prepareStatement("UPDATE projects SET program_name = ? WHERE id = ?")) {
                    stmt.setString(1, matchingProgram);
                    stmt.setInt(2, newProjId);
                    stmt.executeUpdate();
                }
                this.activeProjectId = newProjId;
                this.activeProjectName = name;
                this.activeProgramName = matchingProgram;
                return true;
            }
        }

        return false;
    }

    public int createProject(String name, String description) throws Exception {
        String sql = "INSERT INTO projects (name, description, created_at) VALUES (?, ?, ?)";
        try (Connection conn = databaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            stmt.setString(1, name);
            stmt.setString(2, description);
            stmt.setLong(3, System.currentTimeMillis());
            stmt.executeUpdate();
            try (ResultSet generatedKeys = stmt.getGeneratedKeys()) {
                if (generatedKeys.next()) {
                    return generatedKeys.getInt(1);
                }
            }
        }
        return -1;
    }

    public int addFinding(String title, String severity, String description, String state, double confidence, int evidenceCount) throws Exception {
        String sql = "INSERT INTO findings (project_id, title, severity, description, state, confidence, evidence_count, created_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?)";
        try (Connection conn = databaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            stmt.setInt(1, activeProjectId);
            stmt.setString(2, title);
            stmt.setString(3, severity);
            stmt.setString(4, description);
            stmt.setString(5, state);
            stmt.setDouble(6, confidence);
            stmt.setInt(7, evidenceCount);
            stmt.setLong(8, System.currentTimeMillis());
            stmt.executeUpdate();
            try (ResultSet generatedKeys = stmt.getGeneratedKeys()) {
                if (generatedKeys.next()) {
                    return generatedKeys.getInt(1);
                }
            }
        }
        return -1;
    }

    public int addFinding(String title, String severity, String description) throws Exception {
        return addFinding(title, severity, description, "HYPOTHESIS", 0.05, 0);
    }

    public void updateFindingStatus(int findingId, String state, double confidence, String severity, int evidenceCount) throws Exception {
        String selectSql = "SELECT state, severity, title FROM findings WHERE id = ?";
        String oldState = "";
        String oldSeverity = "";
        String title = "";
        try (Connection conn = databaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(selectSql)) {
            stmt.setInt(1, findingId);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    oldState = rs.getString("state") != null ? rs.getString("state") : "";
                    oldSeverity = rs.getString("severity") != null ? rs.getString("severity") : "";
                    title = rs.getString("title") != null ? rs.getString("title") : "";
                }
            }
        }

        String sql = "UPDATE findings SET state = ?, confidence = ?, severity = ?, evidence_count = ? WHERE id = ?";
        try (Connection conn = databaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, state);
            stmt.setDouble(2, confidence);
            stmt.setString(3, severity);
            stmt.setInt(4, evidenceCount);
            stmt.setInt(5, findingId);
            stmt.executeUpdate();
        }

        if (!oldState.isEmpty()) {
            com.javai.security.skeptic.DecisionEngine decisionEngine = new com.javai.security.skeptic.DecisionEngine(databaseManager);
            if (!oldState.equalsIgnoreCase(state)) {
                String rationale = "Finding '" + title + "' state transitioned from " + oldState + " to " + state + " (Confidence: " + String.format("%.0f%%", confidence * 100) + ", Evidence Count: " + evidenceCount + ")";
                decisionEngine.recordDecision(activeProjectId, findingId, "Finding Transition", rationale);
            }
            if (!oldSeverity.equalsIgnoreCase(severity)) {
                String rationale = "Finding '" + title + "' severity changed from " + oldSeverity + " to " + severity;
                decisionEngine.recordDecision(activeProjectId, findingId, "Severity Changed", rationale);
            }
        }
    }

    public void addJournalEntry(String actionType, String description) throws Exception {
        String sql = "INSERT INTO journal (project_id, action_type, description, created_at) VALUES (?, ?, ?, ?)";
        try (Connection conn = databaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, activeProjectId);
            stmt.setString(2, actionType);
            stmt.setString(3, description);
            stmt.setLong(4, System.currentTimeMillis());
            stmt.executeUpdate();
        }

        File dir = new File("workspace/journal");
        if (!dir.exists()) {
            dir.mkdirs();
        }
        java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("yyyy-MM-dd");
        String filename = "journal_" + sdf.format(new java.util.Date()) + ".txt";
        File journalFile = new File(dir, filename);

        java.text.SimpleDateFormat fullSdf = new java.text.SimpleDateFormat("HH:mm:ss");
        String logLine = String.format("[%s] [%s] %s\n", fullSdf.format(new java.util.Date()), actionType, description);
        try (java.io.FileWriter fw = new java.io.FileWriter(journalFile, true)) {
            fw.write(logLine);
        }
    }

    public void addTask(String title, String status) throws Exception {
        String sql = "INSERT INTO tasks (project_id, title, status, created_at) VALUES (?, ?, ?, ?)";
        try (Connection conn = databaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, activeProjectId);
            stmt.setString(2, title);
            stmt.setString(3, status);
            stmt.setLong(4, System.currentTimeMillis());
            stmt.executeUpdate();
        }
    }

    public void saveKnowledge(String key, String value, String category) throws Exception {
        String sql = "INSERT OR REPLACE INTO knowledge (key, value, category, created_at) VALUES (?, ?, ?, ?)";
        try (Connection conn = databaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, key);
            stmt.setString(2, value);
            stmt.setString(3, category);
            stmt.setLong(4, System.currentTimeMillis());
            stmt.executeUpdate();
        }
    }

    public void addTarget(String domain) throws Exception {
        String sql = "INSERT OR IGNORE INTO targets (project_id, domain, created_at) VALUES (?, ?, ?)";
        try (Connection conn = databaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, activeProjectId);
            stmt.setString(2, domain);
            stmt.setLong(3, System.currentTimeMillis());
            stmt.executeUpdate();
        }
    }

    public int getTargetId(String domain) throws Exception {
        String sql = "SELECT id FROM targets WHERE project_id = ? AND domain = ?";
        try (Connection conn = databaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, activeProjectId);
            stmt.setString(2, domain);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt("id");
                }
            }
        }
        return -1;
    }

    public int createScan(int targetId, String tool, String status) throws Exception {
        String sql = "INSERT INTO scans (project_id, target_id, tool, status, created_at) VALUES (?, ?, ?, ?, ?)";
        try (Connection conn = databaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            stmt.setInt(1, activeProjectId);
            stmt.setInt(2, targetId);
            stmt.setString(3, tool);
            stmt.setString(4, status);
            stmt.setLong(5, System.currentTimeMillis());
            stmt.executeUpdate();
            try (ResultSet generatedKeys = stmt.getGeneratedKeys()) {
                if (generatedKeys.next()) {
                    return generatedKeys.getInt(1);
                }
            }
        }
        return -1;
    }

    public void updateScanStatus(int scanId, String status) throws Exception {
        String sql = "UPDATE scans SET status = ? WHERE id = ?";
        try (Connection conn = databaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, status);
            stmt.setInt(2, scanId);
            stmt.executeUpdate();
        }
    }

    public void saveScanResult(int scanId, String rawOutput) throws Exception {
        String sql = "INSERT INTO scan_results (scan_id, raw_output, created_at) VALUES (?, ?, ?)";
        try (Connection conn = databaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, scanId);
            stmt.setString(2, rawOutput);
            stmt.setLong(3, System.currentTimeMillis());
            stmt.executeUpdate();
        }
    }

    public void addAsset(int targetId, String type, String value, String metadata) throws Exception {
        String sql = "INSERT OR IGNORE INTO assets (project_id, target_id, type, value, metadata, created_at) VALUES (?, ?, ?, ?, ?, ?)";
        try (Connection conn = databaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, activeProjectId);
            stmt.setInt(2, targetId);
            stmt.setString(3, type);
            stmt.setString(4, value);
            stmt.setString(5, metadata);
            stmt.setLong(6, System.currentTimeMillis());
            stmt.executeUpdate();
        }
    }

    public int createReport(String title, String format) throws Exception {
        String sql = "INSERT INTO reports (project_id, title, format, created_at) VALUES (?, ?, ?, ?)";
        try (Connection conn = databaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            stmt.setInt(1, activeProjectId);
            stmt.setString(2, title);
            stmt.setString(3, format);
            stmt.setLong(4, System.currentTimeMillis());
            stmt.executeUpdate();
            try (ResultSet generatedKeys = stmt.getGeneratedKeys()) {
                if (generatedKeys.next()) {
                    return generatedKeys.getInt(1);
                }
            }
        }
        return -1;
    }

    public void addReportSection(int reportId, int findingId, int sortOrder) throws Exception {
        String sql = "INSERT INTO report_sections (report_id, finding_id, sort_order) VALUES (?, ?, ?)";
        try (Connection conn = databaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, reportId);
            stmt.setInt(2, findingId);
            stmt.setInt(3, sortOrder);
            stmt.executeUpdate();
        }
    }

    public void saveEvidence(int findingId, String title, String content) throws Exception {
        String sql = "INSERT INTO evidence (finding_id, title, content, created_at) VALUES (?, ?, ?, ?)";
        try (Connection conn = databaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, findingId);
            stmt.setString(2, title);
            stmt.setString(3, content);
            stmt.setLong(4, System.currentTimeMillis());
            stmt.executeUpdate();
        }

        // Record evidence acceptance decision
        com.javai.security.skeptic.DecisionEngine decisionEngine = new com.javai.security.skeptic.DecisionEngine(databaseManager);
        decisionEngine.recordDecision(activeProjectId, findingId, "Evidence Accepted", 
                "Evidence '" + title + "' (content length: " + content.length() + " characters) was accepted and attached to finding ID " + findingId);

        // Retrieve finding info to re-evaluate it via Skeptic Engine
        String findSql = "SELECT title, severity, description FROM findings WHERE id = ?";
        String fTitle = "";
        String fSeverity = "";
        String fDescription = "";
        try (Connection conn = databaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(findSql)) {
            stmt.setInt(1, findingId);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    fTitle = rs.getString("title");
                    fSeverity = rs.getString("severity");
                    fDescription = rs.getString("description");
                }
            }
        }

        // Run verification and update finding status
        com.javai.security.skeptic.SkepticEngine skeptic = new com.javai.security.skeptic.SkepticEngine(databaseManager);
        com.javai.security.skeptic.SkepticEngine.VerificationReport report = skeptic.verifyFinding(findingId, fTitle, fSeverity, fDescription);
        updateFindingStatus(findingId, report.getState().name(), report.getConfidence(), report.getSeverity(), report.getEvidenceCount());

        // Log actions to the journal
        addJournalEntry("Evidence Added", "Evidence attached to finding ID " + findingId + ": '" + title + "'");
        if (report.getState() == com.javai.models.FindingState.VALIDATED) {
            addJournalEntry("Finding Validated", "Finding ID " + findingId + " validated with confidence " + (report.getConfidence() * 100) + "%");
        }
    }

    public int getTargetCount() throws Exception {
        return getCount("SELECT COUNT(*) FROM targets WHERE project_id = ?");
    }

    public int getAssetCount() throws Exception {
        return getCount("SELECT COUNT(*) FROM assets WHERE project_id = ?");
    }

    public int getScanCount() throws Exception {
        return getCount("SELECT COUNT(*) FROM scans WHERE project_id = ?");
    }

    public int getTaskCount() throws Exception {
        return getCount("SELECT COUNT(*) FROM tasks WHERE project_id = ?");
    }

    public int getFindingCount() throws Exception {
        return getCount("SELECT COUNT(*) FROM findings WHERE project_id = ?");
    }

    public void seedPrograms() throws Exception {
        File dir = new File("knowledge/programs");
        if (!dir.exists() || !dir.isDirectory()) {
            return;
        }
        File[] files = dir.listFiles((d, name) -> name.endsWith(".json"));
        if (files == null) {
            return;
        }
        ObjectMapper mapper = new ObjectMapper();
        try (Connection conn = databaseManager.getConnection()) {
            for (File file : files) {
                try {
                    JsonNode root = mapper.readTree(file);
                    String name = root.has("name") ? root.get("name").asText() : null;
                    if (name == null) continue;
                    String type = root.has("type") ? root.get("type").asText() : "";
                    int maxBounty = root.has("max_bounty") ? root.get("max_bounty").asInt() : 0;

                    // Insert or replace program
                    String insertProg = "INSERT OR REPLACE INTO programs (name, type, max_bounty) VALUES (?, ?, ?)";
                    try (PreparedStatement stmt = conn.prepareStatement(insertProg)) {
                        stmt.setString(1, name);
                        stmt.setString(2, type);
                        stmt.setInt(3, maxBounty);
                        stmt.executeUpdate();
                    }

                    // Delete existing rules/exclusions for this program to reload fresh ones
                    try (PreparedStatement stmt = conn.prepareStatement("DELETE FROM program_rules WHERE program_name = ?")) {
                        stmt.setString(1, name);
                        stmt.executeUpdate();
                    }
                    try (PreparedStatement stmt = conn.prepareStatement("DELETE FROM program_exclusions WHERE program_name = ?")) {
                        stmt.setString(1, name);
                        stmt.executeUpdate();
                    }

                    // Insert rules (focus, low_value, forbidden)
                    String insertRule = "INSERT INTO program_rules (program_name, rule_type, rule_text) VALUES (?, ?, ?)";
                    try (PreparedStatement stmt = conn.prepareStatement(insertRule)) {
                        if (root.has("focus") && root.get("focus").isArray()) {
                            for (JsonNode node : root.get("focus")) {
                                stmt.setString(1, name);
                                stmt.setString(2, "focus");
                                stmt.setString(3, node.asText());
                                stmt.executeUpdate();
                            }
                        }
                        if (root.has("low_value") && root.get("low_value").isArray()) {
                            for (JsonNode node : root.get("low_value")) {
                                stmt.setString(1, name);
                                stmt.setString(2, "low_value");
                                stmt.setString(3, node.asText());
                                stmt.executeUpdate();
                            }
                        }
                        if (root.has("forbidden") && root.get("forbidden").isArray()) {
                            for (JsonNode node : root.get("forbidden")) {
                                stmt.setString(1, name);
                                stmt.setString(2, "forbidden");
                                stmt.setString(3, node.asText());
                                stmt.executeUpdate();
                            }
                        }
                    }

                    // Insert exclusions
                    String insertExclusion = "INSERT INTO program_exclusions (program_name, exclusion_text) VALUES (?, ?)";
                    try (PreparedStatement stmt = conn.prepareStatement(insertExclusion)) {
                        if (root.has("exclusions") && root.get("exclusions").isArray()) {
                            for (JsonNode node : root.get("exclusions")) {
                                stmt.setString(1, name);
                                stmt.setString(2, node.asText());
                                stmt.executeUpdate();
                            }
                        }
                    }

                } catch (Exception e) {
                    System.err.println("Error seeding program from file " + file.getName() + ": " + e.getMessage());
                }
            }
        }
    }

    public boolean setActiveProjectProgram(String programName) throws Exception {
        String realName = null;
        String normalizedInput = programName.replaceAll("\\s+", "").toLowerCase();
        String checkAllSql = "SELECT name FROM programs";
        try (Connection conn = databaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(checkAllSql);
             ResultSet rs = stmt.executeQuery()) {
            while (rs.next()) {
                String prog = rs.getString("name");
                if (prog.replaceAll("\\s+", "").toLowerCase().equals(normalizedInput)) {
                    realName = prog;
                    break;
                }
            }
        }

        if (realName == null) {
            return false;
        }

        String sql = "UPDATE projects SET program_name = ? WHERE id = ?";
        try (Connection conn = databaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
          stmt.setString(1, realName);
          stmt.setInt(2, activeProjectId);
          stmt.executeUpdate();
          this.activeProgramName = realName;
          return true;
        }
    }

    public String getActiveProgramName() {
        return activeProgramName;
    }

    public String getProgramType(String programName) throws Exception {
        String sql = "SELECT type FROM programs WHERE name = ?";
        try (Connection conn = databaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, programName);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getString("type");
                }
            }
        }
        return null;
    }

    public int getProgramMaxBounty(String programName) throws Exception {
        String sql = "SELECT max_bounty FROM programs WHERE name = ?";
        try (Connection conn = databaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, programName);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt("max_bounty");
                }
            }
        }
        return 0;
    }

    public List<String> getProgramRules(String programName, String ruleType) throws Exception {
        List<String> rules = new ArrayList<>();
        String sql = "SELECT rule_text FROM program_rules WHERE program_name = ? AND rule_type = ?";
        try (Connection conn = databaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, programName);
            stmt.setString(2, ruleType);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    rules.add(rs.getString("rule_text"));
                }
            }
        }
        return rules;
    }

    public List<String> getProgramExclusions(String programName) throws Exception {
        List<String> exclusions = new ArrayList<>();
        String sql = "SELECT exclusion_text FROM program_exclusions WHERE program_name = ?";
        try (Connection conn = databaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, programName);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    exclusions.add(rs.getString("exclusion_text"));
                }
            }
        }
        return exclusions;
    }

    public DatabaseManager getDatabaseManager() {
        return databaseManager;
    }

    public Connection getConnection() throws Exception {
        return databaseManager.getConnection();
    }

    private int getCount(String sql) throws Exception {
        try (Connection conn = databaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, activeProjectId);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt(1);
                }
            }
        }
        return 0;
    }
}
