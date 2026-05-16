package io.github.duckasteroid.agentdocs.resolve;

import java.io.IOException;
import java.nio.file.StandardCopyOption;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.Set;
import org.gradle.api.DefaultTask;
import org.gradle.api.Project;
import org.gradle.api.artifacts.ModuleVersionIdentifier;
import org.gradle.api.artifacts.ModuleDependency;
import org.gradle.api.artifacts.result.ResolvedComponentResult;
import org.gradle.api.artifacts.component.ProjectComponentIdentifier;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.OutputDirectory;
import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.TaskAction;

public abstract class ResolveAgentDocsTask extends DefaultTask {
    @Input
    public abstract Property<String> getConfigurationName();

    @OutputFile
    public abstract RegularFileProperty getOutputFile();

    @OutputDirectory
    public abstract DirectoryProperty getLocalRepositoryDirectory();

    @TaskAction
    void resolveAgentDocs() throws IOException {
        Set<IndexEntry> entries = new LinkedHashSet<>();
        var configuration = getProject().getConfigurations().getByName(getConfigurationName().get());

        for (ResolvedComponentResult component : configuration.getIncoming().getResolutionResult().getAllComponents()) {
            ModuleVersionIdentifier moduleVersion = component.getModuleVersion();
            if (moduleVersion != null) {
                entries.add(new IndexEntry(moduleVersion.getGroup(), moduleVersion.getName(), moduleVersion.getVersion()));
                continue;
            }

            if (component.getId() instanceof ProjectComponentIdentifier projectId) {
                Project dependencyProject = getProject().findProject(projectId.getProjectPath());
                if (dependencyProject != null) {
                    entries.add(new IndexEntry(
                            String.valueOf(dependencyProject.getGroup()),
                            dependencyProject.getName(),
                            String.valueOf(dependencyProject.getVersion())));
                }
            }
        }

        cacheSidecarArtifacts();

        Path outputPath = getOutputFile().get().getAsFile().toPath();
        Files.createDirectories(outputPath.getParent());
        Files.writeString(outputPath, toJson(entries));
    }

    private void cacheSidecarArtifacts() throws IOException {
        Set<ModuleCoordinate> candidates = new LinkedHashSet<>();
        collectDirectModuleDependencies("implementation", candidates);
        collectDirectModuleDependencies("api", candidates);

        for (ModuleCoordinate coordinate : candidates) {
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
                continue;
            }

            if (files.isEmpty()) {
                continue;
            }

            Path destination = toLocalRepositoryPath(coordinate);
            Files.createDirectories(destination.getParent());
            Files.copy(files.iterator().next().toPath(), destination, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private void collectDirectModuleDependencies(String configurationName, Set<ModuleCoordinate> candidates) {
        var configurations = getProject().getConfigurations();
        var configuration = configurations.findByName(configurationName);
        if (configuration == null) {
            return;
        }

        configuration.getDependencies().withType(ModuleDependency.class).forEach(dependency -> {
            String group = dependency.getGroup();
            String name = dependency.getName();
            String version = dependency.getVersion();
            if (group == null || group.isBlank() || name == null || name.isBlank() || version == null || version.isBlank()) {
                return;
            }
            candidates.add(new ModuleCoordinate(group, name, version));
        });
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

    private static String toJson(Set<IndexEntry> entries) {
        StringBuilder builder = new StringBuilder();
        builder.append("[\n");

        int index = 0;
        for (IndexEntry entry : entries) {
            if (index > 0) {
                builder.append(",\n");
            }
            builder.append("  {")
                    .append("\"group\":\"").append(escape(entry.group())).append("\",")
                    .append("\"artifact\":\"").append(escape(entry.artifact())).append("\",")
                    .append("\"version\":\"").append(escape(entry.version())).append("\",")
                    .append("\"gav\":\"").append(escape(entry.gav())).append("\"")
                    .append("}");
            index++;
        }

        builder.append("\n]\n");
        return builder.toString();
    }

    private static String escape(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private record IndexEntry(String group, String artifact, String version) {
        private String gav() {
            return group + ":" + artifact + ":" + version;
        }
    }

    private record ModuleCoordinate(String group, String artifact, String version) {
        private String gav() {
            return group + ":" + artifact + ":" + version;
        }
    }
}

