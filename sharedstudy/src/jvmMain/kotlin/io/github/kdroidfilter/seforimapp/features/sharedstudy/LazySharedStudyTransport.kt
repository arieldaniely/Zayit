package io.github.kdroidfilter.seforimapp.features.sharedstudy

import io.github.santimattius.structured.annotations.StructuredScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Defers native Bluetooth and network setup until the user explicitly starts shared-study
 * discovery. Merely constructing the application graph or observing these flows has no OS side
 * effects and cannot trigger permission prompts.
 */
class LazySharedStudyTransport(
    @param:StructuredScope private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
    private val factory: () -> SharedStudyTransport,
) : SharedStudyTransport {
    private val lock = Any()

    @Volatile
    private var initialized: SharedStudyTransport? = null

    private val state = MutableStateFlow(BluetoothState.UNKNOWN)
    private val stage = MutableStateFlow(DiscoveryStage.AUTOMATIC)
    private val devices = MutableStateFlow<List<NearbyStudyDevice>>(emptyList())
    private val messages = MutableSharedFlow<IncomingStudyMessage>(extraBufferCapacity = 256)

    override val bluetoothState = state.asStateFlow()
    override val discoveryStage = stage.asStateFlow()
    override val nearbyDevices = devices.asStateFlow()
    override val incomingMessages = messages.asSharedFlow()

    override suspend fun refreshBluetoothState() = delegate().refreshBluetoothState()

    override suspend fun startDiscovery(localName: String) = delegate().startDiscovery(localName)

    override suspend fun stopDiscovery() {
        initialized?.stopDiscovery()
    }

    override suspend fun advertise(localName: String) = delegate().advertise(localName)

    override suspend fun stopAdvertising() {
        initialized?.stopAdvertising()
    }

    override suspend fun connect(deviceId: String) = delegate().connect(deviceId)

    override suspend fun disconnect(deviceId: String) {
        initialized?.disconnect(deviceId)
    }

    override suspend fun send(
        deviceId: String,
        message: StudyMessage,
    ) = delegate().send(deviceId, message)

    override fun openBluetoothSettings(): Boolean = initialized?.openBluetoothSettings() ?: PlatformConnectionSettings.openBluetooth()

    override fun openHotspotSettings(): Boolean = initialized?.openHotspotSettings() ?: PlatformConnectionSettings.openHotspot()

    private fun delegate(): SharedStudyTransport =
        initialized ?: synchronized(lock) {
            initialized ?: factory().also { transport ->
                initialized = transport
                forwardFlows(transport)
            }
        }

    private fun forwardFlows(transport: SharedStudyTransport) {
        scope.launch(start = CoroutineStart.UNDISPATCHED) {
            transport.bluetoothState.collect(state::emit)
        }
        scope.launch(start = CoroutineStart.UNDISPATCHED) {
            transport.discoveryStage.collect(stage::emit)
        }
        scope.launch(start = CoroutineStart.UNDISPATCHED) {
            transport.nearbyDevices.collect(devices::emit)
        }
        scope.launch(start = CoroutineStart.UNDISPATCHED) {
            transport.incomingMessages.collect(messages::emit)
        }
    }
}
