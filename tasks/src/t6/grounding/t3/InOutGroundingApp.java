package t6.grounding.t3;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.openai.client.OpenAIClient;
import com.openai.client.okhttp.OpenAIOkHttpClient;
import com.openai.models.ResponseFormatJsonObject;
import com.openai.models.chat.completions.ChatCompletionCreateParams;
import commons.Constants;
import commons.exceptions.TaskNotImplementedException;
import org.springframework.ai.document.Document;
import org.springframework.ai.document.MetadataMode;
import org.springframework.ai.openai.OpenAiEmbeddingModel;
import org.springframework.ai.openai.OpenAiEmbeddingOptions;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.SimpleVectorStore;
import t6.grounding.User;
import t6.grounding.UserService;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

/**
 * HOBBIES SEARCHER:
 * Searches users by hobbies and provides their full info:
 * Input: I need to gather people that love to go to mountains
 * Output:
 *    rock climbing: [{full user info},...],
 *    hiking: [{full user info},...],
 *    camping: [{full user info},...]
 */
public class InOutGroundingApp {

    //TODO:
    // Define SYSTEM_PROMPT - instructs the LLM to group users by hobby (Named Entity Extraction):
    //   - Context will contain user IDs and about_me sections from the vector store
    //   - Group users by hobby: each matching user ID should appear under its relevant hobby
    //   - Return only valid JSON matching this format:
    //     {"grouping_results": [{"hobby": "hiking", "user_ids": [1, 2, 3]}, {"hobby": "camping", "user_ids": [4, 5]}]}
    private static final String SYSTEM_PROMPT = """
            You are a hobby-grouping assistant (Named Entity Extraction).
            The user message contains user IDs and their about_me sections.
            Your task:
            - Analyze each user's about_me section.
            - Identify hobbies that match the user's search query.
            - Group matching user IDs under their relevant hobby.
            - Return ONLY valid JSON in this exact format (no extra text):
              {"grouping_results": [{"hobby": "hiking", "user_ids": [1, 2, 3]}, {"hobby": "camping", "user_ids": [4, 5]}]}
            - If no users match, return: {"grouping_results": []}
            """;

    //TODO:
    // Define USER_PROMPT template with two placeholders:
    //   - {context} - the retrieved user hobby data from vector store (id + about_me only)
    //   - {query}   - the user's search question
    private static final String USER_PROMPT = """
            ## USER DATA:
            {context}

            ## SEARCH QUERY:
            {query}
            """;

    private final OpenAIClient openAiClient;
    private final UserService userService;
    private final SimpleVectorStore vectorStore;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final Set<String> knownUserIds = new HashSet<>();

    public InOutGroundingApp(OpenAiEmbeddingModel embeddingModel) {
        this.openAiClient = OpenAIOkHttpClient.builder()
                .apiKey(Constants.OPENAI_API_KEY)
                .build();
        this.userService = new UserService();
        this.vectorStore = SimpleVectorStore.builder(embeddingModel).build();
        initializeVectorStore();
    }

    private void initializeVectorStore() {
        System.out.println("🔍 Loading all users for initial vectorstore...");
        List<User> users = userService.getAllUsers();
        List<Document> documents = users.stream()
                .map(u -> Document.builder().id(String.valueOf(u.id())).text(u.toHobbyDocument()).build())
                .collect(Collectors.toList());
        addInParallel(vectorStore, documents, 50);
        users.forEach(u -> knownUserIds.add(String.valueOf(u.id())));
        System.out.println("Setup FINISHED");
    }

    private void updateVectorStore() {
        List<User> currentUsers = userService.getAllUsers();
        Map<String, User> currentMap = currentUsers.stream()
                .collect(Collectors.toMap(u -> String.valueOf(u.id()), u -> u));
        Set<String> currentIds = currentMap.keySet();

        Set<String> newIds = new HashSet<>(currentIds);
        newIds.removeAll(knownUserIds);

        Set<String> deletedIds = new HashSet<>(knownUserIds);
        deletedIds.removeAll(currentIds);

        if (!deletedIds.isEmpty()) {
            vectorStore.delete(new ArrayList<>(deletedIds));
            knownUserIds.removeAll(deletedIds);
            System.out.println("Deleted " + deletedIds.size() + " users from vectorstore.");
        }
        if (!newIds.isEmpty()) {
            List<Document> newDocuments = newIds.stream()
                    .map(id -> currentMap.get(id))
                    .map(u -> Document.builder().id(String.valueOf(u.id())).text(u.toHobbyDocument()).build())
                    .collect(Collectors.toList());
            addInParallel(vectorStore, newDocuments, 50);
            knownUserIds.addAll(newIds);
            System.out.println("Added " + newIds.size() + " new users to vectorstore.");
        }
    }

    private String retrieveContext(String query, int k, double minScore) {
        updateVectorStore();
        System.out.println("Retrieving context...");
        SearchRequest request = SearchRequest.builder()
                .query(query)
                .topK(k)
                .similarityThreshold(minScore)
                .build();
        List<String> contextParts = vectorStore.similaritySearch(request).stream()
                .peek(doc -> System.out.println("Retrieved (Score: " +
                        doc.getMetadata().getOrDefault("distance", "N/A") + "): " + doc.getText()))
                .map(Document::getText)
                .collect(Collectors.toList());
        System.out.println("=".repeat(100));
        return String.join("\n\n", contextParts);
    }

    private String augmentPrompt(String query, String context) {
        return USER_PROMPT
                .replace("{context}", context)
                .replace("{query}", query);
    }

    private List<GroupingResult> generateGroupingResults(String augmentedPrompt) {
        try {
            ChatCompletionCreateParams params = ChatCompletionCreateParams.builder()
                    .model(Constants.GPT_4_1_NANO)
                    .temperature(0.0)
                    .addSystemMessage(SYSTEM_PROMPT)
                    .addUserMessage(augmentedPrompt)
                    .responseFormat(ResponseFormatJsonObject.builder().build())
                    .build();
            String json = openAiClient.chat().completions().create(params)
                    .choices().getFirst().message().content().orElse("{}");

            JsonNode groupingsNode = objectMapper.readTree(json).path("grouping_results");
            if (groupingsNode.isMissingNode() || !groupingsNode.isArray()) {
                return List.of();
            }

            List<GroupingResult> results = new ArrayList<>();
            for (JsonNode node : groupingsNode) {
                String hobby = node.path("hobby").asText();
                List<Integer> userIds = new ArrayList<>();
                for (JsonNode idNode : node.path("user_ids")) {
                    userIds.add(idNode.asInt());
                }
                results.add(new GroupingResult(hobby, userIds));
            }
            return results;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private void groundResponse(List<GroupingResult> groupingResults) {
        for (GroupingResult result : groupingResults) {
            System.out.println("Hobby: " + result.hobby());
            result.userIds().stream()
                    .map(id -> userService.getUser(id))
                    .flatMap(Optional::stream)
                    .forEach(user -> System.out.println(user.toDocument()));
            System.out.println("----------");
        }
    }

    private static void addInParallel(SimpleVectorStore store, List<Document> documents, int batchSize) {
        List<List<Document>> batches = new ArrayList<>();
        for (int i = 0; i < documents.size(); i += batchSize) {
            batches.add(documents.subList(i, Math.min(i + batchSize, documents.size())));
        }
        List<CompletableFuture<Void>> futures = batches.stream()
                .map(batch -> CompletableFuture.runAsync(() -> store.add(batch)))
                .toList();
        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
    }

    private record GroupingResult(String hobby, List<Integer> userIds) {}

    public static void main(String[] args) {
        OpenAiEmbeddingModel embeddingModel = new OpenAiEmbeddingModel(
                OpenAiApi.builder()
                        .apiKey(Constants.OPENAI_API_KEY)
                        .build(),
                MetadataMode.EMBED,
                OpenAiEmbeddingOptions.builder()
                        .model("text-embedding-3-small")
                        .dimensions(384)
                        .build()
        );

        InOutGroundingApp app = new InOutGroundingApp(embeddingModel);

        System.out.println("Query samples:");
        System.out.println(" - I need people who love to go to mountains");
        System.out.println(" - Find people who love to watch stars and night sky");
        System.out.println(" - I need people to go to fishing together");

        Scanner scanner = new Scanner(System.in);
        while (true) {
            System.out.print("\n> ");
            System.out.flush();
            if (!scanner.hasNextLine()) break;
            String query = scanner.nextLine().strip();
            if (query.isEmpty()) continue;
            if (query.equalsIgnoreCase("quit") || query.equalsIgnoreCase("exit")) break;

            String context   = app.retrieveContext(query, 100, 0.2);
            String augmented = app.augmentPrompt(query, context);
            List<GroupingResult> results = app.generateGroupingResults(augmented);
            app.groundResponse(results);
        }
    }
}
