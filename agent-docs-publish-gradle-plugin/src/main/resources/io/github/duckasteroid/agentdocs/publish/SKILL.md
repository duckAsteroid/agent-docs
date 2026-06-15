---
name: agent-docs-publish
description: A Gradle plugin that packages library documentation into an agent-docs sidecar zip for distribution alongside Maven artifacts.
---

# Agent Docs Publish Plugin

Plugin ID: `io.github.duckasteroid.agent-docs.publish`

Validates and packages a docs directory into a sidecar zip artifact with classifier `agent-docs`. When `maven-publish` is also applied, the zip is attached to every `MavenPublication` automatically.

## Tasks added

- `validateAgentDocs` — validates the docs directory against the Agent Skills spec
- `packageAgentDocs` — packages docs into `build/agent-docs/<project-name>-agent-docs.zip`; runs validation first and is wired to `assemble`
- `installAgentDocsPublishSkill` — writes this file into the local agent skills folder

## Docs source layout

Create docs under `src/agent-docs/` (default):

```text
src/agent-docs/
  SKILL.md          ← required; must contain YAML frontmatter with `description`
  references/       ← optional reference docs
  assets/           ← optional assets
  scripts/          ← optional scripts
```

`SKILL.md` must open with YAML frontmatter:

```markdown
---
description: Short description of this library for agents.
---
```

Do not include a `name:` field — the resolver overwrites names from GAV coordinates at extraction time.

## Extension

```groovy
agentDocs {
  docsDirectory = file('src/agent-docs')         // default
  disabledValidationRules = ['skill-name']        // optional; list of rule IDs to skip
}
```

Disable rules from the CLI: `./gradlew validateAgentDocs -PagentDocs.disabledValidationRules=skill-name,skill-compatibility`

## Validation rule IDs

| Rule ID | What it checks |
|---|---|
| `docs-directory-exists` | Docs directory is present |
| `skill-entrypoint` | Exactly one root-level `SKILL.md` (case-insensitive) |
| `standard-directories` | `scripts`, `references`, `assets` are directories if present |
| `skill-frontmatter-structure` | `SKILL.md` opens with valid YAML frontmatter delimiters |
| `skill-description` | Non-empty `description` with valid length |
| `skill-name` | Warning-only: publisher `name` is ignored at packaging time |
| `skill-compatibility` | Optional `compatibility` frontmatter field has valid length |
