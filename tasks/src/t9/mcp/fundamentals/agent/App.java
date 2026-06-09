package t9.mcp.fundamentals.agent;

import commons.Constants;
import com.openai.models.chat.completions.ChatCompletionMessageParam;
import com.openai.models.chat.completions.ChatCompletionSystemMessageParam;
import com.openai.models.chat.completions.ChatCompletionUserMessageParam;
import io.modelcontextprotocol.spec.McpSchema;
import t9.mcp.fundamentals.agent.mcp.client.BaseClient;
import t9.mcp.fundamentals.agent.mcp.client.HttpClient;
import t9.mcp.fundamentals.agent.mcp.client.StdioClient;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Scanner;

public class App {

    // Classpath of the compiled project for launching the STDIO server as a subprocess
    private static final String STDIO_SERVER_CLASS = "t9.mcp.fundamentals.mcp.server.StdioServerApp";

    public static void main(String[] args) throws Exception {
        // Switch active client by commenting/uncommenting:

//        String javaClasspath = System.getProperty("java.class.path");
//
//        // --- HTTP client (start HttpServer.java first): ---
//        try (BaseClient mcpClient = new HttpClient("http://localhost:8005/mcp")) {
//            runAgent(mcpClient);
//        }

        String javaClasspath = System.getProperty("java.class.path");
        try (BaseClient mcpClient = new StdioClient(
          null,
          "java",
          List.of("-cp", javaClasspath, "t9.mcp.fundamentals.mcp.server.StdioServerApp"),
          null
        )) {
            runAgent(mcpClient);
        }

//        try (BaseClient mcpClient = new StdioClient(
//                null,
//                "java",
//                List.of("-cp", javaClasspath, STDIO_SERVER_CLASS),
//                null
//        )) {
//            runAgent(mcpClient);
//        }

        // --- Docker STDIO client: ---
//        try (BaseClient mcpClient = new StdioClient("mcp/duckduckgo:latest", null, null, null)) {
//            runAgent(mcpClient);
//        }
    }

    private static void runAgent(BaseClient mcpClient) {
        mcpClient.connect();

        // List and print resources
        List<McpSchema.Resource> resources = mcpClient.getResources();
        System.out.println("\n📦 Resources (" + resources.size() + "):");
        resources.forEach(r -> System.out.println("  - " + r.uri() + ": " + r.description()));

        // List and print tools
        List<Map<String, Object>> tools = mcpClient.getTools();
        System.out.println("\n🔧 Tools (" + tools.size() + "):");
        tools.forEach(t -> {
            @SuppressWarnings("unchecked")
            Map<String, Object> fn = (Map<String, Object>) t.get("function");
            System.out.println("  - " + fn.get("name") + ": " + fn.get("description"));
        });

        // Initialize messages with system prompt
        List<ChatCompletionMessageParam> messages = new ArrayList<>();
        messages.add(ChatCompletionMessageParam.ofSystem(
                ChatCompletionSystemMessageParam.builder()
                        .content(Prompts.SYSTEM_PROMPT)
                        .build()
        ));

        // Add MCP server prompts as user messages
        List<McpSchema.Prompt> prompts = mcpClient.getPrompts();
        System.out.println("\n📝 Prompts (" + prompts.size() + "):");
        for (McpSchema.Prompt prompt : prompts) {
            System.out.println("  - " + prompt.name() + ": " + prompt.description());
            String content = mcpClient.getPrompt(prompt.name());
            if (content != null && !content.isBlank()) {
                String userMessage = "## Prompt provided by MCP server:\n" + prompt.description() + "\n" + content;
                messages.add(ChatCompletionMessageParam.ofUser(
                        ChatCompletionUserMessageParam.builder()
                                .content(userMessage)
                                .build()
                ));
            }
        }

        // Create agent
        Agent agent = new Agent(Constants.OPENAI_API_KEY, Constants.GPT_4O_MINI, tools, mcpClient);

        // Interactive chat loop
        System.out.println("\n🚀 User Management Agent ready. Type 'exit' to quit.");
        System.out.println("─".repeat(60));

        try (Scanner scanner = new Scanner(System.in)) {
            while (true) {
                System.out.print("You: ");
                String input = scanner.nextLine();

                if ("exit".equalsIgnoreCase(input.trim())) {
                    break;
                }

                messages.add(ChatCompletionMessageParam.ofUser(
                        ChatCompletionUserMessageParam.builder()
                                .content(input)
                                .build()
                ));

                ChatCompletionMessageParam response = agent.getCompletion(messages);
                messages.add(response);
                System.out.println();
            }
        }
    }
}
