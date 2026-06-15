package io.github.duckasteroid.agentdocs.resolve.task;

import java.io.IOException;
import java.nio.file.Files;
import org.gradle.api.DefaultTask;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.CacheableTask;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.TaskAction;

/**
 * Writes the bundled agent-docs resolve plugin usage guide (SKILL.md) into the consuming
 * project's local agent skills folder so that local LLM agents can discover how to use the plugin.
 */
@CacheableTask
public abstract class InstallAgentDocsSkillTask extends DefaultTask {

    @Input
    public abstract Property<String> getSkillContent();

    @OutputFile
    public abstract RegularFileProperty getOutputFile();

    @TaskAction
    void install() throws IOException {
        var outputFile = getOutputFile().get().getAsFile().toPath();
        Files.createDirectories(outputFile.getParent());
        Files.writeString(outputFile, getSkillContent().get());
    }
}
