package io.github.duckasteroid.agentdocs.mcp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Properties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class AgentDocsMcpApplicationTest {
    @TempDir
    Path tempDir;

    @Test
    void runtimePathsUseDefaultRepositoryUnderUserHomeWhenNoOverridesProvided() {
        Properties properties = new Properties();
        properties.setProperty("user.home", "/tmp/test-home");

        AgentDocsRepository repository = AgentDocsRepository.resolve(
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

        AgentDocsRepository repository = AgentDocsRepository.resolve(
                Map.of("AGENT_DOCS_LOCAL_REPOSITORY", "/ignored/from-env"),
                properties);

        assertEquals(Path.of("/opt/agent-docs/repository"), repository.repositoryDirectory());
        assertEquals(Path.of("/opt/agent-docs"), repository.baseDirectory());
    }

    @Test
    void runtimePathsIgnoreCliArgumentsForRepositoryResolution() {
        Properties properties = new Properties();
        properties.setProperty("user.home", "/tmp/test-home");

        AgentDocsRepository repository = AgentDocsRepository.resolve(
                Map.of(),
                properties);

        assertEquals(Path.of("/tmp/test-home/.agent-docs/repository"), repository.repositoryDirectory());
    }

    @Test
    void listCachedSidecarsToolReturnsRepositoryRelativeSidecarPaths() throws IOException {
        Path repositoryDirectory = tempDir.resolve(".agent-docs/repository");
        Path sidecarA = repositoryDirectory.resolve("com/example/lib-a/1.0.0/lib-a-1.0.0-agent-docs.zip");
        Path sidecarB = repositoryDirectory.resolve("org/acme/lib-b/2.1.0/lib-b-2.1.0-agent-docs.zip");
        Files.createDirectories(sidecarA.getParent());
        Files.createDirectories(sidecarB.getParent());
        Files.writeString(sidecarA, "a");
        Files.writeString(sidecarB, "b");

        String text = ListCachedSidecarsTool.listCachedSidecars(repositoryDirectory);

        assertTrue(text.contains("com/example/lib-a/1.0.0/lib-a-1.0.0-agent-docs.zip"));
        assertTrue(text.contains("org/acme/lib-b/2.1.0/lib-b-2.1.0-agent-docs.zip"));
    }

    @Test
    void resolvePathsTextIncludesAbsoluteBaseAndRepository() {
        AgentDocsRepository repository = new AgentDocsRepository(
                Path.of("/tmp/.agent-docs"),
                Path.of("/tmp/.agent-docs/repository"));

        String text = new ResolvePathsTool().execute(repository, Map.of());

        assertTrue(text.contains("baseDirectory=/tmp/.agent-docs"));
        assertTrue(text.contains("repositoryDirectory=/tmp/.agent-docs/repository"));
    }
}

