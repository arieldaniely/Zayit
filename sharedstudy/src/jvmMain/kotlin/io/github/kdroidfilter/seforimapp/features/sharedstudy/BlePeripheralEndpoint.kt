package io.github.kdroidfilter.seforimapp.features.sharedstudy

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow

/** Local GATT server used when another computer connects to this computer. */
internal interface BlePeripheralEndpoint {
    val isAvailable: Flow<Boolean>
    val incomingPackets: Flow<BleIncomingPacket>

    suspend fun refreshState()

    suspend fun start(
        serviceUuid: String,
        localName: String,
        writeCharacteristicUuid: String,
        notifyCharacteristicUuid: String,
    )

    suspend fun stop()

    suspend fun send(
        deviceId: String,
        packet: ByteArray,
    )

    suspend fun disconnect(deviceId: String)

    fun maximumPacketSize(deviceId: String): Int
}

/**
 * JNI boundary for the small native GATT server bundled per desktop platform. Keeping this API
 * independent from Blue Falcon lets its central engine be upgraded without changing the protocol.
 */
internal class JniBlePeripheralEndpoint : BlePeripheralEndpoint {
    private val available = MutableStateFlow(false)
    private val packets = MutableSharedFlow<BleIncomingPacket>(extraBufferCapacity = 128)
    private val nativeLoaded = loadNativeLibrary()

    override val isAvailable: Flow<Boolean> = available.asStateFlow()
    override val incomingPackets: Flow<BleIncomingPacket> = packets.asSharedFlow()

    init {
        if (nativeLoaded) refreshNativeState()
    }

    override suspend fun refreshState() {
        if (nativeLoaded) refreshNativeState()
    }

    override suspend fun start(
        serviceUuid: String,
        localName: String,
        writeCharacteristicUuid: String,
        notifyCharacteristicUuid: String,
    ) {
        check(nativeLoaded) { "The desktop BLE peripheral library is unavailable" }
        nativeStart(serviceUuid, localName, writeCharacteristicUuid, notifyCharacteristicUuid)
    }

    override suspend fun stop() {
        if (nativeLoaded) nativeStop()
    }

    override suspend fun send(
        deviceId: String,
        packet: ByteArray,
    ) {
        check(nativeLoaded) { "The desktop BLE peripheral library is unavailable" }
        nativeSend(deviceId, packet)
    }

    override suspend fun disconnect(deviceId: String) {
        if (nativeLoaded) nativeDisconnect(deviceId)
    }

    override fun maximumPacketSize(deviceId: String): Int =
        if (nativeLoaded) nativeMaximumPacketSize(deviceId).coerceAtLeast(DEFAULT_PACKET_SIZE) else DEFAULT_PACKET_SIZE

    @Suppress("unused")
    private fun onBluetoothStateChanged(enabled: Boolean) {
        available.value = enabled
    }

    @Suppress("unused")
    private fun onPacketReceived(
        deviceId: String,
        value: ByteArray,
    ) {
        packets.tryEmit(BleIncomingPacket(deviceId, value.copyOf()))
    }

    private fun refreshNativeState() {
        available.value = runCatching { nativeIsBluetoothEnabled() }.getOrDefault(false)
    }

    private fun loadNativeLibrary(): Boolean =
        runCatching {
            check(BundledNativeLibrary.load(NATIVE_LIBRARY_NAME))
            nativeInitialize()
            true
        }.getOrDefault(false)

    private external fun nativeInitialize()
    private external fun nativeIsBluetoothEnabled(): Boolean
    private external fun nativeStart(
        serviceUuid: String,
        localName: String,
        writeCharacteristicUuid: String,
        notifyCharacteristicUuid: String,
    )
    private external fun nativeStop()
    private external fun nativeSend(deviceId: String, packet: ByteArray)
    private external fun nativeDisconnect(deviceId: String)
    private external fun nativeMaximumPacketSize(deviceId: String): Int

    private companion object {
        const val NATIVE_LIBRARY_NAME = "zayit-ble-peripheral"
        const val DEFAULT_PACKET_SIZE = 20
    }
}
