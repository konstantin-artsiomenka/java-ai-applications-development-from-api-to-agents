package t8.agent.task;

import commons.Constants;
import commons.model.Conversation;
import commons.model.Message;
import commons.model.Role;
import commons.user.service.UserServiceClient;
import t8.agent.task.agents.AnthropicBasedAgent;
import t8.agent.task.agents.BaseAgent;
import t8.agent.task.tools.BaseTool;
import t8.agent.task.tools.WebSearchTool;
import t8.agent.task.tools.users.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;

public class App {

    public static void main(String[] args) {
        UserServiceClient userServiceClient = new UserServiceClient();

        List<BaseTool> tools = List.of(
                new WebSearchTool(Constants.OPENAI_API_KEY),
                new GetUserByIdTool(userServiceClient),
                new SearchUsersTool(userServiceClient),
                new CreateUserTool(userServiceClient),
                new UpdateUserTool(userServiceClient),
                new DeleteUserTool(userServiceClient)
        );

        BaseAgent agent = new AnthropicBasedAgent(
                Constants.CLAUDE_SONNET_4_6,
                Constants.ANTHROPIC_API_KEY,
                tools,
                Prompts.SYSTEM_PROMPT
        );

        Conversation conversation = new Conversation();

        System.out.println("User Management Agent is ready. Type 'exit' to quit.");
        System.out.println("─".repeat(60));

        try (Scanner scanner = new Scanner(System.in)) {
            while (true) {
                System.out.print("You: ");
                String input = scanner.nextLine();

                if ("exit".equalsIgnoreCase(input.trim())) {
                    break;
                }

                conversation.addMessage(new Message(Role.USER, input));

                // Mutable copy so getResponse can append intermediate tool messages
                List<Message> messages = new ArrayList<>(conversation.getMessages());
                int sizeBeforeCall = messages.size();

                Message reply = agent.getResponse(messages, true);

                // Sync back any intermediate tool-call / tool-result messages
                for (int i = sizeBeforeCall; i < messages.size(); i++) {
                    conversation.addMessage(messages.get(i));
                }

                conversation.addMessage(reply);
                System.out.println("Assistant: " + reply.content());
                System.out.println();
            }
        }
    }
}
