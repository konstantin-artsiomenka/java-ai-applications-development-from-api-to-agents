package t1.llm.api;


import commons.model.Conversation;
import commons.model.Message;
import commons.model.Role;

import java.util.Scanner;

/**
 * Interactive chat loop shared by all provider App classes.
 * <p>
 * Reads user input from stdin, maintains conversation history, calls the chosen client,
 * and loops until the user types "exit".
 */
public class BaseApp {

    public static void start(boolean stream, AiClient client) {
        Conversation conversation = new Conversation();
        Scanner scanner = new Scanner(System.in);
        System.out.println("Type 'exit' to quit.");
        while (true) {
            System.out.print("=> ");
            String input = scanner.nextLine().strip();
            if (input.equalsIgnoreCase("exit")) {
                System.out.println("Goodbye!");
                break;
            }
            conversation.addMessage(new Message(Role.USER, input));
            System.out.print("AI: ");
            Message aiMessage = stream ? client.streamResponse(conversation.getMessages())
                                       : client.response(conversation.getMessages());
            conversation.addMessage(aiMessage);
        }
    }
}
