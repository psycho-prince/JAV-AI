package com.javai.security.coder;

import com.javai.llm.LLMRequest;
import com.javai.llm.LLMResponse;
import com.javai.llm.ModelRouter;
import com.javai.memory.MemoryEngine;
import com.javai.models.Message;
import com.javai.storage.DatabaseManager;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.SimpleDateFormat;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

public class CoderEngine {
    private final ModelRouter modelRouter;
    private final MemoryEngine memoryEngine;
    private final DatabaseManager databaseManager;

    public CoderEngine(DatabaseManager databaseManager, ModelRouter modelRouter, MemoryEngine memoryEngine) {
        this.databaseManager = databaseManager;
        this.modelRouter = modelRouter;
        this.memoryEngine = memoryEngine;
    }

    public void solveProblem(String description) throws Exception {
        System.out.println("\n\u001B[34m[Coder] Thinking... Consulting model router to solve coding challenge...\u001B[0m");

        String prompt = "You are an elite principal software engineer and expert security code architect. "
                + "Please solve the following programming problem. Provide clean, production-grade, and secure code. "
                + "Explain the algorithm and complexities. Highlight any post-quantum security considerations.\n\n"
                + "Problem Description:\n" + description;

        LLMRequest request = new LLMRequest();
        List<Message> messages = new ArrayList<>();
        messages.add(new Message("user", prompt));
        request.setMessages(messages);
        request.setTemperature(0.3);

        LLMResponse response = modelRouter.complete(request);
        String solution = response.getContent();

        System.out.println("\n\u001B[1m\u001B[32m=== CODER SOLUTION ===\u001B[0m");
        System.out.println(solution);
        System.out.println("\u001B[1m\u001B[32m======================\u001B[0m");

        // Write the solution to a file
        File dir = new File("workspace/code");
        if (!dir.exists()) {
            dir.mkdirs();
        }

        // Determine file extension
        String extension = "txt";
        String lowerSol = solution.toLowerCase();
        if (lowerSol.contains("```java")) extension = "java";
        else if (lowerSol.contains("```python")) extension = "py";
        else if (lowerSol.contains("```go")) extension = "go";
        else if (lowerSol.contains("```rust")) extension = "rs";
        else if (lowerSol.contains("```solidity")) extension = "sol";
        else if (lowerSol.contains("```javascript") || lowerSol.contains("```js")) extension = "js";

        SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMdd_HHmmss");
        String filename = "solution_" + sdf.format(new Date()) + "." + extension;
        File file = new File(dir, filename);
        Files.writeString(file.toPath(), solution, StandardCharsets.UTF_8);

        System.out.println("\u001B[34m[Coder] Code solution saved to: " + file.getPath() + "\u001B[0m");
        
        // Log to journal
        memoryEngine.addJournalEntry("Coder Solve", "Solved problem: " + (description.length() > 50 ? description.substring(0, 47) + "..." : description) + ". Saved in " + file.getName());
    }

    public void auditPqcReadiness(String filePath) throws Exception {
        File file = new File(filePath);
        if (!file.exists()) {
            throw new IOException("File not found: " + filePath);
        }

        System.out.println("\n\u001B[34m[Coder] Auditing file for cryptographic vulnerabilities and Post-Quantum Cryptography (PQC) readiness...\u001B[0m");
        String code = Files.readString(file.toPath(), StandardCharsets.UTF_8);

        List<String> issues = new ArrayList<>();
        if (code.contains("RSA") || code.contains("rsa")) {
            issues.add("- Classical RSA detected: Vulnerable to Shor's algorithm. Recommend migration to ML-KEM (Kyber) for encryption or ML-DSA (Dilithium) for signatures.");
        }
        if (code.contains("ECDSA") || code.contains("ecdsa") || code.contains("ecrecover")) {
            issues.add("- Classical ECDSA/ecrecover detected: Elliptic curve cryptography is broken by Shor's algorithm. Recommend migrating smart contracts/signatures to Falcon (FN-DSA) or Dilithium (ML-DSA).");
        }
        if (code.contains("AES/CBC") || code.contains("CBC")) {
            issues.add("- AES-CBC detected: CBC mode lacks authentication and is vulnerable to padding oracle attacks. Recommend upgrading to AES-GCM (256-bit) which provides authenticated encryption (AEAD) resistant to Grover's algorithm search speedups.");
        }
        if (code.contains("MD5") || code.contains("md5") || code.contains("SHA-1") || code.contains("sha1")) {
            issues.add("- Deprecated Hash Algorithm (MD5/SHA-1) detected: Collision vulnerabilities exist. Upgrade to SHA-256/SHA-512 or SHA-3 (Keccak) for quantum-resistant hash-based systems.");
        }

        System.out.println("File Checked: " + filePath);
        System.out.println("Size:         " + file.length() + " bytes");
        System.out.println("--------------------------------------------------");
        
        if (issues.isEmpty()) {
            System.out.println("\u001B[32m[+] Perfect! No weak classical cryptography patterns found.\u001B[0m");
            System.out.println("[+] File is ready for Post-Quantum Cryptography integration.");
        } else {
            System.out.println("\u001B[31m[!] Cryptographic Audit Flags Found:\u001B[0m");
            for (String issue : issues) {
                System.out.println(issue);
            }
            System.out.println("\n\u001B[33mRecommendation: Notarize/seal your files using `/quantum seal <file>`.\u001B[0m");
        }
        System.out.println("--------------------------------------------------");
    }

    public WorkspaceProfile inspectWorkspace(String rootPath) throws Exception {
        Path root = resolveRoot(rootPath);
        WorkspaceInspector inspector = new WorkspaceInspector();
        WorkspaceProfile profile = inspector.inspect(root);

        System.out.println("\n\u001B[1m\u001B[32m=== CODER WORKSPACE PROFILE ===\u001B[0m");
        System.out.println(profile.toHumanReadableSummary());
        System.out.println("\u001B[1m\u001B[32m===============================\u001B[0m");

        memoryEngine.addJournalEntry("Coder Inspect", "Inspected workspace " + profile.getRootPath()
                + " (" + profile.getBuildSystem() + ", " + profile.getSourceFiles().size() + " source files)");
        return profile;
    }

    public BuildVerifier.VerificationResult verifyWorkspace(String rootPath) throws Exception {
        WorkspaceProfile profile = inspectWorkspace(rootPath);
        if (profile.getVerificationCommands().isEmpty()) {
            throw new IOException("No verification command detected for build system: " + profile.getBuildSystem());
        }

        VerificationCommand command = profile.getVerificationCommands().get(0);
        BuildVerifier verifier = new BuildVerifier();
        BuildVerifier.VerificationResult result = verifier.run(Path.of(profile.getRootPath()), command, Duration.ofMinutes(3));

        System.out.println("\n\u001B[1m\u001B[32m=== CODER VERIFICATION RESULT ===\u001B[0m");
        System.out.println(result.toHumanReadableSummary());
        System.out.println("\u001B[1m\u001B[32m=================================\u001B[0m");

        memoryEngine.addJournalEntry("Coder Verify", "Ran " + command.asShellString() + " in "
                + profile.getRootPath() + " with status " + (result.isSuccess() ? "PASS" : "FAIL"));
        return result;
    }

    private Path resolveRoot(String rootPath) {
        if (rootPath == null || rootPath.isBlank()) {
            return Path.of(".");
        }
        return Path.of(rootPath);
    }
}
