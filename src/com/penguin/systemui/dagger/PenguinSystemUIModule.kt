/*
 * Copyright (C) 2026 The PenguinOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package com.penguin.systemui.dagger

import android.content.Context
import com.android.systemui.dagger.SysUISingleton
import com.android.systemui.globalactions.IosPowerMenuLayout
import com.android.systemui.penguin.DepthSubject
import com.android.systemui.penguin.PowerMenuLayout
import com.android.systemui.penguin.VolumePanelLooks
import com.android.systemui.statusbar.phone.ScrimController
import com.android.systemui.util.WallpaperDepthUtils
import com.android.systemui.volume.StyledVolumePanelLooks
import dagger.Binds
import dagger.Lazy
import dagger.Module
import dagger.Provides

@Module
interface PenguinSystemUIModule {
    @Binds fun powerMenuLayout(impl: IosPowerMenuLayout): PowerMenuLayout

    @Binds fun volumePanelLooks(impl: StyledVolumePanelLooks): VolumePanelLooks

    companion object {
        @Provides
        @SysUISingleton
        fun depthSubject(
            context: Context,
            scrimControllerLazy: Lazy<ScrimController>,
        ): DepthSubject = WallpaperDepthUtils.getInstance(context, scrimControllerLazy)
    }
}
