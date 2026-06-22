package com.javai.security.skeptic;

import com.javai.llm.LLMRequest;
import com.javai.llm.LLMResponse;
import com.javai.llm.ModelRouter;
import com.javai.memory.MemoryEngine;
import com.javai.models.Message;
import com.javai.storage.DatabaseManager;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;

public class CouncilEngine {
    private final DatabaseManager databaseManager;
    private final ModelRouter modelRouter;
    private final MemoryEngine memoryEngine;

    public CouncilEngine(DatabaseManager databaseManager, ModelRouter modelRouter, MemoryEngine memoryEngine) {
        this.databaseManager = databaseManager;
        this.modelRouter = modelRouter;
        this.memoryEngine = memoryEngine;
    }

    public void holdDebate(int findingId) throws Exception {
        // 1. Retrieve finding information
        String title = "";
        String severity = "";
        String description = "";
        String state = "";
        double confidence = 0.0;
        int activeProjectId = memoryEngine.getActiveProjectId();

        String findSql = "SELECT title, severity, description, state, confidence FROM findings WHERE id = ?";
        try (Connection conn = databaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(findSql)) {
            stmt.setInt(1, findingId);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    title = rs.getString("title");
                    severity = rs.getString("severity");
                    description = rs.getString("description");
                    state = rs.getString("state");
                    confidence = rs.getDouble("confidence");
                } else {
                    System.out.println("\u001B[31m[Error] Finding ID " + findingId + " not found.\u001B[0m");
                    return;
                }
            }
        }

        // 2. Retrieve all attached evidence
        List<String> evidenceList = new ArrayList<>();
        String evSql = "SELECT title, content FROM evidence WHERE finding_id = ?";
        try (Connection conn = databaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(evSql)) {
            stmt.setInt(1, findingId);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    evidenceList.add(rs.getString("title") + ": " + rs.getString("content"));
                }
            }
        }

        System.out.println("\n\u001B[35m==============================================================");
        System.out.println("                   SECURITY COUNCIL DEBATE                     ");
        System.out.println("==============================================================\u001B[0m");
        System.out.println("Target Finding: [\u001B[33m" + severity + "\u001B[0m] " + title + " (ID: " + findingId + ")");
        System.out.println("State:          " + state + " (Confidence: " + String.format("%.0f%%", confidence * 100) + ")");
        System.out.println("Evidence Count: " + evidenceList.size());
        System.out.println("--------------------------------------------------------------");

        String context = String.format("Finding Title: %s\nDescription: %s\nOriginal Severity: %s\nEvidence Attached:\n%s",
                title, description, severity, evidenceList.isEmpty() ? "- None -" : String.join("\n", evidenceList));

        // 3. Spawning The Exploiter (Adversarial Attacker)
        System.out.println("\n\u001B[31m[Council] Spawning the Exploiter persona (Escalation Analysis)...\u001B[0m");
        String exploiterPrompt = "You are a professional penetration tester focused on maximizing impact. "
                + "Analyze the following finding context and argue how this finding can be chained or escalated to a higher severity. "
                + "Be highly specific and technical, avoiding generic advice. Keep your response under 150 words.\n\nContext:\n" + context;

        String exploiterArg = queryPersona(exploiterPrompt);
        System.out.println("\n\u001B[1m\u001B[31m--- PERSONA: THE EXPLOITER ---\u001B[0m");
        System.out.println(exploiterArg);

        // 4. Spawning The Skeptic (Defensive/Triager)
        System.out.println("\n\u001B[36m[Council] Spawning the Skeptic persona (False-Positive & Scope Check)...\u001B[0m");
        String skepticPrompt = "You are a cynical bug bounty triager trying to close this report as a false positive, duplicate, or Informational. "
                + "Analyze the following finding context and identify logical flaws, missing evidence, validation gaps, or scope reasons to reject it. "
                + "Keep your response under 150 words.\n\nContext:\n" + context;

        String skepticArg = queryPersona(skepticPrompt);
        System.out.println("\n\u001B[1m\u001B[36m--- PERSONA: THE SKEPTIC ---\u001B[0m");
        System.out.println(skepticArg);

        // 5. Spawning The Moderator (Triage Judge)
        System.out.println("\n\u001B[32m[Council] Spawning the Moderator persona (Consensus and Triage decision)...\u001B[0m");
        String moderatorPrompt = "You are a neutral security referee and lead triager. Review the finding, the Exploiter's escalation argument, "
                + "and the Skeptic's defense arguments. Decide on: 1. Final Severity (Critical, High, Medium, Low, Info) "
                + "2. Final Confidence Level (e.g. 5%, 50%, 90%) 3. Consensus Rationale. "
                + "Format your response EXACTLY like this:\n"
                + "SEVERITY: <Value>\n"
                + "CONFIDENCE: <Value>%\n"
                + "RATIONALE: <Your summary decision explanation>\n\n"
                + "Context:\n" + context + "\n\nArguments:\n[Exploiter]: " + exploiterArg + "\n[Skeptic]: " + skepticArg;

        String moderatorArg = queryPersona(moderatorPrompt);
        System.out.println("\n\u001B[1m\u001B[32m--- PERSONA: THE MODERATOR (TRIAGE DECISION) ---\u001B[0m");
        System.out.println(moderatorArg);

        // 6. Parse Triage Decision
        String newSeverity = severity;
        double newConfidence = confidence;
        String rationale = "Moderator resolution: " + moderatorArg;

        try {
            String[] lines = moderatorArg.split("\n");
            for (String line : lines) {
                if (line.toUpperCase().startsWith("SEVERITY:")) {
                    newSeverity = line.substring(9).trim();
                } else if (line.toUpperCase().startsWith("CONFIDENCE:")) {
                    String pct = line.substring(11).trim().replace("%", "");
                    newConfidence = Double.parseDouble(pct) / 100.0;
                }
            }
        } catch (Exception e) {
            System.out.println("\u001B[33m[Warning] Failed to parse Moderator's exact format. Using fallbacks.\u001B[0m");
        }

        // Adjust state based on new confidence
        String newState = state;
        if (newConfidence >= 0.8) {
            newState = "VALIDATED";
        } else if (newConfidence >= 0.4) {
            newState = "PARTIAL_EVIDENCE";
        } else {
            newState = "HYPOTHESIS";
        }

        // 7. Update database
        memoryEngine.updateFindingStatus(findingId, newState, newConfidence, newSeverity, evidenceList.size());
        
        // Log decision to ledger
        DecisionEngine decisionEngine = new DecisionEngine(databaseManager);
        decisionEngine.recordDecision(activeProjectId, findingId, "Council Debate Resolution", 
                String.format("Adversarial council resolved finding ID %d. Final Severity set to %s (Confidence: %.0f%%). Rationale: %s",
                        findingId, newSeverity, newConfidence * 100, rationale));

        System.out.println("\u001B[35m--------------------------------------------------------------");
        System.out.println("Status updated in database and logged to the Decision Ledger.");
        System.out.println("==============================================================\u001B[0m");
    }

    private String queryPersona(String prompt) {
        try {
            LLMRequest request = new LLMRequest();
            List<Message> messages = new ArrayList<>();
            messages.add(new Message("user", prompt));
            request.setMessages(messages);
            request.setTemperature(0.7);

            LLMResponse response = modelRouter.complete(request);
            // Remove self-evaluation block if LLM adapter automatically appended it, to keep persona output clean
            String content = response.getContent();
            if (content.contains("=== Self-Evaluation ===")) {
                content = content.split("=== Self-Evaluation ===")[0].trim();
            }
            return content;
        } catch (Exception e) {
            return "[Simulation Fallback] Persona simulated response. Connection to provider failed: " + e.getMessage();
        }
    }
}
