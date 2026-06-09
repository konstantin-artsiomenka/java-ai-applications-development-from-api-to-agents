package t8.agent.task.agents;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import commons.Constants;
import commons.model.Message;
import commons.model.Role;
import t8.agent.task.tools.BaseTool;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class OpenAIBasedAgent extends BaseAgent {

    private String endpoint;
    private List<JsonNode> toolsSchemas;
    private HttpClient httpClient;
    private ObjectMapper objectMapper;

    public OpenAIBasedAgent(String model, String apiKey, List<BaseTool> tools, String systemPrompt) {
        super(model, apiKey, tools, systemPrompt);
        this.apiKey = "Bearer " + apiKey;
        this.endpoint = Constants.OPENAI_HOST + "/chat/completions";
        this.httpClient = HttpClient.newHttpClient();
        this.objectMapper = new ObjectMapper();
        this.toolsSchemas = tools.stream()
                .map(t -> {
                    try {
                        return objectMapper.readTree(t.getOpenAiSchema());
                    } catch (Exception e) {
                        throw new RuntimeException("Failed to parse tool schema for: " + t.getName(), e);
                    }
                })
                .collect(Collectors.toList());
        System.out.println("Endpoint: " + endpoint);
        System.out.println("Tools: " + objectMapper.valueToTree(toolsSchemas).toPrettyString());
    }

    @Override
    @SuppressWarnings("unchecked")
    public Message getResponse(List<Message> messages, boolean printRequest) {
        try {
            List<Map<String, Object>> requestMessages = new ArrayList<>();
            if (systemPrompt != null && !systemPrompt.isBlank()) {
                requestMessages.add(new Message(Role.SYSTEM, systemPrompt).toMap());
            }
            messages.forEach(msg -> requestMessages.add(msg.toMap()));

            Map<String, Object> requestData = new HashMap<>();
            requestData.put("model", model);
            requestData.put("messages", requestMessages);
            if (!toolsSchemas.isEmpty()) {
                requestData.put("tools", toolsSchemas);
            }

            if (printRequest) {
                System.out.println("Endpoint: " + endpoint);
                System.out.println("REQUEST: " + objectMapper.writerWithDefaultPrettyPrinter()
                        .writeValueAsString(requestMessages));
            }

            String body = objectMapper.writeValueAsString(requestData);
            HttpRequest httpRequest = HttpRequest.newBuilder()
                    .uri(URI.create(endpoint))
                    .header("Authorization", apiKey)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .build();

            HttpResponse<String> response = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 200) {
                JsonNode root = objectMapper.readTree(response.body());
                JsonNode choices = root.path("choices");
                System.out.println("RESPONSE: " + root.toPrettyString());

                if (choices.isEmpty()) {
                    throw new RuntimeException("No choices returned from OpenAI");
                }

                JsonNode choice = choices.get(0);
                JsonNode messageNode = choice.path("message");
                String content = messageNode.path("content").isNull() ? null : messageNode.path("content").asText(null);
                String finishReason = choice.path("finish_reason").asText("");

                List<Map<String, Object>> toolCalls = null;
                if (messageNode.has("tool_calls") && !messageNode.path("tool_calls").isNull()) {
                    toolCalls = objectMapper.convertValue(
                            messageNode.path("tool_calls"),
                            objectMapper.getTypeFactory().constructCollectionType(List.class, Map.class));
                }

                Message aiResponse = new Message(Role.ASSISTANT, content, null, null, toolCalls);

                if ("tool_calls".equals(finishReason) && toolCalls != null) {
                    messages.add(aiResponse);
                    List<Message> toolResults = processToolCalls(toolCalls);
                    messages.addAll(toolResults);
                    return getResponse(messages, printRequest);
                }

                return aiResponse;
            } else {
                throw new RuntimeException("OpenAI error: " + response.statusCode() + " " + response.body());
            }
        } catch (IOException | InterruptedException e) {
            throw new RuntimeException("Request failed: " + e.getMessage(), e);
        }
    }

    @SuppressWarnings("unchecked")
    private List<Message> processToolCalls(List<Map<String, Object>> toolCalls) throws IOException {
        List<Message> results = new ArrayList<>();
        for (Map<String, Object> toolCall : toolCalls) {
            String toolCallId = (String) toolCall.get("id");
            Map<String, Object> function = (Map<String, Object>) toolCall.get("function");
            String functionName = (String) function.get("name");
            Map<String, Object> arguments = objectMapper.readValue(
                    (String) function.get("arguments"), Map.class);

            String result = callTool(functionName, arguments);
            System.out.println("Tool call: " + functionName + " -> " + result);

            results.add(new Message(Role.TOOL, result, toolCallId, functionName, null));
        }
        return results;
    }
}
