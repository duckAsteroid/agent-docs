package io.github.duckasteroid.agentdocs.resolve;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashSet;
import java.util.LinkedHashMap;
import java.util.Map;

import io.github.duckasteroid.agentdocs.resolve.task.InstallAgentDocsSkillTask;
import io.github.duckasteroid.agentdocs.resolve.task.ResolveAgentDocsTask;
import org.gradle.api.Plugin;
import org.gradle.api.Project;

/**
 * Registers the {@code agentDocs} extension and {@code resolveAgentDocs} task.
 *
 * <p>The task resolves sidecar artefacts, extracts docs for local use into the project skill
 * tree, and cleans stale marker-owned dependency skill folders.
 *
 * <p>When {@code agentDocs.includeSources} is {@code true}, the task also resolves the
 * {@code sources} classifier jar for each dependency that has an agent-docs sidecar and unpacks
 * it into a {@code src/} subdirectory of the skill folder. The extracted {@code SKILL.md}
 * frontmatter records {@code metadata.sources: src/} when sources are available, or
 * {@code metadata.sources: none} when the sources jar is absent from the repository.
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
        extension.getIncludeSources().convention(false);

        project.getTasks().register("resolveAgentDocs", ResolveAgentDocsTask.class, task -> {
            task.setGroup("agent docs");
            task.setDescription("Resolves dependency sidecars and extracts dependency SKILL folders.");
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

            task.getIncludeSources().set(extension.getIncludeSources());

            task.getResolvedSourcePaths().set(extension.getIncludeSources().flatMap(includeSources -> {
                if (!includeSources) {
                    return project.getProviders().provider(LinkedHashMap::new);
                }
                return dependencyCoordinates.map(coordinates -> {
                    Map<String, String> resolvedSources = new LinkedHashMap<>();
                    for (String coordinate : coordinates) {
                        String sourcesNotation = coordinate + ":sources@jar";
                        project.getLogger().info("Attempting to resolve sources jar {}", sourcesNotation);

                        var detached = project.getConfigurations().detachedConfiguration(
                                project.getDependencies().create(sourcesNotation));
                        detached.setTransitive(false);

                        var files = detached.getIncoming()
                                .artifactView(view -> view.lenient(true))
                                .getFiles()
                                .getFiles();
                        if (files.isEmpty()) {
                            project.getLogger().info("No sources jar found for {}", coordinate);
                            continue;
                        }

                        var resolved = files.iterator().next();
                        project.getLogger().info("Resolved sources jar for {} at {}", coordinate, resolved.toPath());
                        resolvedSources.put(coordinate, resolved.getAbsolutePath());
                    }
                    return resolvedSources;
                });
            }));

            task.getSkillsDirectory().set(extension.getSkillsDirectory());
        });

        String skillContent = loadSkillResource();
        project.getTasks().register("installAgentDocsResolveSkill", InstallAgentDocsSkillTask.class, task -> {
            task.setGroup("agent docs");
            task.setDescription("Installs the agent-docs resolve plugin usage guide into the local agent skills folder.");
            task.getSkillContent().set(skillContent);
            task.getOutputFile().convention(
                    extension.getSkillsDirectory().file("agent-docs-resolve/SKILL.md"));
        });
    }

    private static String loadSkillResource() {
        try (InputStream is = AgentDocsResolvePlugin.class.getResourceAsStream("SKILL.md")) {
            if (is == null) {
                throw new IllegalStateException("Bundled SKILL.md resource not found in plugin jar");
            }
            return new String(is.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to read bundled SKILL.md resource", e);
        }
    }
}
