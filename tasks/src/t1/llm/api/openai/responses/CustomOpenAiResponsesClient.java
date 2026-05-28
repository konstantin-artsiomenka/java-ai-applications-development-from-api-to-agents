package t1.llm.api.openai.responses;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import commons.model.Message;
import commons.model.Role;
import t1.llm.api.openai.BaseOpenAiClient;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

/**
 * OpenAI Responses API client using raw HTTP — no SDK.
 * <p>
 * The Responses API uses an event-based SSE format different from Chat Completions:
 * each SSE frame consists of an "event: &lt;type&gt;" line followed by "data: &lt;json&gt;".
 * Only frames with type "response.output_text.delta" carry text content.
 */
public class CustomOpenAiResponsesClient extends BaseOpenAiClient {

  private static final ObjectMapper MAPPER = new ObjectMapper();
  private static final String RESPONSES_ENDPOINT = "/responses";
  private final HttpClient http = HttpClient.newHttpClient();

  public CustomOpenAiResponsesClient(String endpoint, String modelName, String apiKey, String systemPrompt) {
    super(endpoint, modelName, apiKey, systemPrompt);
  }

  @Override
  public Message response(List<Message> messages) {
    try {
      String body = buildRequestBody(messages, false);
      HttpRequest request = buildRequest(body);
      HttpResponse<String> httpResponse = http.send(request, HttpResponse.BodyHandlers.ofString());
      if (httpResponse.statusCode() != 200) {
        throw new RuntimeException(
          "Request failed with status " + httpResponse.statusCode() + ": " + httpResponse.body());
      }
      JsonNode root = MAPPER.readTree(httpResponse.body());
      String content = extractOutputText(root);
      System.out.println(content);
      return new Message(Role.ASSISTANT, content);
    } catch (RuntimeException e) {
      throw e;
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  @Override
  public Message streamResponse(List<Message> messages) {
    try {
      String body = buildRequestBody(messages, true);
      HttpRequest request = buildRequest(body);
      HttpResponse<Stream<String>> httpResponse = http.send(request, HttpResponse.BodyHandlers.ofLines());
      StringBuilder sb = new StringBuilder();
      String[] currentEvent = {""};
      httpResponse.body().forEach(line -> {
        try {
          if (line.startsWith("event: ")) {
            currentEvent[0] = line.substring("event: ".length()).trim();
          } else if (line.startsWith("data: ")) {
            if ("response.output_text.delta".equals(currentEvent[0])) {
              String json = line.substring("data: ".length());
              JsonNode node = MAPPER.readTree(json);
              String delta = node.path("delta").asText("");
              if (!delta.isEmpty()) {
                System.out.print(delta);
                sb.append(delta);
              }
            }
          } else if (line.isEmpty()) {
            currentEvent[0] = "";
          }
        } catch (Exception e) {
          throw new RuntimeException(e);
        }
      });
      System.out.println();
      return new Message(Role.ASSISTANT, sb.toString());
    } catch (RuntimeException e) {
      throw e;
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  private HttpRequest buildRequest(String body) {
    String endpoint = url + RESPONSES_ENDPOINT;
    return HttpRequest.newBuilder()
      .uri(URI.create(endpoint))
      .header("Authorization", apiKey)
      .header("Content-Type", "application/json")
      .POST(HttpRequest.BodyPublishers.ofString(body))
      .build();
  }

  private String buildRequestBody(List<Message> messages, boolean stream) {
    try {
      List<Map<String, Object>> inputList = messages.stream()
        .map(Message::toMap)
        .collect(java.util.stream.Collectors.toList());
      Map<String, Object> bodyMap = new LinkedHashMap<>();
      bodyMap.put("model", modelName);
      bodyMap.put("instructions", systemPrompt);
      bodyMap.put("input", inputList);
      if (stream) {
        bodyMap.put("stream", true);
      }
      return MAPPER.writeValueAsString(bodyMap);
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  private String extractOutputText(JsonNode root) {
    Iterator<JsonNode> outputItems = root.path("output").elements();
    while (outputItems.hasNext()) {
      JsonNode item = outputItems.next();
      if ("message".equals(item.path("type").asText())) {
        Iterator<JsonNode> contentParts = item.path("content").elements();
        while (contentParts.hasNext()) {
          JsonNode part = contentParts.next();
          if ("output_text".equals(part.path("type").asText())) {
            return part.path("text").asText();
          }
        }
      }
    }
    throw new RuntimeException("No output text found in response");
  }
}
