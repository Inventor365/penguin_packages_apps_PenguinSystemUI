/*
 * Copyright (C) 2026 The PenguinOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package com.android.systemui.qs.composefragment

import android.content.Context
import android.database.ContentObserver
import android.graphics.Canvas
import android.graphics.RuntimeShader
import android.graphics.drawable.Drawable
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

    private val rim: Class<*>? by lazy {
        runCatching { Class.forName("com.penguin.cos.systemui.CosRim") }.getOrNull()
    }
    private val rimCreate: Method? by lazy { rim?.getMethod("create") }
    private val rimUpdate: Method? by lazy {
        val f = Float::class.javaPrimitiveType
        val b = Boolean::class.javaPrimitiveType
        rim?.getMethod("update", RuntimeShader::class.java, f, f, f, b, b)
    }

    private val mediaLight: Class<*>? by lazy {
        runCatching { Class.forName("com.penguin.cos.systemui.CosMediaLight") }.getOrNull()
    }
    private fun mediaLightMethod(name: String, vararg types: Class<*>?): Method? =
        runCatching { mediaLight?.getMethod(name, Drawable::class.java, *types) }.getOrNull()
    private val mediaLightCreate: Method? by lazy {
        runCatching { mediaLight?.getMethod("create", Context::class.java) }.getOrNull()
    }
    private val mediaLightCorner by lazy {
        mediaLightMethod("setCorner", Float::class.javaPrimitiveType)
    }
    private val mediaLightOutline by lazy {
        mediaLightMethod(
            "applyShadowAndOutline",
            Int::class.javaPrimitiveType,
            Boolean::class.javaPrimitiveType,
        )
    }
    private val mediaLightSpread by lazy {
        mediaLightMethod("applySpread", Float::class.javaPrimitiveType, Float::class.javaPrimitiveType)
    }
    private val mediaLightClear by lazy { mediaLightMethod("clear") }

    fun createMediaLight(context: Context): Drawable? =
        mediaLightCreate?.invoke(null, context) as Drawable?

    fun setMediaLightCorner(light: Drawable, radius: Float) {
        mediaLightCorner?.invoke(null, light, radius)
    }

    fun applyMediaLight(light: Drawable, color: Int, night: Boolean) {
        mediaLightOutline?.invoke(null, light, color, night)
    }

    fun applyMediaLightSpread(light: Drawable, x: Float, y: Float) {
        mediaLightSpread?.invoke(null, light, x, y)
    }

    fun clearMediaLight(light: Drawable) {
        mediaLightClear?.invoke(null, light)
    }

    private val strokeGlow: Class<*>? by lazy {
        runCatching { Class.forName("com.penguin.cos.systemui.CosStrokeGlow") }.getOrNull()
    }
    private val strokeGlowAngle: Method? by lazy {
        runCatching { strokeGlow?.getMethod("setStartAngle", Float::class.javaPrimitiveType) }
            .getOrNull()
    }
    private val strokeGlowProgress: Method? by lazy {
        runCatching { strokeGlow?.getMethod("setProgress", Float::class.javaPrimitiveType) }
            .getOrNull()
    }
    private val strokeGlowDraw: Method? by lazy {
        runCatching {
                val i = Int::class.javaPrimitiveType
                val f = Float::class.javaPrimitiveType
                strokeGlow?.getMethod("draw", Canvas::class.java, i, i, f, f)
            }
            .getOrNull()
    }

    class StrokeGlow internal constructor(private val handle: Any) {
        fun setStartAngle(angle: Float) {
            strokeGlowAngle?.invoke(handle, angle)
        }

        fun setProgress(progress: Float) {
            strokeGlowProgress?.invoke(handle, progress)
        }

        fun draw(canvas: Canvas, width: Int, height: Int, cornerRadius: Float, weight: Float) {
            strokeGlowDraw?.invoke(handle, canvas, width, height, cornerRadius, weight)
        }
    }

    fun newStrokeGlow(): StrokeGlow? =
        runCatching { strokeGlow?.getConstructor()?.newInstance() }.getOrNull()?.let {
            StrokeGlow(it)
        }

    private val conicLight: Class<*>? by lazy {
        runCatching { Class.forName("com.penguin.cos.systemui.CosConicLight") }.getOrNull()
    }
    private fun conicMethod(name: String, vararg types: Class<*>?): Method? =
        runCatching { conicLight?.getMethod(name, *types) }.getOrNull()
    private val conicSupported by lazy { conicMethod("isSupported") }
    private val conicColor by lazy { conicMethod("setColor", Int::class.javaPrimitiveType) }
    private val conicShape by lazy {
        val f = Float::class.javaPrimitiveType
        conicMethod("setShape", f, f, f, f, f, f, f)
    }
    private val conicDraw by lazy {
        val f = Float::class.javaPrimitiveType
        conicMethod("draw", Canvas::class.java, f, f, f, f, Boolean::class.javaPrimitiveType)
    }

    class ConicLight internal constructor(private val handle: Any) {
        fun setColor(color: Int) {
            conicColor?.invoke(handle, color)
        }

        fun setShape(
            height: Float,
            heightFalloff: Float,
            coneK: Float,
            secondConeK: Float,
            w0: Float,
            alpha1: Float,
            alpha2: Float,
        ) {
            conicShape?.invoke(handle, height, heightFalloff, coneK, secondConeK, w0, alpha1, alpha2)
        }

        fun draw(
            canvas: Canvas,
            progressStart: Float,
            centerY: Float,
            length: Float,
            fraction: Float,
            rtl: Boolean,
        ) {
            conicDraw?.invoke(handle, canvas, progressStart, centerY, length, fraction, rtl)
        }
    }

    fun newConicLight(): ConicLight? =
        runCatching { conicLight?.getConstructor()?.newInstance() }
            .getOrNull()
            ?.takeIf { conicSupported?.invoke(it) == true }
            ?.let { ConicLight(it) }

    val available: Boolean
        get() = create != null

    fun createRim(): RuntimeShader? =
        runCatching { rimCreate?.invoke(null) as RuntimeShader? }.getOrNull()

    fun updateRim(
        shader: RuntimeShader,
        width: Float,
        height: Float,
        radius: Float,
        active: Boolean,
        dark: Boolean,
    ) {
        rimUpdate?.invoke(null, shader, width, height, radius, active, dark)
    }

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
