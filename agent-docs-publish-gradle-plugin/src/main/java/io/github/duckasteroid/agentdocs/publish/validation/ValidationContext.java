package io.github.duckasteroid.agentdocs.publish.validation;

import java.io.File;
import org.gradle.api.GradleException;

/**
 * Shared state and file-system access helpers for validation rules.
 */
public final class ValidationContext {
    private final File docsDirectory;

    /**
     * Creates a validation context for a docs directory.
     *
     * @param docsDirectory docs directory
     */
    public ValidationContext(File docsDirectory) {
        this.docsDirectory = docsDirectory;
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
