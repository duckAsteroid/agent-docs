package io.github.duckasteroid.agentdocs.publish.validation;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SkillFrontmatterStructureRuleTest {

    @TempDir
    Path tempDir;

    @Test
    void returnsFailureWhenFrontmatterIsMissing() throws IOException {
        writeFile(tempDir.resolve("SKILL.md"), "# Missing frontmatter");

        ValidationResult result = new SkillFrontmatterStructureRule().validate(new ValidationContext(tempDir.toFile()));

        assertTrue(result.severity().isPresent());
        assertTrue(result.severity().get() == ValidationSeverity.ERROR);
        assertTrue(result.details().getFirst().contains("must start with YAML frontmatter"));
    }

    @Test
    void returnsPassWhenFrontmatterIsPresent() throws IOException {
        writeFile(tempDir.resolve("SKILL.md"), """
                ---
                description: Valid skill.
                ---

                # Skill
                """);

        ValidationResult result = new SkillFrontmatterStructureRule().validate(new ValidationContext(tempDir.toFile()));

        assertTrue(result.severity().isEmpty());
    }

    private static void writeFile(Path filePath, String content) throws IOException {
        Files.createDirectories(filePath.getParent());
        Files.writeString(filePath, content);
    }
}
