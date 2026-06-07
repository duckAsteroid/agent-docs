package io.github.duckasteroid.agentdocs.resolve.task;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ResolveFilesystemSupportTest {
    @TempDir
    Path tempDir;

    @Test
    void loadTemplateReadsClasspathResource() throws IOException {
        String content = ResolveFilesystemSupport.loadTemplate(
                getClass().getClassLoader(), "agent-docs-dependency-skill-template.md");
        assertTrue(content.contains("{{entrypoint}}"));
    }

    @Test
    void loadTemplateFailsForMissingResource() {
        assertThrows(
                IOException.class,
                () -> ResolveFilesystemSupport.loadTemplate(getClass().getClassLoader(), "missing-resource.md"));
    }

    @Test
    void deleteDirectoryRemovesNestedTree() throws IOException {
        Path nested = tempDir.resolve("a/b/c");
        Files.createDirectories(nested);
        Files.writeString(nested.resolve("file.txt"), "data");

        ResolveFilesystemSupport.deleteDirectory(tempDir.resolve("a"));

        assertTrue(Files.notExists(tempDir.resolve("a")));
    }

    @Test
    void writeOwnershipMarkerCreatesFile() throws IOException {
        Path directory = tempDir.resolve("skill");
        Files.createDirectories(directory);

        ResolveFilesystemSupport.writeOwnershipMarker(directory, ".agent-docs");

        assertTrue(Files.exists(directory.resolve(".agent-docs")));
    }
}
