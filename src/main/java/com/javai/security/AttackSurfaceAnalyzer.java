package com.javai.security;

import java.util.ArrayList;
import java.util.List;

public class AttackSurfaceAnalyzer {

    public String analyzeTargetType(String targetDomain) {
        String domain = targetDomain.toLowerCase();
        if (domain.contains("iam") || domain.contains("auth") || domain.contains("identity") || domain.contains("login")) {
            return "Cloud IAM";
        } else if (domain.contains("s3") || domain.contains("bucket") || domain.contains("storage") || domain.contains("snapshots")) {
            return "Cloud Storage";
        } else if (domain.contains("api") || domain.contains("graphql")) {
            return "API Endpoints";
        } else if (domain.contains("k8s") || domain.contains("kube") || domain.contains("cluster")) {
            return "Kubernetes";
        } else if (domain.contains("k2") || domain.contains("cloud") || domain.contains("aws") || domain.contains("gcp") || domain.contains("azure")) {
            return "Cloud Provider";
        }
        return "Web Application";
    }

    public List<String> getInterestingAreas(String type) {
        List<String> areas = new ArrayList<>();
        switch (type) {
            case "Cloud IAM":
                areas.add("Authentication Mechanisms");
                areas.add("Session Management & SSO");
                areas.add("Privilege Escalation Boundaries");
                areas.add("Cross-Tenant Access Policies");
                break;
            case "Cloud Storage":
                areas.add("Public Access Permissions");
                areas.add("Bucket ACL & Policy Integrity");
                areas.add("Object Listing Exposure");
                areas.add("Unauthenticated File Uploads");
                break;
            case "API Endpoints":
                areas.add("Endpoint Routing & Layout");
                areas.add("BOLA / IDOR Verification");
                areas.add("HTTP Verb Tampering");
                areas.add("Rate Limiting Limits");
                break;
            case "Kubernetes":
                areas.add("API Server Accessibility");
                areas.add("Service Account Privileges");
                areas.add("Network Security Policies");
                areas.add("Pod Security Boundaries");
                break;
            case "Cloud Provider":
                areas.add("Metadata API Services");
                areas.add("Exposed Management Services");
                areas.add("DNS Domain Configurations");
                areas.add("Resource Creation Limits");
                break;
            default:
                areas.add("Input Validation Checks");
                areas.add("Session State Tracking");
                areas.add("Client-Side Assets Analysis");
                areas.add("Access Privilege Mappings");
                break;
        }
        return areas;
    }

    public List<String> getHighValueCategories(String type) {
        List<String> categories = new ArrayList<>();
        switch (type) {
            case "Cloud IAM":
                categories.add("IDOR / Authentication Bypass");
                categories.add("Privilege Escalation");
                categories.add("Token Handling Flaws");
                break;
            case "Cloud Storage":
                categories.add("Information Disclosure");
                categories.add("Arbitrary File Write / Upload");
                break;
            case "API Endpoints":
                categories.add("IDOR (BOLA)");
                categories.add("SSRF");
                categories.add("GraphQL Batching / Depth Abuse");
                break;
            case "Kubernetes":
                categories.add("Cluster Admin Takeover");
                categories.add("Pod Container Escape");
                break;
            default:
                categories.add("Remote Code Execution (RCE)");
                categories.add("SQL Injection");
                categories.add("SSRF with Metadata Access");
                break;
        }
        return categories;
    }
}
