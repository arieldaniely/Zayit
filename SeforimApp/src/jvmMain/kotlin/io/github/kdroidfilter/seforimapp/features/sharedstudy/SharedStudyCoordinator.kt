package io.github.kdroidfilter.seforimapp.features.sharedstudy

import io.github.santimattius.structured.annotations.StructuredScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID
import java.util.concurrent.atomic.AtomicLong
import kotlin.time.Duration.Companion.milliseconds

class SharedStudyCoordinator(
    private val transport: SharedStudyTransport,
    initialDisplayName: String,
    private val localId: String = UUID.randomUUID().toString(),
    private val onDisplayNameChanged: (String) -> Unit = {},
    private val now: () -> Long = System::currentTimeMillis,
    private val scope: CoroutineScope =
        CoroutineScope(SupervisorJob() + Dispatchers.Default.limitedParallelism(1)),
) {
    private data class PendingDelivery(
        val deviceId: String,
        val message: ReliableStudyMessage,
        var attempts: Int,
        var lastAttemptAt: Long,
    )

    val localParticipantId: String get() = localId

    private val sequence = AtomicLong()
    private val lastLocationSequenceBySender = mutableMapOf<String, Long>()
    private val seenReliableMessageIds = linkedSetOf<String>()
    private val pendingDeliveries = mutableMapOf<Pair<String, String>, PendingDelivery>()
    private val connectedDeviceIds = linkedSetOf<String>()
    private val deviceIdByParticipantId = mutableMapOf<String, String>()
    private val participantIdByDeviceId = mutableMapOf<String, String>()
    private val lastSeenByParticipantId = mutableMapOf<String, Long>()
    private val locationUpdates = Channel<StudyLocation>(Channel.CONFLATED)
    private val _state = MutableStateFlow(SharedStudyState(displayName = initialDisplayName))
    val state: StateFlow<SharedStudyState> = _state.asStateFlow()

    init {
        launchInScope { transport.bluetoothState.collect { value -> _state.update { it.copy(bluetoothState = value) } } }
        launchInScope { transport.discoveryStage.collect { value -> _state.update { it.copy(discoveryStage = value) } } }
        launchInScope { transport.nearbyDevices.collect { value -> _state.update { it.copy(nearbyDevices = value) } } }
        launchInScope {
            transport.incomingMessages
                .catch { failure -> reportError(failure, SharedStudyError.CONNECTION_LOST) }
                .collect(::receive)
        }
        launchInScope {
            for (location in locationUpdates) {
                _state.value.sessionId?.let { sessionId ->
                    route(StudyMessage.LocationChanged(localId, nextSequence(), sessionId, location))
                }
                delay(LOCATION_UPDATE_INTERVAL)
            }
        }
        launchInScope {
            while (true) {
                delay(RELIABLE_RETRY_INTERVAL_MS)
                retryPendingDeliveries()
            }
        }
        launchInScope {
            while (true) {
                delay(HEARTBEAT_INTERVAL_MS)
                sendHeartbeatAndRemoveExpiredParticipants()
            }
        }
    }

    fun setDisplayName(value: String) {
        val normalized = value.take(48)
        _state.update { it.copy(displayName = normalized) }
        onDisplayNameChanged(normalized)
    }

    fun refreshBluetoothState() {
        launchInScope { transport.refreshBluetoothState() }
    }

    fun startDiscovery() {
        launchInScope {
            _state.update { it.copy(isScanning = true, hasStartedDiscovery = true, error = null) }
            runCatching {
                transport.refreshBluetoothState()
                transport.startDiscovery(_state.value.displayName.trim())
            }.onFailure { failure -> reportError(failure, SharedStudyError.DISCOVERY_UNAVAILABLE) }
            _state.update { it.copy(isScanning = false) }
        }
    }

    fun stopDiscovery() {
        launchInScope {
            runCatching { transport.stopDiscovery() }
            if (_state.value.sessionId == null) runCatching { transport.stopAdvertising() }
            _state.update { it.copy(isScanning = false, hasStartedDiscovery = false) }
        }
    }

    fun openBluetoothSettings(): Boolean = transport.openBluetoothSettings()

    fun openHotspotSettings(): Boolean = transport.openHotspotSettings()

    fun invite(
        device: NearbyStudyDevice,
        mode: StudyMode = StudyMode.CHAVRUTA,
    ) {
        launchInScope {
            val sessionId = _state.value.sessionId ?: UUID.randomUUID().toString()
            ensureHostSession(sessionId, mode)
            runCatching {
                transport.connect(device.id)
                connectedDeviceIds += device.id
                sendReliably(
                    device.id,
                    StudyMessage.Invitation(localId, nextSequence(), _state.value.displayName, sessionId, mode),
                )
            }.onFailure { failure -> reportError(failure, SharedStudyError.CONNECTION_FAILED) }
        }
    }

    fun respondToInvitation(accepted: Boolean) {
        val invitation = _state.value.pendingInvitation ?: return
        launchInScope {
            sendReliably(
                invitation.deviceId,
                StudyMessage.InvitationResponse(localId, nextSequence(), invitation.sessionId, accepted),
            )
            if (accepted) {
                connectedDeviceIds += invitation.deviceId
                participantIdByDeviceId[invitation.deviceId]?.let { lastSeenByParticipantId[it] = now() }
                _state.update {
                    it.copy(
                        sessionId = invitation.sessionId,
                        mode = invitation.mode,
                        pendingInvitation = null,
                        timedOutParticipantName = null,
                    )
                }
            } else {
                forgetDevice(invitation.deviceId)
                transport.disconnect(invitation.deviceId)
                _state.update { it.copy(pendingInvitation = null) }
            }
        }
    }

    fun publishLocation(location: StudyLocation) {
        val current = _state.value
        if (current.sessionId == null) return
        _state.update { it.copy(locations = it.locations + (localId to location)) }
        locationUpdates.trySend(location)
    }

    fun publishNote(note: SharedStudyNote) {
        val sessionId = _state.value.sessionId ?: return
        val normalized = note.copy(authorId = localId)
        _state.update { it.copy(notes = it.notes + (normalized.id to normalized)) }
        route(StudyMessage.NoteChanged(localId, nextSequence(), sessionId, normalized))
    }

    fun sharedNoteId(
        bookId: Long,
        localNoteId: Long,
    ): String = "$localId:$bookId:$localNoteId"

    fun removeNote(noteId: String) {
        val sessionId = _state.value.sessionId ?: return
        _state.update { it.copy(notes = it.notes - noteId) }
        route(StudyMessage.NoteRemoved(localId, nextSequence(), sessionId, noteId))
    }

    fun leave() {
        val sessionId = _state.value.sessionId ?: return
        launchInScope {
            val leave = StudyMessage.Leave(localId, nextSequence(), sessionId)
            connectedDeviceIds.toList().forEach { deviceId -> runCatching { transport.send(deviceId, leave) } }
            disconnectAll()
            clearSessionState()
        }
    }

    private suspend fun receive(incoming: IncomingStudyMessage) {
        val message = incoming.message

        if (message is StudyMessage.Acknowledgement) {
            pendingDeliveries.remove(incoming.deviceId to message.messageId)
            return
        }
        if (message is ReliableStudyMessage) {
            acknowledge(incoming.deviceId, message.messageId)
            if (!rememberReliableMessage(message.messageId)) return
        }
        if (message is StudyMessage.Invitation) {
            handleInvitation(incoming.deviceId, message)
            return
        }
        if (!belongsToCurrentSession(message)) return

        deviceIdByParticipantId[message.senderId] = incoming.deviceId
        participantIdByDeviceId[incoming.deviceId] = message.senderId
        lastSeenByParticipantId[message.senderId] = now()
        if (message is StudyMessage.LocationChanged && !acceptLocationSequence(message)) return

        when (message) {
            is StudyMessage.Invitation -> Unit
            is StudyMessage.InvitationResponse -> handleInvitationResponse(incoming.deviceId, message)
            is StudyMessage.Roster ->
                if (message.sessionId == _state.value.sessionId) {
                    _state.update { it.copy(participants = message.participants) }
                }
            is StudyMessage.SyncSnapshot ->
                if (message.sessionId == _state.value.sessionId) {
                    _state.update {
                        it.copy(
                            participants = message.participants,
                            locations = message.locations,
                            notes = message.notes,
                        )
                    }
                }
            is StudyMessage.LocationChanged ->
                if (message.sessionId == _state.value.sessionId) {
                    _state.update { it.copy(locations = it.locations + (message.senderId to message.location)) }
                    forwardFromHost(message, incoming.deviceId)
                }
            is StudyMessage.NoteChanged ->
                if (message.sessionId == _state.value.sessionId) {
                    if (message.note.authorId != message.senderId) return
                    val existing = _state.value.notes[message.note.id]
                    if (existing != null && existing.authorId != message.senderId) return
                    _state.update { it.copy(notes = it.notes + (message.note.id to message.note)) }
                    forwardFromHost(message, incoming.deviceId)
                }
            is StudyMessage.NoteRemoved ->
                if (message.sessionId == _state.value.sessionId) {
                    val existing = _state.value.notes[message.noteId] ?: return
                    if (existing.authorId != message.senderId) return
                    _state.update { it.copy(notes = it.notes - message.noteId) }
                    forwardFromHost(message, incoming.deviceId)
                }
            is StudyMessage.Ping ->
                if (message.sessionId == _state.value.sessionId) {
                    transport.send(
                        incoming.deviceId,
                        StudyMessage.Pong(localId, nextSequence(), message.sessionId, message.sequence),
                    )
                }
            is StudyMessage.Pong -> Unit
            is StudyMessage.Leave -> removeParticipant(message.senderId, incoming.deviceId)
            is StudyMessage.Hello -> Unit
            is StudyMessage.Acknowledgement -> Unit
        }
    }

    private suspend fun handleInvitation(
        deviceId: String,
        message: StudyMessage.Invitation,
    ) {
        val current = _state.value
        val pending = current.pendingInvitation
        if (
            current.sessionId != null ||
            (pending != null && (pending.deviceId != deviceId || pending.sessionId != message.sessionId))
        ) {
            runCatching {
                transport.send(
                    deviceId,
                    StudyMessage.InvitationResponse(localId, nextSequence(), message.sessionId, accepted = false),
                )
            }
            if (deviceId !in connectedDeviceIds) runCatching { transport.disconnect(deviceId) }
            return
        }

        deviceIdByParticipantId[message.senderId] = deviceId
        participantIdByDeviceId[deviceId] = message.senderId
        lastSeenByParticipantId[message.senderId] = now()
        _state.update {
            it.copy(
                pendingInvitation = PendingInvitation(deviceId, message.displayName, message.sessionId, message.mode),
            )
        }
    }

    private fun belongsToCurrentSession(message: StudyMessage): Boolean {
        val currentSessionId = _state.value.sessionId ?: return message is StudyMessage.Hello
        val messageSessionId =
            when (message) {
                is StudyMessage.InvitationResponse -> message.sessionId
                is StudyMessage.Roster -> message.sessionId
                is StudyMessage.LocationChanged -> message.sessionId
                is StudyMessage.NoteChanged -> message.sessionId
                is StudyMessage.NoteRemoved -> message.sessionId
                is StudyMessage.SyncSnapshot -> message.sessionId
                is StudyMessage.Ping -> message.sessionId
                is StudyMessage.Pong -> message.sessionId
                is StudyMessage.Leave -> message.sessionId
                is StudyMessage.Hello,
                is StudyMessage.Invitation,
                is StudyMessage.Acknowledgement,
                -> return message is StudyMessage.Hello
            }
        return messageSessionId == currentSessionId
    }

    private suspend fun handleInvitationResponse(
        deviceId: String,
        message: StudyMessage.InvitationResponse,
    ) {
        if (message.sessionId != _state.value.sessionId) return
        if (!message.accepted) {
            connectedDeviceIds -= deviceId
            deviceIdByParticipantId -= message.senderId
            participantIdByDeviceId -= deviceId
            pendingDeliveries.keys.removeAll { it.first == deviceId }
            transport.disconnect(deviceId)
            return
        }
        connectedDeviceIds += deviceId
        deviceIdByParticipantId[message.senderId] = deviceId
        participantIdByDeviceId[deviceId] = message.senderId
        lastSeenByParticipantId[message.senderId] = now()
        val existing = _state.value.participants
        if (existing.none { it.id == message.senderId }) {
            val peerName =
                _state.value.nearbyDevices
                    .firstOrNull { it.id == deviceId }
                    ?.displayName ?: message.senderId
            val peer = Participant(message.senderId, peerName, colorFor(existing.size), ParticipantRole.PARTICIPANT)
            _state.update { it.copy(participants = it.participants + peer) }
        }
        broadcastRosterIfHost()
        sendSnapshot(deviceId)
    }

    private fun ensureHostSession(
        sessionId: String,
        mode: StudyMode,
    ) {
        if (_state.value.sessionId != null) return
        val local = Participant(localId, _state.value.displayName, colorFor(0), ParticipantRole.HOST)
        _state.update {
            it.copy(
                sessionId = sessionId,
                mode = mode,
                participants = listOf(local),
                timedOutParticipantName = null,
            )
        }
        launchInScope { transport.advertise(_state.value.displayName) }
    }

    private fun route(message: StudyMessage) {
        launchInScope {
            val destinations =
                if (isHost()) {
                    connectedDeviceIds.toList()
                } else {
                    listOfNotNull(hostDeviceId())
                }
            destinations.forEach { deviceId -> sendMessage(deviceId, message) }
        }
    }

    private suspend fun forwardFromHost(
        message: StudyMessage,
        sourceDeviceId: String,
    ) {
        if (!isHost()) return
        connectedDeviceIds.filterNot { it == sourceDeviceId }.forEach { sendMessage(it, message) }
    }

    private suspend fun broadcastRosterIfHost() {
        if (!isHost()) return
        val sessionId = _state.value.sessionId ?: return
        val message = StudyMessage.Roster(localId, nextSequence(), sessionId, _state.value.participants)
        connectedDeviceIds.forEach { sendReliably(it, message) }
    }

    private suspend fun sendSnapshot(deviceId: String) {
        if (!isHost()) return
        val current = _state.value
        val sessionId = current.sessionId ?: return
        sendReliably(
            deviceId,
            StudyMessage.SyncSnapshot(
                senderId = localId,
                sequence = nextSequence(),
                sessionId = sessionId,
                participants = current.participants,
                locations = current.locations,
                notes = current.notes,
            ),
        )
    }

    private suspend fun sendMessage(
        deviceId: String,
        message: StudyMessage,
    ) {
        if (message is ReliableStudyMessage) sendReliably(deviceId, message) else transport.send(deviceId, message)
    }

    private suspend fun sendReliably(
        deviceId: String,
        message: ReliableStudyMessage,
    ) {
        val delivery = PendingDelivery(deviceId, message, attempts = 1, lastAttemptAt = now())
        pendingDeliveries[deviceId to message.messageId] = delivery
        runCatching { transport.send(deviceId, message) }
            .onFailure { failure -> reportError(failure, SharedStudyError.MESSAGE_SEND_FAILED) }
    }

    private suspend fun acknowledge(
        deviceId: String,
        messageId: String,
    ) {
        runCatching {
            transport.send(deviceId, StudyMessage.Acknowledgement(localId, nextSequence(), messageId))
        }
    }

    private suspend fun retryPendingDeliveries() {
        val retryBefore = now() - RELIABLE_RETRY_INTERVAL_MS
        pendingDeliveries.values.toList().forEach { delivery ->
            if (delivery.lastAttemptAt > retryBefore) return@forEach
            if (delivery.attempts >= MAX_RELIABLE_ATTEMPTS) {
                pendingDeliveries.remove(delivery.deviceId to delivery.message.messageId)
                handleUnresponsiveDevice(delivery.deviceId)
                return@forEach
            }
            delivery.attempts += 1
            delivery.lastAttemptAt = now()
            runCatching { transport.send(delivery.deviceId, delivery.message) }
        }
    }

    private suspend fun sendHeartbeatAndRemoveExpiredParticipants() {
        val sessionId = _state.value.sessionId ?: return
        val currentTime = now()
        connectedDeviceIds.toList().forEach { deviceId ->
            runCatching {
                transport.send(
                    deviceId,
                    StudyMessage.Ping(localId, nextSequence(), sessionId, currentTime),
                )
            }
        }
        val expired =
            lastSeenByParticipantId
                .filter { (participantId, lastSeen) ->
                    participantId != localId && currentTime - lastSeen >= HEARTBEAT_TIMEOUT_MS
                }.keys
                .toList()
        expired.forEach { participantId ->
            val deviceId = deviceIdByParticipantId[participantId]
            if (deviceId != null) removeParticipant(participantId, deviceId, timedOut = true)
        }
    }

    private suspend fun handleUnresponsiveDevice(deviceId: String) {
        participantIdByDeviceId[deviceId]?.let { removeParticipant(it, deviceId, timedOut = true) }
            ?: runCatching { transport.disconnect(deviceId) }
    }

    private suspend fun removeParticipant(
        participantId: String,
        deviceId: String,
        timedOut: Boolean = false,
    ) {
        val participant = _state.value.participants.firstOrNull { it.id == participantId }
        val removedHost = participant?.role == ParticipantRole.HOST
        connectedDeviceIds -= deviceId
        deviceIdByParticipantId -= participantId
        participantIdByDeviceId -= deviceId
        lastSeenByParticipantId -= participantId
        pendingDeliveries.keys.removeAll { it.first == deviceId }
        runCatching { transport.disconnect(deviceId) }

        if (removedHost && !isHost()) {
            disconnectAll()
            clearSessionState(participant.displayName.takeIf { timedOut })
            return
        }
        _state.update {
            it.copy(
                participants = it.participants.filterNot { person -> person.id == participantId },
                locations = it.locations - participantId,
                notes = it.notes.filterValues { note -> note.authorId != participantId },
                timedOutParticipantName = participant?.displayName.takeIf { timedOut },
            )
        }
        broadcastRosterIfHost()
    }

    private suspend fun disconnectAll() {
        connectedDeviceIds.toList().forEach { deviceId -> runCatching { transport.disconnect(deviceId) } }
        connectedDeviceIds.clear()
        deviceIdByParticipantId.clear()
        participantIdByDeviceId.clear()
        lastSeenByParticipantId.clear()
        pendingDeliveries.clear()
    }

    private fun forgetDevice(deviceId: String) {
        connectedDeviceIds -= deviceId
        participantIdByDeviceId.remove(deviceId)?.let { participantId ->
            deviceIdByParticipantId -= participantId
            lastSeenByParticipantId -= participantId
        }
        pendingDeliveries.keys.removeAll { it.first == deviceId }
    }

    private fun reportError(
        failure: Throwable,
        fallback: SharedStudyError,
    ) {
        val error = (failure as? SharedStudyTransportException)?.error ?: fallback
        _state.update { it.copy(error = error) }
    }

    private fun clearSessionState(timedOutParticipantName: String? = null) {
        lastLocationSequenceBySender.clear()
        seenReliableMessageIds.clear()
        _state.update {
            it.copy(
                sessionId = null,
                participants = emptyList(),
                locations = emptyMap(),
                notes = emptyMap(),
                pendingInvitation = null,
                timedOutParticipantName = timedOutParticipantName,
            )
        }
    }

    private fun rememberReliableMessage(messageId: String): Boolean {
        if (!seenReliableMessageIds.add(messageId)) return false
        while (seenReliableMessageIds.size > MAX_SEEN_RELIABLE_MESSAGES) {
            seenReliableMessageIds.remove(seenReliableMessageIds.first())
        }
        return true
    }

    private fun acceptLocationSequence(message: StudyMessage.LocationChanged): Boolean {
        val previous = lastLocationSequenceBySender[message.senderId] ?: Long.MIN_VALUE
        if (message.sequence <= previous) return false
        lastLocationSequenceBySender[message.senderId] = message.sequence
        return true
    }

    private fun isHost(): Boolean =
        _state.value.participants
            .firstOrNull { it.id == localId }
            ?.role == ParticipantRole.HOST

    private fun hostDeviceId(): String? =
        _state.value.participants
            .firstOrNull { it.role == ParticipantRole.HOST }
            ?.id
            ?.let(deviceIdByParticipantId::get)

    private fun nextSequence(): Long = sequence.incrementAndGet()

    private fun launchInScope(block: suspend CoroutineScope.() -> Unit) {
        launchIn(scope, block)
    }

    private fun launchIn(
        @StructuredScope target: CoroutineScope,
        block: suspend CoroutineScope.() -> Unit,
    ) {
        target.launch(block = block)
    }

    private fun colorFor(index: Int): Long = PARTICIPANT_COLORS[index % PARTICIPANT_COLORS.size]

    private companion object {
        val LOCATION_UPDATE_INTERVAL = 80.milliseconds
        const val RELIABLE_RETRY_INTERVAL_MS = 750L
        const val MAX_RELIABLE_ATTEMPTS = 6
        const val MAX_SEEN_RELIABLE_MESSAGES = 2_048
        const val HEARTBEAT_INTERVAL_MS = 5_000L
        const val HEARTBEAT_TIMEOUT_MS = 15_000L
        val PARTICIPANT_COLORS = longArrayOf(0xFF3B82F6, 0xFFEF4444, 0xFF22C55E, 0xFFF59E0B, 0xFFA855F7, 0xFF06B6D4)
    }
}
