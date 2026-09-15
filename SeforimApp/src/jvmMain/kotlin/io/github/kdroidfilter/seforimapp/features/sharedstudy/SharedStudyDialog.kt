package io.github.kdroidfilter.seforimapp.features.sharedstudy

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import io.github.kdroidfilter.seforimapp.core.presentation.components.ExpandCollapseIcon
import io.github.kdroidfilter.seforimapp.core.presentation.components.HorizontalDivider
import io.github.kdroidfilter.seforimapp.core.presentation.components.VerticalDivider
import io.github.kdroidfilter.seforimapp.icons.Bluetooth
import io.github.kdroidfilter.seforimapp.icons.Wifi
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.ui.component.CircularProgressIndicator
import org.jetbrains.jewel.ui.component.DefaultButton
import org.jetbrains.jewel.ui.component.Icon
import org.jetbrains.jewel.ui.component.IconButton
import org.jetbrains.jewel.ui.component.OutlinedButton
import org.jetbrains.jewel.ui.component.Text
import org.jetbrains.jewel.ui.component.TextField
import org.jetbrains.jewel.ui.icons.AllIconsKeys
import seforimapp.seforimapp.generated.resources.Res
import seforimapp.seforimapp.generated.resources.shared_study
import seforimapp.seforimapp.generated.resources.shared_study_accept
import seforimapp.seforimapp.generated.resources.shared_study_available_people
import seforimapp.seforimapp.generated.resources.shared_study_bluetooth_off
import seforimapp.seforimapp.generated.resources.shared_study_bluetooth_suggestion_desc
import seforimapp.seforimapp.generated.resources.shared_study_bluetooth_suggestion_title
import seforimapp.seforimapp.generated.resources.shared_study_bluetooth_unsupported
import seforimapp.seforimapp.generated.resources.shared_study_close
import seforimapp.seforimapp.generated.resources.shared_study_connect
import seforimapp.seforimapp.generated.resources.shared_study_connected_badge
import seforimapp.seforimapp.generated.resources.shared_study_connected_with
import seforimapp.seforimapp.generated.resources.shared_study_connection_lost
import seforimapp.seforimapp.generated.resources.shared_study_decline
import seforimapp.seforimapp.generated.resources.shared_study_description
import seforimapp.seforimapp.generated.resources.shared_study_edit_name
import seforimapp.seforimapp.generated.resources.shared_study_error_connection_failed
import seforimapp.seforimapp.generated.resources.shared_study_error_connection_lost
import seforimapp.seforimapp.generated.resources.shared_study_error_device_unavailable
import seforimapp.seforimapp.generated.resources.shared_study_error_discovery_unavailable
import seforimapp.seforimapp.generated.resources.shared_study_error_message_send_failed
import seforimapp.seforimapp.generated.resources.shared_study_invitation
import seforimapp.seforimapp.generated.resources.shared_study_invitation_incoming
import seforimapp.seforimapp.generated.resources.shared_study_leave
import seforimapp.seforimapp.generated.resources.shared_study_name_done
import seforimapp.seforimapp.generated.resources.shared_study_name_fallback
import seforimapp.seforimapp.generated.resources.shared_study_name_hint
import seforimapp.seforimapp.generated.resources.shared_study_no_devices
import seforimapp.seforimapp.generated.resources.shared_study_off_description
import seforimapp.seforimapp.generated.resources.shared_study_off_title
import seforimapp.seforimapp.generated.resources.shared_study_open_hotspot
import seforimapp.seforimapp.generated.resources.shared_study_open_settings
import seforimapp.seforimapp.generated.resources.shared_study_refresh
import seforimapp.seforimapp.generated.resources.shared_study_searching_devices
import seforimapp.seforimapp.generated.resources.shared_study_status_active
import seforimapp.seforimapp.generated.resources.shared_study_status_connected
import seforimapp.seforimapp.generated.resources.shared_study_status_off
import seforimapp.seforimapp.generated.resources.shared_study_status_searching
import seforimapp.seforimapp.generated.resources.shared_study_troubleshooting_subtitle
import seforimapp.seforimapp.generated.resources.shared_study_troubleshooting_title
import seforimapp.seforimapp.generated.resources.shared_study_turn_on_action
import seforimapp.seforimapp.generated.resources.shared_study_via_ble
import seforimapp.seforimapp.generated.resources.shared_study_via_classic
import seforimapp.seforimapp.generated.resources.shared_study_via_lan
import seforimapp.seforimapp.generated.resources.shared_study_wifi_suggestion_desc
import seforimapp.seforimapp.generated.resources.shared_study_wifi_suggestion_title

private val DialogShape = RoundedCornerShape(16.dp)
private val CardShape = RoundedCornerShape(10.dp)

@Composable
fun SharedStudyDialog(
    coordinator: SharedStudyCoordinator,
    onDismiss: () -> Unit,
) {
    val state by coordinator.state.collectAsState()
    val nameState = rememberTextFieldState(state.displayName)
    var isEditingName by remember { mutableStateOf(false) }
    LaunchedEffect(nameState.text) { coordinator.setDisplayName(nameState.text.toString()) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
            Column(
                modifier =
                    Modifier
                        .width(560.dp)
                        .heightIn(max = 680.dp)
                        .background(JewelTheme.globalColors.panelBackground, DialogShape)
                        .border(1.dp, JewelTheme.globalColors.borders.normal, DialogShape)
                        .padding(bottom = 16.dp),
            ) {
                DialogHeader(
                    state = state,
                    onDismiss = onDismiss,
                )

                HorizontalDivider()

                Column(
                    modifier =
                        Modifier
                            .weight(1f, fill = false)
                            .verticalScroll(rememberScrollState())
                            .padding(horizontal = 20.dp, vertical = 14.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    // Unified Identity & Availability Card
                    UnifiedProfileCard(
                        displayName = state.displayName,
                        nameState = nameState,
                        isEditing = isEditingName,
                        onEdit = { isEditingName = true },
                        onDone = { isEditingName = false },
                        isDiscoveryEnabled = state.hasStartedDiscovery,
                        onToggleDiscovery = { enabled ->
                            if (enabled) coordinator.startDiscovery() else coordinator.stopDiscovery()
                        },
                    )

                    // Incoming Invitation Banner
                    state.pendingInvitation?.let { invitation ->
                        PendingInvitationBanner(
                            invitation = invitation,
                            onAccept = { coordinator.respondToInvitation(true) },
                            onDecline = { coordinator.respondToInvitation(false) },
                        )
                    }

                    // Active Connected Session Card (with Leave action right inside)
                    if (state.isConnected) {
                        ActiveSessionCard(
                            state = state,
                            onLeave = coordinator::leave,
                        )
                    }

                    // Discovery Section or Disabled Empty State
                    if (state.hasStartedDiscovery) {
                        DiscoverySection(
                            state = state,
                            coordinator = coordinator,
                        )
                    } else if (!state.isConnected) {
                        DiscoveryOffState(
                            onTurnOn = coordinator::startDiscovery,
                        )
                    }

                    // Error & Timeout Alerts
                    state.timedOutParticipantName?.let { name ->
                        AlertBanner(
                            text = stringResource(Res.string.shared_study_connection_lost, name),
                            isError = true,
                        )
                    }
                    state.error?.let { err ->
                        AlertBanner(
                            text = sharedStudyErrorMessage(err),
                            isError = true,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DialogHeader(
    state: SharedStudyState,
    onDismiss: () -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(
                    text = stringResource(Res.string.shared_study),
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                )
                HeaderStatusBadge(state)
            }
            Text(
                text = stringResource(Res.string.shared_study_description),
                color = JewelTheme.globalColors.text.info,
                fontSize = 12.sp,
            )
        }

        IconButton(
            onClick = onDismiss,
            modifier = Modifier.size(28.dp),
        ) {
            Icon(
                key = AllIconsKeys.Windows.Close,
                contentDescription = stringResource(Res.string.shared_study_close),
                modifier = Modifier.size(16.dp),
            )
        }
    }
}

@Composable
private fun HeaderStatusBadge(state: SharedStudyState) {
    val (badgeText, dotColor, badgeBg) =
        when {
            state.isConnected -> {
                Triple(
                    stringResource(Res.string.shared_study_status_connected),
                    Color(0xFF22A06B),
                    Color(0xFF22A06B).copy(alpha = 0.12f),
                )
            }
            state.hasStartedDiscovery && state.isScanning -> {
                Triple(
                    stringResource(Res.string.shared_study_status_searching),
                    JewelTheme.globalColors.outlines.focused,
                    JewelTheme.globalColors.outlines.focused.copy(alpha = 0.12f),
                )
            }
            state.hasStartedDiscovery -> {
                Triple(
                    stringResource(Res.string.shared_study_status_active),
                    Color(0xFF22A06B),
                    Color(0xFF22A06B).copy(alpha = 0.12f),
                )
            }
            else -> {
                Triple(
                    stringResource(Res.string.shared_study_status_off),
                    JewelTheme.globalColors.text.disabled,
                    JewelTheme.globalColors.borders.disabled.copy(alpha = 0.25f),
                )
            }
        }

    Row(
        modifier =
            Modifier
                .background(badgeBg, RoundedCornerShape(12.dp))
                .padding(horizontal = 8.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        if (state.hasStartedDiscovery && state.isScanning && !state.isConnected) {
            CircularProgressIndicator(Modifier.size(8.dp))
        } else {
            Box(
                Modifier
                    .size(7.dp)
                    .background(dotColor, CircleShape),
            )
        }
        Text(
            text = badgeText,
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium,
            color = if (state.hasStartedDiscovery || state.isConnected) JewelTheme.globalColors.text.normal else JewelTheme.globalColors.text.info,
        )
    }
}

@Composable
private fun UnifiedProfileCard(
    displayName: String,
    nameState: TextFieldState,
    isEditing: Boolean,
    onEdit: () -> Unit,
    onDone: () -> Unit,
    isDiscoveryEnabled: Boolean,
    onToggleDiscovery: (Boolean) -> Unit,
) {
    val accent = JewelTheme.globalColors.outlines.focused
    val shape = CardShape

    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .background(JewelTheme.globalColors.toolwindowBackground.copy(alpha = 0.35f), shape)
                .border(1.dp, JewelTheme.globalColors.borders.normal, shape)
                .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        // Avatar circle with first letter of name
        val avatarLetter = displayName.trim().firstOrNull()?.toString()?.uppercase() ?: "ל"
        Box(
            modifier =
                Modifier
                    .size(38.dp)
                    .background(accent.copy(alpha = 0.15f), CircleShape)
                    .border(1.dp, accent.copy(alpha = 0.40f), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = avatarLetter,
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp,
                color = accent,
            )
        }

        // Profile identity info or edit field
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            if (isEditing) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    TextField(
                        state = nameState,
                        modifier = Modifier.weight(1f),
                    )
                    DefaultButton(
                        onClick = onDone,
                        enabled = nameState.text.isNotBlank(),
                    ) {
                        Text(stringResource(Res.string.shared_study_name_done), fontSize = 12.sp)
                    }
                }
            } else {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text(
                        text = displayName.ifBlank { stringResource(Res.string.shared_study_name_fallback) },
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 14.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    IconButton(
                        onClick = onEdit,
                        modifier = Modifier.size(20.dp),
                    ) {
                        Icon(
                            key = AllIconsKeys.Actions.Edit,
                            contentDescription = stringResource(Res.string.shared_study_edit_name),
                            modifier = Modifier.size(13.dp),
                            tint = JewelTheme.globalColors.text.info,
                        )
                    }
                }
                Text(
                    text = stringResource(Res.string.shared_study_name_hint),
                    color = JewelTheme.globalColors.text.info,
                    fontSize = 11.sp,
                )
            }
        }

        // Clean Vertical Separator
        Box(
            modifier =
                Modifier
                    .height(34.dp)
                    .width(1.dp)
                    .background(JewelTheme.globalColors.borders.normal),
        )

        // Availability Toggle Switch
        Row(
            modifier =
                Modifier
                    .clickable { onToggleDiscovery(!isDiscoveryEnabled) }
                    .pointerHoverIcon(PointerIcon.Hand)
                    .padding(vertical = 4.dp, horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Column(
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(1.dp),
            ) {
                Text(
                    text = stringResource(Res.string.shared_study_status_active),
                    fontWeight = FontWeight.Medium,
                    fontSize = 12.sp,
                )
            }
            ModernSwitch(
                checked = isDiscoveryEnabled,
                onCheckedChange = onToggleDiscovery,
            )
        }
    }
}

@Composable
private fun ModernSwitch(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    val trackColor by animateColorAsState(
        if (checked) JewelTheme.globalColors.outlines.focused else JewelTheme.globalColors.borders.disabled,
        label = "switchTrackColor",
    )
    val thumbOffset by animateDpAsState(
        if (checked) 18.dp else 0.dp,
        label = "switchThumbOffset",
    )

    Box(
        modifier =
            Modifier
                .width(40.dp)
                .height(22.dp)
                .clip(CircleShape)
                .background(trackColor)
                .clickable { onCheckedChange(!checked) }
                .pointerHoverIcon(PointerIcon.Hand)
                .padding(2.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        Box(
            modifier =
                Modifier
                    .padding(start = thumbOffset)
                    .size(18.dp)
                    .background(Color.White, CircleShape),
        )
    }
}

@Composable
private fun ActiveSessionCard(
    state: SharedStudyState,
    onLeave: () -> Unit,
) {
    val accent = Color(0xFF22A06B)
    val shape = CardShape

    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .background(accent.copy(alpha = 0.08f), shape)
                .border(1.dp, accent.copy(alpha = 0.40f), shape)
                .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Box(
                    modifier =
                        Modifier
                            .size(10.dp)
                            .background(accent, CircleShape),
                )
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        text = stringResource(Res.string.shared_study_connected_with),
                        fontSize = 11.sp,
                        color = JewelTheme.globalColors.text.info,
                    )
                    Text(
                        text = state.participants.joinToString { it.displayName }.ifBlank { stringResource(Res.string.shared_study_connected_badge) },
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                    )
                }
            }

            // Leave action placed specifically next to the active connection
            OutlinedButton(onClick = onLeave) {
                Text(
                    text = stringResource(Res.string.shared_study_leave),
                    fontSize = 12.sp,
                )
            }
        }
    }
}

@Composable
private fun PendingInvitationBanner(
    invitation: PendingInvitation,
    onAccept: () -> Unit,
    onDecline: () -> Unit,
) {
    val accent = JewelTheme.globalColors.outlines.focused
    val shape = CardShape

    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .background(accent.copy(alpha = 0.12f), shape)
                .border(1.dp, accent.copy(alpha = 0.50f), shape)
                .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            modifier =
                Modifier
                    .size(36.dp)
                    .background(accent.copy(alpha = 0.20f), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                key = AllIconsKeys.General.User,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
                tint = accent,
            )
        }

        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = stringResource(Res.string.shared_study_invitation_incoming),
                fontSize = 11.sp,
                color = JewelTheme.globalColors.text.info,
            )
            Text(
                text = stringResource(Res.string.shared_study_invitation, invitation.displayName),
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp,
            )
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            DefaultButton(onClick = onAccept) {
                Text(stringResource(Res.string.shared_study_accept), fontSize = 12.sp)
            }
            OutlinedButton(onClick = onDecline) {
                Text(stringResource(Res.string.shared_study_decline), fontSize = 12.sp)
            }
        }
    }
}

@Composable
private fun DiscoverySection(
    state: SharedStudyState,
    coordinator: SharedStudyCoordinator,
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        // Section Header with Title, Count and Refresh button (fixed layout bug)
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(Res.string.shared_study_available_people),
                fontWeight = FontWeight.SemiBold,
                fontSize = 13.sp,
            )

            if (state.nearbyDevices.isNotEmpty()) {
                Spacer(Modifier.width(6.dp))
                Box(
                    modifier =
                        Modifier
                            .background(JewelTheme.globalColors.borders.disabled.copy(alpha = 0.4f), RoundedCornerShape(10.dp))
                            .padding(horizontal = 6.dp, vertical = 2.dp),
                ) {
                    Text(
                        text = state.nearbyDevices.size.toString(),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                    )
                }
            }

            Spacer(Modifier.weight(1f))

            // Refresh button: Content wrapped in Row to prevent Jewel OutlinedButton overlap bug!
            OutlinedButton(
                onClick = coordinator::startDiscovery,
                enabled = !state.isScanning,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Icon(
                        key = AllIconsKeys.Actions.Refresh,
                        contentDescription = null,
                        modifier = Modifier.size(13.dp),
                    )
                    Text(
                        text = stringResource(Res.string.shared_study_refresh),
                        fontSize = 12.sp,
                    )
                }
            }
        }

        // Bluetooth off alert if relevant
        if (state.bluetoothState == BluetoothState.OFF) {
            AlertBanner(
                text = stringResource(Res.string.shared_study_bluetooth_off),
                actionText = stringResource(Res.string.shared_study_open_settings),
                onAction = { coordinator.openBluetoothSettings() },
            )
        } else if (state.bluetoothState == BluetoothState.UNSUPPORTED) {
            Text(
                text = stringResource(Res.string.shared_study_bluetooth_unsupported),
                color = JewelTheme.globalColors.text.info,
                fontSize = 12.sp,
            )
        }

        // Devices List or Empty State
        if (state.nearbyDevices.isEmpty()) {
            Box(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .background(JewelTheme.globalColors.toolwindowBackground.copy(alpha = 0.20f), CardShape)
                        .border(1.dp, JewelTheme.globalColors.borders.normal, CardShape)
                        .padding(vertical = 24.dp, horizontal = 16.dp),
                contentAlignment = Alignment.Center,
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    if (state.isScanning) {
                        CircularProgressIndicator(Modifier.size(24.dp))
                        Text(
                            text = stringResource(Res.string.shared_study_searching_devices),
                            color = JewelTheme.globalColors.text.info,
                            fontSize = 12.sp,
                        )
                    } else {
                        Text(
                            text = stringResource(Res.string.shared_study_no_devices),
                            color = JewelTheme.globalColors.text.info,
                            fontSize = 12.sp,
                        )
                    }
                }
            }
        } else {
            LazyColumn(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .heightIn(max = 190.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                items(state.nearbyDevices, key = { it.id }) { device ->
                    DeviceRow(
                        device = device,
                        onConnect = { coordinator.invite(device) },
                    )
                }
            }
        }

        // Troubleshooting Section: Parallel Bluetooth & Wi-Fi suggestions with expandable details
        ParallelTroubleshootingSection(
            discoveryStage = state.discoveryStage,
            coordinator = coordinator,
        )
    }
}

@Composable
private fun DeviceRow(
    device: NearbyStudyDevice,
    onConnect: () -> Unit,
) {
    val shape = RoundedCornerShape(8.dp)
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .background(JewelTheme.globalColors.toolwindowBackground.copy(alpha = 0.35f), shape)
                .border(1.dp, JewelTheme.globalColors.borders.normal, shape)
                .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(
            modifier =
                Modifier
                    .size(32.dp)
                    .background(JewelTheme.globalColors.outlines.focused.copy(alpha = 0.12f), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                key = AllIconsKeys.General.User,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
            )
        }

        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = device.displayName,
                fontWeight = FontWeight.Medium,
                fontSize = 13.sp,
            )
            val protocolText =
                when (device.transportKind) {
                    StudyTransportKind.LOCAL_NETWORK -> stringResource(Res.string.shared_study_via_lan)
                    StudyTransportKind.BLUETOOTH_CLASSIC -> stringResource(Res.string.shared_study_via_classic)
                    StudyTransportKind.BLE -> stringResource(Res.string.shared_study_via_ble)
                }
            Text(
                text = protocolText,
                color = JewelTheme.globalColors.text.info,
                fontSize = 11.sp,
            )
        }

        DefaultButton(onClick = onConnect) {
            Text(
                text = stringResource(Res.string.shared_study_connect),
                fontSize = 12.sp,
            )
        }
    }
}

/**
 * Parallel troubleshooting suggestions with expandable details for Bluetooth and Wi-Fi.
 * Presented simultaneously as requested by the user, with simple icons and concise expandable explanations.
 */
@Composable
private fun ParallelTroubleshootingSection(
    discoveryStage: DiscoveryStage,
    coordinator: SharedStudyCoordinator,
) {
    // Automatically expand the suggested stage if relevant, but let the user toggle freely
    var bluetoothExpanded by remember { mutableStateOf(discoveryStage == DiscoveryStage.BLUETOOTH_PAIRING) }
    var wifiExpanded by remember { mutableStateOf(discoveryStage == DiscoveryStage.HOTSPOT_GUIDANCE) }

    LaunchedEffect(discoveryStage) {
        if (discoveryStage == DiscoveryStage.BLUETOOTH_PAIRING) {
            bluetoothExpanded = true
        } else if (discoveryStage == DiscoveryStage.HOTSPOT_GUIDANCE) {
            wifiExpanded = true
        }
    }

    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(top = 6.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = stringResource(Res.string.shared_study_troubleshooting_title),
            fontWeight = FontWeight.SemiBold,
            fontSize = 12.sp,
            color = JewelTheme.globalColors.text.info,
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            // Bluetooth suggestion card
            TroubleshootingCard(
                modifier = Modifier.weight(1f),
                icon = Bluetooth,
                title = stringResource(Res.string.shared_study_bluetooth_suggestion_title),
                description = stringResource(Res.string.shared_study_bluetooth_suggestion_desc),
                actionText = stringResource(Res.string.shared_study_open_settings),
                onAction = { coordinator.openBluetoothSettings() },
                isExpanded = bluetoothExpanded,
                onToggleExpand = { bluetoothExpanded = !bluetoothExpanded },
            )

            // Wi-Fi / Hotspot suggestion card
            TroubleshootingCard(
                modifier = Modifier.weight(1f),
                icon = Wifi,
                title = stringResource(Res.string.shared_study_wifi_suggestion_title),
                description = stringResource(Res.string.shared_study_wifi_suggestion_desc),
                actionText = stringResource(Res.string.shared_study_open_hotspot),
                onAction = { coordinator.openHotspotSettings() },
                isExpanded = wifiExpanded,
                onToggleExpand = { wifiExpanded = !wifiExpanded },
            )
        }
    }
}

@Composable
private fun TroubleshootingCard(
    modifier: Modifier = Modifier,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    description: String,
    actionText: String,
    onAction: () -> Unit,
    isExpanded: Boolean,
    onToggleExpand: () -> Unit,
) {
    val shape = RoundedCornerShape(8.dp)

    Column(
        modifier =
            modifier
                .clip(shape)
                .background(JewelTheme.globalColors.toolwindowBackground.copy(alpha = 0.25f), shape)
                .border(1.dp, JewelTheme.globalColors.borders.normal, shape)
                .padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .clickable { onToggleExpand() }
                    .pointerHoverIcon(PointerIcon.Hand),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = JewelTheme.globalColors.outlines.focused,
                )
                Text(
                    text = title,
                    fontWeight = FontWeight.Medium,
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            IconButton(
                onClick = onToggleExpand,
                modifier = Modifier.size(20.dp),
            ) {
                ExpandCollapseIcon(
                    expanded = isExpanded,
                    size = 14.dp,
                )
            }
        }

        AnimatedVisibility(visible = isExpanded) {
            Column(
                modifier = Modifier.padding(top = 4.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = description,
                    fontSize = 11.sp,
                    color = JewelTheme.globalColors.text.info,
                    lineHeight = 15.sp,
                )
                OutlinedButton(onClick = onAction) {
                    Text(text = actionText, fontSize = 11.sp)
                }
            }
        }
    }
}

@Composable
private fun DiscoveryOffState(
    onTurnOn: () -> Unit,
) {
    Box(
        modifier =
            Modifier
                .fillMaxWidth()
                .background(JewelTheme.globalColors.toolwindowBackground.copy(alpha = 0.25f), CardShape)
                .border(1.dp, JewelTheme.globalColors.borders.normal, CardShape)
                .padding(24.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                text = stringResource(Res.string.shared_study_off_title),
                fontWeight = FontWeight.SemiBold,
                fontSize = 14.sp,
            )
            Text(
                text = stringResource(Res.string.shared_study_off_description),
                color = JewelTheme.globalColors.text.info,
                fontSize = 12.sp,
            )
            Spacer(Modifier.height(4.dp))
            DefaultButton(onClick = onTurnOn) {
                Text(stringResource(Res.string.shared_study_turn_on_action), fontSize = 12.sp)
            }
        }
    }
}

@Composable
private fun AlertBanner(
    text: String,
    isError: Boolean = false,
    actionText: String? = null,
    onAction: (() -> Unit)? = null,
) {
    val color = if (isError) Color(0xFFB3261E) else Color(0xFFE29500)
    val shape = RoundedCornerShape(8.dp)

    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .background(color.copy(alpha = 0.10f), shape)
                .border(1.dp, color.copy(alpha = 0.35f), shape)
                .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(
            text = text,
            modifier = Modifier.weight(1f),
            color = if (isError) color else JewelTheme.globalColors.text.normal,
            fontSize = 12.sp,
        )
        if (actionText != null && onAction != null) {
            OutlinedButton(onClick = onAction) {
                Text(actionText, fontSize = 11.sp)
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
