package io.github.duckasteroid.agentdocs.publish.validation;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class DocsDirectoryExistsRuleTest {

    @TempDir
    Path tempDir;

    @Test
    void returnsFailureWhenDocsDirectoryIsMissing() {
        ValidationResult result = new DocsDirectoryExistsRule()
                .validate(new ValidationContext(tempDir.resolve("missing").toFile()));

        assertTrue(result.severity().isPresent());
        assertTrue(result.severity().get() == ValidationSeverity.ERROR);
        assertTrue(result.details().getFirst().contains("Agent docs directory does not exist"));
    }

    @Test
    void returnsPassWhenDocsDirectoryExists() {
        ValidationResult result = new DocsDirectoryExistsRule()
                .validate(new ValidationContext(tempDir.toFile()));

        assertTrue(result.severity().isEmpty());
    }
}
