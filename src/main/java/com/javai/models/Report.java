package com.javai.models;

public class Report {
    private int id;
    private int projectId;
    private String title;
    private String format;
    private long createdAt;

    public Report(int id, int projectId, String title, String format, long createdAt) {
        this.id = id;
        this.projectId = projectId;
        this.title = title;
        this.format = format;
        this.createdAt = createdAt;
    }

    public int getId() { return id; }
    public int getProjectId() { return projectId; }
    public String getTitle() { return title; }
    public String getFormat() { return format; }
    public long getCreatedAt() { return createdAt; }
}
