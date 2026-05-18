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

## Exposed MCP methods

- `initialize`
- `ping`
- `resources/list`
- `resources/read`
- `tools/list`
- `tools/call`

Resource template:

- `agentdocs://{groupId}/{artifactId}/{version}/{path}`

Tools:

- `get_agent_docs`

