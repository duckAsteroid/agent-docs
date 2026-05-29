# agent-docs-mcp

Minimal STDIO MCP server built with the official Java MCP SDK for reading cached `agent-docs` sidecar artifacts directly from the local Maven-style repository.

## Runtime path resolution

The server resolves paths in this order:

- Local repository path:
  1. JVM property: `-DagentDocs.localRepository=...`
  2. Environment: `AGENT_DOCS_LOCAL_REPOSITORY`
  3. Default: `~/.agent-docs/repository`
- Base directory: parent of repository path (or repository path itself if parent is unavailable)

## Run

```bash
./gradlew :agent-docs-mcp:run
```

Run with repository override:

```bash
./gradlew :agent-docs-mcp:run -DagentDocs.localRepository=/path/to/.agent-docs/repository
```

## Build GraalVM native executable

Build a native binary for Linux/macOS using the GraalVM Native Build Tools Gradle plugin:

```bash
./gradlew --no-configuration-cache :agent-docs-mcp:nativeCompile
```

Prerequisite: run with a GraalVM JDK that includes `native-image` (for example by setting `JAVA_HOME`/`GRAALVM_HOME` to a GraalVM installation).

Output binary path:

- `agent-docs-mcp/build/native/nativeCompile/agent-docs-mcp`

If your local repository path needs an override at runtime, pass it as a JVM property equivalent through the native process environment and arguments as appropriate.

## CI native release artifacts

Cross-platform native binaries are produced by `.github/workflows/native-release.yml` for `v*` tags (and via manual dispatch) and uploaded to the matching GitHub Release.

Asset naming convention:

- `agent-docs-mcp-<tag>-linux-x64`
- `agent-docs-mcp-<tag>-windows-x64.exe`
- `agent-docs-mcp-<tag>-macos-x64`
- `agent-docs-mcp-<tag>-macos-arm64`

## Capture tracing metadata from integration tests

You can run the integration test launch path with the GraalVM tracing agent enabled to merge metadata into `src/main/resources/META-INF/native-image`.

```bash
./gradlew --no-configuration-cache :agent-docs-mcp:test --tests '*ApplicationIntegrationTest' -PagentDocs.enableTracingAgent=true
```

This updates files such as `reflect-config.json` based on exercised code paths.

## Exposed MCP methods

- `initialize`
- `ping`
- `resources/list`
- `resources/read`
- `tools/list`
- `tools/call`

Resource template:

- `agentdocs:///{groupId}/{artifactId}/{version}/{path}`

Tools:

- `get_agent_docs`

