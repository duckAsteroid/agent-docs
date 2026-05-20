package io.github.duckasteroid.agentdocs.mcp.tools;

import io.github.duckasteroid.agentdocs.mcp.repo.Repository;
import io.modelcontextprotocol.json.jackson2.JacksonMcpJsonMapper;
import io.modelcontextprotocol.spec.McpSchema;

import java.util.*;

public interface Tool {
    String name();

    String description();

    McpSchema.CallToolResult execute(Repository repository, Map<ToolParameter<?>, Object> arguments) throws ToolException;

    List<ToolParameter<?>> parameters();

    default McpSchema.ToolAnnotations annotations(){
        return new McpSchema.ToolAnnotations("x-safety", true, false, true, false, true);
    }

    default Map<ToolParameter<?>, Object> validateAndConvertArguments(Map<String, Object> arguments) {
        Map<String, Object> safeArguments = arguments == null ? Map.of() : arguments;
        if (safeArguments.isEmpty()) {
            return Map.of();
        }

        Map<String, ToolParameter<?>> parametersByName = new LinkedHashMap<>();
        for (ToolParameter<?> parameter : parameters()) {
            parametersByName.put(parameter.name(), parameter);
        }

        Map<ToolParameter<?>, Object> converted = new LinkedHashMap<>();

        for (Map.Entry<String, Object> entry : safeArguments.entrySet()) {
            ToolParameter<?> parameter = parametersByName.get(entry.getKey());
            if (parameter == null) {
                throw new IllegalArgumentException("Unknown argument: " + entry.getKey());
            }
            Object value = entry.getValue();
            if (value != null && !parameter.type().isValidValue(value)) {
                throw new IllegalArgumentException(
                        "Argument '%s' must be %s.".formatted(parameter.name(), parameter.type().jsonType()));
            }
            converted.put(parameter, value);
        }

        return Collections.unmodifiableMap(converted);
    }

    default String inputSchema() {
        List<ToolParameter<?>> parameters = parameters();
        StringBuilder properties = new StringBuilder();
        for (int index = 0; index < parameters.size(); index++) {
            ToolParameter<?> parameter = parameters.get(index);
            if (index > 0) {
                properties.append(',');
            }
            properties.append('"').append(escapeJson(parameter.name())).append('"')
                    .append(":{")
                    .append("\"type\":\"").append(parameter.type().jsonType()).append("\",")
                    .append("\"description\":\"").append(escapeJson(parameter.description())).append("\"")
                    .append('}');
        }
        return "{\"type\":\"object\",\"properties\":{" + properties + "},\"required\":[]}";
    }

    default McpSchema.Tool definition(JacksonMcpJsonMapper jsonMapper) {
        return McpSchema.Tool.builder()
                .name(name())
                .description(description())
                .inputSchema(jsonMapper, inputSchema())
                .annotations(annotations())
                .build();
    }

    private static String escapeJson(String input) {
        return input
                .replace("\\", "\\\\")
                .replace("\"", "\\\"");
    }
}

