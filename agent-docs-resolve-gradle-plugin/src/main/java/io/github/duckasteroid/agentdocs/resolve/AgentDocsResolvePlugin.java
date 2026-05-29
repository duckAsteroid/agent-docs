package io.github.duckasteroid.agentdocs.resolve;

import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.provider.Provider;

/**
 * Registers the {@code agentDocsResolve} extension and {@code resolveAgentDocs} task.
 *
 * <p>The task resolves sidecar artifacts, caches them in a local Maven-style repository,
 * extracts docs for local use, and generates skills according to configured mode.
 */
public class AgentDocsResolvePlugin implements Plugin<Project> {
    static final String LOCAL_REPOSITORY_PROPERTY = "agentDocs.localRepository";
    static final String LOCAL_REPOSITORY_ENV = "AGENT_DOCS_LOCAL_REPOSITORY";

    @Override
    public void apply(Project project) {
        AgentDocsResolveExtension extension =
                project.getExtensions().create("agentDocsResolve", AgentDocsResolveExtension.class);

        extension.getConfigurationName().convention("runtimeClasspath");
        extension.getSkillGenerationMode().convention(SkillGenerationMode.SINGLE_INDEX.name());
        extension.getPerDependencySkillThreshold().convention(10);
        extension.getSkillsDirectory().convention(
                project.getLayout().getProjectDirectory().dir(".agents/skills"));
        extension.getSkillFile().convention(
                project.getLayout().getProjectDirectory().file(".agents/skills/agent-docs.md"));
        extension.getResourcesDirectory().convention(
                project.getLayout().getProjectDirectory().dir(".agents/resources/agent-docs"));

        Provider<String> localRepositoryPathProvider = project.getProviders()
                .systemProperty(LOCAL_REPOSITORY_PROPERTY)
                .orElse(project.getProviders().environmentVariable(LOCAL_REPOSITORY_ENV))
                .orElse(System.getProperty("user.home") + "/.agent-docs/repository");

        project.getTasks().register("resolveAgentDocs", ResolveAgentDocsTask.class, task -> {
            task.setGroup("agent docs");
            task.setDescription("Resolves dependency sidecars and generates an agent-docs router skill.");
            task.getConfigurationName().set(extension.getConfigurationName());
            task.getSkillGenerationMode().set(extension.getSkillGenerationMode());
            task.getPerDependencySkillThreshold().set(extension.getPerDependencySkillThreshold());
            task.getSkillFile().set(extension.getSkillFile());
            task.getSkillsDirectory().set(extension.getSkillsDirectory());
            task.getResourcesDirectory().set(extension.getResourcesDirectory());
            task.getLocalRepositoryDirectory().set(project.getLayout().dir(localRepositoryPathProvider.map(project::file)));
        });
    }
}

