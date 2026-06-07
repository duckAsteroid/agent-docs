package io.github.duckasteroid.agentdocs.resolve;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Locale;

record ModuleCoordinate(String group, String artifact, String version) {
    private static final int MAX_SKILL_NAME_LENGTH = 64;
    private static final int HASH_SUFFIX_LENGTH = 10;
    private static final String FALLBACK_SKILL_NAME = "dep";

    public String gav() {
        return group + ":" + artifact + ":" + version;
    }

    public String skillName() {
        String readable = group + "-" + artifact + "-" + version;
        String normalized = normalizeSkillName(readable);
        if (normalized.length() <= MAX_SKILL_NAME_LENGTH) {
            return normalized;
        }

        String hash = sha256Hex(gav()).substring(0, HASH_SUFFIX_LENGTH);
        int prefixLength = MAX_SKILL_NAME_LENGTH - HASH_SUFFIX_LENGTH - 1;
        String prefix = normalized.substring(0, prefixLength).replaceAll("-+$", "");
        if (prefix.isEmpty()) {
            prefix = FALLBACK_SKILL_NAME;
        }
        return normalizeSkillName(prefix + "-" + hash);
    }

    private String normalizeSkillName(String raw) {
        String normalized = raw.toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9-]", "-")
                .replaceAll("-{2,}", "-")
                .replaceAll("^-+", "")
                .replaceAll("-+$", "");
        return normalized.isEmpty() ? FALLBACK_SKILL_NAME : normalized;
    }

    private String sha256Hex(String value) {
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
