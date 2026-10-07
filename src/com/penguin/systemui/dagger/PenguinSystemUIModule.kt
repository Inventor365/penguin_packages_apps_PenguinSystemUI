/*
 * Copyright (C) 2026 The PenguinOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package com.penguin.systemui.dagger

import com.android.systemui.globalactions.IosPowerMenuLayout
import com.android.systemui.penguin.PowerMenuLayout
import com.android.systemui.penguin.VolumePanelLooks
import com.android.systemui.volume.StyledVolumePanelLooks
import dagger.Binds
import dagger.Module

@Module
interface PenguinSystemUIModule {
    @Binds fun powerMenuLayout(impl: IosPowerMenuLayout): PowerMenuLayout

    @Binds fun volumePanelLooks(impl: StyledVolumePanelLooks): VolumePanelLooks
}
