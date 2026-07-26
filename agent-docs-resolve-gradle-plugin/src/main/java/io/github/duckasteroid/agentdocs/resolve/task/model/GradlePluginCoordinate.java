package io.github.duckasteroid.agentdocs.resolve.task.model;

/**
 * Identifies a Gradle plugin applied via the {@code plugins {}} block, discovered by walking
 * applied plugin classes and reading each one's jar via its classloader — there is no Maven GAV
 * to key off, since plugin jars aren't resolved as normal project dependencies.
 *
 * @param pluginId the plugin's id, e.g. {@code io.github.duckasteroid.agent-docs.publish}
 */
public record GradlePluginCoordinate(String pluginId) implements SkillSource {
    private static final String FALLBACK_SKILL_NAME = "plugin";

    /**
     * {@inheritDoc}
     */
    @Override
    public String describe() {
        return "plugin:" + pluginId;
    }

    /**
     * Untruncated, normalized candidate skill name using only the last dot-separated segment of
     * the plugin id (e.g. {@code publish} from {@code io.github.duckasteroid.agent-docs.publish})
     * — the shortest and most readable tier.
     *
     * @return normalized candidate key
     */
    @Override
    public String artifactNameKey() {
        int lastDot = pluginId.lastIndexOf('.');
        String lastSegment = lastDot < 0 ? pluginId : pluginId.substring(lastDot + 1);
        return SkillNameNormalization.normalize(lastSegment, FALLBACK_SKILL_NAME);
    }

    /**
     * Untruncated, normalized candidate skill name using the full plugin id — the fallback tier
     * when {@link #artifactNameKey()} collides with another source in the same run. Since plugin
     * ids are already globally unique, this tier is effectively unique on its own.
     *
     * @return normalized candidate key
     */
    @Override
    public String groupArtifactNameKey() {
        return SkillNameNormalization.normalize(pluginId, FALLBACK_SKILL_NAME);
    }

    /**
     * Final, maximally-qualified skill name. Identical to {@link #groupArtifactNameKey()} (there's
     * no further-qualifying detail beyond the plugin id itself), finalized with the length-limit
     * and hash-suffix rule.
     *
     * @return skill-folder-compatible name
     */
    @Override
    public String skillName() {
        return finalizeSkillName(groupArtifactNameKey());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String finalizeSkillName(String normalizedKey) {
        return SkillNameNormalization.finalizeSkillName(normalizedKey, pluginId, FALLBACK_SKILL_NAME);
    }
}
