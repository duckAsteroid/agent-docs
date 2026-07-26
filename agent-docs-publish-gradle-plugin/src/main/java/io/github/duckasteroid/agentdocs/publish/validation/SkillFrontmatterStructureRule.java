package io.github.duckasteroid.agentdocs.publish.validation;

import org.gradle.api.GradleException;

/**
 * Ensures {@code SKILL.md} frontmatter, when present, is well-formed. Frontmatter itself is
 * optional per the Agent Docs convention (the resolver generates it at extraction time when
 * absent) — this rule only rejects a frontmatter block that was opened with {@code ---} but never
 * closed, since the resolver would silently fail to rewrite that file.
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
