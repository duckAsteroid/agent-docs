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
        String sidecarNotation = coordinate.gav() + ":agent-docs@zip";
        logger.info("Attempting to resolve agent-docs sidecar {}", sidecarNotation);
        var detached = configurations.detachedConfiguration(
                dependencies.create(sidecarNotation));
        detached.setTransitive(false);

        Set<java.io.File> files;
        try {
            var lenientConfiguration = detached.getResolvedConfiguration().getLenientConfiguration();
            files = lenientConfiguration.getFiles();

            var unresolvedDependencies = lenientConfiguration.getUnresolvedModuleDependencies();
            if (!unresolvedDependencies.isEmpty()) {
                logger.info(
                        "agent-docs sidecar resolution for {} reported {} unresolved module(s)",
                        coordinate.gav(),
                        unresolvedDependencies.size());
                unresolvedDependencies.forEach(unresolved ->
                        logger.debug(
                                "Unresolved sidecar module during {} resolution: {}",
                                coordinate.gav(),
                                unresolved.getSelector()));
            }
        } catch (Exception exception) {
            logger.info("Unable to resolve agent-docs sidecar for {}", coordinate.gav(), exception);
            return null;
        }

        if (files.isEmpty()) {
            logger.info("No agent-docs sidecar found for {}", coordinate.gav());
            return null;
        }

        if (files.size() > 1) {
            logger.info("Resolved multiple sidecar files for {}; selecting first match", coordinate.gav());
        }

        Path resolvedSidecar = files.iterator().next().toPath();
        logger.info("Resolved agent-docs sidecar for {} at {}", coordinate.gav(), resolvedSidecar);

        return resolvedSidecar;
    }
}
