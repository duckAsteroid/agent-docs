package io.github.duckasteroid.agentdocs.publish;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.stream.Stream;
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

                agentDocsPublish {
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
    void deprecatedAgentDocsAliasStillConfiguresPublishExtensionWhenResolvePluginIsNotApplied() throws IOException {
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
        writeFile(projectDir.resolve("docs/agent-docs/SKILL.md"),
                skillFrontmatter("agent-docs", "Configured via the deprecated agentDocs alias."));

        BuildResult result = gradleRunner(projectDir)
                .withArguments("packageAgentDocs")
                .build();

        assertNotNull(result.task(":packageAgentDocs"));
        assertEquals(TaskOutcome.SUCCESS, result.task(":packageAgentDocs").getOutcome());
        assertTrue(result.getOutput().contains("'agentDocs {}' extension block"),
                "Expected a deprecation warning for the legacy agentDocs {} block");
        assertTrue(result.getOutput().contains("agentDocsPublish {}"),
                "Expected the deprecation warning to point consumers at agentDocsPublish {}");

        Path archivePath = projectDir.resolve("build/agent-docs/sample-lib-agent-docs.zip");
        assertTrue(Files.exists(archivePath), "Expected archive built from the docs directory configured via the alias");
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
    void agentDocsSidecarIsAtSameGavAsJarWhenArchivesNameDiffersFromProjectName() throws IOException {
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
                base { archivesName = 'custom-lib' }

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
        writeFile(projectDir.resolve("src/agent-docs/SKILL.md"), skillFrontmatter("agent-docs", "Publish docs when archivesName differs from project name."));
        writeFile(projectDir.resolve("src/agent-docs/topics/overview.md"), "# Overview\n");

        BuildResult result = gradleRunner(projectDir)
                .withArguments("publishToMavenLocal", "-Dmaven.repo.local=" + localRepo)
                .build();

        assertNotNull(result.task(":publishToMavenLocal"));
        assertEquals(TaskOutcome.SUCCESS, result.task(":publishToMavenLocal").getOutcome());

        // Find the published jar — its directory IS the artifact's GAV path in the repo
        Path groupPath = localRepo.resolve("io/github/example");
        Optional<Path> jarPath;
        try (Stream<Path> walker = Files.walk(groupPath, 3)) {
            jarPath = walker
                    .filter(p -> {
                        String name = p.getFileName().toString();
                        return name.endsWith(".jar")
                                && !name.contains("-sources")
                                && !name.contains("-javadoc");
                    })
                    .findFirst();
        }
        assertTrue(jarPath.isPresent(), "Expected a jar to be published under " + groupPath);

        Path artifactDir = jarPath.get().getParent();
        boolean sidecarPresent;
        try (Stream<Path> listing = Files.list(artifactDir)) {
            sidecarPresent = listing.anyMatch(p -> p.getFileName().toString().endsWith("-agent-docs.zip"));
        }
        assertTrue(sidecarPresent,
                "Expected agent-docs sidecar zip in the same GAV directory as the jar (" + artifactDir + ")");
    }

    @Test
    void agentDocsSidecarIsAtSameGavAsJarWhenPublicationArtifactIdIsSetExplicitly() throws IOException {
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
                            artifactId = 'my-custom-lib'
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
        writeFile(projectDir.resolve("src/agent-docs/SKILL.md"), skillFrontmatter("agent-docs", "Publish docs when publication artifactId is set explicitly."));
        writeFile(projectDir.resolve("src/agent-docs/topics/overview.md"), "# Overview\n");

        BuildResult result = gradleRunner(projectDir)
                .withArguments("publishToMavenLocal", "-Dmaven.repo.local=" + localRepo)
                .build();

        assertNotNull(result.task(":publishToMavenLocal"));
        assertEquals(TaskOutcome.SUCCESS, result.task(":publishToMavenLocal").getOutcome());

        Path artifactBasePath = localRepo.resolve("io/github/example/my-custom-lib/1.2.3");
        assertTrue(Files.exists(artifactBasePath.resolve("my-custom-lib-1.2.3.jar")),
                "Expected jar at explicit artifactId GAV coordinates");
        assertTrue(Files.exists(artifactBasePath.resolve("my-custom-lib-1.2.3-agent-docs.zip")),
                "Expected agent-docs sidecar at the same explicit artifactId GAV coordinates");
    }

    @Test
    void packageAgentDocsSucceedsWhenSkillFrontmatterIsEntirelyAbsent() throws IOException {
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
                .build();

        assertTrue(result.task(":packageAgentDocs").getOutcome() == TaskOutcome.SUCCESS);
    }

    @Test
    void packageAgentDocsFailsWhenSkillFrontmatterIsOpenedButNeverClosed() throws IOException {
        writeFile(projectDir.resolve("settings.gradle"), "rootProject.name = 'sample-lib'\n");
        writeFile(projectDir.resolve("build.gradle"), """
                plugins {
                    id 'java'
                    id 'io.github.duckasteroid.agent-docs.publish'
                }
                """);
        writeFile(projectDir.resolve("src/agent-docs/SKILL.md"), """
                ---
                description: Unclosed frontmatter.

                # Skill
                """);

        BuildResult result = gradleRunner(projectDir)
                .withArguments("packageAgentDocs")
                .buildAndFail();

        assertTrue(result.getOutput().contains("must end with a closing --- delimiter"));
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

    @Test
    void jarManifestDeclaresMavenSidecarByDefault() throws IOException {
        writeFile(projectDir.resolve("settings.gradle"), "rootProject.name = 'sample-lib'\n");
        writeFile(projectDir.resolve("build.gradle"), """
                plugins {
                    id 'java'
                    id 'io.github.duckasteroid.agent-docs.publish'
                }
                group = 'com.example'
                version = '1.2.3'
                """);
        writeFile(projectDir.resolve("src/agent-docs/SKILL.md"), skillFrontmatter("agent-docs", "Sidecar distribution."));

        BuildResult result = gradleRunner(projectDir)
                .withArguments("jar")
                .build();

        assertEquals(TaskOutcome.SUCCESS, result.task(":jar").getOutcome());

        Path jarPath = projectDir.resolve("build/libs/sample-lib-1.2.3.jar");
        assertTrue(Files.exists(jarPath), "Expected main jar to be built");
        try (JarFile jarFile = new JarFile(jarPath.toFile())) {
            String agentDocs = jarFile.getManifest().getMainAttributes().getValue("Agent-Docs");
            assertEquals("maven:com.example:sample-lib:1.2.3", agentDocs);
        }
    }

    @Test
    void embeddedDistributionStampsClasspathManifestAndSkipsSidecar() throws IOException {
        writeFile(projectDir.resolve("settings.gradle"), "rootProject.name = 'sample-lib'\n");
        writeFile(projectDir.resolve("build.gradle"), """
                plugins {
                    id 'java'
                    id 'io.github.duckasteroid.agent-docs.publish'
                }
                group = 'com.example'
                version = '1.2.3'

                agentDocsPublish {
                  distribution = 'EMBEDDED'
                }
                """);
        writeFile(projectDir.resolve("src/agent-docs/SKILL.md"), skillFrontmatter("agent-docs", "Embedded distribution."));
        writeFile(projectDir.resolve("src/agent-docs/references/overview.md"), "# Overview\n");

        BuildResult result = gradleRunner(projectDir)
                .withArguments("jar", "packageAgentDocs")
                .build();

        assertEquals(TaskOutcome.SUCCESS, result.task(":jar").getOutcome());
        assertEquals(TaskOutcome.SKIPPED, result.task(":packageAgentDocs").getOutcome());

        Path jarPath = projectDir.resolve("build/libs/sample-lib-1.2.3.jar");
        assertTrue(Files.exists(jarPath), "Expected main jar to be built");
        try (JarFile jarFile = new JarFile(jarPath.toFile())) {
            assertEquals("classpath", jarFile.getManifest().getMainAttributes().getValue("Agent-Docs"));
            assertNotNull(jarFile.getEntry("agent-docs/SKILL.md"), "Expected embedded SKILL.md inside jar");
            assertNotNull(jarFile.getEntry("agent-docs/references/overview.md"), "Expected embedded reference doc inside jar");
        }

        assertTrue(Files.notExists(projectDir.resolve("build/agent-docs/sample-lib-agent-docs.zip")),
                "Expected no sidecar zip in embedded mode");
    }

    @Test
    void javaGradlePluginProjectDefaultsToEmbeddedDistribution() throws IOException {
        writeFile(projectDir.resolve("settings.gradle"), "rootProject.name = 'sample-plugin'\n");
        writeFile(projectDir.resolve("build.gradle"), """
                plugins {
                    id 'java-gradle-plugin'
                    id 'io.github.duckasteroid.agent-docs.publish'
                }
                group = 'com.example'
                version = '1.2.3'

                gradlePlugin {
                    plugins {
                        sample {
                            id = 'com.example.sample'
                            implementationClass = 'com.example.SamplePlugin'
                        }
                    }
                }
                """);
        writeFile(projectDir.resolve("src/main/java/com/example/SamplePlugin.java"), """
                package com.example;

                import org.gradle.api.Plugin;
                import org.gradle.api.Project;

                public class SamplePlugin implements Plugin<Project> {
                    public void apply(Project project) {}
                }
                """);
        writeFile(projectDir.resolve("src/agent-docs/com.example.sample/SKILL.md"),
                skillFrontmatter("agent-docs", "Sample plugin docs."));

        BuildResult result = gradleRunner(projectDir)
                .withArguments("jar", "packageAgentDocs")
                .build();

        assertEquals(TaskOutcome.SUCCESS, result.task(":jar").getOutcome());
        assertEquals(TaskOutcome.SKIPPED, result.task(":packageAgentDocs").getOutcome());

        Path jarPath = projectDir.resolve("build/libs/sample-plugin-1.2.3.jar");
        assertTrue(Files.exists(jarPath), "Expected main jar to be built");
        try (JarFile jarFile = new JarFile(jarPath.toFile())) {
            assertEquals("classpath", jarFile.getManifest().getMainAttributes().getValue("Agent-Docs"));
            assertNotNull(jarFile.getEntry("agent-docs/com.example.sample/SKILL.md"),
                    "Expected embedded SKILL.md inside jar under its plugin id subdirectory");
        }
    }

    @Test
    void javaGradlePluginProjectEmbedsOneBundlePerDeclaredPluginId() throws IOException {
        writeFile(projectDir.resolve("settings.gradle"), "rootProject.name = 'multi-plugin'\n");
        writeFile(projectDir.resolve("build.gradle"), """
                plugins {
                    id 'java-gradle-plugin'
                    id 'io.github.duckasteroid.agent-docs.publish'
                }
                group = 'com.example'
                version = '1.0.0'

                gradlePlugin {
                    plugins {
                        first {
                            id = 'com.example.first'
                            implementationClass = 'com.example.FirstPlugin'
                        }
                        second {
                            id = 'com.example.second'
                            implementationClass = 'com.example.SecondPlugin'
                        }
                    }
                }
                """);
        writeFile(projectDir.resolve("src/main/java/com/example/FirstPlugin.java"), """
                package com.example;

                import org.gradle.api.Plugin;
                import org.gradle.api.Project;

                public class FirstPlugin implements Plugin<Project> {
                    public void apply(Project project) {}
                }
                """);
        writeFile(projectDir.resolve("src/main/java/com/example/SecondPlugin.java"), """
                package com.example;

                import org.gradle.api.Plugin;
                import org.gradle.api.Project;

                public class SecondPlugin implements Plugin<Project> {
                    public void apply(Project project) {}
                }
                """);
        writeFile(projectDir.resolve("src/agent-docs/com.example.first/SKILL.md"),
                skillFrontmatter("ignored-name", "First plugin docs."));
        writeFile(projectDir.resolve("src/agent-docs/com.example.second/SKILL.md"),
                skillFrontmatter("ignored-name", "Second plugin docs."));

        BuildResult result = gradleRunner(projectDir).withArguments("jar").build();

        assertEquals(TaskOutcome.SUCCESS, result.task(":jar").getOutcome());

        Path jarPath = projectDir.resolve("build/libs/multi-plugin-1.0.0.jar");
        try (JarFile jarFile = new JarFile(jarPath.toFile())) {
            assertEquals("classpath", jarFile.getManifest().getMainAttributes().getValue("Agent-Docs"));
            JarEntry firstEntry = jarFile.getJarEntry("agent-docs/com.example.first/SKILL.md");
            JarEntry secondEntry = jarFile.getJarEntry("agent-docs/com.example.second/SKILL.md");
            assertNotNull(firstEntry, "Expected embedded SKILL.md for first declared plugin id");
            assertNotNull(secondEntry, "Expected embedded SKILL.md for second declared plugin id");

            String firstContent = readZipEntry(jarFile, firstEntry);
            assertTrue(!firstContent.contains("\nname:"), "Expected frontmatter name stripped from first plugin's SKILL.md");
            assertTrue(firstContent.contains("First plugin docs."));

            String secondContent = readZipEntry(jarFile, secondEntry);
            assertTrue(!secondContent.contains("\nname:"), "Expected frontmatter name stripped from second plugin's SKILL.md");
            assertTrue(secondContent.contains("Second plugin docs."));
        }
    }

    @Test
    void javaGradlePluginProjectFailsValidationWhenAPluginIdBundleIsMissing() throws IOException {
        writeFile(projectDir.resolve("settings.gradle"), "rootProject.name = 'multi-plugin'\n");
        writeFile(projectDir.resolve("build.gradle"), """
                plugins {
                    id 'java-gradle-plugin'
                    id 'io.github.duckasteroid.agent-docs.publish'
                }
                group = 'com.example'
                version = '1.0.0'

                gradlePlugin {
                    plugins {
                        first {
                            id = 'com.example.first'
                            implementationClass = 'com.example.FirstPlugin'
                        }
                        second {
                            id = 'com.example.second'
                            implementationClass = 'com.example.SecondPlugin'
                        }
                    }
                }
                """);
        writeFile(projectDir.resolve("src/main/java/com/example/FirstPlugin.java"), """
                package com.example;

                import org.gradle.api.Plugin;
                import org.gradle.api.Project;

                public class FirstPlugin implements Plugin<Project> {
                    public void apply(Project project) {}
                }
                """);
        writeFile(projectDir.resolve("src/main/java/com/example/SecondPlugin.java"), """
                package com.example;

                import org.gradle.api.Plugin;
                import org.gradle.api.Project;

                public class SecondPlugin implements Plugin<Project> {
                    public void apply(Project project) {}
                }
                """);
        writeFile(projectDir.resolve("src/agent-docs/com.example.first/SKILL.md"),
                skillFrontmatter("ignored-name", "First plugin docs."));

        BuildResult result = gradleRunner(projectDir).withArguments("validateAgentDocs").buildAndFail();

        assertTrue(result.getOutput().contains("[plugin-bundle-directories]"));
        assertTrue(result.getOutput().contains("missing a bundle subdirectory for declared plugin id 'com.example.second'"));
    }

    @Test
    void javaGradlePluginProjectFailsValidationOnStrayBundleDirectory() throws IOException {
        writeFile(projectDir.resolve("settings.gradle"), "rootProject.name = 'sample-plugin'\n");
        writeFile(projectDir.resolve("build.gradle"), """
                plugins {
                    id 'java-gradle-plugin'
                    id 'io.github.duckasteroid.agent-docs.publish'
                }
                group = 'com.example'
                version = '1.0.0'

                gradlePlugin {
                    plugins {
                        sample {
                            id = 'com.example.sample'
                            implementationClass = 'com.example.SamplePlugin'
                        }
                    }
                }
                """);
        writeFile(projectDir.resolve("src/main/java/com/example/SamplePlugin.java"), """
                package com.example;

                import org.gradle.api.Plugin;
                import org.gradle.api.Project;

                public class SamplePlugin implements Plugin<Project> {
                    public void apply(Project project) {}
                }
                """);
        writeFile(projectDir.resolve("src/agent-docs/com.example.sample/SKILL.md"),
                skillFrontmatter("ignored-name", "Sample plugin docs."));
        writeFile(projectDir.resolve("src/agent-docs/com.example.typo/SKILL.md"),
                skillFrontmatter("ignored-name", "Stray docs that don't match a declared id."));

        BuildResult result = gradleRunner(projectDir).withArguments("validateAgentDocs").buildAndFail();

        assertTrue(result.getOutput().contains("[plugin-bundle-directories]"));
        assertTrue(result.getOutput().contains("'com.example.typo' that doesn't match any plugin id"));
    }

    @Test
    void javaGradlePluginProjectFailsValidationOnTopLevelSkillMd() throws IOException {
        writeFile(projectDir.resolve("settings.gradle"), "rootProject.name = 'sample-plugin'\n");
        writeFile(projectDir.resolve("build.gradle"), """
                plugins {
                    id 'java-gradle-plugin'
                    id 'io.github.duckasteroid.agent-docs.publish'
                }
                group = 'com.example'
                version = '1.0.0'

                gradlePlugin {
                    plugins {
                        sample {
                            id = 'com.example.sample'
                            implementationClass = 'com.example.SamplePlugin'
                        }
                    }
                }
                """);
        writeFile(projectDir.resolve("src/main/java/com/example/SamplePlugin.java"), """
                package com.example;

                import org.gradle.api.Plugin;
                import org.gradle.api.Project;

                public class SamplePlugin implements Plugin<Project> {
                    public void apply(Project project) {}
                }
                """);
        writeFile(projectDir.resolve("src/agent-docs/com.example.sample/SKILL.md"),
                skillFrontmatter("ignored-name", "Sample plugin docs."));
        writeFile(projectDir.resolve("src/agent-docs/SKILL.md"),
                skillFrontmatter("ignored-name", "Stray top-level entrypoint."));

        BuildResult result = gradleRunner(projectDir).withArguments("validateAgentDocs").buildAndFail();

        assertTrue(result.getOutput().contains("[plugin-bundle-directories]"));
        assertTrue(result.getOutput().contains("must not contain a top-level SKILL.md"));
    }

    @Test
    void javaGradlePluginProjectFailsFastOnExplicitSidecarOverride() throws IOException {
        writeFile(projectDir.resolve("settings.gradle"), "rootProject.name = 'sample-plugin'\n");
        writeFile(projectDir.resolve("build.gradle"), """
                plugins {
                    id 'java-gradle-plugin'
                    id 'io.github.duckasteroid.agent-docs.publish'
                }
                group = 'com.example'
                version = '1.2.3'

                agentDocsPublish {
                    distribution = 'SIDECAR'
                }

                gradlePlugin {
                    plugins {
                        sample {
                            id = 'com.example.sample'
                            implementationClass = 'com.example.SamplePlugin'
                        }
                    }
                }
                """);
        writeFile(projectDir.resolve("src/main/java/com/example/SamplePlugin.java"), """
                package com.example;

                import org.gradle.api.Plugin;
                import org.gradle.api.Project;

                public class SamplePlugin implements Plugin<Project> {
                    public void apply(Project project) {}
                }
                """);
        writeFile(projectDir.resolve("src/agent-docs/SKILL.md"), skillFrontmatter("agent-docs", "Sample plugin docs."));

        BuildResult result = gradleRunner(projectDir)
                .withArguments("jar", "packageAgentDocs")
                .buildAndFail();

        assertTrue(result.getOutput().contains("agentDocsPublish.distribution = SIDECAR is not supported"),
                "Expected build to fail fast on SIDECAR override in a java-gradle-plugin project");
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
