package io.github.duckasteroid.agentdocs.publish;

/**
 * Controls how a project's packaged agent docs are made discoverable to consumers.
 */
public enum AgentDocsDistribution {
    /**
     * Copy the docs into the project's own jar (under a reserved resource path) and advertise
     * them via an {@code Agent-Docs: classpath} manifest attribute. No separate sidecar artifact
     * is published; no Maven-coordinate resolution is required to discover them.
     */
    EMBEDDED,

    /**
     * Package the docs as a separate {@code agent-docs} classifier zip attached to Maven
     * publications (the original mechanism), and advertise their existence via an
     * {@code Agent-Docs: maven} manifest attribute on the main jar.
     */
    SIDECAR
}
