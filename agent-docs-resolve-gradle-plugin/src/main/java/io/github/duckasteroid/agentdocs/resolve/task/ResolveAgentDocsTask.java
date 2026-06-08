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
 * Resolves dependency-scoped {@code agent-docs} sidecar archives for the configured classpath,
 * stores each sidecar under flat GAV skill directories in the local project skills tree,
 * extracts docs into dependency-scoped directories, marks managed folders with
 * {@code .agent-docs}, removes stale marker-owned dependency folders, and generates one
 * skill entrypoint per resolved dependency sidecar.
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
     * Direct dependency coordinates from the configured classpath in {@code group:name:version}
     * form, precomputed during task configuration.
     *
     * @return direct dependency coordinates
     */
    @Input
    public abstract ListProperty<String> getDependencyCoordinates();

    /**
     * Sidecar archive paths resolved during task configuration and keyed by
     * {@code group:name:version} coordinates.
     *
     * @return coordinate-to-sidecar-path mapping
     */
    @Input
    public abstract MapProperty<String, String> getResolvedSidecarPaths();

    /**
     * Root output directory for extracted docs and generated skills.
     *
     * @return skills root directory property
     */
    @OutputDirectory
    public abstract DirectoryProperty getSkillsDirectory();

    /**
     * Resolves dependency sidecars, extracts docs into managed skill directories, prunes stale
     * managed folders, and writes generated skill entrypoints.
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
                "Resolving agent-docs sidecars from {} coordinates ({} valid, {} invalid) into {}",
                getDependencyCoordinates().get().size(),
                candidates.size(),
                invalidCoordinates,
                skillsRoot);

        Set<SkillEntry> skillEntries = new LinkedHashSet<>();
        SkillDirectoryManager skillDirectoryManager = new SkillDirectoryManager(getLogger());
        SkillWriter skillWriter = new SkillWriter(getClass().getClassLoader());

        int sidecarsResolved = 0;
        int skillsMaterialized = 0;
        Map<String, String> resolvedSidecars = getResolvedSidecarPaths().get();

        for (ModuleCoordinate coordinate : candidates) {
            getLogger().debug("Checking agent-docs sidecar for {}", coordinate.gav());
            String sidecarPathValue = resolvedSidecars.get(coordinate.gav());
            if (sidecarPathValue == null || sidecarPathValue.isBlank()) {
                getLogger().info("No agent-docs sidecar found for {}", coordinate.gav());
                continue;
            }
            Path sidecarPath = Path.of(sidecarPathValue);
            sidecarsResolved++;

            SkillEntry entry = skillDirectoryManager.materializeSkill(coordinate, sidecarPath, skillsRoot);
            if (entry == null) {
                continue;
            }
            skillEntries.add(entry);
            skillsMaterialized++;
        }

        skillDirectoryManager.cleanupStaleManagedSkillDirectories(skillEntries, skillsRoot);
        skillWriter.writeSkills(skillEntries, skillsRoot);
        getLogger().lifecycle(
                "resolveAgentDocs: inspected {} dependencies, found {} sidecars, materialized {} skills",
                candidates.size(),
                sidecarsResolved,
                skillsMaterialized);
    }
}
