package io.github.duckasteroid.agentdocs.publish.validation;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import org.gradle.api.GradleException;

/**
 * Shared parsing helpers used by multiple validation rules.
 */
final class SkillValidationSupport {
    private SkillValidationSupport() {
    }

    /**
     * Counts root-level {@code SKILL.md} candidates (case-insensitive).
     *
     * @param context validation context
     * @return entrypoint candidate count
     */
    static long skillEntrypointCount(ValidationContext context) {
        return Arrays.stream(context.rootEntries())
                .filter(File::isFile)
                .map(File::getName)
                .filter(fileName -> fileName.equalsIgnoreCase("skill.md"))
                .count();
    }

    /**
     * Returns the single root-level {@code SKILL.md} candidate when present.
     *
     * @param context validation context
     * @return optional entrypoint file
     */
    static Optional<File> singleSkillEntrypoint(ValidationContext context) {
        return Arrays.stream(context.rootEntries())
                .filter(File::isFile)
                .filter(file -> file.getName().equalsIgnoreCase("skill.md"))
                .findFirst();
    }

    /**
     * Parses {@code SKILL.md} frontmatter and returns known fields.
     *
     * @param context validation context
     * @return parsed frontmatter fields
     */
    static SkillFrontmatter parseFrontmatter(ValidationContext context) {
        File skillEntrypoint = singleSkillEntrypoint(context)
                .orElseThrow(() -> new GradleException(
                        "Agent docs directory must contain SKILL.md (case-insensitive): " + context.docsDirectory()));

        String content;
        try {
            content = Files.readString(skillEntrypoint.toPath());
        } catch (IOException exception) {
            throw new GradleException("Unable to read SKILL.md entrypoint: " + skillEntrypoint, exception);
        }

        String normalized = content.replace("\r\n", "\n");
        if (!normalized.startsWith("---\n")) {
            throw new GradleException("SKILL.md must start with YAML frontmatter delimited by --- in: " + context.docsDirectory());
        }

        int closingIndex = normalized.indexOf("\n---\n", 4);
        if (closingIndex < 0) {
            throw new GradleException(
                    "SKILL.md frontmatter must end with a closing --- delimiter in: " + context.docsDirectory());
        }

        String frontmatterBlock = normalized.substring(4, closingIndex);
        Map<String, String> fields = new LinkedHashMap<>();
        for (String line : frontmatterBlock.split("\n")) {
            String trimmed = line.trim();
            if (trimmed.isEmpty() || trimmed.startsWith("#")) {
                continue;
            }

            int separator = trimmed.indexOf(':');
            if (separator <= 0) {
                continue;
            }

            String key = trimmed.substring(0, separator).trim();
            String value = trimmed.substring(separator + 1).trim();
            if ((value.startsWith("\"") && value.endsWith("\"")) || (value.startsWith("'") && value.endsWith("'"))) {
                value = value.substring(1, value.length() - 1).trim();
            }
            fields.put(key, value);
        }

        return new SkillFrontmatter(fields.get("name"), fields.get("description"), fields.get("compatibility"));
    }
}
