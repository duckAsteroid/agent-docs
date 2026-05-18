package io.github.duckasteroid.agentdocs.mcp;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.duckasteroid.agentdocs.mcp.tools.GetAgentDocs;
import io.github.duckasteroid.agentdocs.mcp.tools.MavenCoordinates;
import io.modelcontextprotocol.json.jackson2.JacksonMcpJsonMapper;
import io.modelcontextprotocol.server.McpServer;
import io.modelcontextprotocol.server.McpServerFeatures;
import io.modelcontextprotocol.server.transport.StdioServerTransportProvider;
import io.modelcontextprotocol.spec.McpSchema;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.ServiceLoader;

public final class AgentDocsMcpApplication {
    private static final String SERVER_NAME = "agent-docs-mcp";
    private static final String SERVER_VERSION = resolveServerVersion();
    private static final String SERVER_INSTRUCTIONS = resolveServerInstructions();
    private static final String SERVER_VERSION_RESOURCE = "version.info";
    private static final String SERVER_INSTRUCTIONS_RESOURCE = "instructions.txt";
    private static final String DEFAULT_SERVER_INSTRUCTIONS = "Provides read-only access to LLM usage instructions for Java libraries.";
    private static final String UNKNOWN_VERSION = "unknown";
    private static final String RESOURCE_TEMPLATE_URI = "agentdocs://{groupId}/{artifactId}/{version}/{path}";
    private static final List<AgentDocsTool> TOOLS = loadTools();

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
                .instructions(SERVER_INSTRUCTIONS)
                .resourceTemplates(new McpServerFeatures.SyncResourceTemplateSpecification(
                        McpSchema.ResourceTemplate.builder()
                                .uriTemplate(RESOURCE_TEMPLATE_URI)
                                .name("agent-docs-markdown")
                                .description("Reads markdown documents from the local agent-docs repository.")
                                .mimeType("text/markdown")
                                .build(),
                        (exchange, request) -> readResource(repository, request.uri())));

        for (AgentDocsTool tool : TOOLS) {
            specification = specification.toolCall(tool.definition(jsonMapper), (exchange, request) -> McpSchema.CallToolResult.builder()
                    .addTextContent(executeTool(tool, repository, request.arguments()))
                    .build());
        }

        specification.build();
    }

    static String resolveServerVersion() {
        return resolveTextResource(SERVER_VERSION_RESOURCE, UNKNOWN_VERSION);
    }

    static String resolveServerInstructions() {
        return resolveTextResource(SERVER_INSTRUCTIONS_RESOURCE, DEFAULT_SERVER_INSTRUCTIONS);
    }

    static List<AgentDocsTool> loadTools() {
        return ServiceLoader.load(AgentDocsTool.class).stream()
                .map(ServiceLoader.Provider::get)
                .sorted(Comparator.comparing(AgentDocsTool::name))
                .toList();
    }

    static String executeTool(AgentDocsTool tool, AgentDocsRepository repository, Map<String, Object> requestArguments) {
        try {
            Map<McpParameter<?>, Object> arguments = tool.validateAndConvertArguments(requestArguments);
            return tool.execute(repository, arguments);
        } catch (IllegalArgumentException exception) {
            return "Invalid arguments: " + exception.getMessage();
        }
    }

    static McpSchema.ReadResourceResult readResource(AgentDocsRepository repository, String resourceUri) {
        try {
            AgentDocsResourceResolver.ResolvedResource resolved = AgentDocsResourceResolver.parseResourceUri(resourceUri);
            var coordinates = new MavenCoordinates(
                    resolved.groupId(),
                    resolved.artifactId(),
                    resolved.version());
            String markdown = GetAgentDocs.readAndRewriteMarkdown(repository, coordinates, resolved.markdownPath());
            return new McpSchema.ReadResourceResult(List.of(
                    new McpSchema.TextResourceContents(resourceUri, "text/markdown", markdown)));
        } catch (IllegalArgumentException | IOException exception) {
            String error = "Unable to read resource [ERROR: " + exception.getMessage() + "]";
            return new McpSchema.ReadResourceResult(List.of(
                    new McpSchema.TextResourceContents(resourceUri, "text/plain", error)));
        }
    }

    private static String resolveTextResource(String resourceName, String defaultValue) {
        try (InputStream stream = AgentDocsMcpApplication.class.getClassLoader().getResourceAsStream(resourceName)) {
            if (stream == null) {
                return defaultValue;
            }
            String contents = new String(stream.readAllBytes(), StandardCharsets.UTF_8).trim();
            if (contents.isBlank()) {
                return defaultValue;
            }
            return contents;
        } catch (IOException ignored) {
            return defaultValue;
        }
    }
}

