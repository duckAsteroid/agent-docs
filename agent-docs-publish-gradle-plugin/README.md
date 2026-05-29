# Agent Docs Publish Plugin

This plugin packages agent-oriented documentation into a sidecar zip archive that can be resolved later and consumed locally by resolver-generated skills/resources.

The plugin contract is intentionally minimal:

- Your docs root directory must exist.
- That root must contain exactly one `agents.md` entrypoint file (name matching is case-insensitive).
- Everything in that root directory is included in the archive.

## What it does

- Adds a `packageAgentDocs` task.
- Wires `assemble` to depend on `packageAgentDocs`.
- Fails the build if the docs directory does not exist.
- Fails the build if `agents.md` is missing in docs root (case-insensitive check).
- Produces an archive at:
  - `build/agent-docs/<project-name>-agent-docs.zip`
- Packages the full docs root directory contents at the zip root.
- If `maven-publish` is applied, automatically attaches the archive as an additional artifact
  with classifier `agent-docs` to all `MavenPublication`s.

## Quick start

Apply the plugin in your library project:

```groovy
plugins {
	id 'java-library'
	id 'io.github.duckasteroid.agent-docs.publish'
}
```

Create docs in the default location:

```text
src/agentDocs/
  AGENTS.md
  overview.md
  topics/
	getting-started.md
	troubleshooting.md
```

Build your docs archive:

```bash
./gradlew packageAgentDocs
```

Or run a normal assemble build (which will include docs packaging):

```bash
./gradlew assemble
```

## Default directory layout (recommended)

The plugin defaults to:

- Docs source directory: `src/agentDocs`
- Output archive: `build/agent-docs/<project-name>-agent-docs.zip`
- Mandatory entrypoint in docs root: `agents.md` (case-insensitive in source)
- Zip structure mirrors docs root contents
- The entrypoint is always written to the zip as lowercase `agents.md`

Recommended project layout:

```text
<project-root>/
  src/
	main/
	  java/...
	agentDocs/
	  AGENTS.md
	  overview.md
	  api/
		key-types.md
	  operations/
		deployment.md
```

## Writing docs that work well for agent consumers

Agents consuming docs through resolver-generated skills/resources generally perform best when content is:

- **Task-oriented**: include steps for common goals (setup, auth, migration, troubleshooting).
- **Explicit**: prefer exact commands, concrete paths, and precise config keys.
- **Chunked**: split long guides into focused topic files so retrieval can target relevant sections.
- **Deterministic**: avoid ambiguous wording like "usually" when exact behavior is known.
- **Version-aware**: call out version-specific behavior near the top of each topic.

Practical authoring guidance:

- Use `AGENTS.md` (or `agents.md`) as the navigation hub.
- In `AGENTS.md`, link to the most important task docs first (setup, auth, common workflows, troubleshooting).
- Start each topic with a one-paragraph summary and "when to use this".
- Use stable headings (`## Configure X`, `## Validate Y`, `## Troubleshoot Z`) to improve retrieval quality.
- Include short copy/paste command blocks.
- Add a "Failure modes" section with symptoms and fixes.
- Link related topics with relative links to help navigation.

Recommended `AGENTS.md` structure:

```markdown
# Agent Documentation Index

## Start here
- [Overview](./overview.md)
- [Quickstart](./topics/getting-started.md)

## Common tasks
- [Configure authentication](./topics/auth/configure.md)
- [Publish a release](./topics/release/publish.md)

## Troubleshooting
- [Troubleshooting guide](./topics/troubleshooting.md)
```

### Suggested topic template

```markdown
# <Topic title>

## Purpose
What this topic is for, in 2-4 sentences.

## Prerequisites
- Required tools/versions
- Required credentials

## Steps
1. Concrete step
2. Concrete step

## Validation
- Command to verify success
- Expected output/behavior

## Failure modes
- Symptom: ...
  - Cause: ...
  - Fix: ...
```

## Advanced: use a custom docs directory

If your project cannot use `src/agentDocs`, configure a custom directory:

```groovy
agentDocs {
  docsDirectory = layout.projectDirectory.dir('docs/agent-docs')
}
```

Then place docs at:

```text
docs/agent-docs/
  AGENTS.md
  overview.md
  subfolder/...
```

Notes:

- The configured directory must exist, or packaging fails.
- The configured directory must contain exactly one `agents.md` entrypoint (case-insensitive).
- The zip preserves the configured directory's internal layout.
- The entrypoint file name is normalized to lowercase `agents.md` in the zip.

