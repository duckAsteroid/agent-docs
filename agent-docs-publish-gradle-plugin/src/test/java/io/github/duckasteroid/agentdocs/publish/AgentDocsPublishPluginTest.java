package io.github.duckasteroid.agentdocs.publish;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import org.gradle.testkit.runner.BuildResult;
import org.gradle.testkit.runner.GradleRunner;
import org.gradle.testkit.runner.TaskOutcome;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class AgentDocsPublishPluginTest {

    @TempDir
    Path projectDir;

    @Test
    void packageAgentDocsRunsDuringAssembleAndArchivesAgentDocsFolder() throws IOException {
        writeFile(projectDir.resolve("settings.gradle"), "rootProject.name = 'sample-lib'\n");
        writeFile(projectDir.resolve("build.gradle"), """
                plugins {
                    id 'java'
                    id 'io.github.duckasteroid.agent-docs.publish'
                }
                """);
        writeFile(projectDir.resolve("src/agentDocs/AgEnTs.Md"), "# Agent Docs\n");
        writeFile(projectDir.resolve("src/agentDocs/topics/overview.md"), "# Overview\n");

        BuildResult result = GradleRunner.create()
                .withProjectDir(projectDir.toFile())
                .withPluginClasspath()
                .withArguments("assemble")
                .build();

        assertNotNull(result.task(":packageAgentDocs"));
        assertEquals(TaskOutcome.SUCCESS, result.task(":packageAgentDocs").getOutcome());

        Path archivePath = projectDir.resolve("build/agent-docs/sample-lib-agent-docs.zip");
        assertTrue(Files.exists(archivePath), "Expected archive to be created");

        try (ZipFile zipFile = new ZipFile(archivePath.toFile())) {
            ZipEntry agentsFile = zipFile.getEntry("agents.md");
            ZipEntry overviewFile = zipFile.getEntry("topics/overview.md");
            assertNotNull(agentsFile, "Expected lowercase agents.md entrypoint in archive");
            assertNotNull(overviewFile, "Expected overview file in archive");
        }
    }

    @Test
    void packageAgentDocsFailsWhenDefaultDocsDirectoryIsMissing() throws IOException {
        writeFile(projectDir.resolve("settings.gradle"), "rootProject.name = 'sample-lib'\n");
        writeFile(projectDir.resolve("build.gradle"), """
                plugins {
                    id 'java'
                    id 'io.github.duckasteroid.agent-docs.publish'
                }
                """);

        BuildResult result = GradleRunner.create()
                .withProjectDir(projectDir.toFile())
                .withPluginClasspath()
                .withArguments("packageAgentDocs")
                .buildAndFail();

        assertTrue(result.getOutput().contains("Agent docs directory does not exist:"));
    }

    @Test
    void packageAgentDocsFailsWhenAgentsEntrypointIsMissing() throws IOException {
        writeFile(projectDir.resolve("settings.gradle"), "rootProject.name = 'sample-lib'\n");
        writeFile(projectDir.resolve("build.gradle"), """
                plugins {
                    id 'java'
                    id 'io.github.duckasteroid.agent-docs.publish'
                }
                """);
        writeFile(projectDir.resolve("src/agentDocs/topics/overview.md"), "# Overview\n");

        BuildResult result = GradleRunner.create()
                .withProjectDir(projectDir.toFile())
                .withPluginClasspath()
                .withArguments("packageAgentDocs")
                .buildAndFail();

        assertTrue(result.getOutput().contains("must contain AGENTS.md (or agents.md)"));
    }

    @Test
    void packageAgentDocsFailsWhenMultipleAgentsEntrypointsExistWithDifferentCase() throws IOException {
        writeFile(projectDir.resolve("settings.gradle"), "rootProject.name = 'sample-lib'\n");
        writeFile(projectDir.resolve("build.gradle"), """
                plugins {
                    id 'java'
                    id 'io.github.duckasteroid.agent-docs.publish'
                }
                """);
        writeFile(projectDir.resolve("src/agentDocs/AGENTS.md"), "# Agent Docs\n");
        writeFile(projectDir.resolve("src/agentDocs/agents.md"), "# Agent Docs Duplicate\n");

        BuildResult result = GradleRunner.create()
                .withProjectDir(projectDir.toFile())
                .withPluginClasspath()
                .withArguments("packageAgentDocs")
                .buildAndFail();

        assertTrue(result.getOutput().contains("must contain exactly one AGENTS.md file"));
    }

    @Test
    void packageAgentDocsUsesConfiguredDocsDirectory() throws IOException {
        writeFile(projectDir.resolve("settings.gradle"), "rootProject.name = 'sample-lib'\n");
        writeFile(projectDir.resolve("build.gradle"), """
                plugins {
                    id 'java'
                    id 'io.github.duckasteroid.agent-docs.publish'
                }

                agentDocs {
                    docsDirectory = layout.projectDirectory.dir('docs/agent-docs')
                }
                """);
        writeFile(projectDir.resolve("docs/agent-docs/agents.md"), "# Agent Docs\n");
        writeFile(projectDir.resolve("docs/agent-docs/custom/custom.md"), "custom\n");

        BuildResult result = GradleRunner.create()
                .withProjectDir(projectDir.toFile())
                .withPluginClasspath()
                .withArguments("packageAgentDocs")
                .build();

        assertNotNull(result.task(":packageAgentDocs"));
        assertEquals(TaskOutcome.SUCCESS, result.task(":packageAgentDocs").getOutcome());

        Path archivePath = projectDir.resolve("build/agent-docs/sample-lib-agent-docs.zip");
        assertTrue(Files.exists(archivePath), "Expected archive to be created");

        try (ZipFile zipFile = new ZipFile(archivePath.toFile())) {
            ZipEntry agentsFile = zipFile.getEntry("agents.md");
            ZipEntry customFile = zipFile.getEntry("custom/custom.md");
            assertNotNull(agentsFile, "Expected agents.md entrypoint in archive");
            assertNotNull(customFile, "Expected custom docs file in archive");
        }
    }

    @Test
    void publishToMavenLocalIncludesAgentDocsClassifiedArtifactWhenMavenPublishIsApplied() throws IOException {
        Path localRepo = projectDir.resolve("local-m2");
        writeFile(projectDir.resolve("settings.gradle"), "rootProject.name = 'sample-lib'\n");
        writeFile(projectDir.resolve("build.gradle"), """
                plugins {
                    id 'java-library'
                    id 'maven-publish'
                    id 'io.github.duckasteroid.agent-docs.publish'
                }

                group = 'io.github.example'
                version = '1.2.3'

                publishing {
                    publications {
                        mavenJava(org.gradle.api.publish.maven.MavenPublication) {
                            from components.java
                        }
                    }
                }
                """);
        writeFile(projectDir.resolve("src/main/java/io/github/example/Sample.java"), """
                package io.github.example;

                public class Sample {
                    public String hello() {
                        return "hello";
                    }
                }
                """);
        writeFile(projectDir.resolve("src/agentDocs/AGENTS.md"), "# Agent Docs\n");
        writeFile(projectDir.resolve("src/agentDocs/topics/overview.md"), "# Overview\n");

        BuildResult result = GradleRunner.create()
                .withProjectDir(projectDir.toFile())
                .withPluginClasspath()
                .withArguments("publishToMavenLocal", "-Dmaven.repo.local=" + localRepo)
                .build();

        assertNotNull(result.task(":publishToMavenLocal"));
        assertEquals(TaskOutcome.SUCCESS, result.task(":publishToMavenLocal").getOutcome());

        Path artifactBasePath = localRepo.resolve("io/github/example/sample-lib/1.2.3");
        assertTrue(Files.exists(artifactBasePath.resolve("sample-lib-1.2.3.jar")), "Expected main jar in Maven local repo");
        assertTrue(Files.exists(artifactBasePath.resolve("sample-lib-1.2.3-agent-docs.zip")),
                "Expected agent-docs classified zip in Maven local repo");
    }

    private static void writeFile(Path filePath, String content) throws IOException {
        Files.createDirectories(filePath.getParent());
        Files.writeString(filePath, content);
    }
}

