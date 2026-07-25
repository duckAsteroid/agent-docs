package io.github.duckasteroid.agentdocs.resolve.task;

/**
 * Parsed {@code Agent-Docs} manifest declaration, per {@code specification/java-conventions.md}.
 *
 * @param scheme either {@link #SCHEME_CLASSPATH} or {@link #SCHEME_MAVEN}
 * @param payload the optional value after the first {@code :}, or {@code null} when bare
 */
public record AgentDocsDeclaration(String scheme, String payload) {
    public static final String SCHEME_CLASSPATH = "classpath";
    public static final String SCHEME_MAVEN = "maven";
    public static final String DEFAULT_CLASSPATH_PATH = "agent-docs/";
}
