package io.github.duckasteroid.agentdocs.mcp.repo;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertIterableEquals;

import io.github.duckasteroid.agentdocs.mcp.MavenCoordinates;
import io.github.duckasteroid.agentdocs.mcp.tools.ToolException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Properties;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class RepositoryTest {
    @TempDir
    Path tempDir;

    @Test
    void runtimePathsUseDefaultRepositoryUnderUserHomeWhenNoOverridesProvided() {
        Properties properties = new Properties();
        properties.setProperty("user.home", "/tmp/test-home");

        Repository repository = Repository.resolve(
                Map.of(),
                properties);

        assertEquals(Path.of("/tmp/test-home/.agent-docs/repository"), repository.repositoryDirectory());
        assertEquals(Path.of("/tmp/test-home/.agent-docs"), repository.baseDirectory());
    }

    @Test
    void runtimePathsPreferSystemPropertyForRepositoryOverride() {
        Properties properties = new Properties();
        properties.setProperty("user.home", "/tmp/test-home");
        properties.setProperty("agentDocs.localRepository", "/opt/agent-docs/repository");

        Repository repository = Repository.resolve(
                Map.of("AGENT_DOCS_LOCAL_REPOSITORY", "/ignored/from-env"),
                properties);

        assertEquals(Path.of("/opt/agent-docs/repository"), repository.repositoryDirectory());
        assertEquals(Path.of("/opt/agent-docs"), repository.baseDirectory());
    }

    @Test
    void runtimePathsUseDefaultRepositoryWhenNoRepositoryOverrideProvided() {
        Properties properties = new Properties();
        properties.setProperty("user.home", "/tmp/test-home");

        Repository repository = Repository.resolve(
                Map.of(),
                properties);

        assertEquals(Path.of("/tmp/test-home/.agent-docs/repository"), repository.repositoryDirectory());
    }

    @Test
    void listGroupsReturnsTopLevelGroupDirectories() throws IOException {
        Repository repository = repositoryWithCoordinates();
        Files.writeString(repository.repositoryDirectory().resolve("ignore.txt"), "ignore");

        try (Stream<Path> groups = repository.listGroups()) {
            assertIterableEquals(
                    java.util.List.of("com.example", "org.acme"),
                    groups.map(path -> path.getFileName().toString()).sorted().toList());
        }
    }

    @Test
    void listArtifactsReturnsArtifactsUnderGivenGroup() throws IOException {
        Repository repository = repositoryWithCoordinates();

        try (Stream<Path> artifacts = repository.listArtifacts(repository.repositoryDirectory().resolve("com.example"))) {
            assertIterableEquals(
                    java.util.List.of("alpha", "beta"),
                    artifacts.map(path -> path.getFileName().toString()).sorted().toList());
        }
    }

    @Test
    void listCoordinatesReturnsCoordinatesUnderGivenArtifact() throws IOException {
        Repository repository = repositoryWithCoordinates();

        try (Stream<MavenCoordinates> coordinates = repository.listCoordinates(
                repository.repositoryDirectory().resolve(Path.of("com.example", "alpha")))) {
            assertIterableEquals(
                    java.util.List.of("com.example:alpha:1.0.0", "com.example:alpha:1.1.0"),
                    coordinates.map(MavenCoordinates::toString).sorted().toList());
        }
    }

    @Test
    void listWithNoFiltersReturnsAllCoordinates() throws IOException, ToolException {
        Repository repository = repositoryWithCoordinates();

        assertIterableEquals(
                java.util.List.of(
                        "com.example:alpha:1.0.0",
                        "com.example:alpha:1.1.0",
                        "com.example:beta:2.0.0",
                        "org.acme:core:3.0.0"),
                repository.list(null, null).stream().map(MavenCoordinates::toString).sorted().toList());
    }

    @Test
    void listWithGroupFilterReturnsOnlyCoordinatesInThatGroup() throws IOException, ToolException {
        Repository repository = repositoryWithCoordinates();

        assertIterableEquals(
                java.util.List.of(
                        "com.example:alpha:1.0.0",
                        "com.example:alpha:1.1.0",
                        "com.example:beta:2.0.0"),
                repository.list("com.example", null).stream().map(MavenCoordinates::toString).sorted().toList());
    }

    @Test
    void listWithGroupAndArtifactFiltersReturnsOnlyMatchingCoordinates() throws IOException, ToolException {
        Repository repository = repositoryWithCoordinates();

        assertIterableEquals(
                java.util.List.of("com.example:alpha:1.0.0", "com.example:alpha:1.1.0"),
                repository.list("com.example", "alpha").stream().map(MavenCoordinates::toString).sorted().toList());
    }

    private Repository repositoryWithCoordinates() throws IOException {
        Path base = tempDir.resolve(".agent-docs");
        Path repositoryPath = base.resolve("repository");
        Files.createDirectories(repositoryPath.resolve(Path.of("com.example", "alpha", "1.0.0")));
        Files.createDirectories(repositoryPath.resolve(Path.of("com.example", "alpha", "1.1.0")));
        Files.createDirectories(repositoryPath.resolve(Path.of("com.example", "beta", "2.0.0")));
        Files.createDirectories(repositoryPath.resolve(Path.of("org.acme", "core", "3.0.0")));
        return new Repository(base, repositoryPath);
    }
}

