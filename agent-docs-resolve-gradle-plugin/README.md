# Agent Docs Resolve Plugin

This plugin reads the `Agent-Docs` manifest attribute (see [`specification/core-conventions.md`](../specification/core-conventions.md) and [`specification/java-conventions.md`](../specification/java-conventions.md) at the repo root) from direct dependencies' own resolved jars, and extracts their agent docs for local agent use.

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

Note: this plugin currently only discovers agent docs for regular dependencies on the configured classpath — it does not yet discover docs for Gradle plugins applied via the `plugins {}` block. That support is deferred.

When `includeSources` is enabled, the plugin additionally:

- Fetches the `<group>:<artifact>:<version>:sources@jar` for each dependency.
- Unpacks the sources jar into `assets/sources/` inside the skill folder (per the [Agent Skills convention](https://agentskills.io/specification#assets) that static resources live under `assets/`) so agents can read source code directly without downloading or unzipping anything themselves.
- Injects a `metadata.sources` field alongside the GAV metadata: `assets/sources/` when sources were extracted, `none` when the sources jar is absent from the repository.

## Description prefix

Every extracted skill's `description` is prefixed with a generated, Java/Maven-specific sentence
identifying the library, e.g.:

```
Reference documentation for the Java library `com.example:demo` (Maven, resolved version 1.0.0).
Use this skill when writing, reviewing, or debugging code that depends on it.
```

If the upstream `SKILL.md` already had a `description`, it's appended after this generated prefix
rather than replaced — so authors can still add library-specific detail on top of the generic
"this is library X, use it when..." framing. The combined value is written as a double-quoted YAML
scalar so any colons or quotes in either half stay valid.

## Skill naming

Each resolved dependency is assigned the shortest name that stays unique within a single
`resolveAgentDocs` run, escalating through tiers only when needed:

1. **Artifact name alone** (e.g. `core`) — used whenever no other dependency in the same run
   shares that artifact name. This is the common case: most consumers have few, if any,
   artifact-name clashes across their dependencies.
2. **`group-artifact`** (e.g. `com-acme-core`) — used only for dependencies whose plain artifact
   name collides with another one in the same run.
3. **`group-artifact-version`** (the full GAV) — used only if `group-artifact` also collides
   (e.g. two different versions of the same module resolved side by side). This is essentially
   unreachable in practice, since a single resolved configuration only ever selects one version
   per `group:artifact`, but is kept as a defensive final tier.

At every tier, the candidate name is lowercased, restricted to `[a-z0-9-]` (other characters
become `-`, repeats collapse, leading/trailing hyphens are stripped), and — only if it would
still exceed 64 characters — truncated and given a deterministic SHA-256 hash suffix so it stays
reproducible. This logic lives in `ModuleCoordinate` (the per-tier candidate keys) and
`SkillNameAssigner` (the collision detection across a run).

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
