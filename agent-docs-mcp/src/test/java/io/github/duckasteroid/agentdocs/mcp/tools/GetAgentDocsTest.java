package io.github.duckasteroid.agentdocs.mcp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.duckasteroid.agentdocs.mcp.tools.GetAgentDocs;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class GetAgentDocsTest {
    @TempDir
    Path tempDir;

    @Test
    void executeAcceptsShortFormCoordinates() {
        GetAgentDocs tool = new GetAgentDocs();

        String result = tool.execute(repository(), Map.of(
                parameter(tool, "mavenCoordinates"),
                "com.example:example-lib:1.2.3"));

        assertTrue(result.contains("com.example:example-lib:1.2.3"));
    }

    @Test
    void executeAcceptsSplitCoordinatesWhenShortFormMissing() {
        GetAgentDocs tool = new GetAgentDocs();

        String result = tool.execute(repository(), Map.of(
                parameter(tool, "mavenGroupId"), "org.acme",
                parameter(tool, "mavenArtifactId"), "core",
                parameter(tool, "mavenVersionId"), "2.0.0"));

        assertTrue(result.contains("org.acme:core:2.0.0"));
    }

    @Test
    void executeFailsWhenRequiredSplitCoordinatesAreMissing() {
        GetAgentDocs tool = new GetAgentDocs();

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> tool.execute(repository(), Map.of(
                        parameter(tool, "mavenGroupId"), "org.acme",
                        parameter(tool, "mavenArtifactId"), "core")));

        assertTrue(exception.getMessage().contains("mavenVersionId"));
    }

    @Test
    void executeReturnsAgentsMarkdownWhenPresent() throws IOException {
        GetAgentDocs tool = new GetAgentDocs();
        AgentDocsRepository repository = repository();
        Path docDirectory = repository.repositoryDirectory().resolve(Path.of("com.example", "example-lib", "1.2.3"));
        Files.createDirectories(docDirectory);
        Files.writeString(docDirectory.resolve("agents.md"), "# Agent Docs\nHello");

        String result = tool.execute(repository, Map.of(
                parameter(tool, "mavenCoordinates"),
                "com.example:example-lib:1.2.3"));

        assertTrue(result.contains("Agent Docs"));
    }

    @Test
    void executeReturnsNotFoundMessageWhenCoordinatesDoNotExist() {
        GetAgentDocs tool = new GetAgentDocs();

        String result = tool.execute(repository(), Map.of(
                parameter(tool, "mavenCoordinates"),
                "missing.group:missing-artifact:0.0.1"));

        assertTrue(result.contains("No agent documentation available"));
    }

    @Test
    void executeReturnsNotFoundWhenAgentsMarkdownIsNotARegularFile() throws IOException {
        GetAgentDocs tool = new GetAgentDocs();
        AgentDocsRepository repository = repository();
        Path docDirectory = repository.repositoryDirectory().resolve(Path.of("com.example", "example-lib", "1.2.3"));
        Files.createDirectories(docDirectory.resolve("agents.md"));

        String result = tool.execute(repository, Map.of(
                parameter(tool, "mavenCoordinates"),
                "com.example:example-lib:1.2.3"));

        assertTrue(result.contains("No agent documentation available"));
    }

    private AgentDocsRepository repository() {
        Path base = tempDir.resolve(".agent-docs");
        return new AgentDocsRepository(base, base.resolve("repository"));
    }

    private static McpParameter<?> parameter(GetAgentDocs tool, String name) {
        return tool.parameters().stream()
                .filter(parameter -> parameter.name().equals(name))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Missing parameter: " + name));
    }
}

