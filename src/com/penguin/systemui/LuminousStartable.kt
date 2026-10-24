/*
 * Copyright (C) 2026 The PenguinOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package com.penguin.systemui

import android.content.Context
import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.os.SystemProperties
import android.provider.Settings
import com.android.systemui.CoreStartable
import javax.inject.Inject

class LuminousStartable @Inject constructor(private val context: Context) : CoreStartable {

    override fun start() {
        val resolver = context.contentResolver
        val observer =
            object : ContentObserver(Handler(Looper.getMainLooper())) {
                override fun onChange(selfChange: Boolean) = sync()
            }
        resolver.registerContentObserver(Settings.Secure.getUriFor(SETTING), false, observer)
        sync()
    }

    private fun sync() {
        val allApps = Settings.Secure.getInt(context.contentResolver, SETTING, 0) == 1
        SystemProperties.set(PROPERTY, if (allApps) "2" else "1")
    }

    private companion object {
        const val SETTING = "penguin_luminous_all_apps"
        const val PROPERTY = "persist.sys.penguin.luminous"
    }
}
