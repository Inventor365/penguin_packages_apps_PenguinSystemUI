/*
 * Copyright (C) 2026 The PenguinOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package com.android.systemui.qs.composefragment

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioManager
import android.service.quicksettings.Tile
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Arrangement.spacedBy
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.NotificationsOff
import androidx.compose.material.icons.filled.Vibration
import androidx.compose.material3.Icon as MaterialIcon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.coerceIn
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.LifecycleStartEffect
import com.android.systemui.common.shared.model.Icon
import com.android.systemui.qs.panels.ui.compose.TileListener
import com.android.systemui.qs.panels.ui.compose.infinitegrid.SmallTileContent
import com.android.systemui.qs.panels.ui.viewmodel.TileViewModel
import com.android.systemui.res.R

internal val ColorOsGap = 18.dp
private val ColorOsPillHeight = 68.dp
private val ColorOsModuleHeight = 156.dp
private val ColorOsMaxModuleHeight = 220.dp
private const val ColorOsSliderAspect = 2.1f
private val ColorOsTileSize = 70.dp
private val ColorOsTileRowHeight = 105.dp
private val ColorOsMediaCorner = 28.dp
private const val ColorOsColumns = 4
private const val ColorOsRows = 3

private val ColorOsBlue = Color(0xFF2F6BFF)
private val ColorOsRed = Color(0xFFE5483D)
private val ColorOsGreen = Color(0xFF21B04B)
private val ColorOsInk = Color(0xFF1C1C1E)
private val ColorOsActiveFill = Color(0xF2FFFFFF)
private val ColorOsLabel = Color(0xE6FFFFFF)
private val ColorOsSubLabel = Color(0x99FFFFFF)
private val ColorOsPill = RoundedCornerShape(percent = 50)

private val ColorOsWifiSpecs = listOf("wifi", "internet")
private const val ColorOsBluetoothSpec = "bt"
private const val ColorOsSimSpec = "cell"

fun colorOsCardSpecs(tiles: List<TileViewModel>): Set<String> {
    val present = tiles.map { it.spec.spec }.toSet()
    return buildSet {
        ColorOsWifiSpecs.firstOrNull { it in present }?.let(::add)
        if (ColorOsBluetoothSpec in present) add(ColorOsBluetoothSpec)
        colorOsThirdCardSpec(present)?.let(::add)
    }
}

private fun colorOsThirdCardSpec(present: Set<String>): String? =
    if (ColorOsSimSpec in present) ColorOsSimSpec
    else ColorOsFallbackCardSpecs.firstOrNull { it in present }

private val ColorOsFallbackCardSpecs = listOf("dnd", "dark", "rotation", "hotspot", "cast", "airplane")

private fun colorOsActiveTint(spec: String): Color =
    when (spec) {
        "wifi", "internet", "bt", "cast", "hotspot", "nfc" -> ColorOsBlue
        "cell" -> ColorOsGreen
        "dnd", "dark", "night" -> ColorOsInk
        else -> ColorOsRed
    }

@Composable
private fun ColorOsListen(tiles: List<TileViewModel>) {
    var listening by remember { mutableStateOf(false) }
    LifecycleStartEffect(Unit) {
        listening = true
        onStopOrDispose { listening = false }
    }
    TileListener(tiles) { listening }
}

@Composable
fun ColorOsTopRows(
    tiles: List<TileViewModel>,
    media: (@Composable () -> Unit)?,
    sliders: @Composable (width: Dp, height: Dp) -> Unit,
    modifier: Modifier = Modifier,
) {
    val bySpec = remember(tiles) { tiles.associateBy { it.spec.spec } }
    val wifi = ColorOsWifiSpecs.firstNotNullOfOrNull { bySpec[it] }
    val bluetooth = bySpec[ColorOsBluetoothSpec]
    val sim = colorOsThirdCardSpec(bySpec.keys)?.let { bySpec[it] }
    ColorOsListen(listOfNotNull(wifi, bluetooth, sim))
    BoxWithConstraints(modifier.fillMaxWidth()) {
        val half = (maxWidth - ColorOsGap) / 2
        val fit = (half - ColorOsGap) / 2
        val moduleHeight =
            (fit * ColorOsSliderAspect).coerceIn(ColorOsModuleHeight, ColorOsMaxModuleHeight)
        val sliderWidth = minOf(fit, moduleHeight / ColorOsSliderAspect)
        Column(verticalArrangement = spacedBy(ColorOsGap)) {
            Row(Modifier.cosModuleReveal(1).fillMaxWidth().height(ColorOsPillHeight), spacedBy(ColorOsGap)) {
                Box(Modifier.weight(1f).fillMaxHeight()) { wifi?.let { ColorOsConnectionPill(it) } }
                Box(Modifier.weight(1f).fillMaxHeight()) {
                    bluetooth?.let { ColorOsConnectionPill(it) }
                }
            }
            Row(Modifier.cosModuleReveal(2).fillMaxWidth().height(moduleHeight), spacedBy(ColorOsGap)) {
                Box(Modifier.weight(1f).fillMaxHeight()) { media?.invoke() ?: ColorOsIdleMedia() }
                Box(Modifier.weight(1f).fillMaxHeight()) { sliders(sliderWidth, moduleHeight) }
            }
            Row(Modifier.cosModuleReveal(3).fillMaxWidth().height(ColorOsPillHeight), spacedBy(ColorOsGap)) {
                ColorOsRingerPill(Modifier.weight(1f).fillMaxHeight())
                Box(Modifier.weight(1f).fillMaxHeight()) { sim?.let { ColorOsConnectionPill(it) } }
            }
        }
    }
}

@Composable
private fun ColorOsConnectionPill(tile: TileViewModel) {
    val (uiState, icon) = rememberTileState(tile)
    val active = uiState.visualState == Tile.STATE_ACTIVE
    Row(
        modifier =
            Modifier.fillMaxSize()
                .cosGlass(ColorOsPill)
                .combinedClickable(
                    onClick = { tile.primaryAction(uiState) },
                    onLongClick = { tile.settingsClick(null) },
                )
                .padding(start = 10.dp, end = 14.dp),
        horizontalArrangement = spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ColorOsCircle(icon, active, 46.dp, colorOsActiveTint(tile.spec.spec))
        Column(Modifier.weight(1f)) {
            Text(
                text = uiState.label,
                color = ColorOsLabel,
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (uiState.secondaryLabel.isNotBlank()) {
                Text(
                    text = uiState.secondaryLabel,
                    color = ColorOsSubLabel,
                    fontSize = 13.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun ColorOsCircle(icon: Icon, active: Boolean, size: Dp, activeTint: Color) {
    Box(
        modifier =
            Modifier.size(size)
                .cosGlass(CircleShape, active)
                .then(if (active) Modifier.background(ColorOsActiveFill, CircleShape) else Modifier),
        contentAlignment = Alignment.Center,
    ) {
        SmallTileContent(
            iconProvider = { icon },
            color = if (active) activeTint else Color.White,
            size = { size * 0.42f },
        )
    }
}

@Composable
private fun ColorOsVectorCircle(
    vector: ImageVector,
    selected: Boolean,
    size: Dp,
    onClick: () -> Unit,
) {
    Box(
        modifier =
            Modifier.size(size)
                .clip(CircleShape)
                .then(
                    if (selected) Modifier.background(ColorOsActiveFill, CircleShape)
                    else Modifier
                )
                .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        MaterialIcon(
            imageVector = vector,
            contentDescription = null,
            tint = if (selected) ColorOsRed else ColorOsSubLabel,
            modifier = Modifier.size(size * 0.46f),
        )
    }
}

@Composable
private fun ColorOsRingerPill(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val audio = remember(context) { context.getSystemService(AudioManager::class.java) }
    var mode by remember { mutableIntStateOf(audio.ringerModeInternal) }
    DisposableEffect(context) {
        val receiver =
            object : BroadcastReceiver() {
                override fun onReceive(context: Context?, intent: Intent?) {
                    mode = audio.ringerModeInternal
                }
            }
        context.registerReceiver(
            receiver,
            IntentFilter(AudioManager.INTERNAL_RINGER_MODE_CHANGED_ACTION),
            Context.RECEIVER_NOT_EXPORTED,
        )
        onDispose { context.unregisterReceiver(receiver) }
    }
    val set = { value: Int ->
        audio.ringerModeInternal = value
        mode = value
    }
    Row(
        modifier = modifier.cosGlass(ColorOsPill).padding(horizontal = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ColorOsVectorCircle(Icons.Filled.Notifications, mode == AudioManager.RINGER_MODE_NORMAL, 46.dp) {
            set(AudioManager.RINGER_MODE_NORMAL)
        }
        ColorOsVectorCircle(Icons.Filled.Vibration, mode == AudioManager.RINGER_MODE_VIBRATE, 46.dp) {
            set(AudioManager.RINGER_MODE_VIBRATE)
        }
        ColorOsVectorCircle(Icons.Filled.NotificationsOff, mode == AudioManager.RINGER_MODE_SILENT, 46.dp) {
            set(AudioManager.RINGER_MODE_SILENT)
        }
    }
}

@Composable
private fun ColorOsIdleMedia() {
    Column(
        modifier =
            Modifier.fillMaxSize()
                .cosGlass(RoundedCornerShape(ColorOsMediaCorner))
                .padding(16.dp),
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        Box(
            modifier =
                Modifier.size(56.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color.White.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center,
        ) {
            MaterialIcon(
                imageVector = Icons.Filled.MusicNote,
                contentDescription = null,
                tint = ColorOsSubLabel,
            )
        }
        Text(
            text = stringResource(R.string.harmony_not_playing),
            color = ColorOsLabel,
            fontSize = 16.sp,
            fontWeight = FontWeight.Medium,
        )
    }
}

@Composable
fun ColorOsMediaFrame(content: @Composable () -> Unit) {
    Box(Modifier.fillMaxSize().clip(RoundedCornerShape(ColorOsMediaCorner))) { content() }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ColorOsTileGrid(tiles: List<TileViewModel>, modifier: Modifier = Modifier) {
    ColorOsListen(tiles)
    val pages = remember(tiles) { tiles.chunked(ColorOsColumns * ColorOsRows) }
    if (pages.isEmpty()) return
    val pager = rememberPagerState { pages.size }
    Column(modifier.cosModuleReveal(4).fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        HorizontalPager(
            state = pager,
            modifier = Modifier.fillMaxWidth().height(ColorOsTileRowHeight * ColorOsRows),
            verticalAlignment = Alignment.Top,
        ) { page ->
            Column(Modifier.fillMaxWidth()) {
                pages[page].chunked(ColorOsColumns).forEach { row ->
                    Row(Modifier.fillMaxWidth().height(ColorOsTileRowHeight)) {
                        row.forEach { ColorOsTile(it, Modifier.weight(1f)) }
                        repeat(ColorOsColumns - row.size) { Spacer(Modifier.weight(1f)) }
                    }
                }
            }
        }
        if (pages.size > 1) {
            Row(Modifier.padding(top = 12.dp), horizontalArrangement = spacedBy(8.dp)) {
                repeat(pages.size) { index ->
                    Box(
                        Modifier.size(6.dp)
                            .clip(CircleShape)
                            .background(
                                if (index == pager.currentPage) Color.White
                                else Color.White.copy(alpha = 0.35f)
                            )
                    )
                }
            }
        }
    }
}

@Composable
private fun ColorOsTile(tile: TileViewModel, modifier: Modifier = Modifier) {
    val (uiState, icon) = rememberTileState(tile)
    val active = uiState.visualState == Tile.STATE_ACTIVE
    val unavailable = uiState.visualState == Tile.STATE_UNAVAILABLE
    Column(
        modifier =
            modifier
                .fillMaxHeight()
                .alpha(if (unavailable) 0.45f else 1f)
                .combinedClickable(
                    onClick = { tile.primaryAction(uiState) },
                    onLongClick = { tile.settingsClick(null) },
                ),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        ColorOsCircle(icon, active, ColorOsTileSize, colorOsActiveTint(tile.spec.spec))
        Text(
            text = uiState.label,
            color = ColorOsLabel,
            fontSize = 12.sp,
            lineHeight = 14.sp,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 7.dp, start = 2.dp, end = 2.dp),
        )
    }
}
