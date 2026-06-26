package com.javai.security.coder;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;

public class WorkspaceInspector {
    private static final int MAX_DEPTH = 8;

    public WorkspaceProfile inspect(Path root) throws IOException {
        Path normalizedRoot = root.toAbsolutePath().normalize();
        if (!Files.isDirectory(normalizedRoot)) {
            throw new IOException("Workspace root is not a directory: " + normalizedRoot);
        }

        List<String> sourceFiles = new ArrayList<>();
        List<String> testFiles = new ArrayList<>();
        List<String> documentationFiles = new ArrayList<>();
        List<String> generatedPaths = new ArrayList<>();

        try (Stream<Path> paths = Files.walk(normalizedRoot, MAX_DEPTH)) {
            paths.filter(path -> !path.equals(normalizedRoot))
                    .filter(path -> shouldInclude(normalizedRoot, path))
                    .sorted(Comparator.comparing(path -> normalizedRoot.relativize(path).toString()))
                    .forEach(path -> classify(normalizedRoot, path, sourceFiles, testFiles, documentationFiles, generatedPaths));
        }

        String buildSystem = detectBuildSystem(normalizedRoot);
        List<VerificationCommand> verificationCommands = planVerificationCommands(normalizedRoot, buildSystem);

        return new WorkspaceProfile(
                normalizedRoot.toString(),
                buildSystem,
                sourceFiles,
                testFiles,
                documentationFiles,
                generatedPaths,
                verificationCommands);
    }

    private boolean shouldInclude(Path root, Path path) {
        Path relative = root.relativize(path);
        for (Path part : relative) {
            String name = part.toString();
            if (name.equals(".git")
                    || name.equals("target")
                    || name.equals("build")
                    || name.equals("node_modules")
                    || name.equals(".idea")
                    || name.equals(".gradle")) {
                return false;
            }
        }
        return true;
    }

    private void classify(
            Path root,
            Path path,
            List<String> sourceFiles,
            List<String> testFiles,
            List<String> documentationFiles,
            List<String> generatedPaths) {
        String relative = root.relativize(path).toString().replace('\\', '/');
        if (Files.isDirectory(path)) {
            if (relative.startsWith("workspace/") || relative.startsWith("database/")) {
                generatedPaths.add(relative + "/");
            }
            return;
        }

        String lower = relative.toLowerCase(Locale.ROOT);
        if (lower.endsWith(".md") || lower.endsWith(".txt")) {
            documentationFiles.add(relative);
        }
        if (lower.startsWith("workspace/") || lower.startsWith("database/")) {
            generatedPaths.add(relative);
        }
        if (isTestFile(lower)) {
            testFiles.add(relative);
        } else if (isSourceFile(lower)) {
            sourceFiles.add(relative);
        }
    }

    private boolean isSourceFile(String path) {
        return path.endsWith(".java")
                || path.endsWith(".kt")
                || path.endsWith(".py")
                || path.endsWith(".js")
                || path.endsWith(".ts")
                || path.endsWith(".go")
                || path.endsWith(".rs");
    }

    private boolean isTestFile(String path) {
        return path.contains("/test/")
                || path.endsWith("test.java")
                || path.endsWith("tests.py")
                || path.endsWith(".spec.ts")
                || path.endsWith(".test.ts")
                || path.endsWith(".spec.js")
                || path.endsWith(".test.js");
    }

    private String detectBuildSystem(Path root) {
        if (Files.exists(root.resolve("pom.xml"))) {
            return "Maven";
        }
        if (Files.exists(root.resolve("build.gradle")) || Files.exists(root.resolve("settings.gradle"))) {
            return "Gradle";
        }
        if (Files.exists(root.resolve("package.json"))) {
            return "Node";
        }
        if (Files.exists(root.resolve("Cargo.toml"))) {
            return "Cargo";
        }
        return "Unknown";
    }

    private List<VerificationCommand> planVerificationCommands(Path root, String buildSystem) {
        List<VerificationCommand> commands = new ArrayList<>();
        switch (buildSystem) {
            case "Maven":
                commands.add(new VerificationCommand("Maven tests", List.of("mvn", "test")));
                break;
            case "Gradle":
                if (Files.exists(root.resolve("gradlew"))) {
                    commands.add(new VerificationCommand("Gradle wrapper tests", List.of("./gradlew", "test")));
                } else {
                    commands.add(new VerificationCommand("Gradle tests", List.of("gradle", "test")));
                }
                break;
            case "Node":
                commands.add(new VerificationCommand("Node tests", List.of("npm", "test")));
                break;
            case "Cargo":
                commands.add(new VerificationCommand("Cargo tests", List.of("cargo", "test")));
                break;
            default:
                break;
        }
        return commands;
    }
}
