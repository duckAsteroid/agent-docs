package io.github.duckasteroid.agentdocs.mcp;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

final class ListCachedSidecarsTool implements AgentDocsTool {
    @Override
    public String name() {
        return "agentdocs.list_cached_sidecars";
    }

    @Override
    public String description() {
        return "Lists cached agent-docs sidecar zip files under the local repository.";
    }

    @Override
    public String execute(AgentDocsRepository repository, Map<String, Object> arguments) {
        return listCachedSidecars(repository.repositoryDirectory());
    }

    static String listCachedSidecars(Path repositoryDirectory) {
        if (Files.notExists(repositoryDirectory)) {
            return "No local repository found at: " + repositoryDirectory.toAbsolutePath();
        }
        if (!Files.isDirectory(repositoryDirectory)) {
            return "Configured repository path is not a directory: " + repositoryDirectory.toAbsolutePath();
        }

        List<String> sidecars;
        try (Stream<Path> walk = Files.walk(repositoryDirectory)) {
            sidecars = walk
                    .filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().endsWith("-agent-docs.zip"))
                    .map(path -> repositoryDirectory.relativize(path).toString())
                    .sorted(Comparator.naturalOrder())
                    .collect(Collectors.toList());
        } catch (IOException exception) {
            return "Failed to inspect repository at " + repositoryDirectory.toAbsolutePath() + ": " + exception.getMessage();
        }

        if (sidecars.isEmpty()) {
            return "No cached sidecar artifacts found under: " + repositoryDirectory.toAbsolutePath();
        }

        return String.join(System.lineSeparator(), sidecars);
    }
}

