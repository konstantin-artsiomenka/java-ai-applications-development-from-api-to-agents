package t8.agent.task.tools;

import com.fasterxml.jackson.databind.ObjectMapper;
import commons.Constants;
import commons.exceptions.TaskNotImplementedException;

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
        //TODO: Return tool name "web_search_tool"
        throw new TaskNotImplementedException();
    }

    @Override
    public String getDescription() {
        //TODO: Return a short description of what this tool does
        throw new TaskNotImplementedException();
    }

    @Override
    public String getInputSchema() {
        //TODO: Return JSON schema for this tool — accepts a "request" string parameter (required)
        throw new TaskNotImplementedException();
    }

    @Override
    @SuppressWarnings("unchecked")
    public String execute(Map<String, Object> arguments) {
        // https://developers.openai.com/api/docs/guides/tools-web-search
        //TODO:
        // https://developers.openai.com/api/docs/guides/tools-web-search
        // - Build request payload: model "gpt-5.4", tools=[{"type":"web_search"}],
        //   input from arguments.get("request")
        // - POST to `endpoint` with Authorization header (`apiKey`) and Content-Type: application/json
        // - On HTTP 200: traverse output → find item with type "message"
        //   → find content block with type "output_text" → return its "text"
        // - Return error string on non-200 or exception (e.g. "Error: " + statusCode + " " + body)
        throw new TaskNotImplementedException();
    }
}
