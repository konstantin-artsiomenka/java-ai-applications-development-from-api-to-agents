package t1.llm.api.gemini;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import t1.llm.api.AiClient;
import commons.model.Message;
import commons.model.Role;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

/**
 * Google Gemini client using raw HTTP — no official stable Java SDK available.
 * <p>
 * Key differences from OpenAI/Anthropic:
 * <ul>
 *   <li>Auth header is {@code x-goog-api-key} (not Authorization/x-api-key)</li>
 *   <li>System prompt goes in {@code system_instruction.parts[].text}</li>
 *   <li>The role for AI messages is {@code "model"}, not {@code "assistant"}</li>
 *   <li>Non-streaming URL: {@code {endpoint}/{model}:generateContent}</li>
 *   <li>Streaming URL: {@code {endpoint}/{model}:streamGenerateContent?alt=sse}</li>
 *   <li>Response path: {@code candidates[0].content.parts[*].text}</li>
 * </ul>
 */
public class CustomGeminiAiClient extends AiClient {

  private static final ObjectMapper MAPPER = new ObjectMapper();
  private final HttpClient http = HttpClient.newHttpClient();

  public CustomGeminiAiClient(String endpoint, String modelName, String apiKey, String systemPrompt) {
    super(endpoint, modelName, apiKey, systemPrompt);
  }

  @Override
  public Message response(List<Message> messages) {
    try {
      String url = this.url + "/" + modelName + ":generateContent";
      String body = buildRequestBody(messages);
      HttpRequest request = buildRequest(url, body);
      HttpResponse<String> resp = http.send(request, HttpResponse.BodyHandlers.ofString());
      if (resp.statusCode() != 200) {
        throw new RuntimeException("Gemini API error: " + resp.statusCode() + " " + resp.body());
      }
      JsonNode root = MAPPER.readTree(resp.body());
      JsonNode candidate = root.get("candidates").get(0);
      String content = extractPartsText(candidate);
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
      String url = this.url + "/" + modelName + ":streamGenerateContent?alt=sse";
      String body = buildRequestBody(messages);
      HttpRequest request = buildRequest(url, body);
      HttpResponse<Stream<String>> resp = http.send(request, HttpResponse.BodyHandlers.ofLines());
      StringBuilder sb = new StringBuilder();
      Iterator<String> it = resp.body().iterator();
      while (it.hasNext()) {
        String line = it.next();
          if (!line.startsWith("data: ")) {
              continue;
          }
        String json = line.substring("data: ".length());
        JsonNode root = MAPPER.readTree(json);
        JsonNode candidates = root.get("candidates");
        if (candidates != null && !candidates.isEmpty()) {
          String text = extractPartsText(candidates.get(0));
          if (!text.isEmpty()) {
            System.out.print(text);
            sb.append(text);
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

  private HttpRequest buildRequest(String url, String body) {
    return HttpRequest.newBuilder()
      .uri(URI.create(url))
      .header("Content-Type", "application/json")
      .header("x-goog-api-key", apiKey)
      .POST(HttpRequest.BodyPublishers.ofString(body))
      .build();
  }

  private String buildRequestBody(List<Message> messages) {
    try {
      Map<String, Object> systemPart = Map.of("text", systemPrompt);
      Map<String, Object> systemInstruction = Map.of("parts", List.of(systemPart));

      List<Map<String, Object>> contents = new ArrayList<>();
      for (Message m : messages) {
        Map<String, Object> part = Map.of("text", m.content());
        Map<String, Object> content = new LinkedHashMap<>();
        content.put("role", toGeminiRole(m.role()));
        content.put("parts", List.of(part));
        contents.add(content);
      }

      Map<String, Object> generationConfig = Map.of("maxOutputTokens", 1024);

      Map<String, Object> bodyMap = new LinkedHashMap<>();
      bodyMap.put("system_instruction", systemInstruction);
      bodyMap.put("contents", contents);
      bodyMap.put("generationConfig", generationConfig);

      return MAPPER.writeValueAsString(bodyMap);
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  private String extractPartsText(JsonNode candidate) {
    JsonNode parts = candidate.path("content").path("parts");
    StringBuilder sb = new StringBuilder();
    for (JsonNode part : parts) {
      JsonNode text = part.get("text");
      if (text != null) {
        sb.append(text.asText());
      }
    }
    return sb.toString();
  }

  private String toGeminiRole(Role role) {
    return role == Role.ASSISTANT ? "model" : role.getValue();
  }
}
