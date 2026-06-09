package t8.agent.task.tools.users;

import com.fasterxml.jackson.databind.ObjectMapper;
import commons.user.service.UserServiceClient;
import commons.user.service.UserUpdate;

import java.util.Map;

public class UpdateUserTool extends BaseUserServiceTool {

    private final ObjectMapper objectMapper = new ObjectMapper();

    public UpdateUserTool(UserServiceClient userClient) {
        super(userClient);
    }

    @Override
    public String getName() {
        return "update_user";
    }

    @Override
    public String getDescription() {
        return "Updates an existing user's profile fields by their unique numeric ID.";
    }

    @Override
    public String getInputSchema() {
        return """
                {
                  "type": "object",
                  "properties": {
                    "id": {
                      "type": "number",
                      "description": "The unique numeric ID of the user to update."
                    },
                    "new_info": {
                      "type": "object",
                      "description": "Fields to update.",
                      "properties": {
                        "name":          { "type": "string" },
                        "surname":       { "type": "string" },
                        "email":         { "type": "string" },
                        "phone":         { "type": "string" },
                        "date_of_birth": { "type": "string" },
                        "gender":        { "type": "string" },
                        "company":       { "type": "string" },
                        "salary":        { "type": "number" },
                        "address": {
                          "type": "object",
                          "properties": {
                            "country":    { "type": "string" },
                            "city":       { "type": "string" },
                            "street":     { "type": "string" },
                            "flat_house": { "type": "string" }
                          }
                        },
                        "credit_card": {
                          "type": "object",
                          "properties": {
                            "num":      { "type": "string" },
                            "cvv":      { "type": "string" },
                            "exp_date": { "type": "string" }
                          }
                        }
                      }
                    }
                  },
                  "required": ["id", "new_info"]
                }
                """;
    }

    @Override
    @SuppressWarnings("unchecked")
    public String execute(Map<String, Object> arguments) {
        try {
            int userId = ((Number) arguments.get("id")).intValue();
            Map<String, Object> newInfo = (Map<String, Object>) arguments.get("new_info");
            UserUpdate user = objectMapper.convertValue(newInfo, UserUpdate.class);
            return userClient.updateUser(userId, user);
        } catch (Exception e) {
            return "Error: " + e.getMessage();
        }
    }
}
