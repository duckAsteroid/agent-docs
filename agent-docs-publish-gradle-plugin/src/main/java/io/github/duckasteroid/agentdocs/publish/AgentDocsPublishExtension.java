package io.github.duckasteroid.agentdocs.publish;

import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.provider.Property;
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
     * How packaged docs are made discoverable to consumers: embedded in the jar's own
     * manifest/resources, or as a separate sidecar artifact. Defaults to {@link
     * AgentDocsDistribution#SIDECAR}.
     *
     * @return distribution mode property
     */
    public abstract Property<AgentDocsDistribution> getDistribution();

    /**
     * Validation rule IDs to skip during {@code validateAgentDocs}.
     *
     * @return disabled validation rule IDs
     */
    public abstract SetProperty<String> getDisabledValidationRules();
}
