# Agent Docs Resolve Plugin

This plugin resolves `agent-docs` sidecar artifacts for direct dependencies, caches the sidecar zips locally, extracts docs by GAV for local agent consumption, and generates skill files under `.agents/`.

## What It Does

- Adds a `resolveAgentDocs` task.
- Resolves direct dependencies from the configured classpath (default: `runtimeClasspath`).
- Attempts to resolve sidecars as `<group>:<artifact>:<version>:agent-docs@zip`.
- Caches resolved sidecar zips in a local Maven-style repository.
- Extracts sidecar contents to `.agents/resources/agent-docs/<group>/<artifact>/<version>/`.
- Generates skills in one of three configurable modes:
  - `SINGLE_INDEX`
  - `PER_DEPENDENCY`
  - `AUTO_THRESHOLD`
- Cleans remnants from the non-selected generation model when mode changes.

## Extension Configuration

```groovy
agentDocs {
  configurationName = 'runtimeClasspath'

  // SINGLE_INDEX | PER_DEPENDENCY | AUTO_THRESHOLD
  skillGenerationMode = 'AUTO_THRESHOLD'

  // Used only when skillGenerationMode = AUTO_THRESHOLD
  perDependencySkillThreshold = 10

  skillsDirectory = layout.projectDirectory.dir('.agents/skills')
  skillFile = layout.projectDirectory.file('.agents/skills/agent-docs.md')
  resourcesDirectory = layout.projectDirectory.dir('.agents/resources/agent-docs')
}
```

## Skill Generation Modes

- `SINGLE_INDEX`
  - Writes one router skill at `.agents/skills/agent-docs.md`.
  - Lists resolved GAVs with links to each dependency `agents.md` entrypoint.
  - Removes `.agents/skills/agent-docs-dependencies/` if present.

- `PER_DEPENDENCY`
  - Writes one skill per resolved dependency under `.agents/skills/agent-docs-dependencies/<group>/<artifact>/<version>.md`.
  - Removes `.agents/skills/agent-docs.md` if present.

- `AUTO_THRESHOLD`
  - If resolved sidecars count is `<= perDependencySkillThreshold`, behaves as `PER_DEPENDENCY`.
  - If resolved sidecars count is `> perDependencySkillThreshold`, behaves as `SINGLE_INDEX`.
  - Applies the same cleanup rules for the effective mode.

### Mode Decision Table

| Mode | When to use | Output |
| --- | --- | --- |
| `SINGLE_INDEX` | Large dependency sets where one stable entry skill is preferred | `.agents/skills/agent-docs.md` |
| `PER_DEPENDENCY` | Smaller dependency sets where dependency-scoped skills are preferred | `.agents/skills/agent-docs-dependencies/...` |
| `AUTO_THRESHOLD` | Mixed projects where mode should adapt to resolved sidecar count | Per-dependency when `count <= perDependencySkillThreshold`, otherwise single-index |

## Local Repository Path Resolution

The cache path is resolved in this order:

1. JVM property: `-DagentDocs.localRepository=...`
2. Environment variable: `AGENT_DOCS_LOCAL_REPOSITORY`
3. Default: `~/.agent-docs/repository`

## Run

```bash
./gradlew resolveAgentDocs
```

With local repository override:

```bash
./gradlew resolveAgentDocs -DagentDocs.localRepository=/path/to/.agent-docs/repository
```

