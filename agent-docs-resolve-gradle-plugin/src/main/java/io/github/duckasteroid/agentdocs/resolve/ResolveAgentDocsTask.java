package io.github.duckasteroid.agentdocs.resolve;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import org.gradle.api.DefaultTask;
import org.gradle.api.artifacts.ModuleVersionIdentifier;
import org.gradle.api.artifacts.result.ResolvedComponentResult;
import org.gradle.api.artifacts.result.ResolvedDependencyResult;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.OutputDirectory;
import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.TaskAction;

/**
 * Resolves dependency-scoped {@code agent-docs} sidecar archives for the configured classpath,
 * stores each sidecar under flat GAV skill directories in the local project skills tree,
 * extracts docs into dependency-scoped directories, marks managed folders with
 * {@code .agent-docs}, removes stale marker-owned dependency folders, and generates local
 * skills for agent consumption.
 *
 * <p>Skill output mode is configurable:
 *
 * <ul>
 *   <li>{@code SINGLE_INDEX}: writes one router skill listing all resolved GAVs
 *   <li>{@code PER_DEPENDENCY}: writes one skill file per resolved dependency
 *   <li>{@code AUTO_THRESHOLD}: selects per-dependency up to a configured count, otherwise single-index
 * </ul>
 *
 * <p>When mode changes, stale outputs from the non-selected model are removed.
 */
public abstract class ResolveAgentDocsTask extends DefaultTask {
    private static final String SKILL_TEMPLATE_RESOURCE = "agent-docs-skill-template.md";
    private static final String DEPENDENCY_SKILL_TEMPLATE_RESOURCE = "agent-docs-dependency-skill-template.md";
    private static final String DOC_ENTRIES_PLACEHOLDER = "{{available_dependency_docs}}";
    private static final String GAV_PLACEHOLDER = "{{gav}}";
    private static final String SKILL_NAME_PLACEHOLDER = "{{skill_name}}";
    private static final String ENTRYPOINT_PLACEHOLDER = "{{entrypoint}}";
    private static final String PER_DEPENDENCY_SKILLS_SUBDIRECTORY = "agent-docs-dependencies";
    private static final String SIDECAR_FILENAME = "agent-docs.zip";
    private static final String SKILL_ENTRYPOINT_FILENAME = "SKILL.md";
    private static final String OWNERSHIP_MARKER_FILENAME = ".agent-docs";

    /**
     * Gradle configuration name to inspect for direct dependencies.
     *
     * @return configuration name property
     */
    @Input
    public abstract Property<String> getConfigurationName();

    /**
     * Skill generation mode value.
     *
     * @return generation mode property
     */
    @Input
    public abstract Property<String> getSkillGenerationMode();

    /**
     * Threshold used when generation mode is {@code AUTO_THRESHOLD}.
     *
     * @return per-dependency threshold property
     */
    @Input
    public abstract Property<Integer> getPerDependencySkillThreshold();

    /**
     * Output file for the single router skill mode.
     *
     * @return single-skill output file property
     */
    @OutputFile
    public abstract RegularFileProperty getSkillFile();

    /**
     * Root output directory for extracted docs and generated skills.
     *
     * @return skills root directory property
     */
    @OutputDirectory
    public abstract DirectoryProperty getSkillsDirectory();

    @TaskAction
    void resolveAgentDocs() throws IOException {
        Set<ModuleCoordinate> candidates = collectResolvedDirectDependencies();
        Set<SkillEntry> skillEntries = new LinkedHashSet<>();

        for (ModuleCoordinate coordinate : candidates) {
            Path sidecarPath = resolveSidecar(coordinate);
            if (sidecarPath == null) {
                continue;
            }

            Path extractedRoot = extractSidecarToSkills(coordinate, sidecarPath);
            downloadSidecarArtifact(coordinate, sidecarPath);
            Path entrypoint = findEntrypoint(extractedRoot);
            if (entrypoint == null) {
                getLogger().warn("Resolved sidecar for {} but could not locate SKILL.md entrypoint", coordinate.gav());
                continue;
            }
            rewriteEntrypointSkillName(entrypoint, coordinate.skillName());

            skillEntries.add(new SkillEntry(coordinate, entrypoint));
        }

        cleanupStaleManagedSkillDirectories(skillEntries);
        writeSkills(skillEntries);
    }

    private Set<ModuleCoordinate> collectResolvedDirectDependencies() {
        Set<ModuleCoordinate> candidates = new LinkedHashSet<>();
        var configuration = getProject().getConfigurations().getByName(getConfigurationName().get());
        ResolvedComponentResult root = configuration.getIncoming().getResolutionResult().getRootComponent().get();

        root.getDependencies().forEach(dependency -> {
            if (!(dependency instanceof ResolvedDependencyResult resolvedDependency)) {
                return;
            }
            ModuleVersionIdentifier moduleVersion = resolvedDependency.getSelected().getModuleVersion();
            if (moduleVersion == null) {
                return;
            }

            String group = moduleVersion.getGroup();
            String artifact = moduleVersion.getName();
            String version = moduleVersion.getVersion();
            if (group == null || group.isBlank() || artifact == null || artifact.isBlank() || version == null || version.isBlank()) {
                return;
            }

            candidates.add(new ModuleCoordinate(group, artifact, version));
        });
        return candidates;
    }

    private Path resolveSidecar(ModuleCoordinate coordinate) {
        var detached = getProject().getConfigurations().detachedConfiguration(
                getProject()
                        .getDependencies()
                        .create(coordinate.group() + ":" + coordinate.artifact() + ":" + coordinate.version() + ":agent-docs@zip"));
        detached.setTransitive(false);

        Set<java.io.File> files;
        try {
            files = detached.getResolvedConfiguration().getLenientConfiguration().getFiles();
        } catch (Exception exception) {
            getLogger().info("Unable to resolve agent-docs sidecar for {}", coordinate.gav(), exception);
            return null;
        }

        if (files.isEmpty()) {
            return null;
        }

        return files.iterator().next().toPath();
    }

    private Path downloadSidecarArtifact(ModuleCoordinate coordinate, Path sidecarPath) throws IOException {
        Path destination = toSidecarArchivePath(coordinate);
        Files.createDirectories(destination.getParent());
        Files.copy(sidecarPath, destination, StandardCopyOption.REPLACE_EXISTING);
        return destination;
    }

    private Path extractSidecarToSkills(ModuleCoordinate coordinate, Path sidecarPath) throws IOException {
        Path destination = toAgentSkillDirectory(coordinate);
        deleteDirectory(destination);
        Files.createDirectories(destination);

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

        writeOwnershipMarker(destination);
        return destination;
    }

    private Path findEntrypoint(Path extractedRoot) throws IOException {
        try (Stream<Path> stream = Files.walk(extractedRoot)) {
            return stream.filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().equalsIgnoreCase(SKILL_ENTRYPOINT_FILENAME))
                    .findFirst()
                    .orElse(null);
        }
    }

    private void writeSkills(Set<SkillEntry> entries) throws IOException {
        SkillGenerationMode configuredMode = SkillGenerationMode.from(getSkillGenerationMode().get());
        SkillGenerationMode effectiveMode = resolveEffectiveSkillGenerationMode(configuredMode, entries.size());
        cleanupForMode(effectiveMode);

        if (effectiveMode == SkillGenerationMode.PER_DEPENDENCY) {
            writePerDependencySkillFiles(entries);
            return;
        }

        writeSingleSkillFile(entries);
    }

    private SkillGenerationMode resolveEffectiveSkillGenerationMode(SkillGenerationMode configuredMode, int resolvedEntryCount) {
        if (configuredMode != SkillGenerationMode.AUTO_THRESHOLD) {
            return configuredMode;
        }

        int threshold = getPerDependencySkillThreshold().get();
        if (threshold < 0) {
            throw new IllegalArgumentException("agentDocs.perDependencySkillThreshold must be >= 0");
        }

        return resolvedEntryCount <= threshold ? SkillGenerationMode.PER_DEPENDENCY : SkillGenerationMode.SINGLE_INDEX;
    }

    private void writeSingleSkillFile(Set<SkillEntry> entries) throws IOException {
        Path skillPath = getSkillFile().get().getAsFile().toPath();
        Path skillParent = skillPath.getParent();
        if (skillParent == null) {
            throw new IOException("Skill file parent directory is required: " + skillPath);
        }
        Files.createDirectories(skillParent);

        StringBuilder entriesBuilder = new StringBuilder();

        List<SkillEntry> sortedEntries = entries.stream()
                .sorted(Comparator.comparing(entry -> entry.coordinate().gav()))
                .toList();

        if (sortedEntries.isEmpty()) {
            entriesBuilder.append("- No dependency sidecars were resolved for this project.\n");
        } else {
            for (SkillEntry entry : sortedEntries) {
                String relativeEntrypoint = toRelativePath(skillParent, entry.entrypointPath());
                entriesBuilder.append("- `")
                        .append(entry.coordinate().gav())
                        .append("` -> `")
                        .append(relativeEntrypoint)
                        .append("`\n");
            }
        }

        String template = loadSkillTemplate();
        String skillContent = template.replace(DOC_ENTRIES_PLACEHOLDER, entriesBuilder.toString());
        Files.writeString(skillPath, skillContent);
    }

    private void writePerDependencySkillFiles(Set<SkillEntry> entries) throws IOException {
        Path root = perDependencySkillsRoot();
        deleteDirectory(root);
        Files.createDirectories(root);

        String template = loadTemplate(DEPENDENCY_SKILL_TEMPLATE_RESOURCE);
        for (SkillEntry entry : entries) {
            Path skillPath = toPerDependencySkillPath(entry.coordinate());
            Files.createDirectories(skillPath.getParent());
            writeOwnershipMarker(skillPath.getParent());

            String relativeEntrypoint = toRelativePath(skillPath.getParent(), entry.entrypointPath());
            String generatedSkillName = skillPath.getParent().getFileName().toString();
            String content = template
                    .replace(GAV_PLACEHOLDER, entry.coordinate().gav())
                    .replace(SKILL_NAME_PLACEHOLDER, generatedSkillName)
                    .replace(ENTRYPOINT_PLACEHOLDER, relativeEntrypoint);
            Files.writeString(skillPath, content);
        }
    }

    private void cleanupStaleManagedSkillDirectories(Set<SkillEntry> entries) throws IOException {
        Path skillsRoot = getSkillsDirectory().get().getAsFile().toPath();
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
                deleteDirectory(staleDirectory);
            }
        }
    }

    private void cleanupForMode(SkillGenerationMode mode) throws IOException {
        if (mode == SkillGenerationMode.PER_DEPENDENCY) {
            Files.deleteIfExists(getSkillFile().get().getAsFile().toPath());
            return;
        }

        deleteDirectory(perDependencySkillsRoot());
    }

    private String loadTemplate(String resourcePath) throws IOException {
        try (InputStream templateStream = getClass().getClassLoader().getResourceAsStream(resourcePath)) {
            if (templateStream == null) {
                throw new IOException("Unable to load skill template resource: " + resourcePath);
            }
            return new String(templateStream.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private String loadSkillTemplate() throws IOException {
        return loadTemplate(SKILL_TEMPLATE_RESOURCE);
    }

    private void deleteDirectory(Path directory) throws IOException {
        if (Files.notExists(directory)) {
            return;
        }

        try (Stream<Path> stream = Files.walk(directory)) {
            stream.sorted(Comparator.reverseOrder()).forEach(path -> {
                try {
                    Files.delete(path);
                } catch (IOException exception) {
                    throw new RuntimeException(exception);
                }
            });
        } catch (RuntimeException exception) {
            if (exception.getCause() instanceof IOException ioException) {
                throw ioException;
            }
            throw exception;
        }
    }

    private Path toSidecarArchivePath(ModuleCoordinate coordinate) {
        return toAgentSkillDirectory(coordinate).resolve(SIDECAR_FILENAME);
    }

    private Path toAgentSkillDirectory(ModuleCoordinate coordinate) {
        return getSkillsDirectory().get().getAsFile().toPath().resolve(coordinate.skillName());
    }

    private Path perDependencySkillsRoot() {
        return getSkillsDirectory().get().getAsFile().toPath().resolve(PER_DEPENDENCY_SKILLS_SUBDIRECTORY);
    }

    private Path toPerDependencySkillPath(ModuleCoordinate coordinate) {
        return perDependencySkillsRoot()
                .resolve(coordinate.skillName())
                .resolve(SKILL_ENTRYPOINT_FILENAME);
    }

    private boolean isManagedDependencySkillDirectory(Path directory) {
        return Files.exists(directory.resolve(OWNERSHIP_MARKER_FILENAME));
    }

    private void writeOwnershipMarker(Path directory) throws IOException {
        Files.writeString(directory.resolve(OWNERSHIP_MARKER_FILENAME), "");
    }

    private String toRelativePath(Path fromDirectory, Path toPath) {
        return fromDirectory.relativize(toPath).toString().replace('\\', '/');
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
