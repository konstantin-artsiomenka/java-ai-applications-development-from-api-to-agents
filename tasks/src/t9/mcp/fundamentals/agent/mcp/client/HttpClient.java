package t9.mcp.fundamentals.agent.mcp.client;

import io.modelcontextprotocol.client.McpClient;
import io.modelcontextprotocol.client.transport.HttpClientStreamableHttpTransport;
import io.modelcontextprotocol.json.jackson.JacksonMcpJsonMapper;

public class HttpClient extends BaseClient {

    private final String mcpServerUrl;

    public HttpClient(String mcpServerUrl) {
        this.mcpServerUrl = mcpServerUrl;
    }

    @Override
    public void connect() {
        int lastSlash = mcpServerUrl.lastIndexOf('/');
        String baseUrl = lastSlash > 7 ? mcpServerUrl.substring(0, lastSlash) : mcpServerUrl;
        String endpoint = lastSlash > 7 ? mcpServerUrl.substring(lastSlash) : "/mcp";

        HttpClientStreamableHttpTransport transport = HttpClientStreamableHttpTransport
                .builder(baseUrl)
                .endpoint(endpoint)
                .jsonMapper(new JacksonMcpJsonMapper(objectMapper))
                .build();

        mcpClient = McpClient.sync(transport).build();

        System.out.println("Connecting to HTTP MCP Server at " + mcpServerUrl + "...");
        mcpClient.initialize();
        initToolCallbackProvider();
        System.out.println("Connected to HTTP MCP Server.");
    }
}
