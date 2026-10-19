/*
 * Copyright (C) 2026 The PenguinOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package com.android.systemui.qs.composefragment

import android.content.Context
import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.View
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import java.lang.reflect.Method
import java.lang.reflect.Constructor
import java.util.function.BiConsumer
import java.util.function.Consumer

object CosHooks {
    const val SETTING = "penguin_cos_experience"

    private val slider: Class<*>? by lazy {
        runCatching { Class.forName("com.penguin.cos.systemui.CosBrightnessSlider") }.getOrNull()
    }
    private val create: Method? by lazy {
        slider?.getMethod("create", Context::class.java, BiConsumer::class.java)
    }
    private val setFraction: Method? by lazy {
        slider?.getMethod("setFraction", View::class.java, Float::class.javaPrimitiveType)
    }

    private val spring: Class<*>? by lazy {
        runCatching { Class.forName("com.penguin.cos.systemui.CosSpring") }.getOrNull()
    }
    private val springNew: Constructor<*>? by lazy {
        spring?.getConstructor(Float::class.javaPrimitiveType, Consumer::class.java)
    }
    private val springTo: Method? by lazy {
        spring?.getMethod("animateTo", Float::class.javaPrimitiveType,
            Float::class.javaPrimitiveType, Float::class.javaPrimitiveType)
    }
    private val springSnap: Method? by lazy {
        spring?.getMethod("snapTo", Float::class.javaPrimitiveType)
    }

    val available: Boolean
        get() = create != null

    class Spring internal constructor(private val handle: Any) {
        fun animateTo(target: Float, response: Float, bounce: Float = 0f) {
            springTo!!.invoke(handle, target, response, bounce)
        }

        fun snapTo(value: Float) {
            springSnap!!.invoke(handle, value)
        }
    }

    fun newSpring(start: Float, onUpdate: (Float) -> Unit): Spring? =
        springNew?.newInstance(start, Consumer<Float> { onUpdate(it) })?.let { Spring(it) }

    fun createBrightnessSlider(context: Context, onChange: (Float, Boolean) -> Unit): View =
        create!!.invoke(null, context, BiConsumer<Float, Boolean> { f, done -> onChange(f, done) })
            as View

    fun setBrightnessFraction(view: View, fraction: Float) {
        setFraction?.invoke(null, view, fraction)
    }
}

@Composable
fun rememberCosExperience(): Boolean {
    if (!CosHooks.available) return false
    val context = LocalContext.current
    val resolver = context.contentResolver
    var on by remember {
        mutableStateOf(Settings.Secure.getInt(resolver, CosHooks.SETTING, 0) == 1)
    }
    DisposableEffect(resolver) {
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) {
                on = Settings.Secure.getInt(resolver, CosHooks.SETTING, 0) == 1
            }
        }
        resolver.registerContentObserver(
            Settings.Secure.getUriFor(CosHooks.SETTING), false, observer)
        onDispose { resolver.unregisterContentObserver(observer) }
    }
    return on
}
