/*
 * Copyright (C) 2026 The PenguinOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package com.android.systemui.keyguard.ui.composable.media

import android.graphics.Bitmap
import android.graphics.RuntimeShader
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.graphics.ColorUtils
import androidx.core.graphics.drawable.toBitmap
import androidx.palette.graphics.Palette
import com.android.systemui.common.shared.model.Icon as CommonIcon
import com.android.systemui.qs.composefragment.CosHooks
import com.android.systemui.qs.shared.style.GlassBackdrop
import java.io.File
import kotlin.math.pow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private val TopBand = 186.dp
private val BottomBand = 346.dp
private const val BLUR_RADIUS = 120f
private const val BLUR_CURVE = 1.4f
private const val MASK_ALPHA = 0.4f
private const val BAND_ALPHA = 0.6f
private val GlassOverlay = Color(0x66999999)
private val GlassDodge = Color(0x4D737373)

@Composable
internal fun CosImmersiveArt(artwork: CommonIcon, video: File?, modifier: Modifier = Modifier) {
    val maskColor by
        produceState(Color.Transparent, artwork) {
            value = withContext(Dispatchers.Default) { cosMaskColor(artwork) }
        }
    var origin by remember { mutableStateOf(Offset.Zero) }
    val rims = remember { HashMap<Any, RuntimeShader?>() }
    DisposableEffect(Unit) {
        GlassBackdrop.drawing.value = true
        onDispose { GlassBackdrop.drawing.value = false }
    }
    BoxWithConstraints(modifier.fillMaxSize().onGloballyPositioned { origin = it.positionInWindow() }) {
        val width = maxWidth
        val height = maxHeight
        val base: @Composable (Modifier, File?) -> Unit = { layer, motion ->
            Box(layer.fillMaxSize()) {
                if (width > height) ArtStack(artwork, motion, height, 0.dp)
                else ArtStack(artwork, motion, width, height * 0.2f)
                Box(Modifier.fillMaxSize().background(maskColor.copy(alpha = MASK_ALPHA)))
            }
        }
        base(Modifier, video)
        Box(
            Modifier.fillMaxSize()
                .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
                .drawWithContent {
                    drawContent()
                    drawIntoCanvas { canvas ->
                        canvas.saveLayer(
                            Rect(Offset.Zero, size),
                            Paint().apply { blendMode = BlendMode.DstIn },
                        )
                        drawRect(blurMask(size.height, TopBand.toPx(), BottomBand.toPx()))
                        for (region in GlassBackdrop.regions.values) {
                            drawRoundRect(
                                Color.Black.copy(alpha = region.alpha),
                                region.bounds.topLeft - origin,
                                region.bounds.size,
                                CornerRadius(region.corner),
                            )
                        }
                        canvas.restore()
                    }
                }
        ) {
            base(
                Modifier.graphicsLayer {
                    renderEffect = BlurEffect(BLUR_RADIUS, BLUR_RADIUS, TileMode.Clamp)
                },
                null,
            )
        }
        Box(
            Modifier.align(Alignment.TopCenter)
                .fillMaxWidth()
                .height(TopBand)
                .background(
                    Brush.verticalGradient(
                        listOf(maskColor.copy(alpha = BAND_ALPHA), Color.Transparent)
                    )
                )
        )
        Box(
            Modifier.align(Alignment.BottomCenter)
                .fillMaxWidth()
                .height(BottomBand)
                .background(
                    Brush.verticalGradient(
                        listOf(Color.Transparent, maskColor.copy(alpha = BAND_ALPHA))
                    )
                )
        )
        Box(
            Modifier.fillMaxSize().drawBehind {
                val regions = GlassBackdrop.regions
                rims.keys.retainAll(regions.keys)
                for ((key, region) in regions) {
                    val rim = rims.getOrPut(key) { CosHooks.createRim() }
                    drawGlass(region, region.bounds.topLeft - origin, rim)
                }
            }
        )
    }
}

private fun DrawScope.drawGlass(region: GlassBackdrop.Region, at: Offset, rim: RuntimeShader?) {
    val size = region.bounds.size
    val shape =
        Path().apply { addRoundRect(RoundRect(Rect(at, size), CornerRadius(region.corner))) }
    clipPath(shape) {
        drawRect(GlassOverlay, at, size, alpha = region.alpha, blendMode = BlendMode.Overlay)
        drawRect(GlassDodge, at, size, alpha = region.alpha, blendMode = BlendMode.ColorDodge)
    }
    if (rim != null) {
        CosHooks.updateRim(rim, size.width, size.height, region.corner, false, true)
        translate(at.x, at.y) { drawRect(ShaderBrush(rim), size = size, alpha = region.alpha) }
    }
}

@Composable
private fun ArtStack(artwork: CommonIcon, video: File?, side: Dp, top: Dp) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        Artwork(artwork, Modifier.offset(y = top - side).size(side).graphicsLayer { scaleY = -1f })
        Box(Modifier.offset(y = top).size(side)) {
            Artwork(artwork, Modifier.fillMaxSize())
            video?.let { MotionArtVideo(it, Modifier.fillMaxSize(), crop = true) }
        }
        Artwork(artwork, Modifier.offset(y = top + side).size(side).graphicsLayer { scaleY = -1f })
    }
}

private fun blurMask(height: Float, top: Float, bottom: Float): Brush {
    val steps = 8
    val stops = ArrayList<Pair<Float, Color>>()
    for (i in 0..steps) {
        val t = i / steps.toFloat()
        stops += (t * top / height) to Color.Black.copy(alpha = (1f - t).pow(BLUR_CURVE))
    }
    for (i in 0..steps) {
        val t = i / steps.toFloat()
        stops += ((height - bottom + t * bottom) / height) to Color.Black.copy(alpha = t.pow(BLUR_CURVE))
    }
    return Brush.verticalGradient(*stops.toTypedArray())
}

private fun cosMaskColor(artwork: CommonIcon): Color {
    val drawable = (artwork as? CommonIcon.Loaded)?.drawable ?: return Color.Transparent
    val bitmap = drawable.toBitmap(36, 36, Bitmap.Config.ARGB_8888)
    val rgb = Palette.from(bitmap).generate().dominantSwatch?.rgb ?: return Color.Transparent
    val hsv = FloatArray(3)
    android.graphics.Color.colorToHSV(rgb, hsv)
    hsv[1] = maxOf(hsv[1], 0.6f)
    hsv[2] = 0.8f
    return Color(ColorUtils.setAlphaComponent(android.graphics.Color.HSVToColor(hsv), 255))
}
