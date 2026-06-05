package t6.grounding.t2;

import com.openai.client.OpenAIClient;
import com.openai.client.okhttp.OpenAIOkHttpClient;
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

import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

public class InputVectorBasedGroundingApp {

    //TODO:
    // Define SYSTEM_PROMPT - instructs the LLM to act as a RAG-powered assistant:
    //   - The user message contains two sections: RAG CONTEXT and USER QUESTION
    //   - Answer ONLY based on the provided RAG CONTEXT and conversation history
    //   - If no relevant information exists in RAG CONTEXT, state that the question cannot be answered
    private static final String SYSTEM_PROMPT = """
            You are a RAG-powered assistant.
            The user message contains two sections:
            - RAG CONTEXT: retrieved user data relevant to the query.
            - USER QUESTION: the user's actual question.
            Instructions:
            - Answer ONLY based on the provided RAG CONTEXT and conversation history.
            - If no relevant information exists in RAG CONTEXT, clearly state that you cannot answer the question.
            """;

    //TODO:
    // Define USER_PROMPT template with two placeholders:
    //   - {context} - the retrieved user data from vector store
    //   - {query}   - the user's question
    private static final String USER_PROMPT = """
            ## RAG CONTEXT:
            {context}

            ## USER QUESTION:
            {query}
            """;

    private final OpenAIClient openAiClient;
    private final SimpleVectorStore vectorStore;

    public InputVectorBasedGroundingApp(OpenAiEmbeddingModel embeddingModel) {
        this.openAiClient = OpenAIOkHttpClient.builder()
                .apiKey(Constants.OPENAI_API_KEY)
                .build();
        this.vectorStore = buildVectorStore(embeddingModel);
    }

    private SimpleVectorStore buildVectorStore(OpenAiEmbeddingModel embeddingModel) {
        System.out.println("🔎 Loading all users...");
        List<User> users = new UserService().getAllUsers();
        System.out.println("↗️ Formatting " + users.size() + " user documents and creating embeddings...");
        List<Document> documents = users.stream()
                .map(u -> Document.builder().id(String.valueOf(u.id())).text(u.toDocument()).build())
                .collect(Collectors.toList());
        SimpleVectorStore store = SimpleVectorStore.builder(embeddingModel).build();
        addInParallel(store, documents, 50);
        System.out.println("✅ Vectorstore is ready.");
        return store;
    }

    private static void addInParallel(SimpleVectorStore store, List<Document> documents, int batchSize) {
        List<CompletableFuture<Void>> futures = new ArrayList<>();
        for (int i = 0; i < documents.size(); i += batchSize) {
            List<Document> batch = documents.subList(i, Math.min(i + batchSize, documents.size()));
            futures.add(CompletableFuture.runAsync(() -> store.add(batch)));
        }
        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
    }

    private String retrieveContext(String query, int k, double minScore) {
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

        InputVectorBasedGroundingApp app = new InputVectorBasedGroundingApp(embeddingModel);

        System.out.println("Query samples:");
        System.out.println(" - I need user emails that filled with hiking and psychology");
        System.out.println(" - Who is John?");

        Scanner scanner = new Scanner(System.in);
        while (true) {
            System.out.print("\n> ");
            System.out.flush();
            if (!scanner.hasNextLine()) break;
            String query = scanner.nextLine().strip();
            if (query.isEmpty()) continue;
            if (query.equalsIgnoreCase("quit") || query.equalsIgnoreCase("exit")) break;

            String context  = app.retrieveContext(query, 10, 0.1);
            String augmented = app.augmentPrompt(query, context);
            String answer   = app.generateAnswer(augmented);
            System.out.println("\nAnswer: " + answer);
        }
    }
}

