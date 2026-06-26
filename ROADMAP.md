# JavAI Autonomous Coding Agent Roadmap

## Current Architecture

JavAI is currently a Java 17, Maven-built security research agent. Its useful foundations are:

- Persistent SQLite state for projects, conversations, targets, scans, assets, findings, evidence, observations, reports, journals, and decisions.
- A model-router abstraction with OpenAI-compatible and Qwen providers.
- Context assembly that injects active project state, program rules, findings, knowledge, and retrieved observations into chat prompts.
- Pentest plugins for reconnaissance commands with simulation fallbacks.
- A skeptical validation layer that prevents unsupported high-severity claims from becoming reportable findings.
- A console UI and lightweight HTTP dashboard.
- A `CoderEngine` that can solve prompt-based coding challenges and audit source files for crypto/PQC readiness.

The gap to a Codex/Claude Code-class coding agent is not model access alone. JavAI needs a first-class workspace model, tool protocol, planning loop, patch pipeline, execution sandbox, and regression gate.

## Target Architecture

JavAI should evolve into a bounded autonomous engineering loop:

1. Understand repository state.
2. Build a task plan.
3. Retrieve relevant files and symbols.
4. Edit through structured patch operations.
5. Run allowlisted verification commands.
6. Inspect diffs and failures.
7. Iterate until the change is complete or blocked.
8. Record decisions and final evidence in the project journal.

Core subsystems:

- `WorkspaceInspector`: maps source, tests, docs, generated state, and build system.
- `ToolRegistry`: exposes safe read, search, patch, shell, test, and VCS operations.
- `Planner`: decomposes user requests into actionable steps with explicit acceptance criteria.
- `CodeContextRetriever`: combines text search, file summaries, dependency manifests, and symbol maps.
- `PatchEngine`: applies unified diffs and rejects writes outside the active workspace.
- `BuildVerifier`: runs detected allowlisted verification commands with timeouts and captured output.
- `DiffReviewer`: reviews changed files for regressions, accidental broad edits, secrets, and missing tests.
- `AgentLoop`: coordinates plan, act, observe, revise, and final response.

## Incremental Plan

### Phase 1: Reliable Foundation

- Keep `mvn test` green after every step.
- Fix known schema/API mismatches in the dashboard and memory layer.
- Add workspace inspection and verification-command detection.
- Add CLI access through `/coder inspect [path]` and `/coder verify [path]`.
- Add tests for project/program state and workspace inspection.

Status: implemented in this increment.

### Phase 2: Tool Safety and Patch Editing

- Add a `WorkspacePolicy` that canonicalizes paths and rejects writes outside the repo.
- Add file read/search/list tools as Java services, not raw shell strings.
- Add a patch applier with validation, dry-run mode, and clear failure diagnostics.
- Record every tool action into the journal with timestamp, command, exit code, and files touched.
- Add tests for path traversal, generated directory exclusion, failed patch rollback, and binary-file rejection.

### Phase 3: Planning and Task State

- Introduce a durable `coding_tasks` table with task status, plan items, changed files, verification results, and blocker notes.
- Add `/coder plan <goal>` to create an editable plan before execution.
- Add `/coder status` to show current task, changed files, last verification, and next action.
- Add prompt templates that require assumptions, acceptance criteria, and regression checks.

### Phase 4: Autonomous Code Loop

- Add `/coder work <goal>` for bounded autonomous execution.
- Loop steps: inspect, retrieve, plan, edit, verify, review diff, repair.
- Stop conditions: success, repeated same failure, unsafe requested action, missing dependency, or timeout.
- Require verification before reporting success unless no verification command exists.
- Persist every attempt and test result.

### Phase 5: Deep Code Intelligence

- Add Java symbol indexing using JavaParser or the JDK compiler API.
- Build import/class/method maps for targeted context retrieval.
- Add dependency-aware test selection before falling back to full test runs.
- Add semantic file summaries and refresh them after edits.
- Add retrieval over local docs, README files, build files, and past task journals.

### Phase 6: Multi-Agent Review

- Reuse the existing council pattern for code work:
  - Implementer proposes changes.
  - Reviewer searches for regressions and missing tests.
  - Verifier interprets build/test output.
  - Moderator decides continue, repair, or stop.
- Keep final authority in deterministic checks where possible.

### Phase 7: Provider and Protocol Upgrades

- Add tool-call capable provider contracts.
- Support structured responses for plans, patches, and test interpretation.
- Add streaming output and cancellation for long-running tasks.
- Support model-specific capabilities without leaking them into core orchestration.

### Phase 8: Product Hardening

- Add a non-interactive CLI mode for CI and scripted tasks.
- Add dashboard views for coding tasks, diffs, verification history, and blockers.
- Add configuration for allowed commands, max runtime, writable roots, and ignored paths.
- Add integration tests that create temporary repos and verify end-to-end edit/test loops.

## Engineering Constraints

- Never edit outside the active workspace.
- Never run arbitrary shell generated by the model.
- Prefer structured Java services over command strings.
- Treat generated workspace artifacts and database files as state, not source.
- Require tests or a documented reason tests cannot run.
- Preserve the existing security research capabilities while adding coding-agent behavior.

## Near-Term Backlog

1. Add `WorkspacePolicy` path validation.
2. Add `PatchEngine` with unit tests.
3. Add `coding_tasks` and `coding_task_events` tables.
4. Add `/coder plan` with durable acceptance criteria.
5. Add `/coder diff` backed by `git diff --` when Git is available.
6. Add `/coder work` as a one-iteration loop before allowing multi-iteration autonomy.
7. Add dashboard API endpoints for coding task state.
