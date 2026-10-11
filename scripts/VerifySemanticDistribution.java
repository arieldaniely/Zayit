import java.nio.file.*;
import java.sql.*;
import java.util.*;
import io.github.kdroidfilter.seforimlibrary.search.*;

/** Run the application's actual model and semantic search implementation against extracted distribution assets. */
public class VerifySemanticDistribution {
    public static void main(String[] args) throws Exception {
        Path semantic = Path.of(args[0]), database = Path.of(args[1]);
        var properties = new Properties();
        try (var input = Files.newInputStream(semantic.resolve("index/shard-00/semantic.properties"))) { properties.load(input); }
        if (!"1".equals(properties.getProperty("shardCount")) ||
            !"int8-maxabs-v1".equals(properties.getProperty("vectorEncoding"))) throw new AssertionError("Incorrect semantic format");
        try (var searcher = new VectorSearcher(semantic.resolve("index"), 256, database, semantic.resolve("model"));
             var embedder = SeforimEmbedder.Companion.tryLoad(semantic.resolve("model"));
             var connection = DriverManager.getConnection("jdbc:sqlite:" + database.toUri() + "?mode=ro&immutable=1")) {
            if (embedder == null) throw new AssertionError("Model failed to load");
            for (String query : List.of("מצוות התלויות בארץ", "שמירת שבת", "דיני תפילה")) {
                float[] vector = embedder.embed(query, SeforimEmbedder.Role.QUERY);
                var hits = searcher.search(vector, 150, false, null);
                if (hits.size() != 150) throw new AssertionError("Expected 150 search hits, got " + hits.size());
                for (var hit : hits) checkHit(connection, hit, false);
                var baseHits = searcher.search(vector, 5, true, null);
                if (baseHits.isEmpty()) throw new AssertionError("No base-book hits");
                for (var hit : baseHits) checkHit(connection, hit, true);
                long bookId = hits.getFirst().getBookId();
                var filtered = searcher.search(vector, 5, false, List.of(bookId));
                if (filtered.isEmpty() || filtered.stream().anyMatch(hit -> hit.getBookId() != bookId)) {
                    throw new AssertionError("Book filtering failed");
                }
                System.out.println("PASS: Hebrew query, database joins, base-book filter and book filter: " + query);
            }
        }
        Files.writeString(args.length > 2 ? Path.of(args[2]) : semantic.resolve("runtime-verification.json"),
            "{\"verified\":true,\"queries\":3,\"candidatesPerQuery\":150,\"modelLoaded\":true,\"shardCount\":1,\"vectorEncoding\":\"int8-maxabs-v1\"}");
    }

    static void checkHit(Connection connection, DenseHit hit, boolean requireBase) throws Exception {
        if (!Float.isFinite(hit.getScore())) throw new AssertionError("Non-finite score");
        try (var statement = connection.prepareStatement("SELECT l.bookId, b.isBaseBook FROM line l JOIN book b ON b.id=l.bookId WHERE l.id=?")) {
            statement.setLong(1, hit.getLineId());
            try (var result = statement.executeQuery()) {
                if (!result.next() || result.getLong(1) != hit.getBookId() || (requireBase && result.getInt(2) != 1)) {
                    throw new AssertionError("Search result does not match database: " + hit);
                }
            }
        }
    }
}
