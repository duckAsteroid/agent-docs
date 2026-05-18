package io.github.duckasteroid.agentdocs.mcp;

import com.vladsch.flexmark.ast.Link;
import com.vladsch.flexmark.formatter.Formatter;
import com.vladsch.flexmark.parser.Parser;
import com.vladsch.flexmark.util.ast.NodeVisitor;
import com.vladsch.flexmark.util.ast.VisitHandler;
import com.vladsch.flexmark.util.sequence.BasedSequence;
import io.github.duckasteroid.agentdocs.mcp.tools.MavenCoordinates;

import java.nio.file.Path;
import java.util.Objects;

public final class MarkdownLinkRewriter {
    private static final Parser PARSER = Parser.builder().build();
    private static final Formatter FORMATTER = Formatter.builder().build();

    private MarkdownLinkRewriter() {
    }

    public static String rewriteRelativeMarkdownLinks(String markdown, MavenCoordinates coords,
            Path currentMarkdownPath) {
        Objects.requireNonNull(markdown, "markdown");
        Objects.requireNonNull(currentMarkdownPath, "currentMarkdownPath");

        var document = PARSER.parse(markdown);
        var visitor = new NodeVisitor(new VisitHandler<>(Link.class, link -> rewriteLink(link, coords, currentMarkdownPath)));
        visitor.visit(document);
        return FORMATTER.render(document);
    }

    private static void rewriteLink(Link link, MavenCoordinates coords, Path currentMarkdownPath) {
        String destination = link.getUrl().toString();
        String rewritten = rewriteLinkDestination(destination, coords, currentMarkdownPath);
        if (!rewritten.equals(destination)) {
            link.setUrlChars(BasedSequence.of(rewritten));
        }
    }

    static String rewriteLinkDestination(String destination, MavenCoordinates coords,
            Path currentMarkdownPath) {
        if (destination == null || destination.isBlank()) {
            return destination;
        }
        if (destination.startsWith("#")
                || destination.startsWith("/")
                || destination.startsWith("http://")
                || destination.startsWith("https://")
                || destination.startsWith(AgentDocsResourceResolver.RESOURCE_SCHEME)
                || destination.startsWith("mailto:")) {
            return destination;
        }

        int hashIndex = destination.indexOf('#');
        String pathPart = hashIndex < 0 ? destination : destination.substring(0, hashIndex);
        String suffix = hashIndex < 0 ? "" : destination.substring(hashIndex);
        if (pathPart.isBlank()) {
            return destination;
        }
        if (!pathPart.endsWith(".md")) {
            return destination;
        }

        Path parent = currentMarkdownPath.getParent();
        Path resolved = (parent == null ? Path.of(pathPart) : parent.resolve(pathPart)).normalize();
        if (resolved.isAbsolute() || resolved.startsWith("..")) {
            return destination;
        }

        return AgentDocsResourceResolver.toResourceUri(coords.asPath(), resolved) + suffix;
    }
}

