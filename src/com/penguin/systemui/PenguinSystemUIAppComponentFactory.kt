/*
 * Copyright (C) 2026 The PenguinOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package com.penguin.systemui

import android.content.Context
import com.android.systemui.SystemUIAppComponentFactoryBase

class PenguinSystemUIAppComponentFactory : SystemUIAppComponentFactoryBase() {
    override fun createSystemUIInitializer(context: Context) = PenguinSystemUIInitializer(context)
}
