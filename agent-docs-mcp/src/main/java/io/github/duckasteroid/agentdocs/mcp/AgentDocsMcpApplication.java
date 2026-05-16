package io.github.duckasteroid.agentdocs.mcp;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.modelcontextprotocol.json.jackson2.JacksonMcpJsonMapper;
import io.modelcontextprotocol.server.McpServer;
import io.modelcontextprotocol.server.transport.StdioServerTransportProvider;
import io.modelcontextprotocol.spec.McpSchema;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.List;

public final class AgentDocsMcpApplication {
    private static final String SERVER_NAME = "agent-docs-mcp";
    private static final String SERVER_VERSION = "0.1.0-SNAPSHOT";
    private static final List<AgentDocsTool> TOOLS = List.of(
            new ResolvePathsTool(),
            new ListCachedSidecarsTool());

    private AgentDocsMcpApplication() {
    }

    public static void main(String[] args) throws IOException {
        AgentDocsRepository repository = AgentDocsRepository.resolve();
        startStdioServer(repository, System.in, System.out);
    }

    static void startStdioServer(AgentDocsRepository repository, InputStream inputStream, OutputStream outputStream) {
        var jsonMapper = new JacksonMcpJsonMapper(new ObjectMapper());
        var transportProvider = new StdioServerTransportProvider(jsonMapper, inputStream, outputStream);

        var specification = McpServer.sync(transportProvider)
                .serverInfo(SERVER_NAME, SERVER_VERSION)
                .instructions("Provides read-only access to cached agent-docs sidecar artifacts in the local Maven-style repository.");

        for (AgentDocsTool tool : TOOLS) {
            specification = specification.toolCall(tool.definition(jsonMapper), (exchange, request) -> McpSchema.CallToolResult.builder()
                    .addTextContent(tool.execute(repository, request.arguments()))
                    .build());
        }

        specification.build();
    }
}

