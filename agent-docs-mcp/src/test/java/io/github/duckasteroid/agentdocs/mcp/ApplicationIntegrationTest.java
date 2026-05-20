package io.github.duckasteroid.agentdocs.mcp;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.modelcontextprotocol.client.McpClient;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.client.transport.ServerParameters;
import io.modelcontextprotocol.client.transport.StdioClientTransport;
import io.modelcontextprotocol.json.jackson2.JacksonMcpJsonMapper;
import io.modelcontextprotocol.spec.McpSchema;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class ApplicationIntegrationTest {
    @TempDir
    Path tempDir;
    private McpSyncClient client;
    private McpSchema.InitializeResult initializeResult;

    @BeforeEach
    public void setup() throws IOException {
        Path repositoryRoot = tempDir.resolve(".agent-docs").resolve("repository");
        Path docDirectory = repositoryRoot.resolve(Path.of("com.example", "demo", "1.0.0", "topics"));
        Files.createDirectories(docDirectory);
        Files.writeString(docDirectory.resolve("overview.md"), "See [Setup](../setup.md).\n");
        Files.writeString(repositoryRoot.resolve(Path.of("com.example", "demo", "1.0.0", "setup.md")), "# Setup\n");
        Files.writeString(repositoryRoot.resolve(Path.of("com.example", "demo", "1.0.0", "agents.md")), "# Agent Docs\nHello\n");

        ServerParameters serverParameters = ServerParameters.builder(javaCommand())
                .args("-cp", System.getProperty("java.class.path"), Application.class.getName())
                .addEnvVar("AGENT_DOCS_LOCAL_REPOSITORY", repositoryRoot.toString())
                .build();

        StdioClientTransport transport = new StdioClientTransport(
                serverParameters,
                new JacksonMcpJsonMapper(new ObjectMapper()));

        this.client = McpClient.sync(transport)
                .requestTimeout(Duration.ofSeconds(5))
                .initializationTimeout(Duration.ofSeconds(5))
                .build();
        this.initializeResult = client.initialize();
        assertNotNull(initializeResult);
    }

    @AfterEach
    void teardown() throws IOException {
        client.close();
    }

    @Test
    void testReadResources() throws IOException {
        McpSchema.ListResourceTemplatesResult templates = client.listResourceTemplates();
        assertTrue(templates.resourceTemplates().stream()
                .anyMatch(template -> template.uriTemplate().equals("agentdocs:///{groupId}/{artifactId}/{version}/{path}")));

        var resource = client.readResource(McpSchema.Resource.builder().name("test").uri("agentdocs:///com.example/demo/1.0.0/topics/overview.md").build());
        assertNotNull(resource);
        McpSchema.TextResourceContents resourceContent = (McpSchema.TextResourceContents) resource.contents().get(0);
        assertTrue(resourceContent.text().contains("agentdocs:///com.example/demo/1.0.0/setup.md"));
    }

    @Test
    void testGetDocsTool() {
        McpSchema.ListToolsResult tools = client.listTools();
        assertTrue(tools.tools().stream().anyMatch(tool -> tool.name().equals("get_agent_docs")));

        McpSchema.CallToolResult result = client.callTool(new McpSchema.CallToolRequest(
                "get_agent_docs",
                Map.of("mavenCoordinates", "com.example:demo:1.0.0")));

        assertEquals(1, result.content().size());
        McpSchema.TextContent content = (McpSchema.TextContent) result.content().get(0);
        assertTrue(content.text().contains("Agent Docs"));
    }

    @Test
    void testListResourcesTool() {
        McpSchema.ListToolsResult tools = client.listTools();
        assertTrue(tools.tools().stream().anyMatch(tool -> tool.name().equals("list-resources")));

        McpSchema.CallToolResult result = client.callTool(new McpSchema.CallToolRequest(
                "list-resources",
                Map.of("mavenCoordinates", "com.example:demo:1.0.0")));

        assertTrue(result.content().size() >= 3);
        assertTrue(result.content().stream()
                .filter(McpSchema.TextContent.class::isInstance)
                .map(McpSchema.TextContent.class::cast)
                .map(McpSchema.TextContent::text)
                .anyMatch(text -> text.endsWith("/agents.md")));
        assertTrue(result.content().stream()
                .filter(McpSchema.TextContent.class::isInstance)
                .map(McpSchema.TextContent.class::cast)
                .map(McpSchema.TextContent::text)
                .anyMatch(text -> text.endsWith("/topics/overview.md")));
    }

    @Test
    void testGetResourceTool() {
        McpSchema.ListToolsResult tools = client.listTools();
        assertTrue(tools.tools().stream().anyMatch(tool -> tool.name().equals("get_resource")));

        McpSchema.CallToolResult result = client.callTool(new McpSchema.CallToolRequest(
                "get_resource",
                Map.of("resourceUri", "agentdocs://com.example/demo/1.0.0/topics/overview.md")));

        assertTrue(result.content().stream()
                .filter(McpSchema.TextContent.class::isInstance)
                .map(McpSchema.TextContent.class::cast)
                .map(McpSchema.TextContent::text)
                .anyMatch(text -> text.contains("agentdocs:///com.example/demo/1.0.0/setup.md")));
    }

    @Test
    void testListRepoTool() {
        McpSchema.ListToolsResult tools = client.listTools();
        assertTrue(tools.tools().stream().anyMatch(tool -> tool.name().equals("list-repo")));

        McpSchema.CallToolResult result = client.callTool(new McpSchema.CallToolRequest(
                "list-repo",
                Map.of("mavenGroupId", "com.example", "mavenArtifactId", "demo")));

        assertEquals(1, result.content().size());
        McpSchema.TextContent content = (McpSchema.TextContent) result.content().get(0);
        assertEquals("com.example:demo:1.0.0", content.text());
    }

    private static String javaCommand() {
        Path javaHome = Path.of(System.getProperty("java.home"));
        Path javaBinary = javaHome.resolve("bin").resolve("java");
        if (Files.isExecutable(javaBinary)) {
            return javaBinary.toString();
        }
        return "java";
    }
}
