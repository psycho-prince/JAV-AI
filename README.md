# JavAI Research Edition v1.0

JavAI is an agentic AI assistant built in Java 17. It supports local LLM providers, conversation memory systems, database-backed storage, and dynamic plugin execution.

## Quick Start
To build and run JavAI locally:
```bash
mvn clean compile
mvn exec:java
```

## Global NPM Installation
You can also install the console and visual dashboard globally using the NPM package manager:
```bash
npm install -g @rhyugen/javai
javai
```
> [!NOTE]
> Global execution requires Node.js >= 16.0.0, Java 17+, and Maven on your system shell PATH.

## Initial Architecture Core
This bootstrapping stage instantiates the core engines:
- **Core Engine:** `Bootstrap`, `Main`, `JavAI`, `AgentEngine`
- **LLM Layer:** `LLMProvider`, `LLMRequest`, `LLMResponse`, `OpenAICompatibleProvider`
- **Memory Engine:** `MemoryEngine`, `MemoryStore`
- **Storage Layer:** `DatabaseManager`, `SQLiteManager`
- **User Interface:** `ConsoleUI`, `CommandParser`
