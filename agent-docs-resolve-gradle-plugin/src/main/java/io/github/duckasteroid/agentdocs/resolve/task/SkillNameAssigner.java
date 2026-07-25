package io.github.duckasteroid.agentdocs.resolve.task;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import io.github.duckasteroid.agentdocs.resolve.task.model.ModuleCoordinate;

/**
 * Assigns the shortest safe skill-folder name to each coordinate in a resolution run.
 *
 * <p>Prefers just the artifact name; falls back to {@code group-artifact} only for coordinates
 * whose artifact name collides with another coordinate in the same run; falls back to the full
 * {@code group-artifact-version} (see {@link ModuleCoordinate#skillName()}) only for coordinates
 * still colliding at that tier. Most consumers have no artifact-name clashes at all, so the
 * common case is a short, readable, artifact-only name.
 */
final class SkillNameAssigner {
    private SkillNameAssigner() {
    }

    /**
     * Assigns a skill name to each coordinate, escalating tiers only for coordinates involved in
     * a collision at the previous tier.
     *
     * @param coordinates candidate coordinates for this resolution run
     * @return each coordinate mapped to its assigned skill name
     */
    static Map<ModuleCoordinate, String> assign(Collection<ModuleCoordinate> coordinates) {
        Map<ModuleCoordinate, String> assigned = new LinkedHashMap<>();

        List<ModuleCoordinate> stillUnresolved = assignTier(coordinates, assigned, ModuleCoordinate::artifactNameKey);
        stillUnresolved = assignTier(stillUnresolved, assigned, ModuleCoordinate::groupArtifactNameKey);
        for (ModuleCoordinate coordinate : stillUnresolved) {
            assigned.put(coordinate, coordinate.skillName());
        }

        return assigned;
    }

    private static List<ModuleCoordinate> assignTier(
            Collection<ModuleCoordinate> candidates,
            Map<ModuleCoordinate, String> assigned,
            Function<ModuleCoordinate, String> candidateKey) {
        Map<String, List<ModuleCoordinate>> byKey = new LinkedHashMap<>();
        for (ModuleCoordinate coordinate : candidates) {
            byKey.computeIfAbsent(candidateKey.apply(coordinate), key -> new ArrayList<>()).add(coordinate);
        }

        List<ModuleCoordinate> collisions = new ArrayList<>();
        for (Map.Entry<String, List<ModuleCoordinate>> entry : byKey.entrySet()) {
            if (entry.getValue().size() == 1) {
                ModuleCoordinate coordinate = entry.getValue().get(0);
                assigned.put(coordinate, coordinate.finalizeSkillName(entry.getKey()));
            } else {
                collisions.addAll(entry.getValue());
            }
        }
        return collisions;
    }
}
