package io.github.duckasteroid.agentdocs.publish.validation;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SkillCompatibilityRuleTest {

    @TempDir
    Path tempDir;

    @Test
    void returnsFailureWhenCompatibilityExceedsMaxLength() throws IOException {
        writeFile(tempDir.resolve("SKILL.md"), """
                ---
                description: Valid description.
                compatibility: %s
                ---
                """.formatted("x".repeat(501)));

        ValidationResult result = new SkillCompatibilityRule().validate(new ValidationContext(tempDir.toFile()));

        assertTrue(result.severity().isPresent());
        assertTrue(result.severity().get() == ValidationSeverity.ERROR);
        assertTrue(result.details().getFirst().contains("'compatibility' must be <= 500"));
    }

    @Test
    void returnsPassWhenCompatibilityIsMissing() throws IOException {
        writeFile(tempDir.resolve("SKILL.md"), """
                ---
                description: Valid description.
                ---
                """);

        ValidationResult result = new SkillCompatibilityRule().validate(new ValidationContext(tempDir.toFile()));

        assertTrue(result.severity().isEmpty());
    }

    @Test
    void returnsPassWhenCompatibilityIsValid() throws IOException {
        writeFile(tempDir.resolve("SKILL.md"), """
                ---
                description: Valid description.
                compatibility: local-only
                ---
                """);

        ValidationResult result = new SkillCompatibilityRule().validate(new ValidationContext(tempDir.toFile()));

        assertTrue(result.severity().isEmpty());
    }

    private static void writeFile(Path filePath, String content) throws IOException {
        Files.createDirectories(filePath.getParent());
        Files.writeString(filePath, content);
    }
}
