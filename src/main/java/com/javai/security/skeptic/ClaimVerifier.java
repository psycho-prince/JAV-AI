package com.javai.security.skeptic;

public class ClaimVerifier {

    public boolean hasConfirmationClaim(String title, String description) {
        String combined = (title + " " + description).toLowerCase();
        
        // Match terms asserting a vulnerability is verified or confirmed
        if (combined.contains("confirmed") || 
            combined.contains("verified") || 
            combined.contains("demonstrated") || 
            combined.contains("exploited successfully") || 
            combined.contains("successful exploit") ||
            combined.contains("breached boundary") ||
            combined.contains("cross-tenant read")) {
            return true;
        }
        return false;
    }
    
    public boolean hasHighSeverityClaim(String severity) {
        String sev = severity.toLowerCase();
        return sev.contains("high") || sev.contains("critical");
    }
}
