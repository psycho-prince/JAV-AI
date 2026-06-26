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
            server.createContext("/api/workspace/inspect", new WorkspaceInspectHandler());
            server.createContext("/api/workspace/verify", new WorkspaceVerifyHandler());
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

    private class WorkspaceInspectHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            try {
                String rootPath = ".";
                if (exchange.getRequestMethod().equalsIgnoreCase("POST")) {
                    InputStream is = exchange.getRequestBody();
                    ByteArrayOutputStream bos = new ByteArrayOutputStream();
                    byte[] buffer = new byte[1024];
                    int len;
                    while ((len = is.read(buffer)) > -1) {
                        bos.write(buffer, 0, len);
                    }
                    String body = bos.toString(StandardCharsets.UTF_8);
                    if (!body.isBlank()) {
                        com.fasterxml.jackson.databind.JsonNode bodyNode = mapper.readTree(body);
                        if (bodyNode.has("path")) {
                            rootPath = bodyNode.get("path").asText();
                        }
                    }
                }

                com.javai.security.coder.CoderEngine coder = new com.javai.security.coder.CoderEngine(
                        javAI.getDatabaseManager(),
                        javAI.getModelRouter(),
                        javAI.getMemoryEngine()
                );
                
                com.javai.security.coder.WorkspaceProfile profile = coder.inspectWorkspace(rootPath);
                
                ObjectNode response = mapper.createObjectNode();
                response.put("rootPath", profile.getRootPath());
                response.put("buildSystem", profile.getBuildSystem());
                
                ArrayNode sourceFiles = mapper.createArrayNode();
                for (String f : profile.getSourceFiles()) {
                    sourceFiles.add(f);
                }
                response.set("sourceFiles", sourceFiles);

                ArrayNode testFiles = mapper.createArrayNode();
                for (String f : profile.getTestFiles()) {
                    testFiles.add(f);
                }
                response.set("testFiles", testFiles);

                ArrayNode docFiles = mapper.createArrayNode();
                for (String f : profile.getDocumentationFiles()) {
                    docFiles.add(f);
                }
                response.set("documentationFiles", docFiles);

                ArrayNode genPaths = mapper.createArrayNode();
                for (String f : profile.getGeneratedPaths()) {
                    genPaths.add(f);
                }
                response.set("generatedPaths", genPaths);

                ArrayNode verifyCmds = mapper.createArrayNode();
                for (com.javai.security.coder.VerificationCommand cmd : profile.getVerificationCommands()) {
                    ObjectNode cmdNode = mapper.createObjectNode();
                    cmdNode.put("name", cmd.getName());
                    cmdNode.put("command", cmd.asShellString());
                    verifyCmds.add(cmdNode);
                }
                response.set("verificationCommands", verifyCmds);

                byte[] bytes = mapper.writeValueAsBytes(response);
                sendJsonResponse(exchange, 200, bytes);
            } catch (Exception e) {
                sendErrorResponse(exchange, e);
            }
        }
    }

    private class WorkspaceVerifyHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            try {
                String rootPath = ".";
                if (exchange.getRequestMethod().equalsIgnoreCase("POST")) {
                    InputStream is = exchange.getRequestBody();
                    ByteArrayOutputStream bos = new ByteArrayOutputStream();
                    byte[] buffer = new byte[1024];
                    int len;
                    while ((len = is.read(buffer)) > -1) {
                        bos.write(buffer, 0, len);
                    }
                    String body = bos.toString(StandardCharsets.UTF_8);
                    if (!body.isBlank()) {
                        com.fasterxml.jackson.databind.JsonNode bodyNode = mapper.readTree(body);
                        if (bodyNode.has("path")) {
                            rootPath = bodyNode.get("path").asText();
                        }
                    }
                }

                com.javai.security.coder.CoderEngine coder = new com.javai.security.coder.CoderEngine(
                        javAI.getDatabaseManager(),
                        javAI.getModelRouter(),
                        javAI.getMemoryEngine()
                );
                
                com.javai.security.coder.BuildVerifier.VerificationResult result = coder.verifyWorkspace(rootPath);
                
                ObjectNode response = mapper.createObjectNode();
                response.put("success", result.isSuccess());
                response.put("exitCode", result.getExitCode());
                response.put("timedOut", result.isTimedOut());
                response.put("durationMillis", result.getDurationMillis());
                response.put("output", result.getOutput());
                response.put("command", result.getCommand().asShellString());

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
        try (InputStream is = WebServer.class.getResourceAsStream("/dashboard/index.html")) {
            if (is != null) {
                return new String(is.readAllBytes(), StandardCharsets.UTF_8);
            }
        } catch (IOException ignored) {}
        
        // Fallback minimal HTML
        return "<!DOCTYPE html><html><head><title>JavAI</title></head><body><h1>JavAI Static Resource Error</h1><p>Could not load visual dashboard template from classpath.</p></body></html>";
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
