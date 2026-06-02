package t3.content.generation.t4;

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

/**
 * T3-4: Text to Speech
 * <p>
 * Converts text to speech via /v1/audio/speech using gpt-4o-mini-tts.
 * The response is raw binary audio — saved directly as an MP3 file.
 * Try different voices from the Voice enum and the instructions field
 * to control speaking style.
 */
public class TextToSpeech {

  private static final ObjectMapper MAPPER = new ObjectMapper();
  private static final HttpClient HTTP = HttpClient.newHttpClient();

  public enum Voice {
    ALLOY("alloy"),
    ASH("ash"),
    BALLAD("ballad"),
    CORAL("coral"),
    ECHO("echo"),
    FABLE("fable"),
    NOVA("nova"),
    ONYX("onyx"),
    SAGE("sage"),
    SHIMMER("shimmer");

    private final String value;

    Voice(String value) {
      this.value = value;
    }

    public String getValue() {
      return value;
    }
  }

  static void main(String[] args) throws Exception {
    ObjectNode body = MAPPER.createObjectNode();
    body.put("model", Constants.GPT_4O_MINI_TTS);
    body.put("input", "Why can't we say that black is white?");
    body.put("voice", Voice.CORAL.getValue());
    body.put("instructions", "Speak in a cheerful and positive tone.");

    HttpRequest request = HttpRequest.newBuilder()
      .uri(URI.create(Constants.OPENAI_AUDIO_SPEECH_ENDPOINT))
      .header("Authorization", "Bearer " + Constants.OPENAI_API_KEY)
      .header("Content-Type", "application/json")
      .POST(HttpRequest.BodyPublishers.ofString(MAPPER.writeValueAsString(body)))
      .build();

    HttpResponse<byte[]> response = HTTP.send(request, HttpResponse.BodyHandlers.ofByteArray());
    if (response.statusCode() != 200) {
      throw new RuntimeException(
        "Request failed with status " + response.statusCode() + ": " + new String(response.body()));
    }

    String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
    Path outputPath = Path.of("tasks/src/t3/content/generation/t4/speech_" + timestamp + ".mp3");
    Files.write(outputPath, response.body());
    System.out.println("Audio saved to: " + outputPath);
  }
}
//  https://developers.openai.com/api/docs/guides/text-to-speech
//  Request:
//  curl https://api.openai.com/v1/audio/speech \
//    -H "Authorization: Bearer $OPENAI_API_KEY" \
//    -H "Content-Type: application/json" \
//    -d '{
//      "model": "gpt-4o-mini-tts",
//      "input": "Why can't we say that black is white?",
//      "voice": "coral",
//      "instructions": "Speak in a cheerful and positive tone."
//    }' \
//  Response:
//    bytes with audio