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
            
            ConsoleUI ui = new ConsoleUI(javAI);
            ui.run();
        } catch (Exception e) {
            System.err.println("Fatal error during bootstrap: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }
}
