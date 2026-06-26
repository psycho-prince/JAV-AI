package com.javai.security.coder;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class WorkspaceProfile {
    private final String rootPath;
    private final String buildSystem;
    private final List<String> sourceFiles;
    private final List<String> testFiles;
    private final List<String> documentationFiles;
    private final List<String> generatedPaths;
    private final List<VerificationCommand> verificationCommands;

    public WorkspaceProfile(
            String rootPath,
            String buildSystem,
            List<String> sourceFiles,
            List<String> testFiles,
            List<String> documentationFiles,
            List<String> generatedPaths,
            List<VerificationCommand> verificationCommands) {
        this.rootPath = rootPath;
        this.buildSystem = buildSystem;
        this.sourceFiles = copy(sourceFiles);
        this.testFiles = copy(testFiles);
        this.documentationFiles = copy(documentationFiles);
        this.generatedPaths = copy(generatedPaths);
        this.verificationCommands = verificationCommands == null
                ? Collections.emptyList()
                : Collections.unmodifiableList(new ArrayList<>(verificationCommands));
    }

    public String getRootPath() {
        return rootPath;
    }

    public String getBuildSystem() {
        return buildSystem;
    }

    public List<String> getSourceFiles() {
        return sourceFiles;
    }

    public List<String> getTestFiles() {
        return testFiles;
    }

    public List<String> getDocumentationFiles() {
        return documentationFiles;
    }

    public List<String> getGeneratedPaths() {
        return generatedPaths;
    }

    public List<VerificationCommand> getVerificationCommands() {
        return verificationCommands;
    }

    public String toHumanReadableSummary() {
        StringBuilder sb = new StringBuilder();
        sb.append("Workspace: ").append(rootPath).append("\n");
        sb.append("Build System: ").append(buildSystem).append("\n");
        sb.append("Source Files: ").append(sourceFiles.size()).append("\n");
        sb.append("Test Files: ").append(testFiles.size()).append("\n");
        sb.append("Documentation Files: ").append(documentationFiles.size()).append("\n");
        sb.append("Generated/State Paths: ").append(generatedPaths.size()).append("\n");
        sb.append("Verification Commands:\n");
        if (verificationCommands.isEmpty()) {
            sb.append("- None detected\n");
        } else {
            for (VerificationCommand command : verificationCommands) {
                sb.append("- ").append(command.asShellString()).append("\n");
            }
        }
        return sb.toString();
    }

    private static <T> List<T> copy(List<T> values) {
        return values == null ? Collections.emptyList() : Collections.unmodifiableList(new ArrayList<>(values));
    }
}
