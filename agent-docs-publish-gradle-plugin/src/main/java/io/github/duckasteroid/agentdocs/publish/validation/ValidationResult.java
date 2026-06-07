package io.github.duckasteroid.agentdocs.publish.validation;

import java.util.List;
import java.util.Optional;

/**
 * Result of evaluating a single validation rule.
 *
 * @param ruleId stable rule identifier
 * @param severity optional severity for non-pass outcomes
 * @param details validation details or reasons (empty for pass)
 */
public record ValidationResult(String ruleId, Optional<ValidationSeverity> severity, List<String> details) {
    /**
     * Creates a pass result with no severity.
     *
     * @param ruleId rule identifier
     * @return pass result
     */
    public static ValidationResult pass(String ruleId) {
        return new ValidationResult(ruleId, Optional.empty(), List.of());
    }

    /**
     * Creates an error result.
     *
     * @param ruleId rule identifier
     * @param detail error detail
     * @return error result
     */
    public static ValidationResult error(String ruleId, String detail) {
        return new ValidationResult(ruleId, Optional.of(ValidationSeverity.ERROR), List.of(detail));
    }

    /**
     * Creates a warning result.
     *
     * @param ruleId rule identifier
     * @param detail warning detail
     * @return warning result
     */
    public static ValidationResult warn(String ruleId, String detail) {
        return new ValidationResult(ruleId, Optional.of(ValidationSeverity.WARN), List.of(detail));
    }
}
