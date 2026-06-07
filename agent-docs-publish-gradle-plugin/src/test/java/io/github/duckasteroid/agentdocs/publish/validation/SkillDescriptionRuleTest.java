package io.github.duckasteroid.agentdocs.publish.validation;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SkillDescriptionRuleTest {

    @TempDir
    Path tempDir;

    @Test
    void returnsFailureWhenDescriptionIsMissing() throws IOException {
        writeFile(tempDir.resolve("SKILL.md"), """
                ---
                compatibility: local-only
                ---
                """);

        ValidationResult result = new SkillDescriptionRule().validate(new ValidationContext(tempDir.toFile()));

        assertTrue(result.severity().isPresent());
        assertTrue(result.severity().get() == ValidationSeverity.ERROR);
        assertTrue(result.details().getFirst().contains("must define a non-empty 'description' field"));
    }

    @Test
    void returnsFailureWhenDescriptionExceedsMaxLength() throws IOException {
        writeFile(tempDir.resolve("SKILL.md"), """
                ---
                description: %s
                ---
                """.formatted("x".repeat(1025)));

        ValidationResult result = new SkillDescriptionRule().validate(new ValidationContext(tempDir.toFile()));

        assertTrue(result.severity().isPresent());
        assertTrue(result.severity().get() == ValidationSeverity.ERROR);
        assertTrue(result.details().getFirst().contains("'description' must be <= 1024"));
    }

    @Test
    void returnsPassWhenDescriptionIsValid() throws IOException {
        writeFile(tempDir.resolve("SKILL.md"), """
                ---
                description: Valid description.
                ---
                """);

        ValidationResult result = new SkillDescriptionRule().validate(new ValidationContext(tempDir.toFile()));

        assertTrue(result.severity().isEmpty());
    }

    private static void writeFile(Path filePath, String content) throws IOException {
        Files.createDirectories(filePath.getParent());
        Files.writeString(filePath, content);
    }
}
