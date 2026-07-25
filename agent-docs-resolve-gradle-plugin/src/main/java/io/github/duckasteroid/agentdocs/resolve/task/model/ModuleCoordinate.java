package io.github.duckasteroid.agentdocs.resolve.task.model;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Locale;

/**
 * Represents a Maven GAV coordinate for a library/dependency.
 */
public record ModuleCoordinate(String group, String artifact, String version) {
    private static final int MAX_SKILL_NAME_LENGTH = 64;
    private static final int HASH_SUFFIX_LENGTH = 10;
    private static final String FALLBACK_SKILL_NAME = "dep";

    /**
     * Returns canonical Maven GAV text form.
     *
     * @return {@code group:artifact:version}
     */
    public String gav() {
        return group + ":" + artifact + ":" + version;
    }

    /**
     * Generates a skill-folder-compatible identifier derived from the full GAV. This is the
     * last-resort tier used by {@code SkillNameAssigner} only when a shorter, more readable
     * candidate (see {@link #artifactNameKey()}, {@link #groupArtifactNameKey()}) collides with
     * another coordinate being resolved in the same run.
     *
     * <p>The generated name is deterministic, lower-case, and constrained to skill naming
     * requirements. Names longer than the maximum length are truncated with a hash suffix.
     *
     * @return normalized skill folder name
     */
    public String skillName() {
        return finalizeSkillName(normalizeSkillName(group + "-" + artifact + "-" + version));
    }

    /**
     * Untruncated, normalized candidate skill name using only the artifact name — the shortest
     * and most readable tier. Multiple coordinates with different groups can share the same
     * artifact name, so callers must check for collisions across the full candidate set before
     * relying on this tier (see {@code SkillNameAssigner}).
     *
     * @return normalized artifact-only candidate key
     */
    public String artifactNameKey() {
        return normalizeSkillName(artifact);
    }

    /**
     * Untruncated, normalized candidate skill name using group and artifact — the fallback tier
     * when {@link #artifactNameKey()} collides with another coordinate. Two different versions of
     * the same module would still collide at this tier, but a single resolved configuration only
     * ever selects one version per group:artifact, so this is effectively unique in practice.
     *
     * @return normalized group-artifact candidate key
     */
    public String groupArtifactNameKey() {
        return normalizeSkillName(group + "-" + artifact);
    }

    /**
     * Applies the length-limit and deterministic hash-suffix rule to a chosen, already-normalized
     * candidate key (from {@link #artifactNameKey()}, {@link #groupArtifactNameKey()}, or a raw
     * {@code group-artifact-version} string), producing the final skill folder name.
     *
     * @param normalizedKey a normalized candidate key
     * @return skill-folder-compatible name, truncated with a hash suffix if needed
     */
    public String finalizeSkillName(String normalizedKey) {
        if (normalizedKey.length() <= MAX_SKILL_NAME_LENGTH) {
            return normalizedKey;
        }

        String hash = sha256Hex(gav()).substring(0, HASH_SUFFIX_LENGTH);
        int prefixLength = MAX_SKILL_NAME_LENGTH - HASH_SUFFIX_LENGTH - 1;
        String prefix = normalizedKey.substring(0, prefixLength).replaceAll("-+$", "");
        if (prefix.isEmpty()) {
            prefix = FALLBACK_SKILL_NAME;
        }
        return normalizeSkillName(prefix + "-" + hash);
    }

    private static String normalizeSkillName(String raw) {
        String normalized = raw.toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9-]", "-")
                .replaceAll("-{2,}", "-")
                .replaceAll("^-+", "")
                .replaceAll("-+$", "");
        return normalized.isEmpty() ? FALLBACK_SKILL_NAME : normalized;
    }

    private static String sha256Hex(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder builder = new StringBuilder(hash.length * 2);
            for (byte b : hash) {
                builder.append(String.format("%02x", b));
            }
            return builder.toString();
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is not available", exception);
        }
    }
}
