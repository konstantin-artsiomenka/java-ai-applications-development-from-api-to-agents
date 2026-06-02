package t2.llms.output.tuning.clients;

import commons.Constants;
import commons.model.Message;
import commons.model.Role;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class OpenAiChatCompletionsClient extends AIClient {

  public OpenAiChatCompletionsClient(String modelName) {
    super(
      Constants.OPENAI_HOST,
      modelName,
      "Bearer " + Constants.OPENAI_API_KEY,
      "Authorization"
    );
  }

  @Override
  public Message response(
    List<Message> messages,
    boolean printRequest,
    boolean printOnlyContent,
    Map<String, Object> options
  ) {
    var headers = Map.of(
      "Authorization", apiKey,
      "Content-Type", "application/json"
    );
    var requestData = new LinkedHashMap<String, Object>();
    requestData.put("model", modelName);
    requestData.put("messages", messages.stream().map(Message::toMap).toList());
    requestData.put("n", 1);
    requestData.put("temperature", 1.0);
    requestData.put("top_p", 1.0);
    requestData.put("max_completion_tokens", 2048);
    Object[] array = new Object[1];
    array[0] = "5";
    requestData.put("stop", array);
    //        requestData.putAll(deepCopy(options));

    if (printRequest) {
      printRequest(requestData, headers);
    }

    var responseBody = post(endpoint, headers, requestData);
    try {
      var json = MAPPER.readTree(responseBody);
      var content = json.path("choices").path(0).path("message").path("content").asText(null);
      if (content == null) {
        throw new IllegalStateException("No Choice has been present in the response");
      }

      printResponse(MAPPER.readValue(responseBody, Object.class), printOnlyContent, content);
      return new Message(Role.ASSISTANT, content);
    } catch (IOException e) {
      throw new RuntimeException("Failed to parse OpenAI Chat Completions response", e);
    }
  }
}
