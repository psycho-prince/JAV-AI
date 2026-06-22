package com.javai.models;

public class Evidence {
    private int id;
    private int findingId;
    private String title;
    private String content;
    private long createdAt;

    public Evidence(int id, int findingId, String title, String content, long createdAt) {
        this.id = id;
        this.findingId = findingId;
        this.title = title;
        this.content = content;
        this.createdAt = createdAt;
    }

    public int getId() { return id; }
    public int getFindingId() { return findingId; }
    public String getTitle() { return title; }
    public String getContent() { return content; }
    public long getCreatedAt() { return createdAt; }
}
