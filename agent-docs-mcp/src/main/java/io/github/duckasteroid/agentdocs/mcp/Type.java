package io.github.duckasteroid.agentdocs.mcp;

import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

public class Type<T> {
    public static final Type<String> STRING = new Type<>(String.class, "string");
    public static final Type<Long> INTEGER = new Type<>(Long.class, "integer");
    public static final Type<Number> NUMBER = new Type<>(Number.class, "number");
    public static final Type<Boolean> BOOLEAN = new Type<>(Boolean.class, "boolean");
    public static final Type<Object> OBJECT = new Type<>(Object.class, "object");
    public static final Type<List> ARRAY = new Type<>(List.class, "array", value -> {
        if (value instanceof List) {
            return (List) value;
        } else if (value != null && value.getClass().isArray()) {
            int length = Array.getLength(value);
            List<Object> list = new ArrayList<>(length);
            for (int i = 0; i < length; i++) {
                list.add(Array.get(value, i));
            }
            return list;
        }
        throw new IllegalArgumentException("Not a list or array: " + value);
    });

    private final String jsonType;
    private final Class<T> javaType;
    private final Function<Object, T> converter;

    public Type(Class<T> type, String jsonType) {
        this(type, jsonType, value -> {
            if (type.isInstance(value)) {
                return type.cast(value);
            } else {
                throw new IllegalArgumentException(
                        "Value '%s' is not of type %s.".formatted(value, jsonType));
            }
        });
    }

    Function<Object, T> converter() {
        return converter;
    }

    public Type(Class<T> type, String jsonType, Function<Object, T> converter) {
        this.javaType = type;
        this.jsonType = jsonType;
        this.converter = converter;
    }

    String jsonType() {
        return jsonType;
    }

    boolean isValidValue(Object value) {
        if (this == ARRAY && value != null && value.getClass().isArray()) {
            return true;
        }
        return javaType.isInstance(value);
    }


}
