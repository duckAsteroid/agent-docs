# AGENTS.md

## What this repository is
- `agent-docs` is a Gradle multi-project repo for publishing, resolving, and serving **agent-ready documentation** for binary dependencies.
- The system is split into three runtime pieces: `agent-docs-publish-gradle-plugin`, `agent-docs-resolve-gradle-plugin`, and `agent-docs-mcp`.
- Keep the resolver and MCP components decoupled: the resolver resolves and caches sidecars locally, and the MCP app reads only local files.

## Read first
- `README.md` for the high-level repo shape and the main developer commands.
- `agent-docs-project-proposal.md` for the architectural decisions that still drive the design.
- `agent-docs-publish-gradle-plugin/README.md` for the publish-side archive contract and required docs layout.
- `agent-docs-mcp/README.md` for MCP runtime path resolution and server behavior.

## Repository structure that matters
- `agent-docs-publish-gradle-plugin` packages docs from `src/agentDocs` into a sidecar zip and requires exactly one `agents.md` entrypoint (case-insensitive).
- `agent-docs-resolve-gradle-plugin` resolves the configured classpath and caches `agent-docs` sidecars in a local Maven-style repository.
- `agent-docs-mcp` serves docs from that local repository over MCP (STDIO), including `agentdocs:///{groupId}/{artifactId}/{version}/{path}` resource reads and the `get_agent_docs` tool.

## Current conventions
- Java 21 everywhere; Gradle Groovy build scripts.
- Plugins use Gradle `convention(...)` defaults for their extension properties instead of hard-coding values.
- Local repository override path is shared by resolver and MCP:
  - JVM property: `-DagentDocs.localRepository=...`
  - Environment variable: `AGENT_DOCS_LOCAL_REPOSITORY`
  - Default: `~/.agent-docs/repository`
- Generated output lives under `build/`; do not edit build artifacts directly.

## Developer workflow
- Full repo build: `./gradlew build`
- Run MCP locally: `./gradlew :agent-docs-mcp:run`
- If you change the resolver plugin, verify local repository layout stays compatible with `agent-docs-mcp` resource and tool lookup.
- If you change sidecar structure or entrypoint expectations, align publish plugin behavior with MCP document lookup expectations.

## Integration points to preserve
- Published docs are a sidecar artifact aligned with the library version, not a separately versioned product.
- The resolver should reuse the consumer project’s configured repositories and credentials.
- MCP should never reach out to remote repositories; it only reads local files from the configured local repository.
- Docs entrypoint normalization matters: source may use `AGENTS.md`/`agents.md`, but packaged entrypoint is expected as `agents.md`.

## When editing
- Prefer small, contract-preserving changes.
- Update the root `README.md` or proposal doc when you change a workflow, output path, or artifact contract.
- If you introduce a new default path or file name, make it visible in plugin code, module README files, and MCP-facing documentation.
