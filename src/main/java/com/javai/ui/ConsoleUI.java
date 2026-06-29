package com.javai.ui;

import com.javai.core.JavAI;
import com.javai.models.Message;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.List;

public class ConsoleUI {
    private final JavAI javAI;
    private boolean running = true;

    public ConsoleUI(JavAI javAI) {
        this.javAI = javAI;
    }

    public void run() {
        printBanner();
        
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(System.in))) {
            while (running) {
                String activeProjName = javAI.getMemoryEngine().getActiveProjectName();
                String activeModelName = javAI.getModelRouter().getActiveModelName();
                String activeProgName = javAI.getMemoryEngine().getActiveProgramName();
                if (activeProgName == null) activeProgName = "None";
                
                System.out.printf("\n\u001B[34m[Proj: %s | Model: %s | Prog: %s]\u001B[0m\n\u001B[32mresearcher@javai:~$\u001B[0m ",
                        activeProjName, activeModelName, activeProgName);
                String input = reader.readLine();
                if (input == null) break;
                
                input = input.trim();
                if (input.isEmpty()) continue;

                if (input.startsWith("/")) {
                    handleCommand(input);
                } else {
                    StringBuilder queryBuilder = new StringBuilder(input);
                    // Read all remaining lines in the paste buffer
                    while (reader.ready()) {
                        String extra = reader.readLine();
                        if (extra == null) break;
                        queryBuilder.append("\n").append(extra);
                    }
                    handleQuery(queryBuilder.toString().trim());
                }
            }
        } catch (Exception e) {
            System.err.println("Console UI error: " + e.getMessage());
        }
        
        System.out.println("Shutdown signal received. Exiting JavAI.");
    }

    private void printBanner() {
        System.out.println("\u001B[36m      __                  ___    ____");
        System.out.println("     / /___ __   ______ _/   |  /  _/");
        System.out.println("__  / / __ `/ | / / __ `/ /| |  / /  ");
        System.out.println("/ /_/ / /_/ /| |/ / /_/ / ___ |_/ /   ");
        System.out.println("\\____/\\__,_/ |___/\\__,_/_/  |_/___/   \u001B[0m");
        System.out.println("  \u001B[1m\u001B[35m* JavAI Research Edition v1.0 *\u001B[0m");
        System.out.println("Type your queries, or use \u001B[33m`/help`\u001B[0m to see system commands.");
    }

    private void handleCommand(String input) {
        String[] parts = input.split(" ", 2);
        String cmd = parts[0].toLowerCase();
        String arg = parts.length > 1 ? parts[1] : "";

        try {
            switch (cmd) {
                case "/exit":
                case "/quit":
                    running = false;
                    break;
                case "/help":
                    printHelp();
                    break;
                case "/status":
                    printStatus();
                    break;
                case "/new":
                case "/clear":
                    javAI.getMemoryEngine().clearActiveConversation();
                    System.out.print("\033[H\033[2J");
                    System.out.flush();
                    printBanner();
                    System.out.println("\u001B[32m[System] Active conversation history cleared and terminal screen reset.\u001B[0m");
                    break;
                case "/history":
                    printHistory();
                    break;
                case "/notes":
                    handleNotesCommand(arg);
                    break;
                case "/model":
                    handleModelCommand(arg);
                    break;
                case "/project":
                    handleProjectCommand(arg);
                    break;
                case "/finding":
                    handleFindingCommand(arg);
                    break;
                case "/task":
                    handleTaskCommand(arg);
                    break;
                case "/remember":
                    handleRememberCommand(arg);
                    break;
                case "/run":
                    handleRunCommand(arg);
                    break;
                case "/target":
                    handleTargetCommand(arg);
                    break;
                case "/recon":
                    handleReconCommand(arg);
                    break;
                case "/scan":
                    handleScanCommand(arg);
                    break;
                case "/report":
                    handleReportCommand(arg);
                    break;
                case "/program":
                    handleProgramCommand(arg);
                    break;
                case "/assess":
                    handleAssessCommand(arg);
                    break;
                case "/learn":
                    handleLearnCommand(arg);
                    break;
                case "/coverage":
                    showCoverage();
                    break;
                case "/graph":
                    handleGraphCommand(arg);
                    break;
                case "/observation":
                    handleObservationCommand(arg);
                    break;
                case "/journal":
                    handleJournalCommand(arg);
                    break;
                case "/dashboard":
                    handleDashboardCommand();
                    break;
                case "/decision":
                    handleDecisionCommand(arg);
                    break;
                case "/snapshot":
                    handleSnapshotCommand(arg);
                    break;
                case "/council":
                    handleCouncilCommand(arg);
                    break;
                case "/quantum":
                    handleQuantumCommand(arg);
                    break;
                case "/coder":
                    handleCoderCommand(arg);
                    break;
                case "/verify":
                    handleCoderCommand("verify " + arg);
                    break;
                case "/subfinder":
                case "/httpx":
                case "/nmap":
                case "/katana":
                case "/whois":
                case "/dns":
                    handleDirectPluginCommand(cmd, arg);
                    break;
                default:
                    System.out.println("[System] Unknown command: " + cmd + ". Type `/help` for commands.");
            }
        } catch (Exception e) {
            System.out.println("[Error] Command failed: " + e.getMessage());
        }
    }

    private void handleRunCommand(String arg) {
        String[] parts = arg.trim().split(" ", 2);
        String pluginName = parts[0].toLowerCase();
        String pluginArgStr = parts.length > 1 ? parts[1].trim() : "";

        if (pluginName.isEmpty() || pluginName.equals("list")) {
            System.out.println("Available Plugins:");
            for (com.javai.plugins.Plugin plugin : javAI.getPlugins().values()) {
                System.out.println("  - " + plugin.getName() + ": " + plugin.getDescription());
            }
            return;
        }

        com.javai.plugins.Plugin plugin = javAI.getPlugins().get(pluginName);
        if (plugin == null) {
            System.out.println("[System] Plugin '" + pluginName + "' not registered. Available: " + javAI.getPlugins().keySet());
            return;
        }

        String[] pluginArgs = pluginArgStr.isEmpty() ? new String[0] : pluginArgStr.split("\\s+");
        try {
            System.out.println("[System] Executing plugin: " + plugin.getName() + "...");
            String result = plugin.execute(pluginArgs);
            System.out.println("\n=== Plugin Output ===");
            System.out.println(result);
            System.out.println("=====================");
        } catch (Exception e) {
            System.out.println("[Error] Plugin execution failed: " + e.getMessage());
        }
    }

    private void handleTargetCommand(String arg) throws Exception {
        String[] parts = arg.trim().split(" ", 2);
        String subcmd = parts[0].toLowerCase();
        String subarg = parts.length > 1 ? parts[1].trim() : "";

        if (subcmd.isEmpty() || subcmd.equals("list")) {
            listTargets();
        } else if (subcmd.equals("add")) {
            if (subarg.isEmpty()) {
                System.out.println("[System] Invalid format. Use: `/target add domain.com`");
                return;
            }
            javAI.getMemoryEngine().addTarget(subarg);
            System.out.println("[System] Target '" + subarg + "' registered successfully in project '" + javAI.getMemoryEngine().getActiveProjectName() + "'.");
        } else if (subcmd.equals("assets")) {
            listAssets();
        } else if (subcmd.equals("program")) {
            if (subarg.isEmpty()) {
                System.out.println("[System] Invalid format. Use: `/target program <name>`");
                return;
            }
            boolean success = javAI.getMemoryEngine().setActiveProjectProgram(subarg);
            if (success) {
                String realProgName = javAI.getMemoryEngine().getActiveProgramName();
                System.out.println("[System] Active project '" + javAI.getMemoryEngine().getActiveProjectName() + "' linked to program '" + realProgName + "'.");
                System.out.println("\nProgram Type:");
                System.out.println(javAI.getMemoryEngine().getProgramType(realProgName));
                System.out.println("\nHigh Value:");
                for (String focus : javAI.getMemoryEngine().getProgramRules(realProgName, "focus")) {
                    System.out.println("- " + focus);
                }
                System.out.println("\nLow Value:");
                for (String low : javAI.getMemoryEngine().getProgramRules(realProgName, "low_value")) {
                    System.out.println("- " + low);
                }
            } else {
                System.out.println("[System] Program '" + subarg + "' not found. Available programs: K2 Cloud, TMF Group, NASA, Playtika.");
            }
        } else {
            System.out.println("[System] Unknown target command. Use `/target add <domain>`, `/target program <name>`, `/target list` or `/target assets`.");
        }
    }

    private void handleReconCommand(String arg) throws Exception {
        String[] parts = arg.trim().split(" ", 2);
        String subcmd = parts[0].toLowerCase();
        String subarg = parts.length > 1 ? parts[1].trim() : "";

        if (subcmd.isEmpty() || subcmd.equals("history") || subcmd.equals("list")) {
            listScans();
        } else if (subcmd.equals("start")) {
            if (subarg.isEmpty()) {
                System.out.println("[System] Invalid format. Use: `/recon start domain.com` or `/recon start domain.com | tool`");
                return;
            }
            String domain = subarg;
            String tool = "all";
            if (subarg.contains("|")) {
                String[] scanParts = subarg.split("\\|", 2);
                domain = scanParts[0].trim();
                tool = scanParts[1].trim();
            }

            int targetId = javAI.getMemoryEngine().getTargetId(domain);
            if (targetId == -1) {
                // Auto register target
                javAI.getMemoryEngine().addTarget(domain);
                targetId = javAI.getMemoryEngine().getTargetId(domain);
            }

            System.out.println("[System] Starting recon sequence targeting '" + domain + "' using: " + tool);
            
            // Execute subfinder automatically and store findings
            if (tool.equalsIgnoreCase("all") || tool.equalsIgnoreCase("subfinder")) {
                runPluginAndStore(targetId, "subfinder", new String[]{"-d", domain});
            }
            // Execute httpx automatically
            if (tool.equalsIgnoreCase("all") || tool.equalsIgnoreCase("httpx")) {
                runPluginAndStore(targetId, "httpx", new String[]{"-u", domain});
            }
            // Execute nmap automatically
            if (tool.equalsIgnoreCase("all") || tool.equalsIgnoreCase("nmap")) {
                runPluginAndStore(targetId, "nmap", new String[]{"-F", domain});
            }
            // Execute katana automatically
            if (tool.equalsIgnoreCase("all") || tool.equalsIgnoreCase("katana")) {
                runPluginAndStore(targetId, "katana", new String[]{"-u", "http://" + domain});
            }
            // Execute dns automatically
            if (tool.equalsIgnoreCase("all") || tool.equalsIgnoreCase("dns")) {
                runPluginAndStore(targetId, "dns", new String[]{domain});
            }
            // Execute whois automatically
            if (tool.equalsIgnoreCase("all") || tool.equalsIgnoreCase("whois")) {
                runPluginAndStore(targetId, "whois", new String[]{domain});
            }

            System.out.println("[System] Recon scan sequence completed successfully.");
        } else if (subcmd.equals("status")) {
            System.out.println("Recon Status Summary:");
            System.out.println("  - Active Project: " + javAI.getMemoryEngine().getActiveProjectName());
            System.out.println("  - Registered Targets: " + javAI.getMemoryEngine().getTargetCount());
            System.out.println("  - Discovered Assets: " + javAI.getMemoryEngine().getAssetCount());
            System.out.println("  - Completed Scans: " + javAI.getMemoryEngine().getScanCount());
        } else {
            System.out.println("[System] Unknown recon command. Use `/recon start <domain>`, `/recon status`, or `/recon history`.");
        }
    }

    private void handleScanCommand(String arg) throws Exception {
        listScans();
    }

    private void handleDirectPluginCommand(String cmd, String arg) throws Exception {
        String pluginName = cmd.substring(1).toLowerCase();
        // Resolve active target domain from args if present to auto link results
        String domainArg = arg.trim();
        String[] pluginArgs = domainArg.isEmpty() ? new String[0] : domainArg.split("\\s+");
        
        // Find target domain to link assets
        String targetDomain = null;
        for (int i = 0; i < pluginArgs.length; i++) {
            if (("-d".equals(pluginArgs[i]) || "-u".equals(pluginArgs[i]) || "-l".equals(pluginArgs[i])) && i + 1 < pluginArgs.length) {
                targetDomain = pluginArgs[i + 1];
                break;
            }
        }
        if (targetDomain == null && pluginArgs.length > 0) {
            targetDomain = pluginArgs[pluginArgs.length - 1];
        }
        if (targetDomain != null && targetDomain.startsWith("http")) {
            try {
                java.net.URI uri = new java.net.URI(targetDomain);
                targetDomain = uri.getHost();
            } catch (Exception ignored) {}
        }
        if (targetDomain == null || targetDomain.trim().isEmpty() || targetDomain.startsWith("-")) {
            targetDomain = "localhost";
        }

        int targetId = javAI.getMemoryEngine().getTargetId(targetDomain);
        if (targetId == -1) {
            javAI.getMemoryEngine().addTarget(targetDomain);
            targetId = javAI.getMemoryEngine().getTargetId(targetDomain);
        }

        runPluginAndStore(targetId, pluginName, pluginArgs);
    }

    private void runPluginAndStore(int targetId, String pluginName, String[] args) throws Exception {
        com.javai.plugins.Plugin plugin = javAI.getPlugins().get(pluginName);
        if (plugin == null) {
            System.out.println("[System] Plugin '" + pluginName + "' not registered.");
            return;
        }

        int scanId = javAI.getMemoryEngine().createScan(targetId, pluginName, "running");
        try {
            System.out.println("[System] Executing plugin: " + pluginName + "...");
            String result = plugin.execute(args);
            javAI.getMemoryEngine().updateScanStatus(scanId, "completed");
            javAI.getMemoryEngine().saveScanResult(scanId, result);
            
            // Automatically parse results and store assets
            com.javai.plugins.OutputParser.parseAndStore(pluginName, result, targetId, javAI.getMemoryEngine());

            // Write raw scan output into workspace/scans/ folder
            String sanitizedTarget = "target_" + targetId;
            java.io.File scanLog = new java.io.File("workspace/scans/" + pluginName + "_" + sanitizedTarget + "_" + System.currentTimeMillis() + ".txt");
            try (java.io.FileWriter fw = new java.io.FileWriter(scanLog)) {
                fw.write(result);
            }
            
            System.out.println("\n=== Plugin Output ===");
            System.out.println(result);
            System.out.println("=====================");
            System.out.println("[System] Scan results archived in workspace/scans/ and parsed as project assets.");
        } catch (Exception e) {
            javAI.getMemoryEngine().updateScanStatus(scanId, "failed");
            System.out.println("[Error] Plugin " + pluginName + " execution failed: " + e.getMessage());
        }
    }

    private void listTargets() throws Exception {
        String sql = "SELECT id, domain, created_at FROM targets WHERE project_id = ? ORDER BY created_at DESC";
        try (Connection conn = javAI.getDatabaseManager().getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, javAI.getMemoryEngine().getActiveProjectId());
            try (ResultSet rs = stmt.executeQuery()) {
                System.out.println("\n=== Target Scopes ===");
                int count = 0;
                while (rs.next()) {
                    count++;
                    System.out.printf("[%d] ID: %d | Domain: %s\n    Registered: %s\n\n",
                            count,
                            rs.getInt("id"),
                            rs.getString("domain"),
                            new java.util.Date(rs.getLong("created_at")).toString());
                }
                if (count == 0) {
                    System.out.println("No targets defined for active project.");
                }
                System.out.println("=====================");
            }
        }
    }

    private void listScans() throws Exception {
        String sql = "SELECT s.id, t.domain, s.tool, s.status, s.created_at FROM scans s " +
                     "JOIN targets t ON s.target_id = t.id WHERE s.project_id = ? ORDER BY s.created_at DESC";
        try (Connection conn = javAI.getDatabaseManager().getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, javAI.getMemoryEngine().getActiveProjectId());
            try (ResultSet rs = stmt.executeQuery()) {
                System.out.println("\n=== Recon Scan Log ===");
                int count = 0;
                while (rs.next()) {
                    count++;
                    System.out.printf("[%d] Scan ID: %d | Target: %s | Tool: %s\n    Status: %s\n    Time: %s\n\n",
                            count,
                            rs.getInt("id"),
                            rs.getString("domain"),
                            rs.getString("tool"),
                            rs.getString("status"),
                            new java.util.Date(rs.getLong("created_at")).toString());
                }
                if (count == 0) {
                    System.out.println("No scans recorded for active project.");
                }
                System.out.println("======================");
            }
        }
    }

    private void listAssets() throws Exception {
        String sql = "SELECT a.id, t.domain, a.type, a.value, a.metadata, a.created_at FROM assets a " +
                     "JOIN targets t ON a.target_id = t.id WHERE a.project_id = ? ORDER BY a.created_at DESC";
        try (Connection conn = javAI.getDatabaseManager().getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, javAI.getMemoryEngine().getActiveProjectId());
            try (ResultSet rs = stmt.executeQuery()) {
                System.out.println("\n=== Discovered Assets ===");
                int count = 0;
                while (rs.next()) {
                    count++;
                    System.out.printf("[%d] ID: %d | Target: %s | Type: %s\n    Value: %s\n    Notes: %s\n    Discovered: %s\n\n",
                            count,
                            rs.getInt("id"),
                            rs.getString("domain"),
                            rs.getString("type"),
                            rs.getString("value"),
                            rs.getString("metadata"),
                            new java.util.Date(rs.getLong("created_at")).toString());
                }
                if (count == 0) {
                    System.out.println("No assets discovered yet for active project.");
                }
                System.out.println("=========================");
            }
        }
    }

    private void handleModelCommand(String arg) {
        String[] parts = arg.trim().split(" ", 2);
        String subcmd = parts[0].toLowerCase();
        String subarg = parts.length > 1 ? parts[1].trim() : "";

        if (subcmd.isEmpty() || subcmd.equals("status")) {
            System.out.println("Model Router Status:");
            System.out.println("  - Active Provider Type: " + javAI.getModelRouter().getActiveModelName());
            System.out.println("  - Target Model Name:    " + javAI.getModelConfig().getModelName());
            System.out.println("  - Connection Endpoint:  " + javAI.getModelConfig().getEndpoint());
            System.out.println("  - Timeout Config:       " + javAI.getModelConfig().getTimeoutSeconds() + " seconds");
            System.out.println("  - Temperature:          " + javAI.getModelConfig().getTemperature());
        } else if (subcmd.equals("list")) {
            System.out.println("Available Model Providers:");
            for (String modelName : javAI.getModelRouter().getAvailableModels()) {
                System.out.println("  - " + modelName + (modelName.equals(javAI.getModelRouter().getActiveModelName()) ? " (active)" : ""));
            }
        } else if (subcmd.equals("switch")) {
            if (subarg.isEmpty()) {
                System.out.println("[System] Please specify a model provider. E.g. `/model switch qwen` or `/model switch openai`.");
                return;
            }
            boolean success = javAI.getModelRouter().setActiveModel(subarg);
            if (success) {
                System.out.println("[System] Switched active model provider to: " + javAI.getModelRouter().getActiveModelName());
            } else {
                System.out.println("[System] Provider '" + subarg + "' not recognized. Available: " + javAI.getModelRouter().getAvailableModels());
            }
        } else if (subcmd.equals("test")) {
            System.out.println("[System] Testing connectivity to active model provider '" + javAI.getModelRouter().getActiveModelName() + "' at endpoint '" + javAI.getModelConfig().getEndpoint() + "'...");
            try {
                com.javai.llm.LLMRequest request = new com.javai.llm.LLMRequest();
                List<com.javai.models.Message> messages = new java.util.ArrayList<>();
                messages.add(new com.javai.models.Message("user", "Hello connection test"));
                request.setMessages(messages);
                request.setTemperature(0.1);
                
                long start = System.currentTimeMillis();
                com.javai.llm.LLMResponse response = javAI.getModelRouter().complete(request);
                long duration = System.currentTimeMillis() - start;
                
                System.out.println("\n=== Connection Test Result ===");
                if (response.isFallback()) {
                    System.out.println("Status: OFFLINE (Using simulation fallback)");
                    System.out.println("Error: " + response.getError());
                } else {
                    System.out.println("Status: ONLINE");
                }
                System.out.println("Response Time: " + duration + " ms");
                System.out.println("Response Snippet: " + response.getContent().substring(0, Math.min(100, response.getContent().length())) + "...");
                System.out.println("==============================");
            } catch (Exception e) {
                System.out.println("\n=== Connection Test Result ===");
                System.out.println("Status: OFFLINE (Connection failed)");
                System.out.println("Error: " + e.getMessage());
                System.out.println("==============================");
            }
        } else if (subcmd.equals("configure")) {
            if (subarg.isEmpty()) {
                System.out.println("[System] Use: `/model configure <endpoint|key|model|temp|timeout> <value>`");
                return;
            }
            String[] configParts = subarg.split(" ", 2);
            if (configParts.length < 2) {
                System.out.println("[System] Please specify a value. E.g. `/model configure temp 0.8`.");
                return;
            }
            String key = configParts[0].toLowerCase();
            String value = configParts[1].trim();
            
            com.javai.llm.LocalModelConfig config = javAI.getModelConfig();
            if (key.equals("endpoint")) {
                config.setEndpoint(value);
                System.out.println("[System] Model endpoint set to: " + value);
            } else if (key.equals("key")) {
                config.setApiKey(value);
                System.out.println("[System] Model API key updated.");
            } else if (key.equals("model")) {
                config.setModelName(value);
                System.out.println("[System] Target model name set to: " + value);
            } else if (key.equals("temp")) {
                try {
                    config.setTemperature(Double.parseDouble(value));
                    System.out.println("[System] Model temperature set to: " + value);
                } catch (NumberFormatException e) {
                    System.out.println("[Error] Invalid temperature value: " + value);
                }
            } else if (key.equals("timeout")) {
                try {
                    config.setTimeoutSeconds(Integer.parseInt(value));
                    System.out.println("[System] Model timeout set to: " + value + " seconds");
                } catch (NumberFormatException e) {
                    System.out.println("[Error] Invalid timeout value: " + value);
                }
            } else {
                System.out.println("[System] Unknown configuration key: " + key + ". Available: endpoint, key, model, temp, timeout.");
            }
            
            try {
                javAI.getModelRouter().initialize();
            } catch (Exception e) {
                System.out.println("[Warning] Failed to re-initialize model router: " + e.getMessage());
            }
        } else {
            System.out.println("[System] Unknown model command. Use `/model status`, `/model list`, or `/model switch <name>`.");
        }
    }

    private void handleProjectCommand(String arg) throws Exception {
        String[] parts = arg.trim().split(" ", 2);
        String subcmd = parts[0].toLowerCase();
        String subarg = parts.length > 1 ? parts[1].trim() : "";

        if (subcmd.isEmpty() || subcmd.equals("list")) {
            listProjects();
        } else if (subcmd.equals("create")) {
            if (subarg.isEmpty()) {
                System.out.println("[System] Invalid format. Use: `/project create Name` or `/project create Name | Description`");
                return;
            }
            String name = subarg;
            String description = "No description";
            if (subarg.contains("|")) {
                String[] createParts = subarg.split("\\|", 2);
                name = createParts[0].trim();
                description = createParts[1].trim();
            }
            int id = javAI.getMemoryEngine().createProject(name, description);
            if (id != -1) {
                System.out.println("[System] Project '" + name + "' created successfully with ID " + id);
            } else {
                System.out.println("[System] Failed to create project. Name might already exist.");
            }
        } else if (subcmd.equals("switch")) {
            if (subarg.isEmpty()) {
                System.out.println("[System] Please specify a project name to switch to. E.g. `/project switch default`.");
                return;
            }
            boolean success = javAI.getMemoryEngine().switchProject(subarg);
            if (success) {
                System.out.println("[System] Switched active project to: " + javAI.getMemoryEngine().getActiveProjectName() + " (ID: " + javAI.getMemoryEngine().getActiveProjectId() + ")");
            } else {
                System.out.println("[System] Project '" + subarg + "' not found. Create it first using `/project create " + subarg + "`");
            }
        } else {
            System.out.println("[System] Unknown project command. Use `/project create <name>`, `/project switch <name>`, or `/project list`.");
        }
    }

    private void handleFindingCommand(String arg) throws Exception {
        String[] parts = arg.trim().split(" ", 2);
        String subcmd = parts[0].toLowerCase();
        String subarg = parts.length > 1 ? parts[1].trim() : "";

        if (subcmd.isEmpty() || subcmd.equals("list")) {
            listFindings();
        } else if (subcmd.equals("add")) {
            if (subarg.isEmpty() || !subarg.contains("|")) {
                System.out.println("[System] Invalid format. Use: `/finding add Title | Severity | Description` or `/finding add Title | Description` (Severity defaults to Info)");
                return;
            }
            String[] addParts = subarg.split("\\|", 3);
            String title = addParts[0].trim();
            String severity = "Info";
            String description = "";
            if (addParts.length == 2) {
                description = addParts[1].trim();
            } else {
                severity = addParts[1].trim();
                description = addParts[2].trim();
            }
            // Retrieve active target ID for budget check
            int targetId = -1;
            String targetSql = "SELECT id FROM targets WHERE project_id = ? ORDER BY created_at DESC LIMIT 1";
            try (Connection conn = javAI.getDatabaseManager().getConnection();
                 PreparedStatement stmt = conn.prepareStatement(targetSql)) {
                stmt.setInt(1, javAI.getMemoryEngine().getActiveProjectId());
                try (ResultSet rs = stmt.executeQuery()) {
                    if (rs.next()) {
                        targetId = rs.getInt("id");
                    }
                }
            }

            try {
                com.javai.security.skeptic.HypothesisBudget budget = new com.javai.security.skeptic.HypothesisBudget(javAI.getDatabaseManager());
                if (budget.isBudgetExceeded(javAI.getMemoryEngine().getActiveProjectId(), targetId)) {
                    System.out.println("[Error] Cannot add finding: Hypothesis budget exceeded (Max 3 active untested hypotheses allowed per target area). Acquire observations or attach evidence first.");
                    return;
                }
            } catch (Exception e) {
                System.out.println("[Warning] Budget verification error: " + e.getMessage());
            }

            int findingId = javAI.getMemoryEngine().addFinding(title, severity, description);
            System.out.println("[System] Finding '" + title + "' added successfully to active project '" + javAI.getMemoryEngine().getActiveProjectName() + "' (ID: " + findingId + ").");
            try {
                com.javai.security.skeptic.SkepticEngine skeptic = new com.javai.security.skeptic.SkepticEngine(javAI.getDatabaseManager());
                com.javai.security.skeptic.SkepticEngine.VerificationReport report = skeptic.verifyFinding(findingId, title, severity, description);
                
                javAI.getMemoryEngine().updateFindingStatus(findingId, report.getState().name(), report.getConfidence(), report.getSeverity(), report.getEvidenceCount());
                
                System.out.println("\n=== Skeptic Engine Verification ===");
                System.out.println("Status: " + report.getState());
                System.out.println("Severity: " + report.getSeverity() + " (was: " + severity + ")");
                System.out.printf("Confidence: %.0f%%\n", report.getConfidence() * 100);
                System.out.println("Evidence Count: " + report.getEvidenceCount());
                System.out.println("Rationale: " + report.getReason());
                System.out.println("===================================");
                
                severity = report.getSeverity();
            } catch (Exception e) {
                System.out.println("[Warning] Skeptic verification failed: " + e.getMessage());
            }
            try {
                com.javai.plugins.scoring.FindingScorer scorer = new com.javai.plugins.scoring.FindingScorer(javAI.getMemoryEngine());
                com.javai.plugins.scoring.FindingScorer.ScoreResult scoreRes = scorer.scoreFinding(title, severity, description);
                System.out.println("\n=== Program Scorer Assessment ===");
                System.out.println(scoreRes.toString());
                System.out.println("=================================");
            } catch (Exception e) {
                System.out.println("[Warning] Scoring failed: " + e.getMessage());
            }
        } else {
            System.out.println("[System] Unknown finding command. Use `/finding add [T] | [S] | [D]` or `/finding list`.");
        }
    }

    private void handleTaskCommand(String arg) throws Exception {
        String[] parts = arg.trim().split(" ", 2);
        String subcmd = parts[0].toLowerCase();
        String subarg = parts.length > 1 ? parts[1].trim() : "";

        if (subcmd.isEmpty() || subcmd.equals("list")) {
            listTasks();
        } else if (subcmd.equals("add")) {
            if (subarg.isEmpty()) {
                System.out.println("[System] Invalid format. Use: `/task add Title` or `/task add Title | Status`");
                return;
            }
            String title = subarg;
            String status = "pending";
            if (subarg.contains("|")) {
                String[] addParts = subarg.split("\\|", 2);
                title = addParts[0].trim();
                status = addParts[1].trim();
            }
            javAI.getMemoryEngine().addTask(title, status);
            System.out.println("[System] Task '" + title + "' (" + status + ") added successfully to active project '" + javAI.getMemoryEngine().getActiveProjectName() + "'.");
        } else {
            System.out.println("[System] Unknown task command. Use `/task add [T] | [S]` or `/task list`.");
        }
    }

    private void handleRememberCommand(String arg) throws Exception {
        String[] parts = arg.trim().split(" ", 2);
        String subcmd = parts[0].toLowerCase();
        String subarg = parts.length > 1 ? parts[1].trim() : "";

        if (subcmd.isEmpty() || subcmd.equals("list")) {
            listKnowledge();
        } else if (subcmd.equals("add") || !arg.trim().isEmpty()) {
            String payload = arg.trim();
            if (payload.startsWith("add ")) {
                payload = payload.substring(4).trim();
            }
            if (payload.isEmpty() || !payload.contains("|")) {
                System.out.println("[System] Invalid format. Use: `/remember [Key] | [Value]` or `/remember [Key] | [Value] | [Category]`");
                return;
            }
            String[] addParts = payload.split("\\|", 3);
            String key = addParts[0].trim();
            String value = addParts[1].trim();
            String category = "General";
            if (addParts.length == 3) {
                category = addParts[2].trim();
            }
            javAI.getMemoryEngine().saveKnowledge(key, value, category);
            System.out.println("[System] Knowledge saved successfully: " + key + " -> " + category);
        } else {
            System.out.println("[System] Unknown remember command. Use `/remember [Key] | [Value]` or `/remember list`.");
        }
    }

    private void handleQuery(String query) {
        try {
            String reply = javAI.getAgentEngine().processQuery(query);
            System.out.println("\n[JavAI]: " + reply);
        } catch (Exception e) {
            System.out.println("\n[Error] Failed to process prompt: " + e.getMessage());
        }
    }

    private void printHelp() {
        System.out.println("Available Commands:");
        System.out.println("  /help                   Show this help menu.");
        System.out.println("  /status                 Show system status and configuration.");
        System.out.println("  /history                Print current session's message logs.");
        System.out.println("  /clear                  Wipe active session logs from the local database.");
        System.out.println("  /notes list             List all saved local research notes.");
        System.out.println("  /notes add [T] | [C]   Create a persistent research note (e.g. `/notes add Sample | Content text`).");
        System.out.println("  /model status           Show current model configuration.");
        System.out.println("  /model list             List all registered LLM models.");
        System.out.println("  /model switch [name]    Switch active model (e.g., `/model switch qwen`).");
        System.out.println("  /model test             Test connection to the active LLM provider.");
        System.out.println("  /model configure [k] [v]Set config parameters: endpoint, key, model, temp, timeout.");
        System.out.println("  /project create [N]     Create a new project (e.g., `/project create myproject`).");
        System.out.println("  /project switch [N]     Switch active project.");
        System.out.println("  /project list           List all projects.");
        System.out.println("  /finding add [T]|[S]|[D] Add a finding (e.g. `/finding add SQLi | High | Exploit parameter ID`).");
        System.out.println("  /finding list           List project findings.");
        System.out.println("  /task add [T] | [S]     Add a task (e.g. `/task add Scan | pending`).");
        System.out.println("  /task list              List project tasks.");
        System.out.println("  /remember [K] | [V]     Add a knowledge entry.");
        System.out.println("  /remember list          List knowledge entries.");
        System.out.println("  /target add [domain]    Register target scope (e.g. `/target add example.com`).");
        System.out.println("  /target program [name]  Link active project to a bug bounty program.");
        System.out.println("  /target list            List project target domains.");
        System.out.println("  /target assets          List all discovered assets.");
        System.out.println("  /program info           Show active target program info.");
        System.out.println("  /program focus          Show target program focus areas.");
        System.out.println("  /program exclusions     Show target program exclusions and restrictions.");
        System.out.println("  /recon start [domain]   Execute automatic scan sequence on target.");
        System.out.println("  /recon status           Show project target and asset summary counts.");
        System.out.println("  /recon history          List finished scans list.");
        System.out.println("  /report create [title]  Create a report draft.");
        System.out.println("  /report add-finding [R]|[F] Add finding to report section.");
        System.out.println("  /report generate [R] [f] Build final report file (markdown, html, json).");
        System.out.println("  /report validate [id]   Validate findings structure against program rules.");
        System.out.println("  /assess [domain]        Perform methodology analysis and initialize playbooks.");
        System.out.println("  /assess update-step [d] | [pb] | [step] | [status] | [notes] Update playbook step.");
        System.out.println("  /run [plugin] [args]    Execute a pentest plugin (nmap, subfinder, httpx, katana, whois, dns).");
        System.out.println("  /journal                List project journal entries.");
        System.out.println("  /journal today          Show today's daily text journal entries.");
        System.out.println("  /journal project        Show project journal entries from database.");
        System.out.println("  /dashboard              Display count statistics and target playbook coverage.");
        System.out.println("  /decision               List all decisions logged for the active project.");
        System.out.println("  /decision finding [id]  List decisions specifically for a finding ID.");
        System.out.println("  /snapshot create [name] Create a database and knowledge snapshot.");
        System.out.println("  /snapshot restore [name]Restore database from snapshot.");
        System.out.println("  /snapshot list          List available snapshots.");
        System.out.println("  /council debate [id]    Run a multi-agent debate to validate a finding's severity.");
        System.out.println("  /quantum keygen [pfx]   Generate a pair of ML-KEM/ML-DSA PQC keys.");
        System.out.println("  /quantum seal [file]    Encrypt and sign a file using post-quantum cryptography.");
        System.out.println("  /quantum unseal [f] [p] [s] Decrypt and verify a sealed PQC file.");
        System.out.println("  /coder solve [desc]     Use AI model router to solve coding problems.");
        System.out.println("  /coder audit [file]     Audit code for quantum vulnerabilities and PQC readiness.");
        System.out.println("  /coder inspect [path]   Map a workspace and detect verification commands.");
        System.out.println("  /coder verify [path]    Run the detected allowlisted test command.");
        System.out.println("  /exit                   Close the console interface.");
    }

    private void printStatus() {
        System.out.println("System Status:");
        System.out.println("  - SQLite Database: database/javai.db (Connected)");
        System.out.println("  - Active Session: " + javAI.getMemoryEngine().getActiveConversationId());
        System.out.println("  - Active Project: " + javAI.getMemoryEngine().getActiveProjectName() + " (ID: " + javAI.getMemoryEngine().getActiveProjectId() + ")");
        System.out.println("  - Active Model: " + javAI.getModelRouter().getActiveModelName());
        System.out.println("  - Endpoint: " + javAI.getModelConfig().getEndpoint());
    }

    private void printHistory() throws Exception {
        List<Message> history = javAI.getMemoryEngine().getActiveConversationHistory();
        if (history.isEmpty()) {
            System.out.println("[System] History is empty.");
            return;
        }
        System.out.println("=== Conversation History ===");
        for (Message msg : history) {
            System.out.printf("[%s]: %s\n", msg.getRole().toUpperCase(), msg.getContent());
        }
        System.out.println("=============================");
    }

    private void handleNotesCommand(String arg) throws Exception {
        if (arg.isEmpty() || arg.equalsIgnoreCase("list")) {
            listNotes();
        } else if (arg.startsWith("add ")) {
            String noteData = arg.substring(4).trim();
            String[] noteParts = noteData.split("\\|", 2);
            if (noteParts.length < 2) {
                System.out.println("[System] Invalid format. Use: `/notes add Title | Content`");
                return;
            }
            saveNote(noteParts[0].trim(), noteParts[1].trim());
        } else {
            System.out.println("[System] Unknown notes argument. Use `/notes list` or `/notes add [Title] | [Content]`");
        }
    }

    private void listNotes() throws Exception {
        String sql = "SELECT title, content, created_at FROM notes ORDER BY created_at DESC";
        try (Connection conn = javAI.getDatabaseManager().getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {
            
            System.out.println("\n=== Saved Research Notes ===");
            int count = 0;
            while (rs.next()) {
                count++;
                System.out.printf("[%d] Title: %s\n    Content: %s\n    Created: %s\n\n",
                        count,
                        rs.getString("title"),
                        rs.getString("content"),
                        new java.util.Date(rs.getLong("created_at")).toString());
            }
            if (count == 0) {
                System.out.println("No notes found.");
            }
            System.out.println("============================");
        }
    }

    private void saveNote(String title, String content) throws Exception {
        String sql = "INSERT INTO notes (title, content, created_at) VALUES (?, ?, ?)";
        try (Connection conn = javAI.getDatabaseManager().getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, title);
            stmt.setString(2, content);
            stmt.setLong(3, System.currentTimeMillis());
            stmt.executeUpdate();
            System.out.println("[System] Note saved successfully.");
        }
    }

    private void listProjects() throws Exception {
        String sql = "SELECT id, name, description, created_at FROM projects ORDER BY created_at DESC";
        try (Connection conn = javAI.getDatabaseManager().getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {
            
            System.out.println("\n=== Projects List ===");
            int count = 0;
            while (rs.next()) {
                count++;
                int id = rs.getInt("id");
                String name = rs.getString("name");
                boolean isActive = (id == javAI.getMemoryEngine().getActiveProjectId());
                System.out.printf("[%d] ID: %d | Name: %s %s\n    Description: %s\n    Created: %s\n\n",
                        count,
                        id,
                        name,
                        isActive ? "(ACTIVE)" : "",
                        rs.getString("description"),
                        new java.util.Date(rs.getLong("created_at")).toString());
            }
            if (count == 0) {
                System.out.println("No projects found.");
            }
            System.out.println("=====================");
        }
    }

    private void listFindings() throws Exception {
        String sql = "SELECT id, title, severity, description, state, confidence, evidence_count, created_at FROM findings WHERE project_id = ? ORDER BY created_at DESC";
        try (Connection conn = javAI.getDatabaseManager().getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, javAI.getMemoryEngine().getActiveProjectId());
            try (ResultSet rs = stmt.executeQuery()) {
                System.out.println("\n=== Findings for Project '" + javAI.getMemoryEngine().getActiveProjectName() + "' ===");
                int count = 0;
                while (rs.next()) {
                    count++;
                    int id = rs.getInt("id");
                    String title = rs.getString("title");
                    String severity = rs.getString("severity");
                    String desc = rs.getString("description");
                    String state = rs.getString("state");
                    double confidence = rs.getDouble("confidence");
                    int evidenceCount = rs.getInt("evidence_count");
                    
                    int scoreVal = 50;
                    String reason = "No active program rules scored";
                    try {
                        com.javai.plugins.scoring.FindingScorer scorer = new com.javai.plugins.scoring.FindingScorer(javAI.getMemoryEngine());
                        com.javai.plugins.scoring.FindingScorer.ScoreResult scoreRes = scorer.scoreFinding(title, severity, desc);
                        scoreVal = scoreRes.getScore();
                        reason = scoreRes.getReason();
                    } catch (Exception ignored) {}

                    System.out.printf("[%d] ID: %d | Title: %s\n    Severity: %s | State: %s | Confidence: %.0f%% | Evidence Count: %d | Rules Score: %d/100\n    Assessment: %s\n    Description: %s\n    Created: %s\n\n",
                            count,
                            id,
                            title,
                            severity,
                            state,
                            confidence * 100,
                            evidenceCount,
                            scoreVal,
                            reason,
                            desc,
                            new java.util.Date(rs.getLong("created_at")).toString());
                }
                if (count == 0) {
                    System.out.println("No findings found for this project.");
                }
                System.out.println("======================");
            }
        }
    }

    private void listTasks() throws Exception {
        String sql = "SELECT id, title, status, created_at FROM tasks WHERE project_id = ? ORDER BY created_at DESC";
        try (Connection conn = javAI.getDatabaseManager().getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, javAI.getMemoryEngine().getActiveProjectId());
            try (ResultSet rs = stmt.executeQuery()) {
                System.out.println("\n=== Tasks for Project '" + javAI.getMemoryEngine().getActiveProjectName() + "' ===");
                int count = 0;
                while (rs.next()) {
                    count++;
                    System.out.printf("[%d] ID: %d | Title: %s\n    Status: %s\n    Created: %s\n\n",
                            count,
                            rs.getInt("id"),
                            rs.getString("title"),
                            rs.getString("status"),
                            new java.util.Date(rs.getLong("created_at")).toString());
                }
                if (count == 0) {
                    System.out.println("No tasks found for this project.");
                }
                System.out.println("======================");
            }
        }
    }

    private void listKnowledge() throws Exception {
        String sql = "SELECT id, key, value, category, created_at FROM knowledge ORDER BY created_at DESC";
        try (Connection conn = javAI.getDatabaseManager().getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {
            
            System.out.println("\n=== Knowledge Base ===");
            int count = 0;
            while (rs.next()) {
                count++;
                System.out.printf("[%d] Key: %s\n    Value: %s\n    Category: %s\n    Created: %s\n\n",
                        count,
                        rs.getString("key"),
                        rs.getString("value"),
                        rs.getString("category"),
                        new java.util.Date(rs.getLong("created_at")).toString());
            }
            if (count == 0) {
                System.out.println("Knowledge base is empty.");
            }
            System.out.println("=======================");
        }
    }

    private void handleReportCommand(String arg) throws Exception {
        String[] parts = arg.trim().split(" ", 2);
        String subcmd = parts[0].toLowerCase();
        String subarg = parts.length > 1 ? parts[1].trim() : "";

        if (subcmd.isEmpty() || subcmd.equals("list")) {
            listReports();
        } else if (subcmd.equals("create")) {
            String title = subarg.isEmpty() ? "Security Assessment Report" : subarg;
            int reportId = javAI.getMemoryEngine().createReport(title, "Markdown");
            System.out.println("[System] Report '" + title + "' created successfully with ID " + reportId + ".");
        } else if (subcmd.equals("add-finding")) {
            if (subarg.isEmpty() || !subarg.contains("|")) {
                System.out.println("[System] Invalid format. Use: `/report add-finding ReportID | FindingID`");
                return;
            }
            String[] split = subarg.split("\\|", 2);
            int reportId = Integer.parseInt(split[0].trim());
            int findingId = Integer.parseInt(split[1].trim());
            javAI.getMemoryEngine().addReportSection(reportId, findingId, 0);
            System.out.println("[System] Finding ID " + findingId + " added to Report ID " + reportId + ".");
        } else if (subcmd.equals("generate")) {
            int reportId = -1;
            String format = "markdown";
            if (!subarg.isEmpty()) {
                String[] genParts = subarg.split("\\s+");
                try {
                    reportId = Integer.parseInt(genParts[0].trim());
                    if (genParts.length > 1) {
                        format = genParts[1].trim().toLowerCase();
                    }
                } catch (NumberFormatException e) {
                    format = genParts[0].trim().toLowerCase();
                }
            }
            generateReport(reportId, format);
        } else if (subcmd.equals("validate")) {
            int reportId = -1;
            if (!subarg.isEmpty()) {
                try {
                    reportId = Integer.parseInt(subarg);
                } catch (NumberFormatException ignored) {}
            }
            com.javai.plugins.validation.ReportValidator validator = new com.javai.plugins.validation.ReportValidator(javAI.getDatabaseManager(), javAI.getMemoryEngine());
            List<com.javai.plugins.validation.ReportValidator.ValidationResult> results = validator.validateActiveFindings(reportId);
            if (results.isEmpty()) {
                System.out.println("[System] No findings recorded yet. Add findings using `/finding add` before validating.");
            } else {
                System.out.println("\n=== Report Submission Validation ===");
                for (com.javai.plugins.validation.ReportValidator.ValidationResult res : results) {
                    System.out.println(res.toString());
                }
                System.out.println("====================================");
            }
        } else {
            System.out.println("[System] Unknown report command. Use `/report create <title>`, `/report add-finding <rep_id> | <find_id>`, `/report generate [rep_id] [format]`, or `/report validate [rep_id]`.");
        }
    }

    private void generateReport(int reportId, String format) throws Exception {
        String projectName = javAI.getMemoryEngine().getActiveProjectName();
        int activeProjectId = javAI.getMemoryEngine().getActiveProjectId();

        // Validate that there are no High/Critical findings in the report without state VALIDATED and evidenceCount > 0
        com.javai.plugins.validation.ReportValidator validator = new com.javai.plugins.validation.ReportValidator(javAI.getDatabaseManager(), javAI.getMemoryEngine());
        List<com.javai.plugins.validation.ReportValidator.ValidationResult> validationResults = validator.validateActiveFindings(reportId);
        for (com.javai.plugins.validation.ReportValidator.ValidationResult res : validationResults) {
            String state = "HYPOTHESIS";
            int evidenceCount = 0;
            String severity = "Low";
            String findSql = "SELECT state, evidence_count, severity FROM findings WHERE id = ?";
            try (Connection conn = javAI.getDatabaseManager().getConnection();
                 PreparedStatement stmt = conn.prepareStatement(findSql)) {
                stmt.setInt(1, res.getFindingId());
                try (ResultSet rs = stmt.executeQuery()) {
                    if (rs.next()) {
                        state = rs.getString("state");
                        evidenceCount = rs.getInt("evidence_count");
                        severity = rs.getString("severity");
                    }
                }
            }
            boolean isHighOrCritical = severity.toLowerCase().contains("high") || severity.toLowerCase().contains("critical");
            if (isHighOrCritical && (!"VALIDATED".equals(state) || evidenceCount <= 0)) {
                System.out.println("[Error] Cannot generate report: Finding ID " + res.getFindingId() + " (" + res.getTitle() + ") is High/Critical but its status is not VALIDATED or lacks evidence.");
                return;
            }
        }

        List<String> targets = new java.util.ArrayList<>();
        String targetSql = "SELECT domain FROM targets WHERE project_id = ?";
        try (Connection conn = javAI.getDatabaseManager().getConnection();
             PreparedStatement stmt = conn.prepareStatement(targetSql)) {
            stmt.setInt(1, activeProjectId);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    targets.add(rs.getString("domain"));
                }
            }
        }

        List<String> assets = new java.util.ArrayList<>();
        String assetSql = "SELECT type, value, metadata FROM assets WHERE project_id = ?";
        try (Connection conn = javAI.getDatabaseManager().getConnection();
             PreparedStatement stmt = conn.prepareStatement(assetSql)) {
            stmt.setInt(1, activeProjectId);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    assets.add(String.format("[%s] %s - %s", rs.getString("type"), rs.getString("value"), rs.getString("metadata")));
                }
            }
        }

        List<String[]> findings = new java.util.ArrayList<>();
        String findingSql = (reportId != -1) 
            ? "SELECT f.id, f.title, f.severity, f.description FROM findings f " +
              "JOIN report_sections rs ON f.id = rs.finding_id WHERE rs.report_id = ?"
            : "SELECT id, title, severity, description FROM findings WHERE project_id = ?";
        try (Connection conn = javAI.getDatabaseManager().getConnection();
             PreparedStatement stmt = conn.prepareStatement(findingSql)) {
            stmt.setInt(1, (reportId != -1) ? reportId : activeProjectId);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    findings.add(new String[]{
                            String.valueOf(rs.getInt("id")),
                            rs.getString("title"),
                            rs.getString("severity"),
                            rs.getString("description")
                    });
                }
            }
        }

        String reportTitle = (reportId != -1) ? "Report ID: " + reportId : "Security Assessment Report for " + projectName;
        StringBuilder content = new StringBuilder();

        if (format.equals("html")) {
            content.append("<html><head><title>").append(reportTitle).append("</title></head><body>");
            content.append("<h1>").append(reportTitle).append("</h1>");
            content.append("<p>Generated at: ").append(new java.util.Date().toString()).append("</p>");
            content.append("<h2>Targets Scoped</h2><ul>");
            for (String t : targets) {
                content.append("<li>").append(t).append("</li>");
            }
            content.append("</ul>");
            content.append("<h2>Discovered Assets</h2><ul>");
            for (String a : assets) {
                content.append("<li>").append(a).append("</li>");
            }
            content.append("</ul>");
            content.append("<h2>Vulnerability Findings</h2>");
            for (String[] f : findings) {
                content.append("<h3>[").append(f[2]).append("] ").append(f[1]).append("</h3>");
                content.append("<p>ID: ").append(f[0]).append("</p>");
                content.append("<p>").append(f[3]).append("</p>");
            }
            content.append("</body></html>");
        } else if (format.equals("json")) {
            content.append("{\n");
            content.append("  \"title\": \"").append(reportTitle).append("\",\n");
            content.append("  \"project\": \"").append(projectName).append("\",\n");
            content.append("  \"timestamp\": \"").append(System.currentTimeMillis()).append("\",\n");
            content.append("  \"targets\": ").append(new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(targets)).append(",\n");
            content.append("  \"assets_count\": ").append(assets.size()).append(",\n");
            content.append("  \"findings\": [\n");
            for (int i = 0; i < findings.size(); i++) {
                String[] f = findings.get(i);
                content.append("    {\n");
                content.append("      \"id\": ").append(f[0]).append(",\n");
                content.append("      \"title\": \"").append(f[1]).append("\",\n");
                content.append("      \"severity\": \"").append(f[2]).append("\",\n");
                content.append("      \"description\": \"").append(f[3]).append("\"\n");
                content.append("    }").append(i < findings.size() - 1 ? "," : "").append("\n");
            }
            content.append("  ]\n}");
        } else {
            content.append("# ").append(reportTitle).append("\n\n");
            content.append("Generated at: ").append(new java.util.Date().toString()).append("\n\n");
            content.append("## Targets Scoped\n");
            for (String t : targets) {
                content.append("- ").append(t).append("\n");
            }
            content.append("\n## Discovered Assets (Total: ").append(assets.size()).append(")\n");
            for (String a : assets) {
                content.append("- ").append(a).append("\n");
            }
            content.append("\n## Vulnerability Findings (Total: ").append(findings.size()).append(")\n");
            for (String[] f : findings) {
                content.append("### [").append(f[2]).append("] ").append(f[1]).append("\n");
                content.append("- **Finding ID:** ").append(f[0]).append("\n");
                content.append("- **Description:** ").append(f[3]).append("\n\n");
            }
        }

        String filename = "report_" + System.currentTimeMillis() + "." + (format.equals("html") ? "html" : format.equals("json") ? "json" : "md");
        java.io.File reportFile = new java.io.File("workspace/reports/" + filename);
        try (java.io.FileWriter fw = new java.io.FileWriter(reportFile)) {
            fw.write(content.toString());
        }

        // Record report generation decision
        com.javai.security.skeptic.DecisionEngine decisionEngine = new com.javai.security.skeptic.DecisionEngine(javAI.getDatabaseManager());
        decisionEngine.recordDecision(activeProjectId, (reportId != -1 ? reportId : null), "Report Generated",
                "Report '" + filename + "' was compiled and generated in " + format.toUpperCase() + " format with " + findings.size() + " findings.");

        System.out.println("[System] Generated " + format.toUpperCase() + " report successfully.");
        System.out.println("[System] Saved to: " + reportFile.getAbsolutePath());
    }

    private void listReports() throws Exception {
        String sql = "SELECT id, title, format, created_at FROM reports WHERE project_id = ? ORDER BY created_at DESC";
        try (Connection conn = javAI.getDatabaseManager().getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, javAI.getMemoryEngine().getActiveProjectId());
            try (ResultSet rs = stmt.executeQuery()) {
                System.out.println("\n=== Project Reports ===");
                int count = 0;
                while (rs.next()) {
                    count++;
                    System.out.printf("[%d] ID: %d | Title: %s\n    Format: %s | Created: %s\n\n",
                            count,
                            rs.getInt("id"),
                            rs.getString("title"),
                            rs.getString("format"),
                            new java.util.Date(rs.getLong("created_at")).toString());
                }
                if (count == 0) {
                    System.out.println("No reports generated for active project.");
                }
                System.out.println("=======================");
            }
        }
    }

    private void handleProgramCommand(String arg) throws Exception {
        String progName = javAI.getMemoryEngine().getActiveProgramName();
        if (progName == null) {
            System.out.println("[System] No active program linked to this project. Link one using `/target program <name>`.");
            return;
        }

        String subcmd = arg.trim().toLowerCase();
        if (subcmd.isEmpty() || subcmd.equals("info")) {
            System.out.println("\n=== Program Information ===");
            System.out.println("Name: " + progName);
            System.out.println("Type: " + javAI.getMemoryEngine().getProgramType(progName));
            int maxBounty = javAI.getMemoryEngine().getProgramMaxBounty(progName);
            System.out.println("Max Bounty: " + (maxBounty > 0 ? maxBounty + " USD/Points" : "VDP (Points/Recognition Only)"));
            System.out.println("===========================");
        } else if (subcmd.equals("focus")) {
            System.out.println("\n=== High Value Focus Areas ===");
            List<String> focus = javAI.getMemoryEngine().getProgramRules(progName, "focus");
            for (String f : focus) {
                System.out.println("  - " + f);
            }
            System.out.println("==============================");
        } else if (subcmd.equals("exclusions")) {
            System.out.println("\n=== Program Exclusions & Restrictions ===");
            System.out.println("Low Value / Accepted as Informational:");
            for (String low : javAI.getMemoryEngine().getProgramRules(progName, "low_value")) {
                System.out.println("  - " + low);
            }
            System.out.println("\nForbidden Actions (STRICTLY OUT OF SCOPE):");
            for (String forb : javAI.getMemoryEngine().getProgramRules(progName, "forbidden")) {
                System.out.println("  - " + forb);
            }
            System.out.println("\nStated Rules / Exclusions:");
            for (String excl : javAI.getMemoryEngine().getProgramExclusions(progName)) {
                System.out.println("  - " + excl);
            }
            System.out.println("=========================================");
        } else {
            System.out.println("[System] Unknown program subcommand. Use `/program info`, `/program focus`, or `/program exclusions`.");
        }
    }

    private void handleAssessCommand(String arg) throws Exception {
        String input = arg.trim();
        if (input.isEmpty()) {
            System.out.println("[System] Invalid format. Use: `/assess <domain>` or `/assess update-step <domain> | <playbook> | <step_number> | <status> | <notes>`");
            return;
        }

        if (input.startsWith("update-step ")) {
            String updateData = input.substring(12).trim();
            String[] split = updateData.split("\\|", 5);
            if (split.length < 4) {
                System.out.println("[System] Invalid format. Use: `/assess update-step <domain> | <playbook> | <step_number> | <status> | <notes>`");
                return;
            }
            String domain = split[0].trim();
            String playbook = split[1].trim();
            int stepNum = Integer.parseInt(split[2].trim());
            String status = split[3].trim();
            String notes = split.length > 4 ? split[4].trim() : "";

            int targetId = javAI.getMemoryEngine().getTargetId(domain);
            if (targetId == -1) {
                System.out.println("[System] Target domain '" + domain + "' not found in current project.");
                return;
            }

            javAI.getMethodologyEngine().getTestPlanner().updatePlaybookStep(targetId, playbook, stepNum, status, notes, javAI.getMemoryEngine());
            System.out.println("[System] Target playbook step updated successfully.");
            return;
        }

        // Standard target assessment
        String domain = input;
        System.out.println("[System] Performing target type methodology analysis for '" + domain + "'...");
        String assessment = javAI.getMethodologyEngine().assessTarget(domain);
        System.out.println("\n=== Pentest Methodology Assessment ===");
        System.out.println(assessment);
        System.out.println("======================================");

        // Display current target playbook status
        int targetId = javAI.getMemoryEngine().getTargetId(domain);
        List<com.javai.security.TestPlanner.PlaybookStatus> playbooks = javAI.getMethodologyEngine().getTestPlanner().getTargetPlaybookCoverage(targetId, javAI.getMemoryEngine());
        System.out.println("\n=== Target Playbook Coverage ===");
        for (com.javai.security.TestPlanner.PlaybookStatus pb : playbooks) {
            System.out.println("Playbook: " + pb.getPlaybookName() + " (Status: " + pb.getStatus() + ")");
            for (com.javai.security.TestPlanner.PlaybookStepStatus step : pb.getSteps()) {
                System.out.printf("  [%d] %s - %s%s\n",
                        step.getStepNumber(),
                        step.getStepName(),
                        step.getStatus(),
                        step.getNotes().isEmpty() ? "" : " (Notes: " + step.getNotes() + ")"
                );
            }
            System.out.println();
        }
        System.out.println("=================================");
    }

    private void handleLearnCommand(String arg) {
        String filepath = arg.trim();
        if (filepath.isEmpty()) {
            System.out.println("[System] Invalid format. Use: `/learn <filepath>` (e.g. `/learn playbooks/API.json`)");
            return;
        }
        try {
            java.io.File file = new java.io.File(filepath);
            com.javai.learning.LearningEngine learningEngine = new com.javai.learning.LearningEngine(javAI.getDatabaseManager());
            System.out.println("[System] Learning from: " + file.getAbsolutePath() + "...");
            String result = learningEngine.learnFromFile(file);
            System.out.println("[Learning Result] " + result);
        } catch (Exception e) {
            System.out.println("[Error] Learning failed: " + e.getMessage());
        }
    }

    private void handleGraphCommand(String arg) {
        String input = arg.trim();
        if (input.isEmpty() || !input.startsWith("trace ")) {
            System.out.println("[System] Invalid format. Use: `/graph trace <finding_id>`");
            return;
        }
        try {
            int findingId = Integer.parseInt(input.substring(6).trim());
            com.javai.security.graph.EvidenceGraph graph = new com.javai.security.graph.EvidenceGraph(javAI.getDatabaseManager());
            System.out.println("[System] Tracing evidence lineage for finding ID " + findingId + "...");
            List<String> trace = graph.traceHypothesisLineage(findingId);
            System.out.println("\n=== Evidence Lineage Trace ===");
            for (String step : trace) {
                System.out.println(step);
            }
            System.out.println("==============================");
        } catch (Exception e) {
            System.out.println("[Error] Graph trace failed: " + e.getMessage());
        }
    }

    private void handleObservationCommand(String arg) throws Exception {
        String[] parts = arg.trim().split(" ", 2);
        String subcmd = parts[0].toLowerCase();
        String subarg = parts.length > 1 ? parts[1].trim() : "";

        if (subcmd.isEmpty() || subcmd.equals("list")) {
            listObservations();
        } else if (subcmd.equals("add")) {
            if (subarg.isEmpty() || !subarg.contains("|")) {
                System.out.println("[System] Invalid format. Use: `/observation add Description | Source`");
                return;
            }
            String[] addParts = subarg.split("\\|", 2);
            String description = addParts[0].trim();
            String source = addParts[1].trim();
            
            // Retrieve active target ID
            int targetId = -1;
            String targetSql = "SELECT id FROM targets WHERE project_id = ? ORDER BY created_at DESC LIMIT 1";
            try (Connection conn = javAI.getDatabaseManager().getConnection();
                 PreparedStatement stmt = conn.prepareStatement(targetSql)) {
                stmt.setInt(1, javAI.getMemoryEngine().getActiveProjectId());
                try (ResultSet rs = stmt.executeQuery()) {
                    if (rs.next()) {
                        targetId = rs.getInt("id");
                    }
                }
            }
            if (targetId == -1) {
                System.out.println("[System] No targets defined for active project. Add target scope first via `/target add <domain>`.");
                return;
            }

            com.javai.security.skeptic.ObservationEngine engine = new com.javai.security.skeptic.ObservationEngine(javAI.getDatabaseManager());
            int obsId = engine.recordObservation(javAI.getMemoryEngine().getActiveProjectId(), targetId, description, source);
            System.out.println("[System] Observation ID " + obsId + " recorded successfully in observation registry.");
        } else {
            System.out.println("[System] Unknown observation command. Use `/observation add [D] | [S]` or `/observation list`.");
        }
    }

    private void listObservations() throws Exception {
        int activeProjectId = javAI.getMemoryEngine().getActiveProjectId();
        String sql = "SELECT o.id, t.domain, o.description, o.source, o.confidence, o.created_at " +
                     "FROM observations o JOIN targets t ON o.target_id = t.id " +
                     "WHERE o.project_id = ? ORDER BY o.created_at DESC";
        try (Connection conn = javAI.getDatabaseManager().getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, activeProjectId);
            try (ResultSet rs = stmt.executeQuery()) {
                System.out.println("\n=== Mapped Target Observations ===");
                int count = 0;
                while (rs.next()) {
                    count++;
                    System.out.printf("[%d] ID: %d | Target: %s | Source: %s\n    Observation: %s\n    Confidence: %.0f%%\n    Registered: %s\n\n",
                            count,
                            rs.getInt("id"),
                            rs.getString("domain"),
                            rs.getString("source"),
                            rs.getString("description"),
                            rs.getDouble("confidence") * 100,
                            new java.util.Date(rs.getLong("created_at")).toString());
                }
                if (count == 0) {
                    System.out.println("No observations registered for the active project.");
                }
                System.out.println("==================================");
            }
        }
    }

    private void showCoverage() throws Exception {
        int activeProjectId = javAI.getMemoryEngine().getActiveProjectId();
        
        List<Integer> targets = new java.util.ArrayList<>();
        String targetSql = "SELECT id FROM targets WHERE project_id = ?";
        try (Connection conn = javAI.getDatabaseManager().getConnection();
             PreparedStatement stmt = conn.prepareStatement(targetSql)) {
            stmt.setInt(1, activeProjectId);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    targets.add(rs.getInt("id"));
                }
            }
        }
        
        com.javai.security.TestPlanner planner = javAI.getMethodologyEngine().getTestPlanner();
        for (int targetId : targets) {
            String pbSql = "SELECT playbook_name FROM target_playbooks WHERE target_id = ?";
            try (Connection conn = javAI.getDatabaseManager().getConnection();
                 PreparedStatement stmt = conn.prepareStatement(pbSql)) {
                stmt.setInt(1, targetId);
                try (ResultSet rs = stmt.executeQuery()) {
                    while (rs.next()) {
                        planner.syncCoverage(targetId, rs.getString("playbook_name"), javAI.getMemoryEngine());
                    }
                }
            }
        }
        
        String covSql = "SELECT c.id, t.domain, c.playbook_name, c.completed_steps, c.total_steps, c.coverage_percent " +
                        "FROM coverage c JOIN targets t ON c.target_id = t.id " +
                        "WHERE c.project_id = ?";
                        
        try (Connection conn = javAI.getDatabaseManager().getConnection();
             PreparedStatement stmt = conn.prepareStatement(covSql)) {
            stmt.setInt(1, activeProjectId);
            try (ResultSet rs = stmt.executeQuery()) {
                System.out.println("\n=== Playbook Coverage Intelligence ===");
                boolean found = false;
                while (rs.next()) {
                    found = true;
                    int coverageId = rs.getInt("id");
                    String domain = rs.getString("domain");
                    String pbName = rs.getString("playbook_name");
                    int completed = rs.getInt("completed_steps");
                    int total = rs.getInt("total_steps");
                    double percent = rs.getDouble("coverage_percent");
                    
                    System.out.printf("\nTarget: %s | Playbook: %s Playbook\n", domain, pbName);
                    System.out.printf("Completed: %d/%d\n", completed, total);
                    System.out.printf("Coverage: %.0f%%\n", percent);
                    System.out.println("Untested:");
                    
                    String stepsSql = "SELECT step_name FROM coverage_steps WHERE coverage_id = ? AND status != 'Completed' ORDER BY step_number ASC";
                    try (PreparedStatement sStmt = conn.prepareStatement(stepsSql)) {
                        sStmt.setInt(1, coverageId);
                        try (ResultSet sRs = sStmt.executeQuery()) {
                            int untestedCount = 0;
                            while (sRs.next()) {
                                untestedCount++;
                                System.out.println("  - " + sRs.getString("step_name"));
                            }
                            if (untestedCount == 0) {
                                System.out.println("  - None (100% complete)");
                            }
                        }
                    }
                }
                if (!found) {
                    System.out.println("No playbook coverage records generated. Run `/assess <domain>` to initialize playbooks.");
                }
                System.out.println("======================================");
            }
        }
    }

    private void handleJournalCommand(String arg) throws Exception {
        String subcmd = arg.trim().toLowerCase();
        if (subcmd.equals("today")) {
            java.io.File dir = new java.io.File("workspace/journal");
            java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("yyyy-MM-dd");
            String filename = "journal_" + sdf.format(new java.util.Date()) + ".txt";
            java.io.File journalFile = new java.io.File(dir, filename);
            System.out.println("\n=== Daily Journal Log (" + sdf.format(new java.util.Date()) + ") ===");
            if (!journalFile.exists()) {
                System.out.println("No journal entries logged today.");
            } else {
                try (java.io.BufferedReader br = new java.io.BufferedReader(new java.io.FileReader(journalFile))) {
                    String line;
                    while ((line = br.readLine()) != null) {
                        System.out.println(line);
                    }
                }
            }
            System.out.println("============================================");
        } else if (subcmd.isEmpty() || subcmd.equals("project")) {
            int activeProjectId = javAI.getMemoryEngine().getActiveProjectId();
            String activeProjectName = javAI.getMemoryEngine().getActiveProjectName();
            String sql = "SELECT id, action_type, description, created_at FROM journal WHERE project_id = ? ORDER BY created_at ASC";
            try (Connection conn = javAI.getDatabaseManager().getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setInt(1, activeProjectId);
                try (ResultSet rs = stmt.executeQuery()) {
                    System.out.println("\n=== Project Journal: " + activeProjectName + " ===");
                    int count = 0;
                    while (rs.next()) {
                        count++;
                        long time = rs.getLong("created_at");
                        System.out.printf("[%s] [%s] %s\n",
                                new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new java.util.Date(time)),
                                rs.getString("action_type"),
                                rs.getString("description"));
                    }
                    if (count == 0) {
                        System.out.println("No journal entries for this project.");
                    }
                    System.out.println("============================================");
                }
            }
        } else {
            System.out.println("[System] Unknown journal subcommand. Use `/journal`, `/journal today`, or `/journal project`.");
        }
    }

    private void handleDashboardCommand() throws Exception {
        int activeProjectId = javAI.getMemoryEngine().getActiveProjectId();
        String activeProjectName = javAI.getMemoryEngine().getActiveProjectName();

        int observationsCount = 0;
        int evidenceCount = 0;
        int hypothesesCount = 0;
        int validatedCount = 0;
        int reportedCount = 0;
        int activeReportCount = 0;

        try (Connection conn = javAI.getDatabaseManager().getConnection()) {
            // 1. Observations
            String sqlObs = "SELECT COUNT(*) FROM observations WHERE project_id = ?";
            try (PreparedStatement stmt = conn.prepareStatement(sqlObs)) {
                stmt.setInt(1, activeProjectId);
                try (ResultSet rs = stmt.executeQuery()) {
                    if (rs.next()) observationsCount = rs.getInt(1);
                }
            }

            // 2. Evidence
            String sqlEv = "SELECT COUNT(e.id) FROM evidence e JOIN findings f ON e.finding_id = f.id WHERE f.project_id = ?";
            try (PreparedStatement stmt = conn.prepareStatement(sqlEv)) {
                stmt.setInt(1, activeProjectId);
                try (ResultSet rs = stmt.executeQuery()) {
                    if (rs.next()) evidenceCount = rs.getInt(1);
                }
            }

            // 3. Hypotheses
            String sqlHyp = "SELECT COUNT(*) FROM findings WHERE project_id = ? AND state = 'HYPOTHESIS'";
            try (PreparedStatement stmt = conn.prepareStatement(sqlHyp)) {
                stmt.setInt(1, activeProjectId);
                try (ResultSet rs = stmt.executeQuery()) {
                    if (rs.next()) hypothesesCount = rs.getInt(1);
                }
            }

            // 4. Validated
            String sqlVal = "SELECT COUNT(*) FROM findings WHERE project_id = ? AND state = 'VALIDATED'";
            try (PreparedStatement stmt = conn.prepareStatement(sqlVal)) {
                stmt.setInt(1, activeProjectId);
                try (ResultSet rs = stmt.executeQuery()) {
                    if (rs.next()) validatedCount = rs.getInt(1);
                }
            }

            // 5. Reported
            String sqlRep = "SELECT COUNT(*) FROM findings WHERE project_id = ? AND state = 'REPORTED'";
            try (PreparedStatement stmt = conn.prepareStatement(sqlRep)) {
                stmt.setInt(1, activeProjectId);
                try (ResultSet rs = stmt.executeQuery()) {
                    if (rs.next()) reportedCount = rs.getInt(1);
                }
            }

            // 6. Reports
            String sqlRepCount = "SELECT COUNT(*) FROM reports WHERE project_id = ?";
            try (PreparedStatement stmt = conn.prepareStatement(sqlRepCount)) {
                stmt.setInt(1, activeProjectId);
                try (ResultSet rs = stmt.executeQuery()) {
                    if (rs.next()) activeReportCount = rs.getInt(1);
                }
            }
        }

        // Sync coverage first
        List<Integer> targetIds = new java.util.ArrayList<>();
        String targetSql = "SELECT id FROM targets WHERE project_id = ?";
        try (Connection conn = javAI.getDatabaseManager().getConnection();
             PreparedStatement stmt = conn.prepareStatement(targetSql)) {
            stmt.setInt(1, activeProjectId);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    targetIds.add(rs.getInt("id"));
                }
            }
        }
        
        com.javai.security.TestPlanner planner = javAI.getMethodologyEngine().getTestPlanner();
        for (int targetId : targetIds) {
            String pbSql = "SELECT playbook_name FROM target_playbooks WHERE target_id = ?";
            try (Connection conn = javAI.getDatabaseManager().getConnection();
                 PreparedStatement stmt = conn.prepareStatement(pbSql)) {
                stmt.setInt(1, targetId);
                try (ResultSet rs = stmt.executeQuery()) {
                    while (rs.next()) {
                        planner.syncCoverage(targetId, rs.getString("playbook_name"), javAI.getMemoryEngine());
                    }
                }
            }
        }

        System.out.println("\n\u001B[35m╔══════════════════════════════════════════════════════════════╗");
        System.out.println("║                      PROJECT DASHBOARD                       ║");
        System.out.printf("║  Active Project: %-42s  ║\n", activeProjectName);
        System.out.println("╚══════════════════════════════════════════════════════════════╝\u001B[0m");
        System.out.printf("  \u001B[36m• Observations:\u001B[0m       %-3d       \u001B[32m• Validated Findings:\u001B[0m  %-3d\n", observationsCount, validatedCount);
        System.out.printf("  \u001B[34m• Evidence Collected:\u001B[0m %-3d       \u001B[31m• Reported Findings:\u001B[0m   %-3d\n", evidenceCount, reportedCount);
        System.out.printf("  \u001B[33m• Active Hypotheses:\u001B[0m  %-3d       \u001B[35m• Generated Reports:\u001B[0m   %-3d\n", hypothesesCount, activeReportCount);
        System.out.println("\u001B[35m  (Active Hypotheses are capped at 3 per target area)\u001B[0m");
        System.out.println("\u001B[34m  ────────────────────────────────────────────────────────────\u001B[0m");
        System.out.println("  \u001B[1mPlaybook Coverage by Target:\u001B[0m");
        
        String covSql = "SELECT t.domain, c.playbook_name, c.completed_steps, c.total_steps, c.coverage_percent " +
                        "FROM coverage c JOIN targets t ON c.target_id = t.id " +
                        "WHERE c.project_id = ? ORDER BY t.domain ASC, c.playbook_name ASC";
                        
        try (Connection conn = javAI.getDatabaseManager().getConnection();
             PreparedStatement stmt = conn.prepareStatement(covSql)) {
            stmt.setInt(1, activeProjectId);
            try (ResultSet rs = stmt.executeQuery()) {
                int covCount = 0;
                while (rs.next()) {
                    covCount++;
                    double pct = rs.getDouble("coverage_percent");
                    int completed = rs.getInt("completed_steps");
                    int total = rs.getInt("total_steps");
                    String domain = rs.getString("domain");
                    String playbook = rs.getString("playbook_name");
                    
                    int filled = (int) Math.round(pct / 10.0);
                    StringBuilder sb = new StringBuilder();
                    for (int i = 0; i < 10; i++) {
                        if (i < filled) sb.append("█");
                        else sb.append("░");
                    }
                    System.out.printf("    \u001B[36m•\u001B[0m %s (%s Playbook):\n", domain, playbook);
                    System.out.printf("      \u001B[32m%s\u001B[0m \u001B[1m%.0f%%\u001B[0m (%d/%d steps completed)\n", sb.toString(), pct, completed, total);
                }
                if (covCount == 0) {
                    System.out.println("    No playbook coverage records generated yet.");
                }
            }
        }
        System.out.println("\u001B[34m  ────────────────────────────────────────────────────────────\u001B[0m");
        System.out.println("  \u001B[32m[WebServer] Access visual Web Dashboard at http://localhost:1337\u001B[0m");
        System.out.println("\u001B[35m╚══════════════════════════════════════════════════════════════╝\u001B[0m");
    }

    private void handleDecisionCommand(String arg) throws Exception {
        String input = arg.trim();
        int activeProjectId = javAI.getMemoryEngine().getActiveProjectId();
        String activeProjectName = javAI.getMemoryEngine().getActiveProjectName();

        if (input.startsWith("finding ")) {
            try {
                int findingId = Integer.parseInt(input.substring(8).trim());
                String sql = "SELECT id, decision_type, rationale, created_at FROM decisions WHERE project_id = ? AND finding_id = ? ORDER BY created_at ASC";
                try (Connection conn = javAI.getDatabaseManager().getConnection();
                     PreparedStatement stmt = conn.prepareStatement(sql)) {
                    stmt.setInt(1, activeProjectId);
                    stmt.setInt(2, findingId);
                    try (ResultSet rs = stmt.executeQuery()) {
                        System.out.println("\n=== Decision Ledger: Finding ID " + findingId + " ===");
                        int count = 0;
                        while (rs.next()) {
                            count++;
                            long time = rs.getLong("created_at");
                            System.out.printf("[%s] [%s] %s\n",
                                    new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new java.util.Date(time)),
                                    rs.getString("decision_type"),
                                    rs.getString("rationale"));
                        }
                        if (count == 0) {
                            System.out.println("No decisions recorded for this finding.");
                        }
                        System.out.println("=============================================");
                    }
                }
            } catch (NumberFormatException e) {
                System.out.println("[System] Invalid format. Use: `/decision finding <id>`");
            }
        } else if (input.isEmpty()) {
            String sql = "SELECT id, finding_id, decision_type, rationale, created_at FROM decisions WHERE project_id = ? ORDER BY created_at ASC";
            try (Connection conn = javAI.getDatabaseManager().getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setInt(1, activeProjectId);
                try (ResultSet rs = stmt.executeQuery()) {
                    System.out.println("\n=== Project Decision Ledger: " + activeProjectName + " ===");
                    int count = 0;
                    while (rs.next()) {
                        count++;
                        long time = rs.getLong("created_at");
                        int fId = rs.getInt("finding_id");
                        String fIdStr = rs.wasNull() ? "N/A" : String.valueOf(fId);
                        System.out.printf("[%s] [Finding: %s] [%s] %s\n",
                                new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new java.util.Date(time)),
                                fIdStr,
                                rs.getString("decision_type"),
                                rs.getString("rationale"));
                    }
                    if (count == 0) {
                        System.out.println("No decisions recorded for this project.");
                    }
                    System.out.println("=============================================");
                }
            }
        } else {
            System.out.println("[System] Unknown decision subcommand. Use `/decision` or `/decision finding <id>`.");
        }
    }

    private void handleSnapshotCommand(String arg) {
        String[] parts = arg.trim().split(" ", 2);
        String subcmd = parts[0].toLowerCase();
        String subarg = parts.length > 1 ? parts[1].trim() : "";

        java.io.File snapshotDir = new java.io.File("workspace/snapshots");
        if (!snapshotDir.exists()) {
            snapshotDir.mkdirs();
        }

        try {
            if (subcmd.equals("create")) {
                String dbFilePath = javAI.getDatabaseManager().getDbFile();
                java.io.File dbFile = new java.io.File(dbFilePath);
                if (!dbFile.exists()) {
                    System.out.println("[System] Database file does not exist to copy.");
                    return;
                }

                String name = subarg.isEmpty() ? String.valueOf(System.currentTimeMillis()) : subarg;
                name = name.replaceAll("[^a-zA-Z0-9_-]", "");
                java.io.File snapshotFile = new java.io.File(snapshotDir, "snapshot_" + name + ".db");

                System.out.println("[System] Creating snapshot: " + snapshotFile.getName() + "...");
                java.nio.file.Files.copy(dbFile.toPath(), snapshotFile.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                System.out.println("[System] Snapshot created successfully.");
                
                int activeProjectId = javAI.getMemoryEngine().getActiveProjectId();
                com.javai.security.skeptic.DecisionEngine decisionEngine = new com.javai.security.skeptic.DecisionEngine(javAI.getDatabaseManager());
                decisionEngine.recordDecision(activeProjectId, null, "Snapshot Created", 
                        "Database state snapshot '" + snapshotFile.getName() + "' was successfully created.");
                javAI.getMemoryEngine().addJournalEntry("Snapshot Created", "Snapshot stored in " + snapshotFile.getName());

            } else if (subcmd.equals("restore")) {
                if (subarg.isEmpty()) {
                    System.out.println("[System] Please specify snapshot name to restore (e.g. `/snapshot restore 1719000000`).");
                    return;
                }
                String name = subarg.replaceAll("[^a-zA-Z0-9_-]", "");
                java.io.File snapshotFile = new java.io.File(snapshotDir, "snapshot_" + name + ".db");
                if (!snapshotFile.exists()) {
                    if (subarg.endsWith(".db")) {
                        snapshotFile = new java.io.File(snapshotDir, subarg);
                    }
                }
                if (!snapshotFile.exists()) {
                    System.out.println("[System] Snapshot '" + subarg + "' not found. Run `/snapshot list` to see available snapshots.");
                    return;
                }

                String dbFilePath = javAI.getDatabaseManager().getDbFile();
                java.io.File dbFile = new java.io.File(dbFilePath);

                System.out.println("[System] Restoring database from snapshot: " + snapshotFile.getName() + "...");
                javAI.getDatabaseManager().close();
                java.nio.file.Files.copy(snapshotFile.toPath(), dbFile.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                javAI.getDatabaseManager().getConnection();
                System.out.println("[System] Snapshot restored successfully. Re-established database connection.");

            } else if (subcmd.isEmpty() || subcmd.equals("list")) {
                java.io.File[] files = snapshotDir.listFiles((dir, name1) -> name1.startsWith("snapshot_") && name1.endsWith(".db"));
                System.out.println("\n=== Saved Workspace Snapshots ===");
                if (files == null || files.length == 0) {
                    System.out.println("No snapshots created yet. Use `/snapshot create [name]` to save state.");
                } else {
                    for (int i = 0; i < files.length; i++) {
                        java.io.File f = files[i];
                        String dispName = f.getName().substring(9, f.getName().length() - 3);
                        System.out.printf("[%d] Name: %s\n    File: %s\n    Size: %.2f KB\n    Saved: %s\n\n",
                                i + 1,
                                dispName,
                                f.getName(),
                                (double) f.length() / 1024,
                                new java.util.Date(f.lastModified()).toString());
                    }
                }
                System.out.println("==================================");
            } else {
                System.out.println("[System] Unknown snapshot subcommand. Use `/snapshot create [name]`, `/snapshot restore <name>`, or `/snapshot list`.");
            }
        } catch (Exception e) {
            System.out.println("[Error] Snapshot action failed: " + e.getMessage());
        }
    }

    private void handleCouncilCommand(String arg) {
        String[] parts = arg.trim().split(" ", 2);
        String subcmd = parts[0].toLowerCase();
        String subarg = parts.length > 1 ? parts[1].trim() : "";

        if (subcmd.equals("debate")) {
            if (subarg.isEmpty()) {
                System.out.println("[System] Please specify a finding ID to debate. E.g. `/council debate <finding_id>`");
                return;
            }
            try {
                int findingId = Integer.parseInt(subarg);
                com.javai.security.skeptic.CouncilEngine council = new com.javai.security.skeptic.CouncilEngine(
                        javAI.getDatabaseManager(),
                        javAI.getModelRouter(),
                        javAI.getMemoryEngine()
                );
                council.holdDebate(findingId);
            } catch (NumberFormatException e) {
                System.out.println("[System] Invalid finding ID format: " + subarg);
            } catch (Exception e) {
                System.out.println("[Error] Council debate failed: " + e.getMessage());
            }
        } else {
            System.out.println("[System] Unknown council subcommand. Use `/council debate <finding_id>`");
        }
    }

    private void handleQuantumCommand(String arg) {
        String[] parts = arg.trim().split(" ", 2);
        String subcmd = parts[0].toLowerCase();
        String subarg = parts.length > 1 ? parts[1].trim() : "";

        com.javai.security.pqc.QuantumBlueEngine pqc = new com.javai.security.pqc.QuantumBlueEngine();

        try {
            if (subcmd.equals("keygen")) {
                String prefix = subarg.isEmpty() ? "pqc_user" : subarg;
                pqc.generateKeyPair(prefix);
            } else if (subcmd.equals("seal")) {
                if (subarg.isEmpty()) {
                    System.out.println("[System] Usage: `/quantum seal <file_path>`");
                    return;
                }
                String idSk = "workspace/keys/pqc_user_id.sk";
                String pqcPk = "workspace/keys/pqc_user_pqc.pk";
                if (!new java.io.File(idSk).exists() || !new java.io.File(pqcPk).exists()) {
                    System.out.println("[System] Keys not found. Generating default key pair first...");
                    pqc.generateKeyPair("pqc_user");
                }
                pqc.sealFile(subarg, idSk, pqcPk);
            } else if (subcmd.equals("unseal")) {
                if (subarg.isEmpty()) {
                    System.out.println("[System] Usage: `/quantum unseal <file_path.pqc>` or `/quantum unseal <file_path.pqc> <id.pk> <pqc.sk>`");
                    return;
                }
                String[] unsealParts = subarg.split("\\s+");
                String pqcFile = unsealParts[0];
                String idPk = unsealParts.length > 1 ? unsealParts[1] : "workspace/keys/pqc_user_id.pk";
                String pqcSk = unsealParts.length > 2 ? unsealParts[2] : "workspace/keys/pqc_user_pqc.sk";

                if (!new java.io.File(idPk).exists() || !new java.io.File(pqcSk).exists()) {
                    System.out.println("[Error] Post-quantum public/private keys not found. Run `/quantum keygen` first.");
                    return;
                }
                pqc.unsealFile(pqcFile, idPk, pqcSk);
            } else {
                System.out.println("[System] Unknown quantum subcommand. Use: `/quantum keygen`, `/quantum seal <file>`, or `/quantum unseal <file>`");
            }
        } catch (Exception e) {
            System.out.println("[Error] Quantum operation failed: " + e.getMessage());
        }
    }

    private void handleCoderCommand(String arg) {
        String[] parts = arg.trim().split(" ", 2);
        String subcmd = parts[0].toLowerCase();
        String subarg = parts.length > 1 ? parts[1].trim() : "";

        com.javai.security.coder.CoderEngine coder = new com.javai.security.coder.CoderEngine(
                javAI.getDatabaseManager(),
                javAI.getModelRouter(),
                javAI.getMemoryEngine()
        );

        try {
            if (subcmd.equals("solve")) {
                if (subarg.isEmpty()) {
                    System.out.println("[System] Usage: `/coder solve <description of problem>`");
                    return;
                }
                coder.solveProblem(subarg);
            } else if (subcmd.equals("audit")) {
                if (subarg.isEmpty()) {
                    System.out.println("[System] Usage: `/coder audit <file_path>`");
                    return;
                }
                coder.auditPqcReadiness(subarg);
            } else if (subcmd.equals("inspect")) {
                coder.inspectWorkspace(subarg);
            } else if (subcmd.equals("verify")) {
                coder.verifyWorkspace(subarg);
            } else {
                System.out.println("[System] Unknown coder subcommand. Use: `/coder solve <problem>`, `/coder audit <file>`, `/coder inspect [path]`, or `/coder verify [path]`");
            }
        } catch (Exception e) {
            System.out.println("[Error] Coder operation failed: " + e.getMessage());
        }
    }

    public synchronized String executeWebCommand(String input) {
        java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream();
        java.io.PrintStream ps;
        try {
            ps = new java.io.PrintStream(baos, true, "UTF-8");
        } catch (java.io.UnsupportedEncodingException e) {
            ps = new java.io.PrintStream(baos, true);
        }
        java.io.PrintStream oldOut = System.out;
        java.io.PrintStream oldErr = System.err;
        System.setOut(ps);
        System.setErr(ps);
        try {
            if (input.startsWith("/")) {
                handleCommand(input);
            } else {
                handleQuery(input);
            }
        } catch (Exception e) {
            System.out.println("[Error] Command execution failed: " + e.getMessage());
        } finally {
            System.out.flush();
            System.err.flush();
            System.setOut(oldOut);
            System.setErr(oldErr);
        }
        return baos.toString();
    }
}
