package io.github.duckasteroid.agentdocs.resolve.task;

import java.io.IOException;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

import io.github.duckasteroid.agentdocs.resolve.task.model.ModuleCoordinate;
import io.github.duckasteroid.agentdocs.resolve.task.model.SkillEntry;
import org.gradle.api.DefaultTask;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.provider.ListProperty;
import org.gradle.api.provider.MapProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.OutputDirectory;
import org.gradle.api.tasks.TaskAction;
import org.gradle.work.DisableCachingByDefault;

/**
 * Resolves {@code Agent-Docs} manifest declarations (per {@code specification/java-conventions.md})
 * for direct dependencies on the configured classpath, extracts docs into dependency-scoped
 * directories in the local project skills tree, marks managed folders with {@code .agent-docs},
 * and removes stale marker-owned dependency folders.
 *
 * <p>Only dependencies whose resolved jar carries an {@code Agent-Docs} manifest attribute are
 * considered — dependencies without it are skipped with no further resolution attempt of any
 * kind. A {@code classpath} declaration is extracted directly from the dependency's own jar; a
 * {@code maven} declaration is resolved as a separate {@code agent-docs} sidecar zip.
 *
 * <p>When {@link #getIncludeSources()} is {@code true}, the task also resolves the {@code sources}
 * classifier jar for each declared dependency and unpacks it into a {@code src/} subdirectory of
 * the skill folder. The extracted {@code SKILL.md} frontmatter records {@code metadata.sources:
 * src/} when sources are available, or {@code metadata.sources: none} when the artifact has no
 * sources jar.
 */
@DisableCachingByDefault(because = "Resolver task performs filesystem orchestration not yet modeled for cache reuse")
public abstract class ResolveAgentDocsTask extends DefaultTask {
    /**
     * Gradle configuration name to inspect for direct dependencies.
     *
     * @return configuration name property
     */
    @Input
    public abstract Property<String> getConfigurationName();

    /**
     * Coordinates, in {@code group:name:version} form, of direct dependencies whose resolved jar
     * carries an {@code Agent-Docs} manifest attribute — precomputed during task configuration.
     * Dependencies without the attribute are not included here at all.
     *
     * @return declared dependency coordinates
     */
    @Input
    public abstract ListProperty<String> getDependencyCoordinates();

    /**
     * {@code Agent-Docs} scheme (either {@code classpath} or {@code maven}) keyed by coordinate.
     *
     * @return coordinate-to-scheme mapping
     */
    @Input
    public abstract MapProperty<String, String> getSchemes();

    /**
     * For {@code classpath}-scheme coordinates, the absolute path of the dependency's own resolved
     * jar to extract from.
     *
     * @return coordinate-to-jar-path mapping
     */
    @Input
    public abstract MapProperty<String, String> getClasspathJarPaths();

    /**
     * For {@code classpath}-scheme coordinates, the path within the jar (from {@link
     * #getClasspathJarPaths()}) to the root of the docs bundle.
     *
     * @return coordinate-to-embedded-path mapping
     */
    @Input
    public abstract MapProperty<String, String> getClasspathPrefixes();

    /**
     * For {@code maven}-scheme coordinates, the sidecar archive path resolved during task
     * configuration. Absent when the sidecar didn't resolve.
     *
     * @return coordinate-to-sidecar-path mapping
     */
    @Input
    public abstract MapProperty<String, String> getResolvedSidecarPaths();

    /**
     * When {@code true}, the task also resolves and extracts the {@code sources} classifier jar
     * for each dependency with an agent-docs declaration.
     *
     * @return include-sources flag property
     */
    @Input
    public abstract Property<Boolean> getIncludeSources();

    /**
     * Sources jar paths resolved during task configuration and keyed by
     * {@code group:name:version} coordinates. Only populated when {@link #getIncludeSources()} is
     * {@code true}; empty otherwise.
     *
     * @return coordinate-to-sources-path mapping
     */
    @Input
    public abstract MapProperty<String, String> getResolvedSourcePaths();

    /**
     * Root output directory for extracted docs and generated skills.
     *
     * @return skills root directory property
     */
    @OutputDirectory
    public abstract DirectoryProperty getSkillsDirectory();

    /**
     * Extracts docs for each declared dependency into managed skill directories, prunes stale
     * managed folders.
     *
     * @throws IOException when filesystem operations fail
     */
    @TaskAction
    void resolveAgentDocs() throws IOException {
        Path skillsRoot = getSkillsDirectory().get().getAsFile().toPath();
        Set<ModuleCoordinate> candidates = new LinkedHashSet<>();
        int invalidCoordinates = 0;
        for (String coordinate : getDependencyCoordinates().get()) {
            String[] segments = coordinate.split(":", 3);
            if (segments.length != 3) {
                invalidCoordinates++;
                continue;
            }
            candidates.add(new ModuleCoordinate(segments[0], segments[1], segments[2]));
        }
        getLogger().info(
                "Resolving agent-docs from {} declared coordinates ({} valid, {} invalid) into {}",
                getDependencyCoordinates().get().size(),
                candidates.size(),
                invalidCoordinates,
                skillsRoot);

        Set<SkillEntry> skillEntries = new LinkedHashSet<>();
        SkillDirectoryManager skillDirectoryManager = new SkillDirectoryManager(getLogger());

        boolean includeSources = getIncludeSources().get();
        int skillsMaterialized = 0;
        Map<String, String> schemes = getSchemes().get();
        Map<String, String> classpathJarPaths = getClasspathJarPaths().get();
        Map<String, String> classpathPrefixes = getClasspathPrefixes().get();
        Map<String, String> resolvedSidecars = getResolvedSidecarPaths().get();
        Map<String, String> resolvedSources = getResolvedSourcePaths().get();
        Map<ModuleCoordinate, String> skillNames = SkillNameAssigner.assign(candidates);

        for (ModuleCoordinate coordinate : candidates) {
            String scheme = schemes.get(coordinate.gav());
            String skillName = skillNames.get(coordinate);
            getLogger().debug("Materializing agent-docs for {} ({}) as {}", coordinate.gav(), scheme, skillName);

            Path sourcesPath = null;
            if (includeSources) {
                String sourcesPathValue = resolvedSources.get(coordinate.gav());
                if (sourcesPathValue != null && !sourcesPathValue.isBlank()) {
                    sourcesPath = Path.of(sourcesPathValue);
                }
            }

            SkillEntry entry;
            if (AgentDocsDeclaration.SCHEME_CLASSPATH.equals(scheme)) {
                String jarPathValue = classpathJarPaths.get(coordinate.gav());
                String prefix = classpathPrefixes.get(coordinate.gav());
                if (jarPathValue == null) {
                    continue;
                }
                entry = skillDirectoryManager.materializeSkillFromEmbeddedArchive(
                        coordinate, skillName, Path.of(jarPathValue), prefix, skillsRoot, includeSources, sourcesPath);
            } else if (AgentDocsDeclaration.SCHEME_MAVEN.equals(scheme)) {
                String sidecarPathValue = resolvedSidecars.get(coordinate.gav());
                if (sidecarPathValue == null || sidecarPathValue.isBlank()) {
                    getLogger().info("No agent-docs sidecar found for {}", coordinate.gav());
                    continue;
                }
                entry = skillDirectoryManager.materializeSkill(
                        coordinate, skillName, Path.of(sidecarPathValue), skillsRoot, includeSources, sourcesPath);
            } else {
                getLogger().warn("Unknown agent-docs scheme '{}' for {}; skipping", scheme, coordinate.gav());
                continue;
            }

            if (entry == null) {
                continue;
            }
            skillEntries.add(entry);
            skillsMaterialized++;
        }

        skillDirectoryManager.cleanupStaleManagedSkillDirectories(skillEntries, skillsRoot);
        getLogger().lifecycle(
                "resolveAgentDocs: inspected {} dependencies, materialized {} skills",
                candidates.size(),
                skillsMaterialized);
    }
}
