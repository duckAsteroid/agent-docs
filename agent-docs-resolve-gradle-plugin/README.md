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

## Extension Configuration

```groovy
agentDocs {
  configurationName = 'compileClasspath'
  skillsDirectory = rootProject.layout.projectDirectory.dir('.agents/skills')
}
```

## Run

```bash
./gradlew resolveAgentDocs
```
