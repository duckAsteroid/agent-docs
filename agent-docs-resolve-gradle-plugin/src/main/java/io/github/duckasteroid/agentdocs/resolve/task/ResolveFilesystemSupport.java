package io.github.duckasteroid.agentdocs.resolve.task;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.stream.Stream;

/**
 * Shared file and resource helpers for resolver-side task collaborators.
 */
final class ResolveFilesystemSupport {
    private ResolveFilesystemSupport() {
    }

    /**
     * Recursively deletes a directory tree when it exists.
     *
     * @param directory directory to remove
     * @throws IOException when deletion fails
     */
    static void deleteDirectory(Path directory) throws IOException {
        if (Files.notExists(directory)) {
            return;
        }

        try (Stream<Path> stream = Files.walk(directory)) {
            stream.sorted(Comparator.reverseOrder()).forEach(path -> {
                try {
                    Files.delete(path);
                } catch (IOException exception) {
                    throw new RuntimeException(exception);
                }
            });
        } catch (RuntimeException exception) {
            if (exception.getCause() instanceof IOException ioException) {
                throw ioException;
            }
            throw exception;
        }
    }

    /**
     * Writes an empty ownership marker in the provided directory.
     *
     * @param directory parent directory
     * @param markerFilename marker file name
     * @throws IOException when writing fails
     */
    static void writeOwnershipMarker(Path directory, String markerFilename) throws IOException {
        Files.writeString(directory.resolve(markerFilename), "");
    }

}
