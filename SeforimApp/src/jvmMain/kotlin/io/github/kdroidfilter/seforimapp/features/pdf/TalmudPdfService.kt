package io.github.kdroidfilter.seforimapp.features.pdf

import com.github.luben.zstd.ZstdInputStream
import io.github.kdroidfilter.seforimapp.framework.database.getDatabasePath
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import java.io.BufferedInputStream
import java.io.File
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.file.Files
import java.util.concurrent.atomic.AtomicReference
import javax.imageio.ImageIO
import kotlin.io.path.createTempFile

private const val TALMUD_BAVLI_DIR = "תלמוד בבלי"
private const val TALMUD_ROOT_TITLE = "תלמוד"
private const val BAVLI_CATEGORY_TITLE = "בבלי"
private const val DOWNLOAD_URL = "https://github.com/Otzaria/otzaria-library/releases/latest/download/talmud_bavli_latest.tar.zst"

object TalmudPdfService {
    private val pdfTitleCache = AtomicReference<Set<String>?>(null)
    private val _libraryVersion = MutableStateFlow(0L)
    val libraryVersion: StateFlow<Long> = _libraryVersion.asStateFlow()

    init {
        ImageIO.scanForPlugins()
    }

    fun pdfDirectory(): File = File(File(getDatabasePath()).absoluteFile.parentFile, TALMUD_BAVLI_DIR)

    fun isInstalled(): Boolean = scanAvailablePdfTitles().isNotEmpty()

    fun isTalmudBavliTitle(title: String?): Boolean = title == TALMUD_BAVLI_DIR

    fun isTalmudBavliCategoryPath(titles: Iterable<String>): Boolean {
        var hasTalmudRoot = false
        var hasBavliCategory = false
        for (title in titles) {
            when (title.trim()) {
                TALMUD_ROOT_TITLE -> hasTalmudRoot = true
                BAVLI_CATEGORY_TITLE, TALMUD_BAVLI_DIR -> hasBavliCategory = true
            }
        }
        return hasTalmudRoot && hasBavliCategory
    }

    fun pdfForTitle(title: String): File? {
        val dir = pdfDirectory()
        if (!dir.isDirectory) return null
        val base = title.trim()
        return sequenceOf("$base.pdf", "$base.PDF")
            .map { File(dir, it) }
            .firstOrNull { it.isFile }
    }

    fun availablePdfTitles(): Set<String> {
        pdfTitleCache.get()?.let { return it }
        val titles = scanAvailablePdfTitles()
        pdfTitleCache.compareAndSet(null, titles)
        return pdfTitleCache.get().orEmpty()
    }

    private fun scanAvailablePdfTitles(): Set<String> {
        val dir = pdfDirectory()
        return if (dir.isDirectory) {
            dir
                .walkTopDown()
                .filter { it.isFile && it.extension.equals("pdf", ignoreCase = true) }
                .map { it.nameWithoutExtension.trim() }
                .filter { it.isNotBlank() }
                .toSet()
        } else {
            emptySet()
        }
    }

    fun hasPdfForTitle(title: String): Boolean = availablePdfTitles().contains(title.trim())

    fun refreshAvailablePdfTitles() {
        pdfTitleCache.set(null)
        _libraryVersion.value = _libraryVersion.value + 1
    }

    fun importArchive(archive: File) {
        extractTarZst(archive, pdfDirectory())
        refreshAvailablePdfTitles()
    }

    fun downloadAndInstall(onProgress: (Long, Long) -> Unit = { _, _ -> }) {
        val tmp = createTempFile(prefix = "talmud_bavli_", suffix = ".tar.zst")
        try {
            val client = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.ALWAYS).build()
            val request = HttpRequest.newBuilder(URI.create(DOWNLOAD_URL)).GET().build()
            val response = client.send(request, HttpResponse.BodyHandlers.ofInputStream())
            require(response.statusCode() in 200..299) { "Download failed: HTTP ${response.statusCode()}" }
            val total = response.headers().firstValueAsLong("Content-Length").orElse(-1L)
            Files.newOutputStream(tmp).use { out ->
                response.body().use { input ->
                    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                    var copied = 0L
                    while (true) {
                        val read = input.read(buffer)
                        if (read < 0) break
                        out.write(buffer, 0, read)
                        copied += read
                        onProgress(copied, total)
                    }
                }
            }
            extractTarZst(tmp.toFile(), pdfDirectory())
            refreshAvailablePdfTitles()
        } finally {
            Files.deleteIfExists(tmp)
        }
    }

    fun removeInstalledLibrary() {
        val directory = pdfDirectory().canonicalFile
        val databaseParent = File(getDatabasePath()).absoluteFile.parentFile.canonicalFile
        require(directory.parentFile == databaseParent) { "Refusing to remove an unexpected PDF directory" }
        if (directory.exists()) {
            check(directory.deleteRecursively()) { "Could not remove the installed PDF library" }
        }
        refreshAvailablePdfTitles()
    }

    private fun extractTarZst(
        archive: File,
        targetDir: File,
    ) {
        targetDir.mkdirs()
        val canonicalTarget = targetDir.canonicalFile.toPath()
        TarArchiveInputStream(ZstdInputStream(BufferedInputStream(archive.inputStream()))).use { tar ->
            while (true) {
                val entry = tar.nextEntry ?: break
                if (entry.isDirectory) continue
                val fileName = File(entry.name).name.takeIf { it.endsWith(".pdf", ignoreCase = true) } ?: continue
                val output = targetDir.toPath().resolve(fileName).normalize()
                require(output.startsWith(canonicalTarget)) { "Unsafe archive entry: ${entry.name}" }
                Files.newOutputStream(output).use { tar.copyTo(it) }
            }
        }
    }
}
