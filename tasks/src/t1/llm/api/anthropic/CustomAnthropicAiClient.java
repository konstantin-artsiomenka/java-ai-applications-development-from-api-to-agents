package t1.llm.api.anthropic;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import t1.llm.api.AiClient;
import commons.model.Message;
import commons.model.Role;

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
 * Anthropic Claude client using raw HTTP — no SDK.
 * <p>
 * Key differences from OpenAI raw HTTP:
 * <ul>
 *   <li>Auth header is {@code x-api-key} (no "Bearer" prefix)</li>
 *   <li>Requires {@code anthropic-version: 2023-06-01} header</li>
 *   <li>System prompt is a top-level JSON field, not a message in the array</li>
 *   <li>{@code max_tokens} is required</li>
 *   <li>Streaming SSE: look for {@code content_block_delta} events with {@code text_delta} type;
 *       stop early on {@code message_stop}</li>
 * </ul>
 * This class extends {@link AiClient} directly — the API key is stored raw (no "Bearer" prefix).
 */
public class CustomAnthropicAiClient extends AiClient {

  private static final ObjectMapper MAPPER = new ObjectMapper();
  private final HttpClient http = HttpClient.newHttpClient();

  public CustomAnthropicAiClient(String endpoint, String modelName, String apiKey, String systemPrompt) {
    super(endpoint, modelName, apiKey, systemPrompt);
  }

  @Override
  public Message response(List<Message> messages) {
    try {
      String body = buildRequestBody(messages, false);
      HttpRequest request = buildRequest(body);
      HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
      if (response.statusCode() != 200) {
        throw new RuntimeException("Anthropic API error: " + response.statusCode() + " - " + response.body());
      }
      JsonNode root = MAPPER.readTree(response.body());
      StringBuilder sb = new StringBuilder();
      for (JsonNode block : root.get("content")) {
        if ("text".equals(block.get("type").asText())) {
          sb.append(block.get("text").asText());
        }
      }
      String content = sb.toString();
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
      HttpResponse<Stream<String>> response = http.send(request, HttpResponse.BodyHandlers.ofLines());
      StringBuilder sb = new StringBuilder();
      for (Iterator<String> it = response.body().iterator(); it.hasNext(); ) {
        String line = it.next();
        if (!line.startsWith("data: ")) {
          continue;
        }
        String json = line.substring("data: ".length()).trim();
        if (json.isEmpty()) {
          continue;
        }
        JsonNode node = MAPPER.readTree(json);
        String type = node.path("type").asText();
        if ("message_stop".equals(type)) {
          break;
        }
        if ("content_block_delta".equals(type)) {
          JsonNode delta = node.path("delta");
          if ("text_delta".equals(delta.path("type").asText())) {
            String text = delta.path("text").asText();
            if (!text.isEmpty()) {
              System.out.print(text);
              sb.append(text);
            }
          }
        }
      }
      System.out.println();
      return new Message(Role.ASSISTANT, sb.toString());
    } catch (RuntimeException e) {
      throw e;
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  private HttpRequest buildRequest(String body) {
    return HttpRequest.newBuilder()
      .uri(URI.create(url))
      .header("x-api-key", apiKey)
      .header("Content-Type", "application/json")
      .header("anthropic-version", "2023-06-01")
      .POST(HttpRequest.BodyPublishers.ofString(body))
      .build();
  }

  private String buildRequestBody(List<Message> messages, boolean stream) {
    try {
      Map<String, Object> body = new LinkedHashMap<>();
      body.put("model", modelName);
      body.put("system", systemPrompt);
      body.put("max_tokens", 1024);
      body.put("messages", messages.stream().map(Message::toMap).toList());
      if (stream) {
        body.put("stream", true);
      }
      return MAPPER.writeValueAsString(body);
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }
}
