package io.github.duckasteroid.agentdocs.resolve.task;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
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
     * Loads a text template from classpath resources.
     *
     * @param classLoader classloader used to resolve resources
     * @param resourcePath classpath-relative resource path
     * @return UTF-8 decoded template content
     * @throws IOException when the resource is missing or unreadable
     */
    static String loadTemplate(ClassLoader classLoader, String resourcePath) throws IOException {
        try (InputStream templateStream = classLoader.getResourceAsStream(resourcePath)) {
            if (templateStream == null) {
                throw new IOException("Unable to load skill template resource: " + resourcePath);
            }
            return new String(templateStream.readAllBytes(), StandardCharsets.UTF_8);
        }
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

    /**
     * Converts a path into a normalized forward-slash relative path.
     *
     * @param fromDirectory base directory
     * @param toPath target path
     * @return relative path with {@code /} separators
     */
    static String toRelativePath(Path fromDirectory, Path toPath) {
        return fromDirectory.relativize(toPath).toString().replace('\\', '/');
    }
}
