package io.github.kdroidfilter.seforimapp.features.sharedstudy

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.ConcurrentHashMap

/** Minimal native BLE surface required from the Windows and macOS adapters. */
interface BlePlatformBridge {
    val bluetoothState: Flow<BluetoothState>
    val advertisements: Flow<List<BleAdvertisement>>
    val incomingPackets: Flow<BleIncomingPacket>

    suspend fun refreshState()

    suspend fun startScanning(serviceUuid: String)

    suspend fun stopScanning()

    suspend fun startAdvertising(
        serviceUuid: String,
        localName: String,
    )

    suspend fun stopAdvertising()

    suspend fun connect(
        deviceId: String,
        serviceUuid: String,
        writeCharacteristicUuid: String,
        notifyCharacteristicUuid: String,
    )

    suspend fun disconnect(deviceId: String)

    /** Suspends until the native stack accepts the packet, providing natural backpressure. */
    suspend fun write(
        deviceId: String,
        packet: ByteArray,
    )

    fun maximumPacketSize(deviceId: String): Int

    fun openSettings(): Boolean
}

data class BleAdvertisement(
    val deviceId: String,
    val displayName: String,
    val rssi: Int? = null,
)

data class BleIncomingPacket(
    val deviceId: String,
    val value: ByteArray,
)

/**
 * Converts the native GATT byte stream into the versioned shared-study protocol. Packet writes are
 * serialized per peer, while different participants can progress independently.
 */
class PacketizedBleTransport(
    private val bridge: BlePlatformBridge,
) : SharedStudyTransport {
    private val reassemblers = ConcurrentHashMap<String, SharedStudyPacketCodec.Reassembler>()
    private val sendMutexes = ConcurrentHashMap<String, Mutex>()

    override val bluetoothState: Flow<BluetoothState> = bridge.bluetoothState
    override val nearbyDevices: Flow<List<NearbyStudyDevice>> =
        bridge.advertisements.map { advertisements ->
            advertisements.map { NearbyStudyDevice(it.deviceId, it.displayName, it.rssi) }
        }
    override val incomingMessages: Flow<IncomingStudyMessage> =
        bridge.incomingPackets.mapNotNull { incoming ->
            reassemblers
                .getOrPut(incoming.deviceId, SharedStudyPacketCodec::Reassembler)
                .accept(incoming.value)
                ?.let { IncomingStudyMessage(incoming.deviceId, it) }
        }

    override suspend fun refreshBluetoothState() = bridge.refreshState()

    override suspend fun startDiscovery(localName: String) {
        val advertising = runCatching { bridge.startAdvertising(SERVICE_UUID, localName) }
        val scanning = runCatching { bridge.startScanning(SERVICE_UUID) }
        if (advertising.isFailure && scanning.isFailure) {
            throw scanning.exceptionOrNull() ?: advertising.exceptionOrNull() ?: error("BLE discovery failed")
        }
    }

    override suspend fun stopDiscovery() = bridge.stopScanning()

    override suspend fun advertise(localName: String) = bridge.startAdvertising(SERVICE_UUID, localName)

    override suspend fun stopAdvertising() = bridge.stopAdvertising()

    override suspend fun connect(deviceId: String) {
        bridge.connect(deviceId, SERVICE_UUID, WRITE_CHARACTERISTIC_UUID, NOTIFY_CHARACTERISTIC_UUID)
    }

    override suspend fun disconnect(deviceId: String) {
        bridge.disconnect(deviceId)
        reassemblers.remove(deviceId)
        sendMutexes.remove(deviceId)
    }

    override suspend fun send(
        deviceId: String,
        message: StudyMessage,
    ) {
        sendMutexes.getOrPut(deviceId, ::Mutex).withLock {
            val packetSize = bridge.maximumPacketSize(deviceId).coerceIn(MIN_PACKET_SIZE, MAX_PACKET_SIZE)
            SharedStudyPacketCodec(packetSize).encode(message).forEach { bridge.write(deviceId, it) }
        }
    }

    override fun openBluetoothSettings(): Boolean = bridge.openSettings()

    companion object {
        const val SERVICE_UUID = "5a617969-7400-4c45-8000-000000000001"
        const val WRITE_CHARACTERISTIC_UUID = "5a617969-7400-4c45-8000-000000000002"
        const val NOTIFY_CHARACTERISTIC_UUID = "5a617969-7400-4c45-8000-000000000003"
        private const val MIN_PACKET_SIZE = 20
        private const val MAX_PACKET_SIZE = 512
    }
}
