---
name: agent-docs-resolve
description: Resolves agent-docs sidecar artifacts for project dependencies and extracts them as local agent skill folders.
---

# Agent Docs Resolve Plugin

Plugin ID: `io.github.duckasteroid.agent-docs`

For each direct dependency on the configured classpath, resolves `<group>:<artifact>:<version>:agent-docs@zip` using the project's configured repositories. Resolved sidecars are extracted into `.agents/skills/<gav-skill-name>/`. Stale managed skill folders (identified by a `.agent-docs` ownership marker) are removed when their dependencies are dropped.

When `includeSources` is enabled, the plugin also fetches the `sources` classifier jar for each dependency and unpacks it into `src/` inside the skill folder. The extracted `SKILL.md` frontmatter records `metadata.sources: src/` when sources are available, or `metadata.sources: none` when the sources jar is absent from the repository.

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

Folder names are derived from GAV coordinates: lowercased, non-alphanumeric characters replaced with `-`, max 64 characters with a SHA-256 hash suffix when truncation is needed.

Do not hand-edit files inside marker-owned folders — they are overwritten on the next `resolveAgentDocs` run.
