package com.javai.plugins.validation;

import com.javai.memory.MemoryEngine;
import com.javai.storage.DatabaseManager;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;

public class ReportValidator {
    private final DatabaseManager databaseManager;
    private final MemoryEngine memoryEngine;

    public ReportValidator(DatabaseManager databaseManager, MemoryEngine memoryEngine) {
        this.databaseManager = databaseManager;
        this.memoryEngine = memoryEngine;
    }

    public List<ValidationResult> validateActiveFindings(int reportId) throws Exception {
        List<ValidationResult> results = new ArrayList<>();
        
        // Fetch findings to validate
        String sql;
        if (reportId != -1) {
            sql = "SELECT f.id, f.title, f.description, f.severity FROM findings f " +
                  "JOIN report_sections rs ON f.id = rs.finding_id WHERE rs.report_id = ?";
        } else {
            sql = "SELECT id, title, description, severity FROM findings WHERE project_id = ?";
        }

        try (Connection conn = databaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, reportId != -1 ? reportId : memoryEngine.getActiveProjectId());
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    int id = rs.getInt("id");
                    String title = rs.getString("title");
                    String description = rs.getString("description");
                    String severity = rs.getString("severity");
                    
                    results.add(validateFinding(id, title, description, severity, conn));
                }
            }
        }
        
        return results;
    }

    private ValidationResult validateFinding(int id, String title, String description, String severity, Connection conn) throws Exception {
        String content = (title + " " + description).toLowerCase();

        boolean hasRepro = content.contains("repro") || content.contains("steps") || content.contains("reproduction");
        boolean hasImpact = content.contains("impact") || content.contains("consequence") || content.contains("effect");
        boolean hasPoC = content.contains("poc") || content.contains("proof") || content.contains("exploit");
        boolean hasAsset = content.contains("domain") || content.contains("url") || content.contains("host") || content.contains("target") || content.contains("ip") || content.contains("http");
        boolean hasCVSS = content.contains("cvss") || content.matches(".*[0-9]\\.[0-9].*");
        boolean hasRemediation = content.contains("remediation") || content.contains("mitigation") || content.contains("fix") || content.contains("remedy");

        // Check for evidence in database
        boolean hasEvidence = false;
        String evSql = "SELECT COUNT(*) FROM evidence WHERE finding_id = ?";
        try (PreparedStatement stmt = conn.prepareStatement(evSql)) {
            stmt.setInt(1, id);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    hasEvidence = rs.getInt(1) > 0;
                }
            }
        }

        // Fetch state and evidence count for severity alignment verification
        String state = "HYPOTHESIS";
        int evidenceCount = 0;
        String stateSql = "SELECT state, evidence_count FROM findings WHERE id = ?";
        try (PreparedStatement stmt = conn.prepareStatement(stateSql)) {
            stmt.setInt(1, id);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    state = rs.getString("state");
                    evidenceCount = rs.getInt("evidence_count");
                }
            }
        }

        boolean isHighOrCritical = severity.toLowerCase().contains("high") || severity.toLowerCase().contains("critical");
        boolean stateValidated = !isHighOrCritical || ("VALIDATED".equals(state) && evidenceCount > 0);

        return new ValidationResult(id, title, hasRepro, hasImpact, hasPoC, hasAsset, hasCVSS, hasEvidence, hasRemediation, stateValidated);
    }

    public static class ValidationResult {
        private final int findingId;
        private final String title;
        private final boolean reproductionSteps;
        private final boolean impact;
        private final boolean poc;
        private final boolean asset;
        private final boolean cvss;
        private final boolean evidence;
        private final boolean remediation;
        private final boolean stateValidated;

        public ValidationResult(int findingId, String title, boolean reproductionSteps, boolean impact, boolean poc,
                                boolean asset, boolean cvss, boolean evidence, boolean remediation, boolean stateValidated) {
            this.findingId = findingId;
            this.title = title;
            this.reproductionSteps = reproductionSteps;
            this.impact = impact;
            this.poc = poc;
            this.asset = asset;
            this.cvss = cvss;
            this.evidence = evidence;
            this.remediation = remediation;
            this.stateValidated = stateValidated;
        }

        public int getFindingId() {
            return findingId;
        }

        public String getTitle() {
            return title;
        }

        public boolean hasReproductionSteps() {
            return reproductionSteps;
        }

        public boolean hasImpact() {
            return impact;
        }

        public boolean hasPoc() {
            return poc;
        }

        public boolean hasAsset() {
            return asset;
        }

        public boolean hasCvss() {
            return cvss;
        }

        public boolean hasEvidence() {
            return evidence;
        }

        public boolean hasRemediation() {
            return remediation;
        }

        public boolean isStateValidated() {
            return stateValidated;
        }

        public boolean isValid() {
            return reproductionSteps && impact && poc && asset && cvss && evidence && remediation && stateValidated;
        }

        @Override
        public String toString() {
            StringBuilder sb = new StringBuilder();
            sb.append("Vulnerability Validation for [ID: ").append(findingId).append("] \"").append(title).append("\":\n");
            sb.append(reproductionSteps ? "  ✓ Reproduction steps\n" : "  ✗ Reproduction steps (Missing 'reproduction steps' details)\n");
            sb.append(impact ? "  ✓ Impact\n" : "  ✗ Impact (Missing 'impact' details)\n");
            sb.append(poc ? "  ✓ PoC\n" : "  ✗ PoC (Missing 'PoC' details)\n");
            sb.append(asset ? "  ✓ Asset\n" : "  ✗ Asset (Missing scoped asset or target details)\n");
            sb.append(cvss ? "  ✓ CVSS\n" : "  ✗ CVSS (Missing CVSS vector/score)\n");
            sb.append(evidence ? "  ✓ Evidence\n" : "  ✗ Evidence (No screenshots or network logs attached in evidence table)\n");
            sb.append(remediation ? "  ✓ Remediation\n" : "  ✗ Remediation (Missing 'remediation' or fix steps)\n");
            sb.append(stateValidated ? "  ✓ Severity/Evidence Alignment\n" : "  ✗ Severity/Evidence Alignment (High/Critical severity requires state VALIDATED and evidence count > 0)\n");
            return sb.toString();
        }
    }
}
