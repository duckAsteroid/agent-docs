package io.github.duckasteroid.agentdocs.resolve.task.model;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Locale;

/**
 * Shared normalization and truncation rules used by every {@link SkillSource} implementation to
 * turn a raw candidate key into a skill-folder-compatible name.
 */
final class SkillNameNormalization {
    private static final int MAX_SKILL_NAME_LENGTH = 64;
    private static final int HASH_SUFFIX_LENGTH = 10;

    private SkillNameNormalization() {
    }

    /**
     * Lowercases, restricts to {@code [a-z0-9-]}, collapses repeats, and strips leading/trailing
     * hyphens.
     *
     * @param raw candidate text
     * @param fallback name to use if normalization empties the string entirely
     * @return normalized candidate key
     */
    static String normalize(String raw, String fallback) {
        String normalized = raw.toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9-]", "-")
                .replaceAll("-{2,}", "-")
                .replaceAll("^-+", "")
                .replaceAll("-+$", "");
        return normalized.isEmpty() ? fallback : normalized;
    }

    /**
     * Truncates a normalized candidate key to the skill-name length limit, appending a
     * deterministic hash suffix (seeded by {@code hashSeed}) when truncation was needed.
     *
     * @param normalizedKey already-normalized candidate key
     * @param hashSeed stable text to derive the truncation hash suffix from
     * @param fallback name to use if truncation empties the prefix entirely
     * @return skill-folder-compatible name
     */
    static String finalizeSkillName(String normalizedKey, String hashSeed, String fallback) {
        if (normalizedKey.length() <= MAX_SKILL_NAME_LENGTH) {
            return normalizedKey;
        }

        String hash = sha256Hex(hashSeed).substring(0, HASH_SUFFIX_LENGTH);
        int prefixLength = MAX_SKILL_NAME_LENGTH - HASH_SUFFIX_LENGTH - 1;
        String prefix = normalizedKey.substring(0, prefixLength).replaceAll("-+$", "");
        if (prefix.isEmpty()) {
            prefix = fallback;
        }
        return normalize(prefix + "-" + hash, fallback);
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
