package io.github.duckasteroid.agentdocs.resolve.task;

import java.io.IOException;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.Set;

import io.github.duckasteroid.agentdocs.resolve.task.model.ModuleCoordinate;
import io.github.duckasteroid.agentdocs.resolve.task.model.SkillEntry;
import org.gradle.api.DefaultTask;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.OutputDirectory;
import org.gradle.api.tasks.TaskAction;

/**
 * Resolves dependency-scoped {@code agent-docs} sidecar archives for the configured classpath,
 * stores each sidecar under flat GAV skill directories in the local project skills tree,
 * extracts docs into dependency-scoped directories, marks managed folders with
 * {@code .agent-docs}, removes stale marker-owned dependency folders, and generates one
 * skill entrypoint per resolved dependency sidecar.
 */
public abstract class ResolveAgentDocsTask extends DefaultTask {
    /**
     * Gradle configuration name to inspect for direct dependencies.
     *
     * @return configuration name property
     */
    @Input
    public abstract Property<String> getConfigurationName();

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
        Set<ModuleCoordinate> candidates =
                ResolvedDependencyCollector.collect(getProject(), getConfigurationName().get());
        Set<SkillEntry> skillEntries = new LinkedHashSet<>();
        SidecarArtifactResolver sidecarResolver = new SidecarArtifactResolver(getProject(), getLogger());
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
