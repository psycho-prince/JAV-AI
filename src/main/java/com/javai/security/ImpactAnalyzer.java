package com.javai.security;

import com.javai.memory.MemoryEngine;
import java.util.List;

public class ImpactAnalyzer {
    private final MemoryEngine memoryEngine;

    public ImpactAnalyzer(MemoryEngine memoryEngine) {
        this.memoryEngine = memoryEngine;
    }

    public String analyzeImpact(String title, String severity, String description) throws Exception {
        String activeProgram = memoryEngine.getActiveProgramName();
        if (activeProgram == null) {
            return "No active program linked. Impact assessment defaulting to standard vulnerability categories.";
        }

        String titleLower = title.toLowerCase();
        String descLower = description.toLowerCase();

        // Check for exclusions
        List<String> exclusions = memoryEngine.getProgramExclusions(activeProgram);
        for (String excl : exclusions) {
            if (titleLower.contains(excl.toLowerCase()) || descLower.contains(excl.toLowerCase())) {
                return "Critical Out of Scope Policy Warning: This finding matches exclusion '" + excl + "'. It will be marked Informational and is likely to be rejected.";
            }
        }

        // Check for low value
        List<String> lowValue = memoryEngine.getProgramRules(activeProgram, "low_value");
        for (String low : lowValue) {
            if (titleLower.contains(low.toLowerCase()) || descLower.contains(low.toLowerCase())) {
                return "Low-Value Finding Alert: This finding matches category '" + low + "', which is treated as informational or accepted without reward.";
            }
        }

        // Check for focus area
        List<String> focus = memoryEngine.getProgramRules(activeProgram, "focus");
        boolean isFocus = false;
        String matchedFocus = null;
        for (String f : focus) {
            if (titleLower.contains(f.toLowerCase()) || descLower.contains(f.toLowerCase())) {
                isFocus = true;
                matchedFocus = f;
                break;
            }
        }

        int maxBounty = memoryEngine.getProgramMaxBounty(activeProgram);
        String rewardRange = (maxBounty > 0) ? "Potential Reward: up to " + maxBounty + " USD/Points" : "VDP Status: Points/Recognition Only";

        if (isFocus) {
            if (titleLower.contains("cross-tenant") || titleLower.contains("tenant")) {
                return "High Priority Security Breach: Cross-tenant access detected! Matches focus area '" + matchedFocus + "' with high impact. " + rewardRange + ".";
            }
            return "Focus Area Match: Finding matches focus category '" + matchedFocus + "' on target platform. " + rewardRange + ".";
        }

        return "Standard Finding Checked against program rules. " + rewardRange + ".";
    }
}
