# agent-docs

This repository defines and implements **agent docs**: a tool-agnostic convention for
distributing agent-ready documentation alongside JAR dependencies, plus a Gradle
toolchain (publish + resolve plugins) that implements it.

The full convention lives in [`specification/core-conventions.md`](./specification/core-conventions.md)
(language-agnostic) and [`specification/java-conventions.md`](./specification/java-conventions.md)
(the JAR/manifest specifics) — **read those first if you want the authoritative spec**;
this README describes the Gradle tooling built on top of it. The convention is just a
manifest attribute plus a docs directory layout — it doesn't require these plugins at
all, they just automate it.

In short: a JAR advertises its agent docs with a single `Agent-Docs` manifest attribute,
either `classpath[:path]` (docs embedded in the same jar) or `maven[:group:artifact:version]`
(docs in a separate sidecar zip published alongside it). A consumer reads that attribute
off each direct dependency's own resolved jar and, only when present, extracts the docs
into project-local skill folders under `.agents/skills/`.

Note: this repository currently covers regular Maven/JAR dependencies only. Agent docs
for Gradle plugins (applied via the `plugins {}` block) is a deferred, not-yet-implemented
extension of the same convention.

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

Dependencies whose jar carries an `Agent-Docs` manifest attribute get their docs
extracted into `.agents/skills/<skill-name>/`; dependencies without it are skipped
entirely. See
[`agent-docs-resolve-gradle-plugin/README.md`](./agent-docs-resolve-gradle-plugin/README.md)
for the full resolution mechanics and output layout.

If you also maintain libraries and want to publish your own agent docs, see
[`agent-docs-publish-gradle-plugin/README.md`](./agent-docs-publish-gradle-plugin/README.md).

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

- [`agent-docs-publish-gradle-plugin`](./agent-docs-publish-gradle-plugin/README.md) - validates and distributes a library's agent docs, embedded in its own jar or as a sidecar zip.
- [`agent-docs-resolve-gradle-plugin`](./agent-docs-resolve-gradle-plugin/README.md) - discovers and extracts agent docs for a consuming project's dependencies into local skill folders.
- `agent-docs-integration-tests` - end-to-end TestKit tests that run both plugins together.

See each module's own README for its full behavior, extension options, and output layout. Agent docs for Gradle plugins (applied via `plugins {}`, not depended on directly) is a deferred extension of this same convention — not yet implemented.

## License

This project is licensed under the MIT License. See `LICENSE`.

The implementation keeps publish and resolve concerns decoupled: publish generates sidecars and resolve handles consumer-side resolution, extraction, and skill generation.
