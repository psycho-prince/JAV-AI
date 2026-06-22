package com.javai.security.graph;

public class Relationship {
    private final String sourceId;
    private final String targetId;
    private final String type; // e.g., "MAPS_TO", "SUPPORTS", "LEADS_TO"

    public Relationship(String sourceId, String targetId, String type) {
        this.sourceId = sourceId;
        this.targetId = targetId;
        this.type = type;
    }

    public String getSourceId() {
        return sourceId;
    }

    public String getTargetId() {
        return targetId;
    }

    public String getType() {
        return type;
    }
}
