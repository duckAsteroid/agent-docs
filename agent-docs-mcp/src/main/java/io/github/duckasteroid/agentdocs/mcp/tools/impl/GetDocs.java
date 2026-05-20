package io.github.duckasteroid.agentdocs.mcp.tools.impl;

import io.github.duckasteroid.agentdocs.mcp.MavenCoordinates;
import io.github.duckasteroid.agentdocs.mcp.repo.Repository;
import io.github.duckasteroid.agentdocs.mcp.tools.Tool;
import io.github.duckasteroid.agentdocs.mcp.repo.MarkdownLinkRewriter;
import io.github.duckasteroid.agentdocs.mcp.tools.ToolException;
import io.github.duckasteroid.agentdocs.mcp.tools.ToolParameter;
import io.github.duckasteroid.agentdocs.mcp.tools.ToolMavenCoordinates;
import io.modelcontextprotocol.spec.McpSchema;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static io.github.duckasteroid.agentdocs.mcp.tools.ToolMavenCoordinates.fromToolArgs;

public class GetDocs implements Tool {

    @Override
    public String name() {
        return "get_agent_docs";
    }

    @Override
    public String description() {
        return "Returns the agent documentation for the library specified by a set of maven coordinates. Either short for GAV or separate group, artifact and version parameters";
    }

    @Override
    public List<ToolParameter<?>> parameters() {
        return List.copyOf(ToolMavenCoordinates.parameters());
    }


    @Override
    public McpSchema.CallToolResult execute(Repository repository, Map<ToolParameter<?>, Object> arguments) throws ToolException {
        var coordinates = fromToolArgs(arguments);
        try {
            var builder = McpSchema.CallToolResult.builder();
            builder.addTextContent(readAndRewriteMarkdown(repository, coordinates, Path.of("agents.md")));
            return builder.build();
        } catch (IllegalArgumentException exception) {
            throw new ToolException("No agent documentation available for Maven coordinates: " + coordinates);
        } catch (IOException exception) {
            throw new ToolException("Unable to read agents.md", exception);
        }
    }

    public static String readAndRewriteMarkdown(Repository repository, MavenCoordinates coordinates, Path markdownPath)
            throws IOException {
        Path coordinatePath = coordinates.asPath();
        Path normalizedMarkdownPath = markdownPath.normalize();
        if (normalizedMarkdownPath.isAbsolute() || normalizedMarkdownPath.startsWith("..")) {
            throw new IllegalArgumentException("Invalid markdown path: " + markdownPath);
        }

        Path baseDirectory = repository.repositoryDirectory().resolve(coordinatePath);
        Path markdownFile = baseDirectory.resolve(normalizedMarkdownPath).normalize();
        if (!markdownFile.startsWith(baseDirectory.normalize()) || !Files.isRegularFile(markdownFile)) {
            throw new IllegalArgumentException("No markdown found for path: " + markdownPath);
        }

        String markdown = Files.readString(markdownFile);
        return MarkdownLinkRewriter.rewriteRelativeMarkdownLinks(
                markdown,
                coordinates,
                normalizedMarkdownPath);
    }
}
