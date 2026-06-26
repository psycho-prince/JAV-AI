package com.javai.security.coder;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class VerificationCommand {
    private final String name;
    private final List<String> command;

    public VerificationCommand(String name, List<String> command) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Verification command name is required");
        }
        if (command == null || command.isEmpty()) {
            throw new IllegalArgumentException("Verification command argv is required");
        }
        this.name = name;
        this.command = Collections.unmodifiableList(new ArrayList<>(command));
    }

    public String getName() {
        return name;
    }

    public List<String> getCommand() {
        return command;
    }

    public String asShellString() {
        return String.join(" ", command);
    }
}
