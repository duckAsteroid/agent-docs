package io.github.duckasteroid.agentdocs.mcp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.duckasteroid.agentdocs.mcp.repo.Repository;
import io.github.duckasteroid.agentdocs.mcp.tools.ToolException;
import io.github.duckasteroid.agentdocs.mcp.tools.ToolParameter;
import io.github.duckasteroid.agentdocs.mcp.tools.impl.GetDocs;
import io.modelcontextprotocol.spec.McpSchema;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class GetDocsTest {
    @TempDir
    Path tempDir;

    @Test
    void executeAcceptsShortFormCoordinates() throws ToolException {
        GetDocs tool = new GetDocs();

        ToolException exception = assertThrows(
                ToolException.class,
                () -> tool.execute(repository(), Map.of(
                        parameter(tool, "mavenCoordinates"),
                        "com.example:example-lib:1.2.3")));

        assertTrue(exception.getMessage().contains("No agent documentation available"));
    }

    @Test
    void executeAcceptsSplitCoordinatesWhenShortFormMissing() throws ToolException {
        GetDocs tool = new GetDocs();

        ToolException exception = assertThrows(
                ToolException.class,
                () -> tool.execute(repository(), Map.of(
                        parameter(tool, "mavenGroupId"), "org.acme",
                        parameter(tool, "mavenArtifactId"), "core",
                        parameter(tool, "mavenVersionId"), "2.0.0")));

        assertTrue(exception.getMessage().contains("No agent documentation available"));
    }

    @Test
    void executeFailsWhenRequiredSplitCoordinatesAreMissing() {
        GetDocs tool = new GetDocs();

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> tool.execute(repository(), Map.of(
                        parameter(tool, "mavenGroupId"), "org.acme",
                        parameter(tool, "mavenArtifactId"), "core")));

        assertTrue(exception.getMessage().contains("mavenVersionId"));
    }

    @Test
    void executeReturnsAgentsMarkdownWhenPresent() throws IOException, ToolException {
        GetDocs tool = new GetDocs();
        Repository repository = repository();
        Path docDirectory = repository.repositoryDirectory().resolve(Path.of("com.example", "example-lib", "1.2.3"));
        Files.createDirectories(docDirectory);
        Files.writeString(docDirectory.resolve("agents.md"), "# Agent Docs\nHello");

        McpSchema.CallToolResult result = tool.execute(repository, Map.of(
                parameter(tool, "mavenCoordinates"),
                "com.example:example-lib:1.2.3"));

        assertTrue(firstToolText(result).contains("Agent Docs"));
    }

    @Test
    void executeReturnsNotFoundMessageWhenCoordinatesDoNotExist() {
        GetDocs tool = new GetDocs();

        ToolException exception = assertThrows(
                ToolException.class,
                () -> tool.execute(repository(), Map.of(
                        parameter(tool, "mavenCoordinates"),
                        "missing.group:missing-artifact:0.0.1")));

        assertTrue(exception.getMessage().contains("No agent documentation available"));
    }

    @Test
    void executeReturnsNotFoundWhenAgentsMarkdownIsNotARegularFile() throws IOException {
        GetDocs tool = new GetDocs();
        Repository repository = repository();
        Path docDirectory = repository.repositoryDirectory().resolve(Path.of("com.example", "example-lib", "1.2.3"));
        Files.createDirectories(docDirectory.resolve("agents.md"));

        ToolException exception = assertThrows(
                ToolException.class,
                () -> tool.execute(repository, Map.of(
                        parameter(tool, "mavenCoordinates"),
                        "com.example:example-lib:1.2.3")));

        assertTrue(exception.getMessage().contains("No agent documentation available"));
    }

    private static String firstToolText(McpSchema.CallToolResult result) {
        return result.content().stream()
                .filter(McpSchema.TextContent.class::isInstance)
                .map(McpSchema.TextContent.class::cast)
                .map(McpSchema.TextContent::text)
                .findFirst()
                .orElse("");
    }

    private Repository repository() {
        Path base = tempDir.resolve(".agent-docs");
        return new Repository(base, base.resolve("repository"));
    }

    private static ToolParameter<?> parameter(GetDocs tool, String name) {
        return tool.parameters().stream()
                .filter(parameter -> parameter.name().equals(name))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Missing parameter: " + name));
    }
}

