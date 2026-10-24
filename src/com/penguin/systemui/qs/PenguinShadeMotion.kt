/*
 * Copyright (C) 2026 The PenguinOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package com.penguin.systemui.qs

import androidx.compose.foundation.OverscrollFactory
import androidx.compose.runtime.Composable
import com.android.compose.animation.scene.SceneTransitionsBuilder
import com.android.compose.animation.scene.TransitionBuilder
import com.android.compose.gesture.effect.rememberControlCentreOverscrollEffectFactory
import com.android.systemui.penguin.CosFeature
import com.android.systemui.penguin.CosShadeSwitch
import com.android.systemui.penguin.ShadeMotion
import com.android.systemui.penguin.rememberCosFeature
import com.android.systemui.qs.composefragment.effectiveQsPanelStyle
import com.android.systemui.qs.shared.style.QsPanelStyle
import com.android.systemui.scene.shared.model.Overlays
import com.android.systemui.scene.ui.composable.transitions.cosFromQuickSettingsShadeTransition
import com.android.systemui.scene.ui.composable.transitions.cosNotificationsToQuickSettingsSwitch
import com.android.systemui.scene.ui.composable.transitions.cosSpring
import com.android.systemui.scene.ui.composable.transitions.cosToQuickSettingsShadeTransition
import com.android.systemui.scene.ui.composable.transitions.notificationsToQuickSettingsShadeTransition
import com.android.systemui.scene.ui.composable.transitions.styledFromQuickSettingsShadeTransition
import com.android.systemui.scene.ui.composable.transitions.styledQuickSettingsToShadeTransition
import com.android.systemui.scene.ui.composable.transitions.styledShadeToQuickSettingsTransition
import com.android.systemui.scene.ui.composable.transitions.styledToNotificationsShadeTransition
import com.android.systemui.scene.ui.composable.transitions.styledToQuickSettingsShadeTransition
import javax.inject.Inject

class PenguinShadeMotion @Inject constructor() : ShadeMotion {
    private var defaultQsStyle = false
    private var cos = false

    @Composable
    override fun key(): Any? {
        defaultQsStyle = effectiveQsPanelStyle().stockLayout
        cos = rememberCosFeature(CosFeature.Shade)
        return defaultQsStyle to cos
    }

    override fun TransitionBuilder.shadeToQuickSettings(animateQsTilesAsShared: () -> Boolean) {
        styledShadeToQuickSettingsTransition(
            animateQsTilesAsShared = animateQsTilesAsShared,
            defaultQsStyle = defaultQsStyle,
        )
    }

    override fun TransitionBuilder.quickSettingsToShade(animateQsTilesAsShared: () -> Boolean) {
        styledQuickSettingsToShadeTransition(
            animateQsTilesAsShared = animateQsTilesAsShared,
            defaultQsStyle = defaultQsStyle,
        )
    }

    override fun TransitionBuilder.toQuickSettingsShade(durationScale: Double) {
        if (cos) cosToQuickSettingsShadeTransition(durationScale)
        else styledToQuickSettingsShadeTransition(durationScale = durationScale)
    }

    override fun TransitionBuilder.fromQuickSettingsShade() {
        if (cos) cosFromQuickSettingsShadeTransition() else styledFromQuickSettingsShadeTransition()
    }

    override fun TransitionBuilder.toNotificationsShade(
        durationScale: Double,
        enableSharedElements: Boolean,
    ) {
        styledToNotificationsShadeTransition(
            durationScale = durationScale,
            enableSharedElements = enableSharedElements,
        )
        if (cos) spec = cosSpring(0.2, durationScale)
    }

    override fun SceneTransitionsBuilder.overlaySwitchTransitions() {
        from(
            Overlays.NotificationsShade,
            to = Overlays.QuickSettingsShade,
            key = CosShadeSwitch,
        ) {
            cosNotificationsToQuickSettingsSwitch()
        }
        from(
            Overlays.QuickSettingsShade,
            to = Overlays.NotificationsShade,
            key = CosShadeSwitch,
        ) {
            reversed { cosNotificationsToQuickSettingsSwitch() }
        }
        from(Overlays.NotificationsShade, to = Overlays.QuickSettingsShade) {
            notificationsToQuickSettingsShadeTransition()
        }
        from(Overlays.QuickSettingsShade, to = Overlays.NotificationsShade) {
            reversed { notificationsToQuickSettingsShadeTransition() }
        }
    }

    @Composable
    override fun quickSettingsShadeOverscroll(): OverscrollFactory =
        rememberControlCentreOverscrollEffectFactory()
}
