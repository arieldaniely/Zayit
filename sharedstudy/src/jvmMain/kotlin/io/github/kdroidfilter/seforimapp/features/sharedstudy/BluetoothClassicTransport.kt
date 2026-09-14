package io.github.kdroidfilter.seforimapp.features.sharedstudy

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * JNI-backed RFCOMM transport. The native adapter only exposes paired devices and a framed byte
 * stream; protocol serialization, invitation handling and reliability stay in Kotlin.
 */
class BluetoothClassicTransport : SharedStudyTransport {
    private val state = MutableStateFlow(BluetoothState.UNKNOWN)
    private val devices = MutableStateFlow<List<NearbyStudyDevice>>(emptyList())
    private val incoming = MutableSharedFlow<IncomingStudyMessage>(extraBufferCapacity = 256)
    private val nativeLoaded = loadNativeLibrary()

    override val bluetoothState = state.asStateFlow()
    override val nearbyDevices = devices.asStateFlow()
    override val incomingMessages = incoming.asSharedFlow()

    init {
        refreshNativeState()
    }

    override suspend fun refreshBluetoothState() = refreshNativeState()

    override suspend fun startDiscovery(localName: String) {
        check(nativeLoaded) { "רכיב Bluetooth Classic אינו זמין במערכת זו" }
        nativeStartDiscovery(localName.take(48), SERVICE_UUID)
    }

    override suspend fun stopDiscovery() {
        if (nativeLoaded) nativeStopDiscovery()
    }

    override suspend fun advertise(localName: String) {
        if (nativeLoaded) nativeStartServer(localName.take(48), SERVICE_UUID)
    }

    override suspend fun stopAdvertising() {
        if (nativeLoaded) nativeStopServer()
    }

    override suspend fun connect(deviceId: String) {
        check(nativeLoaded) { "רכיב Bluetooth Classic אינו זמין במערכת זו" }
        nativeConnect(deviceId, SERVICE_UUID)
    }

    override suspend fun disconnect(deviceId: String) {
        if (nativeLoaded) nativeDisconnect(deviceId)
    }

    override suspend fun send(
        deviceId: String,
        message: StudyMessage,
    ) {
        check(nativeLoaded) { "רכיב Bluetooth Classic אינו זמין במערכת זו" }
        nativeSend(deviceId, SharedStudyWireCodec.encode(message))
    }

    override fun openBluetoothSettings(): Boolean = PlatformConnectionSettings.openBluetooth()

    @Suppress("unused")
    private fun onBluetoothStateChanged(enabled: Boolean) {
        state.value = if (enabled) BluetoothState.ON else BluetoothState.OFF
    }

    @Suppress("unused")
    private fun onDevicesChanged(
        ids: Array<String>,
        names: Array<String>,
    ) {
        devices.value =
            ids.indices.map { index ->
                NearbyStudyDevice(
                    id = ids[index],
                    displayName = names.getOrNull(index).orEmpty().ifBlank { ids[index] },
                    transportKind = StudyTransportKind.BLUETOOTH_CLASSIC,
                )
            }
    }

    @Suppress("unused")
    private fun onMessageReceived(
        deviceId: String,
        payload: ByteArray,
    ) {
        SharedStudyWireCodec.decode(payload)?.let { incoming.tryEmit(IncomingStudyMessage(deviceId, it)) }
    }

    private fun refreshNativeState() {
        state.value =
            if (!nativeLoaded) {
                BluetoothState.UNSUPPORTED
            } else if (runCatching { nativeIsBluetoothEnabled() }.getOrDefault(false)) {
                BluetoothState.ON
            } else {
                BluetoothState.OFF
            }
    }

    private fun loadNativeLibrary(): Boolean =
        runCatching {
            check(BundledNativeLibrary.load(NATIVE_LIBRARY_NAME))
            nativeInitialize()
            true
        }.getOrDefault(false)

    private external fun nativeInitialize()
    private external fun nativeIsBluetoothEnabled(): Boolean
    private external fun nativeStartDiscovery(localName: String, serviceUuid: String)
    private external fun nativeStopDiscovery()
    private external fun nativeStartServer(localName: String, serviceUuid: String)
    private external fun nativeStopServer()
    private external fun nativeConnect(deviceId: String, serviceUuid: String)
    private external fun nativeDisconnect(deviceId: String)
    private external fun nativeSend(deviceId: String, payload: ByteArray)

    companion object {
        const val SERVICE_UUID = "5a617969-7400-5246-434f-4d4d00000001"
        private const val NATIVE_LIBRARY_NAME = "zayit-bluetooth"
    }
}
