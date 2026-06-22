package com.javai.models;

public class Target {
    private int id;
    private int projectId;
    private String domain;
    private long createdAt;

    public Target(int id, int projectId, String domain, long createdAt) {
        this.id = id;
        this.projectId = projectId;
        this.domain = domain;
        this.createdAt = createdAt;
    }

    public int getId() { return id; }
    public int getProjectId() { return projectId; }
    public String getDomain() { return domain; }
    public long getCreatedAt() { return createdAt; }
}
