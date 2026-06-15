# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Commands

```bash
# Full multi-project build
./gradlew build

# Run tests for a single module
./gradlew :agent-docs-resolve-gradle-plugin:test
./gradlew :agent-docs-publish-gradle-plugin:test

# Gradle version compatibility matrix
./gradlew testMatrix            # both 8.x and 9.x
./gradlew testMatrixGradle8     # Gradle 8.14.5 only
./gradlew testMatrixGradle9     # Gradle 9.5.1 only

# Version inspection (Axion Release plugin)
./gradlew currentVersion
```

Tests use Gradle TestKit and run against the configured Gradle version via the `agentDocs.test.gradleVersion` system property. Integration tests also require `agentDocs.repoRoot` pointing to the repo root.

## Architecture

This is a Gradle multi-project build with two independently publishable plugins and one integration test module:

- **`agent-docs-publish-gradle-plugin`** — Plugin ID `io.github.duckasteroid.agent-docs.publish`. Validates a library's `src/agent-docs/` docs root, then packages it into a sidecar zip (`build/agent-docs/<project>-agent-docs.zip`) with classifier `agent-docs`. When `maven-publish` is present, attaches the zip to every `MavenPublication` automatically.

- **`agent-docs-resolve-gradle-plugin`** — Plugin ID `io.github.duckasteroid.agent-docs`. For each direct dependency on the configured classpath (default `compileClasspath`), attempts to resolve `<group>:<artifact>:<version>:agent-docs@zip`, extracts the sidecar into `.agents/skills/<gav-skill-name>/`, rewrites the extracted `SKILL.md` frontmatter `name` to the rewritten folder name, writes an `.agent-docs` ownership marker, and removes stale marker-owned folders when dependencies are dropped.

- **`agent-docs-integration-tests`** — End-to-end TestKit tests that run both plugins together.

### Key design invariants

**Publish/resolve decoupling**: Publisher produces sidecars; resolver handles consumer-side extraction and skill generation. These concerns must remain independent.

**Skill naming**: GAV coordinates are normalized to `[a-z0-9-]`, no edge or consecutive hyphens, max 64 chars. Names longer than 64 chars are truncated with a deterministic SHA-256 hash suffix. This logic lives in `ModuleCoordinate.skillName()`.

**`name` frontmatter handling**: The publisher strips `name:` from packaged `SKILL.md` because resolver-generated GAV naming is authoritative. The resolver then writes the canonical `name:` on extraction. Do not preserve publisher `name` through the sidecar.

**Ownership markers**: The resolver writes `.agent-docs` into each managed skill directory. Stale cleanup only removes directories that carry this marker, never user-created folders.

**`@DisableCachingByDefault`**: `ResolveAgentDocsTask` explicitly disables Gradle build cache because it orchestrates filesystem state that isn't fully modeled as task outputs.

### Extension defaults (via `convention(...)`)

| Plugin | Property | Default |
|---|---|---|
| publish | `docsDirectory` | `src/agent-docs` |
| resolve | `configurationName` | `compileClasspath` |
| resolve | `skillsDirectory` | `<rootProject>/.agents/skills` |

### Versioning

Axion Release plugin derives a single version from Git tags (`v<semver>` format). All subprojects share the root version. Tag format: `v0.0.1`.

### Gradle configuration

`gradle.properties` enables `org.gradle.caching`, `org.gradle.parallel`, and `org.gradle.configuration-cache`. Avoid lazy-evaluation pitfalls (e.g., resolving configurations at configuration time) — the resolver plugin resolves sidecars eagerly during task configuration using lenient artifact views to stay compatible with configuration cache.

## Conventions

- Java 21, Gradle Groovy DSL throughout.
- Plugin extension properties use `convention(...)` for defaults, never hardcoded values in task registration.
- Validation rules in the publish plugin are composable (`AgentDocsValidationRule` implementations), individually identified by string IDs, and disableable via `agentDocs.disabledValidationRules` or the CLI property `-PagentDocs.disabledValidationRules=rule-id`.
- When changing output paths, artifact contracts, or skill folder layout, update both plugin READMEs and the root `README.md`.
