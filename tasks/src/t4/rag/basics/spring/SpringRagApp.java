package t4.rag.basics.spring;

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
import org.springframework.ai.reader.TextReader;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.SimpleVectorStore;
import org.springframework.core.io.FileSystemResource;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Scanner;
import java.util.stream.Collectors;

public class SpringRagApp {

  private static final String SYSTEM_PROMPT = """
    You are a RAG-powered assistant that assists users with their questions about microwave usage.
    
    ## Structure of User message:
    `RAG CONTEXT` - Retrieved documents relevant to the query.
    `USER QUESTION` - The user's actual question.
    
    ## Instructions:
    - Use information from `RAG CONTEXT` as context when answering the `USER QUESTION`.
    - Cite specific sources when using information from the context.
    - Answer ONLY based on conversation history and RAG context.
    - If no relevant information exists in `RAG CONTEXT` or conversation history, state that you cannot answer the question.
    """;

  private static final String USER_PROMPT_TEMPLATE =
    "##RAG CONTEXT:\n{context}\n\n\n##USER QUESTION: \n{query}";

  private static final String MANUAL_PATH = "tasks/src/t4/rag/basics/microwave_manual.txt";
  private static final Path INDEX_PATH = Paths.get("tasks/src/t4/rag/basics/spring/microwave_index.json");

  private final OpenAiEmbeddingModel embeddingModel;
  private final OpenAIClient openAiClient;
  private final SimpleVectorStore vectorStore;

  private SpringRagApp(OpenAiEmbeddingModel embeddingModel) {
    this.embeddingModel = embeddingModel;
    this.openAiClient = OpenAIOkHttpClient.builder()
      .apiKey(Constants.OPENAI_API_KEY)
      .build();
    this.vectorStore = setupVectorStore();
  }

  private SimpleVectorStore setupVectorStore() {
    System.out.println("=== Setting up Vector Store ===");
    SimpleVectorStore store = SimpleVectorStore.builder(embeddingModel).build();
    if (Files.exists(INDEX_PATH)) {
      store.load(INDEX_PATH.toFile());
      System.out.println("Index loaded from: " + INDEX_PATH);
    } else {
      populateStore(store);
    }
    return store;
  }

  private void populateStore(SimpleVectorStore store) {
    System.out.println("Loading documents from: " + MANUAL_PATH);
    List<Document> documents = new TextReader(new FileSystemResource(MANUAL_PATH)).get();

    System.out.println("Splitting documents into chunks...");
    List<Document> chunks = TokenTextSplitter.builder()
      .withChunkSize(75)
      .withMinChunkSizeChars(50)
      .withMinChunkLengthToEmbed(5)
      .withMaxNumChunks(10000)
      .withKeepSeparator(true)
      .build()
      .apply(documents);
    System.out.println("Created " + chunks.size() + " chunks.");

    System.out.println("Embedding and indexing chunks...");
    store.add(chunks);
    store.save(INDEX_PATH.toFile());
    System.out.println("Index saved to: " + INDEX_PATH);
    System.out.println("Vector store populated successfully.");
  }

  private String retrieveContext(String query, int k, double minScore) {
    System.out.println("\n=== RETRIEVAL ===");
    System.out.println("Query: " + query + " | topK=" + k + " | minScore=" + minScore);
    SearchRequest request = SearchRequest.builder()
      .query(query)
      .topK(k)
      .similarityThreshold(minScore)
      .build();
    List<String> parts = vectorStore.similaritySearch(request).stream()
      .peek(doc -> doc.getMetadata().entrySet().stream()
        .filter(e -> e.getKey().equals("distance") || e.getKey().equals("score"))
        .forEach(e -> System.out.println(
          "  [score=" + e.getValue() + "] " + doc.getText().substring(0, Math.min(80, doc.getText().length()))
            + "...")))
      .map(Document::getText)
      .collect(Collectors.toList());
    return String.join("\n\n", parts);
  }

  private String augmentPrompt(String query, String context) {
    System.out.println("\n=== AUGMENTATION ===");
    String augmented = USER_PROMPT_TEMPLATE
      .replace("{context}", context)
      .replace("{query}", query);
    System.out.println(augmented);
    return augmented;
  }

  private String generateAnswer(String augmentedPrompt) {
    System.out.println("\n=== GENERATION ===");
    ChatCompletionCreateParams params = ChatCompletionCreateParams.builder()
      .model(Constants.GPT_4O_MINI)
      .temperature(0.0)
      .addSystemMessage(SYSTEM_PROMPT)
      .addUserMessage(augmentedPrompt)
      .build();
    String answer = openAiClient.chat().completions().create(params)
      .choices().get(0).message().content().orElse("");
    System.out.println(answer);
    return answer;
  }

  public static void main(String[] args) {
    OpenAiEmbeddingModel embeddingModel = new OpenAiEmbeddingModel(
      OpenAiApi.builder().apiKey(Constants.OPENAI_API_KEY).build(),
      MetadataMode.EMBED,
      OpenAiEmbeddingOptions.builder().model("text-embedding-3-small").build()
    );
    SpringRagApp app = new SpringRagApp(embeddingModel);
    System.out.println("\nWelcome to the Microwave Manual RAG Assistant! Type your question (Ctrl+C to exit).");
    Scanner scanner = new Scanner(System.in);
    while (true) {
      System.out.print("\nYou: ");
      String query = scanner.nextLine().trim();
        if (query.isBlank()) {
            continue;
        }
      String context = app.retrieveContext(query, 4, 0.3);
      String augmented = app.augmentPrompt(query, context);
      app.generateAnswer(augmented);
    }
  }
}
