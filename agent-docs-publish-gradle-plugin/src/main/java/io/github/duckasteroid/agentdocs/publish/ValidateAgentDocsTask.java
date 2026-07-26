package io.github.duckasteroid.agentdocs.publish;

import io.github.duckasteroid.agentdocs.publish.validation.AgentDocsValidationRule;
import io.github.duckasteroid.agentdocs.publish.validation.DocsDirectoryExistsRule;
import io.github.duckasteroid.agentdocs.publish.validation.PluginBundleDirectoriesRule;
import io.github.duckasteroid.agentdocs.publish.validation.SkillCompatibilityRule;
import io.github.duckasteroid.agentdocs.publish.validation.SkillDescriptionRule;
import io.github.duckasteroid.agentdocs.publish.validation.SkillEntrypointRule;
import io.github.duckasteroid.agentdocs.publish.validation.SkillFrontmatterStructureRule;
import io.github.duckasteroid.agentdocs.publish.validation.SkillNameRule;
import io.github.duckasteroid.agentdocs.publish.validation.StandardDirectoriesRule;
import io.github.duckasteroid.agentdocs.publish.validation.ValidationContext;
import io.github.duckasteroid.agentdocs.publish.validation.ValidationResult;
import io.github.duckasteroid.agentdocs.publish.validation.ValidationSeverity;
import java.io.File;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.TreeSet;
import org.gradle.api.DefaultTask;
import org.gradle.api.GradleException;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.provider.SetProperty;
import org.gradle.work.DisableCachingByDefault;
import org.gradle.api.tasks.Internal;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.TaskAction;

/**
 * Validates docs by running a composite set of focused validation rules.
 *
 * <p>For {@code java-gradle-plugin} projects, {@link #getDeclaredPluginIds()} is non-empty and
 * the docs root is treated as a parent of one bundle subdirectory per declared plugin id
 * ({@code agent-docs/<pluginId>/SKILL.md}), never a bundle in its own right - see {@link
 * PluginBundleDirectoriesRule}. Otherwise the docs root is validated as a single bundle, exactly
 * as before this existed.
 */
@DisableCachingByDefault(because = "Validation task currently does not declare cacheable outputs")
public abstract class ValidateAgentDocsTask extends DefaultTask {
    /**
     * Docs directory containing the skill files to validate.
     *
     * @return docs directory property
     */
    @Internal
    public abstract DirectoryProperty getDocsDirectory();

    /**
     * Rule IDs that should be skipped for this task run.
     *
     * @return disabled rule ID set
     */
    @Input
    public abstract SetProperty<String> getDisabledValidationRules();

    /**
     * Plugin ids declared via {@code gradlePlugin { plugins { ... } } }. Empty for projects that
     * don't apply {@code java-gradle-plugin}, in which case the docs root is validated as a single
     * bundle as usual.
     *
     * @return declared plugin ids
     */
    @Input
    public abstract SetProperty<String> getDeclaredPluginIds();

    @TaskAction
    void validateAgentDocs() {
        File docsDirectory = getDocsDirectory().get().getAsFile();
        Set<String> declaredPluginIds = getDeclaredPluginIds().getOrElse(Set.of());
        Set<String> disabledRules = new LinkedHashSet<>();
        for (String disabledRule : getDisabledValidationRules().getOrElse(Set.of())) {
            disabledRules.add(disabledRule.trim().toLowerCase(Locale.ROOT));
        }

        ValidationContext rootContext = new ValidationContext(docsDirectory, declaredPluginIds);
        List<ValidationResult> failures = new java.util.ArrayList<>(runRules(rootLevelRules(), rootContext, disabledRules));

        if (declaredPluginIds.isEmpty()) {
            failures.addAll(runRules(bundleRules(), rootContext, disabledRules));
        } else {
            for (String pluginId : new TreeSet<>(declaredPluginIds)) {
                File bundleDirectory = new File(docsDirectory, pluginId);
                if (!bundleDirectory.isDirectory()) {
                    // Already reported by PluginBundleDirectoriesRule; validating a bundle that
                    // doesn't exist would just add a confusing, redundant failure.
                    continue;
                }
                ValidationContext bundleContext = new ValidationContext(bundleDirectory);
                failures.addAll(runRules(bundleRules(), bundleContext, disabledRules));
            }
        }

        if (!failures.isEmpty()) {
            StringBuilder message = new StringBuilder("Agent docs validation failed:\n");
            for (ValidationResult failure : failures) {
                message.append("- [").append(failure.ruleId()).append("] ");
                if (failure.details().isEmpty()) {
                    message.append("validation failed");
                } else {
                    message.append(String.join(" | ", failure.details()));
                }
                message.append('\n');
            }
            throw new GradleException(message.toString());
        }
    }

    private List<ValidationResult> runRules(
            List<AgentDocsValidationRule> rules, ValidationContext context, Set<String> disabledRules) {
        List<ValidationResult> failures = new java.util.ArrayList<>();
        for (AgentDocsValidationRule rule : rules) {
            if (disabledRules.contains(rule.id().toLowerCase(Locale.ROOT))) {
                getLogger().info("Skipping agent docs validation rule '{}'", rule.id());
                continue;
            }
            ValidationResult result = rule.validate(context);
            if (result.severity().isPresent()) {
                ValidationSeverity severity = result.severity().get();
                if (severity == ValidationSeverity.WARN) {
                    if (result.details().isEmpty()) {
                        getLogger().warn("[{}] validation warning", result.ruleId());
                    } else {
                        for (String detail : result.details()) {
                            getLogger().warn("[{}] {}", result.ruleId(), detail);
                        }
                    }
                } else if (severity == ValidationSeverity.ERROR) {
                    failures.add(result);
                }
            }
        }
        return failures;
    }

    private List<AgentDocsValidationRule> rootLevelRules() {
        return List.of(new DocsDirectoryExistsRule(), new PluginBundleDirectoriesRule());
    }

    private List<AgentDocsValidationRule> bundleRules() {
        return List.of(
                new SkillEntrypointRule(),
                new StandardDirectoriesRule(),
                new SkillFrontmatterStructureRule(),
                new SkillDescriptionRule(),
                new SkillNameRule(),
                new SkillCompatibilityRule());
    }
}
