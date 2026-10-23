/*
 * Copyright (C) 2026 The PenguinOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package com.penguin.systemui.dagger

import android.content.Context
import com.android.systemui.dagger.SysUISingleton
import com.android.systemui.globalactions.IosPowerMenuLayout
import com.android.systemui.keyguard.ui.composable.clock.CosFluidClockFace
import com.android.systemui.keyguard.ui.composable.media.ExpandableLockscreenPlayer
import com.android.systemui.penguin.DepthSubject
import com.android.systemui.penguin.LockscreenClockFace
import com.android.systemui.penguin.LockscreenPlayer
import com.android.systemui.penguin.PowerMenuLayout
import com.android.systemui.penguin.QsPanels
import com.android.systemui.penguin.ShadeMotion
import com.android.systemui.penguin.StatusBarIsland
import com.android.systemui.penguin.VolumePanelLooks
import com.android.systemui.statusbar.phone.ScrimController
import com.android.systemui.statusbar.quickactions.dynamicisland.media.MediaControlChipModule
import com.android.systemui.statusbar.quickactions.popups.ui.binder.DynamicIsland
import com.android.systemui.util.WallpaperDepthUtils
import com.android.systemui.volume.StyledVolumePanelLooks
import dagger.Binds
import dagger.Lazy
import dagger.Module
import dagger.Provides
import com.penguin.systemui.qs.PenguinQsPanels
import com.penguin.systemui.qs.PenguinShadeMotion

@Module(includes = [MediaControlChipModule::class])
interface PenguinSystemUIModule {
    @Binds fun powerMenuLayout(impl: IosPowerMenuLayout): PowerMenuLayout

    @Binds fun volumePanelLooks(impl: StyledVolumePanelLooks): VolumePanelLooks

    @Binds fun statusBarIsland(impl: DynamicIsland): StatusBarIsland

    @Binds fun lockscreenPlayer(impl: ExpandableLockscreenPlayer): LockscreenPlayer

    @Binds fun lockscreenClockFace(impl: CosFluidClockFace): LockscreenClockFace

    @Binds @SysUISingleton fun qsPanels(impl: PenguinQsPanels): QsPanels

    @Binds @SysUISingleton fun shadeMotion(impl: PenguinShadeMotion): ShadeMotion

    companion object {
        @Provides
        @SysUISingleton
        fun depthSubject(
            context: Context,
            scrimControllerLazy: Lazy<ScrimController>,
        ): DepthSubject = WallpaperDepthUtils.getInstance(context, scrimControllerLazy)
    }
}
