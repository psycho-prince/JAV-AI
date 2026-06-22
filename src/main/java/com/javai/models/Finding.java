package com.javai.models;

public class Finding {
    private int id;
    private int projectId;
    private String title;
    private String description;
    private String severity;
    private FindingState state;
    private double confidence;
    private int evidenceCount;
    private long createdAt;

    public Finding() {}

    public Finding(int id, int projectId, String title, String description, String severity, FindingState state, double confidence, int evidenceCount, long createdAt) {
        this.id = id;
        this.projectId = projectId;
        this.title = title;
        this.description = description;
        this.severity = severity;
        this.state = state;
        this.confidence = confidence;
        this.evidenceCount = evidenceCount;
        this.createdAt = createdAt;
    }

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public int getProjectId() {
        return projectId;
    }

    public void setProjectId(int projectId) {
        this.projectId = projectId;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getSeverity() {
        return severity;
    }

    public void setSeverity(String severity) {
        this.severity = severity;
    }

    public FindingState getState() {
        return state;
    }

    public void setState(FindingState state) {
        this.state = state;
    }

    public double getConfidence() {
        return confidence;
    }

    public void setConfidence(double confidence) {
        this.confidence = confidence;
    }

    public int getEvidenceCount() {
        return evidenceCount;
    }

    public void setEvidenceCount(int evidenceCount) {
        this.evidenceCount = evidenceCount;
    }

    public long getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(long createdAt) {
        this.createdAt = createdAt;
    }
}
