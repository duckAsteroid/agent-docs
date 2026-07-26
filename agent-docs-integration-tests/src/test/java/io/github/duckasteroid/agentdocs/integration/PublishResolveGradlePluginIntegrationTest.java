package io.github.duckasteroid.agentdocs.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.jar.JarFile;
import org.gradle.testkit.runner.BuildResult;
import org.gradle.testkit.runner.GradleRunner;
import org.gradle.testkit.runner.TaskOutcome;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class PublishResolveGradlePluginIntegrationTest {
    private static final String GROUP = "com.example";
    private static final String ARTIFACT = "sample-gradle-plugin";
    private static final String VERSION = "1.2.3";
    private static final String PLUGIN_ID = "com.example.sample-plugin";
    private static final String EXPECTED_SKILL_NAME = "sample-plugin";
    private static final String DEFAULT_TEST_GRADLE_VERSION = "9.5.1";

    private static final String MULTI_ID_ARTIFACT = "multi-id-gradle-plugin";
    private static final String FIRST_PLUGIN_ID = "com.example.first-plugin";
    private static final String SECOND_PLUGIN_ID = "com.example.second-plugin";
    private static final String EXPECTED_FIRST_SKILL_NAME = "first-plugin";

    @TempDir
    Path workspaceDir;

    @Test
    void publishThenResolveEmbeddedGradlePluginDocsProducesPluginIdSkill() throws IOException {
        String repoRoot = System.getProperty("agentDocs.repoRoot");
        assertNotNull(repoRoot, "Expected test system property 'agentDocs.repoRoot' to be set");

        Path producerDir = workspaceDir.resolve("producer");
        Path consumerDir = workspaceDir.resolve("consumer");
        Path mavenRepo = workspaceDir.resolve("maven-repo");

        createProducerProject(producerDir, mavenRepo, repoRoot);
        BuildResult producerResult = runGradle(producerDir, "publish");
        assertNotNull(producerResult.task(":publish"));
        assertEquals(TaskOutcome.SUCCESS, producerResult.task(":publish").getOutcome());

        Path publishedJar = mavenRepo.resolve("com/example/sample-gradle-plugin/1.2.3/sample-gradle-plugin-1.2.3.jar");
        assertTrue(Files.exists(publishedJar), "Expected plugin implementation jar to be published");
        try (JarFile jarFile = new JarFile(publishedJar.toFile())) {
            assertEquals("classpath", jarFile.getManifest().getMainAttributes().getValue("Agent-Docs"));
            assertNotNull(jarFile.getEntry("agent-docs/" + PLUGIN_ID + "/SKILL.md"),
                    "Expected embedded SKILL.md inside plugin jar under its plugin id subdirectory");
            assertNotNull(jarFile.getEntry("META-INF/gradle-plugins/" + PLUGIN_ID + ".properties"),
                    "Expected plugin descriptor inside plugin jar");
        }
        assertTrue(Files.notExists(mavenRepo.resolve("com/example/sample-gradle-plugin/1.2.3/sample-gradle-plugin-1.2.3-agent-docs.zip")),
                "Expected no sidecar zip for an embedded (Gradle plugin) distribution");

        createConsumerProject(consumerDir, mavenRepo, repoRoot);
        BuildResult consumerResult = runGradle(consumerDir, "resolveAgentDocs");
        assertNotNull(consumerResult.task(":resolveAgentDocs"));
        assertEquals(TaskOutcome.SUCCESS, consumerResult.task(":resolveAgentDocs").getOutcome());

        Path extractedSkillRoot = consumerDir.resolve(".agents/skills").resolve(EXPECTED_SKILL_NAME);
        Path extractedSkill = extractedSkillRoot.resolve("SKILL.md");
        assertTrue(Files.exists(extractedSkill), "Expected plugin skill folder at " + extractedSkill);
        assertTrue(Files.exists(extractedSkillRoot.resolve("references/overview.md")));

        String content = Files.readString(extractedSkill);
        assertTrue(content.contains("name: " + EXPECTED_SKILL_NAME));
        assertTrue(content.contains("pluginId: " + PLUGIN_ID));
        assertTrue(!content.contains("group:"), "Plugin-sourced skills should not carry GAV metadata");
        assertTrue(content.contains("Reference documentation for the Gradle plugin `" + PLUGIN_ID + "`"));
    }

    @Test
    void publishThenResolveOnlyMaterializesSkillForAppliedPluginIdFromMultiIdJar() throws IOException {
        String repoRoot = System.getProperty("agentDocs.repoRoot");
        assertNotNull(repoRoot, "Expected test system property 'agentDocs.repoRoot' to be set");

        Path producerDir = workspaceDir.resolve("multi-id-producer");
        Path consumerDir = workspaceDir.resolve("multi-id-consumer");
        Path mavenRepo = workspaceDir.resolve("multi-id-maven-repo");

        createMultiIdProducerProject(producerDir, mavenRepo, repoRoot);
        BuildResult producerResult = runGradle(producerDir, "publish");
        assertNotNull(producerResult.task(":publish"));
        assertEquals(TaskOutcome.SUCCESS, producerResult.task(":publish").getOutcome());

        Path publishedJar = mavenRepo.resolve(
                "com/example/" + MULTI_ID_ARTIFACT + "/" + VERSION + "/" + MULTI_ID_ARTIFACT + "-" + VERSION + ".jar");
        assertTrue(Files.exists(publishedJar), "Expected plugin implementation jar to be published");
        try (JarFile jarFile = new JarFile(publishedJar.toFile())) {
            assertEquals("classpath", jarFile.getManifest().getMainAttributes().getValue("Agent-Docs"));
            assertNotNull(jarFile.getEntry("agent-docs/" + FIRST_PLUGIN_ID + "/SKILL.md"),
                    "Expected both declared ids' bundles to be embedded in the jar regardless of which get applied");
            assertNotNull(jarFile.getEntry("agent-docs/" + SECOND_PLUGIN_ID + "/SKILL.md"),
                    "Expected both declared ids' bundles to be embedded in the jar regardless of which get applied");
        }

        // Consumer applies only FIRST_PLUGIN_ID; SECOND_PLUGIN_ID is declared by the producer but
        // never applied here, and must not get a materialized skill as a result.
        createConsumerApplyingOnlyFirstPluginId(consumerDir, mavenRepo, repoRoot);
        BuildResult consumerResult = runGradle(consumerDir, "resolveAgentDocs");
        assertNotNull(consumerResult.task(":resolveAgentDocs"));
        assertEquals(TaskOutcome.SUCCESS, consumerResult.task(":resolveAgentDocs").getOutcome());

        Path skillsRoot = consumerDir.resolve(".agents/skills");
        Path firstSkill = skillsRoot.resolve(EXPECTED_FIRST_SKILL_NAME).resolve("SKILL.md");
        assertTrue(Files.exists(firstSkill), "Expected applied plugin id's skill folder at " + firstSkill);
        assertTrue(Files.readString(firstSkill).contains("pluginId: " + FIRST_PLUGIN_ID));

        try (var entries = Files.list(skillsRoot)) {
            List<String> skillDirectoryNames = entries.map(path -> path.getFileName().toString()).toList();
            assertFalse(skillDirectoryNames.stream().anyMatch(name -> name.contains("second")),
                    "Expected no skill materialized for the declared-but-unapplied second plugin id, found: "
                            + skillDirectoryNames);
        }
    }

    @Test
    void bothPublishAndResolvePluginsCanBeAppliedTogetherWhenResolveIsDeclaredFirst() throws IOException {
        // Regression test for duckAsteroid/agent-docs#3: both plugins used to unconditionally
        // register an "agentDocs" extension, so applying agent-docs.publish and agent-docs
        // (resolve) to the same project failed with "Cannot add extension with name 'agentDocs'".
        // Declaring the resolve plugin first means it claims "agentDocs" for itself; the publish
        // plugin then detects the name is taken and skips its deprecated alias, registering only
        // "agentDocsPublish".
        String repoRoot = System.getProperty("agentDocs.repoRoot");
        assertNotNull(repoRoot, "Expected test system property 'agentDocs.repoRoot' to be set");

        Path projectDir = workspaceDir.resolve("dual-role");
        writeFile(projectDir.resolve("settings.gradle"), """
                pluginManagement {
                    includeBuild('%s')
                    repositories {
                        mavenLocal()
                        gradlePluginPortal()
                        mavenCentral()
                    }
                }

                rootProject.name = 'dual-role'
                """.formatted(escapeForGroovyString(repoRoot)));
        writeFile(projectDir.resolve("build.gradle"), """
                plugins {
                    id 'java-gradle-plugin'
                    id 'io.github.duckasteroid.agent-docs'
                    id 'io.github.duckasteroid.agent-docs.publish'
                }

                group = 'com.example'
                version = '1.0.0'

                gradlePlugin {
                    plugins {
                        dual {
                            id = 'com.example.dual-role'
                            implementationClass = 'com.example.DualRolePlugin'
                        }
                    }
                }

                agentDocsPublish {
                    docsDirectory = layout.projectDirectory.dir('src/agent-docs')
                }

                agentDocs {
                    includeSources = false
                }
                """);
        writeFile(projectDir.resolve("src/main/java/com/example/DualRolePlugin.java"), """
                package com.example;

                import org.gradle.api.Plugin;
                import org.gradle.api.Project;

                public class DualRolePlugin implements Plugin<Project> {
                    public void apply(Project project) {
                    }
                }
                """);
        writeFile(projectDir.resolve("src/agent-docs/com.example.dual-role/SKILL.md"), """
                ---
                description: A project that both publishes its own docs and resolves dependency docs.
                ---

                # Dual role plugin skill
                """);

        BuildResult result = runGradle(projectDir, "jar", "resolveAgentDocs");

        assertNotNull(result.task(":jar"));
        assertEquals(TaskOutcome.SUCCESS, result.task(":jar").getOutcome());
        assertNotNull(result.task(":resolveAgentDocs"));
        assertEquals(TaskOutcome.SUCCESS, result.task(":resolveAgentDocs").getOutcome());
        assertTrue(!result.getOutput().contains("Cannot add extension"),
                "Expected no extension name collision between the publish and resolve plugins");
        assertTrue(!result.getOutput().contains("extension block"),
                "Expected no deprecated agentDocs {} alias warning when the resolve plugin is declared first");
    }

    @Test
    void bothPublishAndResolvePluginsFailWithGuidanceWhenPublishIsDeclaredFirst() throws IOException {
        // The reverse declaration order can't be made to work transparently: Gradle applies
        // plugins in declared order, synchronously, before any later script line runs, and
        // ExtensionContainer offers no way to remove/replace an extension once registered. So when
        // agent-docs.publish is declared first, it eagerly claims "agentDocs" as its deprecated
        // alias before the resolve plugin ever applies, and the resolve plugin must fail fast with
        // actionable guidance rather than surface Gradle's raw "extension already registered"
        // error.
        String repoRoot = System.getProperty("agentDocs.repoRoot");
        assertNotNull(repoRoot, "Expected test system property 'agentDocs.repoRoot' to be set");

        Path projectDir = workspaceDir.resolve("dual-role-wrong-order");
        writeFile(projectDir.resolve("settings.gradle"), """
                pluginManagement {
                    includeBuild('%s')
                    repositories {
                        mavenLocal()
                        gradlePluginPortal()
                        mavenCentral()
                    }
                }

                rootProject.name = 'dual-role-wrong-order'
                """.formatted(escapeForGroovyString(repoRoot)));
        writeFile(projectDir.resolve("build.gradle"), """
                plugins {
                    id 'java-gradle-plugin'
                    id 'io.github.duckasteroid.agent-docs.publish'
                    id 'io.github.duckasteroid.agent-docs'
                }

                group = 'com.example'
                version = '1.0.0'

                gradlePlugin {
                    plugins {
                        dual {
                            id = 'com.example.dual-role'
                            implementationClass = 'com.example.DualRolePlugin'
                        }
                    }
                }
                """);
        writeFile(projectDir.resolve("src/main/java/com/example/DualRolePlugin.java"), """
                package com.example;

                import org.gradle.api.Plugin;
                import org.gradle.api.Project;

                public class DualRolePlugin implements Plugin<Project> {
                    public void apply(Project project) {
                    }
                }
                """);
        writeFile(projectDir.resolve("src/agent-docs/com.example.dual-role/SKILL.md"), """
                ---
                description: A project that both publishes its own docs and resolves dependency docs.
                ---

                # Dual role plugin skill
                """);

        BuildResult result = GradleRunner.create()
                .withGradleVersion(testGradleVersion())
                .withProjectDir(projectDir.toFile())
                .withArguments("jar", "--stacktrace")
                .buildAndFail();

        assertTrue(result.getOutput().contains("Cannot apply 'io.github.duckasteroid.agent-docs'"),
                "Expected a clear failure identifying the resolve plugin as unable to apply");
        assertTrue(result.getOutput().contains("declare 'io.github.duckasteroid.agent-docs' before "
                        + "'io.github.duckasteroid.agent-docs.publish'"),
                "Expected guidance to reorder the plugins {} block");
        assertTrue(result.getOutput().contains("agentDocsPublish {}"),
                "Expected guidance pointing at agentDocsPublish {} as the alternative fix");
    }

    private void createMultiIdProducerProject(Path projectDir, Path mavenRepo, String repoRoot) throws IOException {
        writeFile(projectDir.resolve("settings.gradle"), """
                pluginManagement {
                    includeBuild('%s')
                    repositories {
                        mavenLocal()
                        gradlePluginPortal()
                        mavenCentral()
                    }
                }

                rootProject.name = '%s'
                """.formatted(escapeForGroovyString(repoRoot), MULTI_ID_ARTIFACT));
        writeFile(projectDir.resolve("build.gradle"), """
                plugins {
                    id 'java-gradle-plugin'
                    id 'maven-publish'
                    id 'io.github.duckasteroid.agent-docs.publish'
                }

                group = '%s'
                version = '%s'

                gradlePlugin {
                    plugins {
                        first {
                            id = '%s'
                            implementationClass = 'com.example.FirstPlugin'
                        }
                        second {
                            id = '%s'
                            implementationClass = 'com.example.SecondPlugin'
                        }
                    }
                }

                publishing {
                    repositories {
                        maven {
                            url = uri('%s')
                        }
                    }
                }
                """.formatted(GROUP, VERSION, FIRST_PLUGIN_ID, SECOND_PLUGIN_ID,
                escapeForGroovyString(mavenRepo.toAbsolutePath().toString())));
        writeFile(projectDir.resolve("src/main/java/com/example/FirstPlugin.java"), """
                package com.example;

                import org.gradle.api.Plugin;
                import org.gradle.api.Project;

                public class FirstPlugin implements Plugin<Project> {
                    public void apply(Project project) {
                    }
                }
                """);
        writeFile(projectDir.resolve("src/main/java/com/example/SecondPlugin.java"), """
                package com.example;

                import org.gradle.api.Plugin;
                import org.gradle.api.Project;

                public class SecondPlugin implements Plugin<Project> {
                    public void apply(Project project) {
                    }
                }
                """);
        writeFile(projectDir.resolve("src/agent-docs/" + FIRST_PLUGIN_ID + "/SKILL.md"), """
                ---
                description: First plugin's own docs bundle.
                ---

                # First plugin skill
                """);
        writeFile(projectDir.resolve("src/agent-docs/" + SECOND_PLUGIN_ID + "/SKILL.md"), """
                ---
                description: Second plugin's own docs bundle.
                ---

                # Second plugin skill
                """);
    }

    private void createConsumerApplyingOnlyFirstPluginId(Path projectDir, Path mavenRepo, String repoRoot) throws IOException {
        writeFile(projectDir.resolve("settings.gradle"), """
                pluginManagement {
                    includeBuild('%s')
                    repositories {
                        maven {
                            url = uri('%s')
                        }
                        mavenLocal()
                        gradlePluginPortal()
                        mavenCentral()
                    }
                }

                rootProject.name = 'multi-id-consumer'
                """.formatted(escapeForGroovyString(repoRoot), escapeForGroovyString(mavenRepo.toAbsolutePath().toString())));
        writeFile(projectDir.resolve("build.gradle"), """
                plugins {
                    id 'java-library'
                    id 'io.github.duckasteroid.agent-docs'
                    id '%s' version '%s'
                }
                """.formatted(FIRST_PLUGIN_ID, VERSION));
        writeFile(projectDir.resolve("src/main/java/example/App.java"), """
                package example;

                public class App {
                    public static String value() {
                        return "ok";
                    }
                }
                """);
    }

    private void createProducerProject(Path projectDir, Path mavenRepo, String repoRoot) throws IOException {
        writeFile(projectDir.resolve("settings.gradle"), """
                pluginManagement {
                    includeBuild('%s')
                    repositories {
                        mavenLocal()
                        gradlePluginPortal()
                        mavenCentral()
                    }
                }

                rootProject.name = 'sample-gradle-plugin'
                """.formatted(escapeForGroovyString(repoRoot)));
        writeFile(projectDir.resolve("build.gradle"), """
                plugins {
                    id 'java-gradle-plugin'
                    id 'maven-publish'
                    id 'io.github.duckasteroid.agent-docs.publish'
                }

                group = '%s'
                version = '%s'

                gradlePlugin {
                    plugins {
                        sample {
                            id = '%s'
                            implementationClass = 'com.example.SamplePlugin'
                        }
                    }
                }

                publishing {
                    repositories {
                        maven {
                            url = uri('%s')
                        }
                    }
                }
                """.formatted(GROUP, VERSION, PLUGIN_ID, escapeForGroovyString(mavenRepo.toAbsolutePath().toString())));
        writeFile(projectDir.resolve("src/main/java/com/example/SamplePlugin.java"), """
                package com.example;

                import org.gradle.api.Plugin;
                import org.gradle.api.Project;

                public class SamplePlugin implements Plugin<Project> {
                    public void apply(Project project) {
                    }
                }
                """);
        writeFile(projectDir.resolve("src/agent-docs/" + PLUGIN_ID + "/SKILL.md"), """
                ---
                description: Producer skill for testing Gradle plugin publish and resolve.
                ---

                # Sample plugin skill

                See references for usage.
                """);
        writeFile(projectDir.resolve("src/agent-docs/" + PLUGIN_ID + "/references/overview.md"), "# Overview\n");
    }

    private void createConsumerProject(Path projectDir, Path mavenRepo, String repoRoot) throws IOException {
        writeFile(projectDir.resolve("settings.gradle"), """
                pluginManagement {
                    includeBuild('%s')
                    repositories {
                        maven {
                            url = uri('%s')
                        }
                        mavenLocal()
                        gradlePluginPortal()
                        mavenCentral()
                    }
                }

                rootProject.name = 'consumer'
                """.formatted(escapeForGroovyString(repoRoot), escapeForGroovyString(mavenRepo.toAbsolutePath().toString())));
        writeFile(projectDir.resolve("build.gradle"), """
                plugins {
                    id 'java-library'
                    id 'io.github.duckasteroid.agent-docs'
                    id '%s' version '%s'
                }
                """.formatted(PLUGIN_ID, VERSION));
        writeFile(projectDir.resolve("src/main/java/example/App.java"), """
                package example;

                public class App {
                    public static String value() {
                        return "ok";
                    }
                }
                """);
    }

    private BuildResult runGradle(Path projectDir, String... taskNames) {
        List<String> arguments = new ArrayList<>(List.of(taskNames));
        arguments.add("--stacktrace");
        return GradleRunner.create()
                .withGradleVersion(testGradleVersion())
                .withProjectDir(projectDir.toFile())
                .withArguments(arguments)
                .build();
    }

    private static String testGradleVersion() {
        return System.getProperty("agentDocs.test.gradleVersion", DEFAULT_TEST_GRADLE_VERSION);
    }

    private static void writeFile(Path filePath, String content) throws IOException {
        Files.createDirectories(filePath.getParent());
        Files.writeString(filePath, content);
    }

    private static String escapeForGroovyString(String value) {
        return value.replace("\\", "\\\\").replace("'", "\\'");
    }
}
