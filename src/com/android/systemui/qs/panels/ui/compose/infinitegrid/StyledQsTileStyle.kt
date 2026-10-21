/*
 * Copyright (C) 2026 The PenguinOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package com.android.systemui.qs.panels.ui.compose.infinitegrid

import android.os.SystemClock
import android.service.quicksettings.Tile.STATE_ACTIVE
import android.service.quicksettings.Tile.STATE_INACTIVE
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.android.compose.theme.LocalAndroidColorScheme
import com.android.systemui.common.shared.model.Icon
import com.android.systemui.penguin.QsTileStyle
import com.android.systemui.qs.panels.ui.compose.infinitegrid.CommonTileDefaults.InactiveTileCornerRadius
import com.android.systemui.qs.panels.ui.viewmodel.TileUiState
import com.android.systemui.qs.shared.style.LiquidGlassBlue
import com.android.systemui.qs.shared.style.LiquidGlassControl
import com.android.systemui.qs.shared.style.LiquidGlassGlyphs
import com.android.systemui.qs.shared.style.LiquidGlassSurface
import com.android.systemui.qs.shared.style.LiquidGlassTune
import com.android.systemui.qs.shared.style.glassBlurRegion
import com.android.systemui.qs.shared.style.isStockQsStyle
import com.android.systemui.qs.shared.style.liquidGlassEnabled
import com.android.systemui.qs.shared.style.liquidGlassOn
import com.android.systemui.qs.shared.style.liquidGlassPress
import com.android.systemui.qs.shared.style.liquidGlassRim
import com.android.systemui.res.R

@Composable
fun styledQsTileStyle(): QsTileStyle? {
    val stock = isStockQsStyle
    val glass = liquidGlassEnabled()
    if (stock && !glass) return null
    val glassTiles = liquidGlassOn && !stock
    return remember(stock, glass, glassTiles) { StyledQsTileStyle(stock, glass, glassTiles) }
}

private class StyledQsTileStyle(
    private val stock: Boolean,
    private val glass: Boolean,
    private val glassTiles: Boolean,
) : QsTileStyle {

    @Composable
    override fun colors(uiState: TileUiState, iconOnly: Boolean, stock: TileColors): TileColors =
        when {
            !this.stock -> StyledTileColors.getColorForState(uiState, iconOnly)
            glass -> StyledTileColors.stockGlassColors(uiState, iconOnly, stock)
            else -> stock
        }

    @Composable
    override fun cornerRadius(uiState: TileUiState, stock: Dp): Dp =
        if (this.stock) stock else InactiveTileCornerRadius

    override val iconHalo: Boolean = !stock

    @Composable
    override fun startPadding(): Dp =
        if (stock) CommonTileDefaults.StartPadding
        else dimensionResource(id = R.dimen.custom_qs_tile_start_padding)

    override val roundIconTiles: Boolean = glassTiles

    // The stock style keeps the tiles' own icons; the Liquid Glass glyphs belong to its tiles.
    override fun icon(spec: String, icon: Icon): Icon =
        if (stock) icon else LiquidGlassGlyphs.swap(spec, icon)

    override fun roundIconSize(tileHeight: Dp): Dp = tileHeight * LiquidGlassTune.f("icon_scale", 0.5f)

    @Composable
    override fun Container(shape: Shape, content: @Composable () -> Unit) {
        if (glass) {
            Box(Modifier.glassBlurRegion(shape).liquidGlassRim(shape)) { content() }
        } else {
            content()
        }
    }

    @Composable override fun Modifier.containerBehaviour(): Modifier =
        liquidGlassPress(glass)

    @Composable
    override fun colorSpec(): AnimationSpec<Color> {
        val shownAt = remember { SystemClock.uptimeMillis() }
        return if (SystemClock.uptimeMillis() - shownAt < TILE_SETTLE_MILLIS) snap() else spring()
    }

    override val editToggleInset: Dp = if (stock) 0.dp else 16.dp
}

private const val TILE_SETTLE_MILLIS = 500L

private object StyledTileColors {
    private const val GlassSurfaceAlpha = 0.45f
    private const val GlassIconSurfaceAlpha = GlassSurfaceAlpha
    private const val ActiveTileAlpha = 1f
    private val GlassGlyph = Color(0xFF1C1C1E)

    private val surfaceAlpha: Float
        @Composable @ReadOnlyComposable get() = if (isStockQsStyle) 1f else GlassSurfaceAlpha

    private val iconSurfaceAlpha: Float
        @Composable @ReadOnlyComposable get() =
            if (isStockQsStyle) 1f else GlassIconSurfaceAlpha

    private val activeAlpha: Float
        @Composable @ReadOnlyComposable get() = if (isStockQsStyle) 1f else ActiveTileAlpha

    @Composable
    @ReadOnlyComposable
    private fun tileSurface(): Color =
        if (liquidGlassOn && !isStockQsStyle) LiquidGlassSurface
        else LocalAndroidColorScheme.current.surfaceEffect1.copy(alpha = surfaceAlpha)

    /** An active tile uses the active color as background */
    @Composable
    @ReadOnlyComposable
    fun activeTileColors(): TileColors =
        if (liquidGlassOn && !isStockQsStyle) glassActiveTileColors()
        else TileColors(
            background = MaterialTheme.colorScheme.primary.copy(alpha = activeAlpha),
            iconBackground = MaterialTheme.colorScheme.primary.copy(alpha = activeAlpha),
            label = MaterialTheme.colorScheme.onPrimary,
            secondaryLabel = MaterialTheme.colorScheme.onPrimary,
            icon = MaterialTheme.colorScheme.onPrimary,
        )

    /** An active tile with dual target only show the active color on the icon */
    @Composable
    @ReadOnlyComposable
    fun activeDualTargetTileColors(): TileColors =
        if (liquidGlassOn && !isStockQsStyle) glassActiveTileColors()
        else TileColors(
            background =
                tileSurface(),
            iconBackground = MaterialTheme.colorScheme.primary,
            label = MaterialTheme.colorScheme.onSurface,
            secondaryLabel = MaterialTheme.colorScheme.onSurface,
            icon = MaterialTheme.colorScheme.onPrimary,
            circleAroundIcon = MaterialTheme.colorScheme.primary,
        )

    @Composable
    @ReadOnlyComposable
    fun inactiveDualTargetTileColors(): TileColors =
        TileColors(
            background =
                tileSurface(),
            iconBackground = Color.Transparent,
            label = MaterialTheme.colorScheme.onSurface,
            secondaryLabel = MaterialTheme.colorScheme.onSurface,
            icon = MaterialTheme.colorScheme.onSurface,
            circleAroundIcon =
                if (liquidGlassOn && !isStockQsStyle) LiquidGlassControl
                else LocalAndroidColorScheme.current.surfaceEffect2.copy(alpha = iconSurfaceAlpha),
        )

    @Composable
    @ReadOnlyComposable
    private fun glassActiveTileColors(): TileColors =
        TileColors(
            background = tileSurface(),
            iconBackground = LiquidGlassBlue,
            label = Color.White,
            secondaryLabel = Color.White.copy(alpha = 0.6f),
            icon = Color.White,
            circleAroundIcon = LiquidGlassBlue,
        )

    @Composable
    @ReadOnlyComposable
    fun inactiveTileColors(): TileColors =
        TileColors(
            background =
                tileSurface(),
            iconBackground = Color.Transparent,
            label = MaterialTheme.colorScheme.onSurface,
            secondaryLabel = MaterialTheme.colorScheme.onSurface,
            icon = if (liquidGlassOn && !isStockQsStyle) Color.White else MaterialTheme.colorScheme.onSurface,
            circleAroundIcon =
                if (liquidGlassOn && !isStockQsStyle) LiquidGlassControl else Color.Transparent,
        )

    @Composable
    @ReadOnlyComposable
    fun unavailableTileColors(): TileColors {
        val surfaceColor =
            tileSurface()
        val onSurfaceVariantColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = .38f)
        return TileColors(
            background = surfaceColor,
            iconBackground = surfaceColor,
            label = onSurfaceVariantColor,
            secondaryLabel = onSurfaceVariantColor,
            icon = onSurfaceVariantColor,
        )
    }

    /**
     * Liquid Glass on the stock layout: the stock tiles keep their shapes and surfaces, but the
     * parts stock fills with the accent colour take Liquid Glass's two-tone look instead, white
     * with a dark glyph, like its sliders.
     */
    @Composable
    @ReadOnlyComposable
    fun stockGlassColors(uiState: TileUiState, iconOnly: Boolean, stock: TileColors): TileColors =
        when {
            uiState.visualState != STATE_ACTIVE -> stock
            uiState.handlesToggleClick && !iconOnly ->
                stock.copy(
                    iconBackground = Color.White,
                    icon = GlassGlyph,
                    circleAroundIcon = Color.White,
                )
            else ->
                stock.copy(
                    background = Color.White,
                    iconBackground = Color.White,
                    label = GlassGlyph,
                    secondaryLabel = GlassGlyph.copy(alpha = 0.7f),
                    icon = GlassGlyph,
                )
        }

    @Composable
    @ReadOnlyComposable
    fun getColorForState(uiState: TileUiState, iconOnly: Boolean): TileColors {
        return when (uiState.visualState) {
            STATE_ACTIVE -> {
                if (iconOnly && liquidGlassOn && !isStockQsStyle) {
                    TileColors(
                        background = Color.White,
                        iconBackground = Color.White,
                        label = Color.White,
                        secondaryLabel = Color.White,
                        icon = GlassGlyph,
                    )
                } else if (uiState.handlesToggleClick && !iconOnly) {
                    activeDualTargetTileColors()
                } else {
                    activeTileColors()
                }
            }

            STATE_INACTIVE -> {
                if (uiState.handlesToggleClick && !iconOnly) {
                    inactiveDualTargetTileColors()
                } else {
                    inactiveTileColors()
                }
            }

            else -> unavailableTileColors()
        }
    }
}
