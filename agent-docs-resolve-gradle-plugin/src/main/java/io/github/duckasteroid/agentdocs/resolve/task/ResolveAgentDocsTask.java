package io.github.duckasteroid.agentdocs.resolve.task;

import java.io.IOException;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.Set;

import io.github.duckasteroid.agentdocs.resolve.task.model.ModuleCoordinate;
import io.github.duckasteroid.agentdocs.resolve.task.model.SkillEntry;
import org.gradle.api.DefaultTask;
import org.gradle.api.artifacts.ConfigurationContainer;
import org.gradle.api.artifacts.dsl.DependencyHandler;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.provider.ListProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.OutputDirectory;
import org.gradle.api.tasks.TaskAction;

import javax.inject.Inject;

/**
 * Resolves dependency-scoped {@code agent-docs} sidecar archives for the configured classpath,
 * stores each sidecar under flat GAV skill directories in the local project skills tree,
 * extracts docs into dependency-scoped directories, marks managed folders with
 * {@code .agent-docs}, removes stale marker-owned dependency folders, and generates one
 * skill entrypoint per resolved dependency sidecar.
 */
public abstract class ResolveAgentDocsTask extends DefaultTask {
    @Inject
    protected abstract ConfigurationContainer getConfigurations();

    @Inject
    protected abstract DependencyHandler getDependencies();

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
        for (String coordinate : getDependencyCoordinates().get()) {
            String[] segments = coordinate.split(":", 3);
            if (segments.length != 3) {
                continue;
            }
            candidates.add(new ModuleCoordinate(segments[0], segments[1], segments[2]));
        }
        Set<SkillEntry> skillEntries = new LinkedHashSet<>();
        SidecarArtifactResolver sidecarResolver =
                new SidecarArtifactResolver(getConfigurations(), getDependencies(), getLogger());
        SkillDirectoryManager skillDirectoryManager = new SkillDirectoryManager(getLogger());
        SkillWriter skillWriter = new SkillWriter(getClass().getClassLoader());

        for (ModuleCoordinate coordinate : candidates) {
            Path sidecarPath = sidecarResolver.resolveSidecar(coordinate);
            if (sidecarPath == null) {
                continue;
            }

            SkillEntry entry = skillDirectoryManager.materializeSkill(coordinate, sidecarPath, skillsRoot);
            if (entry == null) {
                continue;
            }
            skillEntries.add(entry);
        }

        skillDirectoryManager.cleanupStaleManagedSkillDirectories(skillEntries, skillsRoot);
        skillWriter.writeSkills(skillEntries, skillsRoot);
    }
}
