package t5.rag.advanced;

import commons.Constants;
import commons.model.Conversation;
import commons.model.Message;
import commons.model.Role;
import t5.rag.advanced.chat.ChatCompletionClient;
import t5.rag.advanced.embeddings.EmbeddingsClient;
import t5.rag.advanced.embeddings.SearchMode;
import t5.rag.advanced.embeddings.TextProcessor;

import java.util.List;
import java.util.Scanner;

public class App {

    private static final String MANUAL_PATH = "tasks/src/t5/rag/advanced/microwave_manual.txt";

    //...existing code...
    private static final String SYSTEM_PROMPT = """
            You are a RAG-powered assistant that helps users with questions about microwave usage.

            ## Structure of User message:
            `RAG CONTEXT` - Retrieved documents relevant to the query.
            `USER QUESTION` - The user's actual question.

            ## Instructions:
            - Answer using information from `RAG CONTEXT` and conversation history.
            - Cite specific sources when referencing context information.
            - Restrict your answers to microwave-related topics covered in the context or history.
            - If the question is unrelated to microwave usage or the answer is not found in the context, clearly state that you cannot answer it.
            """;

    private static final String USER_PROMPT_TEMPLATE = """
            ##RAG CONTEXT:
            {context}


            ##USER QUESTION:
            {query}
            """;

    public static void main(String[] args) {
        EmbeddingsClient embeddingsClient = new EmbeddingsClient(
                Constants.OPENAI_EMBEDDINGS_ENDPOINT, "text-embedding-3-small", Constants.OPENAI_API_KEY);
        ChatCompletionClient completionClient = new ChatCompletionClient(
                Constants.OPENAI_HOST + "/chat/completions", Constants.GPT_4O_MINI, Constants.OPENAI_API_KEY);
        TextProcessor textProcessor = new TextProcessor(
                embeddingsClient, "localhost", 5433, "vectordb", "postgres", "postgres");

        Scanner scanner = new Scanner(System.in);
        System.out.print("Load context from file? (y/n): ");
        if ("y".equalsIgnoreCase(scanner.nextLine().trim())) {
            System.out.println("Loading and indexing context...");
            textProcessor.processTextFile(MANUAL_PATH, 400, 40, 384);
            System.out.println("Context loaded.");
        }

        Conversation conversation = new Conversation();
        conversation.addMessage(new Message(Role.SYSTEM, SYSTEM_PROMPT));

        System.out.println("Welcome! Ask questions about microwave usage. Type 'quit' or 'exit' to stop.");
        while (true) {
            System.out.print("\nYou: ");
            if (!scanner.hasNextLine()) break;
            String userRequest = scanner.nextLine().trim();
            if (userRequest.equalsIgnoreCase("quit") || userRequest.equalsIgnoreCase("exit")) break;
            if (userRequest.isBlank()) continue;

            // STEP 1: RETRIEVAL
            List<String> contextChunks = textProcessor.search(
                    SearchMode.EUCLIDEAN_DISTANCE, userRequest, 5, 0.01, 384);
            String context = String.join("\n\n", contextChunks);

            // STEP 2: AUGMENTATION
            String augmented = USER_PROMPT_TEMPLATE
                    .replace("{context}", context)
                    .replace("{query}", userRequest);
            conversation.addMessage(new Message(Role.USER, augmented));

            // STEP 3: GENERATION
            Message response = completionClient.getCompletion(conversation.getMessages());
            System.out.println("\nAssistant: " + response.content());
            conversation.addMessage(response);
        }
    }
}
