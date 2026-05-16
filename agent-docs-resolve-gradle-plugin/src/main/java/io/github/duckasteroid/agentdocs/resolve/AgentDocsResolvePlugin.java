package io.github.duckasteroid.agentdocs.resolve;

import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.provider.Provider;

public class AgentDocsResolvePlugin implements Plugin<Project> {
    static final String LOCAL_REPOSITORY_PROPERTY = "agentDocs.localRepository";
    static final String LOCAL_REPOSITORY_ENV = "AGENT_DOCS_LOCAL_REPOSITORY";

    @Override
    public void apply(Project project) {
        AgentDocsResolveExtension extension =
                project.getExtensions().create("agentDocsResolve", AgentDocsResolveExtension.class);

        extension.getConfigurationName().convention("runtimeClasspath");
        extension.getOutputFile().convention(
                project.getLayout().getBuildDirectory().file("agent-docs-resolver/index.json"));

        Provider<String> localRepositoryPathProvider = project.getProviders()
                .systemProperty(LOCAL_REPOSITORY_PROPERTY)
                .orElse(project.getProviders().environmentVariable(LOCAL_REPOSITORY_ENV))
                .orElse(System.getProperty("user.home") + "/.agent-docs/repository");

        project.getTasks().register("resolveAgentDocs", ResolveAgentDocsTask.class, task -> {
            task.setGroup("agent docs");
            task.setDescription("Writes a starter local index for the configured classpath.");
            task.getConfigurationName().set(extension.getConfigurationName());
            task.getOutputFile().set(extension.getOutputFile());
            task.getLocalRepositoryDirectory().set(project.getLayout().dir(localRepositoryPathProvider.map(project::file)));
        });
    }
}

