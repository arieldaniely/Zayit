package io.github.kdroidfilter.seforimapp.features.sharedstudy

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalCoroutinesApi::class)
class LazySharedStudyTransportTest {
    @Test
    fun `observing state does not construct native transport`() =
        runTest {
            var creationCount = 0
            val lazy =
                LazySharedStudyTransport(
                    factory = {
                        creationCount += 1
                        FakeTransport()
                    },
                    scope = backgroundScope,
                )

            lazy.bluetoothState
            lazy.nearbyDevices
            runCurrent()

            assertEquals(0, creationCount)

            lazy.startDiscovery("לוי")
            assertEquals(1, creationCount)
        }

    private class FakeTransport : SharedStudyTransport {
        override val bluetoothState = MutableStateFlow(BluetoothState.ON)
        override val nearbyDevices = MutableStateFlow<List<NearbyStudyDevice>>(emptyList())
        override val incomingMessages = MutableSharedFlow<IncomingStudyMessage>()

        override suspend fun refreshBluetoothState() = Unit

        override suspend fun startDiscovery(localName: String) = Unit

        override suspend fun stopDiscovery() = Unit

        override suspend fun advertise(localName: String) = Unit

        override suspend fun stopAdvertising() = Unit

        override suspend fun connect(deviceId: String) = Unit

        override suspend fun disconnect(deviceId: String) = Unit

        override suspend fun send(
            deviceId: String,
            message: StudyMessage,
        ) = Unit

        override fun openBluetoothSettings() = true
    }
}
