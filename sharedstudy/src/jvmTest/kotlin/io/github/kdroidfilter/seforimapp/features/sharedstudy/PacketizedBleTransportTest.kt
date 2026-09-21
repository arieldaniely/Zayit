package io.github.kdroidfilter.seforimapp.features.sharedstudy

import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class PacketizedBleTransportTest {
    @Test
    fun `decodes the display name from a Zayit manufacturer packet`() {
        val packet = byteArrayOf('Z'.code.toByte(), 'Y'.code.toByte()) + "ראובן".encodeToByteArray()

        assertEquals("ראובן", packet.decodeZayitDisplayName())
        assertNull(byteArrayOf(1, 2, 3).decodeZayitDisplayName())
    }

    @Test
    fun `fragments and reassembles a message using negotiated packet size`() =
        runTest {
            val bridge = FakeBridge(packetSize = 32)
            val transport = PacketizedBleTransport(bridge)
            val expected = StudyMessage.Hello("participant", 1, "שם ארוך מאוד כדי לאלץ חלוקה למספר חבילות")
            val received = async(start = CoroutineStart.UNDISPATCHED) { transport.incomingMessages.first() }

            transport.send("peer-device", expected)
            assertTrue(bridge.writes.size > 1)
            bridge.writes.forEach { (_, packet) ->
                bridge.incoming.emit(BleIncomingPacket("peer-device", packet))
            }

            assertEquals(IncomingStudyMessage("peer-device", expected), received.await())
        }

    private class FakeBridge(
        private val packetSize: Int,
    ) : BlePlatformBridge {
        override val bluetoothState = MutableStateFlow(BluetoothState.ON)
        override val advertisements = MutableStateFlow<List<BleAdvertisement>>(emptyList())
        val incoming = MutableSharedFlow<BleIncomingPacket>(extraBufferCapacity = 16)
        override val incomingPackets = incoming
        val writes = mutableListOf<Pair<String, ByteArray>>()

        override suspend fun refreshState() = Unit

        override suspend fun startScanning(serviceUuid: String) = Unit

        override suspend fun stopScanning() = Unit

        override suspend fun startAdvertising(
            serviceUuid: String,
            localName: String,
        ) = Unit

        override suspend fun stopAdvertising() = Unit

        override suspend fun connect(
            deviceId: String,
            serviceUuid: String,
            writeCharacteristicUuid: String,
            notifyCharacteristicUuid: String,
        ) = Unit

        override suspend fun disconnect(deviceId: String) = Unit

        override suspend fun write(
            deviceId: String,
            packet: ByteArray,
        ) {
            writes += deviceId to packet
        }

        override fun maximumPacketSize(deviceId: String): Int = packetSize

        override fun openSettings(): Boolean = true
    }
}
