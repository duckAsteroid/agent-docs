package io.github.duckasteroid.agentdocs.publish;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.gradle.api.GradleException;
import org.gradle.api.Project;
import org.gradle.testfixtures.ProjectBuilder;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ValidateAgentDocsTaskTest {

    @TempDir
    Path projectDir;

    @Test
    void validateAgentDocsAcceptsValidSkillWithDescriptionOnly() throws IOException {
        writeFile(projectDir.resolve("src/agent-docs/SKILL.md"), """
                ---
                description: Valid skill for publish validation.
                ---

                # Skill
                """);
        writeFile(projectDir.resolve("src/agent-docs/references/overview.md"), "# Overview\n");

        ValidateAgentDocsTask task = createTask(projectDir);

        assertDoesNotThrow(task::validateAgentDocs);
    }

    @Test
    void validateAgentDocsAggregatesMultipleRuleFailures() throws IOException {
        writeFile(projectDir.resolve("src/agent-docs/SKILL.md"), """
                ---
                compatibility: %s
                ---

                # Skill
                """.formatted("x".repeat(501)));
        writeFile(projectDir.resolve("src/agent-docs/assets"), "not-a-directory");

        ValidateAgentDocsTask task = createTask(projectDir);

        GradleException exception = assertThrows(GradleException.class, task::validateAgentDocs);
        assertTrue(exception.getMessage().contains("[skill-description]"));
        assertTrue(exception.getMessage().contains("[skill-compatibility]"));
        assertTrue(exception.getMessage().contains("[standard-directories]"));
    }

    @Test
    void validateAgentDocsAppliesDisabledRulesDuringIntegration() throws IOException {
        writeFile(projectDir.resolve("src/agent-docs/SKILL.md"), """
                ---
                compatibility: local-only
                ---

                # Skill
                """);

        ValidateAgentDocsTask task = createTask(projectDir);
        task.getDisabledValidationRules().add("skill-description");

        assertDoesNotThrow(task::validateAgentDocs);
    }

    private static ValidateAgentDocsTask createTask(Path projectDir) {
        Project project = ProjectBuilder.builder().withProjectDir(projectDir.toFile()).build();
        ValidateAgentDocsTask task = project.getTasks().create("validateTask", ValidateAgentDocsTask.class);
        task.getDocsDirectory().set(project.getLayout().getProjectDirectory().dir("src/agent-docs"));
        return task;
    }

    private static void writeFile(Path filePath, String content) throws IOException {
        Files.createDirectories(filePath.getParent());
        Files.writeString(filePath, content);
    }
}
