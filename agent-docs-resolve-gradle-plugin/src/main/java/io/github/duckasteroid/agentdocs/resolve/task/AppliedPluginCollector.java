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
 *
 * <p>Scope is deliberately limited to <strong>binary plugins</strong>: since {@link PluginContainer}
 * is a flat record of every applied plugin regardless of how it got applied, a convention plugin
 * (itself a binary plugin) that internally applies other binary plugins has each of those inner
 * plugins discovered independently, off their own jars. Precompiled script plugins ({@code buildSrc}
 * or an included {@code build-logic} build) are out of scope: their classes resolve to a local
 * build-output jar that was never stamped with an {@code Agent-Docs} attribute by
 * {@code agent-docs.publish}, so they're silently skipped like any undocumented plugin — this isn't
 * a distinct, supported code path.
 *
 * <p>A single jar can register more than one plugin id (a convention plugin jar declaring several
 * {@code gradlePlugin { plugins { ... } } } entries); each applied class in that jar is resolved to
 * its own id independently, and only ids that are actually applied to this project - i.e. present in
 * {@code plugins} - are considered, never every id the jar happens to declare. Per
 * {@code agent-docs.publish}'s layout convention, each id's own bundle always lives at
 * {@code <declared-path>/<pluginId>/} inside the jar (see {@code AgentDocsPublishPlugin}), so the
 * declared jar-level path is suffixed with the resolved plugin id before being handed off for
 * extraction.
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
     * <p>When a jar registers several plugin ids, one {@link DeclaredPlugin} is returned per
     * applied id (not per jar) — each pointing at that id's own {@code <path>/<pluginId>/} bundle —
     * so a jar declaring ids that weren't actually applied to this project never has docs
     * materialized for those unused ids.
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

            Map<Class<?>, String> pluginIdsByClass = findPluginIds(jarPath, entry.getValue(), logger);
            for (Class<?> pluginClass : entry.getValue()) {
                String pluginId = pluginIdsByClass.get(pluginClass);
                if (pluginId == null) {
                    continue;
                }
                AgentDocsDeclaration perPluginDeclaration =
                        new AgentDocsDeclaration(value.scheme(), pluginBundlePath(value.payload(), pluginId));
                declared.add(new DeclaredPlugin(new GradlePluginCoordinate(pluginId), jarPath, perPluginDeclaration));
            }
        }
        return declared;
    }

    /**
     * Suffixes a jar-level declared classpath path with a plugin id's own bundle segment, per
     * {@code agent-docs.publish}'s {@code <path>/<pluginId>/} layout convention for Gradle plugin
     * projects.
     */
    private static String pluginBundlePath(String declaredPath, String pluginId) {
        String basePath = declaredPath != null && !declaredPath.isBlank()
                ? declaredPath
                : AgentDocsDeclaration.DEFAULT_CLASSPATH_PATH;
        String normalizedBase = basePath.endsWith("/") ? basePath : basePath + "/";
        return normalizedBase + pluginId + "/";
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

    /**
     * Resolves each of {@code candidateClasses} to its own plugin id by scanning every {@code
     * META-INF/gradle-plugins/*.properties} descriptor in the jar once — not just the first
     * matching descriptor — so a jar registering several ids has each applied class resolved
     * independently instead of collapsing to a single id for the whole jar.
     *
     * @return applied class to resolved plugin id; a candidate with no matching descriptor is
     *     simply absent (logged at info level), not mapped to {@code null}
     */
    private static Map<Class<?>, String> findPluginIds(Path jarPath, List<Class<?>> candidateClasses, Logger logger) {
        Map<String, Class<?>> candidatesByName = candidateClasses.stream()
                .collect(Collectors.toMap(Class::getName, candidateClass -> candidateClass, (a, b) -> a, LinkedHashMap::new));

        Map<Class<?>, String> resolved = new LinkedHashMap<>();
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
                Class<?> candidateClass = implementationClass != null ? candidatesByName.get(implementationClass.trim()) : null;
                if (candidateClass == null) {
                    continue;
                }
                String fileName = name.substring(GRADLE_PLUGIN_DESCRIPTOR_DIR.length());
                String pluginId = fileName.substring(0, fileName.length() - GRADLE_PLUGIN_DESCRIPTOR_SUFFIX.length());
                resolved.put(candidateClass, pluginId);
            }
        } catch (IOException exception) {
            logger.debug("Unable to read plugin descriptors from {}: {}", jarPath, exception.getMessage());
            return resolved;
        }

        for (Class<?> candidateClass : candidateClasses) {
            if (!resolved.containsKey(candidateClass)) {
                logger.info("Found an embedded Agent-Docs declaration in Gradle plugin jar {} but could not "
                        + "determine a plugin id for applied class {} from its META-INF/gradle-plugins "
                        + "descriptors; skipping", jarPath, candidateClass.getName());
            }
        }
        return resolved;
    }
}
