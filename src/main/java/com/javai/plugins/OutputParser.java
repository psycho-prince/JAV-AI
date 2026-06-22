package com.javai.plugins;

import com.javai.memory.MemoryEngine;
import java.io.BufferedReader;
import java.io.StringReader;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class OutputParser {
    
    public static void parseAndStore(String pluginName, String output, int targetId, MemoryEngine memoryEngine) {
        if (output == null || output.trim().isEmpty()) {
            return;
        }

        try {
            switch (pluginName.toLowerCase()) {
                case "subfinder":
                    parseSubfinder(output, targetId, memoryEngine);
                    break;
                case "nmap":
                    parseNmap(output, targetId, memoryEngine);
                    break;
                case "httpx":
                    parseHttpx(output, targetId, memoryEngine);
                    break;
                case "katana":
                    parseKatana(output, targetId, memoryEngine);
                    break;
                case "dns":
                    parseDns(output, targetId, memoryEngine);
                    break;
                case "whois":
                    parseWhois(output, targetId, memoryEngine);
                    break;
            }
        } catch (Exception e) {
            System.out.println("[OutputParser] WARNING: Error parsing plugin output: " + e.getMessage());
        }
    }

    private static void parseSubfinder(String output, int targetId, MemoryEngine memoryEngine) throws Exception {
        try (BufferedReader reader = new BufferedReader(new StringReader(output))) {
            String line;
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty() || line.startsWith("[") || line.contains(" ")) {
                    continue;
                }
                // Store subdomain as a domain asset
                memoryEngine.addAsset(targetId, "subdomain", line, "Discovered via subfinder");
            }
        }
    }

    private static void parseNmap(String output, int targetId, MemoryEngine memoryEngine) throws Exception {
        // Find lines like: 80/tcp open http
        Pattern pattern = Pattern.compile("(\\d+)/tcp\\s+(\\w+)\\s+(\\S+)");
        try (BufferedReader reader = new BufferedReader(new StringReader(output))) {
            String line;
            while ((line = reader.readLine()) != null) {
                Matcher matcher = pattern.matcher(line);
                if (matcher.find()) {
                    String port = matcher.group(1);
                    String state = matcher.group(2);
                    String service = matcher.group(3);
                    if ("open".equalsIgnoreCase(state)) {
                        memoryEngine.addAsset(targetId, "port", port, "Service: " + service + " (nmap)");
                    }
                }
            }
        }
    }

    private static void parseHttpx(String output, int targetId, MemoryEngine memoryEngine) throws Exception {
        try (BufferedReader reader = new BufferedReader(new StringReader(output))) {
            String line;
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty() || line.startsWith("[")) {
                    continue;
                }
                // Matches format like: http://example.com [200 OK] ...
                String[] parts = line.split("\\s+", 3);
                String url = parts[0];
                String status = parts.length > 1 ? parts[1] : "";
                String metadata = parts.length > 2 ? parts[2] : "";
                memoryEngine.addAsset(targetId, "url", url, "Status: " + status + " " + metadata + " (httpx)");
            }
        }
    }

    private static void parseKatana(String output, int targetId, MemoryEngine memoryEngine) throws Exception {
        try (BufferedReader reader = new BufferedReader(new StringReader(output))) {
            String line;
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty() || line.startsWith("[")) {
                    continue;
                }
                memoryEngine.addAsset(targetId, "url", line, "Discovered via katana");
            }
        }
    }

    private static void parseDns(String output, int targetId, MemoryEngine memoryEngine) throws Exception {
        try (BufferedReader reader = new BufferedReader(new StringReader(output))) {
            String line;
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (line.contains("has address")) {
                    String[] parts = line.split("has address");
                    String host = parts[0].trim();
                    String ip = parts[1].trim();
                    memoryEngine.addAsset(targetId, "ip", ip, "Host: " + host + " (dns resolution)");
                } else if (line.contains("is an alias for")) {
                    String[] parts = line.split("is an alias for");
                    String host = parts[0].trim();
                    String alias = parts[1].trim();
                    memoryEngine.addAsset(targetId, "cname", alias, "Host: " + host + " (dns CNAME)");
                }
            }
        }
    }

    private static void parseWhois(String output, int targetId, MemoryEngine memoryEngine) throws Exception {
        try (BufferedReader reader = new BufferedReader(new StringReader(output))) {
            String line;
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (line.startsWith("Registrar:") || line.startsWith("Name Server:")) {
                    String[] parts = line.split(":", 2);
                    String key = parts[0].trim();
                    String val = parts[1].trim();
                    memoryEngine.addAsset(targetId, key.toLowerCase().replace(" ", "_"), val, "WHOIS records details");
                }
            }
        }
    }
}
