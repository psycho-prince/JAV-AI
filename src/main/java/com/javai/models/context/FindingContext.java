package com.javai.models.context;

public class FindingContext {
    private final int id;
    private final String title;
    private final String severity;
    private final String description;
    private final com.javai.models.FindingState state;
    private final double confidence;
    private final int evidenceCount;

    public FindingContext(int id, String title, String severity, String description, com.javai.models.FindingState state, double confidence, int evidenceCount) {
        this.id = id;
        this.title = title;
        this.severity = severity;
        this.description = description;
        this.state = state;
        this.confidence = confidence;
        this.evidenceCount = evidenceCount;
    }

    public int getId() {
        return id;
    }

    public String getTitle() {
        return title;
    }

    public String getSeverity() {
        return severity;
    }

    public String getDescription() {
        return description;
    }

    public com.javai.models.FindingState getState() {
        return state;
    }

    public double getConfidence() {
        return confidence;
    }

    public int getEvidenceCount() {
        return evidenceCount;
    }

    @Override
    public String toString() {
        return String.format("- [ID: %d] [%s] State: %s (Confidence: %.0f%%, Evidence Count: %d) Title: %s - %s",
                id, severity, state != null ? state.name() : "HYPOTHESIS", confidence * 100, evidenceCount, title, description);
    }
}
