package com.javai.security.coder;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.TimeUnit;

public class BuildVerifier {
    private static final int OUTPUT_LIMIT = 20_000;

    public VerificationResult run(Path workspaceRoot, VerificationCommand command, Duration timeout) throws IOException, InterruptedException {
        if (command == null) {
            throw new IllegalArgumentException("Verification command is required");
        }
        List<String> argv = command.getCommand();
        validateAllowed(argv);

        ProcessBuilder processBuilder = new ProcessBuilder(argv);
        processBuilder.directory(workspaceRoot.toAbsolutePath().normalize().toFile());
        processBuilder.redirectErrorStream(true);

        long startedAt = System.currentTimeMillis();
        Process process = processBuilder.start();
        StringBuilder output = new StringBuilder();
        Thread reader = new Thread(() -> readOutput(process, output), "javai-build-verifier-output");
        reader.setDaemon(true);
        reader.start();

        boolean completed = process.waitFor(timeout.toMillis(), TimeUnit.MILLISECONDS);
        if (!completed) {
            process.destroyForcibly();
            reader.join(1000);
            return new VerificationResult(command, false, -1, true, System.currentTimeMillis() - startedAt, output.toString());
        }

        reader.join(1000);
        int exitCode = process.exitValue();
        return new VerificationResult(command, exitCode == 0, exitCode, false, System.currentTimeMillis() - startedAt, output.toString());
    }

    private void validateAllowed(List<String> argv) {
        String executable = argv.get(0);
        boolean allowed = executable.equals("mvn")
                || executable.equals("gradle")
                || executable.equals("./gradlew")
                || executable.equals("npm")
                || executable.equals("cargo");
        if (!allowed) {
            throw new IllegalArgumentException("Verification command is not allowlisted: " + String.join(" ", argv));
        }
    }

    private void readOutput(Process process, StringBuilder output) {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (output.length() < OUTPUT_LIMIT) {
                    output.append(line).append('\n');
                }
            }
        } catch (IOException ignored) {
        }
    }

    public static class VerificationResult {
        private final VerificationCommand command;
        private final boolean success;
        private final int exitCode;
        private final boolean timedOut;
        private final long durationMillis;
        private final String output;

        public VerificationResult(
                VerificationCommand command,
                boolean success,
                int exitCode,
                boolean timedOut,
                long durationMillis,
                String output) {
            this.command = command;
            this.success = success;
            this.exitCode = exitCode;
            this.timedOut = timedOut;
            this.durationMillis = durationMillis;
            this.output = output;
        }

        public VerificationCommand getCommand() {
            return command;
        }

        public boolean isSuccess() {
            return success;
        }

        public int getExitCode() {
            return exitCode;
        }

        public boolean isTimedOut() {
            return timedOut;
        }

        public long getDurationMillis() {
            return durationMillis;
        }

        public String getOutput() {
            return output;
        }

        public String toHumanReadableSummary() {
            StringBuilder sb = new StringBuilder();
            sb.append("Command: ").append(command.asShellString()).append("\n");
            sb.append("Status: ").append(success ? "PASS" : "FAIL").append("\n");
            sb.append("Exit Code: ").append(exitCode).append("\n");
            sb.append("Timed Out: ").append(timedOut).append("\n");
            sb.append("Duration: ").append(durationMillis).append(" ms\n");
            if (output != null && !output.isBlank()) {
                sb.append("\nOutput:\n").append(output);
            }
            return sb.toString();
        }
    }
}
