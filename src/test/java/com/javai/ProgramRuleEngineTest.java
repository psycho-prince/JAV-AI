package com.javai;

import com.javai.memory.MemoryEngine;
import com.javai.storage.DatabaseManager;
import com.javai.plugins.scoring.FindingScorer;
import com.javai.plugins.scoring.ProgramRuleEngine;
import com.javai.plugins.validation.ReportValidator;
import com.javai.security.coder.WorkspaceInspector;
import com.javai.security.coder.WorkspaceProfile;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

import java.io.File;
import java.nio.file.Path;
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
    public void testSwitchProjectAutoLinksProgramProject() throws Exception {
        boolean switched = memoryEngine.switchProject("NASA");

        assertTrue(switched);
        assertEquals("NASA", memoryEngine.getActiveProjectName());
        assertEquals("NASA", memoryEngine.getActiveProgramName());
        assertEquals("Government / Aerospace", memoryEngine.getProgramType("NASA"));
    }

    @Test
    public void testCoderWorkspaceInspectionPlansMavenVerification() throws Exception {
        WorkspaceInspector inspector = new WorkspaceInspector();
        WorkspaceProfile profile = inspector.inspect(Path.of("."));

        assertEquals("Maven", profile.getBuildSystem());
        assertFalse(profile.getSourceFiles().isEmpty());
        assertTrue(profile.getTestFiles().stream().anyMatch(path -> path.endsWith("ProgramRuleEngineTest.java")));
        assertFalse(profile.getVerificationCommands().isEmpty());
        assertEquals("mvn test", profile.getVerificationCommands().get(0).asShellString());
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

    @Test
    public void testCouncilEngine() throws Exception {
        // Create a new finding without evidence
        int findingId = memoryEngine.addFinding("CSRF on delete account", "Medium", "Potential cross-site request forgery.");
        assertNotEquals(-1, findingId);

        // Define a test model config and model router
        com.javai.llm.LocalModelConfig modelConfig = new com.javai.llm.LocalModelConfig();
        com.javai.llm.ModelRouter router = new com.javai.llm.ModelRouter(modelConfig);
        router.initialize();

        // Register custom provider that outputs standard Moderator decisions
        router.registerProvider("test-provider", new com.javai.llm.LLMProvider() {
            @Override
            public void initialize() throws Exception {}

            @Override
            public com.javai.llm.LLMResponse complete(com.javai.llm.LLMRequest request) throws Exception {
                String prompt = request.getMessages().get(0).getContent();
                if (prompt.contains("neutral security referee")) {
                    return new com.javai.llm.LLMResponse("SEVERITY: High\nCONFIDENCE: 85%\nRATIONALE: Validated via council.");
                } else if (prompt.contains("professional penetration tester")) {
                    return new com.javai.llm.LLMResponse("Exploiter: escalation is easy.");
                } else {
                    return new com.javai.llm.LLMResponse("Skeptic: proof is missing.");
                }
            }
        });
        router.setActiveModel("test-provider");

        com.javai.security.skeptic.CouncilEngine council = new com.javai.security.skeptic.CouncilEngine(dbManager, router, memoryEngine);
        council.holdDebate(findingId);

        // Verify the finding was updated to VALIDATED state and High severity with 85% confidence
        String sql = "SELECT state, confidence, severity FROM findings WHERE id = ?";
        try (java.sql.Connection conn = dbManager.getConnection();
             java.sql.PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, findingId);
            try (java.sql.ResultSet rs = stmt.executeQuery()) {
                assertTrue(rs.next());
                assertEquals("VALIDATED", rs.getString("state"));
                assertEquals(0.85, rs.getDouble("confidence"), 0.001);
                assertEquals("High", rs.getString("severity"));
            }
        }
    }

    @Test
    public void testPqcAndCoder() throws Exception {
        // 1. Verify PQC Keygen, Seal, and Unseal
        com.javai.security.pqc.QuantumBlueEngine pqc = new com.javai.security.pqc.QuantumBlueEngine();
        pqc.generateKeyPair("test_pfx");

        java.io.File idPkFile = new java.io.File("workspace/keys/test_pfx_id.pk");
        java.io.File idSkFile = new java.io.File("workspace/keys/test_pfx_id.sk");
        java.io.File pqcPkFile = new java.io.File("workspace/keys/test_pfx_pqc.pk");
        java.io.File pqcSkFile = new java.io.File("workspace/keys/test_pfx_pqc.sk");

        assertTrue(idPkFile.exists());
        assertTrue(idSkFile.exists());
        assertTrue(pqcPkFile.exists());
        assertTrue(pqcSkFile.exists());

        // Create a test file
        java.io.File testFile = new java.io.File("workspace/keys/test_data.txt");
        java.nio.file.Files.writeString(testFile.toPath(), "Classical cryptography is broken by quantum computers.", java.nio.charset.StandardCharsets.UTF_8);

        // Seal it
        pqc.sealFile(testFile.getPath(), idSkFile.getPath(), pqcPkFile.getPath());
        java.io.File sealedFile = new java.io.File("workspace/keys/test_data.txt.pqc");
        assertTrue(sealedFile.exists());

        // Unseal it
        pqc.unsealFile(sealedFile.getPath(), idPkFile.getPath(), pqcSkFile.getPath());
        java.io.File recoveredFile = new java.io.File("workspace/keys/test_data.txt.recovered");
        assertTrue(recoveredFile.exists());
        assertEquals("Classical cryptography is broken by quantum computers.", java.nio.file.Files.readString(recoveredFile.toPath()));

        // Clean up PQC test files
        testFile.delete();
        sealedFile.delete();
        recoveredFile.delete();
        idPkFile.delete();
        idSkFile.delete();
        pqcPkFile.delete();
        pqcSkFile.delete();

        // 2. Verify Coder audit PQC readiness
        java.io.File vulnFile = new java.io.File("workspace/keys/VulnerableContract.sol");
        java.nio.file.Files.writeString(vulnFile.toPath(), "contract V { function f() { ecrecover(h,v,r,s); } }");
        
        com.javai.security.coder.CoderEngine coder = new com.javai.security.coder.CoderEngine(dbManager, null, memoryEngine);
        coder.auditPqcReadiness(vulnFile.getPath());
        
        vulnFile.delete();
    }
}
