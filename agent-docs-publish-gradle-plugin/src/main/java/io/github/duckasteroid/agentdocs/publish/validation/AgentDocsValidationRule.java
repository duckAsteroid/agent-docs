package io.github.duckasteroid.agentdocs.publish.validation;

/**
 * Contract for a skill validation rule.
 */
public interface AgentDocsValidationRule {
    /**
     * Stable rule identifier used for opt-out configuration.
     *
     * @return rule ID
     */
    String id();

    /**
     * Validates the docs using the provided context.
     *
     * @param context validation context
     * @return validation result including rule ID and any failure details
     */
    ValidationResult validate(ValidationContext context);
}
