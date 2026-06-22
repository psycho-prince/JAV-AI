package com.javai.security.skeptic;

import com.javai.models.FindingState;
import com.javai.storage.DatabaseManager;

public class SkepticEngine {
    private final EvidenceChecker evidenceChecker;
    private final ClaimVerifier claimVerifier;

    public SkepticEngine(DatabaseManager databaseManager) {
        this.evidenceChecker = new EvidenceChecker(databaseManager);
        this.claimVerifier = new ClaimVerifier();
    }

    public VerificationReport verifyFinding(int findingId, String title, String severity, String description) throws Exception {
        int evidenceCount = evidenceChecker.getEvidenceCount(findingId);
        boolean hasReqResp = evidenceChecker.hasRequestResponseEvidence(findingId);
        boolean hasConfirm = claimVerifier.hasConfirmationClaim(title, description);
        boolean hasHighSev = claimVerifier.hasHighSeverityClaim(severity);

        FindingState calculatedState = FindingState.HYPOTHESIS;
        double confidence = 0.05; // 5% baseline default
        String calculatedSeverity = severity;
        String reason;

        if (evidenceCount == 0) {
            calculatedState = FindingState.HYPOTHESIS;
            confidence = 0.05;
            if (hasConfirm || hasHighSev) {
                // Downgrade unauthorized claims without evidence
                calculatedSeverity = "Info";
                reason = "SKEPTIC WARNING: Finding claims confirmation/high severity but has 0 evidence records. Downgraded finding to HYPOTHESIS and severity to Info.";
            } else {
                reason = "Grounded assessment: No evidence attached. Finding status: HYPOTHESIS (untested).";
            }
        } else {
            // Evidence exists
            if (hasReqResp && evidenceCount >= 2) {
                calculatedState = FindingState.VALIDATED;
                confidence = 0.95;
                reason = "Verified: Multiple evidence records with HTTP requests/responses collected. Confidence: 95%.";
            } else {
                calculatedState = FindingState.PARTIAL_EVIDENCE;
                confidence = 0.50;
                if (hasHighSev) {
                    calculatedSeverity = "Medium";
                    reason = "SKEPTIC WARNING: Finding has some evidence but lacks full validation traces. Capped severity to Medium and confidence to 50%.";
                } else {
                    reason = "Under review: Stored some evidence. Finding status: PARTIAL_EVIDENCE. Confidence: 50%.";
                }
            }
        }

        return new VerificationReport(calculatedState, calculatedSeverity, confidence, evidenceCount, reason);
    }

    public static class VerificationReport {
        private final FindingState state;
        private final String severity;
        private final double confidence;
        private final int evidenceCount;
        private final String reason;

        public VerificationReport(FindingState state, String severity, double confidence, int evidenceCount, String reason) {
            this.state = state;
            this.severity = severity;
            this.confidence = confidence;
            this.evidenceCount = evidenceCount;
            this.reason = reason;
        }

        public FindingState getState() {
            return state;
        }

        public String getSeverity() {
            return severity;
        }

        public double getConfidence() {
            return confidence;
        }

        public int getEvidenceCount() {
            return evidenceCount;
        }

        public String getReason() {
            return reason;
        }
    }
}
