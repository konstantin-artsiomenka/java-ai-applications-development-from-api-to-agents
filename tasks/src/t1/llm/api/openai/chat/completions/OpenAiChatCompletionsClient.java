package t1.llm.api.openai.chat.completions;

import com.openai.client.OpenAIClient;
import com.openai.client.okhttp.OpenAIOkHttpClient;
import com.openai.core.http.StreamResponse;
import com.openai.models.chat.completions.ChatCompletionAssistantMessageParam;
import com.openai.models.chat.completions.ChatCompletionChunk;
import com.openai.models.chat.completions.ChatCompletionCreateParams;
import commons.model.Message;
import commons.model.Role;
import t1.llm.api.openai.BaseOpenAiClient;

import java.util.List;

/**
 * OpenAI Chat Completions client using the official OpenAI Java SDK.
 * <p>
 * Demonstrates how the SDK abstracts HTTP and SSE details. Compare with
 * {@link CustomOpenAiChatCompletionsClient} which does the same via raw HTTP.
 */
public class OpenAiChatCompletionsClient extends BaseOpenAiClient {

  private final OpenAIClient client;

  public OpenAiChatCompletionsClient(String url, String modelName, String apiKey, String systemPrompt) {
    super(url, modelName, apiKey, systemPrompt);
    // this.apiKey already has "Bearer " prefix from BaseOpenAiClient; strip it for the SDK
    String rawKey = this.apiKey.startsWith("Bearer ") ? this.apiKey.substring("Bearer ".length()) : this.apiKey;
    this.client = OpenAIOkHttpClient.builder()
      .baseUrl(url)
      .apiKey(rawKey)
      .build();
  }

  @Override
  public Message response(List<Message> messages) {
    ChatCompletionCreateParams params = buildParams(messages);
    var completion = client.chat().completions().create(params);
    String content = completion.choices().getFirst().message().content()
      .orElseThrow(() -> new RuntimeException("No content in response"));
    System.out.println(content);
    return new Message(Role.ASSISTANT, content);
  }

  @Override
  public Message streamResponse(List<Message> messages) {
    ChatCompletionCreateParams params = buildParams(messages);
    StringBuilder sb = new StringBuilder();
    try (StreamResponse<ChatCompletionChunk> stream = client.chat().completions().createStreaming(params)) {
      stream.stream().forEach(chunk ->
        chunk.choices().forEach(choice ->
          choice.delta().content().ifPresent(token -> {
            System.out.print(token);
            sb.append(token);
          })
        )
      );
    }
    System.out.println();
    return new Message(Role.ASSISTANT, sb.toString());
  }

  private ChatCompletionCreateParams buildParams(List<Message> messages) {
    ChatCompletionCreateParams.Builder builder = ChatCompletionCreateParams.builder()
      .model(modelName)
      .addSystemMessage(systemPrompt);
    for (Message msg : messages) {
      if (msg.role() == Role.USER) {
        builder.addUserMessage(msg.content());
      } else if (msg.role() == Role.ASSISTANT) {
        builder.addMessage(ChatCompletionAssistantMessageParam.builder()
          .content(msg.content())
          .build());
      }
    }
    return builder.build();
  }
}
