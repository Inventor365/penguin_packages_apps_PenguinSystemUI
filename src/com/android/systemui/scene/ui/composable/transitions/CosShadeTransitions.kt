/*
 * Copyright (C) 2026 The PenguinOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package com.android.systemui.scene.ui.composable.transitions

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.SpringSpec
import androidx.compose.animation.core.spring
import androidx.compose.ui.unit.dp
import com.android.compose.animation.scene.Edge
import com.android.compose.animation.scene.ElementKey
import com.android.compose.animation.scene.TransitionBuilder
import com.android.systemui.notifications.ui.composable.NotificationsShade
import com.android.systemui.penguin.PANEL_SWITCH_FADE_IN_START
import com.android.systemui.penguin.PANEL_SWITCH_FADE_OUT_END
import com.android.systemui.qs.ui.composable.QuickSettingsShade
import com.android.systemui.shade.ui.composable.OverlayShade
import kotlin.math.PI

fun cosSpring(response: Double, durationScale: Double): SpringSpec<Float> =
    spring(
        dampingRatio = 1f,
        stiffness = ((2 * PI / (response * durationScale)).let { it * it }).toFloat(),
        visibilityThreshold = Spring.DefaultDisplacementThreshold,
    )

fun TransitionBuilder.cosToQuickSettingsShadeTransition(
    durationScale: Double = 1.0,
    fadePanel: Boolean = false,
) {
    spec = cosSpring(COS_PANEL_RESPONSE, durationScale)

    fractionRange(end = .86f) { translate(QuickSettingsShade.Elements.Panel, y = -CosPanelDrop) }
    // The COS panel reveals its own modules; other panels fade and grow in as they drop instead
    // of appearing at once.
    if (fadePanel) {
        fractionRange(end = PANEL_REVEAL_FADE_END) { fade(QuickSettingsShade.Elements.Panel) }
        scaleDraw(
            QuickSettingsShade.Elements.Panel,
            scaleX = PANEL_FADE_SCALE,
            scaleY = PANEL_FADE_SCALE,
        )
    }
    fractionRange(end = .33f) { fade(OverlayShade.Elements.Scrim) }
    fractionRange(start = .5f) {
        fade(QuickSettingsShade.Elements.StatusBar)
        fade(QuickSettingsShade.Elements.Header)
    }
}

fun TransitionBuilder.cosFromQuickSettingsShadeTransition(
    durationScale: Double = 1.0,
    fadePanel: Boolean = false,
) {
    // The COS panel hides its own modules; other panels would otherwise stay opaque until the
    // shade is removed and vanish at once. They rise, shrink a little and fade out with their glass
    // across the whole dismiss instead, on a softer spring.
    spec = cosSpring(if (fadePanel) PANEL_FADE_RESPONSE else COS_PANEL_RESPONSE, durationScale)

    translate(QuickSettingsShade.Elements.Panel, y = -CosPanelDrop)
    if (fadePanel) {
        scaleDraw(
            QuickSettingsShade.Elements.Panel,
            scaleX = PANEL_FADE_SCALE,
            scaleY = PANEL_FADE_SCALE,
        )
        fade(QuickSettingsShade.Elements.Panel)
    }
    fractionRange(end = .5f) {
        fade(QuickSettingsShade.Elements.StatusBar)
        fade(QuickSettingsShade.Elements.Header)
    }
    fractionRange(start = .67f) { fade(OverlayShade.Elements.Scrim) }
}

fun TransitionBuilder.cosNotificationsToQuickSettingsSwitch() {
    spec =
        spring(
            dampingRatio = 1f - SWITCH_BOUNCE,
            stiffness = (2 * PI / SWITCH_RESPONSE).let { it * it }.toFloat(),
            visibilityThreshold = Spring.DefaultDisplacementThreshold,
        )
    releaseSpec = spec
    translate(NotificationsShade.Elements.Panel, Edge.Start)
    translate(NotificationsShade.Elements.StatusBar, Edge.Start)
    translate(QuickSettingsShade.Elements.Panel, Edge.End)
    translate(QuickSettingsShade.Elements.StatusBar, Edge.End)
}

/**
 * Side-by-side panels don't slide into each other's place: the panel being left sinks towards the
 * bottom as it fades out, and the other one fades in where it sits.
 */
fun TransitionBuilder.cosPanelSwitchFade(
    fromPanel: ElementKey,
    fromStatusBar: ElementKey,
    toPanel: ElementKey,
    toStatusBar: ElementKey,
) {
    spec = cosSpring(PANEL_SWITCH_RESPONSE, durationScale = 1.0)
    releaseSpec = spec
    translate(fromPanel, y = PanelSwitchDrop)
    fractionRange(end = PANEL_SWITCH_FADE_OUT_END) { fade(fromPanel) }
    fractionRange(start = PANEL_SWITCH_FADE_IN_START) { fade(toPanel) }
    fade(fromStatusBar)
    fade(toStatusBar)
}

private const val COS_PANEL_RESPONSE = 0.314
private const val PANEL_FADE_RESPONSE = 0.4
private const val PANEL_FADE_SCALE = 0.96f
private const val PANEL_REVEAL_FADE_END = 0.6f

private const val SWITCH_RESPONSE = 0.2
private const val SWITCH_BOUNCE = 0.05f
private val CosPanelDrop = 38.dp

private const val PANEL_SWITCH_RESPONSE = 0.3
private val PanelSwitchDrop = 56.dp
