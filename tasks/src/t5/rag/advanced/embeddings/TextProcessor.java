package t5.rag.advanced.embeddings;

import t5.rag.advanced.utils.TextUtils;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class TextProcessor {

    private final EmbeddingsClient embeddingsClient;
    private final String jdbcUrl;
    private final String dbUser;
    private final String dbPassword;

    public TextProcessor(EmbeddingsClient embeddingsClient, String host, int port, String database, String user, String password) {
        this.embeddingsClient = embeddingsClient;
        this.jdbcUrl = "jdbc:postgresql://" + host + ":" + port + "/" + database;
        this.dbUser = user;
        this.dbPassword = password;
    }

    private Connection getConnection() throws SQLException {
        return DriverManager.getConnection(jdbcUrl, dbUser, dbPassword);
    }

    /**
     * Load file content, chunk it, generate embeddings, and store all chunks in the DB.
     * Truncates the vectors table before loading by default.
     */
    public void processTextFile(String fileName, int chunkSize, int overlap, int dimensions) {
        processTextFile(fileName, chunkSize, overlap, dimensions, true);
    }

    public void processTextFile(String fileName, int chunkSize, int overlap, int dimensions, boolean truncateTable) {
        if (chunkSize < 10) throw new IllegalArgumentException("chunkSize must be >= 10");
        if (overlap < 0) throw new IllegalArgumentException("overlap must be >= 0");
        if (overlap >= chunkSize) throw new IllegalArgumentException("overlap must be < chunkSize");

        if (truncateTable) {
            truncateTable();
        }

        try {
            String content = Files.readString(Path.of(fileName));
            List<String> chunks = TextUtils.chunkText(content, chunkSize, overlap);
            Map<Integer, List<Float>> embeddings = embeddingsClient.getEmbeddings(chunks, dimensions);
            for (int i = 0; i < chunks.size(); i++) {
                saveChunk(embeddings.get(i), chunks.get(i), fileName);
            }
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private void truncateTable() {
        try (Connection conn = getConnection();
             Statement stmt = conn.createStatement()) {
            stmt.execute("TRUNCATE TABLE vectors");
            System.out.println("Table has been successfully truncated.");
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    private void saveChunk(List<Float> embedding, String chunk, String documentName) {
        String vectorString = "[" + embedding.stream()
                .map(String::valueOf)
                .collect(Collectors.joining(",")) + "]";
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "INSERT INTO vectors (document_name, text, embedding) VALUES (?, ?, ?::vector)")) {
            ps.setString(1, documentName);
            ps.setString(2, chunk);
            ps.setString(3, vectorString);
            ps.executeUpdate();
            System.out.println("Stored chunk from document: " + documentName);
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }


    public List<String> search(SearchMode searchMode, String userRequest, int topK, double scoreThreshold, int dimensions) {
        if (topK < 1) throw new IllegalArgumentException("topK must be >= 1");
        if (scoreThreshold < 0.0 || scoreThreshold > 1.0) throw new IllegalArgumentException("scoreThreshold must be in [0.0, 1.0]");

        List<Float> queryEmbedding = embeddingsClient.getEmbeddings(userRequest, dimensions).get(0);
        String vectorString = "[" + queryEmbedding.stream()
                .map(String::valueOf)
                .collect(Collectors.joining(",")) + "]";

        double maxDistance;
        if (searchMode == SearchMode.COSINE_DISTANCE) {
            maxDistance = 1.0 - scoreThreshold;
        } else {
            maxDistance = scoreThreshold == 0.0 ? Double.MAX_VALUE : (1.0 / scoreThreshold) - 1.0;
        }

        List<String> results = new ArrayList<>();
        String sql = buildSearchQuery(searchMode);
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, vectorString);
            ps.setString(2, vectorString);
            ps.setDouble(3, maxDistance);
            ps.setInt(4, topK);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    String text = rs.getString("text");
                    double distance = rs.getDouble("distance");
                    double similarity = searchMode == SearchMode.COSINE_DISTANCE
                            ? 1.0 - distance
                            : 1.0 / (1.0 + distance);
                    System.out.println("  [similarity=" + similarity + "] " + text.substring(0, Math.min(80, text.length())) + "...");
                    results.add(text);
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
        return results;
    }

    private String buildSearchQuery(SearchMode searchMode) {
        String operator = searchMode == SearchMode.EUCLIDEAN_DISTANCE ? "<->" : "<=>";
        return "SELECT text, embedding " + operator + " ?::vector AS distance " +
               "FROM vectors " +
               "WHERE embedding " + operator + " ?::vector <= ? " +
               "ORDER BY distance " +
               "LIMIT ?";
    }
}
