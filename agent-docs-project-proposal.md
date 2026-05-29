# Agent Docs Toolchain Proposal

> Status note (current implementation): this document captures the original proposal and rationale. The MCP subproject has been removed from the current repository operating model. For the current runtime contract, prefer `README.md`, `AGENTS.md`, `agent-docs-publish-gradle-plugin/README.md`, and `agent-docs-resolve-gradle-plugin/README.md`.

## Summary

This project proposes a reusable toolchain for publishing, resolving, and exposing **agent-oriented documentation** for binary dependencies.

The goal is to let coding agents and MCP clients understand how to use a library dependency even when the consuming project only has access to the compiled binary artifact.

The system is designed around a new sidecar artifact named `agent-docs`, published alongside normal library artifacts and resolved through the same artifact repository using the same coordinates and credentials.

## Problem

Agents working in a dependent project can usually inspect:

- local source code
- dependency declarations
- binary artifacts
- sometimes sources and javadocs

But they often cannot reliably infer:

- intended usage patterns
- supported entry points
- lifecycle constraints
- threading or performance assumptions
- error semantics
- migration notes
- safe and unsafe integration patterns

This creates a gap between what a human library author knows and what an agent can safely use in a consuming project.

## Proposed Solution

Publish an additional sidecar artifact called `agent-docs` for each library release. The artifact is version-aligned with the binary dependency and stored in the same package repository.

The original proposal described three components:

1. **Publisher plugin**
   - Generates and publishes `agent-docs` sidecar artifacts from the library project.
2. **Resolver plugin**
   - Resolves `agent-docs` sidecars for dependencies in consuming projects and stores them in a local Maven-style repository.
3. **MCP component**
   - Reads local sidecar content and exposes resolved documentation to coding agents via MCP resources and tools (historical; no longer in this repository).

## Key Architectural Decisions

### 1. Separate repository

This work should live in a dedicated repository, not inside an existing consumer project.

Reasons:

- reusable across multiple repositories
- independent release lifecycle
- cleaner testing and samples
- clearer ownership of the artifact spec

### 2. Resolver and MCP must be decoupled

> Historical note: this decision remains valid design rationale, but the MCP subproject is not part of the current repository.

The resolver and MCP components must not call each other directly.

They communicate **only via local filesystem artifacts produced by the resolver**.

This means:

- the resolver owns repository access, dependency resolution, and credentials
- the MCP server owns only local read/query behavior
- the MCP process does not depend on Gradle APIs or remote repositories
- both components can be tested independently

### 3. Sidecar artifact over separate coordinates

`agent-docs` should be published as a sidecar artifact tied to the same logical module/version as the binary dependency, rather than as a separately versioned logical product.

This keeps documentation aligned to the exact dependency version being used.

### 4. Publish-time generation preferred

The best source of agent-ready guidance is the library project itself.

The publisher plugin should support generated or curated content from the library repository, rather than relying only on consumer-side inference from source/javadocs.

## Initial Scope

### In scope

- publish `agent-docs` artifacts from library projects
- resolve matching `agent-docs` artifacts in consumer projects
- cache resolved sidecars locally
- expose cached sidecars through MCP
- support Maven/Gradle repositories, including authenticated repositories such as GitHub Packages

### Out of scope for the first iteration

- dynamic online fetching by the MCP server
- tight coupling between MCP and Gradle internals
- full semantic analysis of arbitrary source trees
- IDE-specific integrations beyond MCP consumption
- automatic synthesis of perfect docs from binaries alone

## Proposed Repository Layout

```text
agent-docs/
  agent-docs-publish-gradle-plugin/
  agent-docs-resolve-gradle-plugin/
  docs/
```

## Component Responsibilities

## Shared contract

In the current implementation, the contract is documented across repo docs and plugin/module READMEs rather than a dedicated `agent-docs-spec` module.

## `agent-docs-publish-gradle-plugin`

Responsibilities:

- collect agent-ready inputs from the library project
- package them into a standard sidecar archive
- attach/publish the archive to the normal publication flow

Potential inputs:

- curated markdown guides
- usage recipes
- migration notes
- invariants and constraints
- optional symbol index metadata
- optional generated summaries from source or javadocs

## `agent-docs-resolve-gradle-plugin`

Responsibilities:

- inspect resolved dependencies in a consumer project
- attempt to resolve a matching `agent-docs` sidecar for each dependency
- store sidecars in a deterministic local cache
- preserve a deterministic local repository layout for later lookup by GAV

Important constraint:

This plugin is the only part that should need repository credentials or artifact resolution logic.

## Historical serving component

This component was proposed as an optional serving layer and has been removed from the current repository.

## Artifact Contract

First implementation should use a sidecar archive that works cleanly with Maven-compatible repositories.

Suggested initial shape:

- artifact classifier or equivalent sidecar named `agent-docs`
- archive format: `.zip`

Current implementation shape (simplified):

```text
agent-docs.zip
  agents.md
  ...additional markdown/docs content...
```

## Current Runtime Contract

- Publisher packages docs from a configured root (default `src/agentDocs`) into an `agent-docs` zip sidecar.
- Publisher enforces exactly one `agents.md` entrypoint in docs root (case-insensitive in source, normalized in packaged output).
- Resolver resolves and caches sidecar zips in a local Maven-style repository.
- Resolver extracts docs by GAV under `.agents/resources/agent-docs` and generates local skills under `.agents/skills`.

## Gradle Integration Strategy

### Publisher plugin

Should integrate into library publication flow and publish the sidecar with the same version as the main artifact.

### Resolver plugin

Should run after dependency resolution and fetch matching sidecars from the same repositories already configured for dependency resolution.

This allows reuse of:

- repository URLs
- credentials
- version alignment
- existing enterprise artifact flows

## Why this is useful

This approach gives agents access to guidance that is normally missing from binaries:

- intended entry points
- usage recipes
- constraints and caveats
- lifecycle expectations
- compatibility and migration guidance

It also lets organizations publish private library guidance to internal consumers without exposing source code more broadly than necessary.

## Risks and Open Questions

### Open questions

- exact publication mechanism: classifier only, variant metadata, or both
- exact archive schema and versioning strategy
- whether symbol-level mapping is required in v1
- how aggressively content should be normalized for token efficiency
- what minimum authoring burden is acceptable for library teams

### Risks

- poor docs quality will reduce value even if the infrastructure works
- overcomplicated schema could slow adoption
- tight Gradle integration choices may reduce portability if not carefully isolated

## Recommended First Milestones

1. Define the spec:
   - sidecar archive shape
   - `agents.md` entrypoint expectations
   - resolver local repository and `.agents` output contracts
2. Build a minimal publisher plugin:
   - package curated markdown into `agent-docs.zip`
   - publish alongside a sample Java library
3. Build a minimal resolver plugin:
   - resolve sidecars for dependencies
   - cache sidecars in deterministic local repository layout
4. Add end-to-end documentation and compatibility tests

## Suggested New Repo README Opening

This repository contains a toolchain for publishing and resolving **agent-ready documentation** for binary dependencies. It introduces an `agent-docs` sidecar artifact that travels with the same module/version as a library dependency, is resolved through standard Gradle/Maven repositories, cached locally by a Gradle resolver plugin, and exposed to coding agents through local `.agents` skills and resources.

## Notes For A Future Agent

The important decisions already made are:

1. Use a **new repository** for this work.
2. Create **two separate components**:
   - publisher Gradle plugin
   - resolver Gradle plugin
3. Use the **resolver-managed local repository and `.agents` outputs** as the integration boundary.
4. Prefer a **sidecar artifact** named `agent-docs`, aligned with the same dependency version as the binary artifact.
5. Prefer **publish-time generation** of agent-ready docs from the library project.

These decisions should be treated as the current baseline unless explicitly revised.
