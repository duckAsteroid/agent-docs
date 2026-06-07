# Agent Docs Resolve Plugin

This plugin resolves `agent-docs` sidecar artifacts for direct dependencies, stores each sidecar in a dependency-scoped skill folder, extracts docs for local agent use, and generates one dependency skill per resolved sidecar.

## What It Does

- Adds a `resolveAgentDocs` task.
- Resolves direct dependencies from the configured classpath (default: `compileClasspath`).
- Attempts to resolve sidecars as `<group>:<artifact>:<version>:agent-docs@zip`.
- Copies each resolved sidecar zip to `.agent/skills/<gav-skill-name>/agent-docs.zip`.
- Extracts sidecar contents directly to `.agent/skills/<gav-skill-name>/` (for example `SKILL.md`, `references/`, `assets/`, `scripts/`).
- Rewrites dependency skill folder names to Agent-Skills-compatible identifiers (`<gav-skill-name>`), preserving readability and adding hash suffixes when needed for uniqueness under 64 chars.
- Overwrites each extracted dependency `SKILL.md` frontmatter `name` to match the rewritten folder name.
- Writes an ownership marker file at `.agent/skills/<gav-skill-name>/.agent-docs`.
- Generates a dependency skill at `.agent/skills/agent-docs-dependencies/<gav-skill-name>/SKILL.md` for each resolved sidecar.
- Removes stale, marker-owned dependency skill folders when those dependencies are no longer in the project.
- Cleans legacy `.agent/skills/SKILL.md` single-index output if present from older plugin versions.

## Extension Configuration

```groovy
agentDocs {
  configurationName = 'compileClasspath'
  skillsDirectory = layout.projectDirectory.dir('.agent/skills')
}
```

## Run

```bash
./gradlew resolveAgentDocs
```
