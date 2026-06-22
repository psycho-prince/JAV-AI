package com.javai.security;

import com.javai.memory.MemoryEngine;
import java.util.ArrayList;
import java.util.List;

public class EvidenceManager {
    private final MemoryEngine memoryEngine;

    public EvidenceManager(MemoryEngine memoryEngine) {
        this.memoryEngine = memoryEngine;
    }

    public List<String> getRequiredEvidenceForType(String type) {
        List<String> evidence = new ArrayList<>();
        switch (type) {
            case "Cloud IAM":
                evidence.add("Raw HTTP requests/responses showing authentication tokens exchange");
                evidence.add("Target Account IDs proving tenant identities");
                evidence.add("Policy documents or access control configuration payloads");
                break;
            case "Cloud Storage":
                evidence.add("Object listing endpoint response output XML/JSON");
                evidence.add("Successful upload transaction receipts");
                evidence.add("ACL settings definitions trace logs");
                break;
            case "API Endpoints":
                evidence.add("Trace logs containing parameters tampered variables");
                evidence.add("Server return messages confirming structural variations");
                evidence.add("Curl validation parameters proof command");
                break;
            default:
                evidence.add("HTTP tracer logs (headers + body payloads)");
                evidence.add("Visual screenshots or logs validating code actions execution outcomes");
                break;
        }
        return evidence;
    }

    public void attachEvidenceToFinding(int findingId, String title, String content) throws Exception {
        memoryEngine.saveEvidence(findingId, title, content);
    }
}
