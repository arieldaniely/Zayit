package io.github.kdroidfilter.seforimapp.features.personallibrary

import io.github.kdroidfilter.seforimapp.framework.search.RepositorySnippetSourceProvider
import io.github.kdroidfilter.seforimlibrary.dao.repository.SeforimRepository
import io.github.kdroidfilter.seforimlibrary.search.HybridSearchEngine
import io.github.kdroidfilter.seforimlibrary.search.Int8Vectors
import io.github.kdroidfilter.seforimlibrary.search.LineHit
import io.github.kdroidfilter.seforimlibrary.search.LuceneSearchEngine
import io.github.kdroidfilter.seforimlibrary.search.Round2Normalizer
import io.github.kdroidfilter.seforimlibrary.search.SearchEngine
import io.github.kdroidfilter.seforimlibrary.search.SeforimEmbedder
import io.github.kdroidfilter.seforimlibrary.search.SemanticFileFingerprint
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.yield
import org.apache.lucene.analysis.standard.StandardAnalyzer
import org.apache.lucene.document.Document
import org.apache.lucene.document.IntPoint
import org.apache.lucene.document.KnnByteVectorField
import org.apache.lucene.document.StoredField
import org.apache.lucene.index.DirectoryReader
import org.apache.lucene.index.IndexWriter
import org.apache.lucene.index.IndexWriterConfig
import org.apache.lucene.index.VectorSimilarityFunction
import org.apache.lucene.store.FSDirectory
import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest
import java.sql.DriverManager
import java.util.Properties
import java.util.UUID

/** A single worker keeps memory bounded and shares the already installed model with search. */
internal suspend fun buildPersonalSemanticIndex(
    database: Path,
    modelDirectory: Path,
    output: Path,
    embed: suspend (String) -> FloatArray,
    onProgress: (Float) -> Unit = {},
    previousIndex: Path? = null,
    previousDatabase: Path? = null,
    allowEmpty: Boolean = false,
): Long {
    require(!Files.exists(output))
    Files.createDirectories(output)
    var completed = false
    try {
        val shard = Files.createDirectory(output.resolve("shard-00"))
        val modelHash = SemanticFileFingerprint.hash(modelDirectory.resolve("seforim-embed-round2-int8.onnx"))
        val tokenizerHash = SemanticFileFingerprint.hash(modelDirectory.resolve("tokenizer.json"))
        val previousShard = previousIndex?.resolve("shard-00")
        val reusable =
            previousShard != null &&
                previousDatabase != null &&
                Files.isRegularFile(previousDatabase) &&
                runCatching {
                    val metadata = Properties()
                    Files.newInputStream(previousShard.resolve("semantic.properties")).use(metadata::load)
                    metadata.getProperty("modelSha256") == modelHash &&
                        metadata.getProperty("tokenizerSha256") == tokenizerHash &&
                        metadata.getProperty("vectorEncoding") == Int8Vectors.ENCODING
                }.getOrDefault(false)
        val previousLines = if (reusable) personalSemanticLines(requireNotNull(previousDatabase)) else emptyMap()
        if (reusable) {
            Files.list(requireNotNull(previousShard)).use { files ->
                files.filter { Files.isRegularFile(it) && it.fileName.toString() != "write.lock" }.forEach {
                    Files.copy(it, shard.resolve(it.fileName))
                }
            }
        }
        var indexed = 0L
        DriverManager.getConnection("jdbc:sqlite:${database.toUri()}?mode=ro").use { connection ->
            val count =
                connection.createStatement().use { statement ->
                    statement.executeQuery("SELECT count(*) FROM line").use { rows ->
                        rows.next()
                        rows.getLong(1)
                    }
                }
            FSDirectory.open(shard).use { directory ->
                IndexWriter(directory, IndexWriterConfig(StandardAnalyzer())).use { writer ->
                    val reader = if (reusable) DirectoryReader.open(writer) else null
                    val oldDocuments = mutableMapOf<Long, Int>()
                    reader?.leaves()?.forEach { leaf ->
                        val stored = leaf.reader().storedFields()
                        val live = leaf.reader().liveDocs
                        repeat(leaf.reader().maxDoc()) { localId ->
                            if (live == null || live.get(localId)) {
                                val lineId =
                                    stored
                                        .document(localId)
                                        .getField("line_id")
                                        .numericValue()
                                        .toLong()
                                oldDocuments[lineId] = leaf.docBase + localId
                            }
                        }
                    }
                    try {
                        connection.createStatement().use { statement ->
                            statement
                                .executeQuery(
                                    "SELECT l.id, l.bookId, l.content, b.isBaseBook FROM line l JOIN book b ON b.id=l.bookId ORDER BY l.id",
                                ).use { rows ->
                                    var visited = 0L
                                    while (rows.next()) {
                                        currentCoroutineContext().ensureActive()
                                        val text = Round2Normalizer.clean(rows.getString(3))
                                        if (text.isNotBlank()) {
                                            val lineId = rows.getLong(1)
                                            val oldDocument = oldDocuments.remove(lineId)
                                            val signature = PersonalSemanticLine(rows.getLong(2), rows.getInt(4), semanticTextHash(text))
                                            if (oldDocument != null && previousLines[lineId] == signature) {
                                                indexed++
                                                visited++
                                                if (visited % 16 == 0L || visited == count) {
                                                    onProgress(visited.toFloat() / count.coerceAtLeast(1))
                                                }
                                                yield()
                                                continue
                                            }
                                            if (oldDocument !=
                                                null
                                            ) {
                                                check(writer.tryDeleteDocument(requireNotNull(reader), oldDocument) >= 0)
                                            }
                                            val vector = embed(text)
                                            require(vector.size == 256 && vector.all { it.isFinite() } && vector.any { it != 0f })
                                            val bookId = rows.getLong(2)
                                            require(bookId in Int.MIN_VALUE.toLong()..Int.MAX_VALUE.toLong())
                                            writer.addDocument(
                                                Document().apply {
                                                    add(StoredField("line_id", rows.getLong(1)))
                                                    add(StoredField("book_id", bookId))
                                                    add(IntPoint("book_id", bookId.toInt()))
                                                    add(IntPoint("is_base_book", rows.getInt(4)))
                                                    add(
                                                        KnnByteVectorField(
                                                            "vec",
                                                            Int8Vectors.quantize(vector),
                                                            VectorSimilarityFunction.COSINE,
                                                        ),
                                                    )
                                                },
                                            )
                                            indexed++
                                        }
                                        visited++
                                        if (visited % 16 == 0L || visited == count) onProgress(visited.toFloat() / count.coerceAtLeast(1))
                                        yield()
                                    }
                                }
                        }
                        oldDocuments.values.forEach { check(writer.tryDeleteDocument(requireNotNull(reader), it) >= 0) }
                        require(allowEmpty || indexed > 0) { "No text to index" }
                        writer.commit()
                    } finally {
                        reader?.close()
                    }
                }
            }
        }
        currentCoroutineContext().ensureActive()
        val properties =
            Properties().apply {
                setProperty("format", "zayit-round2-1")
                setProperty("vectorEncoding", Int8Vectors.ENCODING)
                setProperty("dimension", "256")
                setProperty("shardIndex", "0")
                setProperty("shardCount", "1")
                setProperty("eligible", indexed.toString())
                setProperty("indexed", indexed.toString())
                setProperty("databaseSha256", SemanticFileFingerprint.hash(database))
                setProperty("modelSha256", modelHash)
                setProperty("tokenizerSha256", tokenizerHash)
            }
        Files.newOutputStream(shard.resolve("semantic.properties")).use { properties.store(it, null) }
        completed = true
        return indexed
    } finally {
        // The caller supplies a new UUID directory. Never touch a previously published index.
        if (!completed) deletePersonalSemanticBuild(output)
    }
}

private data class PersonalSemanticLine(
    val bookId: Long,
    val baseBook: Int,
    val textHash: String,
)

private fun semanticTextHash(text: String): String = MessageDigest.getInstance("SHA-256").digest(text.toByteArray()).toHex()

private fun personalSemanticLines(database: Path): Map<Long, PersonalSemanticLine> =
    DriverManager.getConnection("jdbc:sqlite:${database.toUri()}?mode=ro").use { connection ->
        connection.createStatement().use { statement ->
            statement.executeQuery("SELECT l.id,l.bookId,l.content,b.isBaseBook FROM line l JOIN book b ON b.id=l.bookId").use { rows ->
                buildMap {
                    while (rows.next()) {
                        put(
                            rows.getLong(1),
                            PersonalSemanticLine(
                                rows.getLong(2),
                                rows.getInt(4),
                                semanticTextHash(Round2Normalizer.clean(rows.getString(3))),
                            ),
                        )
                    }
                }
            }
        }
    }

internal fun deletePersonalSemanticBuild(output: Path) {
    val target = output.toAbsolutePath().normalize()
    require(target.fileName.toString().startsWith("personal-semantic-"))
    if (Files.exists(target)) {
        Files.walk(target).use { paths -> paths.sorted(Comparator.reverseOrder()).forEach(Files::delete) }
    }
}

internal fun personalSearchEngine(
    artifacts: PersonalLibraryArtifacts,
    configuration: PersonalLibraryConfiguration,
    repository: SeforimRepository,
    databasePath: Path,
    embedder: suspend () -> SeforimEmbedder?,
): SearchEngine {
    val lexical =
        LuceneSearchEngine(
            indexDir = artifacts.indexPath,
            snippetProvider = RepositorySnippetSourceProvider(repository),
            dictionaryPath = databasePath.resolveSibling("lexical.db"),
        )
    val semantic = configuration.semanticDirectory?.let(Path::of)
    if (!configuration.semanticEnabled ||
        configuration.semanticGeneration != artifacts.generation ||
        semantic == null ||
        !Files.isRegularFile(semantic.resolve("shard-00/semantic.properties"))
    ) {
        return lexical
    }
    return HybridSearchEngine(
        lexical = lexical,
        modelDir = Path.of("$databasePath.semantic/model"),
        indexDir = semantic,
        dbPath = artifacts.databasePath,
        sharedEmbedderProvider = embedder,
        optionsProvider = io.github.kdroidfilter.seforimapp.features.search.SemanticSearchSettings::currentOptions,
        resolveLine = { lineId, bookId, query ->
            val line = repository.getLine(lineId)
            val book = repository.getBookCore(bookId)
            if (line == null || book == null || line.bookId != book.id) {
                null
            } else {
                LineHit(
                    book.id,
                    book.title,
                    line.id,
                    line.lineIndex,
                    lexical.buildSnippet(line.content, query, 5),
                    0f,
                    line.content,
                    book.isBaseBook,
                )
            }
        },
    )
}

internal fun PersonalLibraryArtifacts.newSemanticDirectory(): Path = databasePath.resolveSibling("personal-semantic-${UUID.randomUUID()}")
