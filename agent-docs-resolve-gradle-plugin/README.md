# Agent Docs Resolve Plugin

This plugin resolves `agent-docs` sidecar artifacts for direct dependencies, stores each sidecar under a dependency-scoped project skill folder, extracts a skill-spec layout for local agent consumption, and generates router/index skills under `.agent/skills/`.

## What It Does

- Adds a `resolveAgentDocs` task.
- Resolves direct dependencies from the configured classpath (default: `runtimeClasspath`).
- Attempts to resolve sidecars as `<group>:<artifact>:<version>:agent-docs@zip`.
- Copies each resolved sidecar zip to `.agent/skills/<gav-skill-name>/agent-docs.zip`.
- Extracts sidecar contents directly to `.agent/skills/<gav-skill-name>/` (for example `SKILL.md`, `references/`, `assets/`, `scripts/`).
- Rewrites dependency skill folder names to Agent-Skills-compatible identifiers (`<gav-skill-name>`), preserving readability and adding hash suffixes when needed for uniqueness under 64 chars.
- Overwrites each extracted dependency `SKILL.md` frontmatter `name` to match the rewritten folder name.
- Writes an ownership marker file at `.agent/skills/<gav-skill-name>/.agent-docs`.
- Generates skills in one of three configurable modes:
  - `SINGLE_INDEX`
  - `PER_DEPENDENCY`
  - `AUTO_THRESHOLD`
- Cleans remnants from the non-selected generation model when mode changes.
- Removes stale, marker-owned dependency skill folders when those dependencies are no longer in the project.

## Extension Configuration

```groovy
agentDocs {
  configurationName = 'runtimeClasspath'

  // SINGLE_INDEX | PER_DEPENDENCY | AUTO_THRESHOLD
  skillGenerationMode = 'AUTO_THRESHOLD'

  // Used only when skillGenerationMode = AUTO_THRESHOLD
  perDependencySkillThreshold = 10

  skillsDirectory = layout.projectDirectory.dir('.agent/skills')
  skillFile = layout.projectDirectory.file('.agent/skills/SKILL.md')
}
```

## Skill Generation Modes

- `SINGLE_INDEX`
  - Writes one router skill at `.agent/skills/SKILL.md`.
  - Lists resolved GAVs with links to each dependency `SKILL.md` entrypoint.
  - Removes `.agent/skills/agent-docs-dependencies/` if present.

- `PER_DEPENDENCY`
  - Writes one generated dependency index skill per resolved dependency under `.agent/skills/agent-docs-dependencies/<gav-skill-name>/SKILL.md`.
  - Writes frontmatter `name` matching each generated skill folder name.
  - Marks each generated folder with `.agent-docs`.
  - Removes `.agent/skills/SKILL.md` if present.

- `AUTO_THRESHOLD`
  - If resolved sidecars count is `<= perDependencySkillThreshold`, behaves as `PER_DEPENDENCY`.
  - If resolved sidecars count is `> perDependencySkillThreshold`, behaves as `SINGLE_INDEX`.
  - Applies the same cleanup rules for the effective mode.

### Mode Decision Table

| Mode | When to use | Output |
| --- | --- | --- |
| `SINGLE_INDEX` | Large dependency sets where one stable entry skill is preferred | `.agent/skills/SKILL.md` |
| `PER_DEPENDENCY` | Smaller dependency sets where dependency-scoped skills are preferred | `.agent/skills/agent-docs-dependencies/...` |
| `AUTO_THRESHOLD` | Mixed projects where mode should adapt to resolved sidecar count | Per-dependency when `count <= perDependencySkillThreshold`, otherwise single-index |

## Run

```bash
./gradlew resolveAgentDocs
```
