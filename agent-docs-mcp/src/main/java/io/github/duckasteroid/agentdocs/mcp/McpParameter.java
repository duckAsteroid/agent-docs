package io.github.duckasteroid.agentdocs.mcp;

import java.util.Map;
import java.util.Optional;

public record McpParameter<T>(String name, Type<T> type, String description) {
    public McpParameter {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Parameter name must not be blank.");
        }
        if (type == null) {
            throw new IllegalArgumentException("Parameter type must not be null.");
        }
        if (description == null || description.isBlank()) {
            throw new IllegalArgumentException("Parameter description must not be blank.");
        }
    }

    public Optional<T> extract(Map<McpParameter<?>, Object> args) {
        if (args == null || !args.containsKey(this)) {
            return Optional.empty();
        }
        var raw = args.get(this);
        return Optional.ofNullable(type.converter().apply(raw));
    }
}

