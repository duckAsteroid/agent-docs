package io.github.duckasteroid.agentdocs.publish;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.gradle.api.GradleException;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.file.DuplicatesStrategy;
import org.gradle.api.plugins.JavaPluginExtension;
import org.gradle.api.provider.Provider;
import org.gradle.api.tasks.Copy;
import org.gradle.api.tasks.TaskProvider;
import org.gradle.api.tasks.bundling.Jar;
import org.gradle.api.tasks.bundling.Zip;
import org.gradle.api.publish.PublishingExtension;
import org.gradle.api.publish.maven.MavenPublication;
import org.gradle.plugin.devel.GradlePluginDevelopmentExtension;
import org.gradle.plugin.devel.PluginDeclaration;

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
 *
 * <p>For {@code java-gradle-plugin} projects, the docs directory is always treated as a parent of
 * one bundle subdirectory per id declared via {@code gradlePlugin { plugins { ... } } } - never a
 * single bundle in its own right, even when only one id is declared - so that a jar registering
 * several plugin ids can carry a distinct agent docs bundle per id
 * ({@code agent-docs/<pluginId>/SKILL.md}) rather than being limited to one. This is scoped to
 * binary Gradle plugins only; see {@code AppliedPluginCollector} on the resolve side for how each
 * applied plugin's own bundle is discovered from a jar that may declare several.
 */
public class AgentDocsPublishPlugin implements Plugin<Project> {
    private static final String EMBEDDED_RESOURCE_ROOT = "agent-docs";

    @Override
    public void apply(Project project) {
        AgentDocsPublishExtension extension =
                project.getExtensions().create("agentDocsPublish", AgentDocsPublishExtension.class);

        extension.getDocsDirectory().convention(project.getLayout().getProjectDirectory().dir("src/agent-docs"));
        extension.getDisabledValidationRules().convention(Set.of());
        extension.getDistribution().convention(AgentDocsDistribution.SIDECAR);

        // Historically this extension was named "agentDocs", which collides with the resolve
        // plugin's own "agentDocs" extension when both plugins are applied to the same project
        // (see duckAsteroid/agent-docs#3). Registered eagerly, not deferred to afterEvaluate, so an
        // inline "agentDocs { ... }" block later in the *same* build script still resolves -
        // Gradle applies every plugin in the plugins {} block, in declared order, before any
        // subsequent script line runs, so whichever of these two plugins applies first wins the
        // name. If the resolve plugin (io.github.duckasteroid.agent-docs) has already applied and
        // claimed "agentDocs" for itself, skip the alias entirely and require "agentDocsPublish {}"
        // instead. If the resolve plugin applies afterwards, it detects this alias and fails the
        // build with guidance to reorder the plugins {} block or switch to "agentDocsPublish {}",
        // rather than surfacing Gradle's raw "extension already registered" error - see
        // AgentDocsResolvePlugin.
        if (project.getExtensions().findByName("agentDocs") == null) {
            project.getLogger().warn(
                    "The 'agentDocs {}' extension block in project '" + project.getPath() + "' is deprecated for "
                            + "io.github.duckasteroid.agent-docs.publish and will be removed in a future release; "
                            + "use 'agentDocsPublish {}' instead.");
            project.getExtensions().add(AgentDocsPublishExtension.class, "agentDocs", extension);
        }

        // Gradle plugin jars aren't resolved as Maven dependencies the way regular libraries are
        // (they're applied via the plugins {} DSL and resolved through the plugin portal/
        // pluginManagement, not the compile/runtime classpath), so a sidecar `agent-docs@zip`
        // published alongside them has no consumer-side resolution path yet. Embedding keeps docs
        // discoverable from the plugin's own jar regardless, so it's both the default and the only
        // supported mode for these projects - an explicit SIDECAR override is a configuration error
        // we fail fast on, rather than silently publishing a sidecar nothing can ever resolve.
        project.getPluginManager().withPlugin("java-gradle-plugin", ignored -> {
            extension.getDistribution().convention(AgentDocsDistribution.EMBEDDED);
            project.afterEvaluate(ignoredProject -> {
                if (extension.getDistribution().get() == AgentDocsDistribution.SIDECAR) {
                    throw new GradleException(
                            "agentDocsPublish.distribution = SIDECAR is not supported in project '" + project.getPath()
                                    + "' because the java-gradle-plugin plugin is applied: Gradle plugins aren't "
                                    + "resolved as Maven dependencies, so a sidecar agent-docs archive would have "
                                    + "no consumer-side resolution path. Remove the distribution override (EMBEDDED "
                                    + "is the default for plugin projects) or set it to EMBEDDED explicitly.");
                }
            });
        });

        // Lazily read at task-execution time rather than eagerly here: the gradlePlugin { plugins
        // { ... } } DSL block that populates GradlePluginDevelopmentExtension typically runs after
        // this plugin's apply(), later in the same build script.
        Provider<Set<String>> declaredPluginIds = project.provider(() -> {
            GradlePluginDevelopmentExtension pluginDevelopment =
                    project.getExtensions().findByType(GradlePluginDevelopmentExtension.class);
            if (pluginDevelopment == null) {
                return Set.of();
            }
            Set<String> ids = new LinkedHashSet<>();
            for (PluginDeclaration declaration : pluginDevelopment.getPlugins()) {
                ids.add(declaration.getId());
            }
            return ids;
        });

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
            task.getDeclaredPluginIds().set(declaredPluginIds);
        });

        TaskProvider<Zip> packageAgentDocs = project.getTasks().register("packageAgentDocs", Zip.class, task -> {
            task.setGroup("agent docs");
            task.setDescription("Packages agent-ready docs as an agent-docs sidecar archive.");
            task.getArchiveBaseName().convention(project.provider(project::getName));
            task.getArchiveClassifier().set("agent-docs");
            task.getDestinationDirectory().set(project.getLayout().getBuildDirectory().dir("agent-docs"));
            task.dependsOn(validateAgentDocs);
            task.onlyIf(ignored -> extension.getDistribution().get() == AgentDocsDistribution.SIDECAR);
            // SIDECAR is unavailable for java-gradle-plugin projects (fails fast above), so this
            // docs directory is always a single bundle here - never the multi plugin-id layout.
            task.doFirst(ignored -> processEntrypoint(
                    extension.getDocsDirectory().get().getAsFile(), task.getTemporaryDir()));
            task.from(extension.getDocsDirectory(), spec -> spec.eachFile(fileCopyDetails -> {
                boolean isRootFile = fileCopyDetails.getRelativePath().getSegments().length == 1;
                if (isRootFile && fileCopyDetails.getName().equalsIgnoreCase("skill.md")) {
                    fileCopyDetails.exclude();
                }
            }));
            task.from(task.getTemporaryDir(), spec -> spec.include("SKILL.md"));
        });

        TaskProvider<Copy> prepareEmbeddedAgentDocs = project.getTasks().register(
                "prepareEmbeddedAgentDocs", Copy.class, task -> {
            task.setGroup("agent docs");
            task.setDescription("Copies agent docs into a resource tree embedded in the built jar.");
            task.dependsOn(validateAgentDocs);
            task.onlyIf(ignored -> extension.getDistribution().get() == AgentDocsDistribution.EMBEDDED);
            task.doFirst(ignored -> processEntrypoints(
                    extension.getDocsDirectory().get().getAsFile(), task.getTemporaryDir(), declaredPluginIds.get()));
            task.into(project.getLayout().getBuildDirectory().dir("agent-docs/embedded"));
            // Deliberately not excluding the raw SKILL.md(s) here (unlike packageAgentDocs' Zip task):
            // Copy's getSource() is @SkipWhenEmpty, and that check runs before doFirst populates the
            // temporary dir, so when docsDirectory contains nothing but SKILL.md, excluding it would
            // make the merged source look empty and the whole task - including doFirst - would be
            // skipped as NO-SOURCE, silently embedding nothing. Copying the raw SKILL.md(s) keeps the
            // source always non-empty (validateAgentDocs already guarantees they exist); the processed
            // copies from the temp dir are added after and overwrite them at the same destination
            // paths - duplicatesStrategy.INCLUDE is required for Copy to allow that overwrite instead
            // of failing on the intentional duplicate relative paths. "*/SKILL.md" additionally
            // matches each plugin's own entrypoint under agent-docs/<pluginId>/ for java-gradle-plugin
            // projects (see class javadoc); it's simply unmatched, and harmless, otherwise.
            task.setDuplicatesStrategy(DuplicatesStrategy.INCLUDE);
            task.from(extension.getDocsDirectory(), spec -> spec.into(EMBEDDED_RESOURCE_ROOT));
            task.from(task.getTemporaryDir(),
                    spec -> spec.into(EMBEDDED_RESOURCE_ROOT).include("SKILL.md", "*/SKILL.md"));
        });

        project.getTasks().matching(task -> task.getName().equals("assemble")).configureEach(task -> task.dependsOn(packageAgentDocs));

        project.getPluginManager().withPlugin("maven-publish", ignored -> project
                .getExtensions()
                .getByType(PublishingExtension.class)
                .getPublications()
                .withType(MavenPublication.class)
                .configureEach(publication -> {
                    if (extension.getDistribution().get() == AgentDocsDistribution.SIDECAR) {
                        publication.artifact(packageAgentDocs);
                    }
                }));

        project.getPluginManager().withPlugin("java", ignored -> project
                .getExtensions()
                .getByType(JavaPluginExtension.class)
                .getSourceSets()
                .getByName("main")
                .getResources()
                .srcDir(prepareEmbeddedAgentDocs));

        // Single Agent-Docs attribute per specification/java-conventions.md: bare "classpath"
        // since embedding always uses the conventional default path, or the explicit-coordinate
        // "maven:<gav>" form (rather than a bare "maven" marker) so this keeps working correctly
        // even if a project's distribution mode changes later.
        project.getTasks().withType(Jar.class).matching(task -> task.getName().equals("jar")).configureEach(jarTask -> {
            AgentDocsDistribution mode = extension.getDistribution().get();
            String coordinates = project.getGroup() + ":" + project.getName() + ":" + project.getVersion();
            String agentDocsValue = mode == AgentDocsDistribution.EMBEDDED ? "classpath" : "maven:" + coordinates;
            jarTask.getManifest().attributes(Map.of("Agent-Docs", agentDocsValue));
        });
    }

    /**
     * Processes the entrypoint for every declared plugin's own bundle subdirectory, or the docs
     * directory's own entrypoint when {@code pluginIds} is empty (not a Gradle plugin project).
     */
    private static void processEntrypoints(File docsDirectory, File destinationDir, Set<String> pluginIds) {
        if (pluginIds.isEmpty()) {
            processEntrypoint(docsDirectory, destinationDir);
            return;
        }
        for (String pluginId : pluginIds) {
            processEntrypoint(new File(docsDirectory, pluginId), new File(destinationDir, pluginId));
        }
    }

    private static void processEntrypoint(File docsDirectory, File destinationDir) {
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
        Path packagedEntrypoint = destinationDir.toPath().resolve("SKILL.md");
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
