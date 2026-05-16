package io.github.duckasteroid.agentdocs.resolve;

import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.provider.Property;

public abstract class AgentDocsResolveExtension {
    public abstract Property<String> getConfigurationName();

    public abstract RegularFileProperty getOutputFile();
}

