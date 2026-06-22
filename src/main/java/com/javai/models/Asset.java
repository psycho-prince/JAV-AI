package com.javai.models;

public class Asset {
    private int id;
    private int projectId;
    private int targetId;
    private String type;
    private String value;
    private String metadata;
    private long createdAt;

    public Asset(int id, int projectId, int targetId, String type, String value, String metadata, long createdAt) {
        this.id = id;
        this.projectId = projectId;
        this.targetId = targetId;
        this.type = type;
        this.value = value;
        this.metadata = metadata;
        this.createdAt = createdAt;
    }

    public int getId() { return id; }
    public int getProjectId() { return projectId; }
    public int getTargetId() { return targetId; }
    public String getType() { return type; }
    public String getValue() { return value; }
    public String getMetadata() { return metadata; }
    public long getCreatedAt() { return createdAt; }
}
