package io.github.kdroidfilter.seforimapp.features.onboarding.extract

import com.github.luben.zstd.ZstdInputStream
import com.github.luben.zstd.ZstdOutputStream
import org.apache.commons.compress.archivers.tar.TarArchiveEntry
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.SequenceInputStream
import java.util.Collections
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class ConcatenatedTarInputStreamTest {
    @Test
    fun `reads all concatenated frames and preserves zero-filled file content`() {
        val expected =
            linkedMapOf(
                "seforim.db" to ByteArray(2048),
                "תלמוד בבלי/ברכות.pdf" to "pdf".toByteArray(),
                "model/tokenizer.json" to "tokenizer".toByteArray(),
            )
        val frames = expected.map { (name, bytes) -> frame(name, bytes) }
        val joined = frames.fold(byteArrayOf()) { bytes, frame -> bytes + frame }
        for (partSize in listOf(joined.size, 17)) {
            val parts = joined.toList().chunked(partSize).map { ByteArrayInputStream(it.toByteArray()) }
            SequenceInputStream(Collections.enumeration(parts)).use { source ->
                ConcatenatedTarInputStream(ZstdInputStream(source)).use { tar ->
                    val actual = linkedMapOf<String, List<Byte>>()
                    while (true) {
                        val entry = tar.nextEntry ?: break
                        actual[entry.name] = tar.readBytes().toList()
                    }
                    assertEquals(expected.mapValues { it.value.toList() }, actual)
                }
            }
        }
    }

    @Test
    fun `rejects an incomplete header after the first archive`() {
        val compressed = frame("seforim.db", "books".toByteArray())
        val first = ZstdInputStream(ByteArrayInputStream(compressed)).use { it.readBytes() }
        ConcatenatedTarInputStream(ByteArrayInputStream(first + byteArrayOf(1, 2))).use { tar ->
            assertEquals("seforim.db", tar.nextEntry?.name)
            assertEquals("books", tar.readBytes().decodeToString())
            assertFailsWith<IllegalStateException> { tar.nextEntry }
        }
    }

    private fun frame(
        name: String,
        bytes: ByteArray,
    ): ByteArray {
        val output = ByteArrayOutputStream()
        TarArchiveOutputStream(ZstdOutputStream(output)).use { tar ->
            tar.setAddPaxHeadersForNonAsciiNames(true)
            tar.putArchiveEntry(TarArchiveEntry(name).apply { size = bytes.size.toLong() })
            tar.write(bytes)
            tar.closeArchiveEntry()
        }
        return output.toByteArray()
    }
}
