package com.fusionlite.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fusionlite.UiState
import com.fusionlite.data.StationMetrics
import com.fusionlite.data.ThemeMode
import com.fusionlite.data.clean
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Account / workspace tab: plant identity, plant switching, appearance,
 * refresh cadence, connection diagnostics and sign-out.
 *
 * Destructive sign-out is confirmed in a dialog; plant switching uses a real
 * radio group (selected state is announced, not implied by colour alone).
 */
@Composable
fun AccountScreen(
    state: UiState,
    themeMode: ThemeMode,
    onThemeMode: (ThemeMode) -> Unit,
    onSelectStation: (String) -> Unit,
    onIntervalChange: (Int) -> Unit,
    onRefresh: () -> Unit,
    onLogout: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val station = state.stations.firstOrNull { it.dn == state.selectedDn } ?: state.stations.firstOrNull()
    val metrics = StationMetrics.from(station)
    val session = state.session
    val palette = LocalFusionPalette.current
    val design = LocalFusionDesign.current
    var confirmSignOut by remember { mutableStateOf(false) }
    var copied by remember { mutableStateOf(false) }
    val clipboard = LocalClipboardManager.current

    LaunchedEffect(copied) {
        if (copied) {
            kotlinx.coroutines.delay(2500)
            copied = false
        }
    }

    if (confirmSignOut) {
        AlertDialog(
            onDismissRequest = { confirmSignOut = false },
            title = { Text("Sign out of FusionLite?", style = design.type.heading) },
            text = {
                Text(
                    "The saved session and encrypted password will be removed from this device. " +
                        "Your plant keeps running; you can sign back in any time.",
                    style = design.type.body,
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmSignOut = false
                        onLogout()
                    },
                ) {
                    Text("Sign out", color = palette.danger, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmSignOut = false }) {
                    Text("Stay signed in", color = palette.cyan)
                }
            },
            containerColor = palette.surfaceRaised,
            titleContentColor = palette.textPrimary,
            textContentColor = palette.textSecondary,
        )
    }

    FusionPage(modifier = modifier, bottomPadding = design.navClearance) {
        item {
            FusionScreenHeader(
                eyebrow = "Workspace",
                title = "Account",
                accent = palette.green,
                trailing = {
                    FusionIconButton(
                        icon = FusionIcon.REFRESH,
                        contentDescription = "Refresh plant data",
                        onClick = onRefresh,
                        enabled = !state.loading,
                        tint = palette.cyan,
                        borderColor = palette.border,
                        spinning = state.loading,
                    )
                },
            )
        }

        item {
            FusionCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier
                            .size(44.dp)
                            .background(palette.green.copy(alpha = 0.14f), CircleShape),
                        contentAlignment = Alignment.Center,
                    ) {
                        FusionIcon(FusionIcon.PANEL, size = 22.dp, tint = palette.green)
                    }
                    Spacer(Modifier.width(design.spacingMd))
                    Column(Modifier.weight(1f)) {
                        Text(
                            station?.name ?: "Solar plant",
                            style = design.type.heading,
                            color = palette.textPrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.semantics { heading() },
                        )
                        Text(
                            station?.plantStatus ?: "Plant status unavailable",
                            style = design.type.bodySmall,
                            color = palette.textSecondary,
                        )
                    }
                    FusionStatusPill(
                        label = if (state.loggedIn) "Signed in" else "Signed out",
                        tone = if (state.loggedIn) FusionTone.SUCCESS else FusionTone.NEUTRAL,
                    )
                }
                FusionDivider()
                FusionKeyValueRow("Data source", session?.let { "${it.host}:${it.port}" } ?: "—")
                FusionKeyValueRow(
                    "Installed capacity",
                    metrics.capacity?.let { "${it.clean(1)} kWp" } ?: "—",
                )
                FusionKeyValueRow(
                    "Current power",
                    metrics.currentPower?.let { "${it.clean(2)} kW" } ?: "—",
                )
                FusionKeyValueRow(
                    "Lifetime energy",
                    metrics.cumulativeEnergy?.let { "${it.clean(1)} kWh" } ?: "—",
                )
                if (station?.dn != null) {
                    val stationDn = station.dn
                    Row(
                        Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text("Station DN", style = design.type.bodySmall, color = palette.textSecondary)
                            Text(
                                stationDn,
                                style = design.type.bodySmall,
                                color = palette.textPrimary,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        FusionIconButton(
                            icon = FusionIcon.COPY,
                            contentDescription = "Copy station identifier",
                            onClick = {
                                clipboard.setText(AnnotatedString(stationDn))
                                copied = true
                            },
                            tint = palette.cyan,
                            container = Color.Transparent,
                            iconSize = 18.dp,
                        )
                    }
                    if (copied) {
                        Text(
                            "Station identifier copied to clipboard.",
                            style = design.type.bodySmall,
                            color = palette.green,
                            modifier = Modifier.semantics {
                                liveRegion = LiveRegionMode.Polite
                            },
                        )
                    }
                }
            }
        }

        if (state.stations.size > 1) {
            item {
                FusionCard {
                    Text("Switch plant", style = design.type.heading, color = palette.textPrimary)
                    Text(
                        "Only one plant is shown at a time. Selecting another reloads live data.",
                        style = design.type.bodySmall,
                        color = palette.textSecondary,
                    )
                    Column(
                        Modifier.selectableGroup(),
                        verticalArrangement = Arrangement.spacedBy(design.spacingSm),
                    ) {
                        state.stations.forEach { s ->
                            val selected = s.dn == state.selectedDn
                            val name = s.name ?: s.dn ?: "Plant"
                            val shape = RoundedCornerShape(design.radiusMd)
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = 48.dp)
                                    .clip(shape)
                                    .background(
                                        if (selected) palette.green.copy(alpha = 0.10f) else palette.glass,
                                        shape,
                                    )
                                    .border(
                                        1.dp,
                                        if (selected) palette.green.copy(alpha = 0.45f) else Color.Transparent,
                                        shape,
                                    )
                                    .selectable(
                                        selected = selected,
                                        role = Role.RadioButton,
                                        onClick = { s.dn?.let(onSelectStation) },
                                    )
                                    .padding(horizontal = design.spacingMd, vertical = design.spacingSm),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Box(
                                    Modifier
                                        .size(20.dp)
                                        .border(
                                            2.dp,
                                            if (selected) palette.green else palette.borderStrong,
                                            CircleShape,
                                        ),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    if (selected) {
                                        Box(
                                            Modifier
                                                .size(10.dp)
                                                .background(palette.green, CircleShape)
                                        )
                                    }
                                }
                                Spacer(Modifier.width(design.spacingMd))
                                Text(
                                    name,
                                    style = design.type.label,
                                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                                    color = if (selected) palette.textPrimary else palette.textSecondary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                    }
                }
            }
        }

        item {
            FusionCard {
                Text("Appearance", style = design.type.heading, color = palette.textPrimary)
                FusionSettingRow(
                    title = themeMode.label,
                    subtitle = themeMode.caption(),
                ) {
                    ThemeToggle(mode = themeMode, onMode = onThemeMode)
                }
                if (themeMode.isExplicit) {
                    FusionButton(
                        text = "Follow the phone instead",
                        onClick = { onThemeMode(ThemeMode.SYSTEM) },
                        variant = FusionButtonVariant.GHOST,
                        accent = palette.cyan,
                        icon = FusionIcon.SUN,
                    )
                }
            }
        }

        item {
            FusionCard {
                Text("Refresh cadence", style = design.type.heading, color = palette.textPrimary)
                Text(
                    "Historical data is loaded when you open a tab; live telemetry follows this cadence.",
                    style = design.type.bodySmall,
                    color = palette.textSecondary,
                )
                FusionSegmentedControl(
                    options = listOf(15, 30, 60),
                    selected = state.refreshIntervalSeconds,
                    label = { "${it}s" },
                    onSelect = onIntervalChange,
                    accent = palette.cyan,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(design.spacingSm)) {
                    FusionButton(
                        text = "Refresh now",
                        onClick = onRefresh,
                        variant = FusionButtonVariant.SECONDARY,
                        icon = FusionIcon.REFRESH,
                        enabled = !state.loading,
                    )
                }
            }
        }

        item {
            FusionCard {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("Connection", style = design.type.heading, color = palette.textPrimary)
                    FusionStatusPill(
                        label = if (state.error == null && state.loggedIn) "Healthy" else "Attention",
                        tone = if (state.error == null && state.loggedIn) {
                            FusionTone.SUCCESS
                        } else {
                            FusionTone.WARNING
                        },
                    )
                }
                FusionKeyValueRow(
                    "Last update",
                    if (state.lastUpdated > 0) {
                        SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
                            .format(Date(state.lastUpdated))
                    } else {
                        "—"
                    },
                )
                FusionKeyValueRow("Diagnostics", state.diag ?: "—")
                state.error?.let { FusionKeyValueRow("Live error", it, valueColor = palette.danger) }
                state.flowError?.let { FusionKeyValueRow("Flow warning", it, valueColor = palette.warning) }
            }
        }

        item {
            FusionCard {
                Text("Session", style = design.type.heading, color = palette.textPrimary)
                Text(
                    "Signing out clears the encrypted password and all tokens stored on this device.",
                    style = design.type.bodySmall,
                    color = palette.textSecondary,
                )
                FusionButton(
                    text = "Sign out",
                    onClick = { confirmSignOut = true },
                    variant = FusionButtonVariant.DANGER,
                    icon = FusionIcon.ARROW_UP_RIGHT,
                    fillWidth = true,
                )
            }
        }

        item {
            FusionCard {
                Text("About this build", style = design.type.heading, color = palette.textPrimary)
                Text(
                    "Statistics endpoints reverse-engineered from com.huawei.smartpvms " +
                        "26.2.6.001: station-real-kpi plus energy-balance v1/v2. Chart filters " +
                        "use StatDim 2/4/5/6 and send the same stationDn, dateStr, queryTime and " +
                        "timeZone keys as the shipped app.",
                    style = design.type.bodySmall,
                    color = palette.textTertiary,
                )
                Text(
                    "Tokens, component states and contrast results: " +
                        "design-system/fusionlite/ACCESSIBILITY.md",
                    style = design.type.bodySmall,
                    color = palette.textTertiary,
                )
            }
        }
    }
}
