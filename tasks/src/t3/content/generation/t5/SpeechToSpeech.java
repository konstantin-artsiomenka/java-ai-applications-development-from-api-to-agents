package t3.content.generation.t5;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
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
 * T3-5: Speech to Speech
 * <p>
 * Sends an audio question (question.mp3) as a base64-encoded input_audio message
 * to gpt-4o-audio-preview via /v1/chat/completions with modalities=["text","audio"].
 * The model responds with both text and audio; the audio is decoded from base64
 * and saved as an MP3 file.
 */
public class SpeechToSpeech {

  private static final ObjectMapper MAPPER = new ObjectMapper();
  private static final HttpClient HTTP = HttpClient.newHttpClient();

  static void main(String[] args) throws Exception {
    // Load and base64-encode the audio question
    Path audioPath = Path.of("tasks/src/t3/content/generation/t5/question.mp3");
    String audioBase64 = Base64.getEncoder().encodeToString(Files.readAllBytes(audioPath));

    // Build input_audio content part
    ObjectNode inputAudioData = MAPPER.createObjectNode();
    inputAudioData.put("data", audioBase64);
    inputAudioData.put("format", "mp3");

    ObjectNode contentPart = MAPPER.createObjectNode();
    contentPart.put("type", "input_audio");
    contentPart.set("input_audio", inputAudioData);

    // Build user message
    ObjectNode userMessage = MAPPER.createObjectNode();
    userMessage.put("role", "user");
    userMessage.set("content", MAPPER.createArrayNode().add(contentPart));

    // Build audio output config
    ObjectNode audioConfig = MAPPER.createObjectNode();
    audioConfig.put("voice", "alloy");
    audioConfig.put("format", "mp3");

    // Build full request body
    ObjectNode body = MAPPER.createObjectNode();
    body.put("model", "gpt-audio-1.5");
    body.set("modalities", MAPPER.createArrayNode().add("text").add("audio"));
    body.set("audio", audioConfig);
    body.set("messages", MAPPER.createArrayNode().add(userMessage));

    HttpRequest request = HttpRequest.newBuilder()
      .uri(URI.create(Constants.OPENAI_HOST + "/chat/completions"))
      .header("Authorization", "Bearer " + Constants.OPENAI_API_KEY)
      .header("Content-Type", "application/json")
      .POST(HttpRequest.BodyPublishers.ofString(MAPPER.writeValueAsString(body)))
      .build();

    HttpResponse<String> response = HTTP.send(request, HttpResponse.BodyHandlers.ofString());
    if (response.statusCode() != 200) {
      throw new RuntimeException("Request failed with status " + response.statusCode() + ": " + response.body());
    }

    // Extract text transcript and audio data
    var responseJson = MAPPER.readTree(response.body());
    var choice = responseJson.at("/choices/0/message");

    String textContent = choice.at("/audio/transcript").asText("");
    System.out.println("Transcript: " + textContent);

    String audioData = choice.at("/audio/data").asText();
    byte[] audioBytes = Base64.getDecoder().decode(audioData);

    String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
    Path outputPath = Path.of("tasks/src/t3/content/generation/t5/answer_" + timestamp + ".mp3");
    Files.write(outputPath, audioBytes);
    System.out.println("Audio saved to: " + outputPath);
  }
}
