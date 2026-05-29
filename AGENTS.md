# AGENTS.md

## What this repository is
- `agent-docs` is a Gradle multi-project repo for publishing and resolving **agent-ready documentation** for binary dependencies.
- The system is split into two runtime pieces: `agent-docs-publish-gradle-plugin` and `agent-docs-resolve-gradle-plugin`.
- Keep publish and resolve concerns decoupled: publish produces sidecars and resolve handles consumer-side retrieval and local skill generation.

## Read first
- `README.md` for the high-level repo shape and the main developer commands.
- `agent-docs-project-proposal.md` for the architectural decisions that still drive the design.
- `agent-docs-publish-gradle-plugin/README.md` for the publish-side archive contract and required docs layout.
- `agent-docs-resolve-gradle-plugin/README.md` for resolver extension options and skill-generation modes.

## Repository structure that matters
- `agent-docs-publish-gradle-plugin` packages docs from `src/agentDocs` into a sidecar zip and requires exactly one `agents.md` entrypoint (case-insensitive).
- `agent-docs-resolve-gradle-plugin` resolves configured dependencies, caches sidecars in a local Maven-style repository, extracts docs by GAV under `.agents/resources/agent-docs`, and generates skills in `SINGLE_INDEX`, `PER_DEPENDENCY`, or `AUTO_THRESHOLD` mode.

## Current conventions
- Java 21 everywhere; Gradle Groovy build scripts.
- Plugins use Gradle `convention(...)` defaults for their extension properties instead of hard-coding values.
- Local repository override path for resolver sidecar cache:
  - JVM property: `-DagentDocs.localRepository=...`
  - Environment variable: `AGENT_DOCS_LOCAL_REPOSITORY`
  - Default: `~/.agent-docs/repository`
- Resolver-generated skill/resource output lives under `.agents/`; do not hand-edit generated files.

## Developer workflow
- Full repo build: `./gradlew build`
- Run resolver plugin tests: `./gradlew :agent-docs-resolve-gradle-plugin:test`
- If you change the resolver plugin, verify both skill output contracts (`.agents/...`) and local repository layout remain stable.
- If you change sidecar structure or entrypoint expectations, align publish plugin behavior with resolver extraction and skill lookup expectations.

## Integration points to preserve
- Published docs are a sidecar artifact aligned with the library version, not a separately versioned product.
- The resolver should reuse the consumer project’s configured repositories and credentials.
- Docs entrypoint normalization matters: source may use `AGENTS.md`/`agents.md`, but packaged entrypoint is expected as `agents.md`.

## When editing
- Prefer small, contract-preserving changes.
- Update the root `README.md` or proposal doc when you change a workflow, output path, or artifact contract.
- If you introduce a new default path or file name, make it visible in plugin code and module README files.
