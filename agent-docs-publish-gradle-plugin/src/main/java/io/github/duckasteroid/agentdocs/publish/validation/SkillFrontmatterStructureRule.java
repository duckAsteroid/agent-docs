package io.github.duckasteroid.agentdocs.publish.validation;

import org.gradle.api.GradleException;

/**
 * Ensures {@code SKILL.md} contains parseable YAML frontmatter.
 */
public final class SkillFrontmatterStructureRule implements AgentDocsValidationRule {
    @Override
    public String id() {
        return "skill-frontmatter-structure";
    }

    @Override
    public ValidationResult validate(ValidationContext context) {
        try {
            SkillValidationSupport.parseFrontmatter(context);
            return ValidationResult.pass(id());
        } catch (GradleException exception) {
            return ValidationResult.error(id(), exception.getMessage());
        }
    }
}
