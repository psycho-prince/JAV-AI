package com.javai.models;

public class ScanResult {
    private int id;
    private int scanId;
    private String rawOutput;
    private long createdAt;

    public ScanResult(int id, int scanId, String rawOutput, long createdAt) {
        this.id = id;
        this.scanId = scanId;
        this.rawOutput = rawOutput;
        this.createdAt = createdAt;
    }

    public int getId() { return id; }
    public int getScanId() { return scanId; }
    public String getRawOutput() { return rawOutput; }
    public long getCreatedAt() { return createdAt; }
}
