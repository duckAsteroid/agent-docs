package io.github.duckasteroid.agentdocs.resolve.task;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.duckasteroid.agentdocs.resolve.task.model.ModuleCoordinate;
import io.github.duckasteroid.agentdocs.resolve.task.model.SkillEntry;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.gradle.testfixtures.ProjectBuilder;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SkillDirectoryManagerTest {
    @TempDir
    Path tempDir;

    @Test
    void materializeSkillExtractsSidecarAndRewritesEntrypointName() throws IOException {
        Path sidecar = tempDir.resolve("sidecar.zip");
        writeZipWithEntries(sidecar, "SKILL.md", "# Original\n");
        Path skillsRoot = tempDir.resolve("skills");
        Files.createDirectories(skillsRoot);

        SkillDirectoryManager manager =
                new SkillDirectoryManager(ProjectBuilder.builder().build().getLogger());
        ModuleCoordinate coordinate = new ModuleCoordinate("com.example", "demo", "1.0.0");

        SkillEntry entry = manager.materializeSkill(coordinate, sidecar, skillsRoot);

        assertNotNull(entry);
        Path skillDir = skillsRoot.resolve(coordinate.skillName());
        assertTrue(Files.notExists(skillDir.resolve("agent-docs.zip")));
        assertTrue(Files.exists(skillDir.resolve(".agent-docs")));
        assertTrue(Files.readString(entry.entrypointPath()).contains("name: " + coordinate.skillName()));
    }

    @Test
    void materializeSkillReturnsNullWhenNoEntrypointExists() throws IOException {
        Path sidecar = tempDir.resolve("sidecar-no-skill.zip");
        writeZipWithEntries(sidecar, "references/overview.md", "# Ref\n");

        SkillDirectoryManager manager =
                new SkillDirectoryManager(ProjectBuilder.builder().build().getLogger());
        SkillEntry entry = manager.materializeSkill(
                new ModuleCoordinate("com.example", "noskill", "1.0.0"), sidecar, tempDir.resolve("skills"));

        assertNull(entry);
    }

    @Test
    void cleanupRemovesOnlyStaleManagedDirectories() throws IOException {
        SkillDirectoryManager manager =
                new SkillDirectoryManager(ProjectBuilder.builder().build().getLogger());
        Path root = tempDir.resolve("skills");
        Files.createDirectories(root);

        ModuleCoordinate activeCoordinate = new ModuleCoordinate("com.example", "active", "1.0.0");
        Path activeDir = root.resolve(activeCoordinate.skillName());
        Files.createDirectories(activeDir);
        Files.writeString(activeDir.resolve(".agent-docs"), "");

        Path staleManaged = root.resolve("stale-managed");
        Files.createDirectories(staleManaged);
        Files.writeString(staleManaged.resolve(".agent-docs"), "");

        Path manual = root.resolve("manual-skill");
        Files.createDirectories(manual);
        Files.writeString(manual.resolve("SKILL.md"), "# Manual\n");

        SkillEntry active = new SkillEntry(activeCoordinate, activeDir.resolve("SKILL.md"));
        manager.cleanupStaleManagedSkillDirectories(Set.of(active), root);

        assertTrue(Files.exists(activeDir));
        assertTrue(Files.notExists(staleManaged));
        assertTrue(Files.exists(manual));
    }

    private static void writeZipWithEntries(Path zipPath, String entryName, String content) throws IOException {
        Files.createDirectories(zipPath.getParent());
        try (ZipOutputStream output = new ZipOutputStream(Files.newOutputStream(zipPath))) {
            output.putNextEntry(new ZipEntry(entryName));
            output.write(content.getBytes());
            output.closeEntry();
        }
    }
}
