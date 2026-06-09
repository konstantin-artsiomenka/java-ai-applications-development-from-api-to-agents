package t8.agent.task.tools.users;

import commons.user.service.UserServiceClient;

import java.util.Map;

public class GetUserByIdTool extends BaseUserServiceTool {

    public GetUserByIdTool(UserServiceClient userClient) {
        super(userClient);
    }

    @Override
    public String getName() {
        return "get_user_by_id";
    }

    @Override
    public String getDescription() {
        return "Retrieves a user's full profile by their unique numeric ID.";
    }

    @Override
    public String getInputSchema() {
        return """
                {
                  "type": "object",
                  "properties": {
                    "id": {
                      "type": "number",
                      "description": "The unique numeric ID of the user to retrieve."
                    }
                  },
                  "required": ["id"]
                }
                """;
    }

    @Override
    public String execute(Map<String, Object> arguments) {
        try {
            int id = ((Number) arguments.get("id")).intValue();
            return userClient.getUser(id);
        } catch (Exception e) {
            return "Error: " + e.getMessage();
        }
    }
}
