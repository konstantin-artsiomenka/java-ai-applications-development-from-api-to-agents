package t1.llm.api.openai.chat.completions;

import com.fasterxml.jackson.databind.ObjectMapper;
import commons.model.Message;
import commons.model.Role;
import t1.llm.api.openai.BaseOpenAiClient;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * OpenAI Chat Completions client using raw HTTP — no SDK.
 * <p>
 * Shows what the SDK does under the hood: plain REST POST with JSON body,
 * and SSE line-by-line parsing for streaming.
 * The "data: [DONE]" sentinel marks the end of the stream.
 */
public class CustomOpenAiChatCompletionsClient extends BaseOpenAiClient {

  private static final ObjectMapper MAPPER = new ObjectMapper();
  private static final String COMPLETIONS = "/chat/completions";
  private final HttpClient http = HttpClient.newHttpClient();

  public CustomOpenAiChatCompletionsClient(String endpoint, String modelName, String apiKey, String systemPrompt) {
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
      String content = MAPPER.readTree(httpResponse.body())
        .at("/choices/0/message/content").asText();
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
      HttpResponse<java.util.stream.Stream<String>> httpResponse =
        http.send(request, HttpResponse.BodyHandlers.ofLines());
      StringBuilder sb = new StringBuilder();
      httpResponse.body()
        .filter(line -> line.startsWith("data: "))
        .map(line -> line.substring("data: ".length()))
        .takeWhile(data -> !"[DONE]".equals(data))
        .forEach(data -> {
          try {
            String delta = MAPPER.readTree(data).at("/choices/0/delta/content").asText("");
            if (!delta.isEmpty()) {
              System.out.print(delta);
              sb.append(delta);
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
    String endpoint = url + COMPLETIONS;
    return HttpRequest.newBuilder()
      .uri(URI.create(endpoint))
      .header("Authorization", apiKey)
      .header("Content-Type", "application/json")
      .POST(HttpRequest.BodyPublishers.ofString(body))
      .build();
  }

  private String buildRequestBody(List<Message> messages, boolean stream) {
    try {
      List<Map<String, Object>> msgList = new ArrayList<>();
      msgList.add(Map.of("role", "system", "content", systemPrompt));
      messages.stream().map(Message::toMap).forEach(msgList::add);
      Map<String, Object> bodyMap = new LinkedHashMap<>();
      bodyMap.put("model", modelName);
      bodyMap.put("messages", msgList);
      if (stream) {
        bodyMap.put("stream", true);
      }
      return MAPPER.writeValueAsString(bodyMap);
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }
}
