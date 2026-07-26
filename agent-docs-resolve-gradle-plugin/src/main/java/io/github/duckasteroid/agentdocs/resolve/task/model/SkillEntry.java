package io.github.duckasteroid.agentdocs.resolve.task.model;

import java.nio.file.Path;

/**
 * Resolved documentation entry consumed by skill generation.
 *
 * @param source the dependency ({@link ModuleCoordinate}) or Gradle plugin
 *     ({@link GradlePluginCoordinate}) this entry documents
 * @param entrypointPath extracted {@code SKILL.md} path for the docs
 * @param skillName assigned skill-folder name for this entry (see {@code SkillNameAssigner});
 *     not necessarily {@code source.skillName()} - may be a shorter, collision-free tier
 */
public record SkillEntry(SkillSource source, Path entrypointPath, String skillName) {
}
