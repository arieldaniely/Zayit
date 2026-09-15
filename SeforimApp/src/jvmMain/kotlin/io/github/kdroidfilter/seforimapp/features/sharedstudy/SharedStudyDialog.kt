package io.github.kdroidfilter.seforimapp.features.sharedstudy

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.ui.component.*
import org.jetbrains.jewel.ui.icons.AllIconsKeys
import seforimapp.seforimapp.generated.resources.*

private val DialogShape = RoundedCornerShape(18.dp)
private val CardShape = RoundedCornerShape(12.dp)

@Composable
fun SharedStudyDialog(
    coordinator: SharedStudyCoordinator,
    onDismiss: () -> Unit,
) {
    val state by coordinator.state.collectAsState()
    val nameState = rememberTextFieldState(state.displayName)
    var isEditingName by remember { mutableStateOf(false) }
    LaunchedEffect(nameState.text) { coordinator.setDisplayName(nameState.text.toString()) }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(
            modifier =
                Modifier
                    .width(600.dp)
                    .heightIn(max = 720.dp)
                    .background(JewelTheme.globalColors.panelBackground, DialogShape)
                    .border(1.dp, JewelTheme.globalColors.borders.normal, DialogShape)
                    .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            DialogHeader(onDismiss)
            AvailabilityCard(
                enabled = state.hasStartedDiscovery,
                isConnected = state.isConnected,
                onToggle = { enabled ->
                    if (enabled) coordinator.startDiscovery() else coordinator.stopDiscovery()
                },
            )
            IdentityCard(
                displayName = state.displayName,
                nameState = nameState,
                isEditing = isEditingName,
                onEdit = { isEditingName = true },
                onDone = { isEditingName = false },
            )

            state.pendingInvitation?.let { InvitationCard(it, coordinator) }

            if (state.hasStartedDiscovery) {
                DiscoveryContent(state, coordinator)
            } else if (!state.isConnected) {
                EmptyAvailabilityState()
            }

            if (state.participants.isNotEmpty()) ConnectedPeople(state)
            state.timedOutParticipantName?.let { name ->
                StatusMessage(stringResource(Res.string.shared_study_connection_lost, name), Color(0xFFB3261E))
            }
            state.error?.let { StatusMessage(sharedStudyErrorMessage(it), Color(0xFFB3261E)) }

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)) {
                if (state.isConnected) {
                    OutlinedButton(onClick = coordinator::leave) {
                        Text(stringResource(Res.string.shared_study_leave))
                    }
                }
                DefaultButton(onClick = onDismiss) { Text(stringResource(Res.string.shared_study_close)) }
            }
        }
    }
}

@Composable
private fun sharedStudyErrorMessage(error: SharedStudyError): String =
    stringResource(
        when (error) {
            SharedStudyError.DISCOVERY_UNAVAILABLE -> Res.string.shared_study_error_discovery_unavailable
            SharedStudyError.DEVICE_UNAVAILABLE -> Res.string.shared_study_error_device_unavailable
            SharedStudyError.CONNECTION_FAILED -> Res.string.shared_study_error_connection_failed
            SharedStudyError.CONNECTION_LOST -> Res.string.shared_study_error_connection_lost
            SharedStudyError.MESSAGE_SEND_FAILED -> Res.string.shared_study_error_message_send_failed
        },
    )

@Composable
private fun DialogHeader(onDismiss: () -> Unit) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(stringResource(Res.string.shared_study), fontSize = 22.sp, fontWeight = FontWeight.SemiBold)
            Text(
                stringResource(Res.string.shared_study_description),
                color = JewelTheme.globalColors.text.info,
                fontSize = 13.sp,
            )
        }
        IconButton(onClick = onDismiss) {
            Icon(
                key = AllIconsKeys.Windows.Close,
                contentDescription = stringResource(Res.string.shared_study_close),
                modifier = Modifier.size(16.dp),
            )
        }
    }
}

@Composable
private fun AvailabilityCard(
    enabled: Boolean,
    isConnected: Boolean,
    onToggle: (Boolean) -> Unit,
) {
    val accent = JewelTheme.globalColors.outlines.focused
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .background(if (enabled) accent.copy(alpha = 0.09f) else Color.Transparent, CardShape)
                .border(
                    1.dp,
                    if (enabled) accent.copy(alpha = 0.45f) else JewelTheme.globalColors.borders.normal,
                    CardShape,
                ).clickable { onToggle(!enabled) }
                .pointerHoverIcon(PointerIcon.Hand)
                .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Box(
            Modifier
                .size(36.dp)
                .background(if (enabled) accent.copy(alpha = 0.16f) else JewelTheme.globalColors.borders.disabled, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                key = AllIconsKeys.General.InspectionsOK,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
                tint = if (enabled) accent else JewelTheme.globalColors.text.disabled,
            )
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(stringResource(Res.string.shared_study_availability_title), fontWeight = FontWeight.SemiBold)
            Text(
                stringResource(
                    when {
                        isConnected -> Res.string.shared_study_availability_connected
                        enabled -> Res.string.shared_study_availability_on
                        else -> Res.string.shared_study_availability_off
                    },
                ),
                color = JewelTheme.globalColors.text.info,
                fontSize = 12.sp,
            )
        }
        AvailabilitySwitch(checked = enabled, onCheckedChange = onToggle)
    }
}

@Composable
private fun AvailabilitySwitch(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    val trackColor by animateColorAsState(
        if (checked) JewelTheme.globalColors.outlines.focused else JewelTheme.globalColors.borders.disabled,
        label = "sharedStudyAvailabilityTrack",
    )
    Box(
        modifier =
            Modifier
                .size(width = 42.dp, height = 24.dp)
                .clip(CircleShape)
                .background(trackColor)
                .clickable { onCheckedChange(!checked) }
                .pointerHoverIcon(PointerIcon.Hand)
                .padding(3.dp),
        contentAlignment = if (checked) Alignment.CenterEnd else Alignment.CenterStart,
    ) {
        Box(Modifier.size(18.dp).background(Color.White, CircleShape))
    }
}

@Composable
private fun IdentityCard(
    displayName: String,
    nameState: TextFieldState,
    isEditing: Boolean,
    onEdit: () -> Unit,
    onDone: () -> Unit,
) {
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .background(
                    JewelTheme.globalColors.borders.disabled
                        .copy(alpha = 0.24f),
                    CardShape,
                ).padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (isEditing) {
            Text(stringResource(Res.string.shared_study_name), fontSize = 12.sp, color = JewelTheme.globalColors.text.info)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextField(state = nameState, modifier = Modifier.weight(1f))
                DefaultButton(onClick = onDone, enabled = nameState.text.isNotBlank()) {
                    Text(stringResource(Res.string.shared_study_name_done))
                }
            }
        } else {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    stringResource(Res.string.shared_study_name),
                    color = JewelTheme.globalColors.text.info,
                    fontSize = 13.sp,
                )
                Text(
                    displayName.ifBlank { stringResource(Res.string.shared_study_name_fallback) },
                    modifier = Modifier.weight(1f),
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                IconButton(onClick = onEdit) {
                    Icon(
                        key = AllIconsKeys.Actions.Edit,
                        contentDescription = stringResource(Res.string.shared_study_edit_name),
                        modifier = Modifier.size(16.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun InvitationCard(
    invitation: PendingInvitation,
    coordinator: SharedStudyCoordinator,
) {
    val accent = JewelTheme.globalColors.outlines.focused
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .background(accent.copy(alpha = 0.10f), CardShape)
                .border(1.dp, accent.copy(alpha = 0.35f), CardShape)
                .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(stringResource(Res.string.shared_study_invitation, invitation.displayName), fontWeight = FontWeight.SemiBold)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            DefaultButton(onClick = { coordinator.respondToInvitation(true) }) {
                Text(stringResource(Res.string.shared_study_accept))
            }
            OutlinedButton(onClick = { coordinator.respondToInvitation(false) }) {
                Text(stringResource(Res.string.shared_study_decline))
            }
        }
    }
}

@Composable
private fun DiscoveryContent(
    state: SharedStudyState,
    coordinator: SharedStudyCoordinator,
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        when (state.bluetoothState) {
            BluetoothState.OFF -> BluetoothHelp(stringResource(Res.string.shared_study_bluetooth_off), coordinator)
            BluetoothState.UNSUPPORTED ->
                StatusMessage(stringResource(Res.string.shared_study_bluetooth_unsupported), JewelTheme.globalColors.text.info)
            BluetoothState.UNKNOWN, BluetoothState.ON -> Unit
        }
        when (state.discoveryStage) {
            DiscoveryStage.AUTOMATIC -> SearchStatus(state.isScanning)
            DiscoveryStage.BLUETOOTH_PAIRING ->
                BluetoothHelp(stringResource(Res.string.shared_study_pairing_help), coordinator)
            DiscoveryStage.HOTSPOT_GUIDANCE -> HotspotHelp(coordinator)
        }
        DeviceList(state, coordinator)
    }
}

@Composable
private fun SearchStatus(isScanning: Boolean) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        if (isScanning) CircularProgressIndicator(Modifier.size(16.dp))
        Box(Modifier.size(7.dp).background(Color(0xFF22A06B), CircleShape))
        Text(
            stringResource(Res.string.shared_study_automatic_search),
            color = JewelTheme.globalColors.text.info,
            fontSize = 12.sp,
        )
    }
}

@Composable
private fun EmptyAvailabilityState() {
    Column(
        modifier = Modifier.fillMaxWidth().padding(vertical = 14.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(stringResource(Res.string.shared_study_off_title), fontWeight = FontWeight.Medium)
        Text(
            stringResource(Res.string.shared_study_off_description),
            color = JewelTheme.globalColors.text.info,
            fontSize = 12.sp,
        )
    }
}

@Composable
private fun HotspotHelp(coordinator: SharedStudyCoordinator) {
    HelpCard(
        message = stringResource(Res.string.shared_study_hotspot_help),
        buttonText = stringResource(Res.string.shared_study_open_hotspot),
        onClick = { coordinator.openHotspotSettings() },
    )
}

@Composable
private fun BluetoothHelp(
    message: String,
    coordinator: SharedStudyCoordinator,
) {
    HelpCard(
        message = message,
        buttonText = stringResource(Res.string.shared_study_open_settings),
        onClick = { coordinator.openBluetoothSettings() },
    )
}

@Composable
private fun HelpCard(
    message: String,
    buttonText: String,
    onClick: () -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .background(Color(0xFFF59E0B).copy(alpha = 0.10f), CardShape)
                .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(message, modifier = Modifier.weight(1f), color = JewelTheme.globalColors.text.info, fontSize = 12.sp)
        OutlinedButton(onClick = onClick) { Text(buttonText) }
    }
}

@Composable
private fun DeviceList(
    state: SharedStudyState,
    coordinator: SharedStudyCoordinator,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(Res.string.shared_study_available_people), fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.weight(1f))
            OutlinedButton(onClick = coordinator::startDiscovery, enabled = !state.isScanning) {
                Icon(AllIconsKeys.Actions.Refresh, contentDescription = null, modifier = Modifier.size(14.dp))
                Spacer(Modifier.width(6.dp))
                Text(stringResource(Res.string.shared_study_refresh))
            }
        }
        if (state.nearbyDevices.isEmpty()) {
            Text(
                stringResource(
                    if (state.isScanning) Res.string.shared_study_searching_devices else Res.string.shared_study_no_devices,
                ),
                modifier = Modifier.fillMaxWidth().padding(vertical = 20.dp),
                color = JewelTheme.globalColors.text.info,
                fontSize = 12.sp,
            )
        }
        androidx.compose.foundation.lazy.LazyColumn(
            modifier = Modifier.fillMaxWidth().heightIn(max = 230.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            items(state.nearbyDevices, key = { it.id }) { device -> DeviceRow(device, coordinator) }
        }
    }
}

@Composable
private fun DeviceRow(
    device: NearbyStudyDevice,
    coordinator: SharedStudyCoordinator,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .background(
                    JewelTheme.globalColors.borders.disabled
                        .copy(alpha = 0.18f),
                    RoundedCornerShape(9.dp),
                ).padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(
            Modifier.size(30.dp).background(
                JewelTheme.globalColors.outlines.focused
                    .copy(alpha = 0.12f),
                CircleShape,
            ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(AllIconsKeys.General.User, contentDescription = null, modifier = Modifier.size(16.dp))
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(device.displayName, fontWeight = FontWeight.Medium)
            Text(stringResource(Res.string.shared_study_nearby), color = JewelTheme.globalColors.text.info, fontSize = 11.sp)
        }
        DefaultButton(onClick = { coordinator.invite(device) }) {
            Text(stringResource(Res.string.shared_study_connect))
        }
    }
}

@Composable
private fun ConnectedPeople(state: SharedStudyState) {
    Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
        Text(stringResource(Res.string.shared_study_connected_people), fontWeight = FontWeight.SemiBold)
        state.participants.forEach { participant ->
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(Modifier.size(9.dp).background(Color(participant.colorArgb.toULong()), CircleShape))
                Text(participant.displayName)
            }
        }
    }
}

@Composable
private fun StatusMessage(
    text: String,
    color: Color,
) {
    Text(text, color = color, fontSize = 12.sp)
}
