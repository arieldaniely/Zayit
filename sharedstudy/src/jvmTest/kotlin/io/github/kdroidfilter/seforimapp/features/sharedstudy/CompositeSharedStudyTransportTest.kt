package io.github.kdroidfilter.seforimapp.features.sharedstudy

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class CompositeSharedStudyTransportTest {
    @Test
    fun `namespaces devices and routes operations to their owning transport`() =
        runTest {
            val ble = FakeTransport()
            val lan = FakeTransport()
            val classic = FakeTransport()
            ble.devices.value = listOf(NearbyStudyDevice("peer", "ראובן"))
            val composite = CompositeSharedStudyTransport(ble, lan, classic, backgroundScope)

            val device = composite.nearbyDevices.first { it.isNotEmpty() }.single()
            composite.connect(device.id)
            composite.send(device.id, StudyMessage.Hello("local", 1, "לוי"))

            assertEquals("ble|peer", device.id)
            assertEquals(StudyTransportKind.BLE, device.transportKind)
            assertEquals(listOf("peer"), ble.connected)
            assertEquals("peer", ble.sent.single().first)
            assertTrue(lan.connected.isEmpty())
        }

    @Test
    fun `falls back from automatic discovery to pairing and hotspot guidance`() =
        runTest {
            val ble = FakeTransport()
            val lan = FakeTransport()
            val classic = FakeTransport()
            val composite = CompositeSharedStudyTransport(ble, lan, classic, backgroundScope)

            composite.startDiscovery("לוי")
            runCurrent()
            assertEquals(1, ble.discoveryStarts)
            assertEquals(1, lan.discoveryStarts)
            assertEquals(0, classic.discoveryStarts)

            advanceTimeBy(6_000L)
            runCurrent()
            assertEquals(DiscoveryStage.BLUETOOTH_PAIRING, composite.discoveryStage.first())
            assertEquals(1, classic.discoveryStarts)

            advanceTimeBy(8_000L)
            runCurrent()
            assertEquals(DiscoveryStage.HOTSPOT_GUIDANCE, composite.discoveryStage.first())
        }

    @Test
    fun `discovery succeeds when at least one automatic transport starts`() =
        runTest {
            val ble = FakeTransport(failDiscovery = true)
            val lan = FakeTransport()
            val composite = CompositeSharedStudyTransport(ble, lan, FakeTransport(), backgroundScope)

            composite.startDiscovery("לוי")

            assertEquals(1, ble.discoveryStarts)
            assertEquals(1, lan.discoveryStarts)
        }

    @Test
    fun `discovery propagates an error when every automatic transport fails`() =
        runTest {
            val composite =
                CompositeSharedStudyTransport(
                    FakeTransport(failDiscovery = true),
                    FakeTransport(failDiscovery = true),
                    FakeTransport(),
                    backgroundScope,
                )

            assertFailsWith<IllegalStateException> { composite.startDiscovery("לוי") }
        }

    private class FakeTransport(
        private val failDiscovery: Boolean = false,
    ) : SharedStudyTransport {
        override val bluetoothState = MutableStateFlow(BluetoothState.ON)
        val devices = MutableStateFlow<List<NearbyStudyDevice>>(emptyList())
        override val nearbyDevices = devices
        override val incomingMessages = MutableSharedFlow<IncomingStudyMessage>()
        val connected = mutableListOf<String>()
        val sent = mutableListOf<Pair<String, StudyMessage>>()
        var discoveryStarts = 0

        override suspend fun refreshBluetoothState() = Unit

        override suspend fun startDiscovery(localName: String) {
            discoveryStarts += 1
            if (failDiscovery) error("Discovery failed")
        }

        override suspend fun stopDiscovery() = Unit

        override suspend fun advertise(localName: String) = Unit

        override suspend fun stopAdvertising() = Unit

        override suspend fun connect(deviceId: String) {
            connected += deviceId
        }

        override suspend fun disconnect(deviceId: String) = Unit

        override suspend fun send(
            deviceId: String,
            message: StudyMessage,
        ) {
            sent += deviceId to message
        }

        override fun openBluetoothSettings(): Boolean = true
    }
}
