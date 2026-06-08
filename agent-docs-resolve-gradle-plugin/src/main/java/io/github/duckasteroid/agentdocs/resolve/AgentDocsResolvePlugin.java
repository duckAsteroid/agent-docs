package io.github.duckasteroid.agentdocs.resolve;

import io.github.duckasteroid.agentdocs.resolve.task.ResolveAgentDocsTask;
import org.gradle.api.Plugin;
import org.gradle.api.Project;

/**
 * Registers the {@code agentDocs} extension and {@code resolveAgentDocs} task.
 *
 * <p>The task resolves sidecar artefacts, stores each sidecar in the local project skill tree,
 * extracts docs for local use, cleans stale marker-owned dependency skill folders, and
 * generates one skill entrypoint per resolved sidecar dependency.
 *
 * <p>The task is intentionally configured to run on every invocation so dependency and
 * skill-folder clean-up stays current as dependencies change.
 */
public class AgentDocsResolvePlugin implements Plugin<Project> {
    @Override
    public void apply(Project project) {
        AgentDocsResolveExtension extension =
                project.getExtensions().create("agentDocs", AgentDocsResolveExtension.class);

        extension.getConfigurationName().convention("compileClasspath");
        extension.getSkillsDirectory().convention(
                project.getRootProject().getLayout().getProjectDirectory().dir(".agents/skills"));

        project.getTasks().register("resolveAgentDocs", ResolveAgentDocsTask.class, task -> {
            task.setGroup("agent docs");
            task.setDescription("Resolves dependency sidecars and generates per-dependency SKILL files.");
            task.getOutputs().upToDateWhen(spec -> false);
            task.getConfigurationName().set(extension.getConfigurationName());
            task.getSkillsDirectory().set(extension.getSkillsDirectory());
        });
    }
}
