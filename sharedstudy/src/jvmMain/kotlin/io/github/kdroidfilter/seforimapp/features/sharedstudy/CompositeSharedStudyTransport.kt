package io.github.kdroidfilter.seforimapp.features.sharedstudy

import io.github.santimattius.structured.annotations.StructuredScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.launch
import kotlinx.coroutines.supervisorScope

/** Runs zero-touch transports in parallel and only then exposes the guided fallback layers. */
class CompositeSharedStudyTransport(
    ble: SharedStudyTransport,
    localNetwork: SharedStudyTransport,
    bluetoothClassic: SharedStudyTransport,
    @param:StructuredScope private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
) : SharedStudyTransport {
    private data class Entry(
        val prefix: String,
        val kind: StudyTransportKind,
        val tier: Int,
        val transport: SharedStudyTransport,
    )

    private val entries =
        listOf(
            Entry("ble", StudyTransportKind.BLE, AUTOMATIC_TIER, ble),
            Entry("lan", StudyTransportKind.LOCAL_NETWORK, AUTOMATIC_TIER, localNetwork),
            Entry("classic", StudyTransportKind.BLUETOOTH_CLASSIC, CLASSIC_TIER, bluetoothClassic),
        )
    private val _stage = MutableStateFlow(DiscoveryStage.AUTOMATIC)
    private var fallbackJob: Job? = null

    @Volatile
    private var automaticPeerAvailable = false

    override val bluetoothState: Flow<BluetoothState> = ble.bluetoothState
    override val discoveryStage = _stage.asStateFlow()
    override val nearbyDevices: Flow<List<NearbyStudyDevice>> =
        combine(entries.map { it.transport.nearbyDevices }) { deviceLists ->
            deviceLists
                .flatMapIndexed { index, devices ->
                    val entry = entries[index]
                    devices.map { device ->
                        device.copy(
                            id = encodeDeviceId(entry, device.id),
                            transportKind = entry.kind,
                        )
                    }
                }.distinctBy { it.id }
                .sortedWith(compareBy<NearbyStudyDevice> { it.transportKind.ordinal }.thenBy { it.displayName })
        }
    override val incomingMessages: Flow<IncomingStudyMessage> =
        merge(
            *entries
                .map { entry ->
                    entry.transport.incomingMessages.map { incoming ->
                        incoming.copy(deviceId = encodeDeviceId(entry, incoming.deviceId))
                    }
                }.toTypedArray(),
        )

    init {
        scope.launch {
            combine(
                entries.filter { it.tier == AUTOMATIC_TIER }.map { it.transport.nearbyDevices },
            ) { lists -> lists.any { it.isNotEmpty() } }
                .collect { automaticPeerAvailable = it }
        }
    }

    override suspend fun refreshBluetoothState() {
        runFor(entries) { refreshBluetoothState() }
    }

    override suspend fun startDiscovery(localName: String) {
        fallbackJob?.cancelAndJoin()
        _stage.value = DiscoveryStage.AUTOMATIC
        runFor(entries.filter { it.tier == AUTOMATIC_TIER }) { startDiscovery(localName) }
        fallbackJob =
            scope.launch {
                delay(AUTOMATIC_DISCOVERY_WINDOW_MS)
                if (automaticPeerAvailable) return@launch
                _stage.value = DiscoveryStage.BLUETOOTH_PAIRING
                runCatching {
                    runFor(entries.filter { it.tier == CLASSIC_TIER }) { startDiscovery(localName) }
                }
                delay(CLASSIC_DISCOVERY_WINDOW_MS)
                if (automaticPeerAvailable) {
                    _stage.value = DiscoveryStage.AUTOMATIC
                    return@launch
                }
                _stage.value = DiscoveryStage.HOTSPOT_GUIDANCE
            }
    }

    override suspend fun stopDiscovery() {
        fallbackJob?.cancelAndJoin()
        fallbackJob = null
        runFor(entries) { stopDiscovery() }
    }

    override suspend fun advertise(localName: String) {
        runFor(entries) { advertise(localName) }
    }

    override suspend fun stopAdvertising() {
        fallbackJob?.cancelAndJoin()
        fallbackJob = null
        runFor(entries) { stopAdvertising() }
        _stage.value = DiscoveryStage.AUTOMATIC
    }

    override suspend fun connect(deviceId: String) {
        val (entry, nativeId) = decodeDeviceId(deviceId)
        entry.transport.connect(nativeId)
    }

    override suspend fun disconnect(deviceId: String) {
        val (entry, nativeId) = decodeDeviceId(deviceId)
        entry.transport.disconnect(nativeId)
    }

    override suspend fun send(
        deviceId: String,
        message: StudyMessage,
    ) {
        val (entry, nativeId) = decodeDeviceId(deviceId)
        entry.transport.send(nativeId, message)
    }

    override fun openBluetoothSettings(): Boolean =
        entries.firstOrNull { it.kind == StudyTransportKind.BLUETOOTH_CLASSIC }?.transport?.openBluetoothSettings()
            ?: PlatformConnectionSettings.openBluetooth()

    override fun openHotspotSettings(): Boolean = PlatformConnectionSettings.openHotspot()

    private suspend fun runFor(
        selected: List<Entry>,
        operation: suspend SharedStudyTransport.() -> Unit,
    ) {
        if (selected.isEmpty()) return
        val results =
            supervisorScope {
                selected
                    .map { entry ->
                        async {
                            try {
                                entry.transport.operation()
                                Result.success(Unit)
                            } catch (cancellation: CancellationException) {
                                throw cancellation
                            } catch (failure: Throwable) {
                                Result.failure(failure)
                            }
                        }
                    }.awaitAll()
            }
        val failures = results.mapNotNull { it.exceptionOrNull() }
        if (failures.size == selected.size) {
            val primary = failures.first()
            failures.drop(1).forEach(primary::addSuppressed)
            throw primary
        }
    }

    private fun encodeDeviceId(
        entry: Entry,
        nativeId: String,
    ): String = "${entry.prefix}$SEPARATOR$nativeId"

    private fun decodeDeviceId(deviceId: String): Pair<Entry, String> {
        val prefix = deviceId.substringBefore(SEPARATOR)
        val nativeId = deviceId.substringAfter(SEPARATOR, missingDelimiterValue = "")
        require(nativeId.isNotBlank()) { "Invalid shared-study device id" }
        val entry = entries.firstOrNull { it.prefix == prefix } ?: error("Unknown shared-study transport")
        return entry to nativeId
    }

    private companion object {
        const val SEPARATOR = '|'
        const val AUTOMATIC_TIER = 0
        const val CLASSIC_TIER = 1
        const val AUTOMATIC_DISCOVERY_WINDOW_MS = 6_000L
        const val CLASSIC_DISCOVERY_WINDOW_MS = 8_000L
    }
}
