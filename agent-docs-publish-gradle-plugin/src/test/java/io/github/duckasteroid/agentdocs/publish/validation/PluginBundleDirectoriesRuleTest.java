package io.github.duckasteroid.agentdocs.publish.validation;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class PluginBundleDirectoriesRuleTest {

    @TempDir
    Path tempDir;

    @Test
    void passesWhenNoPluginIdsAreDeclared() {
        ValidationResult result = new PluginBundleDirectoriesRule().validate(new ValidationContext(tempDir.toFile()));

        assertTrue(result.severity().isEmpty());
    }

    @Test
    void passesWhenEveryDeclaredIdHasItsOwnSubdirectory() throws IOException {
        Files.createDirectories(tempDir.resolve("com.example.first"));
        Files.createDirectories(tempDir.resolve("com.example.second"));

        ValidationResult result = new PluginBundleDirectoriesRule().validate(
                new ValidationContext(tempDir.toFile(), Set.of("com.example.first", "com.example.second")));

        assertTrue(result.severity().isEmpty());
    }

    @Test
    void failsWhenADeclaredIdHasNoSubdirectory() {
        ValidationResult result = new PluginBundleDirectoriesRule().validate(
                new ValidationContext(tempDir.toFile(), Set.of("com.example.missing")));

        assertTrue(result.severity().isPresent());
        assertTrue(result.severity().get() == ValidationSeverity.ERROR);
        assertTrue(result.details().stream().anyMatch(detail -> detail.contains(
                "missing a bundle subdirectory for declared plugin id 'com.example.missing'")));
    }

    @Test
    void failsOnStraySubdirectoryNotMatchingAnyDeclaredId() throws IOException {
        Files.createDirectories(tempDir.resolve("com.example.declared"));
        Files.createDirectories(tempDir.resolve("com.example.typo"));

        ValidationResult result = new PluginBundleDirectoriesRule().validate(
                new ValidationContext(tempDir.toFile(), Set.of("com.example.declared")));

        assertTrue(result.severity().isPresent());
        assertTrue(result.severity().get() == ValidationSeverity.ERROR);
        assertTrue(result.details().stream().anyMatch(
                detail -> detail.contains("'com.example.typo' that doesn't match any plugin id")));
    }

    @Test
    void failsOnTopLevelSkillMdWhenPluginIdsAreDeclared() throws IOException {
        Files.createDirectories(tempDir.resolve("com.example.declared"));
        Files.writeString(tempDir.resolve("SKILL.md"), "---\nname: x\ndescription: y\n---\n");

        ValidationResult result = new PluginBundleDirectoriesRule().validate(
                new ValidationContext(tempDir.toFile(), Set.of("com.example.declared")));

        assertTrue(result.severity().isPresent());
        assertTrue(result.severity().get() == ValidationSeverity.ERROR);
        assertTrue(result.details().stream().anyMatch(detail -> detail.contains("must not contain a top-level SKILL.md")));
    }
}
