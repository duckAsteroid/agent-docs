package io.github.duckasteroid.agentdocs.publish.validation;

import java.io.File;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;

/**
 * For {@code java-gradle-plugin} projects, ensures the docs root contains exactly one bundle
 * subdirectory per declared plugin id and nothing else - no stray entries, and no top-level
 * {@code SKILL.md} of its own. A no-op when {@link ValidationContext#declaredPluginIds()} is
 * empty (not a Gradle plugin project).
 */
public final class PluginBundleDirectoriesRule implements AgentDocsValidationRule {
    @Override
    public String id() {
        return "plugin-bundle-directories";
    }

    @Override
    public ValidationResult validate(ValidationContext context) {
        Set<String> declaredPluginIds = context.declaredPluginIds();
        if (declaredPluginIds.isEmpty()) {
            return ValidationResult.pass(id());
        }

        List<String> problems = new ArrayList<>();
        Set<String> presentDirectoryNames = new LinkedHashSet<>();
        for (File entry : context.rootEntries()) {
            if (entry.isDirectory()) {
                presentDirectoryNames.add(entry.getName());
            } else if (entry.getName().equalsIgnoreCase("skill.md")) {
                problems.add("Agent docs directory must not contain a top-level SKILL.md when java-gradle-plugin "
                        + "declares multiple plugin ids; each plugin's own SKILL.md must live under its own "
                        + "agent-docs/<pluginId>/ subdirectory: " + entry);
            }
        }

        for (String missingId : new TreeSet<>(setDifference(declaredPluginIds, presentDirectoryNames))) {
            problems.add("Agent docs directory is missing a bundle subdirectory for declared plugin id '"
                    + missingId + "': expected " + new File(context.docsDirectory(), missingId));
        }

        for (String strayDirectory : new TreeSet<>(setDifference(presentDirectoryNames, declaredPluginIds))) {
            problems.add("Agent docs directory contains subdirectory '" + strayDirectory
                    + "' that doesn't match any plugin id declared via gradlePlugin { plugins { ... } }: "
                    + new File(context.docsDirectory(), strayDirectory));
        }

        if (problems.isEmpty()) {
            return ValidationResult.pass(id());
        }
        return new ValidationResult(id(), Optional.of(ValidationSeverity.ERROR), problems);
    }

    private static Set<String> setDifference(Set<String> left, Set<String> right) {
        Set<String> difference = new LinkedHashSet<>(left);
        difference.removeAll(right);
        return difference;
    }
}
