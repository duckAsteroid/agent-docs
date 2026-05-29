# agent-docs

This repository contains a toolchain for publishing, resolving, and serving **agent-ready documentation** for binary dependencies. It introduces an `agent-docs` sidecar artifact that travels with the same module/version as a library dependency, is resolved through standard Gradle/Maven repositories, cached locally by a Gradle resolver plugin, and exposed to coding agents through a separate MCP server that reads only from the local cache.

## Quickstart

If you are an AI coding agent, read `AGENTS.md` first for the repo-specific workflow and conventions.

```bash
./gradlew build
./gradlew :agent-docs-mcp:run
```

## Versioning and releases

This repository uses the Axion Release plugin to derive a single repo-wide version from Git tags.

- Tag format: `v<semver>` (for example `v0.0.1`)
- The computed root version is reused by all subprojects
- Check the resolved version with `./gradlew currentVersion`

## CI and release workflows

GitHub Actions workflows live under `.github/workflows`.

- `build.yml`: runs `clean check` on pushes and pull requests to `main`
- `publish.yml`: on `v*` tags, validates publish secrets and runs `clean check publish publishPlugins`
- `native-release.yml`: on `v*` tags (or manual dispatch), builds Graal native binaries for:
  - Linux x64 (`ubuntu-latest`)
  - Windows x64 (`windows-latest`)
  - macOS x64 (`macos-13`)
  - macOS arm64 (`macos-14`)

For tag builds, `native-release.yml` also creates/updates the GitHub Release and uploads platform-specific binaries from `agent-docs-mcp` as assets named like:

- `agent-docs-mcp-v1.2.3-linux-x64`
- `agent-docs-mcp-v1.2.3-windows-x64.exe`
- `agent-docs-mcp-v1.2.3-macos-x64`
- `agent-docs-mcp-v1.2.3-macos-arm64`

## Modules

- `agent-docs-publish-gradle-plugin` - starter Gradle plugin for packaging curated docs into an `agent-docs` zip
- `agent-docs-resolve-gradle-plugin` - starter Gradle plugin for inspecting a resolved classpath, attempting to resolve `agent-docs` sidecars for direct `implementation`/`api` dependencies, and caching found sidecars in a local Maven-style repository (`~/.agent-docs/repository` by default, override via `-DagentDocs.localRepository=...` or `AGENT_DOCS_LOCAL_REPOSITORY`)
- `agent-docs-mcp` - minimal STDIO MCP server that serves markdown via `agentdocs:///{groupId}/{artifactId}/{version}/{path}` and `get_agent_docs`, reading only from the local agent-docs repository
- `docs` - architecture and repository notes

## License

This project is licensed under the MIT License. See `LICENSE`.


The first scaffold keeps the resolver and MCP modules decoupled and mirrors the repository layout from the project proposal so implementation can grow in-place.

