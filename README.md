# agent-docs

This repository contains a toolchain for publishing, resolving, and serving **agent-ready documentation** for binary dependencies. It introduces an `agent-docs` sidecar artifact that travels with the same module/version as a library dependency, is resolved through standard Gradle/Maven repositories, cached locally by a Gradle resolver plugin, and exposed to coding agents through a separate MCP server that reads only from the local cache.

## Quickstart

If you are an AI coding agent, read `AGENTS.md` first for the repo-specific workflow and conventions.

```bash
./gradlew build
./gradlew :samples:consumer-sample:run
```

## Versioning and releases

This repository uses the Axion Release plugin to derive a single repo-wide version from Git tags.

- Tag format: `v<semver>` (for example `v0.0.1`)
- The computed root version is reused by all subprojects
- Check the resolved version with `./gradlew currentVersion`

## Modules

- `agent-docs-spec` - shared contract types for manifests and cache/index metadata
- `agent-docs-publish-gradle-plugin` - starter Gradle plugin for packaging curated docs into an `agent-docs` zip
- `agent-docs-resolve-gradle-plugin` - starter Gradle plugin for inspecting a resolved classpath, attempting to resolve `agent-docs` sidecars for direct `implementation`/`api` dependencies, caching found sidecars in a local Maven-style repository (`~/.agent-docs/repository` by default, override via `-DagentDocs.localRepository=...` or `AGENT_DOCS_LOCAL_REPOSITORY`), and writing a local index scaffold
- `agent-docs-mcp` - minimal application entry point for reading the local resolver output
- `samples/producer-sample` - sample library producer
- `samples/consumer-sample` - sample consumer application
- `docs` - architecture and repository notes


The first scaffold keeps the resolver and MCP modules decoupled and mirrors the repository layout from the project proposal so implementation can grow in-place.

