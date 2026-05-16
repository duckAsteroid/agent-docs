package io.github.duckasteroid.agentdocs.mcp;

import java.util.Map;

final class ResolvePathsTool implements AgentDocsTool {
    @Override
    public String name() {
        return "agentdocs.resolve_paths";
    }

    @Override
    public String description() {
        return "Returns resolved base/repository paths used by this MCP server.";
    }

    @Override
    public String execute(AgentDocsRepository repository, Map<String, Object> arguments) {
        return """
                baseDirectory=%s
                repositoryDirectory=%s
                """.formatted(
                repository.baseDirectory().toAbsolutePath(),
                repository.repositoryDirectory().toAbsolutePath());
    }
}

