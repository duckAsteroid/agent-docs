package io.github.duckasteroid.agentdocs.publish.validation;

import java.io.File;
import java.util.Arrays;

/**
 * Ensures standard optional skill directories are directories when present.
 */
public final class StandardDirectoriesRule implements AgentDocsValidationRule {
    @Override
    public String id() {
        return "standard-directories";
    }

    @Override
    public ValidationResult validate(ValidationContext context) {
        for (String standardDirectoryName : new String[] {"scripts", "references", "assets"}) {
            File entry = Arrays.stream(context.rootEntries())
                    .filter(file -> file.getName().equals(standardDirectoryName))
                    .findFirst()
                    .orElse(null);
            if (entry != null && !entry.isDirectory()) {
                return ValidationResult.error(id(), "Agent docs standard directory '" + standardDirectoryName
                        + "' must be a directory when present: " + context.docsDirectory());
            }
        }
        return ValidationResult.pass(id());
    }
}
