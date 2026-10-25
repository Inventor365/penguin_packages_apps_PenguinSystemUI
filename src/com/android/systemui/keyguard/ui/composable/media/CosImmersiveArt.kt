/*
 * Copyright (C) 2026 The PenguinOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package com.android.systemui.keyguard.ui.composable.media

import android.graphics.Bitmap
import android.graphics.RuntimeShader
import androidx.compose.foundation.Image
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
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalConfiguration
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

// Tablets keep the phone's proportions: the bands cover the same share of the screen and the
// cover's lower edge sits at the same height, where the bottom band's blur has taken over.
private const val TABLET_MIN_WIDTH_DP = 600
private const val TopBandFraction = 0.2f
private const val BottomBandFraction = 0.38f
private const val CoverTopFraction = 0.2f
private const val CoverBottomFraction = 0.65f
private const val SideFeather = 0.16f
private const val SeamRampFraction = 0.25f

@Composable
internal fun CosImmersiveArt(
    artwork: CommonIcon,
    video: File?,
    modifier: Modifier = Modifier,
    cover: ImageBitmap? = null,
) {
    val maskColor by
        produceState(Color.Transparent, artwork) {
            value = withContext(Dispatchers.Default) { cosMaskColor(artwork) }
        }
    val tablet = LocalConfiguration.current.smallestScreenWidthDp >= TABLET_MIN_WIDTH_DP
    var origin by remember { mutableStateOf(Offset.Zero) }
    val rims = remember { HashMap<Any, RuntimeShader?>() }
    DisposableEffect(Unit) {
        GlassBackdrop.drawing.value = true
        onDispose { GlassBackdrop.drawing.value = false }
    }
    BoxWithConstraints(modifier.fillMaxSize().onGloballyPositioned { origin = it.positionInWindow() }) {
        val width = maxWidth
        val height = maxHeight
        val topBand = if (tablet) maxOf(TopBand, height * TopBandFraction) else TopBand
        val bottomBand = if (tablet) maxOf(BottomBand, height * BottomBandFraction) else BottomBand
        // Tablet portrait: the mirrored copy under the cover would show through the bottom band's
        // gradual blur, so the blur is complete by the cover's lower edge instead.
        val coverTop = minOf(height * CoverTopFraction, height * CoverBottomFraction - width)
        val seam = if (tablet && width <= height) coverTop + width else null
        // ColorOS tints only what it blurs, the edges and the glass; the cover itself stays true.
        val base: @Composable (Modifier, File?, Boolean) -> Unit = { layer, motion, tinted ->
            Box(layer.fillMaxSize()) {
                when {
                    // Tablet landscape: the square fits the height over a full-bleed copy of
                    // itself, its sides fading into that copy instead of leaving bars.
                    tablet && width > height -> WideArt(artwork, cover, motion, height, maskColor)
                    // Wide (tablet landscape) screens fit the square to the height.
                    width > height -> ArtStack(artwork, cover, motion, height, 0.dp)
                    // Tablet portrait: lift the square so its lower edge sits where the phone's
                    // does, instead of near the bottom of the much less tall screen.
                    tablet -> ArtStack(artwork, cover, motion, width, coverTop)
                    else -> ArtStack(artwork, cover, motion, width, height * 0.2f)
                }
                if (tinted) {
                    Box(Modifier.fillMaxSize().background(maskColor.copy(alpha = MASK_ALPHA)))
                }
            }
        }
        base(Modifier, video, false)
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
                        val blurEnd = seam?.toPx() ?: size.height
                        val blurStart =
                            if (seam != null) blurEnd - size.height * SeamRampFraction
                            else size.height - bottomBand.toPx()
                        drawRect(blurMask(size.height, topBand.toPx(), blurStart, blurEnd))
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
                true,
            )
        }
        Box(
            Modifier.align(Alignment.TopCenter)
                .fillMaxWidth()
                .height(topBand)
                .background(
                    Brush.verticalGradient(
                        listOf(maskColor.copy(alpha = BAND_ALPHA), Color.Transparent)
                    )
                )
        )
        Box(
            Modifier.align(Alignment.BottomCenter)
                .fillMaxWidth()
                .height(bottomBand)
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
private fun ArtStack(artwork: CommonIcon, cover: ImageBitmap?, video: File?, side: Dp, top: Dp) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        Art(artwork, cover, Modifier.offset(y = top - side).size(side).graphicsLayer { scaleY = -1f })
        Box(Modifier.offset(y = top).size(side)) {
            Art(artwork, cover, Modifier.fillMaxSize())
            video?.let { MotionArtVideo(it, Modifier.fillMaxSize(), crop = true) }
        }
        Art(artwork, cover, Modifier.offset(y = top + side).size(side).graphicsLayer { scaleY = -1f })
    }
}

@Composable
private fun WideArt(
    artwork: CommonIcon,
    cover: ImageBitmap?,
    video: File?,
    side: Dp,
    tint: Color,
) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        Art(
            artwork,
            cover,
            Modifier.fillMaxSize().graphicsLayer {
                renderEffect = BlurEffect(BLUR_RADIUS, BLUR_RADIUS, TileMode.Clamp)
            },
        )
        Box(Modifier.fillMaxSize().background(tint.copy(alpha = MASK_ALPHA)))
        Box(
            Modifier.size(side)
                .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
                .drawWithContent {
                    drawContent()
                    drawRect(
                        Brush.horizontalGradient(
                            0f to Color.Transparent,
                            SideFeather to Color.Black,
                            1f - SideFeather to Color.Black,
                            1f to Color.Transparent,
                        ),
                        blendMode = BlendMode.DstIn,
                    )
                }
        ) {
            Art(artwork, cover, Modifier.fillMaxSize())
            video?.let { MotionArtVideo(it, Modifier.fillMaxSize(), crop = true) }
        }
    }
}

/** The store's full-size [cover] when there is one; the session's far smaller bitmap otherwise. */
@Composable
private fun Art(artwork: CommonIcon, cover: ImageBitmap?, modifier: Modifier) {
    if (cover != null) {
        Image(cover, null, modifier, contentScale = ContentScale.Crop)
    } else {
        Artwork(artwork, modifier)
    }
}

/** Blurred under [top] and from [bottomStart], fully so from [bottomEnd] down. */
private fun blurMask(height: Float, top: Float, bottomStart: Float, bottomEnd: Float): Brush {
    val steps = 8
    val stops = ArrayList<Pair<Float, Color>>()
    for (i in 0..steps) {
        val t = i / steps.toFloat()
        stops += (t * top / height) to Color.Black.copy(alpha = (1f - t).pow(BLUR_CURVE))
    }
    for (i in 0..steps) {
        val t = i / steps.toFloat()
        val y = bottomStart + t * (bottomEnd - bottomStart)
        stops += (y / height) to Color.Black.copy(alpha = t.pow(BLUR_CURVE))
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
