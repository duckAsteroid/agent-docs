package io.github.duckasteroid.agentdocs.resolve.task.model;

import java.nio.file.Path;

/**
 * Resolved dependency documentation entry consumed by skill generation.
 *
 * @param coordinate dependency coordinate represented by this entry
 * @param entrypointPath extracted {@code SKILL.md} path for the dependency docs
 */
public record SkillEntry(ModuleCoordinate coordinate, Path entrypointPath) {
}
