package t8.agent.task.tools.users;

import com.fasterxml.jackson.databind.ObjectMapper;
import commons.user.service.UserCreate;
import commons.user.service.UserServiceClient;

import java.util.Map;

public class CreateUserTool extends BaseUserServiceTool {

    private final ObjectMapper objectMapper = new ObjectMapper();

    public CreateUserTool(UserServiceClient userClient) {
        super(userClient);
    }

    @Override
    public String getName() {
        return "add_user";
    }

    @Override
    public String getDescription() {
        return "Creates a new user with the provided profile information.";
    }

    @Override
    public String getInputSchema() {
        return """
                {
                  "type": "object",
                  "properties": {
                    "name":          { "type": "string",  "description": "First name." },
                    "surname":       { "type": "string",  "description": "Last name." },
                    "email":         { "type": "string",  "description": "Email address." },
                    "about_me":      { "type": "string",  "description": "Short bio or description." },
                    "phone":         { "type": "string",  "description": "Phone number." },
                    "date_of_birth": { "type": "string",  "description": "Date of birth (YYYY-MM-DD)." },
                    "gender":        { "type": "string",  "description": "Gender." },
                    "company":       { "type": "string",  "description": "Company name." },
                    "salary":        { "type": "number",  "description": "Annual salary." },
                    "address": {
                      "type": "object",
                      "properties": {
                        "country": { "type": "string" },
                        "city":    { "type": "string" },
                        "street":  { "type": "string" },
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
                  },
                  "required": ["name", "surname", "email", "about_me"]
                }
                """;
    }

    @Override
    public String execute(Map<String, Object> arguments) {
        try {
            UserCreate user = objectMapper.convertValue(arguments, UserCreate.class);
            return userClient.addUser(user);
        } catch (Exception e) {
            return "Error: " + e.getMessage();
        }
    }
}
