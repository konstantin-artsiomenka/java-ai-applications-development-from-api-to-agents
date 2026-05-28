package t1.llm.api.openai.responses;

import com.openai.client.OpenAIClient;
import com.openai.client.okhttp.OpenAIOkHttpClient;
import com.openai.models.responses.EasyInputMessage;
import com.openai.models.responses.ResponseCreateParams;
import com.openai.models.responses.ResponseInputItem;
import com.openai.models.ResponsesModel;
import com.openai.models.responses.ResponseOutputItem;
import com.openai.models.responses.ResponseOutputMessage;
import com.openai.models.responses.ResponseStreamEvent;
import commons.model.Message;
import commons.model.Role;
import t1.llm.api.openai.BaseOpenAiClient;

import java.util.List;
import java.util.stream.Collectors;

/**
 * OpenAI Responses API client using the official OpenAI Java SDK.
 * <p>
 * The Responses API differs from Chat Completions: it uses {@code instructions} for the system
 * prompt and {@code input} for the conversation history. Compare with
 * {@link CustomOpenAiResponsesClient} which demonstrates the raw HTTP layer.
 */
public class OpenAiResponsesClient extends BaseOpenAiClient {

  private final OpenAIClient client;

  public OpenAiResponsesClient(String url, String modelName, String apiKey, String systemPrompt) {
    super(url, modelName, apiKey, systemPrompt);
    String rawKey = this.apiKey.startsWith("Bearer ") ? this.apiKey.substring("Bearer ".length()) : this.apiKey;
    this.client = OpenAIOkHttpClient.builder()
      .baseUrl(url)
      .apiKey(rawKey)
      .build();
  }

  @Override
  public Message response(List<Message> messages) {
    ResponseCreateParams params = buildParams(messages);
    var response = client.responses().create(params);
    String content = response.output().stream()
      .filter(ResponseOutputItem::isMessage)
      .findFirst()
      .orElseThrow(() -> new RuntimeException("No message output found"))
      .asMessage()
      .content().stream()
      .filter(ResponseOutputMessage.Content::isOutputText)
      .findFirst()
      .orElseThrow(() -> new RuntimeException("No output text found"))
      .asOutputText()
      .text();
    System.out.println(content);
    return new Message(Role.ASSISTANT, content);
  }

  @Override
  public Message streamResponse(List<Message> messages) {
    ResponseCreateParams params = buildParams(messages);
    StringBuilder sb = new StringBuilder();
    try (var stream = client.responses().createStreaming(params)) {
      stream.stream()
        .filter(ResponseStreamEvent::isOutputTextDelta)
        .forEach(event -> {
          String delta = event.asOutputTextDelta().delta();
          System.out.print(delta);
          sb.append(delta);
        });
    }
    System.out.println();
    return new Message(Role.ASSISTANT, sb.toString());
  }

  private ResponseCreateParams buildParams(List<Message> messages) {
    List<ResponseInputItem> inputItems = messages.stream()
      .map(msg -> ResponseInputItem.ofEasyInputMessage(
        EasyInputMessage.builder()
          .role(EasyInputMessage.Role.of(msg.role().getValue()))
          .content(msg.content())
          .build()
      ))
      .collect(Collectors.toList());

    return ResponseCreateParams.builder()
      .model(ResponsesModel.ofString(modelName))
      .instructions(systemPrompt)
      .inputOfResponse(inputItems)
      .build();
  }
}
