/*
 * Copyright (C) 2026 The PenguinOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package com.penguin.systemui

import android.content.Context
import com.android.systemui.SystemUIInitializer
import com.android.systemui.dagger.GlobalRootComponent
import com.penguin.systemui.dagger.DaggerPenguinGlobalRootComponent

class PenguinSystemUIInitializer(context: Context) : SystemUIInitializer(context) {
    override fun getGlobalRootComponentBuilder(): GlobalRootComponent.Builder =
        DaggerPenguinGlobalRootComponent.builder()
}
