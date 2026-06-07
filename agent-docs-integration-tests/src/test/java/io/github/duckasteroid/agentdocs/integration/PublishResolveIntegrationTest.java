package io.github.duckasteroid.agentdocs.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.gradle.testkit.runner.BuildResult;
import org.gradle.testkit.runner.GradleRunner;
import org.gradle.testkit.runner.TaskOutcome;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class PublishResolveIntegrationTest {
    private static final String GROUP = "com.example";
    private static final String ARTIFACT = "sample-lib";
    private static final String VERSION = "1.2.3";
    private static final String REWRITTEN_SKILL_NAME = "com-example-sample-lib-1-2-3";

    @TempDir
    Path workspaceDir;

    @Test
    void publishThenResolveSidecarProducesResolverCompatibleSkills() throws IOException {
        String repoRoot = System.getProperty("agentDocs.repoRoot");
        assertNotNull(repoRoot, "Expected test system property 'agentDocs.repoRoot' to be set");

        Path producerDir = workspaceDir.resolve("producer");
        Path consumerDir = workspaceDir.resolve("consumer");
        Path mavenRepo = workspaceDir.resolve("maven-repo");

        createProducerProject(producerDir, mavenRepo, repoRoot);
        BuildResult producerResult = runGradle(producerDir, "publish");
        assertNotNull(producerResult.task(":publish"));
        assertEquals(TaskOutcome.SUCCESS, producerResult.task(":publish").getOutcome());

        Path publishedBase = mavenRepo.resolve("com/example/sample-lib/1.2.3");
        assertTrue(Files.exists(publishedBase.resolve("sample-lib-1.2.3.jar")));
        assertTrue(Files.exists(publishedBase.resolve("sample-lib-1.2.3-agent-docs.zip")));

        createConsumerProject(consumerDir, mavenRepo, repoRoot);
        BuildResult consumerResult = runGradle(consumerDir, "resolveAgentDocs");
        assertNotNull(consumerResult.task(":resolveAgentDocs"));
        assertEquals(TaskOutcome.SUCCESS, consumerResult.task(":resolveAgentDocs").getOutcome());

        Path extractedSkill = consumerDir.resolve(".agent/skills").resolve(REWRITTEN_SKILL_NAME).resolve("SKILL.md");
        assertTrue(Files.exists(extractedSkill));
        assertTrue(Files.readString(extractedSkill).contains("name: " + REWRITTEN_SKILL_NAME));

        Path generatedResolverSkill = consumerDir.resolve(".agent/skills/agent-docs-dependencies")
                .resolve(REWRITTEN_SKILL_NAME)
                .resolve("SKILL.md");
        assertTrue(Files.exists(generatedResolverSkill));

        String generatedSkillContent = Files.readString(generatedResolverSkill);
        assertTrue(generatedSkillContent.contains("name: " + REWRITTEN_SKILL_NAME));
        assertTrue(generatedSkillContent.contains("../../" + REWRITTEN_SKILL_NAME + "/SKILL.md"));
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

                rootProject.name = 'sample-lib'
                """.formatted(escapeForGroovyString(repoRoot)));
        writeFile(projectDir.resolve("build.gradle"), """
                plugins {
                    id 'java-library'
                    id 'maven-publish'
                    id 'io.github.duckasteroid.agent-docs.publish'
                }

                group = '%s'
                version = '%s'

                publishing {
                    repositories {
                        maven {
                            url = uri('%s')
                        }
                    }
                    publications {
                        mavenJava(org.gradle.api.publish.maven.MavenPublication) {
                            from components.java
                        }
                    }
                }
                """.formatted(GROUP, VERSION, escapeForGroovyString(mavenRepo.toAbsolutePath().toString())));
        writeFile(projectDir.resolve("src/main/java/example/Sample.java"), """
                package example;

                public class Sample {
                    public static String hello() {
                        return "hello";
                    }
                }
                """);
        writeFile(projectDir.resolve("src/agent-docs/SKILL.md"), """
                ---
                description: Producer skill for testing publish and resolve.
                ---

                # Producer skill

                See references for dependency usage.
                """);
        writeFile(projectDir.resolve("src/agent-docs/references/overview.md"), "# Overview\n");
    }

    private void createConsumerProject(Path projectDir, Path mavenRepo, String repoRoot) throws IOException {
        writeFile(projectDir.resolve("settings.gradle"), """
                pluginManagement {
                    includeBuild('%s')
                    repositories {
                        mavenLocal()
                        gradlePluginPortal()
                        mavenCentral()
                    }
                }

                rootProject.name = 'consumer'
                """.formatted(escapeForGroovyString(repoRoot)));
        writeFile(projectDir.resolve("build.gradle"), """
                plugins {
                    id 'java-library'
                    id 'io.github.duckasteroid.agent-docs'
                }

                repositories {
                    maven {
                        url = uri('%s')
                    }
                    mavenCentral()
                }

                dependencies {
                    implementation '%s:%s:%s'
                }
                """.formatted(
                escapeForGroovyString(mavenRepo.toAbsolutePath().toString()),
                GROUP,
                ARTIFACT,
                VERSION));
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
                .withProjectDir(projectDir.toFile())
                .withArguments(taskName, "--stacktrace")
                .build();
    }

    private static void writeFile(Path filePath, String content) throws IOException {
        Files.createDirectories(filePath.getParent());
        Files.writeString(filePath, content);
    }

    private static String escapeForGroovyString(String value) {
        return value.replace("\\", "\\\\").replace("'", "\\'");
    }
}
