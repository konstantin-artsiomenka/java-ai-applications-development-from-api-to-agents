package t3.content.generation.t2;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import commons.Constants;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Base64;

/**
 * T3-2b: GPT-Image-1 Image Generation
 * <p>
 * Generates an image via /v1/images/generations using gpt-image-1.
 * Unlike DALL-E 3, the response returns the image as base64 JSON (b64_json)
 * rather than a URL — this implementation decodes and saves it as a PNG file.
 */
public class GptImageGeneration {

  private static final ObjectMapper MAPPER = new ObjectMapper();
  private static final HttpClient HTTP = HttpClient.newHttpClient();

  static void main(String[] args) throws Exception {
    String json = MAPPER.writeValueAsString(java.util.Map.of(
      "model", "gpt-image-2",
      "prompt", "Smiling catdog"
    ));

    HttpRequest request = HttpRequest.newBuilder()
      .uri(URI.create(Constants.OPENAI_IMAGES_GENERATIONS_ENDPOINT))
      .header("Authorization", "Bearer " + Constants.OPENAI_API_KEY)
      .header("Content-Type", "application/json")
      .POST(HttpRequest.BodyPublishers.ofString(json))
      .build();

    HttpResponse<String> response = HTTP.send(request, HttpResponse.BodyHandlers.ofString());
    if (response.statusCode() != 200) {
      throw new RuntimeException("Request failed with status " + response.statusCode() + ": " + response.body());
    }

    JsonNode responseJson = MAPPER.readTree(response.body());
    String b64Json = responseJson.at("/data/0/b64_json").asText();
    byte[] imageBytes = Base64.getDecoder().decode(b64Json);

    String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
    Path outputPath = Path.of("tasks/src/t3/content/generation/t2/catdog_" + timestamp + ".png");
    Files.write(outputPath, imageBytes);
    System.out.println("Image saved to: " + outputPath);
  }
}

//  https://developers.openai.com/api/reference/resources/images/methods/generate
//  ---
//  Request:
//  curl -X POST "https://api.openai.com/v1/images/generations" \
//      -H "Authorization: Bearer $OPENAI_API_KEY" \
//      -H "Content-type: application/json" \
//      -d '{
//          "model": "gpt-image-2",
//          "prompt": "smiling catdog."
//      }'
//  Response:
//  {
//    "created": 1699900000,
//    "data": [
//      {
//        "b64_json": Qt0n6ArYAEABGOhEoYgVAJFdt8jM79uW2DO...,
//      }
//    ]
//  }
