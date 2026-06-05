package t6.grounding.t2;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.openai.client.OpenAIClient;
import com.openai.client.okhttp.OpenAIOkHttpClient;
import com.openai.models.ResponseFormatJsonObject;
import com.openai.models.chat.completions.ChatCompletionCreateParams;
import commons.Constants;
import commons.exceptions.TaskNotImplementedException;
import t6.grounding.User;
import t6.grounding.UserService;

import java.util.List;
import java.util.Scanner;
import java.util.stream.Collectors;

public class InputApiBasedGroundingApp {

    private static final String QUERY_ANALYSIS_PROMPT = """
            You are a query analysis system. Your task is to extract explicit search parameters from the user's question.
            Available search fields: name, surname, email.
            - Analyze the user question and extract only values that are clearly and explicitly stated.
            - Map extracted values to the appropriate search fields.
            - Do NOT infer, assume, or guess values that are not directly mentioned.
            - Examples:
                "Who is John?" → name: "John"
                "Find John Smith" → name: "John", surname: "Smith"
                "Show me user with email test@test.com" → email: "test@test.com"
            - Return a JSON object in this exact format:
            {
              "search_request_parameters": [
                {"search_field": "name", "search_value": "John"},
                {"search_field": "surname", "search_value": "Smith"}
              ]
            }
            - If no explicit values can be extracted, return: {"search_request_parameters": []}
            """;

    private static final String SYSTEM_PROMPT = """
            You are a RAG-powered assistant.
            The user message contains two sections:
            - RAG CONTEXT: retrieved user data relevant to the query.
            - USER QUESTION: the user's actual question.
            Instructions:
            - Answer ONLY based on the provided RAG CONTEXT and conversation history.
            - If no relevant information exists in RAG CONTEXT, clearly state that you cannot answer the question.
            - Format user information clearly and in an organized manner when presenting it.
            """;

    private static final String USER_PROMPT = """
            ## RAG CONTEXT:
            {context}

            ## USER QUESTION:
            {query}
            """;

    private final OpenAIClient openAiClient;
    private final UserService userService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public InputApiBasedGroundingApp() {
        this.openAiClient = OpenAIOkHttpClient.builder()
                .apiKey(Constants.OPENAI_API_KEY)
                .build();
        this.userService = new UserService();
    }

    private List<User> retrieveContext(String userQuestion) {
        try {
            ChatCompletionCreateParams params = ChatCompletionCreateParams.builder()
                    .model(Constants.GPT_4_1_NANO)
                    .temperature(0.0)
                    .addSystemMessage(QUERY_ANALYSIS_PROMPT)
                    .addUserMessage(userQuestion)
                    .responseFormat(ResponseFormatJsonObject.builder().build())
                    .build();
            String json = openAiClient.chat().completions().create(params)
                    .choices().getFirst().message().content().orElse("{}");

            JsonNode paramsArray = objectMapper.readTree(json).path("search_request_parameters");
            if (paramsArray.isMissingNode() || paramsArray.isEmpty()) {
                System.out.println("No specific search parameters found!");
                return List.of();
            }

            String name = null, surname = null, email = null;
            for (JsonNode param : paramsArray) {
                String field = param.path("search_field").asText();
                String value = param.path("search_value").asText();
                switch (field) {
                    case "name"    -> name = value;
                    case "surname" -> surname = value;
                    case "email"   -> email = value;
                }
            }

            System.out.println("Search parameters — name: " + name + ", surname: " + surname + ", email: " + email);
            return userService.searchUsers(name, surname, email);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private String augmentPrompt(String userQuestion, List<User> context) {
        String contextStr = context.stream().map(User::toDocument).collect(Collectors.joining("\n"));
        String augmented = USER_PROMPT
                .replace("{context}", contextStr)
                .replace("{query}", userQuestion);
        System.out.println(augmented);
        return augmented;
    }

    private String generateAnswer(String augmentedPrompt) {
        ChatCompletionCreateParams params = ChatCompletionCreateParams.builder()
                .model(Constants.GPT_4O_MINI)
                .temperature(0.0)
                .addSystemMessage(SYSTEM_PROMPT)
                .addUserMessage(augmentedPrompt)
                .build();
        return openAiClient.chat().completions().create(params)
                .choices().getFirst().message().content().orElse("");
    }

    public static void main(String[] args) {
        InputApiBasedGroundingApp app = new InputApiBasedGroundingApp();

        System.out.println("Query samples:");
        System.out.println(" - I need user emails that filled with hiking and psychology");
        System.out.println(" - Who is John?");
        System.out.println(" - Find users with surname Adams");
        System.out.println(" - Do we have smbd with name John that love painting?");

        Scanner scanner = new Scanner(System.in);
        while (true) {
            System.out.print("\n> ");
            System.out.flush();
            if (!scanner.hasNextLine()) break;
            String userQuestion = scanner.nextLine().strip();
            if (userQuestion.isEmpty()) continue;
            if (userQuestion.equalsIgnoreCase("quit") || userQuestion.equalsIgnoreCase("exit")) break;

            System.out.println("\n--- Retrieving context ---");
            List<User> context = app.retrieveContext(userQuestion);
            if (!context.isEmpty()) {
                System.out.println("\n--- Augmenting prompt ---");
                String augmented = app.augmentPrompt(userQuestion, context);
                System.out.println("\n--- Generating answer ---");
                String answer = app.generateAnswer(augmented);
                System.out.println("\nAnswer: " + answer + "\n");
            } else {
                System.out.println("\n--- No relevant information found ---");
            }
        }
    }
}

// The problems with API based Grounding approach are:
//   - We need a Pre-Step to figure out what field should be used for search (Takes time)
//   - Values for search should be correct (✅ John -> ❌ Jonh)
//   - Is not so flexible
// Benefits are:
//   - We fetch actual data (new users added and deleted every 5 minutes)
//   - Costs reduce
