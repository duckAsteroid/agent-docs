package io.github.duckasteroid.agentdocs.resolve;

import java.nio.file.Path;

record SkillEntry(ModuleCoordinate coordinate, Path entrypointPath) {
}
