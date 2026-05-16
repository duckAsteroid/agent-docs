package io.github.duckasteroid.agentdocs.mcp;

import java.nio.file.Path;
import java.util.Map;
import java.util.Properties;

final class AgentDocsRepository {
    private static final String LOCAL_REPOSITORY_PROPERTY;
    private static final String LOCAL_REPOSITORY_ENV;

    static {
        LOCAL_REPOSITORY_PROPERTY = "agentDocs.localRepository";
        LOCAL_REPOSITORY_ENV = "AGENT_DOCS_LOCAL_REPOSITORY";
    }

    private final Path baseDirectory;
    private final Path repositoryDirectory;

    AgentDocsRepository(Path baseDirectory, Path repositoryDirectory) {
        this.baseDirectory = baseDirectory;
        this.repositoryDirectory = repositoryDirectory;
    }

    static AgentDocsRepository resolve() {
        return resolve(System.getenv(), System.getProperties());
    }

    static AgentDocsRepository resolve(Map<String, String> environment, Properties systemProperties) {
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
        return new AgentDocsRepository(baseDirectory, repositoryDirectory);
    }

    Path baseDirectory() {
        return baseDirectory;
    }

    Path repositoryDirectory() {
        return repositoryDirectory;
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

