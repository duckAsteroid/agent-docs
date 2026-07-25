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

- **`agent-docs-publish-gradle-plugin`** — Plugin ID `io.github.duckasteroid.agent-docs.publish`. Validates a library's `src/agent-docs/` docs root, then distributes it per `agentDocs.distribution`: `SIDECAR` (default) packages it into a zip (`build/agent-docs/<project>-agent-docs.zip`) with classifier `agent-docs`, attached to every `MavenPublication` when `maven-publish` is present, and stamps the main `jar` task's manifest with `Agent-Docs: maven:<group>:<artifact>:<version>`; `EMBEDDED` instead copies the docs into the project's own jar under `agent-docs/` (no separate artifact) and stamps the manifest with `Agent-Docs: classpath`.

- **`agent-docs-resolve-gradle-plugin`** — Plugin ID `io.github.duckasteroid.agent-docs`. For each direct dependency on the configured classpath (default `compileClasspath`), reads the `Agent-Docs` manifest attribute from that dependency's own resolved jar. A dependency with no attribute is skipped entirely — no further resolution attempt of any kind. `Agent-Docs: classpath[:path]` extracts the docs bundle directly from that same jar; `Agent-Docs: maven[:group:artifact:version]` resolves a separate `agent-docs@zip` sidecar. Either way, the bundle is extracted into `.agents/skills/<skill-name>/` (see "Skill naming" below for how `<skill-name>` is chosen), the extracted `SKILL.md` frontmatter `name` is rewritten to the folder name, an `.agent-docs` ownership marker is written, and stale marker-owned folders are removed when dependencies are dropped. When `includeSources = true`, also resolves `<group>:<artifact>:<version>:sources@jar` and unpacks it into `src/` inside the skill folder; injects `metadata.sources: src/` or `metadata.sources: none` into the `SKILL.md` frontmatter accordingly. **Does not yet discover docs for Gradle plugins applied via `plugins {}`** — only regular dependencies; that's a deferred extension of the same convention.

- **`agent-docs-integration-tests`** — End-to-end TestKit tests that run both plugins together.

### The `Agent-Docs` convention

The authoritative, tool-agnostic specification lives in [`specification/core-conventions.md`](./specification/core-conventions.md) (language-agnostic) and [`specification/java-conventions.md`](./specification/java-conventions.md) (the JAR/manifest specifics) — **read those before changing publish/resolve manifest behavior**. In short: a single `Agent-Docs` manifest attribute (`classpath[:path]` or `maven[:group:artifact:version]`) gates all discovery; it requires neither of these plugins to produce or consume — they're validation/automation on top of a plain manifest convention.

### Key design invariants

**Publish/resolve decoupling**: Publisher produces sidecars/embeds docs; resolver handles consumer-side manifest-gated discovery and skill generation. These concerns must remain independent.

**Manifest-gated discovery, not speculative resolution**: The resolver reads the `Agent-Docs` attribute off each direct dependency's already-resolved jar before doing anything else. A dependency without the attribute triggers zero further resolution attempts — no speculative `:agent-docs@zip` lookups the way earlier versions of this resolver made for every dependency.

**Skill naming**: assigned per run in tiers, shortest-safe-first — artifact name alone, then `group-artifact` if that collides with another dependency in the same run, then the full GAV (`ModuleCoordinate.skillName()`) if `group-artifact` also collides. Each tier is normalized to `[a-z0-9-]`, no edge or consecutive hyphens, max 64 chars with a deterministic SHA-256 hash suffix when truncated. Collision detection across the full candidate set lives in `SkillNameAssigner`; per-tier candidate keys live on `ModuleCoordinate`. See `agent-docs-resolve-gradle-plugin/README.md`'s "Skill naming" section for the full algorithm.

**`name` frontmatter handling**: The publisher strips `name:` from packaged/embedded `SKILL.md` because resolver-generated GAV naming is authoritative. The resolver then writes the canonical `name:` on extraction. Do not preserve publisher `name` through the sidecar or embedded copy.

**`metadata` frontmatter handling**: The resolver strips any existing `metadata:` block before rewriting, then injects its own `metadata.sources` field when `includeSources` is enabled. Upstream `metadata` is intentionally discarded to keep resolver-generated values authoritative. The three possible values for `metadata.sources` are `src/` (sources extracted), `none` (sources unavailable), or the field is absent (feature not enabled).

**Ownership markers**: The resolver writes `.agent-docs` into each managed skill directory. Stale cleanup only removes directories that carry this marker, never user-created folders.

**`@DisableCachingByDefault`**: `ResolveAgentDocsTask` explicitly disables Gradle build cache because it orchestrates filesystem state that isn't fully modeled as task outputs.

### Extension defaults (via `convention(...)`)

| Plugin | Property | Default |
|---|---|---|
| publish | `docsDirectory` | `src/agent-docs` |
| publish | `distribution` | `SIDECAR` |
| resolve | `configurationName` | `compileClasspath` |
| resolve | `skillsDirectory` | `<rootProject>/.agents/skills` |
| resolve | `includeSources` | `false` |

### Versioning

Axion Release plugin derives a single version from Git tags (`v<semver>` format). All subprojects share the root version. Tag format: `v0.0.1`.

### Gradle configuration

`gradle.properties` enables `org.gradle.caching`, `org.gradle.parallel`, and `org.gradle.configuration-cache`. Avoid lazy-evaluation pitfalls (e.g., resolving configurations at configuration time) — the resolver plugin resolves sidecars eagerly during task configuration using lenient artifact views to stay compatible with configuration cache.

## Conventions

- Java 21, Gradle Groovy DSL throughout.
- Plugin extension properties use `convention(...)` for defaults, never hardcoded values in task registration.
- Validation rules in the publish plugin are composable (`AgentDocsValidationRule` implementations), individually identified by string IDs, and disableable via `agentDocs.disabledValidationRules` or the CLI property `-PagentDocs.disabledValidationRules=rule-id`.
- When changing output paths, artifact contracts, or skill folder layout, update both plugin READMEs and the root `README.md`.
