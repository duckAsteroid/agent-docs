# AGENTS.md

## What this repository is
- `agent-docs` is a Gradle multi-project repo for publishing and resolving **agent-ready documentation** for binary dependencies.
- The system is split into two runtime pieces: `agent-docs-publish-gradle-plugin` and `agent-docs-resolve-gradle-plugin`.
- Keep publish and resolve concerns decoupled: publish produces sidecars and resolve handles consumer-side retrieval and local skill generation.

## Read first
- `README.md` for the high-level repo shape and the main developer commands.
- `agent-docs-publish-gradle-plugin/README.md` for the publish-side archive contract and required docs layout.
- `agent-docs-resolve-gradle-plugin/README.md` for resolver extension options and per-dependency skill generation output.

## Repository structure that matters
- `agent-docs-publish-gradle-plugin` validates a `src/agent-docs` docs root (exactly one `SKILL.md` entrypoint, case-insensitive, with `description` frontmatter) and distributes it embedded in the project's own jar or as a sidecar zip, per `specification/java-conventions.md`. Full behavior: `agent-docs-publish-gradle-plugin/README.md`.
- `agent-docs-resolve-gradle-plugin` reads the `Agent-Docs` manifest attribute from each dependency's own resolved jar and, only when present, extracts docs under `.agents/skills/<skill-name>/` (shortest safe name per run, escalating only on collision) using skill-spec layout, rewrites extracted `SKILL.md` frontmatter `name` to match each rewritten folder, writes `.agent-docs` ownership markers for managed folders, and removes stale marker-owned dependency skill folders. Full behavior: `agent-docs-resolve-gradle-plugin/README.md`.

## Current conventions
- Java 21 everywhere; Gradle Groovy build scripts.
- Plugins use Gradle `convention(...)` defaults for their extension properties instead of hard-coding values.
- Resolver-generated skill/resource output lives under `.agent/skills/`; do not hand-edit generated files.

## Developer workflow
- Full repo build: `./gradlew build`
- Run resolver plugin tests: `./gradlew :agent-docs-resolve-gradle-plugin:test`
- If you change the resolver plugin, verify both skill output contracts (`.agent/skills/...`) and sidecar extraction layout remain stable.
- If you change sidecar structure or entrypoint expectations, align publish plugin behavior with resolver extraction and skill lookup expectations.

## Integration points to preserve
- Published docs are a sidecar artifact aligned with the library version, not a separately versioned product.
- The resolver should reuse the consumer project’s configured repositories and credentials.
- Docs entrypoint normalization matters: source may use `SKILL.md`/`skill.md`, but packaged entrypoint is expected as `SKILL.md`.

## When editing
- Prefer small, contract-preserving changes.
- Update the root `README.md` or proposal doc when you change a workflow, output path, or artifact contract.
- If you introduce a new default path or file name, make it visible in plugin code and module README files.
