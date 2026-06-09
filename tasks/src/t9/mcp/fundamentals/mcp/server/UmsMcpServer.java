package t9.mcp.fundamentals.mcp.server;

import commons.user.service.UserCreate;
import commons.user.service.UserSearchRequest;
import commons.user.service.UserServiceClient;
import commons.user.service.UserUpdate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springaicommunity.mcp.annotation.McpPrompt;
import org.springaicommunity.mcp.annotation.McpResource;
import org.springaicommunity.mcp.annotation.McpTool;
import org.springaicommunity.mcp.annotation.McpToolParam;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;

@Component
public class UmsMcpServer {

    private static final Logger log = LoggerFactory.getLogger(UmsMcpServer.class);

    private static final String SEARCH_ASSISTANT_PROMPT = """
            You are a user search assistant. Use the `search_user` tool to find users in the system.

            Available search parameters (all optional):
            - name    — user's first name
            - surname — user's last name
            - email   — user's email address
            - gender  — user's gender

            Search strategy:
            - You may combine any subset of parameters for a more targeted search.
            - Omitting all parameters returns all users in the system.
            - Effective combinations: name + surname for a specific person; email alone for exact lookup; gender alone to browse by demographic.

            Tips:
            - Parameter matching is typically case-insensitive.
            - If no results are found, try broadening the query by removing some filters.
            - If too many results are returned, add more filters to narrow down.
            """;

    private static final String PROFILE_CREATION_PROMPT = """
            You are a user profile creation assistant. Use the `add_user` tool to create new users.

            Required fields:
            - name       — first name
            - surname    — last name
            - email      — valid email address (must be unique)
            - about_me   — short personal bio or description

            Optional fields:
            - phone         — phone number in international format
            - date_of_birth — date of birth in YYYY-MM-DD format
            - gender        — e.g. Male, Female, Non-binary
            - company       — employer or organisation name
            - salary        — annual salary as a numeric value
            - address       — object with: country, city, street, flat_house
            - credit_card   — object with: num (card number), cvv, exp_date (MM/YY)

            Guidelines:
            - Address: provide all sub-fields when possible for a complete profile.
            - Credit card: only include if explicitly requested; never store plaintext CVV in logs.
            - Aim for realistic and diverse profiles: vary names, locations, occupations, and demographics.
            - Validate email format before submitting.
            """;

    // ==================== TOOL DEFINITIONS ====================

    @McpTool(name = "get_user_by_id", description = "Provides full user information by id")
    public String getUserById(@McpToolParam int userId) {
        log.info("getUserById invoked with userId={}", userId);
        try {
            String result = new UserServiceClient().getUser(userId);
            log.info("getUserById completed for userId={}", userId);
            return result;
        } catch (Exception e) {
            log.error("getUserById failed for userId={}: {}", userId, e.getMessage());
            return "Error: " + e.getMessage();
        }
    }

    @McpTool(name = "delete_user", description = "Deletes user")
    public String deleteUser(@McpToolParam int userId) {
        log.info("deleteUser invoked with userId={}", userId);
        try {
            String result = new UserServiceClient().deleteUser(userId);
            log.info("deleteUser completed for userId={}", userId);
            return result;
        } catch (Exception e) {
            log.error("deleteUser failed for userId={}: {}", userId, e.getMessage());
            return "Error: " + e.getMessage();
        }
    }

    @McpTool(name = "search_user", description = "Searches for users by name, surname, email and gender")
    public String searchUser(@McpToolParam UserSearchRequest userSearchRequest) {
        log.info("searchUser invoked with request={}", userSearchRequest);
        try {
            String result = new UserServiceClient().searchUsers(
                    userSearchRequest.name(),
                    userSearchRequest.surname(),
                    userSearchRequest.email(),
                    userSearchRequest.gender()
            );
            log.info("searchUser completed");
            return result;
        } catch (Exception e) {
            log.error("searchUser failed: {}", e.getMessage());
            return "Error: " + e.getMessage();
        }
    }

    @McpTool(name = "add_user", description = "Adds new user into the system")
    public String addUser(@McpToolParam UserCreate userCreate) {
        log.info("addUser invoked for email={}", userCreate.email());
        try {
            String result = new UserServiceClient().addUser(userCreate);
            log.info("addUser completed for email={}", userCreate.email());
            return result;
        } catch (Exception e) {
            log.error("addUser failed for email={}: {}", userCreate.email(), e.getMessage());
            return "Error: " + e.getMessage();
        }
    }

    @McpTool(name = "update_user", description = "Updates user by userId")
    public String updateUser(@McpToolParam int userId, @McpToolParam UserUpdate userUpdate) {
        log.info("updateUser invoked with userId={}", userId);
        try {
            String result = new UserServiceClient().updateUser(userId, userUpdate);
            log.info("updateUser completed for userId={}", userId);
            return result;
        } catch (Exception e) {
            log.error("updateUser failed for userId={}: {}", userId, e.getMessage());
            return "Error: " + e.getMessage();
        }
    }

    // ==================== MCP RESOURCES ====================

    @McpResource(
            uri = "users-management://flow-diagram",
            name = "flow-diagram",
            mimeType = "image/png",
            description = "The Users Management Service flow diagram as PNG image"
    )
    public String flowDiagramResource() {
        try {
            byte[] bytes = Files.readAllBytes(Path.of("tasks/src/t9/mcp/fundamentals/flow.png"));
            return Base64.getEncoder().encodeToString(bytes);
        } catch (IOException e) {
            throw new RuntimeException("Failed to read flow diagram: " + e.getMessage(), e);
        }
    }

    // ==================== MCP PROMPTS ====================

    @McpPrompt(description = "users formulate effective search queries")
    public String searchAssistantPrompt() {
        return SEARCH_ASSISTANT_PROMPT;
    }

    @McpPrompt(description = "Guides creation of realistic user profiles")
    public String profileCreationPrompt() {
        return PROFILE_CREATION_PROMPT;
    }
}
