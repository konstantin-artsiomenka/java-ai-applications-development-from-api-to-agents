package t8.agent.task.tools.users;

import commons.user.service.UserServiceClient;

import java.util.Map;

public class SearchUsersTool extends BaseUserServiceTool {

    public SearchUsersTool(UserServiceClient userClient) {
        super(userClient);
    }

    @Override
    public String getName() {
        return "search_users";
    }

    @Override
    public String getDescription() {
        return "Searches for users by optional filters: name, surname, email, or gender. Returns all matching users.";
    }

    @Override
    public String getInputSchema() {
        return """
                {
                  "type": "object",
                  "properties": {
                    "name":    { "type": "string", "description": "Filter by first name." },
                    "surname": { "type": "string", "description": "Filter by last name." },
                    "email":   { "type": "string", "description": "Filter by email address." },
                    "gender":  { "type": "string", "description": "Filter by gender." }
                  },
                  "required": []
                }
                """;
    }

    @Override
    public String execute(Map<String, Object> arguments) {
        try {
            String name    = (String) arguments.get("name");
            String surname = (String) arguments.get("surname");
            String email   = (String) arguments.get("email");
            String gender  = (String) arguments.get("gender");
            return userClient.searchUsers(name, surname, email, gender);
        } catch (Exception e) {
            return "Error: " + e.getMessage();
        }
    }
}
