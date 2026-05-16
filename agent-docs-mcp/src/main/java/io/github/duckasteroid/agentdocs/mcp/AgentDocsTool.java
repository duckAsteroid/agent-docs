package io.github.duckasteroid.agentdocs.mcp;

import io.modelcontextprotocol.json.jackson2.JacksonMcpJsonMapper;
import io.modelcontextprotocol.spec.McpSchema;
import java.util.Map;

interface AgentDocsTool {
    String name();

    String description();

    String execute(AgentDocsRepository repository, Map<String, Object> arguments);

    default McpSchema.Tool definition(JacksonMcpJsonMapper jsonMapper) {
        return McpSchema.Tool.builder()
                .name(name())
                .description(description())
                .inputSchema(jsonMapper, "{\"type\":\"object\",\"properties\":{},\"required\":[]}")
                .build();
    }
}

