package io.github.duckasteroid.agentdocs.resolve.task;

import java.nio.file.Path;
import java.util.Set;

import io.github.duckasteroid.agentdocs.resolve.task.model.ModuleCoordinate;
import org.gradle.api.artifacts.ConfigurationContainer;
import org.gradle.api.artifacts.dsl.DependencyHandler;
import org.gradle.api.logging.Logger;

/**
 * Resolves dependency-scoped {@code agent-docs} sidecar archives.
 */
final class SidecarArtifactResolver {
    private final ConfigurationContainer configurations;
    private final DependencyHandler dependencies;
    private final Logger logger;

    /**
     * Creates a resolver bound to a Gradle project and logger.
     *
     * @param configurations configuration container for detached resolution
     * @param dependencies dependency factory
     * @param logger logger for diagnostic output
     */
    SidecarArtifactResolver(ConfigurationContainer configurations, DependencyHandler dependencies, Logger logger) {
        this.configurations = configurations;
        this.dependencies = dependencies;
        this.logger = logger;
    }

    /**
     * Attempts to resolve the {@code agent-docs} classifier zip for a dependency.
     *
     * @param coordinate dependency coordinate
     * @return resolved sidecar path, or {@code null} when unavailable
     */
    Path resolveSidecar(ModuleCoordinate coordinate) {
        var detached = configurations.detachedConfiguration(
                dependencies.create(coordinate.gav() + ":agent-docs@zip"));
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
