package com.javai.models.context;

public class ProjectContext {
    private final int id;
    private final String name;
    private final String description;

    public ProjectContext(int id, String name, String description) {
        this.id = id;
        this.name = name;
        this.description = description;
    }

    public int getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    @Override
    public String toString() {
        return String.format("Project: %s (ID: %d)\nDescription: %s", name, id, description);
    }
}
