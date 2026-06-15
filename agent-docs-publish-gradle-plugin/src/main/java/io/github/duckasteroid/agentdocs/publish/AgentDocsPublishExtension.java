package io.github.duckasteroid.agentdocs.publish;

import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.provider.SetProperty;

/**
 * Extension for configuring publish-side agent docs packaging.
 */
public abstract class AgentDocsPublishExtension {
    /**
     * Directory containing the agent docs source tree to package.
     *
     * @return docs source directory property
     */
    public abstract DirectoryProperty getDocsDirectory();

    /**
     * Validation rule IDs to skip during {@code validateAgentDocs}.
     *
     * @return disabled validation rule IDs
     */
    public abstract SetProperty<String> getDisabledValidationRules();

    /**
     * Root directory for generated skill files.
     *
     * @return skills root directory property
     */
    public abstract DirectoryProperty getSkillsDirectory();
}
