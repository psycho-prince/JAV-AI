package com.javai.ui;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import com.javai.core.JavAI;

import java.io.*;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.concurrent.Executors;

public class WebServer {
    private HttpServer server;
    private final JavAI javAI;
    private final ConsoleUI consoleUI;
    private final int port = 1337;
    private final ObjectMapper mapper = new ObjectMapper();

    public WebServer(JavAI javAI, ConsoleUI consoleUI) {
        this.javAI = javAI;
        this.consoleUI = consoleUI;
    }

    public void start() {
        try {
            // Ensure dashboard folder and HTML file exist
            prepareDashboardFiles();

            server = HttpServer.create(new InetSocketAddress(port), 0);
            server.createContext("/", new StaticFileHandler());
            server.createContext("/api/status", new StatusHandler());
            server.createContext("/api/dashboard", new DashboardHandler());
            server.createContext("/api/findings", new FindingsHandler());
            server.createContext("/api/observations", new ObservationsHandler());
            server.createContext("/api/query", new QueryHandler());
            server.createContext("/v1/chat/completions", new OpenAICompletionsHandler());

            server.setExecutor(Executors.newCachedThreadPool());
            server.start();

            // Print styled server startup notice
            System.out.println("\n\u001B[35m==============================================================");
            System.out.println("\u001B[32m[WebServer] Running visual dashboard at http://localhost:" + port + "\u001B[35m");
            System.out.println("==============================================================\u001B[0m\n");
        } catch (Exception e) {
            System.err.println("\u001B[31m[WebServer] Failed to start server: " + e.getMessage() + "\u001B[0m");
        }
    }

    public void stop() {
        if (server != null) {
            server.stop(0);
        }
    }

    private void prepareDashboardFiles() throws IOException {
        File dir = new File("workspace/dashboard");
        if (!dir.exists()) {
            dir.mkdirs();
        }
        File htmlFile = new File(dir, "index.html");
        if (!htmlFile.exists()) {
            String defaultHtml = getDefaultHtmlContent();
            Files.writeString(htmlFile.toPath(), defaultHtml, StandardCharsets.UTF_8);
        }
    }

    private class StaticFileHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            String path = exchange.getRequestURI().getPath();
            if (path.equals("/") || path.isEmpty()) {
                path = "/index.html";
            }

            File file = new File("workspace/dashboard" + path);
            if (!file.exists() || file.isDirectory()) {
                exchange.sendResponseHeaders(404, 0);
                try (OutputStream os = exchange.getResponseBody()) {
                    os.write("404 Not Found".getBytes(StandardCharsets.UTF_8));
                }
                return;
            }

            byte[] content = Files.readAllBytes(file.toPath());
            String contentType = "text/html";
            if (path.endsWith(".css")) {
                contentType = "text/css";
            } else if (path.endsWith(".js")) {
                contentType = "application/javascript";
            } else if (path.endsWith(".json")) {
                contentType = "application/json";
            } else if (path.endsWith(".png")) {
                contentType = "image/png";
            }

            exchange.getResponseHeaders().set("Content-Type", contentType + "; charset=utf-8");
            exchange.sendResponseHeaders(200, content.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(content);
            }
        }
    }

    private class StatusHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            try {
                ObjectNode response = mapper.createObjectNode();
                response.put("projectName", javAI.getMemoryEngine().getActiveProjectName());
                response.put("projectId", javAI.getMemoryEngine().getActiveProjectId());
                response.put("modelName", javAI.getModelRouter().getActiveModelName());
                response.put("programName", javAI.getMemoryEngine().getActiveProgramName() == null ? "None" : javAI.getMemoryEngine().getActiveProgramName());

                byte[] bytes = mapper.writeValueAsBytes(response);
                sendJsonResponse(exchange, 200, bytes);
            } catch (Exception e) {
                sendErrorResponse(exchange, e);
            }
        }
    }

    private class DashboardHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            try {
                int activeProjectId = javAI.getMemoryEngine().getActiveProjectId();

                int obsCount = 0;
                int evCount = 0;
                int hypCount = 0;
                int valCount = 0;
                int repCount = 0;
                int reportsCount = 0;

                try (Connection conn = javAI.getDatabaseManager().getConnection()) {
                    // Observations
                    String sqlObs = "SELECT COUNT(*) FROM observations WHERE project_id = ?";
                    try (PreparedStatement stmt = conn.prepareStatement(sqlObs)) {
                        stmt.setInt(1, activeProjectId);
                        try (ResultSet rs = stmt.executeQuery()) {
                            if (rs.next()) obsCount = rs.getInt(1);
                        }
                    }
                    // Evidence
                    String sqlEv = "SELECT COUNT(e.id) FROM evidence e JOIN findings f ON e.finding_id = f.id WHERE f.project_id = ?";
                    try (PreparedStatement stmt = conn.prepareStatement(sqlEv)) {
                        stmt.setInt(1, activeProjectId);
                        try (ResultSet rs = stmt.executeQuery()) {
                            if (rs.next()) evCount = rs.getInt(1);
                        }
                    }
                    // Hypotheses
                    String sqlHyp = "SELECT COUNT(*) FROM findings WHERE project_id = ? AND state = 'HYPOTHESIS'";
                    try (PreparedStatement stmt = conn.prepareStatement(sqlHyp)) {
                        stmt.setInt(1, activeProjectId);
                        try (ResultSet rs = stmt.executeQuery()) {
                            if (rs.next()) hypCount = rs.getInt(1);
                        }
                    }
                    // Validated
                    String sqlVal = "SELECT COUNT(*) FROM findings WHERE project_id = ? AND state = 'VALIDATED'";
                    try (PreparedStatement stmt = conn.prepareStatement(sqlVal)) {
                        stmt.setInt(1, activeProjectId);
                        try (ResultSet rs = stmt.executeQuery()) {
                            if (rs.next()) valCount = rs.getInt(1);
                        }
                    }
                    // Reported
                    String sqlRep = "SELECT COUNT(*) FROM findings WHERE project_id = ? AND state = 'REPORTED'";
                    try (PreparedStatement stmt = conn.prepareStatement(sqlRep)) {
                        stmt.setInt(1, activeProjectId);
                        try (ResultSet rs = stmt.executeQuery()) {
                            if (rs.next()) repCount = rs.getInt(1);
                        }
                    }
                    // Reports
                    String sqlRepCount = "SELECT COUNT(*) FROM reports WHERE project_id = ?";
                    try (PreparedStatement stmt = conn.prepareStatement(sqlRepCount)) {
                        stmt.setInt(1, activeProjectId);
                        try (ResultSet rs = stmt.executeQuery()) {
                            if (rs.next()) reportsCount = rs.getInt(1);
                        }
                    }
                }

                // Coverage Array
                ArrayNode coverageArray = mapper.createArrayNode();
                String covSql = "SELECT t.domain, c.playbook_name, c.completed_steps, c.total_steps, c.coverage_percent " +
                                "FROM coverage c JOIN targets t ON c.target_id = t.id " +
                                "WHERE c.project_id = ? ORDER BY t.domain ASC, c.playbook_name ASC";
                try (Connection conn = javAI.getDatabaseManager().getConnection();
                     PreparedStatement stmt = conn.prepareStatement(covSql)) {
                    stmt.setInt(1, activeProjectId);
                    try (ResultSet rs = stmt.executeQuery()) {
                        while (rs.next()) {
                            ObjectNode node = mapper.createObjectNode();
                            node.put("domain", rs.getString("domain"));
                            node.put("playbook", rs.getString("playbook_name"));
                            node.put("completed", rs.getInt("completed_steps"));
                            node.put("total", rs.getInt("total_steps"));
                            node.put("percent", rs.getDouble("coverage_percent"));
                            coverageArray.add(node);
                        }
                    }
                }

                ObjectNode response = mapper.createObjectNode();
                response.put("observations", obsCount);
                response.put("evidence", evCount);
                response.put("hypotheses", hypCount);
                response.put("validated", valCount);
                response.put("reported", repCount);
                response.put("reports", reportsCount);
                response.set("coverage", coverageArray);

                byte[] bytes = mapper.writeValueAsBytes(response);
                sendJsonResponse(exchange, 200, bytes);
            } catch (Exception e) {
                sendErrorResponse(exchange, e);
            }
        }
    }

    private class FindingsHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            try {
                int activeProjectId = javAI.getMemoryEngine().getActiveProjectId();
                ArrayNode findings = mapper.createArrayNode();

                String sql = "SELECT id, title, severity, description, state, confidence FROM findings WHERE project_id = ? ORDER BY id DESC";
                try (Connection conn = javAI.getDatabaseManager().getConnection();
                     PreparedStatement stmt = conn.prepareStatement(sql)) {
                    stmt.setInt(1, activeProjectId);
                    try (ResultSet rs = stmt.executeQuery()) {
                        while (rs.next()) {
                            ObjectNode node = mapper.createObjectNode();
                            node.put("id", rs.getInt("id"));
                            node.put("title", rs.getString("title"));
                            node.put("severity", rs.getString("severity"));
                            node.put("description", rs.getString("description"));
                            node.put("state", rs.getString("state"));
                            node.put("confidence", rs.getDouble("confidence"));
                            findings.add(node);
                        }
                    }
                }

                byte[] bytes = mapper.writeValueAsBytes(findings);
                sendJsonResponse(exchange, 200, bytes);
            } catch (Exception e) {
                sendErrorResponse(exchange, e);
            }
        }
    }

    private class ObservationsHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            try {
                int activeProjectId = javAI.getMemoryEngine().getActiveProjectId();
                ArrayNode observations = mapper.createArrayNode();

                String sql = "SELECT o.id, t.domain, o.description, o.source, o.confidence, o.created_at " +
                        "FROM observations o JOIN targets t ON o.target_id = t.id " +
                        "WHERE o.project_id = ? ORDER BY o.id DESC";
                try (Connection conn = javAI.getDatabaseManager().getConnection();
                     PreparedStatement stmt = conn.prepareStatement(sql)) {
                    stmt.setInt(1, activeProjectId);
                    try (ResultSet rs = stmt.executeQuery()) {
                        while (rs.next()) {
                            ObjectNode node = mapper.createObjectNode();
                            node.put("id", rs.getInt("id"));
                            node.put("target", rs.getString("domain"));
                            node.put("description", rs.getString("description"));
                            node.put("source", rs.getString("source"));
                            node.put("confidence", rs.getDouble("confidence"));
                            node.put("created_at", rs.getLong("created_at"));
                            observations.add(node);
                        }
                    }
                }

                byte[] bytes = mapper.writeValueAsBytes(observations);
                sendJsonResponse(exchange, 200, bytes);
            } catch (Exception e) {
                sendErrorResponse(exchange, e);
            }
        }
    }

    private class QueryHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (!exchange.getRequestMethod().equalsIgnoreCase("POST")) {
                exchange.sendResponseHeaders(405, 0);
                return;
            }

            try {
                // Read request body
                InputStream is = exchange.getRequestBody();
                ByteArrayOutputStream bos = new ByteArrayOutputStream();
                byte[] buffer = new byte[1024];
                int len;
                while ((len = is.read(buffer)) > -1) {
                    bos.write(buffer, 0, len);
                }
                String body = bos.toString(StandardCharsets.UTF_8);

                com.fasterxml.jackson.databind.JsonNode bodyNode = mapper.readTree(body);
                String query = bodyNode.get("query").asText();

                // Run query/command using our ConsoleUI helper
                String responseText = consoleUI.executeWebCommand(query);

                ObjectNode response = mapper.createObjectNode();
                response.put("query", query);
                response.put("response", responseText);

                byte[] bytes = mapper.writeValueAsBytes(response);
                sendJsonResponse(exchange, 200, bytes);
            } catch (Exception e) {
                sendErrorResponse(exchange, e);
            }
        }
    }

    private void sendJsonResponse(HttpExchange exchange, int code, byte[] bytes) throws IOException {
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        // Enable CORS
        exchange.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
        exchange.getResponseHeaders().set("Access-Control-Allow-Methods", "GET, POST, OPTIONS");
        exchange.getResponseHeaders().set("Access-Control-Allow-Headers", "Content-Type");
        exchange.sendResponseHeaders(code, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }

    private void sendErrorResponse(HttpExchange exchange, Exception e) throws IOException {
        ObjectNode err = mapper.createObjectNode();
        err.put("error", e.getMessage());
        StringWriter sw = new StringWriter();
        e.printStackTrace(new PrintWriter(sw));
        err.put("trace", sw.toString());

        byte[] bytes = mapper.writeValueAsBytes(err);
        sendJsonResponse(exchange, 500, bytes);
    }

    private String getDefaultHtmlContent() {
        return "<!DOCTYPE html>\n" +
                "<html lang=\"en\">\n" +
                "<head>\n" +
                "    <meta charset=\"UTF-8\">\n" +
                "    <meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\">\n" +
                "    <title>JavAI | Adversarial Security Agent</title>\n" +
                "    <link href=\"https://fonts.googleapis.com/css2?family=Outfit:wght@300;400;600;800&family=JetBrains+Mono:wght@400;700&display=swap\" rel=\"stylesheet\">\n" +
                "    <style>\n" +
                "        :root {\n" +
                "            --bg-gradient: linear-gradient(135deg, #090613 0%, #0d091e 50%, #06040d 100%);\n" +
                "            --glass-bg: rgba(255, 255, 255, 0.02);\n" +
                "            --glass-border: rgba(255, 255, 255, 0.06);\n" +
                "            --glass-glow: rgba(147, 51, 234, 0.15);\n" +
                "            --primary: #9333ea;\n" +
                "            --primary-glow: #a855f7;\n" +
                "            --cyan: #06b6d4;\n" +
                "            --cyan-glow: #22d3ee;\n" +
                "            --green: #10b981;\n" +
                "            --green-glow: #34d399;\n" +
                "            --red: #f43f5e;\n" +
                "            --red-glow: #fb7185;\n" +
                "            --orange: #f97316;\n" +
                "            --orange-glow: #fb923c;\n" +
                "            --text-main: #f3f4f6;\n" +
                "            --text-muted: #9ca3af;\n" +
                "            --terminal-bg: #030206;\n" +
                "        }\n" +
                "\n" +
                "        * {\n" +
                "            box-sizing: border-box;\n" +
                "            margin: 0;\n" +
                "            padding: 0;\n" +
                "            font-family: 'Outfit', sans-serif;\n" +
                "            scrollbar-width: thin;\n" +
                "            scrollbar-color: rgba(255, 255, 255, 0.1) transparent;\n" +
                "        }\n" +
                "\n" +
                "        ::-webkit-scrollbar {\n" +
                "            width: 6px;\n" +
                "        }\n" +
                "        ::-webkit-scrollbar-track {\n" +
                "            background: transparent;\n" +
                "        }\n" +
                "        ::-webkit-scrollbar-thumb {\n" +
                "            background: rgba(255, 255, 255, 0.1);\n" +
                "            border-radius: 4px;\n" +
                "        }\n" +
                "        ::-webkit-scrollbar-thumb:hover {\n" +
                "            background: rgba(255, 255, 255, 0.2);\n" +
                "        }\n" +
                "\n" +
                "        body {\n" +
                "            background: var(--bg-gradient);\n" +
                "            color: var(--text-main);\n" +
                "            min-height: 100vh;\n" +
                "            overflow: hidden;\n" +
                "            display: flex;\n" +
                "            flex-direction: column;\n" +
                "        }\n" +
                "\n" +
                "        /* Layout */\n" +
                "        header {\n" +
                "            display: flex;\n" +
                "            justify-content: space-between;\n" +
                "            align-items: center;\n" +
                "            padding: 16px 32px;\n" +
                "            background: rgba(13, 9, 30, 0.8);\n" +
                "            backdrop-filter: blur(16px);\n" +
                "            border-bottom: 1px solid var(--glass-border);\n" +
                "            z-index: 100;\n" +
                "        }\n" +
                "\n" +
                "        .logo-container {\n" +
                "            display: flex;\n" +
                "            align-items: center;\n" +
                "            gap: 12px;\n" +
                "        }\n" +
                "\n" +
                "        .logo {\n" +
                "            font-size: 24px;\n" +
                "            font-weight: 800;\n" +
                "            letter-spacing: 2px;\n" +
                "            background: linear-gradient(to right, #a855f7, #22d3ee);\n" +
                "            -webkit-background-clip: text;\n" +
                "            -webkit-text-fill-color: transparent;\n" +
                "            text-shadow: 0 0 20px var(--glass-glow);\n" +
                "        }\n" +
                "\n" +
                "        .logo-tag {\n" +
                "            background: rgba(147, 51, 234, 0.15);\n" +
                "            border: 1px solid rgba(147, 51, 234, 0.3);\n" +
                "            padding: 2px 8px;\n" +
                "            border-radius: 99px;\n" +
                "            font-size: 11px;\n" +
                "            font-weight: 600;\n" +
                "            color: var(--cyan-glow);\n" +
                "            letter-spacing: 1px;\n" +
                "        }\n" +
                "\n" +
                "        .status-bar {\n" +
                "            display: flex;\n" +
                "            gap: 24px;\n" +
                "            font-size: 13px;\n" +
                "        }\n" +
                "\n" +
                "        .status-item {\n" +
                "            display: flex;\n" +
                "            align-items: center;\n" +
                "            gap: 8px;\n" +
                "            background: var(--glass-bg);\n" +
                "            border: 1px solid var(--glass-border);\n" +
                "            padding: 6px 12px;\n" +
                "            border-radius: 8px;\n" +
                "        }\n" +
                "\n" +
                "        .status-dot {\n" +
                "            width: 8px;\n" +
                "            height: 8px;\n" +
                "            border-radius: 50%;\n" +
                "            background: var(--green);\n" +
                "            box-shadow: 0 0 8px var(--green-glow);\n" +
                "            animation: pulse 2s infinite;\n" +
                "        }\n" +
                "\n" +
                "        .main-container {\n" +
                "            display: flex;\n" +
                "            flex: 1;\n" +
                "            height: calc(100vh - 73px);\n" +
                "            overflow: hidden;\n" +
                "        }\n" +
                "\n" +
                "        /* Sidebar Navigation */\n" +
                "        nav {\n" +
                "            width: 80px;\n" +
                "            background: rgba(9, 6, 19, 0.4);\n" +
                "            border-right: 1px solid var(--glass-border);\n" +
                "            display: flex;\n" +
                "            flex-direction: column;\n" +
                "            align-items: center;\n" +
                "            padding: 24px 0;\n" +
                "            gap: 20px;\n" +
                "        }\n" +
                "\n" +
                "        .nav-item {\n" +
                "            width: 50px;\n" +
                "            height: 50px;\n" +
                "            border-radius: 12px;\n" +
                "            display: flex;\n" +
                "            align-items: center;\n" +
                "            justify-content: center;\n" +
                "            color: var(--text-muted);\n" +
                "            cursor: pointer;\n" +
                "            transition: all 0.3s ease;\n" +
                "            position: relative;\n" +
                "            border: 1px solid transparent;\n" +
                "        }\n" +
                "\n" +
                "        .nav-item:hover, .nav-item.active {\n" +
                "            color: var(--text-main);\n" +
                "            background: rgba(255, 255, 255, 0.03);\n" +
                "            border: 1px solid var(--glass-border);\n" +
                "            box-shadow: 0 0 15px rgba(147, 51, 234, 0.1);\n" +
                "        }\n" +
                "\n" +
                "        .nav-item.active {\n" +
                "            color: var(--cyan-glow);\n" +
                "            border-color: rgba(6, 182, 212, 0.3);\n" +
                "            background: rgba(6, 182, 212, 0.05);\n" +
                "        }\n" +
                "\n" +
                "        .nav-item .tooltip {\n" +
                "            position: absolute;\n" +
                "            left: 90px;\n" +
                "            background: #110d24;\n" +
                "            border: 1px solid var(--glass-border);\n" +
                "            padding: 6px 12px;\n" +
                "            border-radius: 6px;\n" +
                "            font-size: 12px;\n" +
                "            white-space: nowrap;\n" +
                "            opacity: 0;\n" +
                "            pointer-events: none;\n" +
                "            transition: all 0.2s ease;\n" +
                "            transform: translateX(-10px);\n" +
                "            box-shadow: 0 4px 20px rgba(0,0,0,0.5);\n" +
                "            z-index: 10;\n" +
                "        }\n" +
                "\n" +
                "        .nav-item.active .tooltip {\n" +
                "            display: none;\n" +
                "        }\n" +
                "\n" +
                "        .nav-item:hover .tooltip {\n" +
                "            opacity: 1;\n" +
                "            transform: translateX(0);\n" +
                "        }\n" +
                "\n" +
                "        /* Content Area */\n" +
                "        .content-container {\n" +
                "            flex: 1;\n" +
                "            padding: 32px;\n" +
                "            overflow-y: auto;\n" +
                "            position: relative;\n" +
                "        }\n" +
                "\n" +
                "        .tab-panel {\n" +
                "            display: none;\n" +
                "            height: 100%;\n" +
                "            animation: fadeIn 0.4s ease;\n" +
                "        }\n" +
                "\n" +
                "        .tab-panel.active {\n" +
                "            display: flex;\n" +
                "            flex-direction: column;\n" +
                "        }\n" +
                "\n" +
                "        /* Grid Metrics */\n" +
                "        .metrics-grid {\n" +
                "            display: grid;\n" +
                "            grid-template-columns: repeat(auto-fit, minmax(200px, 1fr));\n" +
                "            gap: 20px;\n" +
                "            margin-bottom: 32px;\n" +
                "        }\n" +
                "\n" +
                "        .metric-card {\n" +
                "            background: var(--glass-bg);\n" +
                "            border: 1px solid var(--glass-border);\n" +
                "            border-radius: 16px;\n" +
                "            padding: 24px;\n" +
                "            display: flex;\n" +
                "            flex-direction: column;\n" +
                "            gap: 8px;\n" +
                "            position: relative;\n" +
                "            overflow: hidden;\n" +
                "            transition: transform 0.3s ease, border-color 0.3s ease;\n" +
                "        }\n" +
                "\n" +
                "        .metric-card::before {\n" +
                "            content: '';\n" +
                "            position: absolute;\n" +
                "            top: 0;\n" +
                "            left: 0;\n" +
                "            width: 100%;\n" +
                "            height: 4px;\n" +
                "            background: var(--primary);\n" +
                "        }\n" +
                "\n" +
                "        .metric-card.cyan::before { background: var(--cyan); }\n" +
                "        .metric-card.green::before { background: var(--green); }\n" +
                "        .metric-card.red::before { background: var(--red); }\n" +
                "        .metric-card.orange::before { background: var(--orange); }\n" +
                "\n" +
                "        .metric-card:hover {\n" +
                "            transform: translateY(-4px);\n" +
                "            border-color: rgba(255,255,255,0.12);\n" +
                "            box-shadow: 0 10px 30px rgba(0,0,0,0.3);\n" +
                "        }\n" +
                "\n" +
                "        .metric-title {\n" +
                "            font-size: 13px;\n" +
                "            text-transform: uppercase;\n" +
                "            letter-spacing: 1px;\n" +
                "            color: var(--text-muted);\n" +
                "        }\n" +
                "\n" +
                "        .metric-value {\n" +
                "            font-size: 36px;\n" +
                "            font-weight: 800;\n" +
                "        }\n" +
                "\n" +
                "        /* Columns Container */\n" +
                "        .dashboard-body {\n" +
                "            display: grid;\n" +
                "            grid-template-columns: 2fr 1fr;\n" +
                "            gap: 24px;\n" +
                "            flex: 1;\n" +
                "            min-height: 0;\n" +
                "        }\n" +
                "\n" +
                "        .card {\n" +
                "            background: var(--glass-bg);\n" +
                "            border: 1px solid var(--glass-border);\n" +
                "            border-radius: 16px;\n" +
                "            padding: 24px;\n" +
                "            display: flex;\n" +
                "            flex-direction: column;\n" +
                "            gap: 16px;\n" +
                "            min-height: 0;\n" +
                "        }\n" +
                "\n" +
                "        .card-header {\n" +
                "            display: flex;\n" +
                "            justify-content: space-between;\n" +
                "            align-items: center;\n" +
                "            border-bottom: 1px solid rgba(255,255,255,0.05);\n" +
                "            padding-bottom: 12px;\n" +
                "        }\n" +
                "\n" +
                "        .card-title {\n" +
                "            font-size: 18px;\n" +
                "            font-weight: 600;\n" +
                "            letter-spacing: 0.5px;\n" +
                "        }\n" +
                "\n" +
                "        /* Lists / Tables */\n" +
                "        .list-container {\n" +
                "            overflow-y: auto;\n" +
                "            flex: 1;\n" +
                "            display: flex;\n" +
                "            flex-direction: column;\n" +
                "            gap: 12px;\n" +
                "        }\n" +
                "\n" +
                "        .list-item {\n" +
                "            background: rgba(255,255,255,0.01);\n" +
                "            border: 1px solid var(--glass-border);\n" +
                "            padding: 16px;\n" +
                "            border-radius: 10px;\n" +
                "            display: flex;\n" +
                "            justify-content: space-between;\n" +
                "            align-items: center;\n" +
                "            transition: all 0.2s ease;\n" +
                "        }\n" +
                "\n" +
                "        .list-item:hover {\n" +
                "            background: rgba(255,255,255,0.03);\n" +
                "            border-color: rgba(255,255,255,0.1);\n" +
                "        }\n" +
                "\n" +
                "        .item-main {\n" +
                "            display: flex;\n" +
                "            flex-direction: column;\n" +
                "            gap: 4px;\n" +
                "        }\n" +
                "\n" +
                "        .item-title {\n" +
                "            font-weight: 600;\n" +
                "            font-size: 15px;\n" +
                "        }\n" +
                "\n" +
                "        .item-subtitle {\n" +
                "            font-size: 13px;\n" +
                "            color: var(--text-muted);\n" +
                "        }\n" +
                "\n" +
                "        .badge {\n" +
                "            font-size: 11px;\n" +
                "            padding: 4px 10px;\n" +
                "            border-radius: 99px;\n" +
                "            font-weight: 600;\n" +
                "            text-transform: uppercase;\n" +
                "        }\n" +
                "\n" +
                "        .badge.critical { background: rgba(244,63,94,0.15); color: var(--red-glow); border: 1px solid rgba(244,63,94,0.3); }\n" +
                "        .badge.high { background: rgba(249,115,22,0.15); color: var(--orange-glow); border: 1px solid rgba(249,115,22,0.3); }\n" +
                "        .badge.medium { background: rgba(238,187,68,0.15); color: #fbbf24; border: 1px solid rgba(238,187,68,0.3); }\n" +
                "        .badge.low { background: rgba(6,182,212,0.15); color: var(--cyan-glow); border: 1px solid rgba(6,182,212,0.3); }\n" +
                "        .badge.info { background: rgba(255,255,255,0.08); color: var(--text-muted); border: 1px solid var(--glass-border); }\n" +
                "\n" +
                "        .badge.validated { background: rgba(16,185,129,0.15); color: var(--green-glow); border: 1px solid rgba(16,185,129,0.3); }\n" +
                "        .badge.hypothesis { background: rgba(147,51,234,0.15); color: var(--primary-glow); border: 1px solid rgba(147,51,234,0.3); }\n" +
                "        .badge.partial { background: rgba(6,182,212,0.15); color: var(--cyan-glow); border: 1px solid rgba(6,182,212,0.3); }\n" +
                "\n" +
                "        /* Coverage Progress Bars */\n" +
                "        .coverage-item {\n" +
                "            display: flex;\n" +
                "            flex-direction: column;\n" +
                "            gap: 8px;\n" +
                "            padding: 8px 0;\n" +
                "        }\n" +
                "\n" +
                "        .coverage-info {\n" +
                "            display: flex;\n" +
                "            justify-content: space-between;\n" +
                "            font-size: 13px;\n" +
                "        }\n" +
                "\n" +
                "        .progress-track {\n" +
                "            height: 6px;\n" +
                "            background: rgba(255,255,255,0.05);\n" +
                "            border-radius: 99px;\n" +
                "            overflow: hidden;\n" +
                "        }\n" +
                "\n" +
                "        .progress-bar {\n" +
                "            height: 100%;\n" +
                "            background: linear-gradient(to right, var(--primary), var(--cyan));\n" +
                "            border-radius: 99px;\n" +
                "            width: 0%;\n" +
                "            transition: width 1s ease;\n" +
                "        }\n" +
                "\n" +
                "        /* Terminal Chat Interface */\n" +
                "        .chat-container {\n" +
                "            display: flex;\n" +
                "            flex-direction: column;\n" +
                "            background: var(--terminal-bg);\n" +
                "            border: 1px solid var(--glass-border);\n" +
                "            border-radius: 16px;\n" +
                "            flex: 1;\n" +
                "            overflow: hidden;\n" +
                "            box-shadow: 0 20px 50px rgba(0,0,0,0.6);\n" +
                "        }\n" +
                "\n" +
                "        .terminal-header {\n" +
                "            display: flex;\n" +
                "            justify-content: space-between;\n" +
                "            align-items: center;\n" +
                "            padding: 12px 20px;\n" +
                "            background: rgba(255,255,255,0.02);\n" +
                "            border-bottom: 1px solid rgba(255,255,255,0.05);\n" +
                "        }\n" +
                "\n" +
                "        .terminal-dots {\n" +
                "            display: flex;\n" +
                "            gap: 6px;\n" +
                "        }\n" +
                "\n" +
                "        .dot {\n" +
                "            width: 12px;\n" +
                "            height: 12px;\n" +
                "            border-radius: 50%;\n" +
                "            background: #ff5f56;\n" +
                "        }\n" +
                "        .dot.yellow { background: #ffbd2e; }\n" +
                "        .dot.green { background: #27c93f; }\n" +
                "\n" +
                "        .terminal-title {\n" +
                "            font-family: 'JetBrains Mono', monospace;\n" +
                "            font-size: 12px;\n" +
                "            color: var(--text-muted);\n" +
                "        }\n" +
                "\n" +
                "        .chat-messages {\n" +
                "            flex: 1;\n" +
                "            overflow-y: auto;\n" +
                "            padding: 24px;\n" +
                "            display: flex;\n" +
                "            flex-direction: column;\n" +
                "            gap: 16px;\n" +
                "            font-family: 'JetBrains Mono', monospace;\n" +
                "            font-size: 14px;\n" +
                "        }\n" +
                "\n" +
                "        .msg-bubble {\n" +
                "            max-width: 85%;\n" +
                "            padding: 16px 20px;\n" +
                "            border-radius: 12px;\n" +
                "            line-height: 1.6;\n" +
                "            white-space: pre-wrap;\n" +
                "            animation: slideUp 0.3s ease;\n" +
                "        }\n" +
                "\n" +
                "        .msg-bubble.user {\n" +
                "            align-self: flex-end;\n" +
                "            background: rgba(147, 51, 234, 0.15);\n" +
                "            border: 1px solid rgba(147, 51, 234, 0.3);\n" +
                "            border-bottom-right-radius: 2px;\n" +
                "            color: #f3e8ff;\n" +
                "        }\n" +
                "\n" +
                "        .msg-bubble.ai {\n" +
                "            align-self: flex-start;\n" +
                "            background: rgba(255, 255, 255, 0.02);\n" +
                "            border: 1px solid var(--glass-border);\n" +
                "            border-bottom-left-radius: 2px;\n" +
                "            color: #e5e7eb;\n" +
                "        }\n" +
                "\n" +
                "        .msg-bubble pre {\n" +
                "            background: rgba(0,0,0,0.4);\n" +
                "            border: 1px solid rgba(255,255,255,0.05);\n" +
                "            padding: 12px;\n" +
                "            border-radius: 6px;\n" +
                "            margin: 8px 0;\n" +
                "            overflow-x: auto;\n" +
                "            font-family: 'JetBrains Mono', monospace;\n" +
                "        }\n" +
                "\n" +
                "        .chat-input-bar {\n" +
                "            display: flex;\n" +
                "            gap: 12px;\n" +
                "            padding: 16px 24px;\n" +
                "            border-top: 1px solid rgba(255,255,255,0.05);\n" +
                "            background: rgba(0,0,0,0.2);\n" +
                "        }\n" +
                "\n" +
                "        .chat-input {\n" +
                "            flex: 1;\n" +
                "            background: rgba(255, 255, 255, 0.03);\n" +
                "            border: 1px solid var(--glass-border);\n" +
                "            border-radius: 10px;\n" +
                "            padding: 12px 16px;\n" +
                "            color: var(--text-main);\n" +
                "            font-family: 'JetBrains Mono', monospace;\n" +
                "            font-size: 14px;\n" +
                "            transition: all 0.3s ease;\n" +
                "        }\n" +
                "\n" +
                "        .chat-input:focus {\n" +
                "            outline: none;\n" +
                "            border-color: var(--cyan);\n" +
                "            box-shadow: 0 0 10px rgba(6, 182, 212, 0.15);\n" +
                "        }\n" +
                "\n" +
                "        .send-btn {\n" +
                "            background: var(--primary);\n" +
                "            border: none;\n" +
                "            color: white;\n" +
                "            padding: 12px 24px;\n" +
                "            border-radius: 10px;\n" +
                "            font-weight: 600;\n" +
                "            cursor: pointer;\n" +
                "            transition: all 0.3s ease;\n" +
                "            box-shadow: 0 4px 15px rgba(147, 51, 234, 0.3);\n" +
                "        }\n" +
                "\n" +
                "        .send-btn:hover {\n" +
                "            background: var(--primary-glow);\n" +
                "            box-shadow: 0 4px 20px rgba(147, 51, 234, 0.5);\n" +
                "        }\n" +
                "\n" +
                "        /* Quantum Blue Seal Panels */\n" +
                "        .quantum-panel {\n" +
                "            display: grid;\n" +
                "            grid-template-columns: 1fr 1fr;\n" +
                "            gap: 24px;\n" +
                "            flex: 1;\n" +
                "        }\n" +
                "\n" +
                "        .form-group {\n" +
                "            display: flex;\n" +
                "            flex-direction: column;\n" +
                "            gap: 8px;\n" +
                "            margin-bottom: 16px;\n" +
                "        }\n" +
                "\n" +
                "        .form-group label {\n" +
                "            font-size: 13px;\n" +
                "            color: var(--text-muted);\n" +
                "            text-transform: uppercase;\n" +
                "            letter-spacing: 0.5px;\n" +
                "        }\n" +
                "\n" +
                "        .form-input {\n" +
                "            background: rgba(255, 255, 255, 0.02);\n" +
                "            border: 1px solid var(--glass-border);\n" +
                "            border-radius: 8px;\n" +
                "            padding: 12px;\n" +
                "            color: var(--text-main);\n" +
                "            font-size: 14px;\n" +
                "            transition: all 0.3s ease;\n" +
                "        }\n" +
                "\n" +
                "        .form-input:focus {\n" +
                "            outline: none;\n" +
                "            border-color: var(--cyan);\n" +
                "            box-shadow: 0 0 10px rgba(6,182,212,0.1);\n" +
                "        }\n" +
                "\n" +
                "        /* Animations */\n" +
                "        @keyframes pulse {\n" +
                "            0%, 100% { opacity: 1; transform: scale(1); }\n" +
                "            50% { opacity: 0.5; transform: scale(0.9); }\n" +
                "        }\n" +
                "\n" +
                "        @keyframes fadeIn {\n" +
                "            from { opacity: 0; transform: translateY(8px); }\n" +
                "            to { opacity: 1; transform: translateY(0); }\n" +
                "        }\n" +
                "\n" +
                "        @keyframes slideUp {\n" +
                "            from { opacity: 0; transform: translateY(16px); }\n" +
                "            to { opacity: 1; transform: translateY(0); }\n" +
                "        }\n" +
                "\n" +
                "        /* Responsive */\n" +
                "        @media(max-width: 1024px) {\n" +
                "            .dashboard-body {\n" +
                "                grid-template-columns: 1fr;\n" +
                "            }\n" +
                "            .quantum-panel {\n" +
                "                grid-template-columns: 1fr;\n" +
                "            }\n" +
                "        }\n" +
                "    </script>\n" +
                "</body>\n" +
                "</html>\n";
    }

    private class OpenAICompletionsHandler implements com.sun.net.httpserver.HttpHandler {
        @Override
        public void handle(com.sun.net.httpserver.HttpExchange exchange) throws IOException {
            // Support CORS preflight
            if (exchange.getRequestMethod().equalsIgnoreCase("OPTIONS")) {
                exchange.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
                exchange.getResponseHeaders().set("Access-Control-Allow-Methods", "POST, OPTIONS");
                exchange.getResponseHeaders().set("Access-Control-Allow-Headers", "Content-Type, Authorization");
                exchange.sendResponseHeaders(204, -1);
                return;
            }

            if (!exchange.getRequestMethod().equalsIgnoreCase("POST")) {
                exchange.sendResponseHeaders(405, 0);
                return;
            }

            try {
                InputStream is = exchange.getRequestBody();
                ByteArrayOutputStream bos = new ByteArrayOutputStream();
                byte[] buffer = new byte[1024];
                int len;
                while ((len = is.read(buffer)) > -1) {
                    bos.write(buffer, 0, len);
                }
                String body = bos.toString(StandardCharsets.UTF_8);

                com.fasterxml.jackson.databind.JsonNode root = mapper.readTree(body);
                com.fasterxml.jackson.databind.JsonNode messagesNode = root.get("messages");
                
                String lastUserMessage = "";
                if (messagesNode != null && messagesNode.isArray() && messagesNode.size() > 0) {
                    com.fasterxml.jackson.databind.JsonNode lastMsg = messagesNode.get(messagesNode.size() - 1);
                    lastUserMessage = lastMsg.path("content").asText();
                }

                // Process standard query through JavAI Agent Engine
                String responseText = consoleUI.executeWebCommand(lastUserMessage);

                // Build standard OpenAI response body
                ObjectNode responseJson = mapper.createObjectNode();
                responseJson.put("id", "chatcmpl-javai-" + System.currentTimeMillis());
                responseJson.put("object", "chat.completion");
                responseJson.put("created", System.currentTimeMillis() / 1000L);
                responseJson.put("model", "javai");

                ArrayNode choices = mapper.createArrayNode();
                ObjectNode choice = mapper.createObjectNode();
                choice.put("index", 0);
                
                ObjectNode message = mapper.createObjectNode();
                message.put("role", "assistant");
                message.put("content", responseText);
                
                choice.set("message", message);
                choice.put("finish_reason", "stop");
                choices.add(choice);
                responseJson.set("choices", choices);

                byte[] bytes = mapper.writeValueAsBytes(responseJson);
                sendJsonResponse(exchange, 200, bytes);
            } catch (Exception e) {
                sendErrorResponse(exchange, e);
            }
        }
    }
}
