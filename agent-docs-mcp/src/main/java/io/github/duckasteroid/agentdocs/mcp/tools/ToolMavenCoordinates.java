package io.github.duckasteroid.agentdocs.mcp.tools;

import io.github.duckasteroid.agentdocs.mcp.MavenCoordinates;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public class ToolMavenCoordinates {
    public static final ToolParameter<String> mavenGroupId = new ToolParameter<>("mavenGroupId", Type.STRING, "The maven groupId of the library.");
    public static final ToolParameter<String> mavenArtifactId = new ToolParameter<>("mavenArtifactId", Type.STRING, "The maven artifact id of the library.");
    public static final ToolParameter<String> mavenVersionId = new ToolParameter<>("mavenVersionId", Type.STRING, "The maven version of the library.");
    public static final ToolParameter<String> mavenShortFormGAV = new ToolParameter<>("mavenCoordinates", Type.STRING, "The maven short form GAV coordinates of the library in the form 'groupId:artifactId:version'.");

    public static MavenCoordinates fromToolArgs(Map<ToolParameter<?>, Object> args) {
        Objects.requireNonNull(args);
        return mavenShortFormGAV.extract(args).map(MavenCoordinates::fromShortForm)
                .orElseGet(() -> {
                    String groupId = mavenGroupId.extract(args).orElseThrow(() -> new IllegalArgumentException("Missing mavenGroupId"));
                    String artifactId = mavenArtifactId.extract(args).orElseThrow(() -> new IllegalArgumentException("Missing mavenArtifactId"));
                    String versionId = mavenVersionId.extract(args).orElseThrow(() -> new IllegalArgumentException("Missing mavenVersionId"));
                    return new MavenCoordinates(groupId, artifactId, versionId);
                });
    }

    public static Collection<ToolParameter<?>> parameters() {
        return List.of(mavenGroupId, mavenArtifactId, mavenVersionId, mavenShortFormGAV);
    }
}
