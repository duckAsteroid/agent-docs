---
name: agent-docs-resolve
description: Reads the Agent-Docs manifest attribute of project dependencies and extracts their agent docs as local agent skill folders.
---

# Agent Docs Resolve Plugin

Plugin ID: `io.github.duckasteroid.agent-docs`

For each direct dependency on the configured classpath, reads the `Agent-Docs` manifest attribute from that dependency's own resolved jar (see `specification/java-conventions.md` at the repo root for the full convention). A dependency with no `Agent-Docs` attribute is skipped entirely — no resolution attempt of any kind is made for it. A dependency with `Agent-Docs: classpath[:path]` has its docs bundle extracted directly from its own jar, at the default path `agent-docs/` or the declared custom path. A dependency with `Agent-Docs: maven[:group:artifact:version]` has a separate `agent-docs` sidecar zip resolved (at its own coordinates, or the explicitly declared ones) and extracted.

Either way, the resulting bundle is extracted into `.agents/skills/<gav-skill-name>/`. Stale managed skill folders (identified by a `.agent-docs` ownership marker) are removed when their dependencies are dropped.

When `includeSources` is enabled, the plugin also fetches the `sources` classifier jar for each declared dependency and unpacks it into `src/` inside the skill folder. The extracted `SKILL.md` frontmatter records `metadata.sources: src/` when sources are available, or `metadata.sources: none` when the sources jar is absent from the repository.

Note: this plugin currently only discovers agent docs for regular dependencies on the configured classpath — it does not discover docs for Gradle plugins applied via the `plugins {}` block.

## Tasks added

- `resolveAgentDocs` — resolves and extracts all resolvable sidecars; always re-runs (not cached)
- `installAgentDocsResolveSkill` — writes this file into the local agent skills folder

## Usage

```bash
./gradlew resolveAgentDocs
```

## Extension

```groovy
agentDocs {
  configurationName = 'compileClasspath'                                          // default
  skillsDirectory = rootProject.layout.projectDirectory.dir('.agents/skills')     // default
  includeSources = false                                                           // default
}
```

## Output layout

Each resolved dependency produces a skill folder:

```text
.agents/skills/
  <gav-skill-name>/
    SKILL.md          ← frontmatter `name` rewritten to match folder name
    references/
    assets/
    scripts/
    .agent-docs       ← ownership marker; do not edit managed folders
```

When `includeSources = true`, source files are also extracted:

```text
.agents/skills/
  <gav-skill-name>/
    SKILL.md          ← frontmatter includes `metadata.sources: src/` or `metadata.sources: none`
    src/              ← unpacked sources jar (only present when sources jar exists)
      com/example/…
    .agent-docs
```

The `metadata.sources` frontmatter field signals source availability to agents:

| Value   | Meaning                                                        |
|---------|----------------------------------------------------------------|
| `src/`  | Sources extracted — read from that subdirectory                |
| `none`  | Sources were requested but unavailable in the repository       |
| absent  | `includeSources` was not enabled when this skill was extracted |

Folder names are the shortest safe tier: just the artifact name (e.g. `core`) unless that
collides with another dependency in the same run, then `group-artifact` (e.g. `com-acme-core`),
then the full GAV as a last resort. Each tier is lowercased, restricted to `[a-z0-9-]`, and
truncated with a SHA-256 hash suffix if it would exceed 64 characters. See the resolve plugin
README's "Skill naming" section for the full algorithm.

Do not hand-edit files inside marker-owned folders — they are overwritten on the next `resolveAgentDocs` run.

## The `Agent-Docs` manifest attribute

| Value | Meaning |
|---|---|
| *(absent)* | No agent docs for this dependency; nothing is resolved. |
| `classpath` | Docs are embedded in the dependency's own jar at `agent-docs/`. |
| `classpath:<path>` | Docs are embedded at a custom path instead of the default. |
| `maven` | A sidecar zip is published at this dependency's own coordinates. |
| `maven:<group>:<artifact>:<version>` | A sidecar zip is published at the given, explicitly-declared coordinates. |

This attribute doesn't require the `agent-docs-publish-gradle-plugin` at all — any jar can hand-write it in a `jar { manifest { attributes(...) } }` block. See `specification/core-conventions.md` and `specification/java-conventions.md` at the repo root for the full, tool-agnostic convention.
