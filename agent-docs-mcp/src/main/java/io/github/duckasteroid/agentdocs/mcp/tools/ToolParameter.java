package io.github.duckasteroid.agentdocs.mcp.tools;

import java.util.Map;
import java.util.Optional;

public record ToolParameter<T>(String name, Type<T> type, String description) {
    public ToolParameter {
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

    public Optional<T> extract(Map<ToolParameter<?>, Object> args) {
        if (args == null || !args.containsKey(this)) {
            return Optional.empty();
        }
        var raw = args.get(this);
        return Optional.ofNullable(type.converter().apply(raw));
    }
}

