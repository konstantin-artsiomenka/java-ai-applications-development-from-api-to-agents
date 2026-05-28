package t1.llm.api.gemini;

import com.google.genai.Client;
import com.google.genai.ResponseStream;
import com.google.genai.types.Content;
import com.google.genai.types.GenerateContentConfig;
import com.google.genai.types.GenerateContentResponse;
import com.google.genai.types.Part;
import commons.model.Message;
import commons.model.Role;
import t1.llm.api.AiClient;

import java.util.ArrayList;
import java.util.List;

/**
 * Google Gemini client using the official Google GenAI Java SDK.
 * <p>
 * Key differences from OpenAI/Anthropic:
 * <ul>
 *   <li>System prompt goes in {@code GenerateContentConfig.systemInstruction}</li>
 *   <li>The role for AI messages is {@code "model"}, not {@code "assistant"}</li>
 *   <li>Streaming uses {@code ResponseStream<GenerateContentResponse>} which is {@code Iterable}</li>
 * </ul>
 * Compare with {@link CustomGeminiAiClient} for the raw HTTP equivalent.
 */
public class GeminiAiClient extends AiClient {

  private final Client client;

  public GeminiAiClient(String endpoint, String modelName, String apiKey, String systemPrompt) {
    super(endpoint, modelName, apiKey, systemPrompt);
    this.client = Client.builder()
      .apiKey(apiKey)
      .build();
  }

  @Override
  public Message response(List<Message> messages) {
    GenerateContentConfig config = buildConfig();
    List<Content> contents = buildContents(messages);
    GenerateContentResponse resp = client.models.generateContent(modelName, contents, config);
    String content = resp.text() != null ? resp.text() : "";
    System.out.println(content);
    return new Message(Role.ASSISTANT, content);
  }

  @Override
  public Message streamResponse(List<Message> messages) {
    GenerateContentConfig config = buildConfig();
    List<Content> contents = buildContents(messages);
    StringBuilder sb = new StringBuilder();
    try (ResponseStream<GenerateContentResponse> stream = client.models.generateContentStream(modelName, contents,
      config)) {
      for (GenerateContentResponse chunk : stream) {
        String text = chunk.text();
        if (text != null && !text.isEmpty()) {
          System.out.print(text);
          sb.append(text);
        }
      }
    }
    System.out.println();
    return new Message(Role.ASSISTANT, sb.toString());
  }

  private GenerateContentConfig buildConfig() {
    Content systemInstruction = Content.builder()
      .parts(Part.fromText(systemPrompt))
      .build();
    return GenerateContentConfig.builder()
      .systemInstruction(systemInstruction)
      .maxOutputTokens(1024)
      .build();
  }

  private List<Content> buildContents(List<Message> messages) {
    List<Content> contents = new ArrayList<>();
    for (Message m : messages) {
      Content content = Content.builder()
        .role(toGeminiRole(m.role()))
        .parts(Part.fromText(m.content()))
        .build();
      contents.add(content);
    }
    return contents;
  }

  private String toGeminiRole(Role role) {
    return role == Role.ASSISTANT ? "model" : role.getValue();
  }
}
