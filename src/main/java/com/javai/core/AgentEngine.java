package com.javai.core;

import com.javai.llm.ModelRouter;
import com.javai.llm.LLMRequest;
import com.javai.llm.LLMResponse;
import com.javai.memory.MemoryEngine;
import com.javai.models.Message;
import com.javai.storage.DatabaseManager;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.List;

public class AgentEngine {
    private final ModelRouter modelRouter;
    private final MemoryEngine memoryEngine;
    private ContextBuilder contextBuilder;
    private PromptAssembler promptAssembler;

    public AgentEngine(ModelRouter modelRouter, MemoryEngine memoryEngine) {
        this.modelRouter = modelRouter;
        this.memoryEngine = memoryEngine;
    }

    public void initialize() {
        // Init context builder and prompt assembler using engines properties
    }

    public void setupContext(ContextBuilder contextBuilder, PromptAssembler promptAssembler) {
        this.contextBuilder = contextBuilder;
        this.promptAssembler = promptAssembler;
    }

    public String processQuery(String userPrompt) throws Exception {
        // 1. Retrieve current conversation message sequence from cache/database
        List<Message> history = memoryEngine.getActiveConversationHistory();
        
        // 2. Wrap and save user prompt to storage
        Message userMessage = new Message("user", userPrompt);
        history.add(userMessage);
        memoryEngine.saveMessage(userMessage);

        // Perform grounding retrieval analysis and display stats
        showRetrievalStats(userPrompt);

        // 3. Assemble prompt with active context if components are set up
        List<Message> finalMessages = history;
        if (promptAssembler != null) {
            finalMessages = promptAssembler.assemblePrompt(history);
        }

        // 4. Construct LLM request parameters
        LLMRequest request = new LLMRequest();
        request.setMessages(finalMessages);
        request.setTemperature(0.7);

        // 5. Query provider
        System.out.println("\n[AgentEngine] Querying active model router (" + modelRouter.getActiveModelName() + ")...");
        LLMResponse response = modelRouter.complete(request);
        
        String assistantResponse = response.getContent();
        
        // Append self-evaluation
        String selfEval = generateSelfEvaluation(assistantResponse, userPrompt);
        assistantResponse += selfEval;
        
        // 6. Wrap and save model response to storage
        Message assistantMessage = new Message("assistant", assistantResponse);
        memoryEngine.saveMessage(assistantMessage);

        return assistantResponse;
    }

    private String generateSelfEvaluation(String response, String userPrompt) {
        int projectId = memoryEngine.getActiveProjectId();
        DatabaseManager db = memoryEngine.getDatabaseManager();

        double groundingScore = 1.0;
        double evidenceScore = 1.0;
        double retrievalScore = 1.0;
        double confidenceScore = 0.05;

        try {
            // 1. Calculate Confidence Score from findings
            String sqlConf = "SELECT AVG(confidence) FROM findings WHERE project_id = ?";
            try (Connection conn = db.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sqlConf)) {
                stmt.setInt(1, projectId);
                try (ResultSet rs = stmt.executeQuery()) {
                    if (rs.next()) {
                        double avg = rs.getDouble(1);
                        if (!rs.wasNull()) {
                            confidenceScore = avg;
                        }
                    }
                }
            }

            // 2. Calculate Evidence Score (ratio of findings with evidence)
            String sqlEv = "SELECT COUNT(*), SUM(CASE WHEN evidence_count > 0 THEN 1 ELSE 0 END) FROM findings WHERE project_id = ?";
            try (Connection conn = db.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sqlEv)) {
                stmt.setInt(1, projectId);
                try (ResultSet rs = stmt.executeQuery()) {
                    if (rs.next()) {
                        int total = rs.getInt(1);
                        int withEv = rs.getInt(2);
                        if (total > 0) {
                            evidenceScore = (double) withEv / total;
                        }
                    }
                }
            }

            // 3. Extract keywords from user prompt & retrieved context to compute Grounding and Retrieval scores
            List<String> keywords = new java.util.ArrayList<>();
            String[] words = userPrompt.toLowerCase().split("[^a-zA-Z0-9]+");
            for (String w : words) {
                w = w.trim();
                if (w.length() >= 3 && !isStopWord(w)) {
                    keywords.add(w);
                }
            }

            // Retrieve grounded texts
            List<String> groundedTexts = new java.util.ArrayList<>();
            if (!keywords.isEmpty()) {
                try (Connection conn = db.getConnection()) {
                    // Observations
                    StringBuilder obsSql = new StringBuilder("SELECT description FROM observations WHERE project_id = ? AND (");
                    for (int i = 0; i < keywords.size(); i++) {
                        if (i > 0) obsSql.append(" OR ");
                        obsSql.append("description LIKE ?");
                    }
                    obsSql.append(")");
                    try (PreparedStatement stmt = conn.prepareStatement(obsSql.toString())) {
                        stmt.setInt(1, projectId);
                        for (int i = 0; i < keywords.size(); i++) {
                            stmt.setString(i + 2, "%" + keywords.get(i) + "%");
                        }
                        try (ResultSet rs = stmt.executeQuery()) {
                            while (rs.next()) groundedTexts.add(rs.getString(1));
                        }
                    }

                    // Notes
                    StringBuilder notesSql = new StringBuilder("SELECT content FROM notes WHERE (");
                    for (int i = 0; i < keywords.size(); i++) {
                        if (i > 0) notesSql.append(" OR ");
                        notesSql.append("title LIKE ? OR content LIKE ?");
                    }
                    notesSql.append(")");
                    try (PreparedStatement stmt = conn.prepareStatement(notesSql.toString())) {
                        int paramIdx = 1;
                        for (int i = 0; i < keywords.size(); i++) {
                            stmt.setString(paramIdx++, "%" + keywords.get(i) + "%");
                            stmt.setString(paramIdx++, "%" + keywords.get(i) + "%");
                        }
                        try (ResultSet rs = stmt.executeQuery()) {
                            while (rs.next()) groundedTexts.add(rs.getString(1));
                        }
                    }
                }
            }

            // Calculate Grounding and Retrieval
            if (!groundedTexts.isEmpty()) {
                int matchedGrounded = 0;
                String respLower = response.toLowerCase();
                for (String text : groundedTexts) {
                    // split text into words and check matching overlap
                    String[] textWords = text.toLowerCase().split("[^a-zA-Z0-9]+");
                    int matchCount = 0;
                    int validWordCount = 0;
                    for (String tw : textWords) {
                        if (tw.length() >= 3 && !isStopWord(tw)) {
                            validWordCount++;
                            if (respLower.contains(tw)) {
                                matchCount++;
                            }
                        }
                    }
                    if (validWordCount > 0 && (double) matchCount / validWordCount > 0.3) {
                        matchedGrounded++;
                    }
                }
                retrievalScore = (double) matchedGrounded / groundedTexts.size();
            }

            // Grounding check: does model claim unobserved vulnerabilities?
            String respLower = response.toLowerCase();
            boolean mentionsVulnerability = respLower.contains("vulnerability") || respLower.contains("critical") || respLower.contains("exploit") || respLower.contains("bypass");
            
            int activeFindingsCount = 0;
            String sqlFindCount = "SELECT COUNT(*) FROM findings WHERE project_id = ?";
            try (Connection conn = db.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sqlFindCount)) {
                stmt.setInt(1, projectId);
                try (ResultSet rs = stmt.executeQuery()) {
                    if (rs.next()) activeFindingsCount = rs.getInt(1);
                }
            }

            int activeObsCount = 0;
            String sqlObsCount = "SELECT COUNT(*) FROM observations WHERE project_id = ?";
            try (Connection conn = db.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sqlObsCount)) {
                stmt.setInt(1, projectId);
                try (ResultSet rs = stmt.executeQuery()) {
                    if (rs.next()) activeObsCount = rs.getInt(1);
                }
            }

            if (activeFindingsCount == 0 && activeObsCount == 0 && mentionsVulnerability) {
                groundingScore = 0.10; // low grounding since database has no observations/findings but model talks about them
            } else if (activeObsCount == 0 && activeFindingsCount == 0) {
                groundingScore = 1.0; // correctly quiet
            }

        } catch (Exception ignored) {}

        double overall = (groundingScore + evidenceScore + retrievalScore) / 3.0;
        String reliability = "Low";
        if (overall > 0.8 && confidenceScore > 0.5) {
            reliability = "High";
        } else if (overall > 0.4) {
            reliability = "Medium";
        }

        StringBuilder eval = new StringBuilder();
        eval.append("\n\n=== Self-Evaluation ===\n");
        eval.append(String.format("Grounding Score:  %.0f%%\n", groundingScore * 100));
        eval.append(String.format("Evidence Score:   %.0f%%\n", evidenceScore * 100));
        eval.append(String.format("Retrieval Score:  %.0f%%\n", retrievalScore * 100));
        eval.append(String.format("Confidence Score: %.0f%%\n", confidenceScore * 100));
        eval.append("Answer Reliability: ").append(reliability).append("\n");
        eval.append("=======================");

        return eval.toString();
    }

    private void showRetrievalStats(String userPrompt) {
        try {
            int projectId = memoryEngine.getActiveProjectId();
            String progName = memoryEngine.getActiveProgramName();
            DatabaseManager db = memoryEngine.getDatabaseManager();
            
            // Extract keywords
            List<String> keywords = new java.util.ArrayList<>();
            String[] words = userPrompt.toLowerCase().split("[^a-zA-Z0-9]+");
            for (String w : words) {
                w = w.trim();
                if (w.length() >= 3 && !isStopWord(w)) {
                    keywords.add(w);
                }
            }
            
            if (keywords.isEmpty()) return;
            
            int obsCount = 0;
            int ruleCount = 0;
            int noteCount = 0;
            int findingCount = 0;
            
            try (Connection conn = db.getConnection()) {
                // Observations count
                StringBuilder obsSql = new StringBuilder("SELECT COUNT(*) FROM observations WHERE project_id = ? AND (");
                for (int i = 0; i < keywords.size(); i++) {
                    if (i > 0) obsSql.append(" OR ");
                    obsSql.append("description LIKE ?");
                }
                obsSql.append(")");
                try (PreparedStatement stmt = conn.prepareStatement(obsSql.toString())) {
                    stmt.setInt(1, projectId);
                    for (int i = 0; i < keywords.size(); i++) {
                        stmt.setString(i + 2, "%" + keywords.get(i) + "%");
                    }
                    try (ResultSet rs = stmt.executeQuery()) {
                        if (rs.next()) obsCount = rs.getInt(1);
                    }
                }
                
                // Rules count
                if (progName != null) {
                    StringBuilder ruleSql = new StringBuilder("SELECT COUNT(*) FROM program_rules WHERE program_name = ? AND (");
                    for (int i = 0; i < keywords.size(); i++) {
                        if (i > 0) ruleSql.append(" OR ");
                        ruleSql.append("rule_text LIKE ?");
                    }
                    ruleSql.append(")");
                    try (PreparedStatement stmt = conn.prepareStatement(ruleSql.toString())) {
                        stmt.setString(1, progName);
                        for (int i = 0; i < keywords.size(); i++) {
                            stmt.setString(i + 2, "%" + keywords.get(i) + "%");
                        }
                        try (ResultSet rs = stmt.executeQuery()) {
                            if (rs.next()) ruleCount = rs.getInt(1);
                        }
                    }
                }
                
                // Notes count
                StringBuilder notesSql = new StringBuilder("SELECT COUNT(*) FROM notes WHERE (");
                for (int i = 0; i < keywords.size(); i++) {
                    if (i > 0) notesSql.append(" OR ");
                    notesSql.append("title LIKE ? OR content LIKE ?");
                }
                notesSql.append(")");
                try (PreparedStatement stmt = conn.prepareStatement(notesSql.toString())) {
                    int paramIdx = 1;
                    for (int i = 0; i < keywords.size(); i++) {
                        stmt.setString(paramIdx++, "%" + keywords.get(i) + "%");
                        stmt.setString(paramIdx++, "%" + keywords.get(i) + "%");
                    }
                    try (ResultSet rs = stmt.executeQuery()) {
                        if (rs.next()) noteCount = rs.getInt(1);
                    }
                }
                
                // Findings count
                StringBuilder findingsSql = new StringBuilder("SELECT COUNT(*) FROM findings WHERE project_id = ? AND (");
                for (int i = 0; i < keywords.size(); i++) {
                    if (i > 0) findingsSql.append(" OR ");
                    findingsSql.append("title LIKE ? OR description LIKE ?");
                }
                findingsSql.append(")");
                try (PreparedStatement stmt = conn.prepareStatement(findingsSql.toString())) {
                    stmt.setInt(1, projectId);
                    int paramIdx = 2;
                    for (int i = 0; i < keywords.size(); i++) {
                        stmt.setString(paramIdx++, "%" + keywords.get(i) + "%");
                        stmt.setString(paramIdx++, "%" + keywords.get(i) + "%");
                    }
                    try (ResultSet rs = stmt.executeQuery()) {
                        if (rs.next()) findingCount = rs.getInt(1);
                    }
                }
            }
            
            if (obsCount > 0 || ruleCount > 0 || noteCount > 0 || findingCount > 0) {
                System.out.printf("[Retrieval-First Grounding] Grounding query with %d relevant observations, %d rules, %d notes, %d findings.\n",
                        obsCount, ruleCount, noteCount, findingCount);
            }
        } catch (Exception ignored) {
            // Ignore stats errors
        }
    }
    
    private boolean isStopWord(String w) {
        String[] stops = {"the", "and", "for", "with", "this", "that", "you", "are", "have", "how", "what", "can", "test"};
        for (String s : stops) {
            if (s.equals(w)) return true;
        }
        return false;
    }
}
