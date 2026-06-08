package io.github.duckasteroid.agentdocs.resolve;

import java.util.LinkedHashSet;
import java.util.LinkedHashMap;
import java.util.Map;

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
            var dependencyCoordinates = extension.getConfigurationName()
                    .map(name -> project.getConfigurations().getByName(name))
                    .map(configuration -> {
                        LinkedHashSet<String> coordinates = new LinkedHashSet<>();
                        configuration.getAllDependencies().forEach(dependency -> {
                            String group = dependency.getGroup();
                            String artifact = dependency.getName();
                            String version = dependency.getVersion();
                            if (group == null
                                    || group.isBlank()
                                    || artifact.isBlank()
                                    || version == null
                                    || version.isBlank()) {
                                return;
                            }
                            coordinates.add(group + ":" + artifact + ":" + version);
                        });
                        return coordinates.stream().toList();
                    });
            task.getDependencyCoordinates().set(dependencyCoordinates);
            task.getResolvedSidecarPaths().set(dependencyCoordinates.map(coordinates -> {
                Map<String, String> resolvedSidecars = new LinkedHashMap<>();
                for (String coordinate : coordinates) {
                    String sidecarNotation = coordinate + ":agent-docs@zip";
                    project.getLogger().info("Attempting to resolve agent-docs sidecar {}", sidecarNotation);

                    var detached = project.getConfigurations().detachedConfiguration(
                            project.getDependencies().create(sidecarNotation));
                    detached.setTransitive(false);

                    var files = detached.getIncoming()
                            .artifactView(view -> view.lenient(true))
                            .getFiles()
                            .getFiles();
                    if (files.isEmpty()) {
                        project.getLogger().info("No agent-docs sidecar found for {}", coordinate);
                        continue;
                    }

                    var resolved = files.iterator().next();
                    project.getLogger().info("Resolved agent-docs sidecar for {} at {}", coordinate, resolved.toPath());
                    resolvedSidecars.put(coordinate, resolved.getAbsolutePath());
                }
                return resolvedSidecars;
            }));
            task.getSkillsDirectory().set(extension.getSkillsDirectory());
        });
    }
}
