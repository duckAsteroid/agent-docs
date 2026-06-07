package io.github.duckasteroid.agentdocs.publish.validation;

import org.gradle.api.GradleException;

/**
 * Validates required {@code description} frontmatter presence and length.
 */
public final class SkillDescriptionRule implements AgentDocsValidationRule {
    @Override
    public String id() {
        return "skill-description";
    }

    @Override
    public ValidationResult validate(ValidationContext context) {
        try {
            SkillFrontmatter frontmatter = SkillValidationSupport.parseFrontmatter(context);
            String description = frontmatter.description();
            if (description == null || description.isBlank()) {
                return ValidationResult.error(id(),
                        "SKILL.md frontmatter must define a non-empty 'description' field in: " + context.docsDirectory());
            }
            if (description.length() > 1024) {
                return ValidationResult.error(id(),
                        "SKILL.md frontmatter 'description' must be <= 1024 characters in: " + context.docsDirectory());
            }
            return ValidationResult.pass(id());
        } catch (GradleException exception) {
            return ValidationResult.error(id(), exception.getMessage());
        }
    }
}
