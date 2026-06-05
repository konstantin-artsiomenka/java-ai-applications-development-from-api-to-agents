package t5.rag.advanced.embeddings;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import commons.exceptions.TaskNotImplementedException;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class EmbeddingsClient {

    private final String endpoint;
    private final String apiKey;
    private final String modelName;
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;

    public EmbeddingsClient(String endpoint, String modelName, String apiKey) {
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalArgumentException("API key cannot be null or empty");
        }
        this.endpoint = endpoint;
        this.apiKey = "Bearer " + apiKey;
        this.modelName = modelName;
        this.httpClient = HttpClient.newHttpClient();
        this.objectMapper = new ObjectMapper();
    }

    /**
     * Generate indexed embeddings for a single input string.
     * Returns Map where key 0 holds the embedding vector.
     */
    public Map<Integer, List<Float>> getEmbeddings(String input, int dimensions) {
        return getEmbeddings(List.of(input), dimensions);
    }

    /**
     * Generate indexed embeddings for a list of input strings.
     * Returns Map: inputs[0] -> [0][embedding], inputs[1] -> [1][embedding], ...
     */
    public Map<Integer, List<Float>> getEmbeddings(List<String> inputs, int dimensions) {
        try {
            Map<String, Object> body = new HashMap<>();
            body.put("model", modelName);
            body.put("input", inputs);
            body.put("dimensions", dimensions);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(endpoint))
                    .header("Authorization", apiKey)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(body)))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                throw new RuntimeException("Request failed with status " + response.statusCode() + ": " + response.body());
            }

            JsonNode data = objectMapper.readTree(response.body()).get("data");
            return fromData(data);
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private Map<Integer, List<Float>> fromData(JsonNode data) {
        Map<Integer, List<Float>> result = new HashMap<>();
        for (JsonNode embeddingObj : data) {
            int index = embeddingObj.get("index").asInt();
            List<Float> embedding = new ArrayList<>();
            for (JsonNode value : embeddingObj.get("embedding")) {
                embedding.add((float) value.asDouble());
            }
            result.put(index, embedding);
        }
        return result;
    }
}
