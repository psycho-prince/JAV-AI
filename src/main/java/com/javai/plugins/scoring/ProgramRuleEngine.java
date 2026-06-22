package com.javai.plugins.scoring;

import com.javai.memory.MemoryEngine;
import java.util.List;

public class ProgramRuleEngine {
    private final MemoryEngine memoryEngine;

    public ProgramRuleEngine(MemoryEngine memoryEngine) {
        this.memoryEngine = memoryEngine;
    }

    public Evaluation evaluate(String title, String severity, String description) throws Exception {
        String activeProgram = memoryEngine.getActiveProgramName();
        if (activeProgram == null) {
            return new Evaluation(50, "No active program associated with the project. Defaulting to neutral score.");
        }

        String titleLower = title.toLowerCase();
        String descLower = description.toLowerCase();

        // 1. Check exclusions
        List<String> exclusions = memoryEngine.getProgramExclusions(activeProgram);
        for (String excl : exclusions) {
            if (titleLower.contains(excl.toLowerCase()) || descLower.contains(excl.toLowerCase())) {
                return new Evaluation(5, "Program explicitly states: '" + excl + "' without demonstrated impact is not accepted.");
            }
        }

        // 2. Check forbidden testing areas
        List<String> forbidden = memoryEngine.getProgramRules(activeProgram, "forbidden");
        for (String forb : forbidden) {
            if (titleLower.contains(forb.toLowerCase()) || descLower.contains(forb.toLowerCase())) {
                return new Evaluation(0, "Forbidden activity: '" + forb + "' matches program rules. Action is prohibited.");
            }
        }

        // 3. Check low value list
        List<String> lowValue = memoryEngine.getProgramRules(activeProgram, "low_value");
        for (String low : lowValue) {
            if (titleLower.contains(low.toLowerCase()) || descLower.contains(low.toLowerCase())) {
                return new Evaluation(5, "Program explicitly states missing/out of scope low value item: '" + low + "' without demonstrated impact is not accepted.");
            }
        }

        // 4. Check high value focus list
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

        // Calculate score
        int score = 50;
        String sev = severity.toLowerCase();
        if (sev.contains("critical")) {
            score = 90;
        } else if (sev.contains("high")) {
            score = 75;
        } else if (sev.contains("medium")) {
            score = 50;
        } else if (sev.contains("low")) {
            score = 25;
        } else {
            score = 10;
        }

        if (isFocus) {
            score = Math.min(score + 15, 100);
            if (score < 80 && (sev.contains("high") || sev.contains("critical"))) {
                score = 85;
            }
            if (titleLower.contains("cross-tenant") || titleLower.contains("tenant")) {
                score = 95;
            }
            return new Evaluation(score, "Cross-tenant access/IDOR or focus matches reward category: '" + matchedFocus + "' with high impact.");
        }

        return new Evaluation(score, "Standard finding checked against program rules.");
    }

    public static class Evaluation {
        private final int score;
        private final String reason;

        public Evaluation(int score, String reason) {
            this.score = score;
            this.reason = reason;
        }

        public int getScore() {
            return score;
        }

        public String getReason() {
            return reason;
        }
    }
}
