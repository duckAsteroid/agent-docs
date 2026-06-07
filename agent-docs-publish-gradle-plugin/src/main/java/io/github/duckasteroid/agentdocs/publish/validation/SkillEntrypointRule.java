package io.github.duckasteroid.agentdocs.publish.validation;

/**
 * Ensures exactly one {@code SKILL.md} exists at docs root.
 */
public final class SkillEntrypointRule implements AgentDocsValidationRule {
    @Override
    public String id() {
        return "skill-entrypoint";
    }

    @Override
    public ValidationResult validate(ValidationContext context) {
        long count = SkillValidationSupport.skillEntrypointCount(context);
        if (count == 0) {
            return ValidationResult.error(id(), "Agent docs directory must contain SKILL.md (case-insensitive): " + context.docsDirectory());
        }
        if (count > 1) {
            return ValidationResult.error(id(),
                    "Agent docs directory must contain exactly one SKILL.md file (case-insensitive): " + context.docsDirectory());
        }
        return ValidationResult.pass(id());
    }
}
