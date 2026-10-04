package io.github.kdroidfilter.seforimapp.features.onboarding.extract

import org.apache.commons.compress.archivers.tar.TarArchiveEntry
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import java.io.InputStream
import java.io.PushbackInputStream

/** Reads all TAR archives in a concatenated, decompressed zstd stream. */
internal class ConcatenatedTarInputStream(
    source: InputStream,
) : InputStream() {
    private val input = PushbackInputStream(source, 512)
    private var archive: TarArchiveInputStream? = null

    val nextEntry: TarArchiveEntry?
        get() {
            archive?.nextEntry?.let { return it }
            // Each archive has EOF records and may have additional zero-filled block padding.
            // A 512-byte TAR block prevents the parser from consuming the next archive's header.
            while (true) {
                val record = input.readNBytes(512)
                if (record.isEmpty()) return null
                check(record.size == 512) { "Incomplete archive header" }
                if (record.all { it == 0.toByte() }) continue
                input.unread(record)
                archive = TarArchiveInputStream(input, 512)
                return archive?.nextEntry
            }
        }

    override fun read(): Int = checkNotNull(archive).read()

    override fun read(
        bytes: ByteArray,
        offset: Int,
        length: Int,
    ): Int = checkNotNull(archive).read(bytes, offset, length)

    override fun close() = input.close()
}
