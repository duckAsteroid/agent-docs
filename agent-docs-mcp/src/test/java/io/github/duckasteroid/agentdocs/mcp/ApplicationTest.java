package io.github.duckasteroid.agentdocs.mcp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.duckasteroid.agentdocs.mcp.repo.Repository;
import io.github.duckasteroid.agentdocs.mcp.tools.Tool;
import io.github.duckasteroid.agentdocs.mcp.tools.ToolParameter;
import io.github.duckasteroid.agentdocs.mcp.tools.impl.GetDocs;
import io.modelcontextprotocol.spec.McpSchema;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ApplicationTest {
    @TempDir
    Path tempDir;

    @Test
    void serverVersionIsResolvedFromClasspathResource() {
        String version = Application.resolveServerVersion();

        assertTrue(!version.isBlank());
        assertTrue(!"unknown".equals(version));
    }

    @Test
    void serverInstructionsAreResolvedFromClasspathResource() {
        String instructions = Application.resolveServerInstructions();

        assertTrue(!instructions.isBlank());
        assertTrue(instructions.contains("read-only access"));
    }

    @Test
    void toolsAreLoadedViaServiceLoader() {
        List<String> toolNames = Application.loadTools().stream()
                .map(Tool::name)
                .toList();

        assertTrue(toolNames.contains("get_agent_docs"));
    }

    @Test
    void getAgentDocsSchemaIsGeneratedFromDeclaredParameters() {
        GetDocs tool = new GetDocs();

        String schema = tool.inputSchema();

        assertTrue(schema.contains("\"mavenGroupId\""));
        assertTrue(schema.contains("\"mavenArtifactId\""));
        assertTrue(schema.contains("\"mavenVersionId\""));
        assertTrue(schema.contains("\"mavenCoordinates\""));
        assertTrue(schema.contains("\"type\":\"string\""));
    }

    @Test
    void validateAndConvertArgumentsMapsStringKeysToMcpParameters() {
        GetDocs tool = new GetDocs();

        Map<ToolParameter<?>, Object> converted = tool.validateAndConvertArguments(
                Map.of("mavenCoordinates", "com.example:lib:1.0.0"));

        ToolParameter<?> coordinates = tool.parameters().stream()
                .filter(parameter -> parameter.name().equals("mavenCoordinates"))
                .findFirst()
                .orElse(null);

        assertNotNull(coordinates);
        assertEquals(1, converted.size());
        assertEquals("com.example:lib:1.0.0", converted.get(coordinates));
    }

    @Test
    void executeToolRejectsUnknownArguments() {
        McpSchema.CallToolResult result = Application.executeTool(
                new GetDocs(),
                new Repository(Path.of("/tmp/.agent-docs"), Path.of("/tmp/.agent-docs/repository")),
                Map.of("unexpected", "value"));

        String text = firstToolText(result);
        assertTrue(Boolean.TRUE.equals(result.isError()));
        assertTrue(text.contains("Invalid arguments"));
        assertTrue(text.contains("Unknown argument"));
    }

    @Test
    void executeToolUsesConvertedArgumentsForToolExecution() {
        McpSchema.CallToolResult result = Application.executeTool(
                new GetDocs(),
                new Repository(Path.of("/tmp/.agent-docs"), Path.of("/tmp/.agent-docs/repository")),
                Map.of("mavenCoordinates", "com.example:lib:1.0.0"));

        String text = firstToolText(result);
        assertTrue(Boolean.TRUE.equals(result.isError()));
        assertTrue(text.contains("No agent documentation available"));
    }

    @Test
    void readResourceReturnsMarkdownWithRewrittenLinks() throws IOException {
        Repository repository = new Repository(tempDir.resolve(".agent-docs"), tempDir.resolve(".agent-docs/repository"));
        Path docDirectory = repository.repositoryDirectory().resolve(Path.of("com.example", "demo", "1.0.0", "topics"));
        Files.createDirectories(docDirectory);
        Files.writeString(docDirectory.resolve("overview.md"), "See [Setup](../setup.md).\n");

        String uri = "agentdocs:///com.example/demo/1.0.0/topics/overview.md";
        McpSchema.ReadResourceResult result = repository.readResource(uri);

        assertEquals(1, result.contents().size());
        McpSchema.TextResourceContents content = (McpSchema.TextResourceContents) result.contents().get(0);
        assertEquals("text/markdown", content.mimeType());
        assertTrue(content.text().contains("agentdocs:///com.example/demo/1.0.0/setup.md"));
    }

    @Test
    void readResourceResolvesSiblingMarkdownLinksFromCurrentDirectory() throws IOException {
        Repository repository = new Repository(tempDir.resolve(".agent-docs"), tempDir.resolve(".agent-docs/repository"));
        Path docDirectory = repository.repositoryDirectory().resolve(Path.of("com.example", "demo", "1.0.0", "topics"));
        Files.createDirectories(docDirectory);
        Files.writeString(docDirectory.resolve("stuff.md"), "See [Another](another.md).\n");

        String uri = "agentdocs:///com.example/demo/1.0.0/topics/stuff.md";
        McpSchema.ReadResourceResult result = repository.readResource(uri);

        assertEquals(1, result.contents().size());
        McpSchema.TextResourceContents content = (McpSchema.TextResourceContents) result.contents().get(0);
        assertEquals("text/markdown", content.mimeType());
        assertTrue(content.text().contains("agentdocs:///com.example/demo/1.0.0/topics/another.md"));
    }

    @Test
    void readResourceResolvesDotSlashAndNestedMarkdownLinksFromCurrentDirectory() throws IOException {
        Repository repository = new Repository(tempDir.resolve(".agent-docs"), tempDir.resolve(".agent-docs/repository"));
        Path docDirectory = repository.repositoryDirectory().resolve(Path.of("com.example", "demo", "1.0.0", "topics"));
        Files.createDirectories(docDirectory.resolve("sub"));
        Files.writeString(docDirectory.resolve("stuff.md"), "See [Sibling](./another.md) and [Nested](sub/another.md).\n");

        String uri = "agentdocs:///com.example/demo/1.0.0/topics/stuff.md";
        McpSchema.ReadResourceResult result = repository.readResource(uri);

        assertEquals(1, result.contents().size());
        McpSchema.TextResourceContents content = (McpSchema.TextResourceContents) result.contents().get(0);
        assertEquals("text/markdown", content.mimeType());
        assertTrue(content.text().contains("agentdocs:///com.example/demo/1.0.0/topics/another.md"));
        assertTrue(content.text().contains("agentdocs:///com.example/demo/1.0.0/topics/sub/another.md"));
    }

    private static String firstToolText(McpSchema.CallToolResult result) {
        return result.content().stream()
                .filter(McpSchema.TextContent.class::isInstance)
                .map(McpSchema.TextContent.class::cast)
                .map(McpSchema.TextContent::text)
                .findFirst()
                .orElse("");
    }

}

