package io.github.duckasteroid.agentdocs.mcp;

import io.modelcontextprotocol.json.jackson2.JacksonMcpJsonMapper;
import io.modelcontextprotocol.spec.McpSchema;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public interface AgentDocsTool {
    String name();

    String description();

    String execute(AgentDocsRepository repository, Map<McpParameter<?>, Object> arguments);

    List<McpParameter<?>> parameters();

    default Map<McpParameter<?>, Object> validateAndConvertArguments(Map<String, Object> arguments) {
        Map<String, Object> safeArguments = arguments == null ? Map.of() : arguments;
        if (safeArguments.isEmpty()) {
            return Map.of();
        }

        Map<String, McpParameter<?>> parametersByName = new LinkedHashMap<>();
        for (McpParameter<?> parameter : parameters()) {
            parametersByName.put(parameter.name(), parameter);
        }

        Map<McpParameter<?>, Object> converted = new LinkedHashMap<>();

        for (Map.Entry<String, Object> entry : safeArguments.entrySet()) {
            McpParameter<?> parameter = parametersByName.get(entry.getKey());
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
        List<McpParameter<?>> parameters = parameters();
        StringBuilder properties = new StringBuilder();
        for (int index = 0; index < parameters.size(); index++) {
            McpParameter<?> parameter = parameters.get(index);
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
                .build();
    }

    private static String escapeJson(String input) {
        return input
                .replace("\\", "\\\\")
                .replace("\"", "\\\"");
    }
}

