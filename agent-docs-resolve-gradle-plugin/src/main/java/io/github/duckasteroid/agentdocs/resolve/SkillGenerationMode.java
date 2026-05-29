package io.github.duckasteroid.agentdocs.resolve;

/**
 * Supported strategies for generating local agent skill files.
 */
public enum SkillGenerationMode {
    /** Generate a single router skill containing entries for all resolved dependencies. */
    SINGLE_INDEX,
    /** Generate one skill file per resolved dependency. */
    PER_DEPENDENCY,
    /** Choose between {@code SINGLE_INDEX} and {@code PER_DEPENDENCY} based on configured threshold. */
    AUTO_THRESHOLD;

    /**
     * Parses configured mode values and common aliases.
     */
    static SkillGenerationMode from(String value) {
        if (value == null || value.isBlank()) {
            return SINGLE_INDEX;
        }

        String normalized = value.trim().replace('-', '_').toUpperCase();
        return switch (normalized) {
            case "SINGLE", "SINGLE_INDEX" -> SINGLE_INDEX;
            case "PER_DEPENDENCY", "PERDEPENDENCY" -> PER_DEPENDENCY;
            case "AUTO", "AUTO_THRESHOLD", "THRESHOLD" -> AUTO_THRESHOLD;
            default -> throw new IllegalArgumentException(
                    "Unsupported agentDocsResolve.skillGenerationMode: " + value
                            + ". Supported values: SINGLE_INDEX, PER_DEPENDENCY, AUTO_THRESHOLD");
        };
    }
}

