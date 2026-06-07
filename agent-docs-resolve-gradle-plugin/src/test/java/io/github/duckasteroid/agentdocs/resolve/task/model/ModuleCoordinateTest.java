package io.github.duckasteroid.agentdocs.resolve.task.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class ModuleCoordinateTest {
    @Test
    void gavReturnsCanonicalCoordinate() {
        ModuleCoordinate coordinate = new ModuleCoordinate("com.example", "demo-lib", "1.2.3");
        assertEquals("com.example:demo-lib:1.2.3", coordinate.gav());
    }

    @Test
    void skillNameNormalizesAndConstrainsOutput() {
        ModuleCoordinate coordinate = new ModuleCoordinate("Com.Example", "DEMO_lib", "1.2.3+meta");
        String skillName = coordinate.skillName();

        assertTrue(skillName.matches("^[a-z0-9]+(?:-[a-z0-9]+)*$"));
        assertTrue(skillName.length() <= 64);
    }

    @Test
    void skillNameUsesStableHashSuffixWhenTruncated() {
        String longGroup = "com.example." + "verylongsegment".repeat(8);
        ModuleCoordinate coordinate = new ModuleCoordinate(longGroup, "artifact-" + "x".repeat(40), "1.0.0");

        String first = coordinate.skillName();
        String second = coordinate.skillName();

        assertEquals(first, second);
        assertTrue(first.length() <= 64);
    }
}
