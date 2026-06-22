package com.javai.security.graph;

public class EvidenceNode {
    private final int id;
    private final int findingId;
    private final String title;
    private final String content;

    public EvidenceNode(int id, int findingId, String title, String content) {
        this.id = id;
        this.findingId = findingId;
        this.title = title;
        this.content = content;
    }

    public int getId() {
        return id;
    }

    public int getFindingId() {
        return findingId;
    }

    public String getTitle() {
        return title;
    }

    public String getContent() {
        return content;
    }
}
