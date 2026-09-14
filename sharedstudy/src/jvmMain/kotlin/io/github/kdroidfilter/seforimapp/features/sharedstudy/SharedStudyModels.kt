package io.github.kdroidfilter.seforimapp.features.sharedstudy

import androidx.compose.runtime.Immutable
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.util.UUID

/** Versioned wire models kept independent of Compose and the BLE implementation. */
const val SHARED_STUDY_PROTOCOL_VERSION = 2

internal fun newStudyMessageId(): String = UUID.randomUUID().toString()

@Serializable
enum class StudyMode { CHAVRUTA, LESSON, GROUP }

@Serializable
enum class ParticipantRole { HOST, PARTICIPANT }

@Serializable
@Immutable
data class Participant(
    val id: String,
    val displayName: String,
    val colorArgb: Long,
    val role: ParticipantRole,
)

@Serializable
@Immutable
data class StudyLocation(
    val bookId: Long,
    val lineId: Long,
    /** Stable TOC ancestor ids, root first. Used to distinguish the same semantic area. */
    val tocPath: List<Long> = emptyList(),
)

@Serializable
@Immutable
data class SharedStudyNote(
    val id: String,
    val authorId: String,
    val bookId: Long,
    val lineId: Long,
    val startOffset: Int,
    val endOffset: Int,
    val body: String,
    val quote: String = "",
    val updatedAt: Long,
)

@Serializable
sealed interface StudyMessage {
    val senderId: String
    val sequence: Long

    @Serializable
    @SerialName("hello")
    data class Hello(
        override val senderId: String,
        override val sequence: Long,
        val displayName: String,
        val protocolVersion: Int = SHARED_STUDY_PROTOCOL_VERSION,
    ) : StudyMessage

    @Serializable
    @SerialName("invitation")
    data class Invitation(
        override val senderId: String,
        override val sequence: Long,
        val displayName: String,
        val sessionId: String,
        val mode: StudyMode,
        override val messageId: String = newStudyMessageId(),
    ) : ReliableStudyMessage

    @Serializable
    @SerialName("invitation_response")
    data class InvitationResponse(
        override val senderId: String,
        override val sequence: Long,
        val sessionId: String,
        val accepted: Boolean,
        override val messageId: String = newStudyMessageId(),
    ) : ReliableStudyMessage

    @Serializable
    @SerialName("roster")
    data class Roster(
        override val senderId: String,
        override val sequence: Long,
        val sessionId: String,
        val participants: List<Participant>,
        override val messageId: String = newStudyMessageId(),
    ) : ReliableStudyMessage

    @Serializable
    @SerialName("location")
    data class LocationChanged(
        override val senderId: String,
        override val sequence: Long,
        val sessionId: String,
        val location: StudyLocation,
    ) : StudyMessage

    @Serializable
    @SerialName("note")
    data class NoteChanged(
        override val senderId: String,
        override val sequence: Long,
        val sessionId: String,
        val note: SharedStudyNote,
        override val messageId: String = newStudyMessageId(),
    ) : ReliableStudyMessage

    @Serializable
    @SerialName("note_removed")
    data class NoteRemoved(
        override val senderId: String,
        override val sequence: Long,
        val sessionId: String,
        val noteId: String,
        override val messageId: String = newStudyMessageId(),
    ) : ReliableStudyMessage

    @Serializable
    @SerialName("sync_snapshot")
    data class SyncSnapshot(
        override val senderId: String,
        override val sequence: Long,
        val sessionId: String,
        val participants: List<Participant>,
        val locations: Map<String, StudyLocation>,
        val notes: Map<String, SharedStudyNote>,
        override val messageId: String = newStudyMessageId(),
    ) : ReliableStudyMessage

    @Serializable
    @SerialName("ack")
    data class Acknowledgement(
        override val senderId: String,
        override val sequence: Long,
        val messageId: String,
    ) : StudyMessage

    @Serializable
    @SerialName("ping")
    data class Ping(
        override val senderId: String,
        override val sequence: Long,
        val sessionId: String,
        val sentAt: Long,
    ) : StudyMessage

    @Serializable
    @SerialName("pong")
    data class Pong(
        override val senderId: String,
        override val sequence: Long,
        val sessionId: String,
        val pingSequence: Long,
    ) : StudyMessage

    @Serializable
    @SerialName("leave")
    data class Leave(
        override val senderId: String,
        override val sequence: Long,
        val sessionId: String,
        override val messageId: String = newStudyMessageId(),
    ) : ReliableStudyMessage
}

/** Messages whose delivery is acknowledged and retried independently of location sequencing. */
sealed interface ReliableStudyMessage : StudyMessage {
    val messageId: String
}

enum class BluetoothState { UNKNOWN, OFF, ON, UNSUPPORTED }

enum class StudyTransportKind { BLE, LOCAL_NETWORK, BLUETOOTH_CLASSIC }

enum class DiscoveryStage { AUTOMATIC, BLUETOOTH_PAIRING, HOTSPOT_GUIDANCE }

@Immutable
data class NearbyStudyDevice(
    val id: String,
    val displayName: String,
    val signalStrength: Int? = null,
    val transportKind: StudyTransportKind = StudyTransportKind.BLE,
)

@Immutable
data class PendingInvitation(
    val deviceId: String,
    val displayName: String,
    val sessionId: String,
    val mode: StudyMode,
)

@Immutable
data class SharedStudyState(
    val bluetoothState: BluetoothState = BluetoothState.UNKNOWN,
    val discoveryStage: DiscoveryStage = DiscoveryStage.AUTOMATIC,
    val isScanning: Boolean = false,
    val hasStartedDiscovery: Boolean = false,
    val displayName: String = "",
    val nearbyDevices: List<NearbyStudyDevice> = emptyList(),
    val sessionId: String? = null,
    val mode: StudyMode = StudyMode.CHAVRUTA,
    val participants: List<Participant> = emptyList(),
    val locations: Map<String, StudyLocation> = emptyMap(),
    val notes: Map<String, SharedStudyNote> = emptyMap(),
    val pendingInvitation: PendingInvitation? = null,
    val timedOutParticipantName: String? = null,
    val error: String? = null,
) {
    val isConnected: Boolean get() = sessionId != null
}
