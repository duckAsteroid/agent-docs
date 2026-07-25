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
    private static final Set<String> MANAGED_METADATA_KEYS = Set.of("group", "artifact", "version", "sources");

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
     * <p>The {@code SKILL.md} frontmatter always receives {@code metadata.group},
     * {@code metadata.artifact} and {@code metadata.version} fields recording the resolved GAV.
     * When {@code includeSources} is {@code true}, the resolver also attempts to unpack the
     * sources jar into a {@code src/} subdirectory and adds a {@code metadata.sources} field:
     * {@code src/} when sources were unpacked, {@code none} when the sources jar was unavailable.
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
            String skillName,
            Path sidecarPath,
            Path skillsRoot,
            boolean includeSources,
            Path sourcesPath) throws IOException {
        return materialize(coordinate, skillName, skillsRoot, includeSources, sourcesPath,
                destination -> extractZip(destination, sidecarPath));
    }

    /**
     * Extracts a subtree embedded within a dependency's own jar (the {@code classpath}
     * distribution) into the managed skill directory and returns the entry.
     *
     * @param coordinate dependency coordinate
     * @param archivePath the dependency's own resolved jar
     * @param embeddedPathPrefix path, within {@code archivePath}, to the root of the docs bundle
     * @param skillsRoot root managed skills directory
     * @param includeSources whether to unpack sources and annotate the skill
     * @param sourcesPath resolved sources jar, or {@code null} when unavailable
     * @return skill entry when entrypoint is present, otherwise {@code null}
     * @throws IOException when extraction or rewrite operations fail
     */
    SkillEntry materializeSkillFromEmbeddedArchive(
            ModuleCoordinate coordinate,
            String skillName,
            Path archivePath,
            String embeddedPathPrefix,
            Path skillsRoot,
            boolean includeSources,
            Path sourcesPath) throws IOException {
        return materialize(coordinate, skillName, skillsRoot, includeSources, sourcesPath,
                destination -> extractZip(destination, archivePath, embeddedPathPrefix));
    }

    private interface ExtractionAction {
        void extract(Path destination) throws IOException;
    }

    private SkillEntry materialize(
            ModuleCoordinate coordinate,
            String skillName,
            Path skillsRoot,
            boolean includeSources,
            Path sourcesPath,
            ExtractionAction extraction) throws IOException {
        Path destination = skillsRoot.resolve(skillName);
        ResolveFilesystemSupport.deleteDirectory(destination);
        Files.createDirectories(destination);

        extraction.extract(destination);
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
            logger.warn("Resolved agent docs for {} but could not locate SKILL.md entrypoint", coordinate.gav());
            return null;
        }

        rewriteEntrypointFrontmatter(entrypoint, skillName, coordinate, sourcesMetadataValue);
        return new SkillEntry(coordinate, entrypoint, skillName);
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
            activeSkillFolders.add(entry.skillName());
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
        extractZip(destination, zipPath, null);
    }

    /**
     * Extracts entries from a zip/jar archive into {@code destination}.
     *
     * <p>When {@code prefix} is non-null, only entries under that path are extracted, with the
     * prefix stripped from each entry's destination path — this is how a {@code classpath}
     * distribution's docs bundle (a subtree embedded within a dependency's own jar) is separated
     * from the rest of that jar's contents. When {@code prefix} is {@code null}, every entry is
     * extracted, as for a standalone {@code agent-docs} sidecar zip.
     */
    private void extractZip(Path destination, Path zipPath, String prefix) throws IOException {
        String normalizedPrefix = normalizeExtractionPrefix(prefix);
        Files.createDirectories(destination);
        try (ZipInputStream zipInputStream = new ZipInputStream(Files.newInputStream(zipPath))) {
            ZipEntry entry;
            while ((entry = zipInputStream.getNextEntry()) != null) {
                String entryName = entry.getName();
                if (normalizedPrefix != null) {
                    if (!entryName.startsWith(normalizedPrefix)) {
                        zipInputStream.closeEntry();
                        continue;
                    }
                    entryName = entryName.substring(normalizedPrefix.length());
                    if (entryName.isEmpty()) {
                        zipInputStream.closeEntry();
                        continue;
                    }
                }

                Path targetPath = destination.resolve(entryName).normalize();
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

    private static String normalizeExtractionPrefix(String prefix) {
        if (prefix == null || prefix.isBlank()) {
            return null;
        }
        String normalized = prefix.startsWith("/") ? prefix.substring(1) : prefix;
        if (!normalized.isEmpty() && !normalized.endsWith("/")) {
            normalized = normalized + "/";
        }
        return normalized.isEmpty() ? null : normalized;
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
     * Rewrites the SKILL.md frontmatter to set the canonical {@code name}, prepend a
     * library-identifying prefix onto {@code description}, and record the resolved GAV (plus,
     * when sources were resolved, a {@code sources} field) under {@code metadata}.
     *
     * <p>Only the specific fields the resolver owns are overwritten: the top-level {@code name}
     * and {@code description}, and the {@code group}/{@code artifact}/{@code version}/
     * {@code sources} keys within {@code metadata}. Any other upstream frontmatter — including
     * unrelated {@code metadata} entries — is preserved untouched.
     *
     * @param entrypoint path to the extracted SKILL.md
     * @param skillName canonical skill name derived from GAV coordinates
     * @param coordinate resolved dependency coordinate
     * @param sourcesMetadataValue {@code "src/"} when sources were extracted, {@code "none"} when
     *     sources were requested but unavailable, or {@code null} when sources were not requested
     * @throws IOException when reading or writing the file fails
     */
    private void rewriteEntrypointFrontmatter(
            Path entrypoint, String skillName, ModuleCoordinate coordinate, String sourcesMetadataValue) throws IOException {
        String normalized = Files.readString(entrypoint).replace("\r\n", "\n");
        String rewritten;

        List<String> metadataLines = new ArrayList<>();
        metadataLines.add("  group: " + coordinate.group());
        metadataLines.add("  artifact: " + coordinate.artifact());
        metadataLines.add("  version: " + coordinate.version());
        if (sourcesMetadataValue != null) {
            metadataLines.add("  sources: " + sourcesMetadataValue);
        }

        if (normalized.startsWith("---\n")) {
            int closingIndex = normalized.indexOf("\n---\n", 4);
            if (closingIndex >= 0) {
                String frontmatterBlock = normalized.substring(4, closingIndex);
                String body = normalized.substring(closingIndex + 5);
                FrontmatterFields fields = extractFrontmatterFields(frontmatterBlock.lines().toList());
                metadataLines.addAll(fields.retainedMetadataLines());

                List<String> topLevel = new ArrayList<>();
                topLevel.add("name: " + skillName);
                topLevel.add("description: " + yamlQuote(buildDescription(coordinate, fields.description())));
                topLevel.addAll(fields.otherTopLevelLines());
                topLevel.add("metadata:");
                topLevel.addAll(metadataLines);

                rewritten = "---\n" + String.join("\n", topLevel) + "\n---\n" + body;
            } else {
                rewritten = normalized;
            }
        } else {
            List<String> topLevel = new ArrayList<>();
            topLevel.add("name: " + skillName);
            topLevel.add("description: " + yamlQuote(buildDescription(coordinate, null)));
            topLevel.add("metadata:");
            topLevel.addAll(metadataLines);
            rewritten = "---\n" + String.join("\n", topLevel) + "\n---\n\n" + normalized;
        }

        Files.writeString(entrypoint, rewritten);
    }

    /**
     * Builds the resolver-generated skill description: a Java/Maven-specific sentence identifying
     * the library this skill documents, followed by the upstream author's own description (if
     * any) — matching the core convention's "generated prefix, author description appended" rule.
     */
    private static String buildDescription(ModuleCoordinate coordinate, String upstreamDescription) {
        String prefix = "Reference documentation for the Java library `" + coordinate.group() + ":"
                + coordinate.artifact() + "` (Maven, resolved version " + coordinate.version()
                + "). Use this skill when writing, reviewing, or debugging code that depends on it.";
        if (upstreamDescription == null || upstreamDescription.isBlank()) {
            return prefix;
        }
        return prefix + " " + unquoteYamlScalar(upstreamDescription);
    }

    /**
     * Holds the parts of an upstream frontmatter block that survive the resolver's rewrite:
     * top-level lines other than {@code name:}/{@code description:}, the raw upstream
     * {@code description} value (or {@code null}), and {@code metadata} children other than the
     * resolver-managed keys.
     */
    private record FrontmatterFields(List<String> otherTopLevelLines, String description, List<String> retainedMetadataLines) {
    }

    /**
     * Parses a frontmatter block, pulling out the fields the resolver manages so they can be
     * regenerated, and retaining everything else (including non-managed {@code metadata} keys)
     * untouched.
     */
    private static FrontmatterFields extractFrontmatterFields(List<String> lines) {
        List<String> otherTopLevelLines = new ArrayList<>();
        List<String> retainedMetadataLines = new ArrayList<>();
        String description = null;
        boolean inMetadataBlock = false;

        for (String line : lines) {
            if (inMetadataBlock && (line.startsWith(" ") || line.startsWith("\t"))) {
                String key = childKey(line);
                if (key == null || !MANAGED_METADATA_KEYS.contains(key)) {
                    retainedMetadataLines.add(line);
                }
                continue;
            }
            inMetadataBlock = false;

            String trimmed = line.trim();
            if (trimmed.startsWith("name:")) {
                continue;
            }
            if (line.equals("metadata:")) {
                inMetadataBlock = true;
                continue;
            }
            if (trimmed.startsWith("description:")) {
                description = trimmed.substring("description:".length()).trim();
                continue;
            }
            otherTopLevelLines.add(line);
        }

        return new FrontmatterFields(otherTopLevelLines, description, retainedMetadataLines);
    }

    private static String childKey(String line) {
        String trimmed = line.trim();
        int colonIndex = trimmed.indexOf(':');
        return colonIndex < 0 ? null : trimmed.substring(0, colonIndex).trim();
    }

    private static String unquoteYamlScalar(String raw) {
        String value = raw.trim();
        if (value.length() >= 2 && value.charAt(0) == '"' && value.charAt(value.length() - 1) == '"') {
            return value.substring(1, value.length() - 1).replace("\\\"", "\"").replace("\\\\", "\\");
        }
        if (value.length() >= 2 && value.charAt(0) == '\'' && value.charAt(value.length() - 1) == '\'') {
            return value.substring(1, value.length() - 1).replace("''", "'");
        }
        return value;
    }

    private static String yamlQuote(String value) {
        return "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
    }
}
