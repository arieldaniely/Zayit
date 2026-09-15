package io.github.kdroidfilter.seforimapp.features.sharedstudy

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class SharedStudyCoordinatorTest {
    @Test
    fun `construction does not activate discovery until the user requests it`() =
        runTest {
            val transport = FakeTransport()
            val coordinator = coordinator(transport)
            runCurrent()

            assertEquals(0, transport.refreshCount)
            assertEquals(0, transport.discoveryCount)

            coordinator.startDiscovery()
            runCurrent()

            assertEquals(1, transport.refreshCount)
            assertEquals(1, transport.discoveryCount)
        }

    @Test
    fun `host assigns distinct colors and broadcasts roster after approval`() =
        runTest {
            val transport = FakeTransport()
            val coordinator = coordinator(transport)
            transport.devices.value = listOf(NearbyStudyDevice("peer", "ראובן"))
            coordinator.invite(transport.devices.value.single())
            runCurrent()
            val invitation = transport.sent.single().second as StudyMessage.Invitation

            transport.receive("ble-peer", StudyMessage.InvitationResponse("peer", 1, invitation.sessionId, accepted = true))
            runCurrent()

            val participants = coordinator.state.value.participants
            assertEquals(2, participants.size)
            assertNotEquals(participants[0].colorArgb, participants[1].colorArgb)
            assertTrue(transport.sent.any { it.first == "ble-peer" && it.second is StudyMessage.Roster })
            assertTrue(transport.sent.any { it.first == "ble-peer" && it.second is StudyMessage.SyncSnapshot })
        }

    @Test
    fun `host forwards participant location only to other peers`() =
        runTest {
            val transport = FakeTransport()
            val coordinator = coordinator(transport)
            val peers = listOf(NearbyStudyDevice("a", "א"), NearbyStudyDevice("b", "ב"))
            transport.devices.value = peers
            peers.forEach { coordinator.invite(it) }
            runCurrent()
            val sessionId = coordinator.state.value.sessionId!!
            transport.receive("a", StudyMessage.InvitationResponse("a", 1, sessionId, true))
            transport.receive("b", StudyMessage.InvitationResponse("b", 1, sessionId, true))
            runCurrent()
            transport.sent.clear()

            transport.receive("a", StudyMessage.LocationChanged("a", 2, sessionId, StudyLocation(9, 42)))
            runCurrent()

            assertEquals(StudyLocation(9, 42), coordinator.state.value.locations["a"])
            assertEquals(listOf("b"), transport.sent.map { it.first })
        }

    @Test
    fun `duplicate reliable packets are acknowledged but applied once`() =
        runTest {
            val transport = FakeTransport()
            val coordinator = coordinator(transport)
            runCurrent()
            val invitation =
                StudyMessage.Invitation("host", 2, "מארח", "session", StudyMode.CHAVRUTA, messageId = "invite-1")
            transport.receive("host-device", invitation)
            transport.receive("host-device", invitation)
            runCurrent()

            assertEquals(
                "session",
                coordinator.state.value.pendingInvitation
                    ?.sessionId,
            )
            assertEquals(
                2,
                transport.sent.count {
                    (it.second as? StudyMessage.Acknowledgement)?.messageId == invitation.messageId
                },
            )
        }

    @Test
    fun `invitation is rejected while another session is active`() =
        runTest {
            val transport = FakeTransport()
            val coordinator = coordinator(transport)
            transport.devices.value = listOf(NearbyStudyDevice("peer-device", "ראובן"))
            coordinator.invite(transport.devices.value.single())
            runCurrent()
            val activeSessionId = coordinator.state.value.sessionId!!

            transport.receive(
                "other-device",
                StudyMessage.Invitation("other", 1, "שמעון", "other-session", StudyMode.CHAVRUTA),
            )
            runCurrent()

            assertEquals(activeSessionId, coordinator.state.value.sessionId)
            assertEquals(null, coordinator.state.value.pendingInvitation)
            assertTrue(
                transport.sent.any { (deviceId, message) ->
                    deviceId == "other-device" &&
                        message is StudyMessage.InvitationResponse &&
                        !message.accepted &&
                        message.sessionId == "other-session"
                },
            )
            assertTrue("other-device" in transport.disconnected)
        }

    @Test
    fun `messages from another session cannot mutate or keep participants alive`() =
        runTest {
            var clock = 0L
            val transport = FakeTransport()
            val coordinator = coordinator(transport, now = { clock })
            transport.devices.value = listOf(NearbyStudyDevice("peer-device", "ראובן"))
            coordinator.invite(transport.devices.value.single())
            runCurrent()
            val sessionId = coordinator.state.value.sessionId!!
            transport.receive("peer-device", StudyMessage.InvitationResponse("peer", 1, sessionId, true))
            runCurrent()

            clock = 14_000L
            transport.receive(
                "peer-device",
                StudyMessage.LocationChanged("peer", 999, "stale-session", StudyLocation(9, 99)),
            )
            runCurrent()
            assertEquals(null, coordinator.state.value.locations["peer"])

            clock = 16_000L
            advanceTimeBy(5_000L)
            runCurrent()
            assertTrue(coordinator.state.value.participants.none { it.id == "peer" })
        }

    @Test
    fun `participant cannot overwrite a note owned by someone else`() =
        runTest {
            val transport = FakeTransport()
            val coordinator = coordinator(transport)
            transport.devices.value = listOf(NearbyStudyDevice("peer-device", "ראובן"))
            coordinator.invite(transport.devices.value.single())
            runCurrent()
            val sessionId = coordinator.state.value.sessionId!!
            transport.receive("peer-device", StudyMessage.InvitationResponse("peer", 1, sessionId, true))
            runCurrent()
            coordinator.publishNote(SharedStudyNote("note", "local", 1, 1, 0, 1, "local", updatedAt = 1))
            runCurrent()

            val forged = SharedStudyNote("note", "peer", 1, 1, 0, 1, "forged", updatedAt = 2)
            transport.receive("peer-device", StudyMessage.NoteChanged("peer", 2, sessionId, forged))
            runCurrent()

            assertEquals("local", coordinator.state.value.notes["note"]?.body)
        }

    @Test
    fun `critical note arriving after a newer location is retained`() =
        runTest {
            val transport = FakeTransport()
            val coordinator = coordinator(transport)
            transport.devices.value = listOf(NearbyStudyDevice("peer-device", "ראובן"))
            coordinator.invite(transport.devices.value.single())
            runCurrent()
            val sessionId = coordinator.state.value.sessionId!!
            transport.receive("peer-device", StudyMessage.InvitationResponse("peer", 1, sessionId, true))
            runCurrent()

            transport.receive("peer-device", StudyMessage.LocationChanged("peer", 5, sessionId, StudyLocation(9, 50)))
            val note = SharedStudyNote("late-note", "peer", 9, 42, 0, 3, "חשוב", updatedAt = 1)
            transport.receive("peer-device", StudyMessage.NoteChanged("peer", 4, sessionId, note))
            runCurrent()

            assertEquals(note, coordinator.state.value.notes["late-note"])
            assertEquals(StudyLocation(9, 50), coordinator.state.value.locations["peer"])
        }

    @Test
    fun `critical delivery is retried until acknowledged`() =
        runTest {
            val transport = FakeTransport()
            val coordinator = coordinator(transport)
            transport.devices.value = listOf(NearbyStudyDevice("peer-device", "ראובן"))
            coordinator.invite(transport.devices.value.single())
            runCurrent()
            val invitation =
                transport.sent
                    .map { it.second }
                    .filterIsInstance<StudyMessage.Invitation>()
                    .single()

            advanceTimeBy(750L)
            runCurrent()
            assertEquals(
                2,
                transport.sent.count { it.second is StudyMessage.Invitation },
            )

            transport.receive(
                "peer-device",
                StudyMessage.Acknowledgement("peer", 1, invitation.messageId),
            )
            runCurrent()
            advanceTimeBy(750L)
            runCurrent()

            assertEquals(
                2,
                transport.sent.count { it.second is StudyMessage.Invitation },
            )
        }

    @Test
    fun `snapshot restores notes and locations that predate joining`() =
        runTest {
            val transport = FakeTransport()
            val coordinator = coordinator(transport)
            runCurrent()
            transport.receive("host-device", StudyMessage.Invitation("host", 1, "מארח", "session", StudyMode.CHAVRUTA))
            runCurrent()
            coordinator.respondToInvitation(true)
            runCurrent()
            val note = SharedStudyNote("n1", "host", 7, 70, 0, 2, "הערה", updatedAt = 1)
            val participants =
                listOf(
                    Participant("host", "מארח", 1, ParticipantRole.HOST),
                    Participant("local", "לוי", 2, ParticipantRole.PARTICIPANT),
                )
            transport.receive(
                "host-device",
                StudyMessage.SyncSnapshot(
                    "host",
                    2,
                    "session",
                    participants,
                    mapOf("host" to StudyLocation(7, 70)),
                    mapOf(note.id to note),
                ),
            )
            runCurrent()

            assertEquals(note, coordinator.state.value.notes[note.id])
            assertEquals(StudyLocation(7, 70), coordinator.state.value.locations["host"])
            assertEquals(participants, coordinator.state.value.participants)
        }

    @Test
    fun `participant is removed after heartbeat timeout`() =
        runTest {
            var clock = 0L
            val transport = FakeTransport()
            val coordinator = coordinator(transport, now = { clock })
            transport.devices.value = listOf(NearbyStudyDevice("peer-device", "ראובן"))
            coordinator.invite(transport.devices.value.single())
            runCurrent()
            val sessionId = coordinator.state.value.sessionId!!
            transport.receive("peer-device", StudyMessage.InvitationResponse("peer", 1, sessionId, true))
            runCurrent()
            transport.sent
                .mapNotNull { (deviceId, message) ->
                    (message as? ReliableStudyMessage)?.let { deviceId to it.messageId }
                }.forEach { (deviceId, messageId) ->
                    transport.receive(deviceId, StudyMessage.Acknowledgement("peer", 100, messageId))
                }
            runCurrent()

            clock = 16_000L
            advanceTimeBy(5_000L)
            runCurrent()

            assertTrue(
                coordinator.state.value.participants
                    .none { it.id == "peer" },
            )
            assertEquals("ראובן", coordinator.state.value.timedOutParticipantName)
        }

    private fun TestScope.coordinator(
        transport: FakeTransport,
        now: () -> Long = { testScheduler.currentTime },
    ) = SharedStudyCoordinator(
        transport = transport,
        initialDisplayName = "לוי",
        localId = "local",
        now = now,
        scope = backgroundScope,
    )

    private class FakeTransport : SharedStudyTransport {
        val state = MutableStateFlow(BluetoothState.ON)
        val devices = MutableStateFlow<List<NearbyStudyDevice>>(emptyList())
        val incoming = MutableSharedFlow<IncomingStudyMessage>(extraBufferCapacity = 8)
        val sent = mutableListOf<Pair<String, StudyMessage>>()
        var refreshCount = 0
        var discoveryCount = 0
        val disconnected = mutableListOf<String>()

        override val bluetoothState = state
        override val nearbyDevices = devices
        override val incomingMessages = incoming

        suspend fun receive(
            deviceId: String,
            message: StudyMessage,
        ) {
            incoming.emit(IncomingStudyMessage(deviceId, message))
        }

        override suspend fun refreshBluetoothState() {
            refreshCount += 1
        }

        override suspend fun startDiscovery(localName: String) {
            discoveryCount += 1
        }

        override suspend fun stopDiscovery() = Unit

        override suspend fun advertise(localName: String) = Unit

        override suspend fun stopAdvertising() = Unit

        override suspend fun connect(deviceId: String) = Unit

        override suspend fun disconnect(deviceId: String) {
            disconnected += deviceId
        }

        override suspend fun send(
            deviceId: String,
            message: StudyMessage,
        ) {
            sent += deviceId to message
        }

        override fun openBluetoothSettings() = true
    }
}
