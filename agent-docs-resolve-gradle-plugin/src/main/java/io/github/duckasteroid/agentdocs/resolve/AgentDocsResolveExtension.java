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
     */
    public abstract Property<String> getConfigurationName();

    /**
     * Skill generation mode: {@code SINGLE_INDEX}, {@code PER_DEPENDENCY}, or {@code AUTO_THRESHOLD}.
     */
    public abstract Property<String> getSkillGenerationMode();

    /**
     * Threshold used when {@code skillGenerationMode=AUTO_THRESHOLD}.
     *
     * <p>If resolved sidecar count is less than or equal to this value, per-dependency skills are generated;
     * otherwise a single index skill is generated.
     */
    public abstract Property<Integer> getPerDependencySkillThreshold();

    /**
     * Path for the single router skill file used in {@code SINGLE_INDEX} mode.
     */
    public abstract RegularFileProperty getSkillFile();

    /**
     * Root directory for generated skill files.
     */
    public abstract DirectoryProperty getSkillsDirectory();

    /**
     * Root directory for extracted sidecar contents organized by GAV.
     */
    public abstract DirectoryProperty getResourcesDirectory();
}

