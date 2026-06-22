package com.javai.models;

public class Scan {
    private int id;
    private int projectId;
    private int targetId;
    private String tool;
    private String status;
    private long createdAt;

    public Scan(int id, int projectId, int targetId, String tool, String status, long createdAt) {
        this.id = id;
        this.projectId = projectId;
        this.targetId = targetId;
        this.tool = tool;
        this.status = status;
        this.createdAt = createdAt;
    }

    public int getId() { return id; }
    public int getProjectId() { return projectId; }
    public int getTargetId() { return targetId; }
    public String getTool() { return tool; }
    public String getStatus() { return status; }
    public long getCreatedAt() { return createdAt; }
}
