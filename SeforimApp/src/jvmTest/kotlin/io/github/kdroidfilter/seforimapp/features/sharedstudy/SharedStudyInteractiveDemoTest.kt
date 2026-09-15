package io.github.kdroidfilter.seforimapp.features.sharedstudy

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowState
import androidx.compose.ui.window.application
import io.github.santimattius.structured.annotations.StructuredScope
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.intui.standalone.theme.IntUiTheme
import org.jetbrains.jewel.ui.component.DefaultButton
import org.jetbrains.jewel.ui.component.OutlinedButton
import org.jetbrains.jewel.ui.component.Text
import java.util.concurrent.atomic.AtomicLong
import kotlin.test.Test

/**
 * An opt-in, fully local playground for the shared-study UI.
 *
 * It is intentionally kept in jvmTest: no demo behavior or fake participant can reach the real
 * application. Run only this test with `SHARED_STUDY_DEMO=1`; closing the window completes it.
 */
class SharedStudyInteractiveDemoTest {
    @Test
    fun interactiveSharedStudyPlayground() {
        if (System.getenv("SHARED_STUDY_DEMO") != "1") return

        val transport = DemoSharedStudyTransport()
        val coordinator = SharedStudyCoordinator(transport = transport, initialDisplayName = "קובי", localId = "demo-local")

        try {
            application {
                var dialogVisible by remember { mutableStateOf(false) }
                val state by coordinator.state.collectAsState()
                val events by transport.events.collectAsState()

                Window(
                    title = "הדגמת חברותא — זיתא",
                    state = WindowState(width = 920.dp, height = 660.dp),
                    onCloseRequest = ::exitApplication,
                ) {
                    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                        IntUiTheme(isDark = false) {
                            DemoWorkspace(
                                isAvailable = state.hasStartedDiscovery,
                                isConnected = state.isConnected,
                                events = events,
                                onOpenDialog = { dialogVisible = true },
                                onClearEvents = transport::clearEvents,
                            )
                            if (dialogVisible) {
                                SharedStudyDialog(coordinator = coordinator, onDismiss = { dialogVisible = false })
                            }
                        }
                    }
                }
            }
        } finally {
            transport.close()
        }
    }
}

@Composable
private fun DemoWorkspace(
    isAvailable: Boolean,
    isConnected: Boolean,
    events: List<String>,
    onOpenDialog: () -> Unit,
    onClearEvents: () -> Unit,
) {
    val accent = JewelTheme.globalColors.outlines.focused
    Row(Modifier.fillMaxSize().background(JewelTheme.globalColors.panelBackground)) {
        Column(Modifier.weight(1f).fillMaxHeight()) {
            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                        .background(JewelTheme.globalColors.panelBackground)
                        .border(1.dp, JewelTheme.globalColors.borders.normal)
                        .padding(horizontal = 18.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("זיתא", fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.weight(1f))
                DefaultButton(onClick = onOpenDialog) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                        if (isAvailable || isConnected) Box(Modifier.size(8.dp).background(Color(0xFF22A06B), CircleShape))
                        Text(if (isConnected) "חברותא פעילה" else if (isAvailable) "זמין לחברותא" else "פתיחת חברותא")
                    }
                }
            }

            Column(
                modifier = Modifier.fillMaxSize().padding(36.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp),
            ) {
                Text("בית המדרש הדיגיטלי", fontSize = 28.sp, fontWeight = FontWeight.SemiBold)
                Text(
                    "זהו מסך הדגמה בלבד. פתחו את חלונית החברותא, הפעילו זמינות והזמינו לומד שנמצא בסביבה.",
                    color = JewelTheme.globalColors.text.info,
                )
                Column(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .background(accent.copy(alpha = 0.07f), RoundedCornerShape(14.dp))
                            .border(1.dp, accent.copy(alpha = 0.25f), RoundedCornerShape(14.dp))
                            .padding(22.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Text("מסכת ברכות", fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
                    Text("מאימתי קורין את שמע בערבית? משעה שהכהנים נכנסים לאכול בתרומתן…", fontSize = 18.sp)
                    Text(
                        if (isConnected) "החברים המחוברים מדמים כעת מעבר בין קטעים והוספת הערות."
                        else "לאחר החיבור יופיעו בצד פעולות מדומות של המשתתפים.",
                        color = JewelTheme.globalColors.text.info,
                    )
                }
            }
        }

        Column(
            modifier =
                Modifier
                    .width(300.dp)
                    .fillMaxHeight()
                    .background(JewelTheme.globalColors.borders.disabled.copy(alpha = 0.16f))
                    .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("מה קורה בחברותא", modifier = Modifier.weight(1f), fontWeight = FontWeight.SemiBold)
                OutlinedButton(onClick = onClearEvents) { Text("נקה") }
            }
            Text("כל הפעולות כאן מדומות ונשארות במחשב הזה.", color = JewelTheme.globalColors.text.info, fontSize = 11.sp)
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(events.asReversed()) { event ->
                    Text(
                        event,
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .background(JewelTheme.globalColors.panelBackground, RoundedCornerShape(9.dp))
                                .padding(10.dp),
                        fontSize = 12.sp,
                    )
                }
            }
        }
    }
}

private class DemoSharedStudyTransport : SharedStudyTransport {
    private data class DemoPeer(val deviceId: String, val participantId: String, val name: String)

    @StructuredScope
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val sequence = AtomicLong()
    private val bluetooth = MutableStateFlow(BluetoothState.ON)
    private val stage = MutableStateFlow(DiscoveryStage.AUTOMATIC)
    private val devices = MutableStateFlow<List<NearbyStudyDevice>>(emptyList())
    private val incoming = MutableSharedFlow<IncomingStudyMessage>(extraBufferCapacity = 64)
    private val _events = MutableStateFlow(listOf("ההדגמה מוכנה — פתחו את חלונית החברותא"))
    private val simulations = mutableMapOf<String, Job>()
    private val peers =
        listOf(
            DemoPeer("demo-yael", "peer-yael", "יעל כהן"),
            DemoPeer("demo-daniel", "peer-daniel", "דניאל לוי"),
            DemoPeer("demo-noam", "peer-noam", "נועם ישראלי"),
        ).associateBy { it.deviceId }

    val events = _events.asStateFlow()
    override val bluetoothState = bluetooth.asStateFlow()
    override val discoveryStage = stage.asStateFlow()
    override val nearbyDevices = devices.asStateFlow()
    override val incomingMessages = incoming

    override suspend fun refreshBluetoothState() = Unit

    override suspend fun startDiscovery(localName: String) {
        addEvent("הזמינות הופעלה בשם $localName")
        delay(650)
        devices.value =
            peers.values.mapIndexed { index, peer ->
                NearbyStudyDevice(
                    id = peer.deviceId,
                    displayName = peer.name,
                    signalStrength = -42 - index * 9,
                    transportKind = StudyTransportKind.LOCAL_NETWORK,
                )
            }
        addEvent("נמצאו ${peers.size} לומדים זמינים בסביבה")
    }

    override suspend fun stopDiscovery() {
        devices.value = emptyList()
        addEvent("הזמינות כובתה")
    }

    override suspend fun advertise(localName: String) = Unit
    override suspend fun stopAdvertising() = Unit

    override suspend fun connect(deviceId: String) {
        peers[deviceId]?.let { addEvent("שולחים הזמנה אל ${it.name}…") }
    }

    override suspend fun disconnect(deviceId: String) {
        simulations.remove(deviceId)?.cancel()
        peers[deviceId]?.let { addEvent("החיבור עם ${it.name} הסתיים") }
    }

    override suspend fun send(deviceId: String, message: StudyMessage) {
        val peer = peers[deviceId] ?: return
        when (message) {
            is StudyMessage.Invitation -> acceptAndSimulate(peer, message.sessionId)
            is StudyMessage.Ping ->
                incoming.emit(
                    IncomingStudyMessage(
                        deviceId,
                        StudyMessage.Pong(peer.participantId, nextSequence(), message.sessionId, message.sequence),
                    ),
                )
            else -> Unit
        }
    }

    override fun openBluetoothSettings() = true
    override fun openHotspotSettings() = true

    fun clearEvents() {
        _events.value = emptyList()
    }

    fun close() {
        scope.cancel()
    }

    private fun acceptAndSimulate(peer: DemoPeer, sessionId: String) {
        if (simulations.containsKey(peer.deviceId)) return
        simulations[peer.deviceId] =
            scope.launch {
                delay(850)
                incoming.emit(
                    IncomingStudyMessage(
                        peer.deviceId,
                        StudyMessage.InvitationResponse(peer.participantId, nextSequence(), sessionId, accepted = true),
                    ),
                )
                addEvent("${peer.name} קיבל/ה את ההזמנה והצטרף/ה ללימוד")

                var turn = 0
                while (isActive) {
                    delay(2_400)
                    turn += 1
                    val line = 100L + turn
                    incoming.emit(
                        IncomingStudyMessage(
                            peer.deviceId,
                            StudyMessage.LocationChanged(
                                peer.participantId,
                                nextSequence(),
                                sessionId,
                                StudyLocation(bookId = 1L, lineId = line, tocPath = listOf(1L, 10L)),
                            ),
                        ),
                    )
                    addEvent("${peer.name} עבר/ה לקטע ${turn + 1} במסכת ברכות")

                    if (turn % 2 == 0) {
                        val noteId = "${peer.participantId}-note-$turn"
                        incoming.emit(
                            IncomingStudyMessage(
                                peer.deviceId,
                                StudyMessage.NoteChanged(
                                    peer.participantId,
                                    nextSequence(),
                                    sessionId,
                                    SharedStudyNote(
                                        id = noteId,
                                        authorId = peer.participantId,
                                        bookId = 1L,
                                        lineId = line,
                                        startOffset = 0,
                                        endOffset = 12,
                                        body = "כדאי לעיין בפירוש רש״י על השורה הזו.",
                                        quote = "מאימתי קורין",
                                        updatedAt = System.currentTimeMillis(),
                                    ),
                                ),
                            ),
                        )
                        addEvent("${peer.name} הוסיף/ה הערה משותפת")
                    }
                }
            }
    }

    private fun addEvent(event: String) {
        _events.value = (_events.value + event).takeLast(40)
    }

    private fun nextSequence() = sequence.incrementAndGet()
}
