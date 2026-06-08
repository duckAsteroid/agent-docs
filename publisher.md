# Publishing agent docs sidecars

This guide shows how to publish `agent-docs` sidecars for your own libraries so consumer projects can resolve them with the resolver plugin and expose them to local LLM agents.

## Fictional example

Assume you publish `com.acme:weather-core:1.4.0` and want to ship agent docs with it.

### 1. Apply the publish plugin

```groovy
plugins {
  id 'java-library'
  id 'maven-publish'
  id 'io.github.duckasteroid.agent-docs.publish' version '<version>'
}
```

### 2. Add docs in `src/agent-docs`

The docs root must include exactly one `SKILL.md` entrypoint (case-insensitive in source naming), and `SKILL.md` must include Agent Skills YAML frontmatter:

```markdown
---
name: agent-docs
description: Publish and validate agent docs sidecars for Gradle libraries.
---
```

```text
src/agent-docs/
  SKILL.md
  references/
    overview.md
    troubleshooting.md
    topics/
      quickstart.md
  assets/
  scripts/
```

If `name` is provided, the publish plugin emits a warning and omits it from the sidecar because resolver-generated dependency skill naming is authoritative at consumption time.

### 3. Build and publish

```bash
./gradlew publish
```

The plugin packages docs into:

```text
build/agent-docs/weather-core-agent-docs.zip
```

When `maven-publish` is applied, this archive is attached to each `MavenPublication` as a sidecar artifact with classifier `agent-docs` and type `zip`.

### 4. What consumers resolve

Consumers that use the resolver plugin and depend on `com.acme:weather-core:1.4.0` will attempt to resolve:

```text
com.acme:weather-core:1.4.0:agent-docs@zip
```

Resolved sidecars are then extracted into root `.agents/skills/<gav-skill-name>/` with skill-spec layout (`SKILL.md`, `references/`, `assets/`, `scripts/`), rewritten so extracted `SKILL.md` frontmatter `name` matches the rewritten folder, and marked with `.agent-docs` for managed cleanup.

## More details

- Publish plugin reference: [agent-docs-publish-gradle-plugin/README.md](./agent-docs-publish-gradle-plugin/README.md)
- Resolver plugin reference: [agent-docs-resolve-gradle-plugin/README.md](./agent-docs-resolve-gradle-plugin/README.md)
