package io.github.kdroidfilter.seforimapp.features.sharedstudy

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.ui.component.*
import seforimapp.seforimapp.generated.resources.*

@Composable
fun SharedStudyDialog(
    coordinator: SharedStudyCoordinator,
    onDismiss: () -> Unit,
) {
    val state by coordinator.state.collectAsState()
    val nameState = rememberTextFieldState(state.displayName)
    LaunchedEffect(Unit) {
        coordinator.refreshBluetoothState()
        coordinator.startDiscovery()
    }
    LaunchedEffect(nameState.text) { coordinator.setDisplayName(nameState.text.toString()) }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(
            modifier =
                Modifier
                    .width(560.dp)
                    .background(JewelTheme.globalColors.panelBackground, RoundedCornerShape(16.dp))
                    .border(1.dp, JewelTheme.globalColors.borders.normal, RoundedCornerShape(16.dp))
                    .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(stringResource(Res.string.shared_study), fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
            Text(stringResource(Res.string.shared_study_name), fontWeight = FontWeight.Medium)
            TextField(state = nameState, modifier = Modifier.fillMaxWidth())

            state.pendingInvitation?.let { invitation ->
                Column(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .background(
                                JewelTheme.globalColors.outlines.focused
                                    .copy(alpha = 0.10f),
                                RoundedCornerShape(8.dp),
                            ).padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(stringResource(Res.string.shared_study_invitation, invitation.displayName))
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

            when (state.bluetoothState) {
                BluetoothState.OFF -> BluetoothHelp(stringResource(Res.string.shared_study_bluetooth_off), coordinator)
                BluetoothState.UNSUPPORTED -> Text(
                    stringResource(Res.string.shared_study_bluetooth_unsupported),
                    color = JewelTheme.globalColors.text.info,
                )
                BluetoothState.UNKNOWN -> if (state.nearbyDevices.isEmpty()) CircularProgressIndicator(Modifier.size(20.dp))
                BluetoothState.ON -> Unit
            }

            when (state.discoveryStage) {
                DiscoveryStage.AUTOMATIC ->
                    Text(stringResource(Res.string.shared_study_automatic_search), color = JewelTheme.globalColors.text.info)
                DiscoveryStage.BLUETOOTH_PAIRING ->
                    BluetoothHelp(stringResource(Res.string.shared_study_pairing_help), coordinator)
                DiscoveryStage.HOTSPOT_GUIDANCE -> HotspotHelp(coordinator)
            }

            DeviceList(state, coordinator)

            if (state.participants.isNotEmpty()) {
                Text(stringResource(Res.string.shared_study_connected_people), fontWeight = FontWeight.SemiBold)
                state.participants.forEach { participant ->
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Box(Modifier.size(10.dp).background(Color(participant.colorArgb.toULong()), CircleShape))
                        Text(participant.displayName)
                    }
                }
            }
            state.timedOutParticipantName?.let { name ->
                Text(stringResource(Res.string.shared_study_connection_lost, name), color = Color(0xFFB3261E))
            }
            state.error?.let { Text(it, color = Color(0xFFB3261E)) }
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)) {
                if (state.isConnected) {
                    OutlinedButton(onClick = coordinator::leave) { Text(stringResource(Res.string.shared_study_leave)) }
                }
                OutlinedButton(onClick = onDismiss) { Text(stringResource(Res.string.shared_study_close)) }
            }
        }
    }
}

@Composable
private fun HotspotHelp(coordinator: SharedStudyCoordinator) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(stringResource(Res.string.shared_study_hotspot_help), color = JewelTheme.globalColors.text.info)
        DefaultButton(onClick = { coordinator.openHotspotSettings() }) {
            Text(stringResource(Res.string.shared_study_open_hotspot))
        }
    }
}

@Composable
private fun BluetoothHelp(
    message: String,
    coordinator: SharedStudyCoordinator,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(message, color = JewelTheme.globalColors.text.info)
        DefaultButton(onClick = { coordinator.openBluetoothSettings() }) {
            Text(stringResource(Res.string.shared_study_open_settings))
        }
    }
}

@Composable
private fun DeviceList(
    state: SharedStudyState,
    coordinator: SharedStudyCoordinator,
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(Res.string.shared_study_available_people), fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.weight(1f))
            OutlinedButton(onClick = coordinator::startDiscovery, enabled = !state.isScanning) {
                Text(stringResource(Res.string.shared_study_refresh))
            }
        }
        if (state.isScanning) CircularProgressIndicator(modifier = Modifier.size(20.dp))
        if (state.nearbyDevices.isEmpty() && !state.isScanning) {
            Text(stringResource(Res.string.shared_study_no_devices), color = JewelTheme.globalColors.text.info)
        }
        androidx.compose.foundation.lazy.LazyColumn(
            modifier = Modifier.fillMaxWidth().heightIn(max = 240.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            items(state.nearbyDevices, key = { it.id }) { device ->
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(device.displayName)
                        Text(
                            when (device.transportKind) {
                                StudyTransportKind.BLE -> stringResource(Res.string.shared_study_via_ble)
                                StudyTransportKind.LOCAL_NETWORK -> stringResource(Res.string.shared_study_via_lan)
                                StudyTransportKind.BLUETOOTH_CLASSIC -> stringResource(Res.string.shared_study_via_classic)
                            },
                            color = JewelTheme.globalColors.text.info,
                            fontSize = 11.sp,
                        )
                        device.signalStrength?.let {
                            Text("$it dBm", color = JewelTheme.globalColors.text.info, fontSize = 11.sp)
                        }
                    }
                    DefaultButton(onClick = { coordinator.invite(device) }) {
                        Text(stringResource(Res.string.shared_study_connect))
                    }
                }
            }
        }
    }
}
