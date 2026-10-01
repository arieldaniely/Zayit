package io.github.kdroidfilter.seforimapp.features.search

import com.github.luben.zstd.ZstdIOException
import com.github.luben.zstd.ZstdInputStream
import io.github.kdroidfilter.seforimapp.framework.database.getDatabasePath
import io.github.kdroidfilter.seforimapp.logger.warnln
import io.github.kdroidfilter.seforimlibrary.search.SeforimEmbedder
import io.github.kdroidfilter.seforimlibrary.search.VectorSearcher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import java.io.EOFException
import java.io.FilterInputStream
import java.io.IOException
import java.io.InputStream
import java.io.SequenceInputStream
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import java.util.Collections
import java.util.Comparator
import java.util.zip.ZipException
import java.util.zip.ZipFile
import java.util.zip.ZipInputStream

/** Installs a complete, versioned index and model bundle beside the database. */
internal object SemanticAssetsManager {
    enum class InstallationPhase { DOWNLOADING, EXTRACTING, VALIDATING }

    data class InstallationProgress(
        val phase: InstallationPhase,
        val fraction: Float? = null,
    )

    private val _installationProgress = MutableStateFlow<InstallationProgress?>(null)
    val installationProgress = _installationProgress.asStateFlow()

    enum class Availability { UNAVAILABLE, VALIDATING, READY, INVALID }

    private val _availability = MutableStateFlow(Availability.UNAVAILABLE)
    val availability = _availability.asStateFlow()
    private var validatedStamp: List<String>? = null

    /** Cache expensive validation until the database or any bundle file changes. Call on IO. */
    @Synchronized
    fun validatedReady(): Boolean {
        if (!isReady()) {
            validatedStamp = null
            _availability.value = Availability.UNAVAILABLE
            return false
        }
        return try {
            val stamp = semanticBundleStamp(assetRoot, db)
            if (stamp != validatedStamp) {
                _availability.value = Availability.VALIDATING
                validate()
                validatedStamp = stamp
                _availability.value = Availability.READY
            }
            _availability.value == Availability.READY
        } catch (failure: Throwable) {
            if (failure !is Exception && failure !is LinkageError) throw failure
            warnln(failure) { "Semantic bundle validation failed" }
            _availability.value = Availability.INVALID
            false
        }
    }

    private const val MODEL_NAME = "seforim-embed-round2-int8.onnx"
    private const val MODEL_SHA = "659226865abd3a1bc833565ae6b2e2f48abdd7136285824a12966d4d3294cbf8"
    private const val TOKENIZER_SHA = "0664287976ecb078bdfd8f5e5515dc87d8cb7f985a79a481aa1cdf7a7321c0e9"
    private const val RELEASE_API = "https://api.github.com/repos/arieldaniely/Zayit/releases?per_page=100"
    private val db: Path get() = Path.of(getDatabasePath())
    private val assetRoot: Path get() = Path.of("${getDatabasePath()}.semantic")
    private val client = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NORMAL).build()

    fun isReady(): Boolean =
        Files.isRegularFile(assetRoot.resolve("model/$MODEL_NAME")) &&
            Files.isRegularFile(assetRoot.resolve("model/tokenizer.json")) &&
            Files.isDirectory(assetRoot.resolve("index"))

    fun validate() {
        require(isReady()) { "חבילת החיפוש החכם אינה מותקנת" }
        verify(assetRoot)
        checkNotNull(SeforimEmbedder.tryLoad(assetRoot.resolve("model"))).use { it.embed("search") }
    }

    @Synchronized
    fun importBundle(selectedArchives: List<Path>) {
        require(selectedArchives.isNotEmpty()) { "יש לבחור את כל חלקי חבילת החיפוש" }
        val archives = resolveSemanticBundleArchives(selectedArchives)
        val names = archives.map { it.fileName.toString() }
        val isZip = names.all { it.endsWith(".zip", ignoreCase = true) }
        val isSingleZstd = names.size == 1 && names.single().endsWith(".tar.zst", ignoreCase = true)
        val partPattern = Regex("semantic-bundle\\.tar\\.zst\\.part(\\d+)")
        val partNumbers =
            names.map {
                partPattern
                    .matchEntire(it)
                    ?.groupValues
                    ?.get(1)
                    ?.toIntOrNull()
            }
        val isSplitZstd =
            partNumbers.all { it != null } &&
                partNumbers.filterNotNull().sorted() ==
                (1..archives.size).toList()
        require(isZip || isSingleZstd || isSplitZstd) { "בחרו קובץ tar.zst אחד או את כל חלקיו לפי הסדר" }
        val stage = Files.createTempDirectory(db.parent, "semantic-bundle-")
        val backup = stage.resolveSibling("${stage.fileName}-backup")
        val totalBytes = archives.sumOf(Files::size).coerceAtLeast(1L)
        var processedBytes = 0L
        var lastProgress = -1f

        fun reportBytes(count: Long) {
            processedBytes += count
            val fraction = (processedBytes.toFloat() / totalBytes).coerceIn(0f, 1f)
            if (fraction - lastProgress >= 0.001f) {
                lastProgress = fraction
                _installationProgress.value = InstallationProgress(InstallationPhase.EXTRACTING, fraction)
            }
        }
        _installationProgress.value = InstallationProgress(InstallationPhase.EXTRACTING, 0f)
        try {
            if (isZip) {
                var extracted = 0L
                archives.forEach { extracted = extractZip(it, stage, extracted, ::reportBytes) }
            } else {
                extractTarZstd(
                    if (isSingleZstd) {
                        archives
                    } else {
                        archives.sortedBy {
                            partPattern.matchEntire(it.fileName.toString())!!.groupValues[1].toInt()
                        }
                    },
                    stage,
                    ::reportBytes,
                )
            }
            _installationProgress.value = InstallationProgress(InstallationPhase.VALIDATING)
            try {
                verify(stage)
            } catch (failure: Exception) {
                throw SemanticBundleImportException(SemanticBundleImportProblem.INVALID_BUNDLE, cause = failure)
            }
            if (Files.exists(assetRoot)) Files.move(assetRoot, backup, StandardCopyOption.ATOMIC_MOVE)
            try {
                Files.move(stage, assetRoot, StandardCopyOption.ATOMIC_MOVE)
            } catch (failure: Throwable) {
                if (Files.exists(backup)) Files.move(backup, assetRoot, StandardCopyOption.ATOMIC_MOVE)
                throw failure
            }
            validatedStamp = null
        } finally {
            _installationProgress.value = null
            if (Files.exists(stage)) deleteTree(stage)
            if (Files.exists(backup) && Files.exists(assetRoot)) deleteTree(backup)
        }
    }

    fun downloadBundle(onProgress: (Int, Int) -> Unit = { _, _ -> }) {
        _installationProgress.value = InstallationProgress(InstallationPhase.DOWNLOADING)
        try {
            downloadAndInstallBundle(onProgress)
        } catch (failure: SemanticBundleImportException) {
            throw failure
        } catch (failure: IOException) {
            throw SemanticBundleImportException(SemanticBundleImportProblem.DOWNLOAD_FAILED, cause = failure)
        } finally {
            _installationProgress.value = null
        }
    }

    private fun downloadAndInstallBundle(onProgress: (Int, Int) -> Unit) {
        val response =
            client.send(
                HttpRequest.newBuilder(URI.create(RELEASE_API)).header("Accept", "application/vnd.github+json").build(),
                HttpResponse.BodyHandlers.ofString(),
            )
        if (response.statusCode() != 200) throw SemanticBundleImportException(SemanticBundleImportProblem.DOWNLOAD_FAILED)
        val databaseSha = hash(db)
        val releases =
            Json.parseToJsonElement(response.body()).jsonArray.filter {
                it.jsonObject["tag_name"]
                    ?.jsonPrimitive
                    ?.content
                    ?.startsWith("semantic-round2-") == true
            }
        val selected =
            releases
                .mapNotNull { entry ->
                    val manifestUrl =
                        entry.jsonObject["assets"]
                            ?.jsonArray
                            .orEmpty()
                            .firstOrNull { it.jsonObject["name"]?.jsonPrimitive?.content == "semantic-bundle.json" }
                            ?.jsonObject
                            ?.get("browser_download_url")
                            ?.jsonPrimitive
                            ?.content ?: return@mapNotNull null
                    val manifestResponse =
                        client.send(
                            HttpRequest.newBuilder(URI.create(manifestUrl)).header("User-Agent", "Zayit-Semantic-Search").build(),
                            HttpResponse.BodyHandlers.ofString(),
                        )
                    if (manifestResponse.statusCode() != 200) return@mapNotNull null
                    val manifest =
                        runCatching {
                            Json.parseToJsonElement(manifestResponse.body()).jsonObject
                        }.getOrNull() ?: return@mapNotNull null
                    val compatible =
                        runCatching {
                            manifest["format"]?.jsonPrimitive?.content == "zayit-round2-1" &&
                                manifest["databaseSha256"]?.jsonPrimitive?.content == databaseSha &&
                                (manifest["parts"]?.jsonPrimitive?.content?.toIntOrNull() ?: 0) > 0
                        }.getOrDefault(false)
                    if (compatible) entry to manifest else null
                }.firstOrNull() ?: throw SemanticBundleImportException(SemanticBundleImportProblem.NO_COMPATIBLE_BUNDLE)
        val (release, manifest) = selected
        val archiveType = manifest["archiveType"]?.jsonPrimitive?.content ?: "zip"
        val expectedParts = manifest["parts"]?.jsonPrimitive?.content?.toIntOrNull() ?: 0
        val assets =
            release.jsonObject["assets"]
                ?.jsonArray
                .orEmpty()
                .mapNotNull { asset ->
                    val obj = asset.jsonObject
                    val name = obj["name"]?.jsonPrimitive?.content ?: return@mapNotNull null
                    val url = obj["browser_download_url"]?.jsonPrimitive?.content ?: return@mapNotNull null
                    val matches =
                        if (archiveType == "tar.zst") {
                            name == "semantic-bundle.tar.zst" || Regex("semantic-bundle\\.tar\\.zst\\.part\\d+").matches(name)
                        } else {
                            Regex("semantic-bundle-part-\\d+\\.zip").matches(name)
                        }
                    if (matches) name to url else null
                }.sortedBy { it.first }
        if (assets.size != expectedParts) throw SemanticBundleImportException(SemanticBundleImportProblem.INVALID_MANIFEST)
        val downloadDir = Files.createTempDirectory(db.parent, "semantic-download-")
        try {
            val archives =
                assets.mapIndexed { index, (name, url) ->
                    onProgress(index + 1, assets.size)
                    val destination = downloadDir.resolve(name)
                    val result =
                        client.send(
                            HttpRequest.newBuilder(URI.create(url)).header("User-Agent", "Zayit-Semantic-Search").build(),
                            HttpResponse.BodyHandlers.ofInputStream(),
                        )
                    if (result.statusCode() != 200) {
                        result.body().close()
                        throw SemanticBundleImportException(SemanticBundleImportProblem.DOWNLOAD_FAILED)
                    }
                    result.body().use { Files.copy(it, destination) }
                    destination
                }
            onProgress(0, 0)
            importBundle(archives)
        } finally {
            deleteTree(downloadDir)
        }
    }

    private fun verify(root: Path) {
        val modelDir = root.resolve("model")
        require(hash(modelDir.resolve(MODEL_NAME)) == MODEL_SHA) { "קובץ המודל בחבילה אינו תואם ל־Round 2" }
        require(hash(modelDir.resolve("tokenizer.json")) == TOKENIZER_SHA) { "הטוקנייזר בחבילה אינו תואם למודל" }
        VectorSearcher(root.resolve("index"), 256, db, modelDir).use { }
    }

    internal fun extractZip(
        archive: Path,
        stage: Path,
        previousBytes: Long,
        onBytesRead: (Long) -> Unit = {},
    ): Long {
        var total = previousBytes
        try {
            // Check the central directory too: ZipInputStream alone accepts missing ZIP footers.
            ZipFile(archive.toFile()).use { }
            ZipInputStream(SemanticProgressInputStream(Files.newInputStream(archive), onBytesRead)).use { zip ->
                while (true) {
                    val entry = zip.nextEntry ?: break
                    val name = entry.name.replace('\\', '/')
                    val path = entryPath(stage, name)
                    if (entry.isDirectory) {
                        Files.createDirectories(path)
                    } else {
                        Files.createDirectories(path.parent)
                        Files.newOutputStream(path).use { output ->
                            val buffer = ByteArray(64 * 1024)
                            while (true) {
                                val count = zip.read(buffer)
                                if (count < 0) break
                                total += count
                                require(total <= 20L * 1024 * 1024 * 1024) { "חבילת החיפוש גדולה מדי" }
                                output.write(buffer, 0, count)
                            }
                        }
                    }
                    zip.closeEntry()
                }
            }
        } catch (failure: ZipException) {
            throw SemanticBundleImportException(SemanticBundleImportProblem.DAMAGED_ARCHIVE, cause = failure)
        } catch (failure: EOFException) {
            throw SemanticBundleImportException(SemanticBundleImportProblem.DAMAGED_ARCHIVE, cause = failure)
        }
        return total
    }

    internal fun extractTarZstd(
        archives: List<Path>,
        stage: Path,
        onBytesRead: (Long) -> Unit = {},
    ) {
        val inputs = archives.map { Files.newInputStream(it) }
        try {
            SequenceInputStream(Collections.enumeration(inputs)).use { joined ->
                ZstdInputStream(SemanticProgressInputStream(joined, onBytesRead)).use { zstd ->
                    TarArchiveInputStream(zstd).use { tar ->
                        var total = 0L
                        while (true) {
                            val entry = tar.nextEntry ?: break
                            require(!entry.isSymbolicLink && !entry.isLink) { "קישור אינו מותר בחבילה" }
                            val path = entryPath(stage, entry.name.replace('\\', '/'))
                            if (entry.isDirectory) {
                                Files.createDirectories(path)
                            } else {
                                require(entry.isFile && entry.size >= 0) { "רשומה לא תקינה בחבילה" }
                                total += entry.size
                                require(total <= 20L * 1024 * 1024 * 1024) { "חבילת החיפוש גדולה מדי" }
                                Files.createDirectories(path.parent)
                                Files.newOutputStream(path).use { output -> tar.copyTo(output, 64 * 1024) }
                            }
                        }
                        // TAR ends before the compression frame does; also verify the end of that frame.
                        val buffer = ByteArray(64 * 1024)
                        while (zstd.read(buffer) >= 0) {
                            // Drain remaining compressed data so a missing last part cannot pass validation.
                        }
                    }
                }
            }
        } catch (failure: ZstdIOException) {
            throw SemanticBundleImportException(SemanticBundleImportProblem.DAMAGED_ARCHIVE, cause = failure)
        }
    }

    private fun entryPath(
        stage: Path,
        name: String,
    ): Path {
        val valid =
            name == "model/" ||
                name == "index/" ||
                name.matches(Regex("model/(?:$MODEL_NAME|tokenizer\\.json)")) ||
                name.matches(Regex("index/shard-\\d+/?")) ||
                name.matches(Regex("index/shard-\\d+/[^/]+"))
        require(valid) { "מבנה חבילת החיפוש אינו תקין: $name" }
        return stage.resolve(name).normalize().also { path ->
            require(path.startsWith(stage)) { "נתיב לא תקין בחבילה" }
        }
    }

    private fun hash(path: Path): String {
        val digest = MessageDigest.getInstance("SHA-256")
        Files.newInputStream(path).use { input ->
            val buffer = ByteArray(1024 * 1024)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                digest.update(buffer, 0, count)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    private fun deleteTree(path: Path) {
        Files.walk(path).use { paths ->
            paths.sorted(Comparator.reverseOrder()).forEach(Files::deleteIfExists)
        }
    }
}

/** Path is Iterable<Path>; wrap the database to append the file, rather than its path components. */
internal fun semanticBundleStamp(
    root: Path,
    database: Path,
): List<String> =
    Files.walk(root).use { paths ->
        (paths.filter { Files.isRegularFile(it) }.toList() + listOf(database)).sorted().map {
            "$it:${Files.size(it)}:${Files.getLastModifiedTime(it)}"
        }
    }

/** Counts compressed input bytes, including bulk reads, without counting a byte twice. */
internal class SemanticProgressInputStream(
    input: InputStream,
    private val onBytesRead: (Long) -> Unit,
) : FilterInputStream(input) {
    override fun read(): Int = `in`.read().also { if (it >= 0) onBytesRead(1L) }

    override fun read(
        buffer: ByteArray,
        offset: Int,
        length: Int,
    ): Int = `in`.read(buffer, offset, length).also { if (it > 0) onBytesRead(it.toLong()) }

    override fun skip(count: Long): Long = `in`.skip(count).also { if (it > 0) onBytesRead(it) }
}
