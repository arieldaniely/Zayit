package io.github.kdroidfilter.seforimapp.features.search

import io.github.kdroidfilter.seforimapp.framework.database.getDatabasePath
import io.github.kdroidfilter.seforimlibrary.search.VectorSearcher
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import java.util.Comparator
import java.util.zip.ZipInputStream

/** Installs a complete, versioned index and model bundle beside the database. */
internal object SemanticAssetsManager {
    private const val MODEL_NAME = "seforim-embed-round2-int8.onnx"
    private const val MODEL_SHA = "659226865abd3a1bc833565ae6b2e2f48abdd7136285824a12966d4d3294cbf8"
    private const val TOKENIZER_SHA = "0664287976ecb078bdfd8f5e5515dc87d8cb7f985a79a481aa1cdf7a7321c0e9"
    private const val RELEASE_API = "https://api.github.com/repos/arieldaniely/Zayit/releases?per_page=100"
    private val db: Path get() = Path.of(getDatabasePath())
    private val assetRoot: Path get() = Path.of("${getDatabasePath()}.semantic")
    private val client = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NORMAL).build()

    fun isReady(): Boolean = Files.isRegularFile(assetRoot.resolve("model/$MODEL_NAME")) &&
        Files.isRegularFile(assetRoot.resolve("model/tokenizer.json")) &&
        Files.isDirectory(assetRoot.resolve("index"))

    fun validate() {
        require(isReady()) { "חבילת החיפוש החכם אינה מותקנת" }
        verify(assetRoot)
    }

    @Synchronized
    fun importBundle(archives: List<Path>) {
        require(archives.isNotEmpty()) { "יש לבחור את כל חלקי חבילת החיפוש" }
        val stage = Files.createTempDirectory(db.parent, "semantic-bundle-")
        val backup = stage.resolveSibling("${stage.fileName}-backup")
        try {
            var extracted = 0L
            archives.forEach { extracted = extract(it, stage, extracted) }
            verify(stage)
            if (Files.exists(assetRoot)) Files.move(assetRoot, backup, StandardCopyOption.ATOMIC_MOVE)
            try {
                Files.move(stage, assetRoot, StandardCopyOption.ATOMIC_MOVE)
            } catch (failure: Throwable) {
                if (Files.exists(backup)) Files.move(backup, assetRoot, StandardCopyOption.ATOMIC_MOVE)
                throw failure
            }
        } finally {
            if (Files.exists(stage)) deleteTree(stage)
            if (Files.exists(backup) && Files.exists(assetRoot)) deleteTree(backup)
        }
    }

    fun downloadBundle() {
        val response = client.send(
            HttpRequest.newBuilder(URI.create(RELEASE_API)).header("Accept", "application/vnd.github+json").build(),
            HttpResponse.BodyHandlers.ofString(),
        )
        require(response.statusCode() == 200) { "לא ניתן לקרוא את רשימת חבילות החיפוש" }
        val databaseSha = hash(db)
        val releases = Json.parseToJsonElement(response.body()).jsonArray.filter {
            it.jsonObject["tag_name"]?.jsonPrimitive?.content?.startsWith("semantic-round2-") == true
        }
        val release = releases.firstOrNull { entry ->
            val manifestUrl = entry.jsonObject["assets"]?.jsonArray.orEmpty()
                .firstOrNull { it.jsonObject["name"]?.jsonPrimitive?.content == "semantic-bundle.json" }
                ?.jsonObject?.get("browser_download_url")?.jsonPrimitive?.content ?: return@firstOrNull false
            val manifestResponse = client.send(
                HttpRequest.newBuilder(URI.create(manifestUrl)).header("User-Agent", "Zayit-Semantic-Search").build(),
                HttpResponse.BodyHandlers.ofString(),
            )
            manifestResponse.statusCode() == 200 && runCatching {
                val manifest = Json.parseToJsonElement(manifestResponse.body()).jsonObject
                manifest["format"]?.jsonPrimitive?.content == "zayit-round2-1" &&
                    manifest["databaseSha256"]?.jsonPrimitive?.content == databaseSha &&
                    manifest["parts"]?.jsonPrimitive?.content == "16"
            }.getOrDefault(false)
        } ?: error("לא פורסמה חבילת חיפוש חכם התואמת למסד הנתונים המותקן")
        val assets = release.jsonObject["assets"]?.jsonArray.orEmpty().mapNotNull { asset ->
            val obj = asset.jsonObject
            val name = obj["name"]?.jsonPrimitive?.content ?: return@mapNotNull null
            val url = obj["browser_download_url"]?.jsonPrimitive?.content ?: return@mapNotNull null
            if (Regex("semantic-bundle-part-\\d+\\.zip").matches(name)) name to url else null
        }.sortedBy { it.first }
        require(assets.size == 16) { "חבילת החיפוש שפורסמה אינה כוללת את כל 16 החלקים" }
        val downloadDir = Files.createTempDirectory(db.parent, "semantic-download-")
        try {
            val archives = assets.map { (name, url) ->
                val destination = downloadDir.resolve(name)
                val result = client.send(
                    HttpRequest.newBuilder(URI.create(url)).header("User-Agent", "Zayit-Semantic-Search").build(),
                    HttpResponse.BodyHandlers.ofInputStream(),
                )
                require(result.statusCode() == 200) { "הורדת חלק $name נכשלה (HTTP ${result.statusCode()})" }
                result.body().use { Files.copy(it, destination) }
                destination
            }
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

    private fun extract(archive: Path, stage: Path, previousBytes: Long): Long {
        var total = previousBytes
        ZipInputStream(Files.newInputStream(archive)).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                val name = entry.name.replace('\\', '/')
                val valid = name == "model/" || name == "index/" ||
                    name.matches(Regex("model/(?:$MODEL_NAME|tokenizer\\.json)")) ||
                    name.matches(Regex("index/shard-\\d+/?")) ||
                    name.matches(Regex("index/shard-\\d+/[^/]+"))
                require(valid) { "מבנה חבילת החיפוש אינו תקין" }
                val path = stage.resolve(name).normalize()
                require(path.startsWith(stage)) { "נתיב לא תקין בחבילה" }
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
        return total
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
