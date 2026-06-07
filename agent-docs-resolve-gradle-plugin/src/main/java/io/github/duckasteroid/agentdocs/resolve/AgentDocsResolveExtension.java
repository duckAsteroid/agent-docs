package io.github.duckasteroid.agentdocs.resolve;

import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.provider.Property;

/**
 * Extension for configuring resolver behavior and local skill generation outputs.
 */
public abstract class AgentDocsResolveExtension {
    /**
     * Gradle configuration to inspect for direct dependencies.
     *
     * @return configuration name property
     */
    public abstract Property<String> getConfigurationName();

    /**
     * Skill generation mode: {@code SINGLE_INDEX}, {@code PER_DEPENDENCY}, or {@code AUTO_THRESHOLD}.
     *
     * @return generation mode property
     */
    public abstract Property<String> getSkillGenerationMode();

    /**
     * Threshold used when {@code skillGenerationMode=AUTO_THRESHOLD}.
     *
     * <p>If resolved sidecar count is less than or equal to this value, per-dependency skills are generated;
     * otherwise a single index skill is generated.
     *
     * @return threshold property
     */
    public abstract Property<Integer> getPerDependencySkillThreshold();

    /**
     * Path for the single router skill file used in {@code SINGLE_INDEX} mode.
     *
     * @return single-skill output file property
     */
    public abstract RegularFileProperty getSkillFile();

    /**
     * Root directory for generated skill files.
     *
     * @return skills root directory property
     */
    public abstract DirectoryProperty getSkillsDirectory();

}
