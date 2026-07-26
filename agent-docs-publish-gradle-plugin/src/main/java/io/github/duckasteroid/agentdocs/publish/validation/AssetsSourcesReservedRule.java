package io.github.duckasteroid.agentdocs.publish.validation;

import java.io.File;

/**
 * Flags an author-provided {@code assets/sources} entry in the docs root, because {@code
 * agent-docs-resolve-gradle-plugin} reserves that exact path: when a consumer enables {@code
 * includeSources}, it unpacks the dependency's sources jar there, overwriting whatever the author
 * shipped without warning.
 */
public final class AssetsSourcesReservedRule implements AgentDocsValidationRule {
    @Override
    public String id() {
        return "assets-sources-reserved";
    }

    @Override
    public ValidationResult validate(ValidationContext context) {
        File sourcesEntry = new File(new File(context.docsDirectory(), "assets"), "sources");
        if (sourcesEntry.exists()) {
            return ValidationResult.error(id(), "Agent docs directory must not contain 'assets/sources': this path "
                    + "is reserved by agent-docs-resolve-gradle-plugin for unpacking a consumer's includeSources "
                    + "output and would be silently overwritten: " + sourcesEntry);
        }
        return ValidationResult.pass(id());
    }
}
