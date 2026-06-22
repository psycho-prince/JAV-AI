package com.javai.core;

import com.javai.models.context.FindingContext;
import com.javai.models.context.KnowledgeContext;
import com.javai.models.context.ProjectContext;
import com.javai.models.Message;
import com.javai.storage.DatabaseManager;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;

public class PromptAssembler {
    private final ContextBuilder contextBuilder;

    public PromptAssembler(ContextBuilder contextBuilder) {
        this.contextBuilder = contextBuilder;
    }

    public List<Message> assemblePrompt(List<Message> history) throws Exception {
        // Build fresh contexts
        ProjectContext project = contextBuilder.buildProjectContext();
        List<FindingContext> findings = contextBuilder.buildFindingContexts();
        List<KnowledgeContext> knowledge = contextBuilder.buildKnowledgeContexts();

        // Build system instruction prompt with injected context
        StringBuilder systemPrompt = new StringBuilder();
        systemPrompt.append("You are JavAI, an advanced agentic AI security research assistant.\n");
        systemPrompt.append("Maintain context of the active project and targets at all times.\n\n");
        
        systemPrompt.append("=== ACTIVE CONTEXT ===\n");
        systemPrompt.append("Current Project: ").append(project.getName()).append("\n");
        systemPrompt.append("Description: ").append(project.getDescription()).append("\n");
        String progName = contextBuilder.getMemoryEngine().getActiveProgramName();
        if (progName != null) {
            systemPrompt.append("Active Program: ").append(progName).append("\n");
            systemPrompt.append("Program Type: ").append(contextBuilder.getMemoryEngine().getProgramType(progName)).append("\n");
            systemPrompt.append("High Value Focus Areas:\n");
            for (String focus : contextBuilder.getMemoryEngine().getProgramRules(progName, "focus")) {
                systemPrompt.append("  - ").append(focus).append("\n");
            }
            systemPrompt.append("Low Value / Out of Scope Areas:\n");
            for (String low : contextBuilder.getMemoryEngine().getProgramRules(progName, "low_value")) {
                systemPrompt.append("  - ").append(low).append("\n");
            }
            systemPrompt.append("Forbidden Testing Areas:\n");
            for (String forbidden : contextBuilder.getMemoryEngine().getProgramRules(progName, "forbidden")) {
                systemPrompt.append("  - ").append(forbidden).append("\n");
            }
            systemPrompt.append("Rules & Exclusions:\n");
            for (String excl : contextBuilder.getMemoryEngine().getProgramExclusions(progName)) {
                systemPrompt.append("  - ").append(excl).append("\n");
            }
        }
        systemPrompt.append("Targets: ").append(contextBuilder.getTargetCount()).append("\n");
        systemPrompt.append("Assets: ").append(contextBuilder.getAssetCount()).append("\n");
        systemPrompt.append("Scans Run: ").append(contextBuilder.getScanCount()).append("\n");
        systemPrompt.append("Open Tasks: ").append(contextBuilder.getTaskCount()).append("\n");
        systemPrompt.append("Findings Recorded: ").append(contextBuilder.getFindingCount()).append("\n\n");

        systemPrompt.append("Recent Findings:\n");
        if (findings.isEmpty()) {
            systemPrompt.append("- No findings recorded yet.\n");
        } else {
            for (FindingContext f : findings) {
                systemPrompt.append(f.toString()).append("\n");
            }
        }
        systemPrompt.append("\n");

        systemPrompt.append("Knowledge Base / Methodology:\n");
        if (knowledge.isEmpty()) {
            systemPrompt.append("- No general knowledge base entries available.\n");
        } else {
            for (KnowledgeContext k : knowledge) {
                systemPrompt.append(k.toString()).append("\n");
            }
        }
        systemPrompt.append("======================\n\n");

        // Grounding Context Injection
        String lastUserMessage = "";
        if (history != null && !history.isEmpty()) {
            lastUserMessage = history.get(history.size() - 1).getContent();
        }
        String groundedContext = retrieveGroundedContext(lastUserMessage, project.getId());
        if (!groundedContext.isEmpty()) {
            systemPrompt.append(groundedContext).append("\n");
        }

        // Mandate custom workspace rules: prioritize observations and evidence
        systemPrompt.append("When providing analysis or answering queries, you MUST structure your responses to include the following sections:\n");
        systemPrompt.append("1. Observations\n");
        systemPrompt.append("2. Evidence\n");
        systemPrompt.append("3. Coverage\n");
        systemPrompt.append("4. Untested Areas / Playbook Steps\n");
        systemPrompt.append("5. Research Journal / Timeline\n\n");

        List<Message> assembled = new ArrayList<>();
        // Inject system instructions as the very first message
        assembled.add(new Message("system", systemPrompt.toString()));
        
        // Append user conversation history (filtering out any previous system prompts if any were recorded)
        for (Message msg : history) {
            if (!"system".equals(msg.getRole())) {
                assembled.add(msg);
            }
        }

        return assembled;
    }

    private String retrieveGroundedContext(String userPrompt, int projectId) {
        List<String> keywords = extractKeywords(userPrompt);
        if (keywords.isEmpty()) return "";

        StringBuilder sb = new StringBuilder();
        DatabaseManager db = contextBuilder.getMemoryEngine().getDatabaseManager();

        try (Connection conn = db.getConnection()) {
            // 1. Query observations
            StringBuilder obsSql = new StringBuilder("SELECT description, source FROM observations WHERE project_id = ? AND (");
            for (int i = 0; i < keywords.size(); i++) {
                if (i > 0) obsSql.append(" OR ");
                obsSql.append("description LIKE ?");
            }
            obsSql.append(") LIMIT 3");

            try (PreparedStatement stmt = conn.prepareStatement(obsSql.toString())) {
                stmt.setInt(1, projectId);
                for (int i = 0; i < keywords.size(); i++) {
                    stmt.setString(i + 2, "%" + keywords.get(i) + "%");
                }
                try (ResultSet rs = stmt.executeQuery()) {
                    boolean sectionHeader = false;
                    while (rs.next()) {
                        if (!sectionHeader) {
                            sb.append("=== RETRIEVED RELEVANT OBSERVATIONS ===\n");
                            sectionHeader = true;
                        }
                        sb.append("  - [").append(rs.getString("source")).append("] ").append(rs.getString("description")).append("\n");
                    }
                }
            }
        } catch (Exception ignored) {}

        try (Connection conn = db.getConnection()) {
            // 2. Query program rules
            String progName = contextBuilder.getMemoryEngine().getActiveProgramName();
            if (progName != null) {
                StringBuilder ruleSql = new StringBuilder("SELECT rule_text, rule_type FROM program_rules WHERE program_name = ? AND (");
                for (int i = 0; i < keywords.size(); i++) {
                    if (i > 0) ruleSql.append(" OR ");
                    ruleSql.append("rule_text LIKE ?");
                }
                ruleSql.append(") LIMIT 3");

                try (PreparedStatement stmt = conn.prepareStatement(ruleSql.toString())) {
                    stmt.setString(1, progName);
                    for (int i = 0; i < keywords.size(); i++) {
                        stmt.setString(i + 2, "%" + keywords.get(i) + "%");
                    }
                    try (ResultSet rs = stmt.executeQuery()) {
                        boolean sectionHeader = false;
                        while (rs.next()) {
                            if (!sectionHeader) {
                                sb.append("=== RETRIEVED RELEVANT PROGRAM RULES ===\n");
                                sectionHeader = true;
                            }
                            sb.append("  - [").append(rs.getString("rule_type")).append("] ").append(rs.getString("rule_text")).append("\n");
                        }
                    }
                }
            }
        } catch (Exception ignored) {}

        try (Connection conn = db.getConnection()) {
            // 3. Query notes
            StringBuilder notesSql = new StringBuilder("SELECT title, content FROM notes WHERE (");
            for (int i = 0; i < keywords.size(); i++) {
                if (i > 0) notesSql.append(" OR ");
                notesSql.append("title LIKE ? OR content LIKE ?");
            }
            notesSql.append(") LIMIT 3");

            try (PreparedStatement stmt = conn.prepareStatement(notesSql.toString())) {
                int paramIdx = 1;
                for (int i = 0; i < keywords.size(); i++) {
                    stmt.setString(paramIdx++, "%" + keywords.get(i) + "%");
                    stmt.setString(paramIdx++, "%" + keywords.get(i) + "%");
                }
                try (ResultSet rs = stmt.executeQuery()) {
                    boolean sectionHeader = false;
                    while (rs.next()) {
                        if (!sectionHeader) {
                            sb.append("=== RETRIEVED RELEVANT RESEARCH NOTES ===\n");
                            sectionHeader = true;
                        }
                        sb.append("  - ").append(rs.getString("title")).append(": ").append(rs.getString("content")).append("\n");
                    }
                }
            }
        } catch (Exception ignored) {}

        try (Connection conn = db.getConnection()) {
            // 4. Query findings
            StringBuilder findingsSql = new StringBuilder("SELECT title, description, state, severity FROM findings WHERE project_id = ? AND (");
            for (int i = 0; i < keywords.size(); i++) {
                if (i > 0) findingsSql.append(" OR ");
                findingsSql.append("title LIKE ? OR description LIKE ?");
            }
            findingsSql.append(") LIMIT 3");

            try (PreparedStatement stmt = conn.prepareStatement(findingsSql.toString())) {
                stmt.setInt(1, projectId);
                int paramIdx = 2;
                for (int i = 0; i < keywords.size(); i++) {
                    stmt.setString(paramIdx++, "%" + keywords.get(i) + "%");
                    stmt.setString(paramIdx++, "%" + keywords.get(i) + "%");
                }
                try (ResultSet rs = stmt.executeQuery()) {
                    boolean sectionHeader = false;
                    while (rs.next()) {
                        if (!sectionHeader) {
                            sb.append("=== RETRIEVED RELEVANT FINDINGS ===\n");
                            sectionHeader = true;
                        }
                        sb.append("  - [").append(rs.getString("state")).append("] ")
                                .append(rs.getString("title")).append(" (").append(rs.getString("severity")).append("): ")
                                .append(rs.getString("description")).append("\n");
                    }
                }
            }
        } catch (Exception ignored) {}

        if (sb.length() > 0) {
            sb.append("==================================\n");
        }
        return sb.toString();
    }

    private List<String> extractKeywords(String query) {
        List<String> keywords = new ArrayList<>();
        if (query == null) return keywords;
        String[] words = query.toLowerCase().split("[^a-zA-Z0-9]+");
        for (String w : words) {
            w = w.trim();
            if (w.length() >= 3 && !isStopWord(w)) {
                keywords.add(w);
            }
        }
        return keywords;
    }

    private boolean isStopWord(String w) {
        String[] stops = {"the", "and", "for", "with", "this", "that", "you", "are", "have", "how", "what", "can", "test"};
        for (String s : stops) {
            if (s.equals(w)) return true;
        }
        return false;
    }
}
