package io.github.duckasteroid.agentdocs.mcp.tools;

import io.modelcontextprotocol.spec.McpSchema;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.Optional;

/**
 * An exception thrown by tools that the {@link io.github.duckasteroid.agentdocs.mcp.Application}
 * will wrap into a tool result.
 */
public class ToolException extends Exception {
    public ToolException(String message) {
        super(message);
    }
    public ToolException(String message, Throwable cause) {
        super(message, cause);
    }

    public McpSchema.CallToolResult toResult() {
        var builder = McpSchema.CallToolResult.builder();
        builder.isError(true);
        builder.addTextContent(getMessage());
        addStackTrace("ToolException", this, builder);

        var cause = Optional.ofNullable(getCause());
        cause.ifPresent(c -> addStackTrace("Caused by: " + c.getClass().getName(), c, builder));

        return builder.build();
    }

    public void addStackTrace(String message, Throwable throwable, McpSchema.CallToolResult.Builder builder) {
        var sw = new StringWriter();
        var stackTrace = new PrintWriter(sw);
        this.printStackTrace(stackTrace);
        builder.addTextContent(sw.toString());
    }
}
