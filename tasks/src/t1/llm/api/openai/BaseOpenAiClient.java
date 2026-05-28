package t1.llm.api.openai;

import t1.llm.api.AiClient;

/**
 * Abstract base for OpenAI clients.
 * <p>
 * Validates the raw API key and prepends "Bearer " before storing it, matching
 * the Authorization header format required by the OpenAI REST API.
 */
public abstract class BaseOpenAiClient extends AiClient {

  protected BaseOpenAiClient(String url, String modelName, String apiKey, String systemPrompt) {
    super(url, modelName, withBearer(apiKey), systemPrompt);
  }

  private static String withBearer(String apiKey) {
    if (apiKey == null || apiKey.isBlank()) {
      throw new IllegalArgumentException("apiKey must not be null or blank");
    }
    return "Bearer " + apiKey;
  }
}
