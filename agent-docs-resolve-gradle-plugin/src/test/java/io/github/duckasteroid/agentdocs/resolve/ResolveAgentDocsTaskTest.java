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
    private static final String DEFAULT_DEPENDENCIES = """
                dependencies {
                    implementation 'com.example:dep-impl:1.0.0'
                    implementation 'com.example:dep-no-sidecar:3.0.0'
                    api 'com.example:dep-api:2.0.0'
                }
                """;

    @TempDir
    Path projectDir;

    @Test
    void resolveAgentDocsDownloadsSidecarsForDirectImplementationAndApiDependencies() throws IOException {
        Path upstreamRepo = projectDir.resolve("upstream-repo");

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

        BuildResult result = runResolve();

        assertNotNull(result.task(":resolveAgentDocs"));
        assertEquals(TaskOutcome.SUCCESS, result.task(":resolveAgentDocs").getOutcome());

        Path implSidecar = projectDir.resolve(".agent/skills/com-example-dep-impl-1-0-0/agent-docs.zip");
        Path apiSidecar = projectDir.resolve(".agent/skills/com-example-dep-api-2-0-0/agent-docs.zip");
        Path missingSidecar = projectDir.resolve(".agent/skills/com-example-dep-no-sidecar-3-0-0/agent-docs.zip");

        assertTrue(Files.exists(implSidecar), "Expected implementation dependency sidecar to be downloaded");
        assertTrue(Files.exists(apiSidecar), "Expected api dependency sidecar to be downloaded");
        assertTrue(Files.notExists(missingSidecar), "Expected missing sidecar dependency to be skipped");

        Path implRoot = projectDir.resolve(".agent/skills/com-example-dep-impl-1-0-0");
        Path apiRoot = projectDir.resolve(".agent/skills/com-example-dep-api-2-0-0");
        Path implEntrypoint = implRoot.resolve("SKILL.md");
        Path apiEntrypoint = apiRoot.resolve("SKILL.md");
        Path missingEntrypoint = projectDir.resolve(".agent/skills/com-example-dep-no-sidecar-3-0-0/SKILL.md");

        assertTrue(Files.exists(implEntrypoint), "Expected implementation dependency docs to be extracted");
        assertTrue(Files.exists(apiEntrypoint), "Expected api dependency docs to be extracted");
        assertTrue(Files.notExists(missingEntrypoint), "Expected missing sidecar docs to be skipped");
        assertTrue(Files.exists(implRoot.resolve(".agent-docs")), "Expected implementation skill folder ownership marker");
        assertTrue(Files.exists(apiRoot.resolve(".agent-docs")), "Expected api skill folder ownership marker");
        String implSkill = Files.readString(implEntrypoint);
        String apiSkill = Files.readString(apiEntrypoint);
        assertTrue(implSkill.contains("name: com-example-dep-impl-1-0-0"));
        assertTrue(apiSkill.contains("name: com-example-dep-api-2-0-0"));

        Path skillFile = projectDir.resolve(".agent/skills/SKILL.md");
        assertTrue(Files.exists(skillFile), "Expected generated agent-docs skill to be written");
        String skill = Files.readString(skillFile);
        assertTrue(skill.contains("com.example:dep-impl:1.0.0"));
        assertTrue(skill.contains("com.example:dep-api:2.0.0"));
        assertTrue(skill.contains("com-example-dep-impl-1-0-0/SKILL.md"));
        assertTrue(skill.contains("com-example-dep-api-2-0-0/SKILL.md"));
        assertFalse(skill.contains("com.example:dep-no-sidecar:3.0.0"));
    }

    @Test
    void resolveAgentDocsCleansUpWhenSwitchingSkillGenerationModes() throws IOException {
        Path upstreamRepo = projectDir.resolve("upstream-repo");

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
        runResolve();

        Path singleSkillFile = projectDir.resolve(".agent/skills/SKILL.md");
        Path perDependencySkillRoot = projectDir.resolve(".agent/skills/agent-docs-dependencies");
        assertTrue(Files.exists(singleSkillFile), "Expected single index skill to exist in SINGLE_INDEX mode");
        assertTrue(Files.notExists(perDependencySkillRoot), "Expected per-dependency skill root to be absent in SINGLE_INDEX mode");

        writeConsumerBuildFile("PER_DEPENDENCY", null);
        runResolve();

        Path implPerDependencySkill =
                projectDir.resolve(".agent/skills/agent-docs-dependencies/com-example-dep-impl-1-0-0/SKILL.md");
        Path apiPerDependencySkill =
                projectDir.resolve(".agent/skills/agent-docs-dependencies/com-example-dep-api-2-0-0/SKILL.md");
        assertTrue(Files.notExists(singleSkillFile), "Expected single index skill to be removed in PER_DEPENDENCY mode");
        assertTrue(Files.exists(implPerDependencySkill), "Expected implementation per-dependency skill to be generated");
        assertTrue(Files.exists(apiPerDependencySkill), "Expected api per-dependency skill to be generated");
        String implDependencySkill = Files.readString(implPerDependencySkill);
        assertTrue(implDependencySkill.contains("name: com-example-dep-impl-1-0-0"));
        assertTrue(
                Files.exists(implPerDependencySkill.getParent().resolve(".agent-docs")),
                "Expected implementation per-dependency skill marker");
        assertTrue(
                Files.exists(apiPerDependencySkill.getParent().resolve(".agent-docs")),
                "Expected api per-dependency skill marker");

        writeConsumerBuildFile("SINGLE_INDEX", null);
        runResolve();

        assertTrue(Files.exists(singleSkillFile), "Expected single index skill to be restored in SINGLE_INDEX mode");
        assertTrue(Files.notExists(perDependencySkillRoot), "Expected per-dependency skills to be cleaned up after switching back");
    }

    @Test
    void resolveAgentDocsUsesThresholdToPickSkillGenerationModel() throws IOException {
        Path upstreamRepo = projectDir.resolve("upstream-repo");

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

        Path singleSkillFile = projectDir.resolve(".agent/skills/SKILL.md");
        Path perDependencySkillRoot = projectDir.resolve(".agent/skills/agent-docs-dependencies");
        Path implPerDependencySkill =
                projectDir.resolve(".agent/skills/agent-docs-dependencies/com-example-dep-impl-1-0-0/SKILL.md");

        writeConsumerBuildFile("AUTO_THRESHOLD", 1);
        runResolve();

        assertTrue(Files.exists(singleSkillFile), "Expected single index skill when resolved docs exceed threshold");
        assertTrue(Files.notExists(perDependencySkillRoot), "Expected per-dependency skills to be absent above threshold");

        writeConsumerBuildFile("AUTO_THRESHOLD", 2);
        runResolve();

        assertTrue(Files.notExists(singleSkillFile), "Expected single index skill to be removed at-or-below threshold");
        assertTrue(Files.exists(implPerDependencySkill), "Expected per-dependency skills when resolved docs are at threshold");
    }

    @Test
    void resolveAgentDocsRemovesOnlyStaleManagedSkillFolders() throws IOException {
        Path upstreamRepo = projectDir.resolve("upstream-repo");

        writeMavenModule(upstreamRepo, "com.example", "dep-impl", "1.0.0", true);
        writeMavenModule(upstreamRepo, "com.example", "dep-api", "2.0.0", true);
        writeMavenModule(upstreamRepo, "com.example", "dep-no-sidecar", "3.0.0", false);

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
        runResolve();

        Path implSkillDir = projectDir.resolve(".agent/skills/com-example-dep-impl-1-0-0");
        Path apiSkillDir = projectDir.resolve(".agent/skills/com-example-dep-api-2-0-0");
        assertTrue(Files.exists(implSkillDir.resolve(".agent-docs")));
        assertTrue(Files.exists(apiSkillDir.resolve(".agent-docs")));

        Path manualSkillDir = projectDir.resolve(".agent/skills/custom-manual-skill");
        writeFile(manualSkillDir.resolve("SKILL.md"), "# custom\n");

        writeConsumerBuildFile(
                "SINGLE_INDEX",
                null,
                """
                dependencies {
                    implementation 'com.example:dep-impl:1.0.0'
                    implementation 'com.example:dep-no-sidecar:3.0.0'
                }
                """);
        runResolve();

        assertTrue(Files.exists(implSkillDir), "Expected active managed skill to remain");
        assertTrue(Files.notExists(apiSkillDir), "Expected stale managed skill to be removed");
        assertTrue(Files.exists(manualSkillDir), "Expected non-managed skill folders to remain untouched");
    }

    private BuildResult runResolve() {
        return GradleRunner.create()
                .withProjectDir(projectDir.toFile())
                .withPluginClasspath()
                .withArguments("resolveAgentDocs")
                .build();
    }

    private void writeConsumerBuildFile(String skillGenerationMode, Integer perDependencySkillThreshold) throws IOException {
        writeConsumerBuildFile(skillGenerationMode, perDependencySkillThreshold, DEFAULT_DEPENDENCIES);
    }

    private void writeConsumerBuildFile(String skillGenerationMode, Integer perDependencySkillThreshold, String dependenciesBlock)
            throws IOException {
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

                %s

                agentDocs {
                    skillGenerationMode = '%s'
                %s
                }
                """.formatted(dependenciesBlock, skillGenerationMode, thresholdLine));
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
            outputStream.putNextEntry(new ZipEntry("SKILL.md"));
            outputStream.write(("# " + group + ":" + artifact + ":" + version + "\n").getBytes());
            outputStream.closeEntry();
        }
    }

    private static void writeFile(Path filePath, String content) throws IOException {
        Files.createDirectories(filePath.getParent());
        Files.writeString(filePath, content);
    }
}
