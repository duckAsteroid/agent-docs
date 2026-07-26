# Agent Docs Resolve Plugin

This plugin reads the `Agent-Docs` manifest attribute (see [`specification/core-conventions.md`](../specification/core-conventions.md) and [`specification/java-conventions.md`](../specification/java-conventions.md) at the repo root) from direct dependencies' own resolved jars — and from the jars of Gradle plugins applied via `plugins {}` — and extracts their agent docs for local agent use.

## What It Does

- Adds a `resolveAgentDocs` task.
- Inspects direct dependencies from the configured classpath (default: `compileClasspath`).
- For each dependency, reads the `Agent-Docs` attribute from its own resolved jar's manifest. A dependency with no attribute is skipped entirely — no further resolution of any kind is attempted for it.
- `Agent-Docs: classpath[:path]` — extracts the docs bundle directly from that same jar, at the default path `agent-docs/` or the declared custom path. No network access needed.
- `Agent-Docs: maven[:group:artifact:version]` — resolves a separate `<group>:<artifact>:<version>:agent-docs@zip` sidecar (at the dependency's own coordinates, or the explicitly declared ones) and extracts it.
- Extracts the resulting bundle to `.agents/skills/<skill-name>/` (for example `SKILL.md`, `references/`, `assets/`, `scripts/`).
- Assigns each dependency the shortest safe skill name (see "Skill naming" below), preserving readability and adding hash suffixes when needed for uniqueness under 64 chars.
- Rewrites each extracted dependency `SKILL.md` frontmatter: `name` becomes the rewritten folder name; `description` is prefixed with a generated sentence identifying the library (see "Description prefix" below); `metadata.group`, `metadata.artifact`, and `metadata.version` record the resolved GAV. Only these specific fields are overwritten — any other upstream frontmatter, including unrelated `metadata` keys, passes through untouched.
- Writes an ownership marker file at `.agents/skills/<skill-name>/.agent-docs`.
- Removes stale, marker-owned dependency skill folders when those dependencies are no longer in the project.

### Gradle plugins applied via `plugins {}`

The plugin also discovers agent docs for Gradle plugins applied via the `plugins {}` block — not just regular dependencies. Since a plugin jar isn't resolved onto a project dependency configuration the way a regular dependency is, there's no Maven GAV to read off it; instead, each applied plugin's own class reveals the jar it was loaded from via its classloader, and that jar is inspected exactly like a dependency's jar would be:

- Only the `classpath` scheme is meaningful for plugins (the only distribution mode `agent-docs.publish` supports for `java-gradle-plugin` projects — see that plugin's README). A plugin jar declaring `maven` is skipped with a warning, since there's no consumer-side resolution path for a sidecar here.
- The plugin id itself — recovered from the same jar's `META-INF/gradle-plugins/<id>.properties` descriptor (the standard mechanism `java-gradle-plugin` generates and Gradle itself uses to resolve `id '...'` to an implementation class) — stands in for the GAV a regular dependency would have. If a jar carries an `Agent-Docs` declaration but no matching descriptor can be found, it's skipped with an info-level log (not a warning), since it isn't actionable by the consumer.
- A jar can declare more than one plugin id (a shared "conventions" plugin, for example). Each applied plugin instance is resolved to its own id independently, and its bundle is always read from `<declared-path>/<pluginId>/` inside the jar — never a single bundle shared by every id in the jar — matching `agent-docs.publish`'s always-per-id layout for `java-gradle-plugin` projects (see that plugin's README). Because discovery starts from `project.getPlugins()` (every plugin actually *applied* to the consuming project, not every id the jar happens to declare), only ids the consumer actually applies get a skill — a declared-but-unapplied id from a multi-id jar is never materialized.
- The skill name is derived from the plugin id the same way a dependency's is derived from its GAV: the last dotted segment alone (e.g. `publish` from `io.github.duckasteroid.agent-docs.publish`) when that doesn't collide with anything else resolved in the same run, escalating to the full, normalized plugin id otherwise. Dependency- and plugin-sourced candidates share one collision-detection pass, since both land in the same skills directory.
- The rewritten frontmatter records `metadata.pluginId` instead of `metadata.group`/`metadata.artifact`/`metadata.version`, and the generated `description` prefix identifies it as a Gradle plugin rather than a Java library (see "Description prefix" below).
- `includeSources` has no effect for plugin-sourced skills (there's no Maven coordinate to resolve a `sources` jar from) — `metadata.sources: none` is recorded when the flag is enabled, same as an unavailable sources jar for a regular dependency.
- Scope is deliberately limited to **binary plugins** — published plugin jars applied via `plugins { id '...' }`, including convention plugins that themselves apply other binary plugins internally (each inner plugin is discovered independently off its own jar, since `project.getPlugins()` is a flat record regardless of how a plugin got applied). Precompiled script plugins (`buildSrc` or an included `build-logic` build, e.g. `id 'my-conventions'` backed by a `.gradle`/`.gradle.kts` script) are out of scope: their compiled classes resolve to a local build-output jar that was never stamped by `agent-docs.publish`, so they carry no `Agent-Docs` attribute and are silently skipped like any undocumented plugin — not a distinct, supported code path.

When `includeSources` is enabled, the plugin additionally:

- Fetches the `<group>:<artifact>:<version>:sources@jar` for each dependency.
- Unpacks the sources jar into `assets/sources/` inside the skill folder (per the [Agent Skills convention](https://agentskills.io/specification#assets) that static resources live under `assets/`) so agents can read source code directly without downloading or unzipping anything themselves.
- Injects a `metadata.sources` field alongside the GAV metadata: `assets/sources/` when sources were extracted, `none` when the sources jar is absent from the repository.

## Description prefix

Every extracted skill's `description` is prefixed with a generated sentence identifying what it
documents. For a regular dependency, that's a Java/Maven-specific sentence:

```
Reference documentation for the Java library `com.example:demo` (Maven, resolved version 1.0.0).
Use this skill when writing, reviewing, or debugging code that depends on it.
```

For a Gradle plugin applied via `plugins {}`, it instead reads:

```
Reference documentation for the Gradle plugin `com.example.sample-plugin`. Use this skill when
configuring, writing, or troubleshooting Gradle builds that apply it.
```

If the upstream `SKILL.md` already had a `description`, it's appended after this generated prefix
rather than replaced — so authors can still add library-specific detail on top of the generic
"this is library X, use it when..." framing. The combined value is written as a double-quoted YAML
scalar so any colons or quotes in either half stay valid.

## Skill naming

Each resolved dependency or applied plugin is assigned the shortest name that stays unique within
a single `resolveAgentDocs` run, escalating through tiers only when needed. Dependencies and
plugins share one collision-detection pass, since both land in the same skills directory.

For dependencies:

1. **Artifact name alone** (e.g. `core`) — used whenever no other dependency or plugin in the same
   run shares that name. This is the common case: most consumers have few, if any, name clashes.
2. **`group-artifact`** (e.g. `com-acme-core`) — used only for dependencies whose plain artifact
   name collides with another one in the same run.
3. **`group-artifact-version`** (the full GAV) — used only if `group-artifact` also collides
   (e.g. two different versions of the same module resolved side by side). This is essentially
   unreachable in practice, since a single resolved configuration only ever selects one version
   per `group:artifact`, but is kept as a defensive final tier.

For Gradle plugins:

1. **The plugin id's last dotted segment** (e.g. `publish` from `io.github.duckasteroid.agent-docs.publish`)
   — used whenever it doesn't collide with anything else resolved in the same run.
2. **The full, normalized plugin id** (e.g. `io-github-duckasteroid-agent-docs-publish`) — used
   only on a collision at the first tier. Since plugin ids are already globally unique, this tier
   is effectively unique on its own, so there's no further "version" tier the way a dependency has.

At every tier, the candidate name is lowercased, restricted to `[a-z0-9-]` (other characters
become `-`, repeats collapse, leading/trailing hyphens are stripped), and — only if it would
still exceed 64 characters — truncated and given a deterministic SHA-256 hash suffix so it stays
reproducible. This logic lives in `ModuleCoordinate`/`GradlePluginCoordinate` (the per-tier
candidate keys, both implementing the shared `SkillSource` contract) and `SkillNameAssigner` (the
collision detection across a run).

## Extension Configuration

```groovy
agentDocs {
  configurationName = 'compileClasspath'
  skillsDirectory = rootProject.layout.projectDirectory.dir('.agents/skills')
  includeSources = false   // set to true to also fetch and unpack sources jars
}
```

## Run

```bash
./gradlew resolveAgentDocs
```

## Output layout

Standard (no sources):

```text
.agents/skills/
  <skill-name>/
    SKILL.md
    references/
    assets/
    scripts/
    .agent-docs
```

With `includeSources = true`:

```text
.agents/skills/
  <skill-name>/
    SKILL.md          ← metadata.group/artifact/version + metadata.sources: assets/sources/ (or none if sources jar absent)
    assets/
      sources/        ← unpacked sources jar (only when available)
        com/example/…
    .agent-docs
```

`metadata.group`, `metadata.artifact`, and `metadata.version` are always present, recording the
resolved GAV regardless of `includeSources`. The `metadata.sources` field tells agents what source
code is available, on top of that:

| Value            | Meaning                                                        |
|-------------------|----------------------------------------------------------------|
| `assets/sources/` | Sources extracted — read from that subdirectory                |
| `none`            | Sources were requested but unavailable in the repository       |
| absent            | `includeSources` was not enabled when this skill was extracted |

For a Gradle plugin discovered via `plugins {}`, the layout is the same but the frontmatter
carries `metadata.pluginId` instead of GAV fields, and `metadata.sources` (when `includeSources`
is enabled) is always `none`:

```text
.agents/skills/
  <skill-name>/
    SKILL.md          ← metadata.pluginId: <plugin id>
    references/
    assets/
    scripts/
    .agent-docs
```
