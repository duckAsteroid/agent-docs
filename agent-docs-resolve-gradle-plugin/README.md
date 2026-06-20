# Agent Docs Resolve Plugin

This plugin resolves `agent-docs` sidecar artifacts for direct dependencies, stores each sidecar in a dependency-scoped skill folder, and extracts docs for local agent use.

## What It Does

- Adds a `resolveAgentDocs` task.
- Resolves direct dependencies from the configured classpath (default: `compileClasspath`).
- Attempts to resolve sidecars as `<group>:<artifact>:<version>:agent-docs@zip`.
- Extracts sidecar contents directly to `.agents/skills/<gav-skill-name>/` (for example `SKILL.md`, `references/`, `assets/`, `scripts/`).
- Rewrites dependency skill folder names to Agent-Skills-compatible identifiers (`<gav-skill-name>`), preserving readability and adding hash suffixes when needed for uniqueness under 64 chars.
- Overwrites each extracted dependency `SKILL.md` frontmatter `name` to match the rewritten folder name.
- Writes an ownership marker file at `.agents/skills/<gav-skill-name>/.agent-docs`.
- Removes stale, marker-owned dependency skill folders when those dependencies are no longer in the project.

When `includeSources` is enabled, the plugin additionally:

- Fetches the `<group>:<artifact>:<version>:sources@jar` for each dependency.
- Unpacks the sources jar into `src/` inside the skill folder so agents can read source code directly without downloading or unzipping anything themselves.
- Injects a `metadata.sources` field into the extracted `SKILL.md` frontmatter: `src/` when sources were extracted, `none` when the sources jar is absent from the repository.

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
  <gav-skill-name>/
    SKILL.md
    references/
    assets/
    scripts/
    .agent-docs
```

With `includeSources = true`:

```text
.agents/skills/
  <gav-skill-name>/
    SKILL.md          ← metadata.sources: src/  (or none if sources jar absent)
    src/              ← unpacked sources jar (only when available)
      com/example/…
    .agent-docs
```

The `metadata.sources` frontmatter field tells agents what is available:

| Value   | Meaning                                                        |
|---------|----------------------------------------------------------------|
| `src/`  | Sources extracted — read from that subdirectory                |
| `none`  | Sources were requested but unavailable in the repository       |
| absent  | `includeSources` was not enabled when this skill was extracted |
