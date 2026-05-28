package t1.llm.api.anthropic;

import com.anthropic.client.AnthropicClient;
import com.anthropic.client.okhttp.AnthropicOkHttpClient;
import com.anthropic.core.http.StreamResponse;
import com.anthropic.models.messages.ContentBlock;
import com.anthropic.models.messages.MessageCreateParams;
import com.anthropic.models.messages.RawMessageStreamEvent;
import t1.llm.api.AiClient;
import commons.model.Message;
import commons.model.Role;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Anthropic Claude client using the official Anthropic Java SDK.
 * <p>
 * Claude's API differs from OpenAI: the system prompt is a separate {@code system} parameter,
 * not a message in the conversation. Max tokens must always be specified.
 * Compare with {@link CustomAnthropicAiClient} for the raw HTTP equivalent.
 */
public class AnthropicAiClient extends AiClient {

  private final AnthropicClient client;

  public AnthropicAiClient(String endpoint, String modelName, String apiKey, String systemPrompt) {
    super(endpoint, modelName, apiKey, systemPrompt);
    this.client = AnthropicOkHttpClient.builder()
      .apiKey(apiKey)
      .build();
  }

  @Override
  public Message response(List<Message> messages) {
    MessageCreateParams params = buildParams(messages);
    var msg = client.messages().create(params);
    String content = msg.content().stream()
      .filter(ContentBlock::isText)
      .map(block -> block.asText().text())
      .collect(Collectors.joining());
    System.out.println(content);
    return new Message(Role.ASSISTANT, content);
  }

  @Override
  public Message streamResponse(List<Message> messages) {
    MessageCreateParams params = buildParams(messages);
    StringBuilder sb = new StringBuilder();
    try (StreamResponse<RawMessageStreamEvent> stream = client.messages().createStreaming(params)) {
      stream.stream()
        .filter(RawMessageStreamEvent::isContentBlockDelta)
        .forEach(event -> {
          var delta = event.asContentBlockDelta().delta();
          if (delta.isText()) {
            String text = delta.asText().text();
            if (!text.isEmpty()) {
              System.out.print(text);
              sb.append(text);
            }
          }
        });
    }
    System.out.println();
    return new Message(Role.ASSISTANT, sb.toString());
  }

  private MessageCreateParams buildParams(List<Message> messages) {
    MessageCreateParams.Builder builder = MessageCreateParams.builder()
      .model(modelName)
      .system(systemPrompt)
      .maxTokens(1024L);
    for (Message msg : messages) {
      if (msg.role() == Role.USER) {
        builder.addUserMessage(msg.content());
      } else if (msg.role() == Role.ASSISTANT) {
        builder.addAssistantMessage(msg.content());
      }
    }
    return builder.build();
  }
}
