package io.github.duckasteroid.agentdocs.resolve.task;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.CodeSource;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;
import java.util.Set;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.stream.Collectors;

import io.github.duckasteroid.agentdocs.resolve.task.model.GradlePluginCoordinate;
import org.gradle.api.Plugin;
import org.gradle.api.logging.Logger;
import org.gradle.api.plugins.PluginContainer;

/**
 * Discovers {@code Agent-Docs}-aware Gradle plugins applied via the {@code plugins {}} block.
 *
 * <p>Unlike regular dependencies, plugins applied this way aren't resolved onto a project
 * {@link org.gradle.api.artifacts.Configuration} — there's no Maven GAV to read off a resolved
 * component. Instead, each applied {@link Plugin}'s own class reveals the jar it was loaded from
 * (via its classloader's code source), which is exactly where publish-side tooling would have
 * stamped the {@code Agent-Docs} manifest attribute (per {@code specification/java-conventions.md})
 * when embedding docs — the only distribution mode supported for Gradle plugin projects.
 *
 * <p>The plugin id itself is recovered from the same jar's {@code META-INF/gradle-plugins/*.properties}
 * descriptors (the standard mechanism {@code java-gradle-plugin} generates and Gradle itself uses
 * to resolve {@code id '...'} to an implementation class), matched back to the applied class.
 */
public final class AppliedPluginCollector {
    private static final String GRADLE_PLUGIN_DESCRIPTOR_DIR = "META-INF/gradle-plugins/";
    private static final String GRADLE_PLUGIN_DESCRIPTOR_SUFFIX = ".properties";
    private static final String IMPLEMENTATION_CLASS_KEY = "implementation-class";

    private AppliedPluginCollector() {
    }

    /**
     * Discovered {@code Agent-Docs}-aware Gradle plugin, analogous to a declared dependency.
     *
     * @param coordinate the plugin's identity
     * @param jarPath the plugin's own resolved jar (from its classloader's code source)
     * @param declaration the plugin jar's {@code Agent-Docs} declaration
     */
    public record DeclaredPlugin(GradlePluginCoordinate coordinate, Path jarPath, AgentDocsDeclaration declaration) {
    }

    /**
     * Collects every applied plugin whose own jar carries an {@code Agent-Docs} manifest
     * declaration, resolving each one's plugin id along the way.
     *
     * <p>Plugins without the attribute (essentially every core Gradle plugin, and any third-party
     * plugin that hasn't adopted the convention) are skipped entirely — no different from a
     * regular dependency without the attribute. A non-{@code classpath} scheme (there is no
     * consumer-side resolution path for a {@code maven} sidecar here, since plugin jars aren't
     * resolved as project dependencies) is skipped with a warning. A jar carrying the attribute
     * whose plugin id can't be determined from its descriptors is skipped with an info-level log,
     * since it's not actionable by the consumer.
     *
     * @param plugins the project's applied plugins
     * @param logger logger for diagnostics
     * @return discovered plugins with a usable {@code classpath} declaration
     */
    public static List<DeclaredPlugin> collect(PluginContainer plugins, Logger logger) {
        Map<Path, List<Class<?>>> classesByJar = new LinkedHashMap<>();
        for (Plugin<?> plugin : plugins) {
            Class<?> pluginClass = plugin.getClass();
            Path jarPath = jarPathOf(pluginClass);
            if (jarPath == null) {
                continue;
            }
            classesByJar.computeIfAbsent(jarPath, ignored -> new ArrayList<>()).add(pluginClass);
        }

        List<DeclaredPlugin> declared = new ArrayList<>();
        for (Map.Entry<Path, List<Class<?>>> entry : classesByJar.entrySet()) {
            Path jarPath = entry.getKey();
            Optional<AgentDocsDeclaration> declaration = AgentDocsManifestReader.read(jarPath, logger);
            if (declaration.isEmpty()) {
                continue;
            }

            AgentDocsDeclaration value = declaration.get();
            if (!AgentDocsDeclaration.SCHEME_CLASSPATH.equals(value.scheme())) {
                logger.warn("Ignoring Agent-Docs '{}' declaration on Gradle plugin jar {}; only the 'classpath' "
                        + "scheme is supported for plugins applied via plugins {{}} - a maven sidecar has no "
                        + "consumer-side resolution path here", value.scheme(), jarPath);
                continue;
            }

            String pluginId = findPluginId(jarPath, entry.getValue(), logger);
            if (pluginId == null) {
                continue;
            }
            declared.add(new DeclaredPlugin(new GradlePluginCoordinate(pluginId), jarPath, value));
        }
        return declared;
    }

    private static Path jarPathOf(Class<?> pluginClass) {
        CodeSource codeSource = pluginClass.getProtectionDomain().getCodeSource();
        if (codeSource == null) {
            return null;
        }
        URL location = codeSource.getLocation();
        if (location == null) {
            return null;
        }
        try {
            Path path = Path.of(new URI(location.toString()));
            return Files.isRegularFile(path) ? path : null;
        } catch (URISyntaxException | IllegalArgumentException exception) {
            return null;
        }
    }

    private static String findPluginId(Path jarPath, List<Class<?>> candidateClasses, Logger logger) {
        Set<String> candidateNames = candidateClasses.stream().map(Class::getName).collect(Collectors.toSet());
        try (JarFile jarFile = new JarFile(jarPath.toFile())) {
            Enumeration<JarEntry> entries = jarFile.entries();
            while (entries.hasMoreElements()) {
                JarEntry entry = entries.nextElement();
                String name = entry.getName();
                if (!name.startsWith(GRADLE_PLUGIN_DESCRIPTOR_DIR) || !name.endsWith(GRADLE_PLUGIN_DESCRIPTOR_SUFFIX)) {
                    continue;
                }
                Properties properties = new Properties();
                try (InputStream in = jarFile.getInputStream(entry)) {
                    properties.load(in);
                }
                String implementationClass = properties.getProperty(IMPLEMENTATION_CLASS_KEY);
                if (implementationClass != null && candidateNames.contains(implementationClass.trim())) {
                    String fileName = name.substring(GRADLE_PLUGIN_DESCRIPTOR_DIR.length());
                    return fileName.substring(0, fileName.length() - GRADLE_PLUGIN_DESCRIPTOR_SUFFIX.length());
                }
            }
        } catch (IOException exception) {
            logger.debug("Unable to read plugin descriptors from {}: {}", jarPath, exception.getMessage());
        }
        logger.info("Found an embedded Agent-Docs declaration in Gradle plugin jar {} but could not determine its "
                + "plugin id from its META-INF/gradle-plugins descriptors; skipping", jarPath);
        return null;
    }
}
