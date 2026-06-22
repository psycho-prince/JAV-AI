package com.javai.security;

import com.javai.memory.MemoryEngine;
import com.javai.storage.DatabaseManager;
import com.javai.plugins.validation.ReportValidator;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;

public class FindingValidator {
    private final ReportValidator reportValidator;
    private final DatabaseManager databaseManager;
    private final MemoryEngine memoryEngine;

    public FindingValidator(DatabaseManager databaseManager, MemoryEngine memoryEngine) {
        this.databaseManager = databaseManager;
        this.memoryEngine = memoryEngine;
        this.reportValidator = new ReportValidator(databaseManager, memoryEngine);
    }

    public boolean validateFindingStructure(int findingId) throws Exception {
        List<ReportValidator.ValidationResult> results = reportValidator.validateActiveFindings(-1);
        for (ReportValidator.ValidationResult res : results) {
            if (res.getFindingId() == findingId) {
                return res.isValid();
            }
        }
        return false;
    }

    public ValidationDetails getValidationDetails(int findingId) throws Exception {
        String title = "Unknown";
        String description = "";
        String severity = "Low";
        String state = "HYPOTHESIS";
        int evidenceCount = 0;

        String sql = "SELECT title, description, severity, state, evidence_count FROM findings WHERE id = ?";
        try (Connection conn = databaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, findingId);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    title = rs.getString("title");
                    description = rs.getString("description");
                    severity = rs.getString("severity");
                    state = rs.getString("state");
                    evidenceCount = rs.getInt("evidence_count");
                }
            }
        }

        String content = (title + " " + description).toLowerCase();

        boolean hasRepro = content.contains("repro") || content.contains("steps") || content.contains("reproduction");
        boolean hasImpact = content.contains("impact") || content.contains("consequence") || content.contains("effect");
        boolean hasPoC = content.contains("poc") || content.contains("proof") || content.contains("exploit");
        boolean hasAsset = content.contains("domain") || content.contains("url") || content.contains("host") || content.contains("target") || content.contains("ip") || content.contains("http");
        boolean hasCVSS = content.contains("cvss") || content.matches(".*[0-9]\\.[0-9].*");
        boolean hasRemediation = content.contains("remediation") || content.contains("mitigation") || content.contains("fix") || content.contains("remedy");

        boolean hasEvidence = false;
        String evSql = "SELECT COUNT(*) FROM evidence WHERE finding_id = ?";
        try (Connection conn = databaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(evSql)) {
            stmt.setInt(1, findingId);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    hasEvidence = rs.getInt(1) > 0;
                }
            }
        }

        boolean isHighOrCritical = severity.toLowerCase().contains("high") || severity.toLowerCase().contains("critical");
        boolean stateValidated = !isHighOrCritical || ("VALIDATED".equals(state) && evidenceCount > 0);

        return new ValidationDetails(
                findingId, title, hasRepro, hasImpact, hasPoC, hasAsset, hasCVSS, hasEvidence, hasRemediation, stateValidated
        );
    }

    public static class ValidationDetails {
        private final int id;
        private final String title;
        private final boolean reproductionSteps;
        private final boolean impact;
        private final boolean poc;
        private final boolean asset;
        private final boolean cvss;
        private final boolean evidence;
        private final boolean remediation;
        private final boolean stateValidated;

        public ValidationDetails(int id, String title, boolean reproductionSteps, boolean impact, boolean poc,
                                 boolean asset, boolean cvss, boolean evidence, boolean remediation, boolean stateValidated) {
            this.id = id;
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

        public int getId() {
            return id;
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
    }
}
