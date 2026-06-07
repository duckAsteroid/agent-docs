package io.github.duckasteroid.agentdocs.publish.validation;

/**
 * Parsed subset of {@code SKILL.md} frontmatter fields used by validation rules.
 *
 * @param name optional skill name
 * @param description required skill description
 * @param compatibility optional environment compatibility note
 */
record SkillFrontmatter(String name, String description, String compatibility) {
}
