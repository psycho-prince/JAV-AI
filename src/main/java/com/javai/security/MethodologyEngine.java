package com.javai.security;

import com.javai.memory.MemoryEngine;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonNode;

import java.io.File;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;

public class MethodologyEngine {
    private final MemoryEngine memoryEngine;
    private final AttackSurfaceAnalyzer attackSurfaceAnalyzer;
    private final TestPlanner testPlanner;
    private final EvidenceManager evidenceManager;
    private final FindingValidator findingValidator;
    private final ImpactAnalyzer impactAnalyzer;

    public MethodologyEngine(MemoryEngine memoryEngine, com.javai.storage.DatabaseManager databaseManager) {
        this.memoryEngine = memoryEngine;
        this.attackSurfaceAnalyzer = new AttackSurfaceAnalyzer();
        this.testPlanner = new TestPlanner();
        this.evidenceManager = new EvidenceManager(memoryEngine);
        this.findingValidator = new FindingValidator(databaseManager, memoryEngine);
        this.impactAnalyzer = new ImpactAnalyzer(memoryEngine);
    }

    public String assessTarget(String targetDomain) throws Exception {
        int targetId = memoryEngine.getTargetId(targetDomain);
        if (targetId == -1) {
            memoryEngine.addTarget(targetDomain);
            targetId = memoryEngine.getTargetId(targetDomain);
        }

        String type = attackSurfaceAnalyzer.analyzeTargetType(targetDomain);
        List<String> areas = attackSurfaceAnalyzer.getInterestingAreas(type);
        List<String> categories = attackSurfaceAnalyzer.getHighValueCategories(type);

        // Plan target playbooks
        String playbookName = "API";
        if (type.equals("Cloud IAM") || type.equals("Identity Management Service")) {
            playbookName = "IAM";
        } else if (type.equals("Cloud Provider") || type.equals("Cloud Storage")) {
            playbookName = "Cloud";
        }
        
        testPlanner.initializePlaybookForTarget(targetId, playbookName, memoryEngine);

        StringBuilder sb = new StringBuilder();
        sb.append("Target Category:\n").append(type).append("\n\n");
        sb.append("Recommended Focus:\n");
        for (int i = 0; i < areas.size(); i++) {
            sb.append(i + 1).append(". ").append(areas.get(i)).append("\n");
        }
        sb.append("\nHigh Value Potential:\n");
        for (String cat : categories) {
            sb.append("- ").append(cat).append("\n");
        }
        sb.append("\nEvidence Needed:\n");
        List<String> evidence = evidenceManager.getRequiredEvidenceForType(type);
        for (String ev : evidence) {
            sb.append("- ").append(ev).append("\n");
        }
        return sb.toString();
    }

    public AttackSurfaceAnalyzer getAttackSurfaceAnalyzer() {
        return attackSurfaceAnalyzer;
    }

    public TestPlanner getTestPlanner() {
        return testPlanner;
    }

    public EvidenceManager getEvidenceManager() {
        return evidenceManager;
    }

    public FindingValidator getFindingValidator() {
        return findingValidator;
    }

    public ImpactAnalyzer getImpactAnalyzer() {
        return impactAnalyzer;
    }
}
