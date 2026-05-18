package io.github.duckasteroid.agentdocs.mcp.tools;

import io.github.duckasteroid.agentdocs.mcp.AgentDocsRepository;
import io.github.duckasteroid.agentdocs.mcp.AgentDocsResourceResolver;
import io.github.duckasteroid.agentdocs.mcp.AgentDocsTool;
import io.github.duckasteroid.agentdocs.mcp.MarkdownLinkRewriter;
import io.github.duckasteroid.agentdocs.mcp.McpParameter;
import io.github.duckasteroid.agentdocs.mcp.Type;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public class GetAgentDocs implements AgentDocsTool {
    private static final McpParameter<String> mavenGroupId = new McpParameter<>("mavenGroupId", Type.STRING, "The maven groupId of the library.");
    private static final McpParameter<String> mavenArtifactId = new McpParameter<>("mavenArtifactId", Type.STRING, "The maven artifact id of the library.");
    private static final McpParameter<String> mavenVersionId = new McpParameter<>("mavenVersionId", Type.STRING, "The maven version of the library.");
    private static final McpParameter<String> mavenShortFormGAV = new McpParameter<>("mavenCoordinates", Type.STRING, "The maven short form GAV coordinates of the library in the form 'groupId:artifactId:version'.");

    static MavenCoordinates fromToolArgs(Map<McpParameter<?>, Object> args) {
        Objects.requireNonNull(args);
        return mavenShortFormGAV.extract(args).map(MavenCoordinates::fromShortForm)
                .orElseGet(() -> {
                    String groupId = mavenGroupId.extract(args).orElseThrow(() -> new IllegalArgumentException("Missing mavenGroupId"));
                    String artifactId = mavenArtifactId.extract(args).orElseThrow(() -> new IllegalArgumentException("Missing mavenArtifactId"));
                    String versionId = mavenVersionId.extract(args).orElseThrow(() -> new IllegalArgumentException("Missing mavenVersionId"));
                    return new MavenCoordinates(groupId, artifactId, versionId);
                });
    }


    @Override
    public String name() {
        return "get_agent_docs";
    }

    @Override
    public String description() {
        return "Returns the agent documentation for the library specified by a set of maven coordinates. Either short for GAV or separate group, artifact and version parameters";
    }

    @Override
    public List<McpParameter<?>> parameters() {
        return List.of(mavenGroupId, mavenArtifactId, mavenVersionId, mavenShortFormGAV);
    }

    @Override
    public String execute(AgentDocsRepository repository, Map<McpParameter<?>, Object> arguments) {
        var coordinates = fromToolArgs(arguments);
        try {
            return readAndRewriteMarkdown(repository, coordinates, Path.of("agents.md"));
        } catch (IllegalArgumentException exception) {
            return "No agent documentation available for Maven coordinates: " + coordinates;
        } catch (IOException exception) {
            return "Unable to read agents.md [ERROR: " + exception.getMessage() + "]";
        }
    }

    public static String readAndRewriteMarkdown(AgentDocsRepository repository, MavenCoordinates coordinates, Path markdownPath)
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
