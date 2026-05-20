package io.github.duckasteroid.agentdocs.mcp.repo;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;

import io.github.duckasteroid.agentdocs.mcp.MavenCoordinates;
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

        assertTrue(rewritten.contains("agentdocs:///com.example/demo/1.0.0/setup.md"));
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

    @Test
    void rewritesSiblingMarkdownLinksRelativeToCurrentDirectory() {
        String markdown = "[Another](another.md)";

        String rewritten = MarkdownLinkRewriter.rewriteRelativeMarkdownLinks(
                markdown,
                new MavenCoordinates("com.example",
                        "demo",
                        "1.0.0"),
                Path.of("topics/stuff.md"));

        assertTrue(rewritten.contains("agentdocs:///com.example/demo/1.0.0/topics/another.md"));
    }

    @Test
    void rewritesDotSlashAndNestedMarkdownLinksRelativeToCurrentDirectory() {
        String markdown = "[Sibling](./another.md) and [Nested](sub/another.md)";

        String rewritten = MarkdownLinkRewriter.rewriteRelativeMarkdownLinks(
                markdown,
                new MavenCoordinates("com.example",
                        "demo",
                        "1.0.0"),
                Path.of("topics/stuff.md"));

        assertTrue(rewritten.contains("agentdocs:///com.example/demo/1.0.0/topics/another.md"));
        assertTrue(rewritten.contains("agentdocs:///com.example/demo/1.0.0/topics/sub/another.md"));
    }
}

