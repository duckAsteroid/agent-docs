package io.github.duckasteroid.agentdocs.publish.validation;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class AssetsSourcesReservedRuleTest {

    @TempDir
    Path tempDir;

    @Test
    void returnsFailureWhenAssetsSourcesDirectoryIsPresent() throws IOException {
        Files.createDirectories(tempDir.resolve("assets/sources"));

        ValidationResult result = new AssetsSourcesReservedRule().validate(new ValidationContext(tempDir.toFile()));

        assertTrue(result.severity().isPresent());
        assertTrue(result.severity().get() == ValidationSeverity.ERROR);
        assertTrue(result.details().getFirst().contains("must not contain 'assets/sources'"));
    }

    @Test
    void returnsFailureWhenAssetsSourcesFileIsPresent() throws IOException {
        writeFile(tempDir.resolve("assets/sources"), "not a directory");

        ValidationResult result = new AssetsSourcesReservedRule().validate(new ValidationContext(tempDir.toFile()));

        assertTrue(result.severity().isPresent());
        assertTrue(result.severity().get() == ValidationSeverity.ERROR);
    }

    @Test
    void returnsPassWhenAssetsSourcesIsAbsent() throws IOException {
        Files.createDirectories(tempDir.resolve("assets"));

        ValidationResult result = new AssetsSourcesReservedRule().validate(new ValidationContext(tempDir.toFile()));

        assertTrue(result.severity().isEmpty());
    }

    @Test
    void returnsPassWhenDocsDirectoryHasNoAssetsDirectory() throws IOException {
        ValidationResult result = new AssetsSourcesReservedRule().validate(new ValidationContext(tempDir.toFile()));

        assertTrue(result.severity().isEmpty());
    }

    private static void writeFile(Path filePath, String content) throws IOException {
        Files.createDirectories(filePath.getParent());
        Files.writeString(filePath, content);
    }
}
