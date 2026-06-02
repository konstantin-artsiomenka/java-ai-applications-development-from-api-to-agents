package t3.content.generation.t1;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import commons.Constants;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * T3-1: Image Analysis (Vision)
 * <p>
 * Sends two images to gpt model via /v1/chat/completions:
 * - a remote image by URL
 * - a local logo.png encoded as a base64 data URL
 * and asks the model to write a poem based on both images.
 */
public class ImageAnalysis {

  private static final ObjectMapper MAPPER = new ObjectMapper();
  private static final HttpClient HTTP = HttpClient.newHttpClient();

  static void main(String[] args) throws Exception {
    // Encode local logo.png as base64 data URL
    Path logoPath = Path.of("tasks/src/t3/content/generation/t1/logo.png");
    byte[] logoBytes = Files.readAllBytes(logoPath);
    String base64Logo = Base64.getEncoder().encodeToString(logoBytes);
    String dataUrl = "data:image/png;base64," + base64Logo;

    // Remote image URL (publicly accessible sample image)
    String remoteImageUrl = "https://www.lrt.lt/img/2025/11/27/2250539-745973-615x345.jpg";

    // Build 3-part user message content
    List<Map<String, Object>> contentParts = new ArrayList<>();
    contentParts.add(Map.of("type", "text", "text", "Write a short poem inspired by both of these images."));
    contentParts.add(Map.of("type", "image_url", "image_url", Map.of("url", remoteImageUrl)));
    contentParts.add(Map.of("type", "image_url", "image_url", Map.of("url", dataUrl)));

    List<Map<String, Object>> messages = new ArrayList<>();
    messages.add(Map.of("role", "user", "content", contentParts));

    Map<String, Object> body = new LinkedHashMap<>();
    body.put("model", Constants.GPT_4O_MINI);
    body.put("messages", messages);

    String json = MAPPER.writeValueAsString(body);

    HttpRequest request = HttpRequest.newBuilder()
      .uri(URI.create(Constants.OPENAI_HOST + "/chat/completions"))
      .header("Authorization", "Bearer " + Constants.OPENAI_API_KEY)
      .header("Content-Type", "application/json")
      .POST(HttpRequest.BodyPublishers.ofString(json))
      .build();

    HttpResponse<String> response = HTTP.send(request, HttpResponse.BodyHandlers.ofString());
    if (response.statusCode() != 200) {
      throw new RuntimeException("Request failed with status " + response.statusCode() + ": " + response.body());
    }

    JsonNode responseJson = MAPPER.readTree(response.body());
    String poem = responseJson.at("/choices/0/message/content").asText();
    System.out.println(poem);
  }
}
