package io.github.duckasteroid.agentdocs.resolve.task;

import java.util.LinkedHashSet;
import java.util.Set;

import io.github.duckasteroid.agentdocs.resolve.task.model.ModuleCoordinate;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.ModuleVersionIdentifier;
import org.gradle.api.artifacts.result.ResolvedComponentResult;
import org.gradle.api.artifacts.result.ResolvedDependencyResult;

/**
 * Collects resolved direct dependencies from the configured classpath.
 */
final class ResolvedDependencyCollector {
    private ResolvedDependencyCollector() {
    }

    /**
     * Resolves first-level dependencies and returns valid Maven coordinates.
     *
     * @param configuration configuration to inspect
     * @return ordered set of direct dependency coordinates
     */
    static Set<ModuleCoordinate> collect(Configuration configuration) {
        Set<ModuleCoordinate> candidates = new LinkedHashSet<>();
        ResolvedComponentResult root = configuration.getIncoming().getResolutionResult().getRootComponent().get();

        root.getDependencies().forEach(dependency -> {
            if (!(dependency instanceof ResolvedDependencyResult resolvedDependency)) {
                return;
            }
            ModuleVersionIdentifier moduleVersion = resolvedDependency.getSelected().getModuleVersion();
            if (moduleVersion == null) {
                return;
            }

            String group = moduleVersion.getGroup();
            String artifact = moduleVersion.getName();
            String version = moduleVersion.getVersion();
            if (group == null || group.isBlank() || artifact == null || artifact.isBlank() || version == null || version.isBlank()) {
                return;
            }

            candidates.add(new ModuleCoordinate(group, artifact, version));
        });
        return candidates;
    }
}
