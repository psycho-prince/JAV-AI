package com.javai;

import com.javai.memory.MemoryEngine;
import com.javai.storage.DatabaseManager;
import com.javai.plugins.scoring.FindingScorer;
import com.javai.plugins.scoring.ProgramRuleEngine;
import com.javai.plugins.validation.ReportValidator;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

import java.io.File;
import java.util.List;

public class ProgramRuleEngineTest {
    private DatabaseManager dbManager;
    private MemoryEngine memoryEngine;

    @BeforeEach
    public void setUp() throws Exception {
        File f = new File("database/javai_test.db");
        if (f.exists()) {
            f.delete();
        }
        dbManager = new DatabaseManager("database/javai_test.db");
        dbManager.initialize();
        memoryEngine = new MemoryEngine(dbManager);
        memoryEngine.initialize();
    }

    @AfterEach
    public void tearDown() throws Exception {
        dbManager.close();
        File f = new File("database/javai_test.db");
        if (f.exists()) {
            f.delete();
        }
    }

    @Test
    public void testScoringAndValidation() throws Exception {
        assertNotNull(memoryEngine.getProgramType("K2 Cloud"));
        
        boolean linkSuccess = memoryEngine.setActiveProjectProgram("K2Cloud");
        assertTrue(linkSuccess);
        assertEquals("K2 Cloud", memoryEngine.getActiveProgramName());

        FindingScorer scorer = new FindingScorer(memoryEngine);

        // Low Value / Exclusion Finding
        FindingScorer.ScoreResult lowVal = scorer.scoreFinding("Missing CSP Header on Login", "Info", "We found that the CSP header is missing.");
        assertEquals(5, lowVal.getScore());
        assertTrue(lowVal.getReason().contains("CSP"));

        // Forbidden Finding
        FindingScorer.ScoreResult forbidden = scorer.scoreFinding("DoS attack performed on iam.k2.cloud", "High", "We performed Denial of Service.");
        assertEquals(0, forbidden.getScore());
        assertTrue(forbidden.getReason().contains("DoS"));

        // High Value Focus Finding
        FindingScorer.ScoreResult highVal = scorer.scoreFinding("Cross-tenant IDOR in cloud snapshots API", "High", "Allows reading snapshot data of other clients.");
        assertEquals(95, highVal.getScore());
        assertTrue(highVal.getReason().contains("tenant"));

        // Validate findings checks
        memoryEngine.addFinding("SQLi in profile", "Critical", "Found SQL injection parameter id.");
        
        ReportValidator validator = new ReportValidator(dbManager, memoryEngine);
        List<ReportValidator.ValidationResult> validationResults = validator.validateActiveFindings(-1);
        assertEquals(1, validationResults.size());
        
        ReportValidator.ValidationResult res = validationResults.get(0);
        assertFalse(res.isValid());
        assertFalse(res.hasEvidence());

        // Test Pentest Reasoning Layer
        com.javai.security.MethodologyEngine methodologyEngine = new com.javai.security.MethodologyEngine(memoryEngine, dbManager);
        String assessment = methodologyEngine.assessTarget("iam.k2.cloud");
        assertNotNull(assessment);
        assertTrue(assessment.contains("Cloud IAM"));
        assertTrue(assessment.contains("Focus"));

        // Playbook registration validation
        int targetId = memoryEngine.getTargetId("iam.k2.cloud");
        assertNotEquals(-1, targetId);
        List<com.javai.security.TestPlanner.PlaybookStatus> playbooks = methodologyEngine.getTestPlanner().getTargetPlaybookCoverage(targetId, memoryEngine);
        assertFalse(playbooks.isEmpty());
        assertEquals("IAM", playbooks.get(0).getPlaybookName());

        // Playbook step update validation
        methodologyEngine.getTestPlanner().updatePlaybookStep(targetId, "IAM", 1, "Completed", "Mapped account roles.", memoryEngine);
        List<com.javai.security.TestPlanner.PlaybookStatus> playbooksUpdated = methodologyEngine.getTestPlanner().getTargetPlaybookCoverage(targetId, memoryEngine);
        assertEquals("Completed", playbooksUpdated.get(0).getSteps().get(0).getStatus());
        assertEquals("Mapped account roles.", playbooksUpdated.get(0).getSteps().get(0).getNotes());
    }

    @Test
    public void testSkepticEngine() throws Exception {
        // Create a new finding without evidence
        int findingId = memoryEngine.addFinding("Potential cross-tenant IDOR", "High", "Prematurely claiming cross-tenant boundary breach.");
        assertNotEquals(-1, findingId);

        com.javai.security.skeptic.SkepticEngine skeptic = new com.javai.security.skeptic.SkepticEngine(dbManager);
        
        // 1. Verify finding with 0 evidence (should downgrade to HYPOTHESIS/Info)
        com.javai.security.skeptic.SkepticEngine.VerificationReport report1 = skeptic.verifyFinding(findingId, "Potential cross-tenant IDOR", "High", "Prematurely claiming cross-tenant boundary breach.");
        assertEquals(com.javai.models.FindingState.HYPOTHESIS, report1.getState());
        assertEquals("Info", report1.getSeverity());
        assertEquals(0.05, report1.getConfidence());
        assertEquals(0, report1.getEvidenceCount());

        // Update finding status to the downgraded values (simulating add behavior)
        memoryEngine.updateFindingStatus(findingId, report1.getState().name(), report1.getConfidence(), report1.getSeverity(), report1.getEvidenceCount());

        // 2. Attach first evidence (should transition to PARTIAL_EVIDENCE, cap at Medium if we pass original High severity)
        memoryEngine.saveEvidence(findingId, "HTTP Request Trace", "GET /api/v1/snapshots/123 HTTP/1.1\nHost: target.com");
        
        // The saveEvidence method re-evaluated based on database severity (which was Info).
        // Let's verify database state after saveEvidence
        String sql = "SELECT state, confidence, severity, evidence_count FROM findings WHERE id = ?";
        try (java.sql.Connection conn = dbManager.getConnection();
             java.sql.PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, findingId);
            try (java.sql.ResultSet rs = stmt.executeQuery()) {
                assertTrue(rs.next());
                assertEquals("PARTIAL_EVIDENCE", rs.getString("state"));
                assertEquals(0.50, rs.getDouble("confidence"));
                assertEquals("Info", rs.getString("severity")); // Retains Info since database value was Info
                assertEquals(1, rs.getInt("evidence_count"));
            }
        }

        // Test running verification with original High severity manually under 1 evidence
        com.javai.security.skeptic.SkepticEngine.VerificationReport report2 = skeptic.verifyFinding(findingId, "Potential cross-tenant IDOR", "High", "Prematurely claiming cross-tenant boundary breach.");
        assertEquals(com.javai.models.FindingState.PARTIAL_EVIDENCE, report2.getState());
        assertEquals("Medium", report2.getSeverity()); // Capped to Medium because not VALIDATED
        assertEquals(0.50, report2.getConfidence());
        assertEquals(1, report2.getEvidenceCount());

        // 3. Attach second evidence (with HTTP trace to qualify as VALIDATED)
        memoryEngine.saveEvidence(findingId, "HTTP Response Trace", "HTTP/1.1 200 OK\nContent-Type: application/json\n\n{\"owner\":\"tenant2\"}");

        // Test running verification with original High severity manually under 2 evidences (now qualifies as VALIDATED)
        com.javai.security.skeptic.SkepticEngine.VerificationReport report3 = skeptic.verifyFinding(findingId, "Potential cross-tenant IDOR", "High", "Prematurely claiming cross-tenant boundary breach.");
        assertEquals(com.javai.models.FindingState.VALIDATED, report3.getState());
        assertEquals("High", report3.getSeverity()); // Allowed High severity because state is VALIDATED and evidenceCount >= 2
        assertEquals(0.95, report3.getConfidence());
        assertEquals(2, report3.getEvidenceCount());
    }
}
