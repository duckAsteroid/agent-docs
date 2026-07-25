package io.github.duckasteroid.agentdocs.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
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
            assertNotNull(jarFile.getEntry("agent-docs/SKILL.md"), "Expected embedded SKILL.md inside plugin jar");
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
        writeFile(projectDir.resolve("src/agent-docs/SKILL.md"), """
                ---
                description: Producer skill for testing Gradle plugin publish and resolve.
                ---

                # Sample plugin skill

                See references for usage.
                """);
        writeFile(projectDir.resolve("src/agent-docs/references/overview.md"), "# Overview\n");
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

    private BuildResult runGradle(Path projectDir, String taskName) {
        return GradleRunner.create()
                .withGradleVersion(testGradleVersion())
                .withProjectDir(projectDir.toFile())
                .withArguments(taskName, "--stacktrace")
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
