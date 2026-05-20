package io.github.duckasteroid.agentdocs.mcp;

import java.nio.file.Path;

public record MavenCoordinates(String groupId, String artifactId, String version) {
    public static MavenCoordinates fromShortForm(String shortForm) {
        String[] parts = shortForm.split(":");
        if (parts.length != 3) {
            throw new IllegalArgumentException("Invalid maven coordinates: " + shortForm);
        }
        return new MavenCoordinates(parts[0], parts[1], parts[2]);
    }

    @Override
    public String toString() {
        return groupId + ":" + artifactId + ":" + version;
    }

    public Path asPath() {
        return Path.of(groupId, artifactId, version);
    }

    public String asJson() {
        return """
                {
                    "groupId": "%s",
                    "artifactId": "%s",
                    "version": "%s"
                }
                """.formatted(groupId, artifactId, version);
    }
}
