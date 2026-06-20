package io.github.duckasteroid.agentdocs.resolve.task;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
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
    private static final String SKILL_ENTRYPOINT_FILENAME = "SKILL.md";
    private static final String OWNERSHIP_MARKER_FILENAME = ".agent-docs";
    private static final String SOURCES_SUBDIRECTORY = "src/";

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
     * <p>When {@code includeSources} is {@code true}, the resolver also attempts to unpack the
     * sources jar into a {@code src/} subdirectory. The {@code SKILL.md} frontmatter receives a
     * {@code metadata.sources} field: {@code src/} when sources were unpacked, {@code none} when
     * the sources jar was unavailable.
     *
     * @param coordinate dependency coordinate
     * @param sidecarPath resolved sidecar archive
     * @param skillsRoot root managed skills directory
     * @param includeSources whether to unpack sources and annotate the skill
     * @param sourcesPath resolved sources jar, or {@code null} when unavailable
     * @return skill entry when entrypoint is present, otherwise {@code null}
     * @throws IOException when extraction or rewrite operations fail
     */
    SkillEntry materializeSkill(
            ModuleCoordinate coordinate,
            Path sidecarPath,
            Path skillsRoot,
            boolean includeSources,
            Path sourcesPath) throws IOException {
        Path destination = skillsRoot.resolve(coordinate.skillName());
        ResolveFilesystemSupport.deleteDirectory(destination);
        Files.createDirectories(destination);

        extractZip(destination, sidecarPath);
        ResolveFilesystemSupport.writeOwnershipMarker(destination, OWNERSHIP_MARKER_FILENAME);

        String sourcesMetadataValue = null;
        if (includeSources) {
            if (sourcesPath != null) {
                extractZip(destination.resolve(SOURCES_SUBDIRECTORY), sourcesPath);
                sourcesMetadataValue = SOURCES_SUBDIRECTORY;
                logger.info("Extracted sources for {} into {}", coordinate.gav(), destination.resolve(SOURCES_SUBDIRECTORY));
            } else {
                sourcesMetadataValue = "none";
                logger.info("No sources jar found for {}", coordinate.gav());
            }
        }

        Path entrypoint = findEntrypoint(destination);
        if (entrypoint == null) {
            logger.warn("Resolved sidecar for {} but could not locate SKILL.md entrypoint", coordinate.gav());
            return null;
        }

        rewriteEntrypointFrontmatter(entrypoint, coordinate.skillName(), sourcesMetadataValue);
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

    private void extractZip(Path destination, Path zipPath) throws IOException {
        Files.createDirectories(destination);
        try (ZipInputStream zipInputStream = new ZipInputStream(Files.newInputStream(zipPath))) {
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

    /**
     * Rewrites the SKILL.md frontmatter to set the canonical {@code name} and, when sources were
     * resolved, inject a {@code metadata.sources} field.
     *
     * <p>Any existing {@code name:} or {@code metadata:} blocks in the sidecar are stripped before
     * the rewrite so that resolver-generated values are always authoritative.
     *
     * @param entrypoint path to the extracted SKILL.md
     * @param skillName canonical skill name derived from GAV coordinates
     * @param sourcesMetadataValue {@code "src/"} when sources were extracted, {@code "none"} when
     *     sources were requested but unavailable, or {@code null} when sources were not requested
     * @throws IOException when reading or writing the file fails
     */
    private void rewriteEntrypointFrontmatter(Path entrypoint, String skillName, String sourcesMetadataValue) throws IOException {
        String normalized = Files.readString(entrypoint).replace("\r\n", "\n");
        String rewritten;

        String metadataSuffix = sourcesMetadataValue != null
                ? "\nmetadata:\n  sources: " + sourcesMetadataValue
                : "";

        if (normalized.startsWith("---\n")) {
            int closingIndex = normalized.indexOf("\n---\n", 4);
            if (closingIndex >= 0) {
                String frontmatterBlock = normalized.substring(4, closingIndex);
                String body = normalized.substring(closingIndex + 5);
                List<String> retainedLines = stripManagedFrontmatterFields(frontmatterBlock.lines().toList());
                String updatedFrontmatter = "name: " + skillName
                        + (retainedLines.isEmpty() ? "" : "\n" + String.join("\n", retainedLines))
                        + metadataSuffix;
                rewritten = "---\n" + updatedFrontmatter + "\n---\n" + body;
            } else {
                rewritten = normalized;
            }
        } else {
            rewritten = "---\nname: " + skillName + metadataSuffix + "\n---\n\n" + normalized;
        }

        Files.writeString(entrypoint, rewritten);
    }

    /**
     * Filters frontmatter lines, removing {@code name:} entries and {@code metadata:} blocks
     * (including their indented children) so resolver-generated values can be injected cleanly.
     */
    private static List<String> stripManagedFrontmatterFields(List<String> lines) {
        List<String> result = new ArrayList<>();
        boolean inMetadataBlock = false;
        for (String line : lines) {
            if (line.trim().startsWith("name:")) {
                inMetadataBlock = false;
                continue;
            }
            if (line.startsWith("metadata:")) {
                inMetadataBlock = true;
                continue;
            }
            if (inMetadataBlock && (line.startsWith(" ") || line.startsWith("\t"))) {
                continue;
            }
            inMetadataBlock = false;
            result.add(line);
        }
        return result;
    }
}
