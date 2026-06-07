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
- `agent-docs-publish-gradle-plugin` packages docs from `src/agent-docs` into a sidecar zip and requires exactly one `SKILL.md` entrypoint (case-insensitive) with Agent Skills frontmatter (`description`; `name` is optional input and omitted from the packaged sidecar with a warning).
- `agent-docs-resolve-gradle-plugin` resolves configured dependencies, stores sidecars under `.agent/skills/<gav-skill-name>/agent-docs.zip`, extracts docs under `.agent/skills/<gav-skill-name>/` using skill-spec layout (`SKILL.md`, `references/`, `assets/`, `scripts/`), rewrites extracted `SKILL.md` frontmatter `name` to match each rewritten folder, writes `.agent-docs` ownership markers for managed folders, removes stale marker-owned dependency skill folders, and generates one dependency skill per resolved sidecar at `.agent/skills/agent-docs-dependencies/<gav-skill-name>/SKILL.md`.

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
