package io.github.duckasteroid.agentdocs.mcp.tools.impl;

import io.github.duckasteroid.agentdocs.mcp.tools.*;
import io.github.duckasteroid.agentdocs.mcp.repo.Repository;
import io.modelcontextprotocol.spec.McpSchema;

import java.net.URI;
import java.util.List;
import java.util.Map;

public class GetResource implements Tool {
    private final static ToolParameter<String> resourceUri = new ToolParameter<>(
            "resourceUri",
            Type.STRING,
            "The URI of the resource to retrieve, the path of the URI should be in the format `agentdocs:///groupId/artifactId/version/path`"
    );

    @Override
    public String name() {
        return "get_resource";
    }

    @Override
    public String description() {
        return "Returns a resource from a URI with the agentdocs:// protocol";
    }

    @Override
    public List<ToolParameter<?>> parameters() {
        return List.of(resourceUri);
    }

    @Override
    public McpSchema.CallToolResult execute(Repository repository, Map<ToolParameter<?>, Object> arguments) throws ToolException {
        String uriRaw = resourceUri.extract(arguments).orElseThrow(() -> new ToolException("Missing required parameter: resourceUri"));
        URI uri = URI.create(uriRaw);
        if (!"agentdocs".equals(uri.getScheme())) {
            throw new IllegalArgumentException("Invalid URI scheme: " + uri.getScheme());
        }

        McpSchema.ReadResourceResult resourceResult = repository.readResource(uri.toString());
        String content = resourceResult.contents().stream()
                .filter(McpSchema.TextResourceContents.class::isInstance)
                .map(McpSchema.TextResourceContents.class::cast)
                .map(McpSchema.TextResourceContents::text)
                .findFirst()
                .orElse("Unable to read resource [ERROR: Empty resource result]");

        var builder = McpSchema.CallToolResult.builder();
        builder.addTextContent(content);
        return builder.build();
    }
}
