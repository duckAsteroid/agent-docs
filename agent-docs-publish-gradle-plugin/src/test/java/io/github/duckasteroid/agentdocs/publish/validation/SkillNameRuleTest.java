package io.github.duckasteroid.agentdocs.publish.validation;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SkillNameRuleTest {

    @TempDir
    Path tempDir;

    @Test
    void returnsWarningWhenNameIsInvalid() throws IOException {
        writeFile(tempDir.resolve("SKILL.md"), """
                ---
                name: Invalid_Name
                description: Valid description.
                ---
                """);

        ValidationResult result = new SkillNameRule().validate(new ValidationContext(tempDir.toFile()));

        assertTrue(result.severity().isPresent());
        assertTrue(result.severity().get() == ValidationSeverity.WARN);
        assertTrue(result.details().getFirst().contains("frontmatter 'name' is ignored"));
    }

    @Test
    void returnsPassWhenNameIsMissing() throws IOException {
        writeFile(tempDir.resolve("SKILL.md"), """
                ---
                description: Valid description.
                ---
                """);

        ValidationResult result = new SkillNameRule().validate(new ValidationContext(tempDir.toFile()));

        assertTrue(result.severity().isEmpty());
    }

    @Test
    void returnsWarningWhenNameIsValid() throws IOException {
        writeFile(tempDir.resolve("SKILL.md"), """
                ---
                name: example-skill-1
                description: Valid description.
                ---
                """);

        ValidationResult result = new SkillNameRule().validate(new ValidationContext(tempDir.toFile()));

        assertTrue(result.severity().isPresent());
        assertTrue(result.severity().get() == ValidationSeverity.WARN);
        assertFalse(result.details().isEmpty());
    }

    private static void writeFile(Path filePath, String content) throws IOException {
        Files.createDirectories(filePath.getParent());
        Files.writeString(filePath, content);
    }
}
