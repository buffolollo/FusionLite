package com.fusionlite.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
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

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 118.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            Column {
                Text(
                    "WORKSPACE",
                    fontSize = 9.sp,
                    letterSpacing = 2.2.sp,
                    fontWeight = FontWeight.Bold,
                    color = palette.green,
                )
                Text(
                    station?.name ?: "Solar plant",
                    fontSize = 21.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }

        item {
            InfoCard("Plant details") {
                InfoRow("Data source", session?.let { "${it.host}:${it.port}" } ?: "—")
                InfoRow("Station DN", station?.dn ?: "—")
                InfoRow("Installed capacity", metrics.capacity?.let { "${it.clean(1)} kWp" } ?: "—")
                InfoRow("Current power", metrics.currentPower?.let { "${it.clean(2)} kW" } ?: "—")
                InfoRow("Lifetime energy", metrics.cumulativeEnergy?.let { "${it.clean(1)} kWh" } ?: "—")
            }
        }

        if (state.stations.size > 1) {
            item {
                InfoCard("Switch plant") {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        state.stations.chunked(2).forEach { row ->
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                row.forEach { s ->
                                    val selected = s.dn == state.selectedDn
                                    Box(
                                        Modifier
                                            .weight(1f)
                                            .clip(RoundedCornerShape(14.dp))
                                            .background(
                                                if (selected) palette.green.copy(alpha = 0.16f)
                                                else palette.glass
                                            )
                                            .border(
                                                1.dp,
                                                if (selected) palette.green.copy(alpha = 0.5f) else Color.Transparent,
                                                RoundedCornerShape(14.dp),
                                            )
                                            .clickable { s.dn?.let(onSelectStation) }
                                            .padding(horizontal = 10.dp, vertical = 9.dp),
                                    ) {
                                        Text(
                                            s.name ?: s.dn ?: "Plant",
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = if (selected) palette.green else MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                    }
                                }
                                if (row.size == 1) Spacer(Modifier.weight(1f))
                            }
                        }
                    }
                }
            }
        }

        item {
            InfoCard("Appearance") {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            themeMode.label,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Text(
                            themeMode.caption(),
                            fontSize = 10.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Spacer(Modifier.width(12.dp))
                    ThemeToggle(mode = themeMode, onMode = onThemeMode)
                }
                if (themeMode.isExplicit) {
                    TextButton(
                        onClick = { onThemeMode(ThemeMode.SYSTEM) },
                        contentPadding = PaddingValues(0.dp),
                    ) {
                        Text("Follow the phone instead", fontSize = 11.sp, color = palette.cyan)
                    }
                }
            }
        }

        item {
            InfoCard("Auto refresh") {
                Row(
                    Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    listOf(15, 30, 60).forEach { seconds ->
                        val selected = state.refreshIntervalSeconds == seconds
                        Box(
                            Modifier
                                .clip(CircleShape)
                                .background(
                                    if (selected) palette.cyan.copy(alpha = 0.18f)
                                    else palette.glass
                                )
                                .border(
                                    1.dp,
                                    if (selected) palette.cyan.copy(alpha = 0.55f) else Color.Transparent,
                                    CircleShape,
                                )
                                .clickable { onIntervalChange(seconds) }
                                .padding(horizontal = 14.dp, vertical = 7.dp),
                        ) {
                            Text(
                                "${seconds}s",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = if (selected) palette.cyan else MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    PillButton("Refresh now", palette.cyan, onRefresh)
                    TextButton(onClick = onLogout) {
                        Text("Sign out", fontSize = 12.sp, color = MaterialTheme.colorScheme.error)
                    }
                }
            }
        }

        item {
            InfoCard("Connection") {
                InfoRow("Status", if (state.loggedIn) "Signed in" else "Signed out")
                InfoRow(
                    "Last update",
                    if (state.lastUpdated > 0) {
                        SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date(state.lastUpdated))
                    } else {
                        "—"
                    },
                )
                InfoRow("Diagnostics", state.diag ?: "—")
                state.flowError?.let { InfoRow("Flow warning", it) }
            }
        }

        item {
            Text(
                "Statistics endpoints reverse-engineered from com.huawei.smartpvms 26.2.6.001: " +
                    "station-real-kpi + energy-balance v1/v2. Chart filters use StatDim " +
                    "2/4/5/6 and send the same stationDn, dateStr, queryTime and timeZone " +
                    "keys as the shipped app.",
                fontSize = 9.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
            )
        }
    }
}

@Composable
private fun InfoCard(title: String, content: @Composable () -> Unit) {
    Card(
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            content()
        }
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(
            label,
            fontSize = 10.5.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.width(10.dp))
        Text(
            value,
            fontSize = 10.5.sp,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false),
        )
    }
}

@Composable
private fun PillButton(text: String, color: Color, onClick: () -> Unit) {
    Box(
        Modifier
            .clip(CircleShape)
            .background(color.copy(alpha = 0.14f))
            .border(1.dp, color.copy(alpha = 0.4f), CircleShape)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 7.dp),
    ) {
        Text(text, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = color)
    }
}
