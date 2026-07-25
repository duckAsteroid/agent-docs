package io.github.duckasteroid.agentdocs.resolve.task;

import java.util.LinkedHashMap;
import java.util.Map;

import io.github.duckasteroid.agentdocs.resolve.task.model.ModuleCoordinate;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.ModuleVersionIdentifier;
import org.gradle.api.artifacts.component.ComponentIdentifier;
import org.gradle.api.artifacts.result.ResolvedComponentResult;
import org.gradle.api.artifacts.result.ResolvedDependencyResult;

/**
 * Collects resolved direct dependencies from the configured classpath.
 */
public final class ResolvedDependencyCollector {
    private ResolvedDependencyCollector() {
    }

    /**
     * Resolves first-level dependencies and correlates each with its component identifier, so
     * resolved artifact files can be filtered down to direct dependencies only.
     *
     * @param configuration configuration to inspect
     * @return direct dependency component identifiers mapped to their Maven coordinates
     */
    public static Map<ComponentIdentifier, ModuleCoordinate> collectDirectDependencyCoordinates(Configuration configuration) {
        Map<ComponentIdentifier, ModuleCoordinate> candidates = new LinkedHashMap<>();
        ResolvedComponentResult root = configuration.getIncoming().getResolutionResult().getRootComponent().get();

        root.getDependencies().forEach(dependency -> {
            if (!(dependency instanceof ResolvedDependencyResult resolvedDependency)) {
                return;
            }
            ResolvedComponentResult selected = resolvedDependency.getSelected();
            ModuleVersionIdentifier moduleVersion = selected.getModuleVersion();
            if (moduleVersion == null) {
                return;
            }

            String group = moduleVersion.getGroup();
            String artifact = moduleVersion.getName();
            String version = moduleVersion.getVersion();
            if (group == null || group.isBlank() || artifact == null || artifact.isBlank() || version == null || version.isBlank()) {
                return;
            }

            candidates.put(selected.getId(), new ModuleCoordinate(group, artifact, version));
        });
        return candidates;
    }
}
