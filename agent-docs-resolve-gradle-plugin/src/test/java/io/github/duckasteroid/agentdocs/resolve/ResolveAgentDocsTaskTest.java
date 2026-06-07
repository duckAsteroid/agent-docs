package io.github.duckasteroid.agentdocs.resolve;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
    void resolveAgentDocsDownloadsExtractsAndGeneratesPerDependencySkills() throws IOException {
        Path upstreamRepo = projectDir.resolve("upstream-repo");

        writeMavenModule(upstreamRepo, "com.example", "dep-impl", "1.0.0", true);
        writeMavenModule(upstreamRepo, "com.example", "dep-api", "2.0.0", true);
        writeMavenModule(upstreamRepo, "com.example", "dep-no-sidecar", "3.0.0", false);

        writeFile(projectDir.resolve("settings.gradle"), "rootProject.name = 'consumer'\n");
        writeConsumerBuildFile(DEFAULT_DEPENDENCIES);
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

        Path implRoot = projectDir.resolve(".agent/skills/com-example-dep-impl-1-0-0");
        Path apiRoot = projectDir.resolve(".agent/skills/com-example-dep-api-2-0-0");
        Path missingRoot = projectDir.resolve(".agent/skills/com-example-dep-no-sidecar-3-0-0");

        assertTrue(Files.exists(implRoot.resolve("agent-docs.zip")));
        assertTrue(Files.exists(apiRoot.resolve("agent-docs.zip")));
        assertTrue(Files.notExists(missingRoot.resolve("agent-docs.zip")));

        Path implEntrypoint = implRoot.resolve("SKILL.md");
        Path apiEntrypoint = apiRoot.resolve("SKILL.md");
        assertTrue(Files.exists(implEntrypoint));
        assertTrue(Files.exists(apiEntrypoint));
        assertTrue(Files.exists(implRoot.resolve(".agent-docs")));
        assertTrue(Files.exists(apiRoot.resolve(".agent-docs")));
        assertTrue(Files.readString(implEntrypoint).contains("name: com-example-dep-impl-1-0-0"));
        assertTrue(Files.readString(apiEntrypoint).contains("name: com-example-dep-api-2-0-0"));

        Path implGeneratedSkill =
                projectDir.resolve(".agent/skills/agent-docs-dependencies/com-example-dep-impl-1-0-0/SKILL.md");
        Path apiGeneratedSkill =
                projectDir.resolve(".agent/skills/agent-docs-dependencies/com-example-dep-api-2-0-0/SKILL.md");
        assertTrue(Files.exists(implGeneratedSkill));
        assertTrue(Files.exists(apiGeneratedSkill));
        assertTrue(Files.exists(implGeneratedSkill.getParent().resolve(".agent-docs")));
        assertTrue(Files.exists(apiGeneratedSkill.getParent().resolve(".agent-docs")));
        assertTrue(Files.readString(implGeneratedSkill).contains("name: com-example-dep-impl-1-0-0"));
        assertTrue(Files.readString(apiGeneratedSkill).contains("name: com-example-dep-api-2-0-0"));
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

        writeConsumerBuildFile("""
                dependencies {
                    implementation 'com.example:dep-impl:1.0.0'
                    implementation 'com.example:dep-api:2.0.0'
                }
                """);
        runResolve();

        Path implSkillDir = projectDir.resolve(".agent/skills/com-example-dep-impl-1-0-0");
        Path apiSkillDir = projectDir.resolve(".agent/skills/com-example-dep-api-2-0-0");
        assertTrue(Files.exists(implSkillDir.resolve(".agent-docs")));
        assertTrue(Files.exists(apiSkillDir.resolve(".agent-docs")));

        Path manualSkillDir = projectDir.resolve(".agent/skills/custom-manual-skill");
        writeFile(manualSkillDir.resolve("SKILL.md"), "# custom\n");

        writeConsumerBuildFile("""
                dependencies {
                    implementation 'com.example:dep-impl:1.0.0'
                    implementation 'com.example:dep-no-sidecar:3.0.0'
                }
                """);
        runResolve();

        assertTrue(Files.exists(implSkillDir));
        assertTrue(Files.notExists(apiSkillDir));
        assertTrue(Files.exists(manualSkillDir));
    }

    private BuildResult runResolve() {
        return GradleRunner.create()
                .withProjectDir(projectDir.toFile())
                .withPluginClasspath()
                .withArguments("resolveAgentDocs")
                .build();
    }

    private void writeConsumerBuildFile(String dependenciesBlock) throws IOException {
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
                """.formatted(dependenciesBlock));
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
