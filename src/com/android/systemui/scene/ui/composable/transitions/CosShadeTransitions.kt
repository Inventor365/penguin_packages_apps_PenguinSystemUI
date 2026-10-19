/*
 * Copyright (C) 2026 The PenguinOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package com.android.systemui.scene.ui.composable.transitions

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.SpringSpec
import androidx.compose.animation.core.spring
import com.android.compose.animation.scene.TransitionBuilder
import com.android.systemui.qs.ui.composable.QuickSettingsShade
import com.android.systemui.shade.ui.composable.OverlayShade
import kotlin.math.PI

fun cosSpring(response: Double, durationScale: Double): SpringSpec<Float> =
    spring(
        dampingRatio = 1f,
        stiffness = ((2 * PI / (response * durationScale)).let { it * it }).toFloat(),
        visibilityThreshold = Spring.DefaultDisplacementThreshold,
    )

fun TransitionBuilder.cosToQuickSettingsShadeTransition(durationScale: Double = 1.0) {
    spec = cosSpring(0.2, durationScale)

    fractionRange(end = .6f) { fade(QuickSettingsShade.Elements.Panel) }
    scaleDraw(QuickSettingsShade.Elements.Panel, scaleX = 0.8f, scaleY = 0.8f)
    fractionRange(end = .5f) { fade(OverlayShade.Elements.Scrim) }
    fractionRange(start = .5f) {
        fade(QuickSettingsShade.Elements.StatusBar)
        fade(QuickSettingsShade.Elements.Header)
    }
}

fun TransitionBuilder.cosFromQuickSettingsShadeTransition(durationScale: Double = 1.0) {
    spec = cosSpring(0.3, durationScale)

    fractionRange(end = .6f) { fade(QuickSettingsShade.Elements.Panel) }
    scaleDraw(QuickSettingsShade.Elements.Panel, scaleX = 0.8f, scaleY = 0.8f)
    fractionRange(end = .5f) {
        fade(QuickSettingsShade.Elements.StatusBar)
        fade(QuickSettingsShade.Elements.Header)
    }
    fade(OverlayShade.Elements.Scrim)
}
