package io.github.duckasteroid.agentdocs.mcp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;

import io.github.duckasteroid.agentdocs.mcp.tools.MavenCoordinates;
import org.junit.jupiter.api.Test;

class MarkdownLinkRewriterTest {
    @Test
    void rewritesRelativeMarkdownLinksToResourceUris() {
        String markdown = "See [Setup](../setup.md) and [Anchor](#intro).";

        String rewritten = MarkdownLinkRewriter.rewriteRelativeMarkdownLinks(
                markdown,
                new MavenCoordinates("com.example",
                "demo",
                "1.0.0"),
                Path.of("topics/overview.md"));

        assertTrue(rewritten.contains("agentdocs://com.example/demo/1.0.0/setup.md"));
        assertTrue(rewritten.contains("[Anchor](#intro)"));
    }

    @Test
    void keepsAbsoluteLinksUnchanged() {
        String markdown = "[Docs](https://example.com/docs.md)";

        String rewritten = MarkdownLinkRewriter.rewriteRelativeMarkdownLinks(
                markdown,
                new MavenCoordinates("com.example",
                "demo",
                "1.0.0"),
                Path.of("agents.md"));

        assertEquals(markdown, rewritten.trim());
    }
}

