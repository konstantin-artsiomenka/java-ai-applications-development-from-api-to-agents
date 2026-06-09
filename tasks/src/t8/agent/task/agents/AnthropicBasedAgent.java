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

public class AnthropicBasedAgent extends BaseAgent {

    private final String endpoint;
    private final List<JsonNode> toolsSchemas;
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;

    public AnthropicBasedAgent(String model, String apiKey, List<BaseTool> tools, String systemPrompt) {
        super(model, apiKey, tools, systemPrompt);
        this.endpoint = Constants.ANTHROPIC_ENDPOINT;
        this.httpClient = HttpClient.newHttpClient();
        this.objectMapper = new ObjectMapper();
        this.toolsSchemas = tools.stream()
                .map(t -> {
                    try {
                        return objectMapper.readTree(t.getAnthropicSchema());
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
            List<Map<String, Object>> anthropicMessages = toAnthropicMessages(messages);

            Map<String, Object> requestData = new HashMap<>();
            requestData.put("model", model);
            requestData.put("max_tokens", 8096);
            requestData.put("messages", anthropicMessages);
            if (!toolsSchemas.isEmpty()) {
                requestData.put("tools", toolsSchemas);
            }
            if (systemPrompt != null && !systemPrompt.isBlank()) {
                requestData.put("system", systemPrompt);
            }

            if (printRequest) {
                System.out.println("Endpoint: " + endpoint);
                System.out.println("REQUEST: " + objectMapper.writerWithDefaultPrettyPrinter()
                        .writeValueAsString(anthropicMessages));
            }

            String body = objectMapper.writeValueAsString(requestData);
            HttpRequest httpRequest = HttpRequest.newBuilder()
                    .uri(URI.create(endpoint))
                    .header("x-api-key", apiKey)
                    .header("anthropic-version", "2023-06-01")
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .build();

            HttpResponse<String> response = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 200) {
                JsonNode root = objectMapper.readTree(response.body());
                System.out.println("RESPONSE: " + root.toPrettyString());

                JsonNode contentArray = root.path("content");
                String stopReason = root.path("stop_reason").asText("");

                // Extract text content
                String textContent = null;
                for (JsonNode block : contentArray) {
                    if ("text".equals(block.path("type").asText())) {
                        textContent = block.path("text").asText();
                        break;
                    }
                }

                // Extract tool_use blocks
                List<Map<String, Object>> toolUseBlocks = new ArrayList<>();
                for (JsonNode block : contentArray) {
                    if ("tool_use".equals(block.path("type").asText())) {
                        toolUseBlocks.add(objectMapper.convertValue(block, Map.class));
                    }
                }

                Message aiResponse = new Message(
                        Role.ASSISTANT,
                        textContent,
                        null,
                        null,
                        toolUseBlocks.isEmpty() ? null : toolUseBlocks
                );

                if ("tool_use".equals(stopReason)) {
                    messages.add(aiResponse);
                    List<Message> toolResults = processToolCalls(toolUseBlocks);
                    messages.addAll(toolResults);
                    return getResponse(messages, printRequest);
                }

                return aiResponse;
            } else {
                throw new RuntimeException("Anthropic error: " + response.statusCode() + " " + response.body());
            }
        } catch (IOException | InterruptedException e) {
            throw new RuntimeException("Request failed: " + e.getMessage(), e);
        }
    }

    private List<Map<String, Object>> toAnthropicMessages(List<Message> messages) {
        List<Map<String, Object>> result = new ArrayList<>();
        int i = 0;
        while (i < messages.size()) {
            Message msg = messages.get(i);

            if (msg.role() == Role.TOOL) {
                // Group consecutive TOOL messages into a single "user" message with tool_result content
                List<Map<String, Object>> toolResults = new ArrayList<>();
                while (i < messages.size() && messages.get(i).role() == Role.TOOL) {
                    Message toolMsg = messages.get(i);
                    Map<String, Object> toolResult = new HashMap<>();
                    toolResult.put("type", "tool_result");
                    toolResult.put("tool_use_id", toolMsg.toolCallId());
                    toolResult.put("content", toolMsg.content());
                    toolResults.add(toolResult);
                    i++;
                }
                Map<String, Object> userMsg = new HashMap<>();
                userMsg.put("role", "user");
                userMsg.put("content", toolResults);
                result.add(userMsg);

            } else if (msg.role() == Role.ASSISTANT) {
                Map<String, Object> assistantMsg = new HashMap<>();
                assistantMsg.put("role", "assistant");
                assistantMsg.put("content", msg.toolCalls() != null ? msg.toolCalls() : msg.content());
                result.add(assistantMsg);
                i++;

            } else {
                Map<String, Object> genericMsg = new HashMap<>();
                genericMsg.put("role", msg.role().getValue());
                genericMsg.put("content", msg.content());
                result.add(genericMsg);
                i++;
            }
        }
        return result;
    }

    @SuppressWarnings("unchecked")
    private List<Message> processToolCalls(List<Map<String, Object>> toolUseBlocks) {
        List<Message> results = new ArrayList<>();
        for (Map<String, Object> block : toolUseBlocks) {
            String toolUseId = (String) block.get("id");
            String functionName = (String) block.get("name");
            Map<String, Object> arguments = (Map<String, Object>) block.get("input");

            String result = callTool(functionName, arguments);
            System.out.println("Tool call: " + functionName + " -> " + result);

            results.add(new Message(Role.TOOL, result, toolUseId, functionName, null));
        }
        return results;
    }
}
