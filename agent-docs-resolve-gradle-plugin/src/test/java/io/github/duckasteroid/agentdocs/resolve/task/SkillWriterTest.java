package io.github.duckasteroid.agentdocs.resolve.task;

import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.duckasteroid.agentdocs.resolve.task.model.ModuleCoordinate;
import io.github.duckasteroid.agentdocs.resolve.task.model.SkillEntry;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SkillWriterTest {
    @TempDir
    Path tempDir;

    @Test
    void writeSkillsWritesPerDependencySkillAndMarker() throws IOException {
        Path skillsRoot = tempDir.resolve("skills");
        Path singleSkill = skillsRoot.resolve("SKILL.md");
        Files.createDirectories(singleSkill.getParent());
        Files.writeString(singleSkill, "stale");

        ModuleCoordinate coordinate = new ModuleCoordinate("com.example", "demo", "1.0.0");
        Path dependencyEntrypoint = skillsRoot.resolve(coordinate.skillName()).resolve("SKILL.md");
        Files.createDirectories(dependencyEntrypoint.getParent());
        Files.writeString(dependencyEntrypoint, "# dep\n");
        Set<SkillEntry> entries = Set.of(new SkillEntry(coordinate, dependencyEntrypoint));

        SkillWriter writer = new SkillWriter(getClass().getClassLoader());
        writer.writeSkills(entries, skillsRoot);

        Path perDependencySkill = skillsRoot
                .resolve("agent-docs-dependencies")
                .resolve(coordinate.skillName())
                .resolve("SKILL.md");
        assertTrue(Files.exists(perDependencySkill));
        assertTrue(Files.exists(perDependencySkill.getParent().resolve(".agent-docs")));
        assertTrue(Files.notExists(singleSkill));
    }
}
