package t8.agent.task.tools;

import com.fasterxml.jackson.databind.ObjectMapper;
import commons.Constants;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.Map;

public class WebSearchTool extends BaseTool {

    private final String apiKey;
    private final String endpoint;
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;

    public WebSearchTool(String openAiApiKey) {
        this.apiKey = "Bearer " + openAiApiKey;
        this.endpoint = Constants.OPENAI_HOST;
        this.httpClient = HttpClient.newHttpClient();
        this.objectMapper = new ObjectMapper();
    }

    @Override
    public String getName() {
        return "web_search_tool";
    }

    @Override
    public String getDescription() {
        return "Searches the web for up-to-date information based on a given query and returns a summary of the results.";
    }

    @Override
    public String getInputSchema() {
        return """
                {
                  "type": "object",
                  "properties": {
                    "request": {
                      "type": "string",
                      "description": "The search query to look up on the web."
                    }
                  },
                  "required": ["request"]
                }
                """;
    }

    @Override
    @SuppressWarnings("unchecked")
    public String execute(Map<String, Object> arguments) {
        // https://developers.openai.com/api/docs/guides/tools-web-search
        try {
            String request = (String) arguments.get("request");

            Map<String, Object> payload = Map.of(
                    "model", Constants.GPT_5_4,
                    "tools", List.of(Map.of("type", "web_search_preview")),
                    "input", request
            );

            String body = objectMapper.writeValueAsString(payload);

            HttpRequest httpRequest = HttpRequest.newBuilder()
                    .uri(URI.create(endpoint + "/responses"))
                    .header("Authorization", apiKey)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .build();

            HttpResponse<String> response = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 200) {
                var root = objectMapper.readTree(response.body());
                var output = root.path("output");
                for (var item : output) {
                    if ("message".equals(item.path("type").asText())) {
                        for (var contentBlock : item.path("content")) {
                            if ("output_text".equals(contentBlock.path("type").asText())) {
                                return contentBlock.path("text").asText();
                            }
                        }
                    }
                }
                return "No text output found in response.";
            } else {
                return "Error: " + response.statusCode() + " " + response.body();
            }
        } catch (IOException | InterruptedException e) {
            return "Error: " + e.getMessage();
        }
    }
}
