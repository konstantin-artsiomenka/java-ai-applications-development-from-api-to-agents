package t3.content.generation.t3;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import commons.Constants;

import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * T3-3: Speech to Text (Transcription)
 * <p>
 * Transcribes audio_sample.mp3 via /v1/audio/transcriptions using multipart/form-data.
 * Try both WHISPER_1 and GPT_4O_TRANSCRIBE models and compare the results.
 */
public class SpeechToText {

  private static final ObjectMapper MAPPER = new ObjectMapper();
  private static final HttpClient HTTP = HttpClient.newHttpClient();

  static void main(String[] args) throws Exception {
    Path audioPath = Path.of("tasks/src/t3/content/generation/t3/audio_sample.mp3");
    byte[] audioBytes = Files.readAllBytes(audioPath);
    String model = Constants.WHISPER_1;

    String boundary = "----Boundary" + System.currentTimeMillis();

    ByteArrayOutputStream out = new ByteArrayOutputStream();

    // -- model field
    out.write(("--" + boundary + "\r\n").getBytes());
    out.write("Content-Disposition: form-data; name=\"model\"\r\n\r\n".getBytes());
    out.write((model + "\r\n").getBytes());

    // -- file field
    out.write(("--" + boundary + "\r\n").getBytes());
    out.write("Content-Disposition: form-data; name=\"file\"; filename=\"audio_sample.mp3\"\r\n".getBytes());
    out.write("Content-Type: audio/mpeg\r\n\r\n".getBytes());
    out.write(audioBytes);
    out.write("\r\n".getBytes());

    // -- closing boundary
    out.write(("--" + boundary + "--\r\n").getBytes());

    HttpRequest request = HttpRequest.newBuilder()
      .uri(URI.create(Constants.OPENAI_AUDIO_TRANSCRIPTIONS_ENDPOINT))
      .header("Authorization", "Bearer " + Constants.OPENAI_API_KEY)
      .header("Content-Type", "multipart/form-data; boundary=" + boundary)
      .POST(HttpRequest.BodyPublishers.ofByteArray(out.toByteArray()))
      .build();

    HttpResponse<String> response = HTTP.send(request, HttpResponse.BodyHandlers.ofString());
    if (response.statusCode() != 200) {
      throw new RuntimeException("Request failed with status " + response.statusCode() + ": " + response.body());
    }

    JsonNode json = MAPPER.readTree(response.body());
    String text = json.at("/text").asText();
    System.out.println(text);
  }
}
