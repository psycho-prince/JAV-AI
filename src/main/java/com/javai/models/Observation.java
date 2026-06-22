package com.javai.models;

public class Observation {
    private int id;
    private int projectId;
    private int targetId;
    private String description;
    private String source;
    private double confidence;
    private long createdAt;

    public Observation() {}

    public Observation(int id, int projectId, int targetId, String description, String source, double confidence, long createdAt) {
        this.id = id;
        this.projectId = projectId;
        this.targetId = targetId;
        this.description = description;
        this.source = source;
        this.confidence = confidence;
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

    public int getTargetId() {
        return targetId;
    }

    public void setTargetId(int targetId) {
        this.targetId = targetId;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getSource() {
        return source;
    }

    public void setSource(String source) {
        this.source = source;
    }

    public double getConfidence() {
        return confidence;
    }

    public void setConfidence(double confidence) {
        this.confidence = confidence;
    }

    public long getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(long createdAt) {
        this.createdAt = createdAt;
    }
}
