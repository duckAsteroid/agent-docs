package io.github.duckasteroid.agentdocs.publish;

import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.provider.Property;

public abstract class AgentDocsPublishExtension {
    public abstract DirectoryProperty getDocsDirectory();

    public abstract Property<String> getFormatVersion();
}

