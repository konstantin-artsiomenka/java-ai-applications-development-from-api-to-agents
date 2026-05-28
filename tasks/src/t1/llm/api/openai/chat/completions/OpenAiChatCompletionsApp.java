package t1.llm.api.openai.chat.completions;

import t1.llm.api.BaseApp;

import static commons.Constants.DEFAULT_SYSTEM_PROMPT;
import static commons.Constants.GPT_5_4;
import static commons.Constants.OPENAI_API_KEY;
import static commons.Constants.OPENAI_HOST;

public class OpenAiChatCompletionsApp {

  static void main(String[] args) {
    OpenAiChatCompletionsClient sdkClient =
      new OpenAiChatCompletionsClient(OPENAI_HOST, GPT_5_4, OPENAI_API_KEY, DEFAULT_SYSTEM_PROMPT);
    CustomOpenAiChatCompletionsClient customClient =
      new CustomOpenAiChatCompletionsClient(OPENAI_HOST, GPT_5_4, OPENAI_API_KEY, DEFAULT_SYSTEM_PROMPT);

    // Switch between sdkClient and customClient to compare SDK vs raw HTTP
    BaseApp.start(true, customClient);
  }
}
