# agent-docs

This repository contains a toolchain for publishing, resolving, and consuming **agent-ready documentation** for binary dependencies.

It introduces an `agent-docs` sidecar artifact that travels with the same module/version as a library dependency, is resolved through standard Gradle/Maven repositories, cached locally by a Gradle resolver plugin, and exposed as local agent skills/resources.

- Local agent skills and resources under `.agents/`

## Quickstart

If you are an AI coding agent, read `AGENTS.md` first for the repo-specific workflow and conventions.

```bash
./gradlew build
./gradlew :agent-docs-resolve-gradle-plugin:test
```

## Versioning and releases

This repository uses the Axion Release plugin to derive a single repo-wide version from Git tags.

- Tag format: `v<semver>` (for example `v0.0.1`)
- The computed root version is reused by all subprojects
- Check the resolved version with `./gradlew currentVersion`

## Modules

- `agent-docs-publish-gradle-plugin` - starter Gradle plugin for packaging curated docs into an `agent-docs` zip
- `agent-docs-resolve-gradle-plugin` - resolver plugin that resolves sidecars for direct dependencies, caches sidecars in a local Maven-style repository (`~/.agent-docs/repository` by default, override via `-DagentDocs.localRepository=...` or `AGENT_DOCS_LOCAL_REPOSITORY`), extracts docs by GAV under `.agents/resources/agent-docs`, and generates skills in one of three modes:
  - `SINGLE_INDEX` -> `.agents/skills/agent-docs.md`
  - `PER_DEPENDENCY` -> `.agents/skills/agent-docs-dependencies/...`
  - `AUTO_THRESHOLD` -> per-dependency when resolved sidecars are `<= N`, otherwise single-index
- `docs` - architecture and repository notes

## Resolver mode guide

Use this quick table when configuring `agentDocsResolve.skillGenerationMode`:

| Mode | When to use | Output |
| --- | --- | --- |
| `SINGLE_INDEX` | Large dependency sets where one stable entry skill is preferred | `.agents/skills/agent-docs.md` |
| `PER_DEPENDENCY` | Smaller dependency sets where dependency-scoped skills are preferred | `.agents/skills/agent-docs-dependencies/...` |
| `AUTO_THRESHOLD` | Mixed projects where mode should adapt to resolved sidecar count | Per-dependency when `count <= perDependencySkillThreshold`, otherwise single-index |

Notes:

- `AUTO_THRESHOLD` still applies cleanup for the non-selected model each run.
- Threshold comparison is inclusive for per-dependency mode (`<= N`).

## License

This project is licensed under the MIT License. See `LICENSE`.

The implementation keeps publish and resolve concerns decoupled: publish generates sidecars and resolve handles consumer-side resolution, extraction, and skill generation.

