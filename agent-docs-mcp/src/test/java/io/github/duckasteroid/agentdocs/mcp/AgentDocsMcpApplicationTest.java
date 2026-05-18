package io.github.duckasteroid.agentdocs.mcp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.duckasteroid.agentdocs.mcp.tools.GetAgentDocs;
import io.modelcontextprotocol.spec.McpSchema;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class AgentDocsMcpApplicationTest {
    @TempDir
    Path tempDir;

    @Test
    void serverVersionIsResolvedFromClasspathResource() {
        String version = AgentDocsMcpApplication.resolveServerVersion();

        assertTrue(!version.isBlank());
        assertTrue(!"unknown".equals(version));
    }

    @Test
    void serverInstructionsAreResolvedFromClasspathResource() {
        String instructions = AgentDocsMcpApplication.resolveServerInstructions();

        assertTrue(!instructions.isBlank());
        assertTrue(instructions.contains("read-only access"));
    }

    @Test
    void toolsAreLoadedViaServiceLoader() {
        List<String> toolNames = AgentDocsMcpApplication.loadTools().stream()
                .map(AgentDocsTool::name)
                .toList();

        assertTrue(toolNames.contains("get_agent_docs"));
    }

    @Test
    void getAgentDocsSchemaIsGeneratedFromDeclaredParameters() {
        GetAgentDocs tool = new GetAgentDocs();

        String schema = tool.inputSchema();

        assertTrue(schema.contains("\"mavenGroupId\""));
        assertTrue(schema.contains("\"mavenArtifactId\""));
        assertTrue(schema.contains("\"mavenVersionId\""));
        assertTrue(schema.contains("\"mavenCoordinates\""));
        assertTrue(schema.contains("\"type\":\"string\""));
    }

    @Test
    void validateAndConvertArgumentsMapsStringKeysToMcpParameters() {
        GetAgentDocs tool = new GetAgentDocs();

        Map<McpParameter<?>, Object> converted = tool.validateAndConvertArguments(
                Map.of("mavenCoordinates", "com.example:lib:1.0.0"));

        McpParameter<?> coordinates = tool.parameters().stream()
                .filter(parameter -> parameter.name().equals("mavenCoordinates"))
                .findFirst()
                .orElse(null);

        assertNotNull(coordinates);
        assertEquals(1, converted.size());
        assertEquals("com.example:lib:1.0.0", converted.get(coordinates));
    }

    @Test
    void executeToolRejectsUnknownArguments() {
        String text = AgentDocsMcpApplication.executeTool(
                new GetAgentDocs(),
                new AgentDocsRepository(Path.of("/tmp/.agent-docs"), Path.of("/tmp/.agent-docs/repository")),
                Map.of("unexpected", "value"));

        assertTrue(text.contains("Invalid arguments"));
        assertTrue(text.contains("Unknown argument"));
    }

    @Test
    void executeToolUsesConvertedArgumentsForToolExecution() {
        String text = AgentDocsMcpApplication.executeTool(
                new GetAgentDocs(),
                new AgentDocsRepository(Path.of("/tmp/.agent-docs"), Path.of("/tmp/.agent-docs/repository")),
                Map.of("mavenCoordinates", "com.example:lib:1.0.0"));

        assertTrue(text.contains("No agent documentation available"));
    }

    @Test
    void readResourceReturnsMarkdownWithRewrittenLinks() throws IOException {
        AgentDocsRepository repository = new AgentDocsRepository(tempDir.resolve(".agent-docs"), tempDir.resolve(".agent-docs/repository"));
        Path docDirectory = repository.repositoryDirectory().resolve(Path.of("com.example", "demo", "1.0.0", "topics"));
        Files.createDirectories(docDirectory);
        Files.writeString(docDirectory.resolve("overview.md"), "See [Setup](../setup.md).\n");

        String uri = "agentdocs://com.example/demo/1.0.0/topics/overview.md";
        McpSchema.ReadResourceResult result = AgentDocsMcpApplication.readResource(repository, uri);

        assertEquals(1, result.contents().size());
        McpSchema.TextResourceContents content = (McpSchema.TextResourceContents) result.contents().get(0);
        assertEquals("text/markdown", content.mimeType());
        assertTrue(content.text().contains("agentdocs://com.example/demo/1.0.0/setup.md"));
    }
}

