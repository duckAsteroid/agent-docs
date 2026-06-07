package io.github.duckasteroid.agentdocs.resolve;

import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.provider.Property;

/**
 * Extension for configuring resolver behaviour and local skill generation outputs.
 */
public abstract class AgentDocsResolveExtension {
    /**
     * Gradle configuration to inspect for direct dependencies.
     *
     * @return configuration name property
     */
    public abstract Property<String> getConfigurationName();

    /**
     * Root directory for generated skill files.
     *
     * @return skills root directory property
     */
    public abstract DirectoryProperty getSkillsDirectory();

}
