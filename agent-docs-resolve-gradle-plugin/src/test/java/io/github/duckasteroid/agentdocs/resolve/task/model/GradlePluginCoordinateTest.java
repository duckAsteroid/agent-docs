package io.github.duckasteroid.agentdocs.resolve.task.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class GradlePluginCoordinateTest {
    @Test
    void describeReturnsPluginPrefixedId() {
        GradlePluginCoordinate coordinate = new GradlePluginCoordinate("io.github.duckasteroid.agent-docs.publish");
        assertEquals("plugin:io.github.duckasteroid.agent-docs.publish", coordinate.describe());
    }

    @Test
    void artifactNameKeyUsesLastDottedSegment() {
        GradlePluginCoordinate coordinate = new GradlePluginCoordinate("io.github.duckasteroid.agent-docs.publish");
        assertEquals("publish", coordinate.artifactNameKey());
    }

    @Test
    void artifactNameKeyHandlesIdWithNoDots() {
        GradlePluginCoordinate coordinate = new GradlePluginCoordinate("checkstyle");
        assertEquals("checkstyle", coordinate.artifactNameKey());
    }

    @Test
    void groupArtifactNameKeyNormalizesFullId() {
        GradlePluginCoordinate coordinate = new GradlePluginCoordinate("io.github.duckasteroid.agent-docs.publish");
        assertEquals("io-github-duckasteroid-agent-docs-publish", coordinate.groupArtifactNameKey());
    }

    @Test
    void skillNameNormalizesAndConstrainsOutput() {
        GradlePluginCoordinate coordinate = new GradlePluginCoordinate("io.github.Example.MyPlugin_Thing");
        String skillName = coordinate.skillName();

        assertTrue(skillName.matches("^[a-z0-9]+(?:-[a-z0-9]+)*$"));
        assertTrue(skillName.length() <= 64);
    }

    @Test
    void skillNameUsesStableHashSuffixWhenTruncated() {
        GradlePluginCoordinate coordinate = new GradlePluginCoordinate("io.github.example." + "verylongsegment".repeat(8));

        String first = coordinate.skillName();
        String second = coordinate.skillName();

        assertEquals(first, second);
        assertTrue(first.length() <= 64);
    }
}
