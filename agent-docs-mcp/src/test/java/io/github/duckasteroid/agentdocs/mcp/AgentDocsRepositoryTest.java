package io.github.duckasteroid.agentdocs.mcp;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.file.Path;
import java.util.Map;
import java.util.Properties;
import org.junit.jupiter.api.Test;

class AgentDocsRepositoryTest {
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
    void runtimePathsUseDefaultRepositoryWhenNoRepositoryOverrideProvided() {
        Properties properties = new Properties();
        properties.setProperty("user.home", "/tmp/test-home");

        AgentDocsRepository repository = AgentDocsRepository.resolve(
                Map.of(),
                properties);

        assertEquals(Path.of("/tmp/test-home/.agent-docs/repository"), repository.repositoryDirectory());
    }
}

