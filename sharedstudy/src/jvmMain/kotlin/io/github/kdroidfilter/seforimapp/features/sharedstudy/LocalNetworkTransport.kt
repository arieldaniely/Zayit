package io.github.kdroidfilter.seforimapp.features.sharedstudy

import io.github.santimattius.structured.annotations.StructuredScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.DataInputStream
import java.io.DataOutputStream
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.NetworkInterface
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketTimeoutException
import java.util.Base64
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Zero-configuration LAN transport. Peers announce themselves by UDP broadcast and exchange
 * length-prefixed protocol messages over a persistent TCP connection. It works equally on an
 * existing local network and on a phone/computer hotspot; no internet service is involved.
 */
class LocalNetworkTransport(
    private val instanceId: String = UUID.randomUUID().toString(),
    private val discoveryPort: Int = DEFAULT_DISCOVERY_PORT,
    private val dataPort: Int = DEFAULT_DATA_PORT,
    @param:StructuredScope private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
) : SharedStudyTransport {
    private data class DiscoveredPeer(
        val endpoint: InetSocketAddress,
        val displayName: String,
        val lastSeenAt: Long,
    )

    private class PeerConnection(
        val socket: Socket,
        val input: DataInputStream,
        val output: DataOutputStream,
        val sendMutex: Mutex = Mutex(),
    )

    private val started = AtomicBoolean(false)
    private val peers = ConcurrentHashMap<String, DiscoveredPeer>()
    private val connections = ConcurrentHashMap<String, PeerConnection>()
    private val _devices = MutableStateFlow<List<NearbyStudyDevice>>(emptyList())
    private val _incoming = MutableSharedFlow<IncomingStudyMessage>(extraBufferCapacity = 256)
    private val jobs = mutableListOf<Job>()

    @Volatile
    private var advertisedName: String = "זית"
    private var discoverySocket: DatagramSocket? = null
    private var serverSocket: ServerSocket? = null

    override val bluetoothState = MutableStateFlow(BluetoothState.UNSUPPORTED).asStateFlow()
    override val nearbyDevices = _devices.asStateFlow()
    override val incomingMessages = _incoming.asSharedFlow()

    override suspend fun refreshBluetoothState() = Unit

    override suspend fun startDiscovery(localName: String) {
        advertisedName = localName.ifBlank { "זית" }.take(MAX_NAME_LENGTH)
        if (started.compareAndSet(false, true)) {
            runCatching { startServer() }
                .onFailure {
                    started.set(false)
                    throw it
                }
        }
        if (discoverySocket == null) startDiscoverySocket()
    }

    override suspend fun stopDiscovery() {
        discoverySocket?.close()
        discoverySocket = null
    }

    override suspend fun advertise(localName: String) = startDiscovery(localName)

    override suspend fun stopAdvertising() {
        if (!started.compareAndSet(true, false)) return
        discoverySocket?.close()
        serverSocket?.close()
        discoverySocket = null
        serverSocket = null
        val activeJobs =
            synchronized(jobs) {
                jobs.toList().also { jobs.clear() }
            }
        activeJobs.forEach { it.cancelAndJoin() }
        connections.keys.toList().forEach { disconnect(it) }
        peers.clear()
        _devices.value = emptyList()
    }

    override suspend fun connect(deviceId: String) {
        if (connections[deviceId]?.socket?.isConnected == true) return
        val peer =
            peers[deviceId]
                ?: throw SharedStudyTransportException(SharedStudyError.DEVICE_UNAVAILABLE)
        val socket = Socket()
        socket.connect(peer.endpoint, CONNECT_TIMEOUT_MS)
        installConnection(socket, expectedDeviceId = deviceId)
    }

    override suspend fun disconnect(deviceId: String) {
        connections.remove(deviceId)?.socket?.close()
    }

    override suspend fun send(
        deviceId: String,
        message: StudyMessage,
    ) {
        val connection =
            connections[deviceId]
                ?: throw SharedStudyTransportException(SharedStudyError.CONNECTION_LOST)
        val payload = SharedStudyWireCodec.encode(message)
        require(payload.size <= MAX_FRAME_SIZE) { "Shared-study message is too large" }
        connection.sendMutex.withLock {
            connection.output.writeInt(payload.size)
            connection.output.write(payload)
            connection.output.flush()
        }
    }

    override fun openBluetoothSettings(): Boolean = PlatformConnectionSettings.openBluetooth()

    private fun startServer() {
        val server = ServerSocket()
        server.reuseAddress = true
        server.bind(InetSocketAddress(dataPort))
        serverSocket = server
        addJob {
            while (isActive && !server.isClosed) {
                val socket = runCatching { server.accept() }.getOrNull() ?: break
                launch { runCatching { installConnection(socket) }.onFailure { socket.close() } }
            }
        }
    }

    private fun startDiscoverySocket() {
        val socket = DatagramSocket(null)
        socket.reuseAddress = true
        socket.broadcast = true
        socket.soTimeout = RECEIVE_TIMEOUT_MS
        socket.bind(InetSocketAddress(discoveryPort))
        discoverySocket = socket

        addJob {
            val buffer = ByteArray(MAX_DISCOVERY_PACKET_SIZE)
            while (isActive && !socket.isClosed) {
                val packet = DatagramPacket(buffer, buffer.size)
                try {
                    socket.receive(packet)
                    acceptAnnouncement(packet)
                } catch (_: SocketTimeoutException) {
                    removeExpiredPeers()
                } catch (_: Exception) {
                    if (!socket.isClosed) delay(250)
                }
            }
        }
        addJob {
            while (isActive && !socket.isClosed) {
                broadcastAnnouncement(socket)
                removeExpiredPeers()
                delay(ANNOUNCEMENT_INTERVAL_MS)
            }
        }
    }

    private suspend fun installConnection(
        socket: Socket,
        expectedDeviceId: String? = null,
    ) {
        socket.tcpNoDelay = true
        socket.keepAlive = true
        socket.soTimeout = HANDSHAKE_TIMEOUT_MS
        val input = DataInputStream(socket.getInputStream().buffered())
        val output = DataOutputStream(socket.getOutputStream().buffered())
        output.writeUTF(instanceId)
        output.flush()
        val remoteInstanceId = input.readUTF().take(MAX_INSTANCE_ID_LENGTH)
        require(remoteInstanceId.isNotBlank() && remoteInstanceId != instanceId) { "Invalid LAN peer identity" }
        val deviceId = "lan:$remoteInstanceId"
        require(expectedDeviceId == null || expectedDeviceId == deviceId) { "LAN peer identity changed" }
        socket.soTimeout = 0
        val connection = PeerConnection(socket, input, output)
        connections.put(deviceId, connection)?.socket?.close()
        addJob { readFrames(deviceId, connection) }
    }

    private suspend fun readFrames(
        deviceId: String,
        connection: PeerConnection,
    ) {
        try {
            while (scope.isActive && !connection.socket.isClosed) {
                val size = connection.input.readInt()
                require(size in 1..MAX_FRAME_SIZE) { "Invalid shared-study frame size: $size" }
                val payload = ByteArray(size)
                connection.input.readFully(payload)
                SharedStudyWireCodec.decode(payload)?.let { _incoming.emit(IncomingStudyMessage(deviceId, it)) }
            }
        } finally {
            connections.remove(deviceId, connection)
            connection.socket.close()
        }
    }

    private fun acceptAnnouncement(packet: DatagramPacket) {
        val parts = packet.data.decodeToString(packet.offset, packet.offset + packet.length).split('|', limit = 5)
        if (parts.size != 5 || parts[0] != DISCOVERY_MAGIC || parts[1] != SHARED_STUDY_PROTOCOL_VERSION.toString()) return
        val remoteId = parts[2].take(MAX_INSTANCE_ID_LENGTH)
        if (remoteId.isBlank() || remoteId == instanceId) return
        val port = parts[3].toIntOrNull()?.takeIf { it in 1..65535 } ?: return
        val name = runCatching { Base64.getUrlDecoder().decode(parts[4]).decodeToString() }.getOrNull() ?: return
        peers["lan:$remoteId"] =
            DiscoveredPeer(
                endpoint = InetSocketAddress(packet.address, port),
                displayName = name.ifBlank { packet.address.hostAddress }.take(MAX_NAME_LENGTH),
                lastSeenAt = System.currentTimeMillis(),
            )
        publishDevices()
    }

    private fun broadcastAnnouncement(socket: DatagramSocket) {
        val encodedName = Base64.getUrlEncoder().withoutPadding().encodeToString(advertisedName.encodeToByteArray())
        val bytes = "$DISCOVERY_MAGIC|$SHARED_STUDY_PROTOCOL_VERSION|$instanceId|$dataPort|$encodedName".encodeToByteArray()
        broadcastAddresses().forEach { address ->
            runCatching { socket.send(DatagramPacket(bytes, bytes.size, address, discoveryPort)) }
        }
    }

    private fun broadcastAddresses(): Set<InetAddress> =
        buildSet {
            add(InetAddress.getByName("255.255.255.255"))
            runCatching {
                NetworkInterface.getNetworkInterfaces().toList().forEach { network ->
                    if (network.isUp && !network.isLoopback) {
                        network.interfaceAddresses.mapNotNullTo(this) { it.broadcast }
                    }
                }
            }
        }

    private fun removeExpiredPeers() {
        val cutoff = System.currentTimeMillis() - PEER_EXPIRY_MS
        peers.entries.removeIf { it.value.lastSeenAt < cutoff }
        publishDevices()
    }

    private fun publishDevices() {
        _devices.value =
            peers.entries
                .map { (id, peer) ->
                    NearbyStudyDevice(id, peer.displayName, transportKind = StudyTransportKind.LOCAL_NETWORK)
                }.sortedBy { it.displayName }
    }

    private fun addJob(block: suspend CoroutineScope.() -> Unit) {
        val job = scope.launch(block = block)
        synchronized(jobs) { jobs += job }
    }

    companion object {
        const val DEFAULT_DISCOVERY_PORT = 42424
        const val DEFAULT_DATA_PORT = 42425
        private const val DISCOVERY_MAGIC = "ZAYIT-STUDY"
        private const val MAX_NAME_LENGTH = 48
        private const val MAX_INSTANCE_ID_LENGTH = 128
        private const val MAX_DISCOVERY_PACKET_SIZE = 1024
        private const val MAX_FRAME_SIZE = 4 * 1024 * 1024
        private const val CONNECT_TIMEOUT_MS = 8_000
        private const val HANDSHAKE_TIMEOUT_MS = 8_000
        private const val RECEIVE_TIMEOUT_MS = 1_000
        private const val ANNOUNCEMENT_INTERVAL_MS = 2_000L
        private const val PEER_EXPIRY_MS = 8_000L
    }
}
