/*
 * Copyright (C) 2026 The PenguinOS Project
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

package com.android.systemui.scene.ui.composable.transitions

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.SpringSpec
import androidx.compose.animation.core.spring
import androidx.compose.ui.unit.dp
import com.android.compose.animation.scene.Edge
import com.android.compose.animation.scene.ElementKey
import com.android.compose.animation.scene.FixedDistance
import com.android.compose.animation.scene.TransitionBuilder
import com.android.systemui.notifications.ui.composable.NotificationsShade
import com.android.systemui.qs.ui.composable.QuickSettingsShade

fun overlayShadeSpring(durationScale: Double = 1.0): SpringSpec<Float> =
    spring(
        dampingRatio = 0.9f,
        stiffness = (380.0 / (durationScale * durationScale)).toFloat(),
        visibilityThreshold = 0.001f,
    )

fun controlCentreSpring(durationScale: Double = 1.0): SpringSpec<Float> =
    spring(
        dampingRatio = 0.92f,
        stiffness = (380.0 / (durationScale * durationScale)).toFloat(),
        visibilityThreshold = 0.001f,
    )

fun TransitionBuilder.controlCentreReveal(panel: ElementKey) {
    fractionRange(end = 0.35f) { fade(panel) }
    scaleDraw(panel, scaleX = 0.95f, scaleY = 0.95f)
    translate(panel, y = (-24).dp)
}

fun TransitionBuilder.oxygenPanelReveal(panel: ElementKey) {
    fractionRange(end = 0.45f) { fade(panel) }
    scaleDraw(panel, scaleX = 0.94f, scaleY = 0.94f)
    translate(panel, y = (-28).dp)
}

fun TransitionBuilder.notificationsToQuickSettingsShadeTransition() {
    spec = spring(dampingRatio = 0.9f, stiffness = 320f, visibilityThreshold = 0.001f)
    distance = FixedDistance(200.dp)

    translate(NotificationsShade.Elements.Panel, Edge.Start)
    translate(QuickSettingsShade.Elements.Panel, Edge.End)
    fade(NotificationsShade.Elements.Panel)
    fade(QuickSettingsShade.Elements.Panel)
}
