package io.github.duckasteroid.agentdocs.mcp.tools.impl;

import io.github.duckasteroid.agentdocs.mcp.repo.Repository;
import io.github.duckasteroid.agentdocs.mcp.tools.Tool;
import io.github.duckasteroid.agentdocs.mcp.tools.ToolException;
import io.github.duckasteroid.agentdocs.mcp.tools.ToolMavenCoordinates;
import io.github.duckasteroid.agentdocs.mcp.tools.ToolParameter;
import io.modelcontextprotocol.spec.McpSchema;

import java.util.List;
import java.util.Map;

public class ListRepo implements Tool {
    @Override
    public String name() {
        return "list-repo";
    }

    @Override
    public String description() {
        return "Lists all maven GAV coordinates in the repository. Optionally filtered by group ID and/or artifact ID";
    }

    @Override
    public McpSchema.CallToolResult execute(Repository repository, Map<ToolParameter<?>, Object> arguments) throws ToolException {
        var builder = new McpSchema.CallToolResult.Builder();
        var group = ToolMavenCoordinates.mavenGroupId.extract(arguments).orElse(null);
        var artifactId = ToolMavenCoordinates.mavenArtifactId.extract(arguments).orElse(null);
        repository.list(group, artifactId)
                .forEach(coords -> builder.addTextContent(coords.toString()));

        return builder.build();
    }

    @Override
    public List<ToolParameter<?>> parameters() {
        return List.of(ToolMavenCoordinates.mavenGroupId, ToolMavenCoordinates.mavenArtifactId);
    }

}
