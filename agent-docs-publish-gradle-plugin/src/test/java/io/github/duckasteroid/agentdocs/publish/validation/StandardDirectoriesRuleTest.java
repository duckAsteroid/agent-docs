package io.github.duckasteroid.agentdocs.publish.validation;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class StandardDirectoriesRuleTest {

    @TempDir
    Path tempDir;

    @Test
    void returnsFailureWhenStandardDirectoryIsAFile() throws IOException {
        writeFile(tempDir.resolve("assets"), "not a directory");

        ValidationResult result = new StandardDirectoriesRule().validate(new ValidationContext(tempDir.toFile()));

        assertTrue(result.severity().isPresent());
        assertTrue(result.severity().get() == ValidationSeverity.ERROR);
        assertTrue(result.details().getFirst().contains("standard directory 'assets' must be a directory"));
    }

    @Test
    void returnsPassWhenStandardDirectoriesAreDirectories() throws IOException {
        Files.createDirectories(tempDir.resolve("scripts"));
        Files.createDirectories(tempDir.resolve("references"));
        Files.createDirectories(tempDir.resolve("assets"));

        ValidationResult result = new StandardDirectoriesRule().validate(new ValidationContext(tempDir.toFile()));

        assertTrue(result.severity().isEmpty());
    }

    private static void writeFile(Path filePath, String content) throws IOException {
        Files.createDirectories(filePath.getParent());
        Files.writeString(filePath, content);
    }
}
