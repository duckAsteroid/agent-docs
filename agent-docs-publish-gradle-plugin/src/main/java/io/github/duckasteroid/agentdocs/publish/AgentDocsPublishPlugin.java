package io.github.duckasteroid.agentdocs.publish;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.provider.Provider;
import org.gradle.api.tasks.TaskProvider;
import org.gradle.api.tasks.bundling.Zip;
import org.gradle.api.publish.PublishingExtension;
import org.gradle.api.publish.maven.MavenPublication;

/**
 * Registers publish-side tasks that validate and package docs into an {@code agent-docs} sidecar zip.
 *
 * <p>Validation enforces the Agent Skills contract for the docs root: one {@code SKILL.md}
 * entrypoint, required {@code description} frontmatter, warning-level handling for publisher
 * {@code name} frontmatter, and standard folder shape for {@code scripts}, {@code references},
 * and {@code assets} when present.
 *
 * <p>During packaging, the publisher removes any frontmatter {@code name} field from sidecar
 * {@code SKILL.md} because resolver-generated G__A__V naming is authoritative.
 *
 * <p>When {@code maven-publish} is present, the packaged archive is attached to every
 * {@link MavenPublication} with classifier {@code agent-docs}.
 */
public class AgentDocsPublishPlugin implements Plugin<Project> {
    @Override
    public void apply(Project project) {
        AgentDocsPublishExtension extension =
                project.getExtensions().create("agentDocs", AgentDocsPublishExtension.class);

        extension.getDocsDirectory().convention(project.getLayout().getProjectDirectory().dir("src/agent-docs"));
        extension.getDisabledValidationRules().convention(Set.of());

        Provider<Set<String>> disabledRulesFromProperties = project.getProviders()
                .gradleProperty("agentDocs.disabledValidationRules")
                .orElse(project.getProviders().systemProperty("agentDocs.disabledValidationRules"))
                .map(AgentDocsPublishPlugin::parseDisabledRulesCsv)
                .orElse(Set.of());

        TaskProvider<ValidateAgentDocsTask> validateAgentDocs = project.getTasks().register(
                "validateAgentDocs", ValidateAgentDocsTask.class, task -> {
            task.setGroup("agent docs");
            task.setDescription("Validates that the agent docs directory follows the Agent Skills spec.");
            task.getDocsDirectory().set(extension.getDocsDirectory());
            task.getDisabledValidationRules().set(extension.getDisabledValidationRules());
            task.getDisabledValidationRules().addAll(disabledRulesFromProperties);
        });

        TaskProvider<Zip> packageAgentDocs = project.getTasks().register("packageAgentDocs", Zip.class, task -> {
            task.setGroup("agent docs");
            task.setDescription("Packages agent-ready docs as an agent-docs sidecar archive.");
            task.getArchiveBaseName().convention(project.provider(project::getName));
            task.getArchiveClassifier().set("agent-docs");
            task.getDestinationDirectory().set(project.getLayout().getBuildDirectory().dir("agent-docs"));
            task.dependsOn(validateAgentDocs);
            task.doFirst(ignored -> writePackagedSkillEntrypoint(task, extension.getDocsDirectory().get().getAsFile()));
            task.from(extension.getDocsDirectory(), spec -> spec.eachFile(fileCopyDetails -> {
                boolean isRootFile = fileCopyDetails.getRelativePath().getSegments().length == 1;
                if (isRootFile && fileCopyDetails.getName().equalsIgnoreCase("skill.md")) {
                    fileCopyDetails.exclude();
                }
            }));
            task.from(task.getTemporaryDir(), spec -> spec.include("SKILL.md"));
        });

        project.getPluginManager().withPlugin("maven-publish", ignored -> project
                .getExtensions()
                .getByType(PublishingExtension.class)
                .getPublications()
                .withType(MavenPublication.class)
                .configureEach(publication -> publication.artifact(packageAgentDocs)));

        project.getTasks().matching(task -> task.getName().equals("assemble")).configureEach(task -> task.dependsOn(packageAgentDocs));
    }

    private static void writePackagedSkillEntrypoint(Zip task, File docsDirectory) {
        File sourceEntrypoint = Arrays.stream(docsDirectory.listFiles(file -> file.isFile() && file.getName().equalsIgnoreCase("skill.md")))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Unable to locate SKILL.md in docs directory: " + docsDirectory));

        String content;
        try {
            content = Files.readString(sourceEntrypoint.toPath());
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to read SKILL.md entrypoint: " + sourceEntrypoint, exception);
        }

        String transformed = removeNameFromFrontmatter(content);
        Path packagedEntrypoint = task.getTemporaryDir().toPath().resolve("SKILL.md");
        try {
            Files.createDirectories(packagedEntrypoint.getParent());
            Files.writeString(packagedEntrypoint, transformed);
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to write packaged SKILL.md entrypoint: " + packagedEntrypoint, exception);
        }
    }

    private static String removeNameFromFrontmatter(String content) {
        String normalized = content.replace("\r\n", "\n");
        if (!normalized.startsWith("---\n")) {
            return normalized;
        }

        int closingIndex = normalized.indexOf("\n---\n", 4);
        if (closingIndex < 0) {
            return normalized;
        }

        String frontmatterBlock = normalized.substring(4, closingIndex);
        String body = normalized.substring(closingIndex + 5);

        List<String> retainedLines = new ArrayList<>();
        for (String line : frontmatterBlock.split("\n", -1)) {
            String trimmed = line.trim();
            if (trimmed.startsWith("name:")) {
                continue;
            }
            retainedLines.add(line);
        }

        return "---\n" + String.join("\n", retainedLines) + "\n---\n" + body;
    }

    private static Set<String> parseDisabledRulesCsv(String csv) {
        Set<String> rules = new LinkedHashSet<>();
        for (String token : csv.split(",")) {
            String trimmed = token.trim();
            if (!trimmed.isEmpty()) {
                rules.add(trimmed);
            }
        }
        return rules;
    }
}
