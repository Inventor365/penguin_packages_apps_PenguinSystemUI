/*
 * Copyright (C) 2026 The PenguinOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package com.android.systemui.qs.composefragment

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.unit.Dp
import com.android.systemui.penguin.CosFeature
import com.android.systemui.penguin.rememberCosFeature

private const val SWEEP_MS = 1200

@Composable
fun Modifier.cosStatusGlow(
    trigger: Any?,
    cornerRadius: Dp,
    capsule: Boolean = false,
    startAngle: Float = 0.25f,
): Modifier {
    if (!rememberCosFeature(CosFeature.MediaLights)) return this
    val glow = remember { CosHooks.newStrokeGlow() } ?: return this
    val progress = remember { Animatable(1f) }

    LaunchedEffect(trigger) {
        if (trigger == null) return@LaunchedEffect
        glow.setStartAngle(startAngle)
        progress.snapTo(0f)
        progress.animateTo(1f, tween(SWEEP_MS, easing = LinearEasing))
    }

    return drawWithContent {
        drawContent()
        val p = progress.value
        if (p < 1f) {
            glow.setProgress(p)
            drawIntoCanvas {
                glow.draw(
                    it.nativeCanvas,
                    size.width.toInt(),
                    size.height.toInt(),
                    minOf(cornerRadius.toPx(), size.minDimension / 2),
                    capsule,
                )
            }
        }
    }
}
