package t4.rag.basics.langchain;

import com.openai.client.OpenAIClient;
import com.openai.client.okhttp.OpenAIOkHttpClient;
import com.openai.models.chat.completions.ChatCompletionCreateParams;
import commons.Constants;
import commons.exceptions.TaskNotImplementedException;
import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.document.loader.FileSystemDocumentLoader;
import dev.langchain4j.data.document.splitter.DocumentSplitters;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.openai.OpenAiEmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingMatch;
import dev.langchain4j.store.embedding.EmbeddingSearchRequest;
import dev.langchain4j.store.embedding.EmbeddingSearchResult;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.inmemory.InMemoryEmbeddingStore;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;

public class RagApp {

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
  private static final Path INDEX_PATH = Paths.get("tasks/src/t4/rag/basics/langchain/microwave_index.json");

  private final EmbeddingModel embeddingModel;
  private final OpenAIClient openAiClient;
  private final EmbeddingStore<TextSegment> embeddingStore;

  private RagApp(EmbeddingModel embeddingModel) {
    this.embeddingModel = embeddingModel;
    this.openAiClient = OpenAIOkHttpClient.builder()
      .apiKey(Constants.OPENAI_API_KEY)
      .build();
    this.embeddingStore = setupEmbeddingStore();
  }

  private EmbeddingStore<TextSegment> setupEmbeddingStore() {
    System.out.println("=== Setting up Embedding Store ===");
    if (Files.exists(INDEX_PATH)) {
      InMemoryEmbeddingStore<TextSegment> store = InMemoryEmbeddingStore.fromFile(INDEX_PATH);
      System.out.println("Index loaded from: " + INDEX_PATH);
      return store;
    }
    return createNewIndex();
  }

  private InMemoryEmbeddingStore<TextSegment> createNewIndex() {
    System.out.println("Loading document from: " + MANUAL_PATH);
    Document document = FileSystemDocumentLoader.loadDocument(MANUAL_PATH);

    System.out.println("Splitting document into chunks...");
    List<TextSegment> segments = DocumentSplitters.recursive(300, 50).split(document);
    System.out.println("Created " + segments.size() + " chunks.");

    System.out.println("Embedding and indexing chunks...");
    InMemoryEmbeddingStore<TextSegment> store = new InMemoryEmbeddingStore<>();
    List<Embedding> embeddings = embeddingModel.embedAll(segments).content();
    store.addAll(embeddings, segments);

    store.serializeToFile(INDEX_PATH);
    System.out.println("Index saved to: " + INDEX_PATH);
    System.out.println("Embedding store populated successfully.");
    return store;
  }

  private String retrieveContext(String query, int k, double minScore) {
    System.out.println("\n=== RETRIEVAL ===");
    System.out.println("Query: " + query + " | maxResults=" + k + " | minScore=" + minScore);
    Embedding queryEmbedding = embeddingModel.embed(query).content();
    EmbeddingSearchRequest request = EmbeddingSearchRequest.builder()
      .queryEmbedding(queryEmbedding)
      .maxResults(k)
      .minScore(minScore)
      .build();
    EmbeddingSearchResult<TextSegment> result = embeddingStore.search(request);
    List<String> parts = new ArrayList<>();
    for (EmbeddingMatch<TextSegment> match : result.matches()) {
      String content = match.embedded().text();
      System.out.println(
        "  [score=" + match.score() + "] " + content.substring(0, Math.min(80, content.length())) + "...");
      parts.add(content);
    }
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
      .choices().getFirst().message().content().orElse("");
    System.out.println(answer);
    return answer;
  }

  public static void main(String[] args) {
    RagApp app = new RagApp(
      OpenAiEmbeddingModel.builder()
        .apiKey(Constants.OPENAI_API_KEY)
        .modelName("text-embedding-3-small")
        .build()
    );
    System.out.println("\nWelcome to the Microwave Manual RAG Assistant! Type your question (Ctrl+C to exit).");
    Scanner scanner = new Scanner(System.in);
    while (true) {
      System.out.print("\nYou: ");
      String query = scanner.nextLine().trim();
        if (query.isBlank()) {
            continue;
        }
      String context = app.retrieveContext(query, 4, 0.7);
      String augmented = app.augmentPrompt(query, context);
      app.generateAnswer(augmented);
    }
  }
}
