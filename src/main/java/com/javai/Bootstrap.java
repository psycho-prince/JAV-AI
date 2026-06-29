package com.javai;

import com.javai.core.JavAI;
import com.javai.ui.ConsoleUI;

public class Bootstrap {
    public static void start() {
        System.out.println("==================================================");
        System.out.println("           JavAI RESEARCH ENGINE v1.0            ");
        System.out.println("==================================================");
        System.out.println("Initializing system components...");
        
        try {
            JavAI javAI = new JavAI();
            javAI.initialize();
            
            System.out.println("JavAI core initialized successfully.");
            System.out.println("--------------------------------------------------");
            
            checkModelAvailability(javAI);
            
            ConsoleUI ui = new ConsoleUI(javAI);
            
            com.javai.ui.WebServer webServer = new com.javai.ui.WebServer(javAI, ui);
            webServer.start();
            
            ui.run();
            
            webServer.stop();
        } catch (Exception e) {
            System.err.println("Fatal error during bootstrap: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }

    private static void checkModelAvailability(com.javai.core.JavAI javAI) {
        String activeModel = javAI.getModelRouter().getActiveModelName();
        if ("qwen".equals(activeModel) || "openai".equals(activeModel)) {
            String endpoint = javAI.getModelConfig().getEndpoint();
            try {
                java.net.URI uri = new java.net.URI(endpoint);
                String host = uri.getHost();
                int port = uri.getPort();
                if (port == -1) {
                    port = uri.getScheme().equalsIgnoreCase("https") ? 443 : 80;
                }
                
                try (java.net.Socket socket = new java.net.Socket()) {
                    socket.connect(new java.net.InetSocketAddress(host, port), 1000);
                }
            } catch (Exception e) {
                System.out.println("\u001B[33m[Warning] Local model service is unreachable at: " + endpoint);
                System.out.println("          Please make sure Ollama is running and has the model pulled.");
                System.out.println("          (Or switch to a cloud model using: `/model switch gemini` or `/model switch claude`)\u001B[0m");
                System.out.println("--------------------------------------------------");
            }
        }
    }
}
