package io.github.duckasteroid.agentdocs.publish.validation;

/**
 * Ensures the configured docs directory exists.
 */
public final class DocsDirectoryExistsRule implements AgentDocsValidationRule {
    @Override
    public String id() {
        return "docs-directory-exists";
    }

    @Override
    public ValidationResult validate(ValidationContext context) {
        if (!context.docsDirectory().exists()) {
            return ValidationResult.error(id(), "Agent docs directory does not exist: " + context.docsDirectory());
        }
        return ValidationResult.pass(id());
    }
}
