package io.github.duckasteroid.agentdocs.mcp.tools.impl;

import io.github.duckasteroid.agentdocs.mcp.repo.Repository;
import io.github.duckasteroid.agentdocs.mcp.tools.Tool;
import io.github.duckasteroid.agentdocs.mcp.tools.ToolException;
import io.github.duckasteroid.agentdocs.mcp.tools.ToolMavenCoordinates;
import io.github.duckasteroid.agentdocs.mcp.tools.ToolParameter;
import io.modelcontextprotocol.spec.McpSchema;

import java.net.URI;
import java.util.List;
import java.util.Map;

public class ListResources implements Tool {
    @Override
    public String name() {
        return "list-resources";
    }

    @Override
    public String description() {
        return "Lists all resources for a maven coordinate";
    }

    @Override
    public List<ToolParameter<?>> parameters() {
        return List.copyOf(ToolMavenCoordinates.parameters());
    }

    @Override
    public McpSchema.CallToolResult execute(Repository repository, Map<ToolParameter<?>, Object> arguments) throws ToolException {
        var coords = ToolMavenCoordinates.fromToolArgs(arguments);
        var builder = new McpSchema.CallToolResult.Builder();
        repository.listResources(coords).stream()
                .map(Repository.ResolvedResource::asUri)
                .forEach(uri -> builder.addTextContent(uri.toString()));
        return builder.build();
    }
}
