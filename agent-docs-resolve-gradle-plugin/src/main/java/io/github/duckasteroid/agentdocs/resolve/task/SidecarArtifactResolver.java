package io.github.duckasteroid.agentdocs.resolve.task;

import java.nio.file.Path;
import java.util.Set;

import io.github.duckasteroid.agentdocs.resolve.task.model.ModuleCoordinate;
import org.gradle.api.Project;
import org.gradle.api.logging.Logger;

/**
 * Resolves dependency-scoped {@code agent-docs} sidecar archives.
 */
final class SidecarArtifactResolver {
    private final Project project;
    private final Logger logger;

    /**
     * Creates a resolver bound to a Gradle project and logger.
     *
     * @param project current Gradle project
     * @param logger logger for diagnostic output
     */
    SidecarArtifactResolver(Project project, Logger logger) {
        this.project = project;
        this.logger = logger;
    }

    /**
     * Attempts to resolve the {@code agent-docs} classifier zip for a dependency.
     *
     * @param coordinate dependency coordinate
     * @return resolved sidecar path, or {@code null} when unavailable
     */
    Path resolveSidecar(ModuleCoordinate coordinate) {
        var detached = project.getConfigurations().detachedConfiguration(
                project.getDependencies()
                        .create(coordinate.gav() + ":agent-docs@zip"));
        detached.setTransitive(false);

        Set<java.io.File> files;
        try {
            files = detached.getResolvedConfiguration().getLenientConfiguration().getFiles();
        } catch (Exception exception) {
            logger.info("Unable to resolve agent-docs sidecar for {}", coordinate.gav(), exception);
            return null;
        }

        if (files.isEmpty()) {
            return null;
        }

        return files.iterator().next().toPath();
    }
}
