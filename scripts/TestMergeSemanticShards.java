import java.nio.file.*;
import java.util.*;
import org.apache.lucene.index.*;
import org.apache.lucene.store.*;
import org.apache.lucene.document.*;
import org.apache.lucene.search.*;

/** Standalone regression fixture for the local converter (no external databases or model downloads). */
public class TestMergeSemanticShards {
    public static void main(String[] args) throws Exception {
        Path root = Path.of(args[0]);
        Files.createDirectories(root);
        Path db = root.resolve("seforim.db"), model = root.resolve("model"), input = root.resolve("input");
        Files.createDirectories(model);
        Files.writeString(db, "fixture");
        Files.writeString(model.resolve("seforim-embed-round2-int8.onnx"), "model");
        Files.writeString(model.resolve("tokenizer.json"), "tokenizer");
        if (!Arrays.equals(MergeSemanticShards.quantize(new float[] {1f, -1f, 0f, 0.5f}), new byte[] {127, -127, 0, 64})) {
            throw new AssertionError("Quantization does not match Int8Vectors");
        }
        for (float[] invalid : List.of(new float[] {0f}, new float[] {Float.NaN}, new float[] {Float.POSITIVE_INFINITY})) {
            try { MergeSemanticShards.quantize(invalid); throw new AssertionError("Invalid vector accepted"); }
            catch (IllegalArgumentException expected) { }
        }
        for (int shard = 0; shard < 2; shard++) {
            Path path = input.resolve("shard-%02d".formatted(shard));
            Files.createDirectories(path);
            try (var dir = FSDirectory.open(path); var writer = new IndexWriter(dir, new IndexWriterConfig())) {
                for (int line = 1; line <= 20; line++) {
                    if (line % 2 != shard) continue;
                    var document = new Document();
                    document.add(new StoredField("line_id", (long) line));
                    document.add(new StoredField("book_id", (long) (line % 3 + 1)));
                    document.add(new IntPoint("book_id", line % 3 + 1));
                    document.add(new IntPoint("is_base_book", line % 2));
                    var vector = new float[256];
                    vector[line] = 1f;
                    vector[0] = 0.25f;
                    document.add(new KnnFloatVectorField("vec", vector, VectorSimilarityFunction.COSINE));
                    writer.addDocument(document);
                }
            }
            var properties = new Properties();
            properties.setProperty("format", "zayit-round2-1");
            properties.setProperty("dimension", "256");
            properties.setProperty("shardIndex", Integer.toString(shard));
            properties.setProperty("shardCount", "2");
            properties.setProperty("indexed", "10");
            properties.setProperty("eligible", "10");
            properties.setProperty("databaseSha256", MergeSemanticShards.sha256(db));
            properties.setProperty("modelSha256", MergeSemanticShards.sha256(model.resolve("seforim-embed-round2-int8.onnx")));
            properties.setProperty("tokenizerSha256", MergeSemanticShards.sha256(model.resolve("tokenizer.json")));
            try (var stream = Files.newOutputStream(path.resolve("semantic.properties"))) { properties.store(stream, "Fixture"); }
        }
        Path output = root.resolve("output");
        MergeSemanticShards.main(new String[] {input.toString(), output.toString(), db.toString(), model.toString()});
        try (var dir = FSDirectory.open(output.resolve("shard-00")); var reader = DirectoryReader.open(dir)) {
            var searcher = new IndexSearcher(reader);
            if (reader.numDocs() != 20 || reader.leaves().size() != 1) throw new AssertionError("Incorrect merged index");
            if (searcher.count(IntPoint.newExactQuery("is_base_book", 1)) != 10) throw new AssertionError("Base flags lost");
            for (int line = 1; line <= 20; line++) {
                var query = new float[256]; query[line] = 1f; query[0] = 0.25f;
                var hits = searcher.search(new KnnByteVectorQuery("vec", MergeSemanticShards.quantize(query), 1,
                          IntPoint.newExactQuery("book_id", line % 3 + 1)), 1);
                if (hits.scoreDocs.length != 1 || searcher.storedFields().document(hits.scoreDocs[0].doc)
                    .getField("line_id").numericValue().longValue() != line) throw new AssertionError("Incorrect filtered KNN result");
            }
        }
        MergeSemanticShards.main(new String[] {output.toString(), root.resolve("int8-reimport").toString(), db.toString(), model.toString()});
        System.out.println("PASS: float32 and int8 import, quantization, document preservation, base/book filters and KNN");
    }
}
