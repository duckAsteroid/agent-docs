# AGENTS.md

## What this repository is
- `agent-docs` is a Gradle multi-project repo for publishing, resolving, and serving **agent-ready documentation** for binary dependencies.
- The system is split into three runtime pieces: `agent-docs-publish-gradle-plugin`, `agent-docs-resolve-gradle-plugin`, and `agent-docs-mcp`.
- Keep the resolver and MCP components decoupled: the resolver writes a local index, and the MCP app reads only local files.

## Read first
- `README.md` for the high-level repo shape and the main developer commands.
- `agent-docs-project-proposal.md` for the architectural decisions that still drive the design.
- `agent-docs-spec/src/main/java/io/github/duckasteroid/agentdocs/spec/AgentDocsManifest.java` and `AgentDocsCoordinates.java` for the shared contract.

## Repository structure that matters
- `agent-docs-spec` owns the manifest and coordinate records used across all modules.
- `agent-docs-publish-gradle-plugin` packages docs from `src/agentDocs/topics` into a sidecar zip; its task fails if that directory is missing.
- `agent-docs-resolve-gradle-plugin` resolves the configured classpath and writes `build/agent-docs-resolver/index.json`.
- `agent-docs-mcp` is a tiny local reader; its default index path is `build/agent-docs-resolver/index.json`.
- `samples/producer-sample/src/agentDocs/topics/overview.md` is the current example of the doc-authoring layout.

## Current conventions
- Java 21 everywhere; Gradle Groovy build scripts; prefer records for small immutable contracts (`AgentDocsManifest`, `AgentDocsCoordinates`).
- Plugins use Gradle `convention(...)` defaults for their extension properties instead of hard-coding values.
- The resolver task builds JSON manually with `StringBuilder` today; don’t assume a JSON library is already in use.
- Generated output lives under `build/`; do not edit build artifacts directly.

## Developer workflow
- Full repo build: `./gradlew build`
- Run the consumer sample: `./gradlew :samples:consumer-sample:run`
- If you change the resolver plugin, verify the output path and index shape stay compatible with `agent-docs-mcp`.
- If you change the manifest or index schema, update `agent-docs-spec` first, then align the plugins and samples.

## Integration points to preserve
- Published docs are a sidecar artifact aligned with the library version, not a separately versioned product.
- The resolver should reuse the consumer project’s configured repositories and credentials; MCP should never reach out to remote repositories.
- Keep sample code simple and aligned with the proposal: producer docs under `src/agentDocs/topics/`, consumer code only depending on the compiled library API.

## When editing
- Prefer small, contract-preserving changes.
- Update the root `README.md` or proposal doc when you change a workflow, output path, or artifact contract.
- If you introduce a new default path or file name, make it visible in both the plugin code and the samples.
