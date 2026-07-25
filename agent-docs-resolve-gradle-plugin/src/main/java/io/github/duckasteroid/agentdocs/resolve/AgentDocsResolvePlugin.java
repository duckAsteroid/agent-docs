package io.github.duckasteroid.agentdocs.resolve;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import io.github.duckasteroid.agentdocs.resolve.task.AgentDocsDeclaration;
import io.github.duckasteroid.agentdocs.resolve.task.AgentDocsManifestReader;
import io.github.duckasteroid.agentdocs.resolve.task.InstallAgentDocsSkillTask;
import io.github.duckasteroid.agentdocs.resolve.task.ResolveAgentDocsTask;
import io.github.duckasteroid.agentdocs.resolve.task.ResolvedDependencyCollector;
import io.github.duckasteroid.agentdocs.resolve.task.SidecarArtifactResolver;
import io.github.duckasteroid.agentdocs.resolve.task.model.ModuleCoordinate;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.component.ComponentIdentifier;
import org.gradle.api.artifacts.result.ResolvedArtifactResult;

/**
 * Registers the {@code agentDocs} extension and {@code resolveAgentDocs} task.
 *
 * <p>For each direct dependency on the configured classpath, the task reads the {@code
 * Agent-Docs} manifest attribute (per {@code specification/java-conventions.md}) from that
 * dependency's own resolved jar. Dependencies without the attribute are skipped entirely, with no
 * further resolution attempt of any kind. A {@code classpath} declaration is extracted directly
 * from that same jar; a {@code maven} declaration is resolved as a separate {@code agent-docs}
 * sidecar zip.
 *
 * <p>When {@code agentDocs.includeSources} is {@code true}, the task also resolves the {@code
 * sources} classifier jar for each declared dependency and unpacks it into a {@code src/}
 * subdirectory of the skill folder. The extracted {@code SKILL.md} frontmatter records {@code
 * metadata.sources: src/} when sources are available, or {@code metadata.sources: none} when the
 * sources jar is absent from the repository.
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

        SidecarArtifactResolver sidecarArtifactResolver = new SidecarArtifactResolver(
                project.getConfigurations(), project.getDependencies(), project.getLogger());

        project.getTasks().register("resolveAgentDocs", ResolveAgentDocsTask.class, task -> {
            task.setGroup("agent docs");
            task.setDescription("Resolves agent-docs manifest declarations and extracts dependency SKILL folders.");
            task.getOutputs().upToDateWhen(spec -> false);
            task.getConfigurationName().set(extension.getConfigurationName());

            var declaredDependencies = extension.getConfigurationName().map(name -> {
                Configuration configuration = project.getConfigurations().getByName(name);
                Map<ComponentIdentifier, ModuleCoordinate> directCoordinates =
                        ResolvedDependencyCollector.collectDirectDependencyCoordinates(configuration);

                List<ResolvedArtifactResult> artifacts = new ArrayList<>(configuration.getIncoming()
                        .artifactView(view -> {
                            view.setLenient(true);
                            view.componentFilter(directCoordinates::containsKey);
                        })
                        .getArtifacts()
                        .getArtifacts());

                List<DeclaredDependency> declarations = new ArrayList<>();
                for (ResolvedArtifactResult artifact : artifacts) {
                    ModuleCoordinate coordinate = directCoordinates.get(artifact.getId().getComponentIdentifier());
                    if (coordinate == null) {
                        continue;
                    }
                    Path jarPath = artifact.getFile().toPath();
                    Optional<AgentDocsDeclaration> declaration = AgentDocsManifestReader.read(jarPath, project.getLogger());
                    if (declaration.isEmpty()) {
                        project.getLogger().debug("No Agent-Docs manifest attribute for {}", coordinate.gav());
                        continue;
                    }
                    declarations.add(new DeclaredDependency(coordinate, jarPath, declaration.get()));
                }
                return declarations;
            });

            task.getDependencyCoordinates().set(
                    declaredDependencies.map(declarations -> declarations.stream().map(d -> d.coordinate.gav()).toList()));

            task.getSchemes().set(declaredDependencies.map(declarations -> {
                Map<String, String> schemes = new LinkedHashMap<>();
                declarations.forEach(d -> schemes.put(d.coordinate.gav(), d.declaration.scheme()));
                return schemes;
            }));

            task.getClasspathJarPaths().set(declaredDependencies.map(declarations -> {
                Map<String, String> jarPaths = new LinkedHashMap<>();
                declarations.stream()
                        .filter(d -> AgentDocsDeclaration.SCHEME_CLASSPATH.equals(d.declaration.scheme()))
                        .forEach(d -> jarPaths.put(d.coordinate.gav(), d.jarPath.toAbsolutePath().toString()));
                return jarPaths;
            }));

            task.getClasspathPrefixes().set(declaredDependencies.map(declarations -> {
                Map<String, String> prefixes = new LinkedHashMap<>();
                declarations.stream()
                        .filter(d -> AgentDocsDeclaration.SCHEME_CLASSPATH.equals(d.declaration.scheme()))
                        .forEach(d -> prefixes.put(
                                d.coordinate.gav(),
                                d.declaration.payload() != null ? d.declaration.payload() : AgentDocsDeclaration.DEFAULT_CLASSPATH_PATH));
                return prefixes;
            }));

            task.getResolvedSidecarPaths().set(declaredDependencies.map(declarations -> {
                Map<String, String> resolvedSidecars = new LinkedHashMap<>();
                declarations.stream()
                        .filter(d -> AgentDocsDeclaration.SCHEME_MAVEN.equals(d.declaration.scheme()))
                        .forEach(d -> {
                            ModuleCoordinate coordinate = resolveMavenCoordinate(d);
                            Path sidecarPath = sidecarArtifactResolver.resolveSidecar(coordinate);
                            if (sidecarPath != null) {
                                resolvedSidecars.put(d.coordinate.gav(), sidecarPath.toAbsolutePath().toString());
                            }
                        });
                return resolvedSidecars;
            }));

            task.getIncludeSources().set(extension.getIncludeSources());

            task.getResolvedSourcePaths().set(extension.getIncludeSources().flatMap(includeSources -> {
                if (!includeSources) {
                    return project.getProviders().provider(LinkedHashMap::new);
                }
                return declaredDependencies.map(declarations -> {
                    Map<String, String> resolvedSources = new LinkedHashMap<>();
                    for (DeclaredDependency declared : declarations) {
                        String sourcesNotation = declared.coordinate.gav() + ":sources@jar";
                        project.getLogger().info("Attempting to resolve sources jar {}", sourcesNotation);

                        var detached = project.getConfigurations().detachedConfiguration(
                                project.getDependencies().create(sourcesNotation));
                        detached.setTransitive(false);

                        var files = detached.getIncoming()
                                .artifactView(view -> view.lenient(true))
                                .getFiles()
                                .getFiles();
                        if (files.isEmpty()) {
                            project.getLogger().info("No sources jar found for {}", declared.coordinate.gav());
                            continue;
                        }

                        var resolved = files.iterator().next();
                        project.getLogger().info("Resolved sources jar for {} at {}", declared.coordinate.gav(), resolved.toPath());
                        resolvedSources.put(declared.coordinate.gav(), resolved.getAbsolutePath());
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

    /**
     * A {@code maven}-scheme declaration's explicit {@code group:artifact:version} payload
     * overrides the coordinate to resolve; a bare {@code maven} marker falls back to the
     * dependency's own already-known coordinate, per {@code specification/java-conventions.md}
     * §5 ("A resolved dependency ... use that GAV, regardless of ... distribution").
     */
    private static ModuleCoordinate resolveMavenCoordinate(DeclaredDependency declared) {
        String payload = declared.declaration.payload();
        if (payload == null) {
            return declared.coordinate;
        }
        String[] segments = payload.split(":", 3);
        if (segments.length != 3 || segments[0].isBlank() || segments[1].isBlank() || segments[2].isBlank()) {
            return declared.coordinate;
        }
        return new ModuleCoordinate(segments[0], segments[1], segments[2]);
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

    private record DeclaredDependency(ModuleCoordinate coordinate, Path jarPath, AgentDocsDeclaration declaration) {
    }
}
