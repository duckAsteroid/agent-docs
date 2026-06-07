package io.github.duckasteroid.agentdocs.resolve;

import org.gradle.api.Plugin;
import org.gradle.api.Project;

/**
 * Registers the {@code agentDocs} extension and {@code resolveAgentDocs} task.
 *
 * <p>The task resolves sidecar artifacts, stores each sidecar in the local project skill tree,
 * extracts docs for local use, cleans stale marker-owned dependency skill folders, and
 * generates skills according to configured mode.
 *
 * <p>The task is intentionally configured to run on every invocation so dependency and
 * skill-folder cleanup stays current as dependencies change.
 */
public class AgentDocsResolvePlugin implements Plugin<Project> {
    @Override
    public void apply(Project project) {
        AgentDocsResolveExtension extension =
                project.getExtensions().create("agentDocs", AgentDocsResolveExtension.class);

        extension.getConfigurationName().convention("runtimeClasspath");
        extension.getSkillGenerationMode().convention(SkillGenerationMode.SINGLE_INDEX.name());
        extension.getPerDependencySkillThreshold().convention(10);
        extension.getSkillsDirectory().convention(
                project.getLayout().getProjectDirectory().dir(".agent/skills"));
        extension.getSkillFile().convention(
                project.getLayout().getProjectDirectory().file(".agent/skills/SKILL.md"));

        project.getTasks().register("resolveAgentDocs", ResolveAgentDocsTask.class, task -> {
            task.setGroup("agent docs");
            task.setDescription("Resolves dependency sidecars and generates a router SKILL.md file.");
            task.getOutputs().upToDateWhen(spec -> false);
            task.getConfigurationName().set(extension.getConfigurationName());
            task.getSkillGenerationMode().set(extension.getSkillGenerationMode());
            task.getPerDependencySkillThreshold().set(extension.getPerDependencySkillThreshold());
            task.getSkillFile().set(extension.getSkillFile());
            task.getSkillsDirectory().set(extension.getSkillsDirectory());
        });
    }
}
