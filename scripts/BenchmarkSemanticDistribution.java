import java.nio.file.*;
import java.util.*;
import io.github.kdroidfilter.seforimlibrary.search.*;

/** Compare warmed vector retrieval with identical query vectors and the application's default 150 candidates. */
public class BenchmarkSemanticDistribution {
    public static void main(String[] args) throws Exception {
        Path original = Path.of(args[0]), unified = Path.of(args[1]), database = Path.of(args[2]);
        var queries = List.of("מצוות התלויות בארץ", "שמירת שבת", "דיני תפילה", "השבת אבדה", "כיבוד אב ואם",
            "ברכת המזון", "דיני מוקצה", "שמיטת כספים", "הלכות צדקה", "לימוד תורה");
        try (var embedder = SeforimEmbedder.Companion.tryLoad(unified.resolve("model"));
             var oldSearch = new VectorSearcher(original.resolve("index"), 256, database, original.resolve("model"));
             var newSearch = new VectorSearcher(unified.resolve("index"), 256, database, unified.resolve("model"))) {
            if (embedder == null) throw new IllegalStateException("Model failed to load");
            var vectors = new ArrayList<float[]>();
            for (var query : queries) vectors.add(embedder.embed(query, SeforimEmbedder.Role.QUERY));
            var oldTimes = new ArrayList<Double>();
            var newTimes = new ArrayList<Double>();
            for (int round = -2; round < 5; round++) {
                for (var vector : vectors) {
                    double oldMs, newMs;
                    if ((round & 1) == 0) {
                        oldMs = measure(oldSearch, vector, "original"); newMs = measure(newSearch, vector, "unified");
                    } else {
                        newMs = measure(newSearch, vector, "unified"); oldMs = measure(oldSearch, vector, "original");
                    }
                    if (round >= 0) { oldTimes.add(oldMs); newTimes.add(newMs); }
                }
                System.out.println("Vector benchmark round " + round + " complete");
            }
            Collections.sort(oldTimes); Collections.sort(newTimes);
            double oldMedian = percentile(oldTimes, .5), newMedian = percentile(newTimes, .5);
            String report = String.format(Locale.ROOT,
                "{\"phase\":\"warmed vector retrieval only\",\"queries\":10,\"samplesPerIndex\":50,\"warmupRounds\":2," +
                "\"candidates\":150,\"oldMedianMs\":%.3f,\"newMedianMs\":%.3f,\"oldP95Ms\":%.3f,\"newP95Ms\":%.3f," +
                "\"medianSpeedup\":%.3f}", oldMedian, newMedian, percentile(oldTimes, .95), percentile(newTimes, .95), oldMedian/newMedian);
            Files.writeString(Path.of(args[3]), report);
            System.out.println(report);
        }
    }

    static double measure(VectorSearcher searcher, float[] vector, String label) {
        long start = System.nanoTime();
        int count = searcher.search(vector, 150, false, null).size();
        if (count != 150) throw new AssertionError(label + ": expected 150 hits, got " + count);
        return (System.nanoTime() - start) / 1_000_000.0;
    }

    static double percentile(List<Double> values, double quantile) {
        return values.get((int)Math.ceil(values.size() * quantile) - 1);
    }
}
