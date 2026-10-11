import java.io.*;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import org.apache.lucene.index.*;
import org.apache.lucene.store.*;
import org.apache.lucene.document.*;
import org.apache.lucene.search.*;
import org.apache.lucene.codecs.*;
import org.apache.lucene.codecs.lucene104.Lucene104Codec;
import org.apache.lucene.codecs.lucene99.Lucene99HnswVectorsFormat;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicLong;

/** Rebuild completed float32/int8 Lucene shards into one int8 shard and one HNSW graph. */
public class MergeSemanticShards {
    static byte[] quantize(float[] vector) {
        float maximum = 0;
        for (float value : vector) {
            if (!Float.isFinite(value)) throw new IllegalArgumentException("Non-finite vector");
            maximum = Math.max(maximum, Math.abs(value));
        }
        if (maximum == 0) throw new IllegalArgumentException("Zero vector");
        var result = new byte[vector.length];
        for (int i = 0; i < vector.length; i++) {
            result[i] = (byte) Math.max(-127, Math.min(127, (int) Math.floor(vector[i] / maximum * 127f + 0.5f)));
        }
        return result;
    }
    static String sha256(Path file) throws Exception {
        var digest = MessageDigest.getInstance("SHA-256");
        try (var input = Files.newInputStream(file)) {
            var buffer = new byte[1 << 20];
            for (int n; (n = input.read(buffer)) != -1;) digest.update(buffer, 0, n);
        }
        return HexFormat.of().formatHex(digest.digest());
    }

    public static void main(String[] args) throws Exception {
        if (args.length != 4) throw new IllegalArgumentException("Usage: <source-index> <output-index> <database> <model-dir>");
        Path source = Path.of(args[0]), output = Path.of(args[1]), database = Path.of(args[2]), model = Path.of(args[3]);
        if (Files.exists(output)) throw new IllegalArgumentException("Output must not exist: " + output);
        List<Path> shards;
        try (var files = Files.list(source)) {
            shards = files.filter(Files::isDirectory).filter(p -> p.getFileName().toString().matches("shard-\\d+")).sorted().toList();
        }
        if (shards.isEmpty()) throw new IllegalArgumentException("No shards");
        var manifests = new ArrayList<Properties>();
        long total = 0;
        for (int i = 0; i < shards.size(); i++) {
            var properties = new Properties();
            try (var input = Files.newInputStream(shards.get(i).resolve("semantic.properties"))) { properties.load(input); }
            if (!"zayit-round2-1".equals(properties.getProperty("format")) ||
                !Integer.toString(i).equals(properties.getProperty("shardIndex")) ||
                !Integer.toString(shards.size()).equals(properties.getProperty("shardCount")) ||
                !properties.getProperty("indexed").equals(properties.getProperty("eligible"))) {
                throw new IllegalArgumentException("Incomplete shard: " + shards.get(i));
            }
            if (!manifests.isEmpty()) {
                for (String key : List.of("format", "dimension", "databaseSha256", "modelSha256", "tokenizerSha256", "vectorEncoding")) {
                    if (!Objects.equals(properties.getProperty(key), manifests.getFirst().getProperty(key))) {
                        throw new IllegalArgumentException("Inconsistent " + key);
                    }
                }
            }
            long count = Long.parseLong(properties.getProperty("indexed"));
            if (count <= 0) throw new IllegalArgumentException("Empty shard");
            var declaredEncoding = properties.getProperty("vectorEncoding", "float32");
            if (!Set.of("float32", "int8-maxabs-v1").contains(declaredEncoding)) {
                throw new IllegalArgumentException("Unsupported vector encoding: " + declaredEncoding);
            }
            try (var dir = FSDirectory.open(shards.get(i)); var reader = DirectoryReader.open(dir)) {
                var vec = FieldInfos.getMergedFieldInfos(reader).fieldInfo("vec");
                var encoding = "int8-maxabs-v1".equals(properties.getProperty("vectorEncoding")) ? VectorEncoding.BYTE : VectorEncoding.FLOAT32;
                if (reader.numDocs() != count || reader.hasDeletions() || vec == null ||
                    vec.getVectorDimension() != Integer.parseInt(properties.getProperty("dimension")) ||
                    vec.getVectorEncoding() != encoding || vec.getVectorSimilarityFunction() != VectorSimilarityFunction.COSINE) {
                    throw new IllegalArgumentException("Invalid shard contents: " + shards.get(i));
                }
            }
            manifests.add(properties);
            total += count;
        }
        var first = manifests.getFirst();
        for (var entry : Map.of(database, "databaseSha256", model.resolve("seforim-embed-round2-int8.onnx"), "modelSha256",
                               model.resolve("tokenizer.json"), "tokenizerSha256").entrySet()) {
            System.out.println("Verifying " + entry.getKey());
            if (!sha256(entry.getKey()).equals(first.getProperty(entry.getValue()))) throw new IllegalArgumentException("Fingerprint mismatch: " + entry.getKey());
        }
        Path destination = output.resolve("shard-00");
        Files.createDirectories(destination);
        var executor = Executors.newFixedThreadPool(4);
        var vectorsFormat = new Lucene99HnswVectorsFormat(16, 100, 4, executor);
        var config = new IndexWriterConfig().setOpenMode(IndexWriterConfig.OpenMode.CREATE).setRAMBufferSizeMB(256);
        config.setCodec(new Lucene104Codec() {
            @Override public KnnVectorsFormat getKnnVectorsFormatForField(String field) { return vectorsFormat; }
        });
        var indexed = new AtomicLong();
        var indexingExecutor = Executors.newFixedThreadPool(4);
        final long expectedTotal = total;
        try (var dir = FSDirectory.open(destination); var writer = new IndexWriter(dir, config)) {
            for (int shardIndex = 0; shardIndex < shards.size(); shardIndex++) {
                Path shard = shards.get(shardIndex);
                System.out.println("Quantizing " + shard + " at " + java.time.Instant.now());
                try (var input = FSDirectory.open(shard); var reader = DirectoryReader.open(input)) {
                    final int sourceShardIndex = shardIndex;
                    var futures = new ArrayList<Future<Void>>();
                    for (var context : reader.leaves()) {
                        futures.add(indexingExecutor.submit(() -> {
                            var leaf = context.reader();
                            int[] baseFlags = new int[leaf.maxDoc()];
                            Arrays.fill(baseFlags, -1);
                            var points = leaf.getPointValues("is_base_book");
                            if (points == null) throw new IllegalArgumentException("Missing base-book flags");
                            points.intersect(new PointValues.IntersectVisitor() {
                                public void visit(int docId) { throw new IllegalStateException("Missing point value"); }
                                public void visit(int docId, byte[] packed) { baseFlags[docId] = IntPoint.decodeDimension(packed, 0); }
                                public PointValues.Relation compare(byte[] min, byte[] max) { return PointValues.Relation.CELL_CROSSES_QUERY; }
                            });
                            var floats = leaf.getFloatVectorValues("vec");
                            var bytes = floats == null ? leaf.getByteVectorValues("vec") : null;
                            var iterator = (floats == null ? bytes : floats).iterator();
                            var stored = leaf.storedFields();
                            for (int docId; (docId = iterator.nextDoc()) != DocIdSetIterator.NO_MORE_DOCS;) {
                                var old = stored.document(docId);
                                long lineId = old.getField("line_id").numericValue().longValue();
                                long bookId = old.getField("book_id").numericValue().longValue();
                                if (lineId <= 0 || bookId <= 0 || lineId % shards.size() != sourceShardIndex || baseFlags[docId] < 0) {
                                    throw new IllegalArgumentException("Invalid document " + lineId);
                                }
                                byte[] vector = floats == null ? bytes.vectorValue(iterator.index()).clone() : quantize(floats.vectorValue(iterator.index()));
                                var document = new Document();
                                document.add(new StoredField("line_id", lineId));
                                document.add(new StoredField("book_id", bookId));
                                document.add(new IntPoint("book_id", Math.toIntExact(bookId)));
                                document.add(new IntPoint("is_base_book", baseFlags[docId]));
                                document.add(new KnnByteVectorField("vec", vector, VectorSimilarityFunction.COSINE));
                                writer.addDocument(document);
                                long count = indexed.incrementAndGet();
                                if (count % 100_000 == 0) System.out.println("Indexed " + count + "/" + expectedTotal + " at " + java.time.Instant.now());
                            }
                            return null;
                        }));
                    }
                    for (var future : futures) future.get();
                }
            }
            if (indexed.get() != total) throw new IllegalStateException("Missing vectors");
            System.out.println("Merging into one HNSW graph at " + java.time.Instant.now());
            writer.forceMerge(1);
            writer.commit();
        } finally {
            executor.shutdown();
            indexingExecutor.shutdownNow();
        }
        try (var dir = FSDirectory.open(destination); var reader = DirectoryReader.open(dir)) {
            if (reader.numDocs() != total || reader.hasDeletions() || reader.leaves().size() != 1 ||
                FieldInfos.getMergedFieldInfos(reader).fieldInfo("vec").getVectorEncoding() != VectorEncoding.BYTE) {
                throw new IllegalStateException("Merged index validation failed");
            }
            var values = reader.leaves().getFirst().reader().getByteVectorValues("vec");
            var hits = new IndexSearcher(reader).search(new KnnByteVectorQuery("vec", values.vectorValue(0), 10), 10);
            if (hits.scoreDocs.length != Math.min(10, total)) throw new IllegalStateException("KNN smoke test failed");
        }
        if (!sha256(database).equals(first.getProperty("databaseSha256"))) {
            throw new IllegalStateException("Database changed while quantizing");
        }
        first.setProperty("vectorEncoding", "int8-maxabs-v1");
        first.setProperty("shardCount", "1");
        first.setProperty("shardIndex", "0");
        first.setProperty("indexed", Long.toString(total));
        first.setProperty("eligible", Long.toString(total));
        try (var stream = Files.newOutputStream(destination.resolve("semantic.properties"))) { first.store(stream, "Unified int8 Zayit Round 2 index"); }
        System.out.println("Merged " + shards.size() + " shards, " + total + " vectors into " + destination);
    }
}
