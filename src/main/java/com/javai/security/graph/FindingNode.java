package com.javai.security.graph;

public class FindingNode {
    private final int id;
    private final String title;
    private final String state;
    private final String severity;
    private final double confidence;

    public FindingNode(int id, String title, String state, String severity, double confidence) {
        this.id = id;
        this.title = title;
        this.state = state;
        this.severity = severity;
        this.confidence = confidence;
    }

    public int getId() {
        return id;
    }

    public String getTitle() {
        return title;
    }

    public String getState() {
        return state;
    }

    public String getSeverity() {
        return severity;
    }

    public double getConfidence() {
        return confidence;
    }
}
