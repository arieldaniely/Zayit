package io.github.kdroidfilter.seforimapp.features.sharedstudy

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SharedStudyPacketCodecTest {
    @Test
    fun `encoder and reassembler share the packet count boundary`() {
        val codec = SharedStudyPacketCodec(maxPacketSize = 20)
        val empty = StudyMessage.Hello("peer", 1, "")
        val overhead = SharedStudyWireCodec.encode(empty).size
        val message = empty.copy(displayName = "a".repeat(4096 * 10 - overhead))
        val packets = codec.encode(message)

        assertEquals(4096, packets.size)
        assertTrue(packets.all { it.size <= 20 })
        val reassembler = SharedStudyPacketCodec.Reassembler()
        assertEquals(message, packets.reversed().mapNotNull(reassembler::accept).single())
        assertFailsWith<IllegalArgumentException> {
            codec.encode(message.copy(displayName = message.displayName + "a"))
        }
    }

    @Test
    fun `long note is fragmented and reassembled out of order`() {
        val codec = SharedStudyPacketCodec(maxPacketSize = 40)
        val message =
            StudyMessage.NoteChanged(
                senderId = "peer",
                sequence = 8,
                sessionId = "session",
                note = SharedStudyNote("note", "peer", 10, 20, 1, 9, "תוכן ".repeat(80), updatedAt = 7),
            )
        val packets = codec.encode(message)
        val reassembler = SharedStudyPacketCodec.Reassembler()

        assertTrue(packets.size > 1)
        val decoded = packets.reversed().mapNotNull(reassembler::accept).single()
        assertEquals(message, decoded)
    }

    @Test
    fun `corrupted packet does not produce a message`() {
        val codec = SharedStudyPacketCodec(maxPacketSize = 512)
        val packet = codec.encode(StudyMessage.Hello("a", 1, "א")).single()
        packet[packet.lastIndex] = (packet.last() + 1).toByte()

        assertNull(SharedStudyPacketCodec.Reassembler().accept(packet))
    }
}
