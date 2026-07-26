package io.github.duckasteroid.agentdocs.resolve.task;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import io.github.duckasteroid.agentdocs.resolve.task.model.ModuleCoordinate;
import io.github.duckasteroid.agentdocs.resolve.task.model.SkillSource;

/**
 * Assigns the shortest safe skill-folder name to each source in a resolution run.
 *
 * <p>Prefers just the artifact name (or, for a Gradle plugin, its id's last dotted segment);
 * falls back to {@code group-artifact} (or the full plugin id) only for sources whose short name
 * collides with another source in the same run; falls back to the full, maximally-qualified name
 * (see {@link SkillSource#skillName()}) only for sources still colliding at that tier. Most
 * consumers have no name clashes at all, so the common case is a short, readable name.
 *
 * <p>Generic over {@link SkillSource} so dependency- ({@link ModuleCoordinate}) and plugin-sourced
 * candidates can be assigned names in a single pass, sharing one collision-detection namespace
 * since both land in the same skill-folder directory.
 */
final class SkillNameAssigner {
    private SkillNameAssigner() {
    }

    /**
     * Assigns a skill name to each source, escalating tiers only for sources involved in a
     * collision at the previous tier.
     *
     * @param sources candidate sources for this resolution run
     * @param <T> the concrete source type
     * @return each source mapped to its assigned skill name
     */
    static <T extends SkillSource> Map<T, String> assign(Collection<T> sources) {
        Map<T, String> assigned = new LinkedHashMap<>();

        List<T> stillUnresolved = assignTier(sources, assigned, SkillSource::artifactNameKey);
        stillUnresolved = assignTier(stillUnresolved, assigned, SkillSource::groupArtifactNameKey);
        for (T source : stillUnresolved) {
            assigned.put(source, source.skillName());
        }

        return assigned;
    }

    private static <T extends SkillSource> List<T> assignTier(
            Collection<T> candidates,
            Map<T, String> assigned,
            Function<T, String> candidateKey) {
        Map<String, List<T>> byKey = new LinkedHashMap<>();
        for (T source : candidates) {
            byKey.computeIfAbsent(candidateKey.apply(source), key -> new ArrayList<>()).add(source);
        }

        List<T> collisions = new ArrayList<>();
        for (Map.Entry<String, List<T>> entry : byKey.entrySet()) {
            if (entry.getValue().size() == 1) {
                T source = entry.getValue().get(0);
                assigned.put(source, source.finalizeSkillName(entry.getKey()));
            } else {
                collisions.addAll(entry.getValue());
            }
        }
        return collisions;
    }
}
