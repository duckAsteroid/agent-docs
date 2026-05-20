package io.github.duckasteroid.agentdocs.mcp;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.duckasteroid.agentdocs.mcp.repo.Repository;
import io.github.duckasteroid.agentdocs.mcp.tools.Tool;
import io.github.duckasteroid.agentdocs.mcp.tools.ToolException;
import io.github.duckasteroid.agentdocs.mcp.tools.ToolParameter;
import io.modelcontextprotocol.json.jackson2.JacksonMcpJsonMapper;
import io.modelcontextprotocol.server.McpServer;
import io.modelcontextprotocol.server.McpServerFeatures;
import io.modelcontextprotocol.server.transport.StdioServerTransportProvider;
import io.modelcontextprotocol.spec.McpSchema;
import io.modelcontextprotocol.util.McpUriTemplateManager;
import io.modelcontextprotocol.util.McpUriTemplateManagerFactory;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.ServiceLoader;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class Application {
    private static final String SERVER_NAME = "agent-docs-mcp";
    private static final String SERVER_VERSION = resolveServerVersion();
    private static final String SERVER_INSTRUCTIONS = resolveServerInstructions();
    private static final String SERVER_VERSION_RESOURCE = "version.info";
    private static final String SERVER_INSTRUCTIONS_RESOURCE = "instructions.txt";
    private static final String DEFAULT_SERVER_INSTRUCTIONS = "Provides read-only access to LLM usage instructions for Java libraries.";
    private static final String UNKNOWN_VERSION = "unknown";
    private static final String RESOURCE_TEMPLATE_URI = "agentdocs:///{groupId}/{artifactId}/{version}/{path}";
    private static final List<Tool> TOOLS = loadTools();

    private Application() {
    }

    public static void main(String[] args) throws IOException {
        Repository repository = Repository.resolve();
        startStdioServer(repository, System.in, System.out);
    }

    static void startStdioServer(Repository repository, InputStream inputStream, OutputStream outputStream) {
        var jsonMapper = new JacksonMcpJsonMapper(new ObjectMapper());
        var transportProvider = new StdioServerTransportProvider(jsonMapper, inputStream, outputStream);

        var specification = McpServer.sync(transportProvider)
                .uriTemplateManagerFactory(new AgentDocsUriTemplateManagerFactory())
                .serverInfo(SERVER_NAME, SERVER_VERSION)
                .instructions(SERVER_INSTRUCTIONS)
                .capabilities(McpSchema.ServerCapabilities.builder()
                        .resources(false, false)
                        .tools(false)
                        .build())
                .resourceTemplates(new McpServerFeatures.SyncResourceTemplateSpecification(
                        McpSchema.ResourceTemplate.builder()
                                .uriTemplate(RESOURCE_TEMPLATE_URI)
                                .name("agent-docs-markdown")
                                .description("Reads markdown documents from the local agent-docs repository.")
                                .mimeType("text/markdown")
                                .build(),
                        (exchange, request) -> repository.readResource(request.uri())));

        for (Tool tool : TOOLS) {
            specification = specification.toolCall(tool.definition(jsonMapper),
                    (exchange, request) ->
                    executeTool(tool, repository, request.arguments()));
        }

        specification.build();
    }

    static String resolveServerVersion() {
        return resolveTextResource(SERVER_VERSION_RESOURCE, UNKNOWN_VERSION);
    }

    static String resolveServerInstructions() {
        return resolveTextResource(SERVER_INSTRUCTIONS_RESOURCE, DEFAULT_SERVER_INSTRUCTIONS);
    }

    static List<Tool> loadTools() {
        return ServiceLoader.load(Tool.class).stream()
                .map(ServiceLoader.Provider::get)
                .sorted(Comparator.comparing(Tool::name))
                .toList();
    }

    static McpSchema.CallToolResult executeTool(Tool tool, Repository repository, Map<String, Object> requestArguments) {
        try {
            Map<ToolParameter<?>, Object> arguments = tool.validateAndConvertArguments(requestArguments);
            return tool.execute(repository, arguments);
        } catch (IllegalArgumentException exception) {
            return new ToolException("Invalid arguments: " + exception.getMessage()).toResult();
        } catch (ToolException exception) {
            return exception.toResult();
        }
    }


    private static String resolveTextResource(String resourceName, String defaultValue) {
        try (InputStream stream = Application.class.getClassLoader().getResourceAsStream(resourceName)) {
            if (stream == null) {
                return defaultValue;
            }
            String contents = new String(stream.readAllBytes(), StandardCharsets.UTF_8).trim();
            if (contents.isBlank()) {
                return defaultValue;
            }
            return contents;
        } catch (IOException ignored) {
            return defaultValue;
        }
    }

    private static final class AgentDocsUriTemplateManagerFactory implements McpUriTemplateManagerFactory {
        @Override
        public McpUriTemplateManager create(String uriTemplate) {
            return new AgentDocsUriTemplateManager(uriTemplate);
        }
    }

    private static final class AgentDocsUriTemplateManager implements McpUriTemplateManager {
        private static final Pattern URI_VARIABLE_PATTERN = Pattern.compile("\\{([^/]+?)\\}");
        private static final String MULTI_SEGMENT_PATH_VARIABLE = "path";

        private final String uriTemplate;

        private AgentDocsUriTemplateManager(String uriTemplate) {
            if (uriTemplate == null || uriTemplate.isBlank()) {
                throw new IllegalArgumentException("URI template must not be null or empty");
            }
            this.uriTemplate = uriTemplate;
        }

        @Override
        public List<String> getVariableNames() {
            List<String> variables = new ArrayList<>();
            Matcher matcher = URI_VARIABLE_PATTERN.matcher(this.uriTemplate);
            while (matcher.find()) {
                String variableName = matcher.group(1);
                if (variables.contains(variableName)) {
                    throw new IllegalArgumentException("Duplicate URI variable name in template: " + variableName);
                }
                variables.add(variableName);
            }
            return variables;
        }

        @Override
        public Map<String, String> extractVariableValues(String requestUri) {
            List<String> variableNames = getVariableNames();
            Map<String, String> variableValues = new HashMap<>();
            if ((requestUri == null || requestUri.isBlank()) || variableNames.isEmpty()) {
                return variableValues;
            }

            Pattern pattern = buildPattern();
            Matcher matcher = pattern.matcher(requestUri);
            if (!matcher.matches() || matcher.groupCount() != variableNames.size()) {
                return variableValues;
            }

            for (int i = 0; i < variableNames.size(); i++) {
                String value = matcher.group(i + 1);
                if (value == null || value.isEmpty()) {
                    throw new IllegalArgumentException(
                            "Empty value for URI variable '" + variableNames.get(i) + "' in URI: " + requestUri);
                }
                variableValues.put(variableNames.get(i), value);
            }
            return variableValues;
        }

        @Override
        public boolean matches(String uri) {
            if (!isUriTemplate(this.uriTemplate)) {
                return Objects.equals(uri, this.uriTemplate);
            }
            return buildPattern().matcher(uri).matches();
        }

        @Override
        public boolean isUriTemplate(String uri) {
            return URI_VARIABLE_PATTERN.matcher(uri).find();
        }

        private Pattern buildPattern() {
            StringBuilder patternBuilder = new StringBuilder("^");
            Matcher matcher = URI_VARIABLE_PATTERN.matcher(this.uriTemplate);
            int lastEnd = 0;
            while (matcher.find()) {
                String textBefore = this.uriTemplate.substring(lastEnd, matcher.start());
                patternBuilder.append(Pattern.quote(textBefore));

                String variableName = matcher.group(1);
                boolean trailingPathVariable = MULTI_SEGMENT_PATH_VARIABLE.equals(variableName)
                        && matcher.end() == this.uriTemplate.length();
                patternBuilder.append(trailingPathVariable ? "(.+?)" : "([^/]+?)");
                lastEnd = matcher.end();
            }

            if (lastEnd < this.uriTemplate.length()) {
                patternBuilder.append(Pattern.quote(this.uriTemplate.substring(lastEnd)));
            }
            patternBuilder.append("$");
            return Pattern.compile(patternBuilder.toString());
        }
    }
}

