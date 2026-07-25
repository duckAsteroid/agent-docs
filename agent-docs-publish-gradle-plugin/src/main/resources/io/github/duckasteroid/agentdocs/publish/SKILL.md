---
name: agent-docs-publish
description: A Gradle plugin that validates and distributes agent docs, either embedded in a project's own jar or as a sidecar zip, per the Agent-Docs manifest convention.
---

# Agent Docs Publish Plugin

Plugin ID: `io.github.duckasteroid.agent-docs.publish`

Validates a docs directory and distributes it one of two ways, controlled by `agentDocs.distribution`:

- **`sidecar`** (default) — packages the docs into a separate zip artifact with classifier `agent-docs` (attached to every `MavenPublication` when `maven-publish` is applied) and stamps the main jar's manifest with `Agent-Docs: maven:<group>:<artifact>:<version>`.
- **`embedded`** — copies the docs into the project's own jar (under `agent-docs/`) and stamps its manifest with `Agent-Docs: classpath`. No separate artifact is published.

Either way, a consuming project's `agent-docs-resolve-gradle-plugin` (or any tool that understands the `Agent-Docs` manifest attribute) discovers and extracts the docs — see `specification/core-conventions.md` and `specification/java-conventions.md` at the repo root for the full convention. **This convention doesn't require this plugin at all** — the manifest attribute and docs bundle layout can be hand-written by anyone; this plugin is validation and packaging automation on top of it.

## Tasks added

- `validateAgentDocs` — validates the docs directory against the Agent Skills spec
- `packageAgentDocs` — packages docs into `build/agent-docs/<project-name>-agent-docs.zip` for `sidecar` distribution; runs validation first and is wired to `assemble`; skipped when distribution is `embedded`
- `prepareEmbeddedAgentDocs` — copies docs into the jar's own resources for `embedded` distribution; skipped when distribution is `sidecar`
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
  docsDirectory = file('src/agent-docs')          // default
  disabledValidationRules = ['skill-name']        // optional; list of rule IDs to skip
  distribution = AgentDocsDistribution.SIDECAR    // default; or EMBEDDED
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
