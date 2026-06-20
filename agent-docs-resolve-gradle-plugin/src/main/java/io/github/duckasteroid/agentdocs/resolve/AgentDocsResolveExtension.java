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

    /**
     * When {@code true}, the resolver also attempts to fetch the {@code sources} classifier jar
     * for each dependency and unpacks it into a {@code src/} subdirectory of the skill folder.
     * The extracted {@code SKILL.md} frontmatter will include {@code metadata.sources: src/} when
     * sources are available, or {@code metadata.sources: none} when the artifact has no sources jar.
     *
     * <p>Defaults to {@code false}.
     *
     * @return include-sources flag property
     */
    public abstract Property<Boolean> getIncludeSources();

}
