package io.github.duckasteroid.agentdocs.publish.validation;

import org.gradle.api.GradleException;

/**
 * Validates optional {@code description} frontmatter length.
 *
 * <p>Presence is not required: per the Agent Docs convention, an author's {@code description} is
 * appended to a resolver-generated prefix when present, so omitting it entirely (or omitting
 * frontmatter altogether) is valid and the resolver's generated description still applies.
 *
 * <p>The cap is well under the Agent Skills format's own 1024-character description limit,
 * reserving headroom for the resolver-generated prefix sentence prepended ahead of the author's
 * text (see {@code SkillDirectoryManager.buildDescription} in the resolve plugin). This rule
 * deliberately doesn't compute the resolver's exact prefix length itself - that would couple the
 * publish and resolve plugins' string formats together - it just reserves a conservative fixed
 * margin comfortably larger than any prefix the resolver is expected to generate.
 */
public final class SkillDescriptionRule implements AgentDocsValidationRule {
    private static final int MAX_DESCRIPTION_LENGTH = 700;

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
                return ValidationResult.pass(id());
            }
            if (description.length() > MAX_DESCRIPTION_LENGTH) {
                return ValidationResult.error(id(), "SKILL.md frontmatter 'description' must be <= "
                        + MAX_DESCRIPTION_LENGTH + " characters (leaving headroom for the resolver-generated "
                        + "prefix within the Agent Skills format's 1024-character description limit) in: "
                        + context.docsDirectory());
            }
            return ValidationResult.pass(id());
        } catch (GradleException exception) {
            return ValidationResult.error(id(), exception.getMessage());
        }
    }
}
