package io.github.duckasteroid.agentdocs.publish;

import java.io.File;
import java.util.Arrays;
import org.gradle.api.GradleException;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.Task;
import org.gradle.api.tasks.TaskProvider;
import org.gradle.api.tasks.bundling.Zip;
import org.gradle.api.publish.PublishingExtension;
import org.gradle.api.publish.maven.MavenPublication;

public class AgentDocsPublishPlugin implements Plugin<Project> {
    @Override
    public void apply(Project project) {
        AgentDocsPublishExtension extension =
                project.getExtensions().create("agentDocs", AgentDocsPublishExtension.class);

        extension.getDocsDirectory().convention(project.getLayout().getProjectDirectory().dir("src/agentDocs"));
        extension.getFormatVersion().convention("1");

        TaskProvider<Task> validateAgentDocs = project.getTasks().register("validateAgentDocs", task -> {
            task.setGroup("agent docs");
            task.setDescription("Validates that the agent docs directory exists.");
            task.doLast(ignored -> {
                File docsDirectory = extension.getDocsDirectory().get().getAsFile();
                if (!docsDirectory.exists()) {
                    throw new GradleException("Agent docs directory does not exist: " + docsDirectory);
                }
                File[] rootEntries = docsDirectory.listFiles();
                if (rootEntries == null) {
                    throw new GradleException("Unable to list contents of agent docs directory: " + docsDirectory);
                }
                long agentsEntrypointCount = Arrays.stream(rootEntries)
                        .filter(File::isFile)
                        .map(File::getName)
                        .filter(fileName -> fileName.equalsIgnoreCase("agents.md"))
                        .count();
                if (agentsEntrypointCount == 0) {
                    throw new GradleException("Agent docs directory must contain AGENTS.md (or agents.md): " + docsDirectory);
                }
                if (agentsEntrypointCount > 1) {
                    throw new GradleException("Agent docs directory must contain exactly one AGENTS.md file (case-insensitive): " + docsDirectory);
                }
            });
        });

        TaskProvider<Zip> packageAgentDocs = project.getTasks().register("packageAgentDocs", Zip.class, task -> {
            task.setGroup("agent docs");
            task.setDescription("Packages agent-ready docs as an agent-docs sidecar archive.");
            task.getArchiveBaseName().convention(project.provider(project::getName));
            task.getArchiveClassifier().set("agent-docs");
            task.getDestinationDirectory().set(project.getLayout().getBuildDirectory().dir("agent-docs"));
            task.dependsOn(validateAgentDocs);
            task.from(extension.getDocsDirectory(), spec -> spec.eachFile(fileCopyDetails -> {
                boolean isRootFile = fileCopyDetails.getRelativePath().getSegments().length == 1;
                if (isRootFile && fileCopyDetails.getName().equalsIgnoreCase("agents.md")) {
                    fileCopyDetails.setName("agents.md");
                }
            }));
        });

        project.getPluginManager().withPlugin("maven-publish", ignored -> project
                .getExtensions()
                .getByType(PublishingExtension.class)
                .getPublications()
                .withType(MavenPublication.class)
                .configureEach(publication -> publication.artifact(packageAgentDocs)));

        project.getTasks().matching(task -> task.getName().equals("assemble")).configureEach(task -> task.dependsOn(packageAgentDocs));
    }
}

