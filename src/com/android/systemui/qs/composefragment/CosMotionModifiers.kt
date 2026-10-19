/*
 * Copyright (C) 2026 The PenguinOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package com.android.systemui.qs.composefragment

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

private const val STAGGER_MS = 16L
private const val MAX_STAGGER = 6

@Composable
fun Modifier.cosTileReveal(): Modifier {
    if (!rememberCosExperience()) return this
    val density = LocalDensity.current
    var scale by remember { mutableFloatStateOf(0.8f) }
    var alpha by remember { mutableFloatStateOf(0f) }
    var order by remember { mutableIntStateOf(-1) }
    val scaleSpring = remember { CosHooks.newSpring(0.8f) { scale = it } }
    val alphaSpring = remember { CosHooks.newSpring(0f) { alpha = it } }
    if (scaleSpring == null || alphaSpring == null) return this

    LaunchedEffect(order) {
        if (order < 0) return@LaunchedEffect
        val step = order.coerceAtMost(MAX_STAGGER)
        scaleSpring.snapTo(0.8f - step * 0.05f)
        alphaSpring.snapTo(0f)
        delay(step * STAGGER_MS)
        scaleSpring.animateTo(1f, response = 0.3f, bounce = 0.25f)
        alphaSpring.animateTo(1f, response = 0.35f)
    }

    return this
        .onPlaced { coords ->
            if (order < 0) {
                val pos = coords.positionInRoot()
                with(density) {
                    val row = (pos.y / 72.dp.toPx()).toInt()
                    val col = (pos.x / 90.dp.toPx()).toInt()
                    order = row + col
                }
            }
        }
        .graphicsLayer {
            scaleX = scale
            scaleY = scale
            this.alpha = alpha
        }
}
