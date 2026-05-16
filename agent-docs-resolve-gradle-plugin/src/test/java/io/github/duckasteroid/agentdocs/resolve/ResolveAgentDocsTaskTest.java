package io.github.duckasteroid.agentdocs.resolve;

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
        writeFile(projectDir.resolve("build.gradle"), """
                plugins {
                    id 'java-library'
                    id 'io.github.duckasteroid.agent-docs.resolve'
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
                """);
        writeFile(projectDir.resolve("src/main/java/example/App.java"), """
                package example;
                public class App {
                    public static String value() {
                        return "ok";
                    }
                }
                """);

        BuildResult result = GradleRunner.create()
                .withProjectDir(projectDir.toFile())
                .withPluginClasspath()
                .withArguments("resolveAgentDocs", "-DagentDocs.localRepository=" + localSidecarRepo)
                .build();

        assertNotNull(result.task(":resolveAgentDocs"));
        assertEquals(TaskOutcome.SUCCESS, result.task(":resolveAgentDocs").getOutcome());

        Path implSidecar = localSidecarRepo.resolve("com/example/dep-impl/1.0.0/dep-impl-1.0.0-agent-docs.zip");
        Path apiSidecar = localSidecarRepo.resolve("com/example/dep-api/2.0.0/dep-api-2.0.0-agent-docs.zip");
        Path missingSidecar = localSidecarRepo.resolve("com/example/dep-no-sidecar/3.0.0/dep-no-sidecar-3.0.0-agent-docs.zip");

        assertTrue(Files.exists(implSidecar), "Expected implementation dependency sidecar to be cached");
        assertTrue(Files.exists(apiSidecar), "Expected api dependency sidecar to be cached");
        assertTrue(Files.notExists(missingSidecar), "Expected missing sidecar dependency to be skipped");

        Path indexFile = projectDir.resolve("build/agent-docs-resolver/index.json");
        assertTrue(Files.exists(indexFile), "Expected resolver index to be written");
        String index = Files.readString(indexFile);
        assertTrue(index.contains("com.example:dep-impl:1.0.0"));
        assertTrue(index.contains("com.example:dep-api:2.0.0"));
        assertTrue(index.contains("com.example:dep-no-sidecar:3.0.0"));
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
            writeFile(baseDir.resolve(artifact + "-" + version + "-agent-docs.zip"), "sidecar");
        }
    }

    private static void writeFile(Path filePath, String content) throws IOException {
        Files.createDirectories(filePath.getParent());
        Files.writeString(filePath, content);
    }
}

