package com.javai.core;

import com.javai.llm.ModelRouter;
import com.javai.llm.LocalModelConfig;
import com.javai.memory.MemoryEngine;
import com.javai.storage.DatabaseManager;
import com.javai.plugins.Plugin;
import com.javai.plugins.pentest.NmapPlugin;
import com.javai.plugins.pentest.SubfinderPlugin;
import com.javai.plugins.pentest.HttpxPlugin;
import com.javai.plugins.pentest.KatanaPlugin;
import com.javai.plugins.pentest.WhoisPlugin;
import com.javai.plugins.pentest.DnsPlugin;

import java.util.HashMap;
import java.util.Map;

public class JavAI {
    private AgentEngine agentEngine;
    private MemoryEngine memoryEngine;
    private DatabaseManager databaseManager;
    private ModelRouter modelRouter;
    private LocalModelConfig modelConfig;
    private final Map<String, Plugin> plugins = new HashMap<>();

    public void initialize() throws Exception {
        // 1. Initialize SQLite Database storage manager
        databaseManager = new DatabaseManager();
        databaseManager.initialize();

        // Ensure workspace directories exist
        String[] dirs = {"workspace", "workspace/targets", "workspace/scans", "workspace/findings", "workspace/screenshots", "workspace/reports"};
        for (String dirPath : dirs) {
            java.io.File dir = new java.io.File(dirPath);
            if (!dir.exists()) {
                dir.mkdirs();
            }
        }

        // 2. Initialize Conversation memory system
        memoryEngine = new MemoryEngine(databaseManager);
        memoryEngine.initialize();

        // 3. Initialize Model Router with config
        modelConfig = new LocalModelConfig();
        modelRouter = new ModelRouter(modelConfig);
        modelRouter.initialize();

        // 4. Register and initialize plugins
        registerPlugin(new NmapPlugin());
        registerPlugin(new SubfinderPlugin());
        registerPlugin(new HttpxPlugin());
        registerPlugin(new KatanaPlugin());
        registerPlugin(new WhoisPlugin());
        registerPlugin(new DnsPlugin());

        // 5. Initialize Agent orchestration coordinator
        agentEngine = new AgentEngine(modelRouter, memoryEngine);
        agentEngine.initialize();

        // 6. Setup Context Engine
        ContextBuilder contextBuilder = new ContextBuilder(databaseManager, memoryEngine);
        PromptAssembler promptAssembler = new PromptAssembler(contextBuilder);
        agentEngine.setupContext(contextBuilder, promptAssembler);

        // 7. Setup Pentest Reasoning layer
        methodologyEngine = new com.javai.security.MethodologyEngine(memoryEngine, databaseManager);
    }

    private void registerPlugin(Plugin plugin) {
        plugins.put(plugin.getName().toLowerCase(), plugin);
    }

    public AgentEngine getAgentEngine() {
        return agentEngine;
    }

    public MemoryEngine getMemoryEngine() {
        return memoryEngine;
    }

    public DatabaseManager getDatabaseManager() {
        return databaseManager;
    }

    public ModelRouter getModelRouter() {
        return modelRouter;
    }

    public LocalModelConfig getModelConfig() {
        return modelConfig;
    }

    public Map<String, Plugin> getPlugins() {
        return plugins;
    }

    public com.javai.security.MethodologyEngine getMethodologyEngine() {
        return methodologyEngine;
    }

    private com.javai.security.MethodologyEngine methodologyEngine;
}
