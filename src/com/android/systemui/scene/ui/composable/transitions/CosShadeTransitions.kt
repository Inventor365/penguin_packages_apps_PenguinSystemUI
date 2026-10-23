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
import androidx.compose.ui.unit.dp
import kotlin.math.PI

fun cosSpring(response: Double, durationScale: Double): SpringSpec<Float> =
    spring(
        dampingRatio = 1f,
        stiffness = ((2 * PI / (response * durationScale)).let { it * it }).toFloat(),
        visibilityThreshold = Spring.DefaultDisplacementThreshold,
    )

fun TransitionBuilder.cosToQuickSettingsShadeTransition(durationScale: Double = 1.0) {
    spec = cosSpring(COS_PANEL_RESPONSE, durationScale)

    fractionRange(end = .86f) { translate(QuickSettingsShade.Elements.Panel, y = -CosPanelDrop) }
    fractionRange(end = .33f) { fade(OverlayShade.Elements.Scrim) }
    fractionRange(start = .5f) {
        fade(QuickSettingsShade.Elements.StatusBar)
        fade(QuickSettingsShade.Elements.Header)
    }
}

fun TransitionBuilder.cosFromQuickSettingsShadeTransition(durationScale: Double = 1.0) {
    spec = cosSpring(COS_PANEL_RESPONSE, durationScale)

    translate(QuickSettingsShade.Elements.Panel, y = -CosPanelDrop)
    fractionRange(end = .5f) {
        fade(QuickSettingsShade.Elements.StatusBar)
        fade(QuickSettingsShade.Elements.Header)
    }
    fractionRange(start = .67f) { fade(OverlayShade.Elements.Scrim) }
}

private const val COS_PANEL_RESPONSE = 0.314
private val CosPanelDrop = 38.dp
