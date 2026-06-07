package io.github.duckasteroid.agentdocs.publish.validation;

import org.gradle.api.GradleException;

/**
 * Validates optional {@code compatibility} frontmatter length.
 */
public final class SkillCompatibilityRule implements AgentDocsValidationRule {
    @Override
    public String id() {
        return "skill-compatibility";
    }

    @Override
    public ValidationResult validate(ValidationContext context) {
        try {
            String compatibility = SkillValidationSupport.parseFrontmatter(context).compatibility();
            if (compatibility == null || compatibility.isBlank()) {
                return ValidationResult.pass(id());
            }
            if (compatibility.length() > 500) {
                return ValidationResult.error(id(),
                        "SKILL.md frontmatter 'compatibility' must be <= 500 characters in: " + context.docsDirectory());
            }
            return ValidationResult.pass(id());
        } catch (GradleException exception) {
            return ValidationResult.error(id(), exception.getMessage());
        }
    }
}
