package io.github.kdroidfilter.seforimapp.features.pdf

import io.github.kdroidfilter.seforimapp.framework.database.getDatabasePath
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import com.github.luben.zstd.ZstdInputStream
import java.io.BufferedInputStream
import java.io.File
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.file.Files
import kotlin.io.path.createTempFile

private const val TALMUD_BAVLI_DIR = "תלמוד בבלי"
private const val DOWNLOAD_URL = "https://github.com/Otzaria/otzaria-library/releases/latest/download/talmud_bavli_latest.tar.zst"

object TalmudPdfService {
    fun pdfDirectory(): File = File(File(getDatabasePath()).absoluteFile.parentFile, TALMUD_BAVLI_DIR)

    fun pdfForTitle(title: String): File? {
        val dir = pdfDirectory()
        if (!dir.isDirectory) return null
        val base = title.trim()
        return sequenceOf("$base.pdf", "$base.PDF")
            .map { File(dir, it) }
            .firstOrNull { it.isFile }
    }

    fun hasPdfForTitle(title: String): Boolean = pdfForTitle(title) != null


    fun importArchive(archive: File) {
        extractTarZst(archive, pdfDirectory())
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
        } finally {
            Files.deleteIfExists(tmp)
        }
    }

    private fun extractTarZst(archive: File, targetDir: File) {
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
