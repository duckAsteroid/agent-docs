# agent-docs

This repository contains a toolchain for publishing, resolving, and consuming **agent-ready documentation** for binary dependencies.

It introduces an `agent-docs` sidecar artifact that travels with the same module/version as a library dependency, is resolved through standard Gradle/Maven repositories, copied into project-local skill folders by a Gradle resolver plugin, and exposed as local agent skills/resources.

- Local agent skills and resources under root `.agents/skills/`

## Quickstart

If you are an AI coding agent, read `AGENTS.md` first for the repo-specific workflow and conventions.

Most users will **resolve** docs for dependencies in a consumer project.

Add the resolver plugin and a dependency:

```groovy
plugins {
  id 'java'
  id 'io.github.duckasteroid.agent-docs' version '<version>'
}

repositories {
  mavenCentral()
}

dependencies {
  implementation 'com.acme:weather-core:1.4.0'
}
```

Resolve docs sidecars and generate local agent files:

```bash
./gradlew resolveAgentDocs
```

In this fictional example, the resolver attempts to fetch `com.acme:weather-core:1.4.0:agent-docs@zip` and extracts docs into:

```text
.agents/skills/com.acme__weather-core__1.4.0/
```

This extracted skill folder is expected to mirror the skill spec layout (`SKILL.md`, `references/`, `assets/`, `scripts/`) and includes a marker file at `.agent-docs`.

The resolver also generates local LLM-agent-readable skills under root `.agents/skills/`.

If you also maintain libraries and want to publish your own sidecar docs, see [publisher.md](./publisher.md).

### Contributor commands

```bash
./gradlew build
./gradlew :agent-docs-resolve-gradle-plugin:test
./gradlew testMatrix
./gradlew testMatrixGradle8
./gradlew testMatrixGradle9
```

## Versioning and releases

This repository uses the Axion Release plugin to derive a single repo-wide version from Git tags.

- Tag format: `v<semver>` (for example `v0.0.1`)
- The computed root version is reused by all subprojects
- Check the resolved version with `./gradlew currentVersion`

## Modules

- `agent-docs-publish-gradle-plugin` - starter Gradle plugin for packaging curated docs into an `agent-docs` zip
- `agent-docs-resolve-gradle-plugin` - resolver plugin that resolves sidecars for direct dependencies, extracts docs under root `.agents/skills/<gav-skill-name>/` (skill-spec layout), overwrites extracted `SKILL.md` frontmatter `name` to match each rewritten folder name, writes `.agent-docs` marker files for managed skill directories, and removes stale marker-owned dependency skill folders.

Notes:

- Dependency folder names use a resolver rewrite strategy that enforces Agent Skills name constraints (`[a-z0-9-]`, no edge/consecutive hyphens, max 64 chars) and appends a hash suffix when truncation is needed.

## License

This project is licensed under the MIT License. See `LICENSE`.

The implementation keeps publish and resolve concerns decoupled: publish generates sidecars and resolve handles consumer-side resolution, extraction, and skill generation.
