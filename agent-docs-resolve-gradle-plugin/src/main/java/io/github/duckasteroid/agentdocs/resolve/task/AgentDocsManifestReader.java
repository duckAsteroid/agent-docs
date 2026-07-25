package io.github.duckasteroid.agentdocs.resolve.task;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Optional;
import java.util.jar.JarFile;
import java.util.jar.Manifest;

import org.gradle.api.logging.Logger;

/**
 * Reads the single {@code Agent-Docs} manifest attribute from a jar, per
 * {@code specification/java-conventions.md}.
 */
public final class AgentDocsManifestReader {
    private static final String MANIFEST_ATTRIBUTE = "Agent-Docs";

    private AgentDocsManifestReader() {
    }

    /**
     * Reads and parses the {@code Agent-Docs} attribute from a jar's manifest.
     *
     * @param jarPath path to the jar file
     * @param logger logger for diagnostics
     * @return parsed declaration, or empty when absent, blank, unrecognised, or unreadable
     */
    public static Optional<AgentDocsDeclaration> read(Path jarPath, Logger logger) {
        if (!Files.isRegularFile(jarPath)) {
            return Optional.empty();
        }

        Manifest manifest;
        try (JarFile jarFile = new JarFile(jarPath.toFile())) {
            manifest = jarFile.getManifest();
        } catch (IOException exception) {
            logger.debug("Unable to read manifest from {}: {}", jarPath, exception.getMessage());
            return Optional.empty();
        }

        if (manifest == null) {
            return Optional.empty();
        }

        String value = manifest.getMainAttributes().getValue(MANIFEST_ATTRIBUTE);
        if (value == null || value.isBlank()) {
            return Optional.empty();
        }

        String trimmed = value.trim();
        int separator = trimmed.indexOf(':');
        String scheme = (separator < 0 ? trimmed : trimmed.substring(0, separator)).trim().toLowerCase(Locale.ROOT);
        String payload = separator < 0 ? null : trimmed.substring(separator + 1).trim();
        if (payload != null && payload.isBlank()) {
            payload = null;
        }

        if (!scheme.equals(AgentDocsDeclaration.SCHEME_CLASSPATH) && !scheme.equals(AgentDocsDeclaration.SCHEME_MAVEN)) {
            logger.info("Unrecognised Agent-Docs value '{}' in {}; treating as absent", value, jarPath);
            return Optional.empty();
        }

        return Optional.of(new AgentDocsDeclaration(scheme, payload));
    }
}
