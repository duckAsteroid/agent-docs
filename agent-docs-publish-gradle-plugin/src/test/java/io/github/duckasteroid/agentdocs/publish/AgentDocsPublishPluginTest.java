package io.github.duckasteroid.agentdocs.publish;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.charset.StandardCharsets;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import org.gradle.testkit.runner.BuildResult;
import org.gradle.testkit.runner.GradleRunner;
import org.gradle.testkit.runner.TaskOutcome;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class AgentDocsPublishPluginTest {
    private static final String DEFAULT_TEST_GRADLE_VERSION = "9.5.1";

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
        writeFile(projectDir.resolve("src/agent-docs/sKiLl.Md"), skillFrontmatter("agent-docs", "Build and publish agent docs sidecars."));
        writeFile(projectDir.resolve("src/agent-docs/topics/overview.md"), "# Overview\n");

        BuildResult result = gradleRunner(projectDir)
                .withArguments("assemble")
                .build();

        assertNotNull(result.task(":packageAgentDocs"));
        assertEquals(TaskOutcome.SUCCESS, result.task(":packageAgentDocs").getOutcome());

        Path archivePath = projectDir.resolve("build/agent-docs/sample-lib-agent-docs.zip");
        assertTrue(Files.exists(archivePath), "Expected archive to be created");

        try (ZipFile zipFile = new ZipFile(archivePath.toFile())) {
            ZipEntry skillFile = zipFile.getEntry("SKILL.md");
            ZipEntry overviewFile = zipFile.getEntry("topics/overview.md");
            assertNotNull(skillFile, "Expected SKILL.md entrypoint in archive");
            assertNotNull(overviewFile, "Expected overview file in archive");
            String skillContent = readZipEntry(zipFile, skillFile);
            assertTrue(skillContent.contains("description:"), "Expected description frontmatter to remain");
            assertTrue(!skillContent.contains("\nname:"), "Expected frontmatter name to be omitted from sidecar SKILL.md");
        }
        assertTrue(result.getOutput().contains("frontmatter 'name' is ignored"));
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

        BuildResult result = gradleRunner(projectDir)
                .withArguments("packageAgentDocs")
                .buildAndFail();

        assertTrue(result.getOutput().contains(":validateAgentDocs"));
    }

    @Test
    void packageAgentDocsFailsWhenSkillEntrypointIsMissing() throws IOException {
        writeFile(projectDir.resolve("settings.gradle"), "rootProject.name = 'sample-lib'\n");
        writeFile(projectDir.resolve("build.gradle"), """
                plugins {
                    id 'java'
                    id 'io.github.duckasteroid.agent-docs.publish'
                }
                """);
        writeFile(projectDir.resolve("src/agent-docs/topics/overview.md"), "# Overview\n");

        BuildResult result = gradleRunner(projectDir)
                .withArguments("packageAgentDocs")
                .buildAndFail();

        assertTrue(result.getOutput().contains("must contain SKILL.md (case-insensitive)"));
    }

    @Test
    void packageAgentDocsFailsWhenMultipleSkillEntrypointsExistWithDifferentCase() throws IOException {
        writeFile(projectDir.resolve("settings.gradle"), "rootProject.name = 'sample-lib'\n");
        writeFile(projectDir.resolve("build.gradle"), """
                plugins {
                    id 'java'
                    id 'io.github.duckasteroid.agent-docs.publish'
                }
                """);
        writeFile(projectDir.resolve("src/agent-docs/SKILL.md"), skillFrontmatter("agent-docs", "Primary skill."));
        writeFile(projectDir.resolve("src/agent-docs/skill.md"), skillFrontmatter("agent-docs", "Duplicate skill."));

        BuildResult result = gradleRunner(projectDir)
                .withArguments("packageAgentDocs")
                .buildAndFail();

        assertTrue(result.getOutput().contains("must contain exactly one SKILL.md file"));
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
        writeFile(projectDir.resolve("docs/agent-docs/SKILL.md"), skillFrontmatter("agent-docs", "Publish docs with a custom docs directory."));
        writeFile(projectDir.resolve("docs/agent-docs/custom/custom.md"), "custom\n");

        BuildResult result = gradleRunner(projectDir)
                .withArguments("packageAgentDocs")
                .build();

        assertNotNull(result.task(":packageAgentDocs"));
        assertEquals(TaskOutcome.SUCCESS, result.task(":packageAgentDocs").getOutcome());

        Path archivePath = projectDir.resolve("build/agent-docs/sample-lib-agent-docs.zip");
        assertTrue(Files.exists(archivePath), "Expected archive to be created");

        try (ZipFile zipFile = new ZipFile(archivePath.toFile())) {
            ZipEntry skillFile = zipFile.getEntry("SKILL.md");
            ZipEntry customFile = zipFile.getEntry("custom/custom.md");
            assertNotNull(skillFile, "Expected SKILL.md entrypoint in archive");
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
        writeFile(projectDir.resolve("src/agent-docs/SKILL.md"), skillFrontmatter("agent-docs", "Publish docs alongside a Maven publication."));
        writeFile(projectDir.resolve("src/agent-docs/topics/overview.md"), "# Overview\n");

        BuildResult result = gradleRunner(projectDir)
                .withArguments("publishToMavenLocal", "-Dmaven.repo.local=" + localRepo)
                .build();

        assertNotNull(result.task(":publishToMavenLocal"));
        assertEquals(TaskOutcome.SUCCESS, result.task(":publishToMavenLocal").getOutcome());

        Path artifactBasePath = localRepo.resolve("io/github/example/sample-lib/1.2.3");
        assertTrue(Files.exists(artifactBasePath.resolve("sample-lib-1.2.3.jar")), "Expected main jar in Maven local repo");
        assertTrue(Files.exists(artifactBasePath.resolve("sample-lib-1.2.3-agent-docs.zip")),
                "Expected agent-docs classified zip in Maven local repo");
    }

    @Test
    void packageAgentDocsFailsWhenSkillFrontmatterIsMissing() throws IOException {
        writeFile(projectDir.resolve("settings.gradle"), "rootProject.name = 'sample-lib'\n");
        writeFile(projectDir.resolve("build.gradle"), """
                plugins {
                    id 'java'
                    id 'io.github.duckasteroid.agent-docs.publish'
                }
                """);
        writeFile(projectDir.resolve("src/agent-docs/SKILL.md"), "# Missing frontmatter\n");

        BuildResult result = gradleRunner(projectDir)
                .withArguments("packageAgentDocs")
                .buildAndFail();

        assertTrue(result.getOutput().contains("must start with YAML frontmatter"));
    }

    @Test
    void packageAgentDocsAllowsAnyPublisherNameAndOmitsItFromSidecar() throws IOException {
        writeFile(projectDir.resolve("settings.gradle"), "rootProject.name = 'sample-lib'\n");
        writeFile(projectDir.resolve("build.gradle"), """
                plugins {
                    id 'java'
                    id 'io.github.duckasteroid.agent-docs.publish'
                }
                """);
        writeFile(projectDir.resolve("src/agent-docs/SKILL.md"), skillFrontmatter("wrong-skill", "Name mismatch should fail validation."));

        BuildResult result = gradleRunner(projectDir)
                .withArguments("packageAgentDocs")
                .build();

        assertNotNull(result.task(":packageAgentDocs"));
        assertEquals(TaskOutcome.SUCCESS, result.task(":packageAgentDocs").getOutcome());
        assertTrue(result.getOutput().contains("frontmatter 'name' is ignored"));

        Path archivePath = projectDir.resolve("build/agent-docs/sample-lib-agent-docs.zip");
        try (ZipFile zipFile = new ZipFile(archivePath.toFile())) {
            ZipEntry skillFile = zipFile.getEntry("SKILL.md");
            assertNotNull(skillFile, "Expected SKILL.md entrypoint in archive");
            String skillContent = readZipEntry(zipFile, skillFile);
            assertTrue(!skillContent.contains("\nname:"), "Expected frontmatter name to be omitted from sidecar SKILL.md");
            assertTrue(skillContent.contains("description:"), "Expected description frontmatter to remain");
        }
    }

    @Test
    void installAgentDocsPublishSkillWritesSkillFileToDefaultLocation() throws IOException {
        writeFile(projectDir.resolve("settings.gradle"), "rootProject.name = 'sample-lib'\n");
        writeFile(projectDir.resolve("build.gradle"), """
                plugins {
                    id 'java'
                    id 'io.github.duckasteroid.agent-docs.publish'
                }
                """);

        BuildResult result = gradleRunner(projectDir)
                .withArguments("installAgentDocsPublishSkill")
                .build();

        assertEquals(TaskOutcome.SUCCESS, result.task(":installAgentDocsPublishSkill").getOutcome());

        Path skillFile = projectDir.resolve(".agents/skills/agent-docs-publish/SKILL.md");
        assertTrue(Files.exists(skillFile), "Expected SKILL.md to be written at default location");
        String content = Files.readString(skillFile);
        assertTrue(content.contains("name: agent-docs-publish"), "Expected skill name in frontmatter");
        assertTrue(content.contains("io.github.duckasteroid.agent-docs.publish"), "Expected plugin ID in content");
    }

    @Test
    void installAgentDocsPublishSkillIsUpToDateOnSecondRun() throws IOException {
        writeFile(projectDir.resolve("settings.gradle"), "rootProject.name = 'sample-lib'\n");
        writeFile(projectDir.resolve("build.gradle"), """
                plugins {
                    id 'java'
                    id 'io.github.duckasteroid.agent-docs.publish'
                }
                """);

        gradleRunner(projectDir).withArguments("installAgentDocsPublishSkill").build();
        BuildResult second = gradleRunner(projectDir).withArguments("installAgentDocsPublishSkill").build();

        assertEquals(TaskOutcome.UP_TO_DATE, second.task(":installAgentDocsPublishSkill").getOutcome());
    }

    @Test
    void installAgentDocsPublishSkillWritesToRootProjectInMultiProjectBuild() throws IOException {
        writeFile(projectDir.resolve("settings.gradle"), """
                rootProject.name = 'multi-root'
                include 'lib'
                """);
        writeFile(projectDir.resolve("build.gradle"), "// root\n");
        writeFile(projectDir.resolve("lib/build.gradle"), """
                plugins {
                    id 'java'
                    id 'io.github.duckasteroid.agent-docs.publish'
                }
                """);

        BuildResult result = gradleRunner(projectDir)
                .withArguments(":lib:installAgentDocsPublishSkill")
                .build();

        assertEquals(TaskOutcome.SUCCESS, result.task(":lib:installAgentDocsPublishSkill").getOutcome());
        assertTrue(Files.exists(projectDir.resolve(".agents/skills/agent-docs-publish/SKILL.md")),
                "Expected SKILL.md written in root project, not subproject");
        assertTrue(Files.notExists(projectDir.resolve("lib/.agents/skills/agent-docs-publish/SKILL.md")),
                "Expected SKILL.md not written in subproject directory");
    }

    @Test
    void packageAgentDocsFailsWhenStandardDirectoryIsAFile() throws IOException {
        writeFile(projectDir.resolve("settings.gradle"), "rootProject.name = 'sample-lib'\n");
        writeFile(projectDir.resolve("build.gradle"), """
                plugins {
                    id 'java'
                    id 'io.github.duckasteroid.agent-docs.publish'
                }
                """);
        writeFile(projectDir.resolve("src/agent-docs/SKILL.md"), skillFrontmatter("agent-docs", "Validate standard folders."));
        writeFile(projectDir.resolve("src/agent-docs/references"), "not-a-directory\n");

        BuildResult result = gradleRunner(projectDir)
                .withArguments("packageAgentDocs")
                .buildAndFail();

        assertTrue(result.getOutput().contains("standard directory 'references' must be a directory"));
    }

    private static void writeFile(Path filePath, String content) throws IOException {
        Files.createDirectories(filePath.getParent());
        Files.writeString(filePath, content);
    }

    private static GradleRunner gradleRunner(Path projectDir) {
        return GradleRunner.create()
                .withGradleVersion(testGradleVersion())
                .withProjectDir(projectDir.toFile())
                .withPluginClasspath();
    }

    private static String testGradleVersion() {
        return System.getProperty("agentDocs.test.gradleVersion", DEFAULT_TEST_GRADLE_VERSION);
    }

    private static String skillFrontmatter(String name, String description) {
        return """
                ---
                name: %s
                description: %s
                ---

                # %s

                ## Usage
                - Follow the documented workflow.
                """.formatted(name, description, name);
    }

    private static String readZipEntry(ZipFile zipFile, ZipEntry entry) throws IOException {
        try (var inputStream = zipFile.getInputStream(entry)) {
            return new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
