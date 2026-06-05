package t6.grounding.t1;

import com.openai.client.OpenAIClient;
import com.openai.client.okhttp.OpenAIOkHttpClient;
import com.openai.models.chat.completions.ChatCompletionCreateParams;
import commons.Constants;
import commons.exceptions.TaskNotImplementedException;
import t6.grounding.User;
import t6.grounding.UserService;

import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

public class NoGroundingApp {

    private static final String BATCH_SYSTEM_PROMPT = """
            You are a user search assistant.
            - Carefully analyze the search criteria from the user's question.
            - Examine each user in the provided list and determine if they match the criteria.
            - Return the full details of all matching users in their original format.
            - If no users match, return exactly: NO_MATCHES_FOUND
            """;

    private static final String FINAL_SYSTEM_PROMPT = """
            You are a user search assistant compiling final search results.
            - Review all batch search results provided.
            - Combine and deduplicate matching users found across all batches.
            - Present the final results in a clear, organized manner.
            """;

    // ...existing code...

    private static final String USER_PROMPT = """
            ## USER DATA:
            {context}

            ## SEARCH QUERY:\s
            {query}""";

    private final OpenAIClient openAiClient;
    private final UserService userService;
    private final AtomicInteger totalTokens = new AtomicInteger(0);
    private final List<Integer> batchTokens = new CopyOnWriteArrayList<>();

    public NoGroundingApp() {
        this.openAiClient = OpenAIOkHttpClient.builder()
                .apiKey(Constants.OPENAI_API_KEY)
                .build();
        this.userService = new UserService();
    }

    private String generateResponse(String systemPrompt, String userMessage) {
        System.out.println("Processing...");
        ChatCompletionCreateParams params = ChatCompletionCreateParams.builder()
                .model(Constants.GPT_4_1_NANO)
                .temperature(0.0)
                .addSystemMessage(systemPrompt)
                .addUserMessage(userMessage)
                .build();
        var completion = openAiClient.chat().completions().create(params);
        int tokens = completion.usage().map(u -> (int) u.totalTokens()).orElse(0);
        totalTokens.addAndGet(tokens);
        batchTokens.add(tokens);
        String content = completion.choices().get(0).message().content().orElse("");
        System.out.println("Response: " + content);
        System.out.println("Tokens used: " + tokens);
        return content;
    }

    public void run(String userQuestion) {
        System.out.println("\n--- Searching user database ---");
        List<User> allUsers = userService.getAllUsers();

        List<CompletableFuture<String>> futures = new ArrayList<>();
        for (int i = 0; i < allUsers.size(); i += 100) {
            List<User> batch = allUsers.subList(i, Math.min(i + 100, allUsers.size()));
            String context = batch.stream().map(User::toDocument).collect(Collectors.joining("\n"));
            String userPrompt = USER_PROMPT
                    .replace("{context}", context)
                    .replace("{query}", userQuestion);
            futures.add(CompletableFuture.supplyAsync(() -> generateResponse(BATCH_SYSTEM_PROMPT, userPrompt)));
        }

        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
        List<String> batchResults = futures.stream()
                .map(CompletableFuture::join)
                .toList();

        System.out.println("\n--- Compiling results ---");
        List<String> relevantResults = batchResults.stream()
                .filter(r -> !r.strip().equals("NO_MATCHES_FOUND"))
                .toList();

        System.out.println("\n=== SEARCH RESULTS ===");
        if (!relevantResults.isEmpty()) {
            String combinedContext = String.join("\n\n", relevantResults);
            String finalPrompt = USER_PROMPT
                    .replace("{context}", combinedContext)
                    .replace("{query}", userQuestion);
            generateResponse(FINAL_SYSTEM_PROMPT, finalPrompt);
        } else {
            System.out.println("No users found matching your criteria. Please try refining your search query.");
        }

        System.out.println("\n=== Performance ===");
        System.out.println("Total API calls: " + batchTokens.size());
        System.out.println("Total tokens used: " + totalTokens.get());
    }

    public static void main(String[] args) {
        NoGroundingApp app = new NoGroundingApp();

        System.out.println("Query samples:");
        System.out.println(" - Do we have someone with name John that loves traveling?");

        Scanner scanner = new Scanner(System.in);
        while (true) {
            System.out.print("\n> ");
            System.out.flush();
            if (!scanner.hasNextLine()) break;
            String query = scanner.nextLine().strip();
            if (query.isEmpty()) continue;
            app.run(query);
        }
    }
}

// The problems with No Grounding approach are:
//   - If we load whole users as context in one request to LLM we will hit context window
//   - Huge token usage == Higher price per request
//   - Added + one chain in flow where original user data can be changed by LLM (before final generation)
// User Question -> Get all users -> ‼️parallel search of possible candidates‼️ -> probably changed original context -> final generation
