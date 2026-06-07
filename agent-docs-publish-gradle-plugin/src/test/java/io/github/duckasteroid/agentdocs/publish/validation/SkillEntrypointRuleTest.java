package io.github.duckasteroid.agentdocs.publish.validation;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SkillEntrypointRuleTest {

    @TempDir
    Path tempDir;

    @Test
    void returnsFailureWhenSkillEntrypointIsMissing() {
        ValidationResult result = new SkillEntrypointRule().validate(new ValidationContext(tempDir.toFile()));

        assertTrue(result.severity().isPresent());
        assertTrue(result.severity().get() == ValidationSeverity.ERROR);
        assertTrue(result.details().getFirst().contains("must contain SKILL.md"));
    }

    @Test
    void returnsFailureWhenMultipleSkillEntrypointsExist() throws IOException {
        writeFile(tempDir.resolve("SKILL.md"), "one");
        writeFile(tempDir.resolve("skill.md"), "two");

        ValidationResult result = new SkillEntrypointRule().validate(new ValidationContext(tempDir.toFile()));

        assertTrue(result.severity().isPresent());
        assertTrue(result.severity().get() == ValidationSeverity.ERROR);
        assertTrue(result.details().getFirst().contains("must contain exactly one SKILL.md file"));
    }

    @Test
    void returnsPassWhenSingleSkillEntrypointExists() throws IOException {
        writeFile(tempDir.resolve("sKiLl.Md"), "content");

        ValidationResult result = new SkillEntrypointRule().validate(new ValidationContext(tempDir.toFile()));

        assertTrue(result.severity().isEmpty());
    }

    private static void writeFile(Path filePath, String content) throws IOException {
        Files.createDirectories(filePath.getParent());
        Files.writeString(filePath, content);
    }
}
