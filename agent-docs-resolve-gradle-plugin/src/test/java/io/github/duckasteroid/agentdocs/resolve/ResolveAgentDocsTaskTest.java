package io.github.duckasteroid.agentdocs.resolve;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.gradle.testkit.runner.BuildResult;
import org.gradle.testkit.runner.GradleRunner;
import org.gradle.testkit.runner.TaskOutcome;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ResolveAgentDocsTaskTest {

    @TempDir
    Path projectDir;

    @Test
    void resolveAgentDocsCachesSidecarsForDirectImplementationAndApiDependencies() throws IOException {
        Path upstreamRepo = projectDir.resolve("upstream-repo");
        Path localSidecarRepo = projectDir.resolve("agent-docs-repo");

        writeMavenModule(upstreamRepo, "com.example", "dep-impl", "1.0.0", true);
        writeMavenModule(upstreamRepo, "com.example", "dep-api", "2.0.0", true);
        writeMavenModule(upstreamRepo, "com.example", "dep-no-sidecar", "3.0.0", false);

        writeFile(projectDir.resolve("settings.gradle"), "rootProject.name = 'consumer'\n");
        writeConsumerBuildFile("SINGLE_INDEX", null);
        writeFile(projectDir.resolve("src/main/java/example/App.java"), """
                package example;
                public class App {
                    public static String value() {
                        return "ok";
                    }
                }
                """);

        BuildResult result = runResolve(localSidecarRepo);

        assertNotNull(result.task(":resolveAgentDocs"));
        assertEquals(TaskOutcome.SUCCESS, result.task(":resolveAgentDocs").getOutcome());

        Path implSidecar = localSidecarRepo.resolve("com/example/dep-impl/1.0.0/dep-impl-1.0.0-agent-docs.zip");
        Path apiSidecar = localSidecarRepo.resolve("com/example/dep-api/2.0.0/dep-api-2.0.0-agent-docs.zip");
        Path missingSidecar = localSidecarRepo.resolve("com/example/dep-no-sidecar/3.0.0/dep-no-sidecar-3.0.0-agent-docs.zip");

        assertTrue(Files.exists(implSidecar), "Expected implementation dependency sidecar to be cached");
        assertTrue(Files.exists(apiSidecar), "Expected api dependency sidecar to be cached");
        assertTrue(Files.notExists(missingSidecar), "Expected missing sidecar dependency to be skipped");

        Path implEntrypoint = projectDir.resolve(".agents/resources/agent-docs/com/example/dep-impl/1.0.0/agents.md");
        Path apiEntrypoint = projectDir.resolve(".agents/resources/agent-docs/com/example/dep-api/2.0.0/agents.md");
        Path missingEntrypoint = projectDir.resolve(".agents/resources/agent-docs/com/example/dep-no-sidecar/3.0.0/agents.md");

        assertTrue(Files.exists(implEntrypoint), "Expected implementation dependency docs to be extracted");
        assertTrue(Files.exists(apiEntrypoint), "Expected api dependency docs to be extracted");
        assertTrue(Files.notExists(missingEntrypoint), "Expected missing sidecar docs to be skipped");

        Path skillFile = projectDir.resolve(".agents/skills/agent-docs.md");
        assertTrue(Files.exists(skillFile), "Expected generated agent-docs skill to be written");
        String skill = Files.readString(skillFile);
        assertTrue(skill.contains("com.example:dep-impl:1.0.0"));
        assertTrue(skill.contains("com.example:dep-api:2.0.0"));
        assertTrue(skill.contains("../resources/agent-docs/com/example/dep-impl/1.0.0/agents.md"));
        assertTrue(skill.contains("../resources/agent-docs/com/example/dep-api/2.0.0/agents.md"));
        assertFalse(skill.contains("com.example:dep-no-sidecar:3.0.0"));
    }

    @Test
    void resolveAgentDocsCleansUpWhenSwitchingSkillGenerationModes() throws IOException {
        Path upstreamRepo = projectDir.resolve("upstream-repo");
        Path localSidecarRepo = projectDir.resolve("agent-docs-repo");

        writeMavenModule(upstreamRepo, "com.example", "dep-impl", "1.0.0", true);
        writeMavenModule(upstreamRepo, "com.example", "dep-api", "2.0.0", true);

        writeFile(projectDir.resolve("settings.gradle"), "rootProject.name = 'consumer'\n");
        writeFile(projectDir.resolve("src/main/java/example/App.java"), """
                package example;
                public class App {
                    public static String value() {
                        return "ok";
                    }
                }
                """);

        writeConsumerBuildFile("SINGLE_INDEX", null);
        runResolve(localSidecarRepo);

        Path singleSkillFile = projectDir.resolve(".agents/skills/agent-docs.md");
        Path perDependencySkillRoot = projectDir.resolve(".agents/skills/agent-docs-dependencies");
        assertTrue(Files.exists(singleSkillFile), "Expected single index skill to exist in SINGLE_INDEX mode");
        assertTrue(Files.notExists(perDependencySkillRoot), "Expected per-dependency skill root to be absent in SINGLE_INDEX mode");

        writeConsumerBuildFile("PER_DEPENDENCY", null);
        runResolve(localSidecarRepo);

        Path implPerDependencySkill = projectDir.resolve(".agents/skills/agent-docs-dependencies/com/example/dep-impl/1.0.0.md");
        Path apiPerDependencySkill = projectDir.resolve(".agents/skills/agent-docs-dependencies/com/example/dep-api/2.0.0.md");
        assertTrue(Files.notExists(singleSkillFile), "Expected single index skill to be removed in PER_DEPENDENCY mode");
        assertTrue(Files.exists(implPerDependencySkill), "Expected implementation per-dependency skill to be generated");
        assertTrue(Files.exists(apiPerDependencySkill), "Expected api per-dependency skill to be generated");

        writeConsumerBuildFile("SINGLE_INDEX", null);
        runResolve(localSidecarRepo);

        assertTrue(Files.exists(singleSkillFile), "Expected single index skill to be restored in SINGLE_INDEX mode");
        assertTrue(Files.notExists(perDependencySkillRoot), "Expected per-dependency skills to be cleaned up after switching back");
    }

    @Test
    void resolveAgentDocsUsesThresholdToPickSkillGenerationModel() throws IOException {
        Path upstreamRepo = projectDir.resolve("upstream-repo");
        Path localSidecarRepo = projectDir.resolve("agent-docs-repo");

        writeMavenModule(upstreamRepo, "com.example", "dep-impl", "1.0.0", true);
        writeMavenModule(upstreamRepo, "com.example", "dep-api", "2.0.0", true);

        writeFile(projectDir.resolve("settings.gradle"), "rootProject.name = 'consumer'\n");
        writeFile(projectDir.resolve("src/main/java/example/App.java"), """
                package example;
                public class App {
                    public static String value() {
                        return "ok";
                    }
                }
                """);

        Path singleSkillFile = projectDir.resolve(".agents/skills/agent-docs.md");
        Path perDependencySkillRoot = projectDir.resolve(".agents/skills/agent-docs-dependencies");
        Path implPerDependencySkill = projectDir.resolve(".agents/skills/agent-docs-dependencies/com/example/dep-impl/1.0.0.md");

        writeConsumerBuildFile("AUTO_THRESHOLD", 1);
        runResolve(localSidecarRepo);

        assertTrue(Files.exists(singleSkillFile), "Expected single index skill when resolved docs exceed threshold");
        assertTrue(Files.notExists(perDependencySkillRoot), "Expected per-dependency skills to be absent above threshold");

        writeConsumerBuildFile("AUTO_THRESHOLD", 2);
        runResolve(localSidecarRepo);

        assertTrue(Files.notExists(singleSkillFile), "Expected single index skill to be removed at-or-below threshold");
        assertTrue(Files.exists(implPerDependencySkill), "Expected per-dependency skills when resolved docs are at threshold");
    }

    private BuildResult runResolve(Path localSidecarRepo) {
        return GradleRunner.create()
                .withProjectDir(projectDir.toFile())
                .withPluginClasspath()
                .withArguments("resolveAgentDocs", "-DagentDocs.localRepository=" + localSidecarRepo)
                .build();
    }

    private void writeConsumerBuildFile(String skillGenerationMode, Integer perDependencySkillThreshold) throws IOException {
        String thresholdLine = perDependencySkillThreshold == null
                ? ""
                : "    perDependencySkillThreshold = " + perDependencySkillThreshold + "\n";
        writeFile(projectDir.resolve("build.gradle"), """
                plugins {
                    id 'java-library'
                    id 'io.github.duckasteroid.agent-docs'
                }

                repositories {
                    maven {
                        url = uri('upstream-repo')
                    }
                }

                dependencies {
                    implementation 'com.example:dep-impl:1.0.0'
                    implementation 'com.example:dep-no-sidecar:3.0.0'
                    api 'com.example:dep-api:2.0.0'
                }

                agentDocs {
                    skillGenerationMode = '%s'
                %s
                }
                """.formatted(skillGenerationMode, thresholdLine));
    }

    private static void writeMavenModule(Path repositoryRoot, String group, String artifact, String version, boolean withSidecar)
            throws IOException {
        Path baseDir = repositoryRoot
                .resolve(group.replace('.', '/'))
                .resolve(artifact)
                .resolve(version);

        Files.createDirectories(baseDir);
        writeFile(baseDir.resolve(artifact + "-" + version + ".pom"), """
                <project>
                  <modelVersion>4.0.0</modelVersion>
                  <groupId>%s</groupId>
                  <artifactId>%s</artifactId>
                  <version>%s</version>
                </project>
                """.formatted(group, artifact, version));
        writeFile(baseDir.resolve(artifact + "-" + version + ".jar"), "placeholder");
        if (withSidecar) {
            writeAgentDocsSidecar(baseDir.resolve(artifact + "-" + version + "-agent-docs.zip"), group, artifact, version);
        }
    }

    private static void writeAgentDocsSidecar(Path zipPath, String group, String artifact, String version) throws IOException {
        Files.createDirectories(zipPath.getParent());
        try (ZipOutputStream outputStream = new ZipOutputStream(Files.newOutputStream(zipPath))) {
            outputStream.putNextEntry(new ZipEntry("agents.md"));
            outputStream.write(("# " + group + ":" + artifact + ":" + version + "\n").getBytes());
            outputStream.closeEntry();
        }
    }

    private static void writeFile(Path filePath, String content) throws IOException {
        Files.createDirectories(filePath.getParent());
        Files.writeString(filePath, content);
    }
}

