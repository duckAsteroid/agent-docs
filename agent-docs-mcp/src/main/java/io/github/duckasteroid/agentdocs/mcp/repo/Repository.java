package io.github.duckasteroid.agentdocs.mcp.repo;

import io.github.duckasteroid.agentdocs.mcp.MavenCoordinates;
import io.github.duckasteroid.agentdocs.mcp.tools.ToolException;
import io.github.duckasteroid.agentdocs.mcp.tools.impl.GetDocs;
import io.modelcontextprotocol.spec.McpSchema;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Stream;

public final class Repository {
    public static final String RESOURCE_SCHEME = "agentdocs://";
    private static final String LOCAL_REPOSITORY_PROPERTY;
    private static final String LOCAL_REPOSITORY_ENV;

    static {
        LOCAL_REPOSITORY_PROPERTY = "agentDocs.localRepository";
        LOCAL_REPOSITORY_ENV = "AGENT_DOCS_LOCAL_REPOSITORY";
    }

    private final Path baseDirectory;
    private final Path repositoryDirectory;

    public Repository(Path baseDirectory, Path repositoryDirectory) {
        this.baseDirectory = baseDirectory;
        this.repositoryDirectory = repositoryDirectory;
    }

    public static Repository resolve() {
        return resolve(System.getenv(), System.getProperties());
    }

    static Repository resolve(Map<String, String> environment, Properties systemProperties) {
        String userHome = systemProperties.getProperty("user.home", ".");
        Path defaultRepository = Path.of(userHome, ".agent-docs", "repository");

        String repositoryOverride = firstNonBlank(
                systemProperties.getProperty(LOCAL_REPOSITORY_PROPERTY),
                environment.get(LOCAL_REPOSITORY_ENV));
        Path repositoryDirectory = repositoryOverride == null
                ? defaultRepository
                : Path.of(repositoryOverride);

        Path baseDirectory = repositoryDirectory.getParent() == null
                ? repositoryDirectory
                : repositoryDirectory.getParent();
        return new Repository(baseDirectory, repositoryDirectory);
    }

    Path baseDirectory() {
        return baseDirectory;
    }

    public Path repositoryDirectory() {
        return repositoryDirectory;
    }

    public McpSchema.ReadResourceResult readResource(String resourceUri) {
        try {
            ResolvedResource resolved = parseResourceUri(resourceUri);
            var coordinates = resolved.coordinates();
            String markdown = GetDocs.readAndRewriteMarkdown(this, coordinates, resolved.path());
            return new McpSchema.ReadResourceResult(List.of(
                    new McpSchema.TextResourceContents(resourceUri, "text/markdown", markdown)));
        } catch (IllegalArgumentException | IOException exception) {
            String error = "Unable to read resource [ERROR: " + exception.getMessage() + "]";
            return new McpSchema.ReadResourceResult(List.of(
                    new McpSchema.TextResourceContents(resourceUri, "text/plain", error)));
        }
    }

    public Stream<Path> listGroups() {
        return listDirectories(repositoryDirectory).filter(Files::isDirectory);
    }

    public Stream<Path> listArtifacts(Path group) {
        Objects.requireNonNull(group, "group");
        return listDirectories(group).filter(Files::isDirectory);
    }

    public Stream<MavenCoordinates> listCoordinates(Path artifact) {
        Objects.requireNonNull(artifact, "artifact");
        return listDirectories(artifact)
                .filter(Files::isDirectory)
                .map(path -> {
                    String version = path.getFileName().toString();
                    String artifactId = artifact.getFileName().toString();
                    String groupId = artifact.getParent().getFileName().toString();
                    return new MavenCoordinates(groupId, artifactId, version);
                });
    }

    private static Stream<Path> listDirectories(Path p) {
        try {
            return Files.list(p);
        } catch (IOException e) {
            return Stream.empty();
        }
    }

    public List<MavenCoordinates> list(String groupId, String artifactId) throws ToolException {
        try (Stream<Path> groups = listGroups()) {
            return groups
                    .filter(group -> groupId == null || group.getFileName().toString().equals(groupId))
                    .flatMap(this::listArtifacts)
                    .filter(artifact -> artifactId == null || artifact.getFileName().toString().equals(artifactId))
                    .flatMap(this::listCoordinates)
                    .toList();
        }
    }

    public static String toResourceUri(Path coords, Path markdownPath) {
        Objects.requireNonNull(coords, "coords");
        Objects.requireNonNull(markdownPath, "path");

        Path normalized = markdownPath.normalize();
        if (normalized.isAbsolute() || normalized.startsWith("..")) {
            throw new IllegalArgumentException("Invalid markdown path: " + markdownPath);
        }

        String relative = normalized.toString().replace('\\', '/');
        String coordUriPath = coords.toString().replace('\\', '/');
        return RESOURCE_SCHEME + "/" + coordUriPath + "/" + relative;
    }

    public static ResolvedResource parseResourceUri(String uri) {
        Objects.requireNonNull(uri, "uri");
        if (!uri.startsWith(RESOURCE_SCHEME)) {
            throw new IllegalArgumentException("Unsupported resource URI: " + uri);
        }

        String remainder = uri.substring(RESOURCE_SCHEME.length());
        while (remainder.startsWith("/")) {
            remainder = remainder.substring(1);
        }
        String[] parts = remainder.split("/", 4);
        if (parts.length != 4) {
            throw new IllegalArgumentException("Invalid resource URI: " + uri);
        }

        String groupId = parts[0];
        String artifactId = parts[1];
        String version = parts[2];
        if (groupId.isBlank() || artifactId.isBlank() || version.isBlank()) {
            throw new IllegalArgumentException("Invalid resource URI: " + uri);
        }

        Path markdownPath = Path.of(parts[3]).normalize();
        if (markdownPath.toString().isBlank() || markdownPath.isAbsolute() || markdownPath.startsWith("..")) {
            throw new IllegalArgumentException("Invalid resource URI path: " + uri);
        }

        return new ResolvedResource(new MavenCoordinates(groupId, artifactId, version), markdownPath);
    }

    public List<ResolvedResource> listResources(MavenCoordinates coords) throws ToolException {
        Objects.requireNonNull(coords, "coords");
        var resolvedPath = repositoryDirectory.resolve(coords.asPath());
        if (Files.exists(resolvedPath)) {
            if (Files.isDirectory(resolvedPath)) {
                try (var stream = Files.walk(resolvedPath)) {
                    return stream.filter(Files::isRegularFile)
                            .map(path -> {
                                var relative = resolvedPath.relativize(path);
                                return new ResolvedResource(coords, relative);
                            })
                            .toList();
                } catch (IOException e) {
                    throw new ToolException("Unable to browse path:" + resolvedPath, e);
                }
            }
        }
        return Collections.emptyList();
    }

    public record ResolvedResource(MavenCoordinates coordinates, Path path) {
        public URI asUri() {
            var fullPath = coordinates.asPath().resolve(path);
            return URI.create(Repository.RESOURCE_SCHEME +"/" + fullPath.toString().replace('\\', '/'));
        }
    }

    private static String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return null;
    }
}

