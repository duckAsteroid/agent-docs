package io.github.duckasteroid.agentdocs.resolve;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Comparator;
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
 * caches each resolved sidecar in a local Maven-style repository, extracts docs into a GAV-based
 * resources layout, and generates local skills for agent consumption.
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
    private static final String ENTRYPOINT_PLACEHOLDER = "{{entrypoint}}";
    private static final String PER_DEPENDENCY_SKILLS_SUBDIRECTORY = "agent-docs-dependencies";

    @Input
    public abstract Property<String> getConfigurationName();

    @Input
    public abstract Property<String> getSkillGenerationMode();

    @Input
    public abstract Property<Integer> getPerDependencySkillThreshold();

    @OutputFile
    public abstract RegularFileProperty getSkillFile();

    @OutputDirectory
    public abstract DirectoryProperty getSkillsDirectory();

    @OutputDirectory
    public abstract DirectoryProperty getResourcesDirectory();

    @OutputDirectory
    public abstract DirectoryProperty getLocalRepositoryDirectory();

    @TaskAction
    void resolveAgentDocs() throws IOException {
        Set<ModuleCoordinate> candidates = collectResolvedDirectDependencies();
        Set<SkillEntry> skillEntries = new LinkedHashSet<>();

        for (ModuleCoordinate coordinate : candidates) {
            Path sidecarPath = resolveSidecar(coordinate);
            if (sidecarPath == null) {
                continue;
            }

            cacheSidecarArtifact(coordinate, sidecarPath);

            Path extractedRoot = extractSidecarToResources(coordinate, sidecarPath);
            Path entrypoint = findEntrypoint(extractedRoot);
            if (entrypoint == null) {
                getLogger().warn("Resolved sidecar for {} but could not locate agents.md entrypoint", coordinate.gav());
                continue;
            }

            skillEntries.add(new SkillEntry(coordinate, entrypoint));
        }

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

    private void cacheSidecarArtifact(ModuleCoordinate coordinate, Path sidecarPath) throws IOException {
        Path destination = toLocalRepositoryPath(coordinate);
        Files.createDirectories(destination.getParent());
        Files.copy(sidecarPath, destination, StandardCopyOption.REPLACE_EXISTING);
    }

    private Path extractSidecarToResources(ModuleCoordinate coordinate, Path sidecarPath) throws IOException {
        Path destination = toResourcesPath(coordinate);
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
                    Files.createDirectories(targetPath.getParent());
                    Files.copy(zipInputStream, targetPath, StandardCopyOption.REPLACE_EXISTING);
                }
                zipInputStream.closeEntry();
            }
        }

        return destination;
    }

    private Path findEntrypoint(Path extractedRoot) throws IOException {
        try (Stream<Path> stream = Files.walk(extractedRoot)) {
            return stream.filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().equalsIgnoreCase("agents.md"))
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
            throw new IllegalArgumentException("agentDocsResolve.perDependencySkillThreshold must be >= 0");
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

            String relativeEntrypoint = toRelativePath(skillPath.getParent(), entry.entrypointPath());
            String content = template
                    .replace(GAV_PLACEHOLDER, entry.coordinate().gav())
                    .replace(ENTRYPOINT_PLACEHOLDER, relativeEntrypoint);
            Files.writeString(skillPath, content);
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

    private Path toLocalRepositoryPath(ModuleCoordinate coordinate) {
        String groupPath = coordinate.group().replace('.', '/');
        return getLocalRepositoryDirectory()
                .get()
                .getAsFile()
                .toPath()
                .resolve(groupPath)
                .resolve(coordinate.artifact())
                .resolve(coordinate.version())
                .resolve(coordinate.artifact() + "-" + coordinate.version() + "-agent-docs.zip");
    }

    private Path toResourcesPath(ModuleCoordinate coordinate) {
        String groupPath = coordinate.group().replace('.', '/');
        return getResourcesDirectory()
                .get()
                .getAsFile()
                .toPath()
                .resolve(groupPath)
                .resolve(coordinate.artifact())
                .resolve(coordinate.version());
    }

    private Path perDependencySkillsRoot() {
        return getSkillsDirectory().get().getAsFile().toPath().resolve(PER_DEPENDENCY_SKILLS_SUBDIRECTORY);
    }

    private Path toPerDependencySkillPath(ModuleCoordinate coordinate) {
        String groupPath = coordinate.group().replace('.', '/');
        return perDependencySkillsRoot()
                .resolve(groupPath)
                .resolve(coordinate.artifact())
                .resolve(coordinate.version() + ".md");
    }

    private String toRelativePath(Path fromDirectory, Path toPath) {
        return fromDirectory.relativize(toPath).toString().replace('\\', '/');
    }

    private record ModuleCoordinate(String group, String artifact, String version) {
        private String gav() {
            return group + ":" + artifact + ":" + version;
        }
    }

    private record SkillEntry(ModuleCoordinate coordinate, Path entrypointPath) {}
}
