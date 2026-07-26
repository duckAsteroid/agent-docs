package io.github.duckasteroid.agentdocs.publish.validation;

import java.io.File;
import java.util.Set;
import org.gradle.api.GradleException;

/**
 * Shared state and file-system access helpers for validation rules.
 */
public final class ValidationContext {
    private final File docsDirectory;
    private final Set<String> declaredPluginIds;

    /**
     * Creates a validation context for a single-bundle docs directory (not a Gradle plugin
     * project, or an individual plugin's own bundle directory within one).
     *
     * @param docsDirectory docs directory
     */
    public ValidationContext(File docsDirectory) {
        this(docsDirectory, Set.of());
    }

    /**
     * Creates a validation context for the docs root of a {@code java-gradle-plugin} project,
     * which must contain one bundle subdirectory per id in {@code declaredPluginIds} rather than
     * a {@code SKILL.md} of its own.
     *
     * @param docsDirectory docs directory
     * @param declaredPluginIds plugin ids declared via {@code gradlePlugin { plugins { ... } } };
     *     empty for non-plugin projects and for a single plugin's own bundle directory
     */
    public ValidationContext(File docsDirectory, Set<String> declaredPluginIds) {
        this.docsDirectory = docsDirectory;
        this.declaredPluginIds = declaredPluginIds;
    }

    /**
     * Returns the configured docs directory.
     *
     * @return docs directory
     */
    public File docsDirectory() {
        return docsDirectory;
    }

    /**
     * Plugin ids declared via {@code gradlePlugin { plugins { ... } } } for this docs root, or
     * empty when this context isn't the root of a multi-plugin-bundle layout.
     *
     * @return declared plugin ids
     */
    public Set<String> declaredPluginIds() {
        return declaredPluginIds;
    }

    /**
     * Lists docs root entries.
     *
     * @return files and directories directly under docs root
     * @throws GradleException if listing fails
     */
    public File[] rootEntries() {
        File[] entries = docsDirectory.listFiles();
        if (entries == null) {
            throw new GradleException("Unable to list contents of agent docs directory: " + docsDirectory);
        }
        return entries;
    }
}
