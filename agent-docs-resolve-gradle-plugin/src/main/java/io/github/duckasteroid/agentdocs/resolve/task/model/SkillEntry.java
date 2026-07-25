package io.github.duckasteroid.agentdocs.resolve.task.model;

import java.nio.file.Path;

/**
 * Resolved dependency documentation entry consumed by skill generation.
 *
 * @param coordinate dependency coordinate represented by this entry
 * @param entrypointPath extracted {@code SKILL.md} path for the dependency docs
 * @param skillName assigned skill-folder name for this entry (see {@code SkillNameAssigner});
 *     not necessarily {@code coordinate.skillName()} - may be a shorter, collision-free tier
 */
public record SkillEntry(ModuleCoordinate coordinate, Path entrypointPath, String skillName) {
}
