package io.github.duckasteroid.agentdocs.resolve.task;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import java.util.List;
import java.util.Map;

import io.github.duckasteroid.agentdocs.resolve.task.model.GradlePluginCoordinate;
import io.github.duckasteroid.agentdocs.resolve.task.model.ModuleCoordinate;
import io.github.duckasteroid.agentdocs.resolve.task.model.SkillSource;
import org.junit.jupiter.api.Test;

class SkillNameAssignerTest {
    @Test
    void singleCoordinateGetsArtifactOnlyName() {
        ModuleCoordinate coordinate = new ModuleCoordinate("com.example", "widget", "1.0.0");

        Map<ModuleCoordinate, String> assigned = SkillNameAssigner.assign(List.of(coordinate));

        assertEquals("widget", assigned.get(coordinate));
    }

    @Test
    void distinctArtifactNamesAllStayAtArtifactOnlyTier() {
        ModuleCoordinate widget = new ModuleCoordinate("com.example", "widget", "1.0.0");
        ModuleCoordinate gadget = new ModuleCoordinate("com.example", "gadget", "2.0.0");

        Map<ModuleCoordinate, String> assigned = SkillNameAssigner.assign(List.of(widget, gadget));

        assertEquals("widget", assigned.get(widget));
        assertEquals("gadget", assigned.get(gadget));
    }

    @Test
    void clashingArtifactNamesAcrossGroupsEscalateToGroupArtifact() {
        ModuleCoordinate acmeCore = new ModuleCoordinate("com.acme", "core", "1.0.0");
        ModuleCoordinate otherCore = new ModuleCoordinate("com.other", "core", "2.0.0");

        Map<ModuleCoordinate, String> assigned = SkillNameAssigner.assign(List.of(acmeCore, otherCore));

        assertEquals("com-acme-core", assigned.get(acmeCore));
        assertEquals("com-other-core", assigned.get(otherCore));
        assertNotEquals(assigned.get(acmeCore), assigned.get(otherCore));
    }

    @Test
    void onlyCollidingCoordinatesEscalateWhileOthersStayShort() {
        ModuleCoordinate acmeCore = new ModuleCoordinate("com.acme", "core", "1.0.0");
        ModuleCoordinate otherCore = new ModuleCoordinate("com.other", "core", "2.0.0");
        ModuleCoordinate uniqueWidget = new ModuleCoordinate("com.acme", "widget", "1.0.0");

        Map<ModuleCoordinate, String> assigned = SkillNameAssigner.assign(List.of(acmeCore, otherCore, uniqueWidget));

        assertEquals("com-acme-core", assigned.get(acmeCore));
        assertEquals("com-other-core", assigned.get(otherCore));
        assertEquals("widget", assigned.get(uniqueWidget));
    }

    @Test
    void sameGroupAndArtifactDifferentVersionsEscalateToFullGav() {
        // Not reachable via a single resolved Gradle configuration (which selects one version per
        // group:artifact), but the algorithm must still resolve it correctly as a defensive tier.
        ModuleCoordinate v1 = new ModuleCoordinate("com.acme", "core", "1.0.0");
        ModuleCoordinate v2 = new ModuleCoordinate("com.acme", "core", "2.0.0");

        Map<ModuleCoordinate, String> assigned = SkillNameAssigner.assign(List.of(v1, v2));

        assertEquals(v1.skillName(), assigned.get(v1));
        assertEquals(v2.skillName(), assigned.get(v2));
        assertNotEquals(assigned.get(v1), assigned.get(v2));
    }

    @Test
    void dependencyAndPluginShareOneCollisionNamespace() {
        ModuleCoordinate dependency = new ModuleCoordinate("com.example", "publish", "1.0.0");
        GradlePluginCoordinate plugin = new GradlePluginCoordinate("io.github.duckasteroid.agent-docs.publish");

        Map<SkillSource, String> assigned = SkillNameAssigner.assign(List.of(dependency, plugin));

        assertEquals("com-example-publish", assigned.get(dependency));
        assertEquals("io-github-duckasteroid-agent-docs-publish", assigned.get(plugin));
    }

    @Test
    void nonCollidingDependencyAndPluginBothStayAtShortTier() {
        ModuleCoordinate dependency = new ModuleCoordinate("com.example", "widget", "1.0.0");
        GradlePluginCoordinate plugin = new GradlePluginCoordinate("io.github.duckasteroid.agent-docs.publish");

        Map<SkillSource, String> assigned = SkillNameAssigner.assign(List.of(dependency, plugin));

        assertEquals("widget", assigned.get(dependency));
        assertEquals("publish", assigned.get(plugin));
    }
}
