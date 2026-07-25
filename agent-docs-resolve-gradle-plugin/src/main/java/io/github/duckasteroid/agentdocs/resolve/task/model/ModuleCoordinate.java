package io.github.duckasteroid.agentdocs.resolve.task.model;

/**
 * Represents a Maven GAV coordinate for a library/dependency.
 */
public record ModuleCoordinate(String group, String artifact, String version) implements SkillSource {
    private static final String FALLBACK_SKILL_NAME = "dep";

    /**
     * Returns canonical Maven GAV text form.
     *
     * @return {@code group:artifact:version}
     */
    public String gav() {
        return group + ":" + artifact + ":" + version;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String describe() {
        return gav();
    }

    /**
     * Generates a skill-folder-compatible identifier derived from the full GAV. This is the
     * last-resort tier used by {@code SkillNameAssigner} only when a shorter, more readable
     * candidate (see {@link #artifactNameKey()}, {@link #groupArtifactNameKey()}) collides with
     * another coordinate being resolved in the same run.
     *
     * <p>The generated name is deterministic, lower-case, and constrained to skill naming
     * requirements. Names longer than the maximum length are truncated with a hash suffix.
     *
     * @return normalized skill folder name
     */
    @Override
    public String skillName() {
        return finalizeSkillName(SkillNameNormalization.normalize(group + "-" + artifact + "-" + version, FALLBACK_SKILL_NAME));
    }

    /**
     * Untruncated, normalized candidate skill name using only the artifact name — the shortest
     * and most readable tier. Multiple coordinates with different groups can share the same
     * artifact name, so callers must check for collisions across the full candidate set before
     * relying on this tier (see {@code SkillNameAssigner}).
     *
     * @return normalized artifact-only candidate key
     */
    @Override
    public String artifactNameKey() {
        return SkillNameNormalization.normalize(artifact, FALLBACK_SKILL_NAME);
    }

    /**
     * Untruncated, normalized candidate skill name using group and artifact — the fallback tier
     * when {@link #artifactNameKey()} collides with another coordinate. Two different versions of
     * the same module would still collide at this tier, but a single resolved configuration only
     * ever selects one version per group:artifact, so this is effectively unique in practice.
     *
     * @return normalized group-artifact candidate key
     */
    @Override
    public String groupArtifactNameKey() {
        return SkillNameNormalization.normalize(group + "-" + artifact, FALLBACK_SKILL_NAME);
    }

    /**
     * Applies the length-limit and deterministic hash-suffix rule to a chosen, already-normalized
     * candidate key (from {@link #artifactNameKey()}, {@link #groupArtifactNameKey()}, or a raw
     * {@code group-artifact-version} string), producing the final skill folder name.
     *
     * @param normalizedKey a normalized candidate key
     * @return skill-folder-compatible name, truncated with a hash suffix if needed
     */
    @Override
    public String finalizeSkillName(String normalizedKey) {
        return SkillNameNormalization.finalizeSkillName(normalizedKey, gav(), FALLBACK_SKILL_NAME);
    }
}
