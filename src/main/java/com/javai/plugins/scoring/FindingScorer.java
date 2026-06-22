package com.javai.plugins.scoring;

import com.javai.memory.MemoryEngine;

public class FindingScorer {
    private final ProgramRuleEngine ruleEngine;

    public FindingScorer(MemoryEngine memoryEngine) {
        this.ruleEngine = new ProgramRuleEngine(memoryEngine);
    }

    public ScoreResult scoreFinding(String title, String severity, String description) throws Exception {
        ProgramRuleEngine.Evaluation eval = ruleEngine.evaluate(title, severity, description);
        return new ScoreResult(title, eval.getScore(), eval.getReason());
    }

    public static class ScoreResult {
        private final String title;
        private final int score;
        private final String reason;

        public ScoreResult(String title, int score, String reason) {
            this.title = title;
            this.score = score;
            this.reason = reason;
        }

        public String getTitle() {
            return title;
        }

        public int getScore() {
            return score;
        }

        public String getReason() {
            return reason;
        }

        @Override
        public String toString() {
            StringBuilder sb = new StringBuilder();
            sb.append("Finding:\n").append(title).append("\n\n");
            sb.append("Score:\n").append(score).append("/100\n\n");
            sb.append("Reason:\n").append(reason);
            return sb.toString();
        }
    }
}
