/*
 * Copyright (C) 2024-2026 Lunaris AOSP
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.systemui.statusbar.quickactions.popups.ui.compose

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.android.systemui.qs.shared.style.glassBlurRegion
import com.android.systemui.qs.shared.style.liquidGlassRim
import com.android.systemui.statusbar.quickactions.popups.shared.DynamicIslandFeatureSettings.POPUP_COLOR_MODE_BLUR
import com.android.systemui.statusbar.quickactions.popups.shared.DynamicIslandFeatureSettings.POPUP_COLOR_MODE_DEFAULT
import com.android.systemui.statusbar.quickactions.popups.shared.DynamicIslandFeatureSettings.POPUP_COLOR_MODE_LIQUID_GLASS
import com.android.systemui.statusbar.quickactions.popups.shared.DynamicIslandFeatureSettings.POPUP_COLOR_MODE_SOLID_BLACK
import com.android.systemui.statusbar.quickactions.popups.shared.DynamicIslandFeatureSettings.observeDynamicIslandPopupColorMode
import com.android.systemui.statusbar.quickactions.popups.shared.DynamicIslandFeatureSettings.readEffectiveDynamicIslandPopupColorMode

@Composable
fun rememberPopupColorMode(): Int {
    val context = LocalContext.current
    val initial = remember(context) { context.contentResolver.readEffectiveDynamicIslandPopupColorMode() }
    val flow = remember(context) { observeDynamicIslandPopupColorMode(context) }
    val mode by flow.collectAsState(initial = initial)
    return mode
}

fun Modifier.dynamicIslandPillSurface(
    shape: Shape,
    outlineColor: Color,
    colorMode: Int,
): Modifier =
    when (colorMode) {
        POPUP_COLOR_MODE_LIQUID_GLASS ->
            this.clip(shape)
                .background(
                    Brush.verticalGradient(
                        0f to Color(0xDF141720),
                        0.45f to Color(0xEB0B0D12),
                        1f to Color(0xF208090D),
                    )
                )
                .liquidGlassRim(shape, strength = 0.72f)
        POPUP_COLOR_MODE_BLUR ->
            this.clip(shape)
                .background(Color(0xEC121318))
                .liquidGlassRim(shape, strength = 0.38f)
        else ->
            this.clip(shape)
                .background(Color.Black)
                .border(width = 1.dp, color = outlineColor, shape = shape)
    }

@Composable
fun PopupSurface(
    shape: Shape,
    modifier: Modifier = Modifier,
    shadowElevation: Dp = 12.dp,
    colorMode: Int = rememberPopupColorMode(),
    content: @Composable () -> Unit,
) {
    val cameraClearance = LocalPopupCameraClearance.current
    val body: @Composable () -> Unit = {
        Box(Modifier.padding(top = cameraClearance)) { content() }
    }
    val effectiveMode =
        if (colorMode == POPUP_COLOR_MODE_DEFAULT) rememberPopupColorMode() else colorMode

    when (effectiveMode) {
        POPUP_COLOR_MODE_LIQUID_GLASS -> {
            val glassGradient = remember {
                Brush.verticalGradient(
                    0.0f to Color(0x5C1A1E28),
                    0.35f to Color(0x6611141C),
                    1.0f to Color(0x780C0E14),
                )
            }
            Box(
                modifier = modifier
                    .shadow(shadowElevation, shape, clip = false)
                    .clip(shape)
                    .glassBlurRegion(shape)
                    .background(glassGradient)
                    .liquidGlassRim(shape, strength = 1.05f),
            ) {
                CompositionLocalProvider(
                    LocalContentColor provides Color.White
                ) {
                    body()
                }
            }
        }
        POPUP_COLOR_MODE_BLUR -> {
            val frostedGradient = remember {
                Brush.verticalGradient(
                    0.0f to Color(0x9422232B),
                    1.0f to Color(0xA417181E),
                )
            }
            Box(
                modifier = modifier
                    .shadow(shadowElevation, shape, clip = false)
                    .clip(shape)
                    .glassBlurRegion(shape)
                    .background(frostedGradient)
                    .liquidGlassRim(shape, strength = 0.45f),
            ) {
                CompositionLocalProvider(
                    LocalContentColor provides Color.White
                ) {
                    body()
                }
            }
        }
        POPUP_COLOR_MODE_SOLID_BLACK -> {
            Surface(
                color = Color.Black,
                contentColor = Color.White,
                shape = shape,
                shadowElevation = shadowElevation,
                modifier = modifier.liquidGlassRim(shape, strength = 0.22f),
            ) { body() }
        }
        else -> {
            Surface(
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                contentColor = MaterialTheme.colorScheme.onSurface,
                shape = shape,
                shadowElevation = shadowElevation,
                modifier = modifier,
            ) { body() }
        }
    }
}

val LocalPopupCameraClearance = compositionLocalOf { 0.dp }
