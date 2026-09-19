package io.github.kdroidfilter.seforimapp.features.sharedstudy

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.nio.ByteBuffer
import java.util.zip.CRC32
import kotlin.math.ceil

internal object SharedStudyWireCodec {
    val json: Json =
        Json {
            classDiscriminator = "kind"
            ignoreUnknownKeys = true
            encodeDefaults = true
        }

    fun encode(message: StudyMessage): ByteArray = json.encodeToString<StudyMessage>(message).encodeToByteArray()

    fun decode(payload: ByteArray): StudyMessage? =
        runCatching { json.decodeFromString<StudyMessage>(payload.decodeToString()) }.getOrNull()
}

/**
 * Encodes protocol messages into MTU-safe BLE packets and reassembles out-of-order notifications.
 * The default 180-byte packet size fits a commonly negotiated 185-byte ATT MTU after overhead.
 */
class SharedStudyPacketCodec(
    private val maxPacketSize: Int = 180,
    private val json: Json = SharedStudyWireCodec.json,
) {
    init {
        require(maxPacketSize > HEADER_SIZE) { "BLE packet size must exceed $HEADER_SIZE bytes" }
    }

    fun encode(message: StudyMessage): List<ByteArray> {
        val payload = json.encodeToString<StudyMessage>(message).encodeToByteArray()
        val contentSize = maxPacketSize - HEADER_SIZE
        val packetCount = ceil(payload.size.toDouble() / contentSize).toInt().coerceAtLeast(1)
        require(packetCount <= UShort.MAX_VALUE.toInt()) { "Message is too large" }
        val messageId = CRC32().also { it.update(payload) }.value.toInt()
        return List(packetCount) { index ->
            val start = index * contentSize
            val end = minOf(payload.size, start + contentSize)
            val content = payload.copyOfRange(start, end)
            ByteBuffer
                .allocate(HEADER_SIZE + content.size)
                .putShort(MAGIC)
                .putInt(messageId)
                .putShort(index.toShort())
                .putShort(packetCount.toShort())
                .put(content)
                .array()
        }
    }

    class Reassembler(
        private val json: Json = SharedStudyWireCodec.json,
        private val maxPendingMessages: Int = 32,
    ) {
        private val pending = linkedMapOf<Int, Array<ByteArray?>>()

        @Synchronized
        fun accept(packet: ByteArray): StudyMessage? {
            if (packet.size < HEADER_SIZE) return null
            val buffer = ByteBuffer.wrap(packet)
            if (buffer.short != MAGIC) return null
            val messageId = buffer.int
            val index = buffer.short.toInt() and 0xFFFF
            val count = buffer.short.toInt() and 0xFFFF
            if (count == 0 || index >= count || count > MAX_PACKET_COUNT) return null
            if (messageId !in pending && pending.size >= maxPendingMessages) {
                pending.remove(pending.keys.first())
            }
            val chunks = pending.getOrPut(messageId) { arrayOfNulls(count) }
            if (chunks.size != count) {
                pending.remove(messageId)
                return null
            }
            val content = ByteArray(buffer.remaining())
            buffer.get(content)
            chunks[index] = content
            if (chunks.any { it == null }) return null
            pending.remove(messageId)
            val payload = chunks.filterNotNull().fold(ByteArray(0)) { acc, bytes -> acc + bytes }
            val checksum = CRC32().also { it.update(payload) }.value.toInt()
            if (checksum != messageId) return null
            return runCatching { json.decodeFromString<StudyMessage>(payload.decodeToString()) }.getOrNull()
        }
    }

    private companion object {
        const val HEADER_SIZE = 10
        const val MAX_PACKET_COUNT = 4096
        const val MAGIC_VALUE = 0x5A59
        val MAGIC = MAGIC_VALUE.toShort()
    }
}
