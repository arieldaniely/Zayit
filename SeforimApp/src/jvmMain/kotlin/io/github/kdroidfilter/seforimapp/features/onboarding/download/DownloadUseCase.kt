package io.github.kdroidfilter.seforimapp.features.onboarding.download

import io.github.kdroidfilter.seforimapp.core.settings.AppSettings
import io.github.kdroidfilter.seforimapp.framework.database.databaseInstallDirectory
import io.github.kdroidfilter.seforimapp.network.HttpsConnectionFactory
import io.github.kdroidfilter.seforimapp.releasefetcher.github.GitHubReleaseFetcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Encapsulates the download of the latest database .zst asset with reactive progress.
 */
class DownloadUseCase(
    private val gitHubReleaseFetcher: GitHubReleaseFetcher,
    private val appSettings: AppSettings,
) {
    /**
     * Downloads the latest split bundle (.tar.zst split into .part01/.part02) and extracts it directly,
     * without creating an intermediate .tar file. If only a single .tar.zst exists (no parts), it is
     * downloaded and extracted as well.
     *
     * Progress: during network transfers, reports bytes and speed; during extraction, progress continues
     * from the last value to 100% with speed set to 0.
     */
    suspend fun downloadLatestBundle(
        onProgress: (readSoFar: Long, totalBytes: Long?, progress: Float, speedBytesPerSec: Long) -> Unit,
    ): String =
        withContext(Dispatchers.Default) {
            val latestRelease =
                withContext(Dispatchers.IO) { gitHubReleaseFetcher.getLatestRelease() }
                    ?: error("No release found")

            // Choose one complete distribution, never mix parts from optional bundles or variants.
            val allAssets = latestRelease.assets
            val partPattern = Regex("seforim_bundle\\.tar\\.zst\\.part(\\d+)", RegexOption.IGNORE_CASE)
            val partAssets =
                allAssets
                    .filter { partPattern.matches(it.name) }
                    .sortedBy { partPattern.matchEntire(it.name)!!.groupValues[1].toInt() }
            val singleAsset = allAssets.firstOrNull { it.name.equals("seforim_bundle.tar.zst", ignoreCase = true) }

            val dbDir = databaseInstallDirectory(appSettings).apply { mkdirs() }

            // Keep running stats for a smoother, more stable UX
            var lastBytes = 0L
            var lastTimeNs = System.nanoTime()
            var emaSpeed = 0.0 // Exponential moving average (bytes/sec)
            var lastEmittedNs = 0L

            fun report(
                read: Long,
                total: Long?,
            ) {
                val now = System.nanoTime()
                val dtNs = now - lastTimeNs
                if (dtNs > 0) {
                    val deltaBytes = (read - lastBytes).coerceAtLeast(0)
                    val instSpeed = (deltaBytes.toDouble() * 1_000_000_000.0) / dtNs.toDouble() // bytes/sec

                    // Time-constant based EMA like modern browsers (~3s smoothing)
                    val dtSec = dtNs / 1_000_000_000.0
                    val tau = 3.0 // seconds
                    val alpha = 1.0 - kotlin.math.exp(-dtSec / tau)
                    emaSpeed = if (emaSpeed == 0.0) instSpeed else emaSpeed + alpha * (instSpeed - emaSpeed)

                    lastBytes = read
                    lastTimeNs = now
                }

                val progress = if (total != null && total > 0) (read.toDouble() / total.toDouble()).toFloat() else 0f

                // Throttle UI updates to reduce flicker (~4 Hz) while staying responsive
                val shouldEmit = (now - lastEmittedNs) >= 250_000_000L || progress >= 1f
                if (shouldEmit) {
                    lastEmittedNs = now
                    onProgress(read, total, progress.coerceIn(0f, 1f), emaSpeed.toLong().coerceAtLeast(0L))
                }
            }

            if (partAssets.isNotEmpty()) {
                require(
                    partAssets.size >= 2 &&
                        partAssets.map {
                            partPattern.matchEntire(it.name)!!.groupValues[1].toInt()
                        } == (1..partAssets.size).toList(),
                ) { "Missing bundle parts" }
                val knownTotal = partAssets.sumOf { it.size.toLong() }.takeIf { it > 0L }
                var downloaded = 0L
                val files =
                    partAssets.map { asset ->
                        val file = File(dbDir, asset.name)
                        downloadFile(asset.browser_download_url, file) { read, _ -> report(downloaded + read, knownTotal) }
                        downloaded += file.length()
                        file
                    }
                report(downloaded, downloaded)
                return@withContext files.first().absolutePath
            } else if (singleAsset != null) {
                // Backward-compatible: single .tar.zst
                val tmp = File(dbDir, singleAsset.name)
                val knownTotal = runCatching { (singleAsset.size as? Number)?.toLong() }.getOrNull()?.takeIf { it > 0L }
                var totalLength: Long? = null
                downloadFile(singleAsset.browser_download_url, tmp) { r, t ->
                    if (t != null) totalLength = t
                    report(r, knownTotal ?: totalLength)
                }
                val total = knownTotal ?: totalLength ?: tmp.length()
                onProgress(total, total, 1f, 0L)
                return@withContext tmp.absolutePath
            } else {
                error("No bundle assets found in latest release")
            }
        }

    private suspend fun downloadFile(
        url: String,
        dest: File,
        onBytes: (readSoFar: Long, totalBytes: Long?) -> Unit,
    ) {
        withContext(Dispatchers.IO) {
            val connection =
                HttpsConnectionFactory.openConnection(url) {
                    setRequestProperty("Accept", "application/octet-stream")
                    setRequestProperty("User-Agent", "Zayita/1.0 (+https://github.com/arieldaniely/Zayit)")
                    connectTimeout = 30_000
                    readTimeout = 60_000
                    instanceFollowRedirects = true
                }
            connection.connect()
            val code = connection.responseCode
            if (code !in 200..299) {
                connection.disconnect()
                error("Download failed: $code")
            }
            val totalLength =
                connection.contentLengthLong.takeIf { it > 0 }
                    ?: connection.getHeaderFieldLong("Content-Length", -1L).takeIf { it > 0 }
            connection.inputStream.use { input ->
                dest.outputStream().use { out ->
                    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                    var total = 0L
                    while (true) {
                        val read = input.read(buffer)
                        if (read <= 0) break
                        out.write(buffer, 0, read)
                        total += read
                        onBytes(total, totalLength)
                    }
                    out.flush()
                }
            }
            connection.disconnect()
        }
        // Final callback to ensure UI shows completed values
        onBytes(dest.length(), dest.length().takeIf { it > 0L })
    }
}
