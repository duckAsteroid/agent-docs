package io.github.duckasteroid.agentdocs.resolve.task;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import io.github.duckasteroid.agentdocs.resolve.task.model.ModuleCoordinate;
import io.github.duckasteroid.agentdocs.resolve.task.model.SkillEntry;
import org.gradle.api.logging.Logger;

/**
 * Manages extracted dependency skill directories and stale managed-folder cleanup.
 */
final class SkillDirectoryManager {
    private static final String SIDECAR_FILENAME = "agent-docs.zip";
    private static final String SKILL_ENTRYPOINT_FILENAME = "SKILL.md";
    private static final String OWNERSHIP_MARKER_FILENAME = ".agent-docs";

    private final Logger logger;

    /**
     * Creates a manager for dependency skill directory operations.
     *
     * @param logger logger for warnings and diagnostics
     */
    SkillDirectoryManager(Logger logger) {
        this.logger = logger;
    }

    /**
     * Extracts a dependency sidecar into the managed skill directory and returns the entry.
     *
     * @param coordinate dependency coordinate
     * @param sidecarPath resolved sidecar archive
     * @param skillsRoot root managed skills directory
     * @return skill entry when entrypoint is present, otherwise {@code null}
     * @throws IOException when extraction or rewrite operations fail
     */
    SkillEntry materializeSkill(ModuleCoordinate coordinate, Path sidecarPath, Path skillsRoot) throws IOException {
        Path destination = skillsRoot.resolve(coordinate.skillName());
        ResolveFilesystemSupport.deleteDirectory(destination);
        Files.createDirectories(destination);

        extractSidecar(destination, sidecarPath);
        Files.copy(sidecarPath, destination.resolve(SIDECAR_FILENAME), StandardCopyOption.REPLACE_EXISTING);
        ResolveFilesystemSupport.writeOwnershipMarker(destination, OWNERSHIP_MARKER_FILENAME);

        Path entrypoint = findEntrypoint(destination);
        if (entrypoint == null) {
            logger.warn("Resolved sidecar for {} but could not locate SKILL.md entrypoint", coordinate.gav());
            return null;
        }

        rewriteEntrypointSkillName(entrypoint, coordinate.skillName());
        return new SkillEntry(coordinate, entrypoint);
    }

    /**
     * Removes managed dependency skill folders that are no longer active.
     *
     * @param entries active skill entries for current resolution
     * @param skillsRoot managed skills root directory
     * @throws IOException when cleanup fails
     */
    void cleanupStaleManagedSkillDirectories(Set<SkillEntry> entries, Path skillsRoot) throws IOException {
        if (Files.notExists(skillsRoot)) {
            return;
        }

        Set<String> activeSkillFolders = new HashSet<>();
        for (SkillEntry entry : entries) {
            activeSkillFolders.add(entry.coordinate().skillName());
        }

        try (Stream<Path> children = Files.list(skillsRoot)) {
            List<Path> staleManagedDirectories = children
                    .filter(Files::isDirectory)
                    .filter(this::isManagedDependencySkillDirectory)
                    .filter(path -> !activeSkillFolders.contains(path.getFileName().toString()))
                    .toList();

            for (Path staleDirectory : staleManagedDirectories) {
                ResolveFilesystemSupport.deleteDirectory(staleDirectory);
            }
        }
    }

    private void extractSidecar(Path destination, Path sidecarPath) throws IOException {
        try (ZipInputStream zipInputStream = new ZipInputStream(Files.newInputStream(sidecarPath))) {
            ZipEntry entry;
            while ((entry = zipInputStream.getNextEntry()) != null) {
                Path targetPath = destination.resolve(entry.getName()).normalize();
                if (!targetPath.startsWith(destination)) {
                    throw new IOException("Refusing to extract zip entry outside destination: " + entry.getName());
                }

                if (entry.isDirectory()) {
                    Files.createDirectories(targetPath);
                } else {
                    Path parent = targetPath.getParent();
                    if (parent == null) {
                        throw new IOException("Zip entry has no parent directory: " + entry.getName());
                    }
                    Files.createDirectories(parent);
                    Files.copy(zipInputStream, targetPath, StandardCopyOption.REPLACE_EXISTING);
                }
                zipInputStream.closeEntry();
            }
        }
    }

    private Path findEntrypoint(Path extractedRoot) throws IOException {
        try (Stream<Path> stream = Files.walk(extractedRoot)) {
            return stream.filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().equalsIgnoreCase(SKILL_ENTRYPOINT_FILENAME))
                    .findFirst()
                    .orElse(null);
        }
    }

    private boolean isManagedDependencySkillDirectory(Path directory) {
        return Files.exists(directory.resolve(OWNERSHIP_MARKER_FILENAME));
    }

    private void rewriteEntrypointSkillName(Path entrypoint, String skillName) throws IOException {
        String normalized = Files.readString(entrypoint).replace("\r\n", "\n");
        String rewritten = normalized;

        if (normalized.startsWith("---\n")) {
            int closingIndex = normalized.indexOf("\n---\n", 4);
            if (closingIndex >= 0) {
                String frontmatterBlock = normalized.substring(4, closingIndex);
                String body = normalized.substring(closingIndex + 5);
                List<String> retainedLines = frontmatterBlock.lines()
                        .filter(line -> !line.trim().startsWith("name:"))
                        .toList();
                String updatedFrontmatter = "name: " + skillName
                        + (retainedLines.isEmpty() ? "" : "\n" + String.join("\n", retainedLines));
                rewritten = "---\n" + updatedFrontmatter + "\n---\n" + body;
            }
        } else {
            rewritten = "---\nname: " + skillName + "\n---\n\n" + normalized;
        }

        Files.writeString(entrypoint, rewritten);
    }
}
