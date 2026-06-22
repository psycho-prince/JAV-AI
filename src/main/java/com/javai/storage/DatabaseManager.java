package com.javai.storage;

import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;

public class DatabaseManager {
    private String dbDir = "database";
    private String dbFile = dbDir + "/javai.db";
    private String jdbcUrl = "jdbc:sqlite:" + dbFile;

    private Connection connection;

    public DatabaseManager() {
    }

    public DatabaseManager(String dbFile) {
        this.dbFile = dbFile;
        File file = new File(dbFile);
        File parent = file.getParentFile();
        this.dbDir = parent != null ? parent.getPath() : ".";
        this.jdbcUrl = "jdbc:sqlite:" + dbFile;
    }

    public void initialize() throws Exception {
        // Ensure database directory exists
        File dir = new File(dbDir);
        if (!dir.exists()) {
            dir.mkdirs();
        }

        // Load SQLite JDBC Driver
        Class.forName("org.sqlite.JDBC");
        
        // Establish Connection
        connection = DriverManager.getConnection(jdbcUrl);
        
        // Setup initial schema
        createTables();
    }

    private void createTables() throws Exception {
        try (Statement stmt = connection.createStatement()) {
            // Enable Foreign Keys
            stmt.execute("PRAGMA foreign_keys = ON;");

            // Users Table
            stmt.execute("CREATE TABLE IF NOT EXISTS users (" +
                    "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                    "username TEXT NOT NULL UNIQUE," +
                    "role TEXT NOT NULL" +
                    ");");

            // Conversations Table
            stmt.execute("CREATE TABLE IF NOT EXISTS conversations (" +
                    "id TEXT PRIMARY KEY," +
                    "title TEXT NOT NULL," +
                    "created_at INTEGER NOT NULL" +
                    ");");

            // Messages Table (linked to Conversations)
            stmt.execute("CREATE TABLE IF NOT EXISTS messages (" +
                    "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                    "conversation_id TEXT NOT NULL," +
                    "role TEXT NOT NULL," +
                    "content TEXT NOT NULL," +
                    "timestamp INTEGER NOT NULL," +
                    "FOREIGN KEY(conversation_id) REFERENCES conversations(id) ON DELETE CASCADE" +
                    ");");

            // Notes Table
            stmt.execute("CREATE TABLE IF NOT EXISTS notes (" +
                    "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                    "title TEXT NOT NULL," +
                    "content TEXT NOT NULL," +
                    "created_at INTEGER NOT NULL" +
                    ");");
            
            // Projects Table
            stmt.execute("CREATE TABLE IF NOT EXISTS projects (" +
                    "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                    "name TEXT NOT NULL UNIQUE," +
                    "description TEXT," +
                    "program_name TEXT," +
                    "created_at INTEGER NOT NULL" +
                    ");");

            // Seed a default project
            stmt.execute("INSERT OR IGNORE INTO projects (id, name, description, created_at) " +
                    "VALUES (1, 'default', 'Default security research project', " + System.currentTimeMillis() + ");");

            // Findings Table
            stmt.execute("CREATE TABLE IF NOT EXISTS findings (" +
                    "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                    "project_id INTEGER NOT NULL," +
                    "title TEXT NOT NULL," +
                    "description TEXT NOT NULL," +
                    "severity TEXT NOT NULL," +
                    "state TEXT DEFAULT 'HYPOTHESIS'," +
                    "confidence REAL DEFAULT 0.05," +
                    "evidence_count INTEGER DEFAULT 0," +
                    "created_at INTEGER NOT NULL," +
                    "FOREIGN KEY(project_id) REFERENCES projects(id) ON DELETE CASCADE" +
                    ");");

            // Tasks Table
            stmt.execute("CREATE TABLE IF NOT EXISTS tasks (" +
                    "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                    "project_id INTEGER NOT NULL," +
                    "title TEXT NOT NULL," +
                    "status TEXT NOT NULL," +
                    "created_at INTEGER NOT NULL," +
                    "FOREIGN KEY(project_id) REFERENCES projects(id) ON DELETE CASCADE" +
                    ");");

            // Knowledge Table
            stmt.execute("CREATE TABLE IF NOT EXISTS knowledge (" +
                    "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                    "key TEXT UNIQUE NOT NULL," +
                    "value TEXT NOT NULL," +
                    "category TEXT," +
                    "created_at INTEGER NOT NULL," +
                    "accepted_reports INTEGER DEFAULT 0," +
                    "duplicates INTEGER DEFAULT 0," +
                    "informational_items INTEGER DEFAULT 0," +
                    "rewards REAL DEFAULT 0.0," +
                    "severity TEXT" +
                    ");");

            // Targets Table
            stmt.execute("CREATE TABLE IF NOT EXISTS targets (" +
                    "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                    "project_id INTEGER NOT NULL," +
                    "domain TEXT NOT NULL," +
                    "created_at INTEGER NOT NULL," +
                    "FOREIGN KEY(project_id) REFERENCES projects(id) ON DELETE CASCADE," +
                    "UNIQUE(project_id, domain)" +
                    ");");

            // Scans Table
            stmt.execute("CREATE TABLE IF NOT EXISTS scans (" +
                    "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                    "project_id INTEGER NOT NULL," +
                    "target_id INTEGER NOT NULL," +
                    "tool TEXT NOT NULL," +
                    "status TEXT NOT NULL," +
                    "created_at INTEGER NOT NULL," +
                    "FOREIGN KEY(project_id) REFERENCES projects(id) ON DELETE CASCADE," +
                    "FOREIGN KEY(target_id) REFERENCES targets(id) ON DELETE CASCADE" +
                    ");");

            // Scan Results Table
            stmt.execute("CREATE TABLE IF NOT EXISTS scan_results (" +
                    "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                    "scan_id INTEGER NOT NULL," +
                    "raw_output TEXT NOT NULL," +
                    "created_at INTEGER NOT NULL," +
                    "FOREIGN KEY(scan_id) REFERENCES scans(id) ON DELETE CASCADE" +
                    ");");

            // Assets Table
            stmt.execute("CREATE TABLE IF NOT EXISTS assets (" +
                    "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                    "project_id INTEGER NOT NULL," +
                    "target_id INTEGER NOT NULL," +
                    "type TEXT NOT NULL," +
                    "value TEXT NOT NULL," +
                    "metadata TEXT," +
                    "created_at INTEGER NOT NULL," +
                    "FOREIGN KEY(project_id) REFERENCES projects(id) ON DELETE CASCADE," +
                    "FOREIGN KEY(target_id) REFERENCES targets(id) ON DELETE CASCADE," +
                    "UNIQUE(project_id, target_id, type, value)" +
                    ");");

            // Reports Table
            stmt.execute("CREATE TABLE IF NOT EXISTS reports (" +
                    "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                    "project_id INTEGER NOT NULL," +
                    "title TEXT NOT NULL," +
                    "format TEXT NOT NULL," +
                    "created_at INTEGER NOT NULL," +
                    "FOREIGN KEY(project_id) REFERENCES projects(id) ON DELETE CASCADE" +
                    ");");

            // Report Sections Table
            stmt.execute("CREATE TABLE IF NOT EXISTS report_sections (" +
                    "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                    "report_id INTEGER NOT NULL," +
                    "finding_id INTEGER NOT NULL," +
                    "sort_order INTEGER NOT NULL," +
                    "FOREIGN KEY(report_id) REFERENCES reports(id) ON DELETE CASCADE," +
                    "FOREIGN KEY(finding_id) REFERENCES findings(id) ON DELETE CASCADE" +
                    ");");

            // Evidence Table
            stmt.execute("CREATE TABLE IF NOT EXISTS evidence (" +
                    "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                    "finding_id INTEGER NOT NULL," +
                    "title TEXT NOT NULL," +
                    "content TEXT NOT NULL," +
                    "created_at INTEGER NOT NULL," +
                    "FOREIGN KEY(finding_id) REFERENCES findings(id) ON DELETE CASCADE" +
                    ");");

            // Programs Table
            stmt.execute("CREATE TABLE IF NOT EXISTS programs (" +
                    "name TEXT PRIMARY KEY," +
                    "type TEXT," +
                    "max_bounty INTEGER" +
                    ");");

            // Program Rules Table
            stmt.execute("CREATE TABLE IF NOT EXISTS program_rules (" +
                    "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                    "program_name TEXT NOT NULL," +
                    "rule_type TEXT NOT NULL," +
                    "rule_text TEXT NOT NULL," +
                    "FOREIGN KEY(program_name) REFERENCES programs(name) ON DELETE CASCADE" +
                    ");");

            // Program Exclusions Table
            stmt.execute("CREATE TABLE IF NOT EXISTS program_exclusions (" +
                    "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                    "program_name TEXT NOT NULL," +
                    "exclusion_text TEXT NOT NULL," +
                    "FOREIGN KEY(program_name) REFERENCES programs(name) ON DELETE CASCADE" +
                    ");");

            // Program Assets Table
            stmt.execute("CREATE TABLE IF NOT EXISTS program_assets (" +
                    "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                    "program_name TEXT NOT NULL," +
                    "asset_pattern TEXT NOT NULL," +
                    "type TEXT," +
                    "is_in_scope INTEGER DEFAULT 1," +
                    "FOREIGN KEY(program_name) REFERENCES programs(name) ON DELETE CASCADE" +
                    ");");

            // Reward Categories Table
            stmt.execute("CREATE TABLE IF NOT EXISTS reward_categories (" +
                    "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                    "program_name TEXT NOT NULL," +
                    "severity TEXT NOT NULL," +
                    "reward_range TEXT NOT NULL," +
                    "FOREIGN KEY(program_name) REFERENCES programs(name) ON DELETE CASCADE" +
                    ");");

            // Target Playbooks Table
            stmt.execute("CREATE TABLE IF NOT EXISTS target_playbooks (" +
                    "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                    "target_id INTEGER NOT NULL," +
                    "playbook_name TEXT NOT NULL," +
                    "status TEXT NOT NULL," +
                    "created_at INTEGER NOT NULL," +
                    "FOREIGN KEY(target_id) REFERENCES targets(id) ON DELETE CASCADE" +
                    ");");

            // Target Playbook Steps Table
            stmt.execute("CREATE TABLE IF NOT EXISTS target_playbook_steps (" +
                    "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                    "target_playbook_id INTEGER NOT NULL," +
                    "step_number INTEGER NOT NULL," +
                    "step_name TEXT NOT NULL," +
                    "status TEXT NOT NULL," +
                    "notes TEXT," +
                    "FOREIGN KEY(target_playbook_id) REFERENCES target_playbooks(id) ON DELETE CASCADE" +
                    ");");

            // Observations Table
            stmt.execute("CREATE TABLE IF NOT EXISTS observations (" +
                    "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                    "project_id INTEGER NOT NULL," +
                    "target_id INTEGER NOT NULL," +
                    "description TEXT NOT NULL," +
                    "source TEXT NOT NULL," +
                    "confidence REAL DEFAULT 1.0," +
                    "created_at INTEGER NOT NULL," +
                    "FOREIGN KEY(project_id) REFERENCES projects(id) ON DELETE CASCADE," +
                    "FOREIGN KEY(target_id) REFERENCES targets(id) ON DELETE CASCADE" +
                    ");");

            // Coverage Table
            stmt.execute("CREATE TABLE IF NOT EXISTS coverage (" +
                    "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                    "project_id INTEGER NOT NULL," +
                    "target_id INTEGER NOT NULL," +
                    "playbook_name TEXT NOT NULL," +
                    "completed_steps INTEGER DEFAULT 0," +
                    "total_steps INTEGER DEFAULT 0," +
                    "coverage_percent REAL DEFAULT 0.0," +
                    "updated_at INTEGER NOT NULL," +
                    "FOREIGN KEY(project_id) REFERENCES projects(id) ON DELETE CASCADE," +
                    "FOREIGN KEY(target_id) REFERENCES targets(id) ON DELETE CASCADE," +
                    "UNIQUE(target_id, playbook_name)" +
                    ");");

            // Coverage Steps Table
            stmt.execute("CREATE TABLE IF NOT EXISTS coverage_steps (" +
                    "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                    "coverage_id INTEGER NOT NULL," +
                    "step_number INTEGER NOT NULL," +
                    "step_name TEXT NOT NULL," +
                    "status TEXT NOT NULL," +
                    "notes TEXT," +
                    "FOREIGN KEY(coverage_id) REFERENCES coverage(id) ON DELETE CASCADE," +
                    "UNIQUE(coverage_id, step_number)" +
                    ");");

            // Coverage Gaps Table
            stmt.execute("CREATE TABLE IF NOT EXISTS coverage_gaps (" +
                    "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                    "project_id INTEGER NOT NULL," +
                    "target_id INTEGER NOT NULL," +
                    "gap_description TEXT NOT NULL," +
                    "severity TEXT," +
                    "created_at INTEGER NOT NULL," +
                    "FOREIGN KEY(project_id) REFERENCES projects(id) ON DELETE CASCADE," +
                    "FOREIGN KEY(target_id) REFERENCES targets(id) ON DELETE CASCADE" +
                    ");");

            // Journal Table
            stmt.execute("CREATE TABLE IF NOT EXISTS journal (" +
                    "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                    "project_id INTEGER NOT NULL," +
                    "action_type TEXT NOT NULL," +
                    "description TEXT NOT NULL," +
                    "created_at INTEGER NOT NULL," +
                    "FOREIGN KEY(project_id) REFERENCES projects(id) ON DELETE CASCADE" +
                    ");");

            // Decisions Table
            stmt.execute("CREATE TABLE IF NOT EXISTS decisions (" +
                    "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                    "project_id INTEGER NOT NULL," +
                    "finding_id INTEGER," +
                    "decision_type TEXT NOT NULL," +
                    "rationale TEXT NOT NULL," +
                    "created_at INTEGER NOT NULL," +
                    "FOREIGN KEY(project_id) REFERENCES projects(id) ON DELETE CASCADE" +
                    ");");

            // Alter projects table to add program_name if missing
            try {
                stmt.execute("ALTER TABLE projects ADD COLUMN program_name TEXT;");
            } catch (Exception ignored) {
            }

            // Alter findings table to add state, confidence, evidence_count if missing
            try {
                stmt.execute("ALTER TABLE findings ADD COLUMN state TEXT DEFAULT 'HYPOTHESIS';");
            } catch (Exception ignored) {}
            try {
                stmt.execute("ALTER TABLE findings ADD COLUMN confidence REAL DEFAULT 0.05;");
            } catch (Exception ignored) {}
            try {
                stmt.execute("ALTER TABLE findings ADD COLUMN evidence_count INTEGER DEFAULT 0;");
            } catch (Exception ignored) {}

            // Alter knowledge table to add quality metrics if missing
            try {
                stmt.execute("ALTER TABLE knowledge ADD COLUMN accepted_reports INTEGER DEFAULT 0;");
            } catch (Exception ignored) {}
            try {
                stmt.execute("ALTER TABLE knowledge ADD COLUMN duplicates INTEGER DEFAULT 0;");
            } catch (Exception ignored) {}
            try {
                stmt.execute("ALTER TABLE knowledge ADD COLUMN informational_items INTEGER DEFAULT 0;");
            } catch (Exception ignored) {}
            try {
                stmt.execute("ALTER TABLE knowledge ADD COLUMN rewards REAL DEFAULT 0.0;");
            } catch (Exception ignored) {}
            try {
                stmt.execute("ALTER TABLE knowledge ADD COLUMN severity TEXT;");
            } catch (Exception ignored) {}

            // Seed a default user if database is new
            stmt.execute("INSERT OR IGNORE INTO users (id, username, role) VALUES (1, 'researcher', 'admin');");
        }
    }

    public Connection getConnection() throws Exception {
        if (connection == null || connection.isClosed()) {
            connection = DriverManager.getConnection(jdbcUrl);
        }
        return connection;
    }

    public String getDbFile() {
        return dbFile;
    }

    public void close() {
        try {
            if (connection != null && !connection.isClosed()) {
                connection.close();
            }
        } catch (Exception e) {
            System.err.println("Error closing database connection: " + e.getMessage());
        }
    }
}
