package t8.agent.task.tools;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.util.Map;

public abstract class BaseTool {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    public abstract String execute(Map<String, Object> arguments);

    public abstract String getName();

    public abstract String getDescription();

    public abstract String getInputSchema();

    public String getOpenAiSchema() {
        try {
            ObjectNode functionNode = MAPPER.createObjectNode();
            functionNode.put("name", getName());
            functionNode.put("description", getDescription());
            functionNode.set("parameters", MAPPER.readTree(getInputSchema()));

            ObjectNode rootNode = MAPPER.createObjectNode();
            rootNode.put("type", "function");
            rootNode.set("function", functionNode);

            return MAPPER.writeValueAsString(rootNode);
        } catch (Exception e) {
            throw new RuntimeException("Failed to build OpenAI schema", e);
        }
    }

    public String getAnthropicSchema() {
        try {
            ObjectNode toolNode = MAPPER.createObjectNode();
            toolNode.put("name", getName());
            toolNode.put("description", getDescription());
            toolNode.set("input_schema", MAPPER.readTree(getInputSchema()));

            return MAPPER.writeValueAsString(toolNode);
        } catch (Exception e) {
            throw new RuntimeException("Failed to build Anthropic schema", e);
        }
    }
}
