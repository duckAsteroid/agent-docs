package io.github.duckasteroid.agentdocs.resolve;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.jar.Attributes;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.gradle.testkit.runner.BuildResult;
import org.gradle.testkit.runner.GradleRunner;
import org.gradle.testkit.runner.TaskOutcome;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ResolveAgentDocsTaskTest {
    private static final String DEFAULT_TEST_GRADLE_VERSION = "9.5.1";
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
    void resolveAgentDocsDownloadsAndExtractsSkills() throws IOException {
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

        Path implRoot = projectDir.resolve(".agents/skills/dep-impl");
        Path apiRoot = projectDir.resolve(".agents/skills/dep-api");
        Path missingRoot = projectDir.resolve(".agents/skills/dep-no-sidecar");

        assertTrue(Files.notExists(implRoot.resolve("agent-docs.zip")));
        assertTrue(Files.notExists(apiRoot.resolve("agent-docs.zip")));
        assertTrue(Files.notExists(missingRoot.resolve("agent-docs.zip")));

        Path implEntrypoint = implRoot.resolve("SKILL.md");
        Path apiEntrypoint = apiRoot.resolve("SKILL.md");
        assertTrue(Files.exists(implEntrypoint));
        assertTrue(Files.exists(apiEntrypoint));
        assertTrue(Files.exists(implRoot.resolve(".agent-docs")));
        assertTrue(Files.exists(apiRoot.resolve(".agent-docs")));
        assertTrue(Files.readString(implEntrypoint).contains("name: dep-impl"));
        assertTrue(Files.readString(apiEntrypoint).contains("name: dep-api"));

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

        Path implSkillDir = projectDir.resolve(".agents/skills/dep-impl");
        Path apiSkillDir = projectDir.resolve(".agents/skills/dep-api");
        assertTrue(Files.exists(implSkillDir.resolve(".agent-docs")));
        assertTrue(Files.exists(apiSkillDir.resolve(".agent-docs")));

        Path manualSkillDir = projectDir.resolve(".agents/skills/custom-manual-skill");
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

    @Test
    void resolveAgentDocsFromSubprojectWritesToRootAgentsDirectory() throws IOException {
        Path upstreamRepo = projectDir.resolve("upstream-repo");
        writeMavenModule(upstreamRepo, "com.example", "dep-impl", "1.0.0", true);

        writeFile(projectDir.resolve("settings.gradle"), """
                rootProject.name = 'consumer'
                include 'app'
                """);

        writeFile(projectDir.resolve("build.gradle"), "// root build\n");
        writeFile(projectDir.resolve("app/build.gradle"), """
                plugins {
                    id 'java-library'
                    id 'io.github.duckasteroid.agent-docs'
                }

                repositories {
                    maven {
                        url = uri('../upstream-repo')
                    }
                }

                dependencies {
                    implementation 'com.example:dep-impl:1.0.0'
                }
                """);
        writeFile(projectDir.resolve("app/src/main/java/example/App.java"), """
                package example;
                public class App {
                    public static String value() {
                        return "ok";
                    }
                }
                """);

        BuildResult result = runResolve(":app:resolveAgentDocs");

        assertNotNull(result.task(":app:resolveAgentDocs"));
        assertEquals(TaskOutcome.SUCCESS, result.task(":app:resolveAgentDocs").getOutcome());
        assertTrue(Files.exists(projectDir.resolve(".agents/skills/dep-impl/SKILL.md")));
        assertTrue(Files.notExists(projectDir.resolve("app/.agents/skills/dep-impl/SKILL.md")));
    }

    @Test
    void resolveAgentDocsSupportsConfigurationCache() throws IOException {
        Path upstreamRepo = projectDir.resolve("upstream-repo");
        writeMavenModule(upstreamRepo, "com.example", "dep-impl", "1.0.0", true);

        writeFile(projectDir.resolve("settings.gradle"), "rootProject.name = 'consumer'\n");
        writeConsumerBuildFile("""
                dependencies {
                    implementation 'com.example:dep-impl:1.0.0'
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

        BuildResult first = runResolve("resolveAgentDocs", "--configuration-cache");
        BuildResult second = runResolve("resolveAgentDocs", "--configuration-cache");

        assertNotNull(first.task(":resolveAgentDocs"));
        assertNotNull(second.task(":resolveAgentDocs"));
        assertEquals(TaskOutcome.SUCCESS, first.task(":resolveAgentDocs").getOutcome());
        assertEquals(TaskOutcome.SUCCESS, second.task(":resolveAgentDocs").getOutcome());
        assertTrue(first.getOutput().contains("Configuration cache entry"));
        assertTrue(second.getOutput().contains("Configuration cache entry"));
        assertTrue(!first.getOutput().contains("Configuration cache problems found in this build."));
        assertTrue(!second.getOutput().contains("Configuration cache problems found in this build."));
    }

    @Test
    void resolveAgentDocsFromSubprojectAvoidsDetachedConfigurationExclusiveLockFailure() throws IOException {
        Path upstreamRepo = projectDir.resolve("upstream-repo");
        writeMavenModule(upstreamRepo, "com.example", "dep-impl", "1.0.0", true);

        writeFile(projectDir.resolve("settings.gradle"), """
                rootProject.name = 'consumer'
                include 'persistence-neo4j'
                """);

        writeFile(projectDir.resolve("build.gradle"), "// root build\n");
        writeFile(projectDir.resolve("persistence-neo4j/build.gradle"), """
                plugins {
                    id 'java-library'
                    id 'io.github.duckasteroid.agent-docs'
                }

                repositories {
                    maven {
                        url = uri('../upstream-repo')
                    }
                }

                dependencies {
                    implementation 'com.example:dep-impl:1.0.0'
                }
                """);
        writeFile(projectDir.resolve("persistence-neo4j/src/main/java/example/App.java"), """
                package example;
                public class App {
                    public static String value() {
                        return "ok";
                    }
                }
                """);

        BuildResult result = runResolve(
                ":persistence-neo4j:resolveAgentDocs",
                "--parallel",
                "--configuration-cache",
                "--stacktrace");

        assertNotNull(result.task(":persistence-neo4j:resolveAgentDocs"));
        assertEquals(TaskOutcome.SUCCESS, result.task(":persistence-neo4j:resolveAgentDocs").getOutcome());
        assertTrue(!result.getOutput().contains("was attempted without an exclusive lock"));
        assertTrue(result.getOutput().contains("materialized 1 skills"));
        assertTrue(Files.exists(projectDir.resolve(".agents/skills/dep-impl/SKILL.md")));
    }

    @Test
    void installAgentDocsResolveSkillWritesSkillFileToDefaultLocation() throws IOException {
        writeFile(projectDir.resolve("settings.gradle"), "rootProject.name = 'consumer'\n");
        writeFile(projectDir.resolve("build.gradle"), """
                plugins {
                    id 'java-library'
                    id 'io.github.duckasteroid.agent-docs'
                }
                """);

        BuildResult result = GradleRunner.create()
                .withGradleVersion(testGradleVersion())
                .withProjectDir(projectDir.toFile())
                .withPluginClasspath()
                .withArguments("installAgentDocsResolveSkill")
                .build();

        assertEquals(TaskOutcome.SUCCESS, result.task(":installAgentDocsResolveSkill").getOutcome());

        Path skillFile = projectDir.resolve(".agents/skills/agent-docs-resolve/SKILL.md");
        assertTrue(Files.exists(skillFile), "Expected SKILL.md to be written at default location");
        String content = Files.readString(skillFile);
        assertTrue(content.contains("name: agent-docs-resolve"), "Expected skill name in frontmatter");
        assertTrue(content.contains("io.github.duckasteroid.agent-docs"), "Expected plugin ID in content");
    }

    @Test
    void installAgentDocsResolveSkillIsUpToDateOnSecondRun() throws IOException {
        writeFile(projectDir.resolve("settings.gradle"), "rootProject.name = 'consumer'\n");
        writeFile(projectDir.resolve("build.gradle"), """
                plugins {
                    id 'java-library'
                    id 'io.github.duckasteroid.agent-docs'
                }
                """);

        GradleRunner.create()
                .withGradleVersion(testGradleVersion())
                .withProjectDir(projectDir.toFile())
                .withPluginClasspath()
                .withArguments("installAgentDocsResolveSkill")
                .build();

        BuildResult second = GradleRunner.create()
                .withGradleVersion(testGradleVersion())
                .withProjectDir(projectDir.toFile())
                .withPluginClasspath()
                .withArguments("installAgentDocsResolveSkill")
                .build();

        assertEquals(TaskOutcome.UP_TO_DATE, second.task(":installAgentDocsResolveSkill").getOutcome());
    }

    @Test
    void installAgentDocsResolveSkillRespectsConfiguredSkillsDirectory() throws IOException {
        writeFile(projectDir.resolve("settings.gradle"), "rootProject.name = 'consumer'\n");
        writeFile(projectDir.resolve("build.gradle"), """
                plugins {
                    id 'java-library'
                    id 'io.github.duckasteroid.agent-docs'
                }

                agentDocs {
                    skillsDirectory = rootProject.layout.projectDirectory.dir('custom-skills')
                }
                """);

        BuildResult result = GradleRunner.create()
                .withGradleVersion(testGradleVersion())
                .withProjectDir(projectDir.toFile())
                .withPluginClasspath()
                .withArguments("installAgentDocsResolveSkill")
                .build();

        assertEquals(TaskOutcome.SUCCESS, result.task(":installAgentDocsResolveSkill").getOutcome());
        assertTrue(Files.exists(projectDir.resolve("custom-skills/agent-docs-resolve/SKILL.md")),
                "Expected SKILL.md written in configured skills directory");
        assertTrue(Files.notExists(projectDir.resolve(".agents/skills/agent-docs-resolve/SKILL.md")),
                "Expected default location to be unused when skills directory is customized");
    }

    @Test
    void installAgentDocsResolveSkillWritesToRootProjectInMultiProjectBuild() throws IOException {
        writeFile(projectDir.resolve("settings.gradle"), """
                rootProject.name = 'multi-root'
                include 'app'
                """);
        writeFile(projectDir.resolve("build.gradle"), "// root\n");
        writeFile(projectDir.resolve("app/build.gradle"), """
                plugins {
                    id 'java-library'
                    id 'io.github.duckasteroid.agent-docs'
                }
                """);

        BuildResult result = GradleRunner.create()
                .withGradleVersion(testGradleVersion())
                .withProjectDir(projectDir.toFile())
                .withPluginClasspath()
                .withArguments(":app:installAgentDocsResolveSkill")
                .build();

        assertEquals(TaskOutcome.SUCCESS, result.task(":app:installAgentDocsResolveSkill").getOutcome());
        assertTrue(Files.exists(projectDir.resolve(".agents/skills/agent-docs-resolve/SKILL.md")),
                "Expected SKILL.md written in root project, not subproject");
        assertTrue(Files.notExists(projectDir.resolve("app/.agents/skills/agent-docs-resolve/SKILL.md")),
                "Expected SKILL.md not written in subproject directory");
    }

    @Test
    void resolveAgentDocsFromSubprojectReusesConfigurationCacheAcrossRuns() throws IOException {
        Path upstreamRepo = projectDir.resolve("upstream-repo");
        writeMavenModule(upstreamRepo, "com.example", "dep-impl", "1.0.0", true);

        writeFile(projectDir.resolve("settings.gradle"), """
                rootProject.name = 'consumer'
                include 'persistence-neo4j'
                """);

        writeFile(projectDir.resolve("build.gradle"), "// root build\n");
        writeFile(projectDir.resolve("persistence-neo4j/build.gradle"), """
                plugins {
                    id 'java-library'
                    id 'io.github.duckasteroid.agent-docs'
                }

                repositories {
                    maven {
                        url = uri('../upstream-repo')
                    }
                }

                dependencies {
                    implementation 'com.example:dep-impl:1.0.0'
                }
                """);
        writeFile(projectDir.resolve("persistence-neo4j/src/main/java/example/App.java"), """
                package example;
                public class App {
                    public static String value() {
                        return "ok";
                    }
                }
                """);

        BuildResult first = runResolve(
                ":persistence-neo4j:resolveAgentDocs",
                "--parallel",
                "--configuration-cache");
        BuildResult second = runResolve(
                ":persistence-neo4j:resolveAgentDocs",
                "--parallel",
                "--configuration-cache");

        assertNotNull(first.task(":persistence-neo4j:resolveAgentDocs"));
        assertNotNull(second.task(":persistence-neo4j:resolveAgentDocs"));
        assertEquals(TaskOutcome.SUCCESS, first.task(":persistence-neo4j:resolveAgentDocs").getOutcome());
        assertEquals(TaskOutcome.SUCCESS, second.task(":persistence-neo4j:resolveAgentDocs").getOutcome());
        assertTrue(first.getOutput().contains("Configuration cache entry"));
        assertTrue(second.getOutput().contains("Configuration cache entry"));
        assertTrue(!first.getOutput().contains("was attempted without an exclusive lock"));
        assertTrue(!second.getOutput().contains("was attempted without an exclusive lock"));
        assertTrue(first.getOutput().contains("materialized 1 skills"));
        assertTrue(second.getOutput().contains("materialized 1 skills"));
        assertTrue(Files.exists(projectDir.resolve(".agents/skills/dep-impl/SKILL.md")));
    }

    @Test
    void resolveAgentDocsExtractsSourcesAndAnnotatesSkillWhenIncludeSourcesEnabled() throws IOException {
        Path upstreamRepo = projectDir.resolve("upstream-repo");
        writeMavenModule(upstreamRepo, "com.example", "dep-impl", "1.0.0", true, true);
        writeMavenModule(upstreamRepo, "com.example", "dep-no-sources", "2.0.0", true, false);

        writeFile(projectDir.resolve("settings.gradle"), "rootProject.name = 'consumer'\n");
        writeConsumerBuildFileWithSources("""
                dependencies {
                    implementation 'com.example:dep-impl:1.0.0'
                    implementation 'com.example:dep-no-sources:2.0.0'
                }
                """);
        writeFile(projectDir.resolve("src/main/java/example/App.java"), """
                package example;
                public class App {}
                """);

        BuildResult result = runResolve();

        assertEquals(TaskOutcome.SUCCESS, result.task(":resolveAgentDocs").getOutcome());

        Path implRoot = projectDir.resolve(".agents/skills/dep-impl");
        String implSkill = Files.readString(implRoot.resolve("SKILL.md"));
        assertTrue(implSkill.contains("metadata:\n  sources: src/"), "Expected sources path in metadata");
        assertTrue(Files.exists(implRoot.resolve("src/com/example/dep/impl/Main.java")),
                "Expected sources extracted to src/ subdirectory");

        Path noSourcesRoot = projectDir.resolve(".agents/skills/dep-no-sources");
        String noSourcesSkill = Files.readString(noSourcesRoot.resolve("SKILL.md"));
        assertTrue(noSourcesSkill.contains("metadata:\n  sources: none"),
                "Expected 'none' when sources jar is absent");
        assertTrue(Files.notExists(noSourcesRoot.resolve("src/")),
                "Expected no src/ directory when sources unavailable");
    }

    @Test
    void resolveAgentDocsDoesNotInjectSourcesMetadataWhenIncludeSourcesIsFalse() throws IOException {
        Path upstreamRepo = projectDir.resolve("upstream-repo");
        writeMavenModule(upstreamRepo, "com.example", "dep-impl", "1.0.0", true, true);

        writeFile(projectDir.resolve("settings.gradle"), "rootProject.name = 'consumer'\n");
        writeConsumerBuildFile("""
                dependencies {
                    implementation 'com.example:dep-impl:1.0.0'
                }
                """);
        writeFile(projectDir.resolve("src/main/java/example/App.java"), """
                package example;
                public class App {}
                """);

        BuildResult result = runResolve();

        assertEquals(TaskOutcome.SUCCESS, result.task(":resolveAgentDocs").getOutcome());

        Path implRoot = projectDir.resolve(".agents/skills/dep-impl");
        String skillContent = Files.readString(implRoot.resolve("SKILL.md"));
        assertTrue(!skillContent.contains("metadata:"), "Expected no metadata block when includeSources is false");
        assertTrue(!skillContent.contains("sources:"), "Expected no sources field when includeSources is false");
    }

    @Test
    void resolveAgentDocsExtractsEmbeddedClasspathDocsWithDefaultPath() throws IOException {
        Path upstreamRepo = projectDir.resolve("upstream-repo");
        writeMavenModuleWithEmbeddedDocs(upstreamRepo, "com.example", "dep-embedded", "1.0.0", "classpath", "agent-docs/");

        writeFile(projectDir.resolve("settings.gradle"), "rootProject.name = 'consumer'\n");
        writeConsumerBuildFile("""
                dependencies {
                    implementation 'com.example:dep-embedded:1.0.0'
                }
                """);
        writeFile(projectDir.resolve("src/main/java/example/App.java"), """
                package example;
                public class App {}
                """);

        BuildResult result = runResolve();
        assertEquals(TaskOutcome.SUCCESS, result.task(":resolveAgentDocs").getOutcome());

        Path skillRoot = projectDir.resolve(".agents/skills/dep-embedded");
        Path entrypoint = skillRoot.resolve("SKILL.md");
        assertTrue(Files.exists(entrypoint), "Expected SKILL.md extracted from the dependency's own jar");
        assertTrue(Files.exists(skillRoot.resolve(".agent-docs")));
        assertTrue(Files.readString(entrypoint).contains("name: dep-embedded"));
    }

    @Test
    void resolveAgentDocsExtractsEmbeddedClasspathDocsWithCustomPath() throws IOException {
        Path upstreamRepo = projectDir.resolve("upstream-repo");
        writeMavenModuleWithEmbeddedDocs(
                upstreamRepo, "com.example", "dep-custom-path", "1.0.0", "classpath:custom/docs/", "custom/docs/");

        writeFile(projectDir.resolve("settings.gradle"), "rootProject.name = 'consumer'\n");
        writeConsumerBuildFile("""
                dependencies {
                    implementation 'com.example:dep-custom-path:1.0.0'
                }
                """);
        writeFile(projectDir.resolve("src/main/java/example/App.java"), """
                package example;
                public class App {}
                """);

        BuildResult result = runResolve();
        assertEquals(TaskOutcome.SUCCESS, result.task(":resolveAgentDocs").getOutcome());

        Path skillRoot = projectDir.resolve(".agents/skills/dep-custom-path");
        Path entrypoint = skillRoot.resolve("SKILL.md");
        assertTrue(Files.exists(entrypoint), "Expected SKILL.md extracted from the custom embedded path");
        assertTrue(Files.readString(entrypoint).contains("name: dep-custom-path"));
    }

    @Test
    void resolveAgentDocsEscalatesToGroupArtifactWhenArtifactNamesClash() throws IOException {
        Path upstreamRepo = projectDir.resolve("upstream-repo");
        writeMavenModule(upstreamRepo, "com.acme", "core", "1.0.0", true);
        writeMavenModule(upstreamRepo, "com.other", "core", "2.0.0", true);

        writeFile(projectDir.resolve("settings.gradle"), "rootProject.name = 'consumer'\n");
        writeConsumerBuildFile("""
                dependencies {
                    implementation 'com.acme:core:1.0.0'
                    implementation 'com.other:core:2.0.0'
                }
                """);
        writeFile(projectDir.resolve("src/main/java/example/App.java"), """
                package example;
                public class App {}
                """);

        BuildResult result = runResolve();
        assertEquals(TaskOutcome.SUCCESS, result.task(":resolveAgentDocs").getOutcome());

        // Both artifacts are named "core", so neither gets the short artifact-only name -
        // both escalate to group-artifact instead.
        assertTrue(Files.notExists(projectDir.resolve(".agents/skills/core")));
        Path acmeRoot = projectDir.resolve(".agents/skills/com-acme-core");
        Path otherRoot = projectDir.resolve(".agents/skills/com-other-core");
        assertTrue(Files.exists(acmeRoot.resolve("SKILL.md")));
        assertTrue(Files.exists(otherRoot.resolve("SKILL.md")));
        assertTrue(Files.readString(acmeRoot.resolve("SKILL.md")).contains("name: com-acme-core"));
        assertTrue(Files.readString(otherRoot.resolve("SKILL.md")).contains("name: com-other-core"));
    }

    private void writeConsumerBuildFileWithSources(String dependenciesBlock) throws IOException {
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

                agentDocs {
                    includeSources = true
                }

                %s
                """.formatted(dependenciesBlock));
    }

    private BuildResult runResolve() {
        return runResolve("resolveAgentDocs");
    }

    private BuildResult runResolve(String... arguments) {
        return GradleRunner.create()
                .withGradleVersion(testGradleVersion())
                .withProjectDir(projectDir.toFile())
                .withPluginClasspath()
                .withArguments(arguments)
                .build();
    }

    private static String testGradleVersion() {
        return System.getProperty("agentDocs.test.gradleVersion", DEFAULT_TEST_GRADLE_VERSION);
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
        writeMavenModule(repositoryRoot, group, artifact, version, withSidecar, false);
    }

    /**
     * Writes a Maven module whose main jar carries the {@code Agent-Docs: maven} manifest
     * attribute when {@code withSidecar} is {@code true} (with a real sidecar zip published
     * alongside it), or no {@code Agent-Docs} attribute at all otherwise.
     */
    private static void writeMavenModule(
            Path repositoryRoot, String group, String artifact, String version,
            boolean withSidecar, boolean withSources) throws IOException {
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
        writeJarWithManifest(
                baseDir.resolve(artifact + "-" + version + ".jar"),
                withSidecar ? "maven" : null,
                Map.of());
        if (withSidecar) {
            writeAgentDocsSidecar(baseDir.resolve(artifact + "-" + version + "-agent-docs.zip"), group, artifact, version);
        }
        if (withSources) {
            writeSourcesJar(baseDir.resolve(artifact + "-" + version + "-sources.jar"), group, artifact);
        }
    }

    /**
     * Writes a Maven module whose main jar carries the {@code Agent-Docs} attribute given by
     * {@code agentDocsValue} (e.g. {@code "classpath"} or {@code "classpath:custom/docs/"}) and
     * embeds a docs bundle directly inside that same jar at {@code embeddedPath} — no separate
     * sidecar zip is published at all.
     */
    private static void writeMavenModuleWithEmbeddedDocs(
            Path repositoryRoot, String group, String artifact, String version, String agentDocsValue, String embeddedPath)
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

        String skillContent = "---\ndescription: " + group + ":" + artifact + ":" + version + "\n---\n\n# Docs\n";
        writeJarWithManifest(
                baseDir.resolve(artifact + "-" + version + ".jar"),
                agentDocsValue,
                Map.of(embeddedPath + "SKILL.md", skillContent.getBytes()));
    }

    private static void writeJarWithManifest(Path jarPath, String agentDocsValue, Map<String, byte[]> extraEntries)
            throws IOException {
        Files.createDirectories(jarPath.getParent());
        Manifest manifest = new Manifest();
        manifest.getMainAttributes().put(Attributes.Name.MANIFEST_VERSION, "1.0");
        if (agentDocsValue != null) {
            manifest.getMainAttributes().putValue("Agent-Docs", agentDocsValue);
        }
        try (JarOutputStream jarOutputStream = new JarOutputStream(Files.newOutputStream(jarPath), manifest)) {
            for (Map.Entry<String, byte[]> entry : extraEntries.entrySet()) {
                jarOutputStream.putNextEntry(new JarEntry(entry.getKey()));
                jarOutputStream.write(entry.getValue());
                jarOutputStream.closeEntry();
            }
        }
    }

    private static void writeAgentDocsSidecar(Path zipPath, String group, String artifact, String version) throws IOException {
        Files.createDirectories(zipPath.getParent());
        try (ZipOutputStream outputStream = new ZipOutputStream(Files.newOutputStream(zipPath))) {
            outputStream.putNextEntry(new ZipEntry("SKILL.md"));
            outputStream.write(("---\ndescription: " + group + ":" + artifact + ":" + version + "\n---\n\n# Docs\n").getBytes());
            outputStream.closeEntry();
        }
    }

    private static void writeSourcesJar(Path jarPath, String group, String artifact) throws IOException {
        Files.createDirectories(jarPath.getParent());
        String javaPath = group.replace('.', '/') + "/" + artifact.replace('-', '/') + "/Main.java";
        try (ZipOutputStream outputStream = new ZipOutputStream(Files.newOutputStream(jarPath))) {
            outputStream.putNextEntry(new ZipEntry(javaPath));
            outputStream.write(("package " + group + ";\npublic class Main {}\n").getBytes());
            outputStream.closeEntry();
        }
    }

    private static void writeFile(Path filePath, String content) throws IOException {
        Files.createDirectories(filePath.getParent());
        Files.writeString(filePath, content);
    }
}
