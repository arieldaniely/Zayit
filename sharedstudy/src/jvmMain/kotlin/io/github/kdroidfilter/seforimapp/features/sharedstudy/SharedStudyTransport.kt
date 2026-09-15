package io.github.kdroidfilter.seforimapp.features.sharedstudy

import kotlinx.coroutines.flow.Flow

data class IncomingStudyMessage(
    val deviceId: String,
    val message: StudyMessage,
)

/** BLE boundary. Production platform code and deterministic tests implement the same contract. */
interface SharedStudyTransport {
    val bluetoothState: Flow<BluetoothState>
    val discoveryStage: Flow<DiscoveryStage>
        get() = kotlinx.coroutines.flow.flowOf(DiscoveryStage.AUTOMATIC)
    val nearbyDevices: Flow<List<NearbyStudyDevice>>
    val incomingMessages: Flow<IncomingStudyMessage>

    suspend fun refreshBluetoothState()

    suspend fun startDiscovery(localName: String)

    suspend fun stopDiscovery()

    suspend fun advertise(localName: String)

    suspend fun stopAdvertising()

    suspend fun connect(deviceId: String)

    suspend fun disconnect(deviceId: String)

    suspend fun send(
        deviceId: String,
        message: StudyMessage,
    )

    fun openBluetoothSettings(): Boolean

    fun openHotspotSettings(): Boolean = PlatformConnectionSettings.openHotspot()
}

class UnavailableBleTransport : SharedStudyTransport {
    private val state = kotlinx.coroutines.flow.MutableStateFlow(BluetoothState.UNSUPPORTED)
    override val bluetoothState: Flow<BluetoothState> = state
    override val nearbyDevices = kotlinx.coroutines.flow.flowOf(emptyList<NearbyStudyDevice>())
    override val incomingMessages = kotlinx.coroutines.flow.emptyFlow<IncomingStudyMessage>()

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

    override fun openBluetoothSettings(): Boolean = PlatformConnectionSettings.openBluetooth()
}

internal object PlatformConnectionSettings {
    fun openBluetooth(): Boolean =
        runCatching {
            val os = System.getProperty("os.name").lowercase()
            val command =
                when {
                    os.contains("win") -> listOf("explorer.exe", "ms-settings:bluetooth")
                    os.contains("mac") -> listOf("open", "x-apple.systempreferences:com.apple.BluetoothSettings")
                    else -> listOf("blueman-manager")
                }
            ProcessBuilder(command).start()
            true
        }.getOrDefault(false)

    fun openHotspot(): Boolean =
        runCatching {
            val os = System.getProperty("os.name").lowercase()
            val command =
                when {
                    os.contains("win") -> listOf("explorer.exe", "ms-settings:network-mobilehotspot")
                    os.contains("mac") -> listOf("open", "x-apple.systempreferences:com.apple.NetworkSettings")
                    else -> listOf("nm-connection-editor")
                }
            ProcessBuilder(command).start()
            true
        }.getOrDefault(false)
}
