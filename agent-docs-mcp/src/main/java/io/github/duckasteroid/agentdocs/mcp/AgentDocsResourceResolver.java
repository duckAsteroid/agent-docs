package io.github.duckasteroid.agentdocs.mcp;

import io.github.duckasteroid.agentdocs.mcp.tools.MavenCoordinates;

import java.nio.file.Path;
import java.util.Objects;

public final class AgentDocsResourceResolver {
    public static final String RESOURCE_SCHEME = "agentdocs://";

    private AgentDocsResourceResolver() {
    }

    public static String toResourceUri(Path coords, Path markdownPath) {
        Objects.requireNonNull(coords, "coords");
        Objects.requireNonNull(markdownPath, "markdownPath");

        Path normalized = markdownPath.normalize();
        if (normalized.isAbsolute() || normalized.startsWith("..")) {
            throw new IllegalArgumentException("Invalid markdown path: " + markdownPath);
        }

        String relative = normalized.toString().replace('\\', '/');
        String coordUriPath = coords.toString().replace('\\', '/');
        return RESOURCE_SCHEME + coordUriPath + "/" + relative;
    }

    public static ResolvedResource parseResourceUri(String uri) {
        Objects.requireNonNull(uri, "uri");
        if (!uri.startsWith(RESOURCE_SCHEME)) {
            throw new IllegalArgumentException("Unsupported resource URI: " + uri);
        }

        String remainder = uri.substring(RESOURCE_SCHEME.length());
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

        return new ResolvedResource(groupId, artifactId, version, markdownPath);
    }

    public record ResolvedResource(String groupId, String artifactId, String version, Path markdownPath) {
        public Path coordinatesPath() {
            return Path.of(groupId, artifactId, version);
        }
    }
}

