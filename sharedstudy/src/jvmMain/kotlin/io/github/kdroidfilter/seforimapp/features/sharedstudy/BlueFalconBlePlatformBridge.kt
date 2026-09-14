package io.github.kdroidfilter.seforimapp.features.sharedstudy

import dev.bluefalcon.core.BlueFalcon
import dev.bluefalcon.core.BluetoothCharacteristic
import dev.bluefalcon.core.BluetoothManagerState
import dev.bluefalcon.core.BluetoothPeripheral
import dev.bluefalcon.core.BluetoothPeripheralState
import dev.bluefalcon.core.ServiceDiscoveryPhase
import dev.bluefalcon.core.ServiceFilter
import dev.bluefalcon.core.toUuid
import dev.bluefalcon.engine.macos.jvm.MacosJvmEngine
import dev.bluefalcon.engine.windows.WindowsEngine
import io.github.santimattius.structured.annotations.StructuredScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import java.util.concurrent.Callable
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.uuid.ExperimentalUuidApi

/**
 * Desktop BLE central implementation backed by Blue Falcon. Peripheral/server operations are
 * delegated because Blue Falcon's JVM engines currently only implement the central role.
 */
@OptIn(ExperimentalUuidApi::class)
internal class BlueFalconBlePlatformBridge(
    private val blueFalcon: BlueFalcon,
    private val peripheralEndpoint: BlePeripheralEndpoint,
    private val engineDispatcher: CoroutineDispatcher? = null,
    @param:StructuredScope private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
) : BlePlatformBridge {
    private data class CentralConnection(
        val peripheral: BluetoothPeripheral,
        val writeCharacteristic: BluetoothCharacteristic,
    )

    private val centralConnections = ConcurrentHashMap<String, CentralConnection>()
    private val _centralPackets = MutableSharedFlow<BleIncomingPacket>(extraBufferCapacity = 128)

    override val bluetoothState: Flow<BluetoothState> =
        combine(blueFalcon.managerState, peripheralEndpoint.isAvailable) { managerState, peripheralAvailable ->
            when {
                managerState == BluetoothManagerState.Ready -> BluetoothState.ON
                peripheralAvailable -> BluetoothState.OFF
                else -> BluetoothState.UNSUPPORTED
            }
        }

    override val advertisements: Flow<List<BleAdvertisement>> =
        blueFalcon.peripherals.map { peripherals ->
            peripherals
                .map { peripheral ->
                    BleAdvertisement(
                        deviceId = peripheral.uuid,
                        displayName = peripheral.name?.takeIf(String::isNotBlank) ?: peripheral.uuid,
                        rssi = peripheral.rssi?.toInt(),
                    )
                }.sortedWith(compareByDescending<BleAdvertisement> { it.rssi ?: Int.MIN_VALUE }.thenBy { it.displayName })
        }

    override val incomingPackets: Flow<BleIncomingPacket> =
        kotlinx.coroutines.flow.merge(_centralPackets.asSharedFlow(), peripheralEndpoint.incomingPackets)

    init {
        scope.launch {
            blueFalcon.engine.characteristicNotifications
                .filter { notification ->
                    notification.characteristic.uuid.toString().equals(
                        PacketizedBleTransport.NOTIFY_CHARACTERISTIC_UUID,
                        ignoreCase = true,
                    )
                }.collect { notification ->
                    _centralPackets.emit(BleIncomingPacket(notification.peripheral.uuid, notification.value))
                }
        }
    }

    override suspend fun refreshState() {
        peripheralEndpoint.refreshState()
    }

    override suspend fun startScanning(serviceUuid: String) {
        onEngineThread {
            blueFalcon.clearPeripherals()
            blueFalcon.scan(listOf(ServiceFilter(serviceUuid.toUuid())))
        }
    }

    override suspend fun stopScanning() = onEngineThread { blueFalcon.stopScanning() }

    override suspend fun startAdvertising(
        serviceUuid: String,
        localName: String,
    ) {
        peripheralEndpoint.start(
            serviceUuid = serviceUuid,
            localName = localName,
            writeCharacteristicUuid = PacketizedBleTransport.WRITE_CHARACTERISTIC_UUID,
            notifyCharacteristicUuid = PacketizedBleTransport.NOTIFY_CHARACTERISTIC_UUID,
        )
    }

    override suspend fun stopAdvertising() = peripheralEndpoint.stop()

    override suspend fun connect(
        deviceId: String,
        serviceUuid: String,
        writeCharacteristicUuid: String,
        notifyCharacteristicUuid: String,
    ) =
        onEngineThread {
            val peripheral =
                blueFalcon.peripherals.value.firstOrNull { it.uuid == deviceId }
                    ?: blueFalcon.retrievePeripheral(deviceId)
                    ?: error("BLE device is no longer available")

            blueFalcon.connect(peripheral)
            if (blueFalcon.connectionState(peripheral) != BluetoothPeripheralState.Connected) {
                withTimeout(CONNECTION_TIMEOUT_MS) {
                    blueFalcon.connectionStateUpdates
                        .filter { it.peripheral.uuid == deviceId && it.state == BluetoothPeripheralState.Connected }
                        .first()
                }
            }

            blueFalcon.discoverServices(peripheral, listOf(serviceUuid.toUuid()))
            if (peripheral.services.none { it.uuid.toString().equals(serviceUuid, ignoreCase = true) }) {
                withTimeout(DISCOVERY_TIMEOUT_MS) {
                    blueFalcon.serviceDiscoveryUpdates
                        .filter {
                            it.peripheral.uuid == deviceId && it.phase == ServiceDiscoveryPhase.ServicesDiscovered
                        }.first()
                }
            }
            val service =
                peripheral.services.firstOrNull { it.uuid.toString().equals(serviceUuid, ignoreCase = true) }
                    ?: error("Shared-study BLE service was not found")

            blueFalcon.discoverCharacteristics(peripheral, service)
            if (service.characteristics.none { it.uuid.toString().equals(writeCharacteristicUuid, ignoreCase = true) }) {
                withTimeout(DISCOVERY_TIMEOUT_MS) {
                    blueFalcon.serviceDiscoveryUpdates
                        .filter {
                            it.peripheral.uuid == deviceId &&
                                it.phase == ServiceDiscoveryPhase.CharacteristicsDiscovered &&
                                it.service?.uuid == service.uuid
                        }.first()
                }
            }

            val writeCharacteristic = service.requiredCharacteristic(writeCharacteristicUuid)
            val notifyCharacteristic = service.requiredCharacteristic(notifyCharacteristicUuid)
            blueFalcon.notifyCharacteristic(peripheral, notifyCharacteristic, true)
            centralConnections[deviceId] = CentralConnection(peripheral, writeCharacteristic)
        }

    override suspend fun disconnect(deviceId: String) {
        onEngineThread {
            centralConnections.remove(deviceId)?.let { blueFalcon.disconnect(it.peripheral) }
        }
        peripheralEndpoint.disconnect(deviceId)
    }

    override suspend fun write(
        deviceId: String,
        packet: ByteArray,
    ) {
        val central = centralConnections[deviceId]
        if (central != null) {
            onEngineThread {
                blueFalcon.writeCharacteristic(
                    peripheral = central.peripheral,
                    characteristic = central.writeCharacteristic,
                    value = packet,
                )
            }
        } else {
            peripheralEndpoint.send(deviceId, packet)
        }
    }

    override fun maximumPacketSize(deviceId: String): Int =
        centralConnections[deviceId]?.peripheral?.mtuSize?.minus(ATT_WRITE_OVERHEAD)
            ?: peripheralEndpoint.maximumPacketSize(deviceId)

    override fun openSettings(): Boolean = PlatformConnectionSettings.openBluetooth()

    private suspend fun <T> onEngineThread(block: suspend () -> T): T =
        engineDispatcher?.let { withContext(it) { block() } } ?: block()

    private fun dev.bluefalcon.core.BluetoothService.requiredCharacteristic(uuid: String): BluetoothCharacteristic =
        characteristics.firstOrNull { it.uuid.toString().equals(uuid, ignoreCase = true) }
            ?: error("Shared-study BLE characteristic $uuid was not found")

    private companion object {
        const val ATT_WRITE_OVERHEAD = 3
        const val CONNECTION_TIMEOUT_MS = 15_000L
        const val DISCOVERY_TIMEOUT_MS = 10_000L
    }
}

/** Creates the Blue Falcon central engine appropriate for the current desktop OS. */
fun createDesktopBlePlatformBridge(): BlePlatformBridge =
    createDesktopBlePlatformBridge(JniBlePeripheralEndpoint())

internal fun createDesktopBlePlatformBridge(
    peripheralEndpoint: BlePeripheralEndpoint = JniBlePeripheralEndpoint(),
): BlePlatformBridge {
    val osName = System.getProperty("os.name").lowercase()
    if (osName.contains("win")) {
        val executor =
            Executors.newSingleThreadExecutor { runnable ->
                Thread(runnable, "Zayit-BlueFalcon-Windows").apply { isDaemon = true }
            }
        val dispatcher = executor.asCoroutineDispatcher()
        return runCatching {
            // C++/WinRT apartment initialization is thread-local. Construct the engine and retain
            // this same thread for every native BlueFalcon call; moving scan/connect to another
            // coroutine worker lets an uncaught C++ exception cross JNI and terminate the JVM.
            executor.submit(
                Callable<BlePlatformBridge> {
                    BlueFalconBlePlatformBridge(
                        blueFalcon = BlueFalcon(WindowsEngine()),
                        peripheralEndpoint = peripheralEndpoint,
                        engineDispatcher = dispatcher,
                    )
                },
            ).get(ENGINE_INITIALIZATION_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        }.getOrElse {
            dispatcher.close()
            UnsupportedBlePlatformBridge()
        }
    }
    if (osName.contains("mac")) {
        return runCatching {
            BlueFalconBlePlatformBridge(BlueFalcon(MacosJvmEngine()), peripheralEndpoint)
        }.getOrElse { UnsupportedBlePlatformBridge() }
    }
    return UnsupportedBlePlatformBridge()
}

private const val ENGINE_INITIALIZATION_TIMEOUT_SECONDS = 5L

internal class UnsupportedBlePlatformBridge : BlePlatformBridge {
    override val bluetoothState = MutableStateFlow(BluetoothState.UNSUPPORTED).asStateFlow()
    override val advertisements = MutableStateFlow(emptyList<BleAdvertisement>()).asStateFlow()
    override val incomingPackets = MutableSharedFlow<BleIncomingPacket>().asSharedFlow()

    override suspend fun refreshState() = Unit
    override suspend fun startScanning(serviceUuid: String) = Unit
    override suspend fun stopScanning() = Unit
    override suspend fun startAdvertising(serviceUuid: String, localName: String) = Unit
    override suspend fun stopAdvertising() = Unit
    override suspend fun connect(
        deviceId: String,
        serviceUuid: String,
        writeCharacteristicUuid: String,
        notifyCharacteristicUuid: String,
    ) = Unit
    override suspend fun disconnect(deviceId: String) = Unit
    override suspend fun write(deviceId: String, packet: ByteArray) = Unit
    override fun maximumPacketSize(deviceId: String): Int = 20
    override fun openSettings(): Boolean = PlatformConnectionSettings.openBluetooth()
}
