package io.github.duckasteroid.agentdocs.resolve.task.model;

/**
 * Common identity contract for anything a resolved skill folder can be generated from: a Maven
 * dependency ({@link ModuleCoordinate}) or a Gradle plugin applied via {@code plugins {}}
 * ({@link GradlePluginCoordinate}).
 *
 * <p>Implementations supply the same tiered-naming methods {@code SkillNameAssigner} escalates
 * through, so dependency- and plugin-sourced candidates can share a single collision-detection
 * pass and land in one shared skill-folder namespace.
 */
public sealed interface SkillSource permits ModuleCoordinate, GradlePluginCoordinate {
    /**
     * Shortest, most readable candidate skill name — the first tier {@code SkillNameAssigner}
     * tries.
     *
     * @return normalized, untruncated candidate key
     */
    String artifactNameKey();

    /**
     * Fallback candidate skill name used only when {@link #artifactNameKey()} collides with
     * another source in the same run.
     *
     * @return normalized, untruncated candidate key
     */
    String groupArtifactNameKey();

    /**
     * Final, maximally-qualified skill name, used only when {@link #groupArtifactNameKey()} also
     * collides. Already finalized (length-limited with a hash suffix if needed).
     *
     * @return skill-folder-compatible name
     */
    String skillName();

    /**
     * Applies the length-limit and deterministic hash-suffix rule to an already-normalized
     * candidate key.
     *
     * @param normalizedKey a normalized candidate key
     * @return skill-folder-compatible name, truncated with a hash suffix if needed
     */
    String finalizeSkillName(String normalizedKey);

    /**
     * Human-readable identity for logging (a GAV string, or {@code plugin:<id>}).
     *
     * @return display string
     */
    String describe();
}
