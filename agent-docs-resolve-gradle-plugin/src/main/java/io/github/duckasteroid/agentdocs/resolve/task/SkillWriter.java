package io.github.duckasteroid.agentdocs.resolve.task;

import io.github.duckasteroid.agentdocs.resolve.task.model.ModuleCoordinate;
import io.github.duckasteroid.agentdocs.resolve.task.model.SkillEntry;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;

/**
 * Writes generated resolver skills in per-dependency mode.
 */
final class SkillWriter {
    private static final String DEPENDENCY_SKILL_TEMPLATE_RESOURCE = "agent-docs-dependency-skill-template.md";
    private static final String GAV_PLACEHOLDER = "{{gav}}";
    private static final String SKILL_NAME_PLACEHOLDER = "{{skill_name}}";
    private static final String ENTRYPOINT_PLACEHOLDER = "{{entrypoint}}";
    private static final String PER_DEPENDENCY_SKILLS_SUBDIRECTORY = "agent-docs-dependencies";
    private static final String SINGLE_SKILL_FILENAME = "SKILL.md";
    private static final String SKILL_ENTRYPOINT_FILENAME = "SKILL.md";
    private static final String OWNERSHIP_MARKER_FILENAME = ".agent-docs";

    private final ClassLoader classLoader;

    /**
     * Creates a writer backed by classpath templates.
     *
     * @param classLoader classloader used to load skill templates
     */
    SkillWriter(ClassLoader classLoader) {
        this.classLoader = classLoader;
    }

    /**
     * Generates per-dependency output skills for resolved sidecars.
     *
     * @param entries resolved dependency entries
     * @param skillsRoot root skills directory
     * @throws IOException when writing or cleanup fails
     */
    void writeSkills(Set<SkillEntry> entries, Path skillsRoot) throws IOException {
        cleanupLegacySingleSkill(skillsRoot);
        writePerDependencySkillFiles(entries, skillsRoot);
    }

    private void writePerDependencySkillFiles(Set<SkillEntry> entries, Path skillsRoot) throws IOException {
        Path root = perDependencySkillsRoot(skillsRoot);
        ResolveFilesystemSupport.deleteDirectory(root);
        Files.createDirectories(root);

        String template = ResolveFilesystemSupport.loadTemplate(classLoader, DEPENDENCY_SKILL_TEMPLATE_RESOURCE);
        for (SkillEntry entry : entries) {
            Path skillPath = toPerDependencySkillPath(root, entry.coordinate());
            Files.createDirectories(skillPath.getParent());
            ResolveFilesystemSupport.writeOwnershipMarker(skillPath.getParent(), OWNERSHIP_MARKER_FILENAME);

            String relativeEntrypoint = ResolveFilesystemSupport.toRelativePath(skillPath.getParent(), entry.entrypointPath());
            String generatedSkillName = skillPath.getParent().getFileName().toString();
            String content = template
                    .replace(GAV_PLACEHOLDER, entry.coordinate().gav())
                    .replace(SKILL_NAME_PLACEHOLDER, generatedSkillName)
                    .replace(ENTRYPOINT_PLACEHOLDER, relativeEntrypoint);
            Files.writeString(skillPath, content);
        }
    }

    private void cleanupLegacySingleSkill(Path skillsRoot) throws IOException {
        Files.deleteIfExists(skillsRoot.resolve(SINGLE_SKILL_FILENAME));
    }

    private Path perDependencySkillsRoot(Path skillsRoot) {
        return skillsRoot.resolve(PER_DEPENDENCY_SKILLS_SUBDIRECTORY);
    }

    private Path toPerDependencySkillPath(Path perDependencyRoot, ModuleCoordinate coordinate) {
        return perDependencyRoot.resolve(coordinate.skillName()).resolve(SKILL_ENTRYPOINT_FILENAME);
    }
}
