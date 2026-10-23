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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.dynamicanimation.animation.FloatValueHolder
import androidx.dynamicanimation.animation.SpringAnimation
import androidx.dynamicanimation.animation.SpringForce
import com.android.systemui.qs.shared.style.GlassBackdrop
import com.penguin.systemui.light.CardLight
import com.penguin.systemui.light.EdgeSweep
import com.penguin.systemui.light.GlassRim
import com.penguin.systemui.light.SeekLight
import kotlin.math.PI
import kotlin.math.pow

object CosHooks {
    const val SETTING = "penguin_cos_experience"

    @Suppress("UNUSED_PARAMETER")
    fun createMediaLight(context: Context): Drawable = CardLight()

    fun setMediaLightCorner(light: Drawable, radius: Float) =
        (light as CardLight).setCornerRadius(radius)

    fun applyMediaLight(light: Drawable, color: Int, night: Boolean) =
        (light as CardLight).setColor(color, night)

    fun applyMediaLightSpread(light: Drawable, x: Float, y: Float) =
        (light as CardLight).playRipple(x, y)

    fun clearMediaLight(light: Drawable) = (light as CardLight).clear()

    class StrokeGlow internal constructor(private val sweep: EdgeSweep) {
        fun setStartAngle(angle: Float) = sweep.setStartAngle(angle)

        fun setProgress(progress: Float) = sweep.setProgress(progress)

        fun draw(canvas: Canvas, width: Int, height: Int, cornerRadius: Float, weight: Float) =
            sweep.draw(canvas, width, height, cornerRadius, capsule = weight < 2f)
    }

    fun newStrokeGlow(): StrokeGlow? = runCatching { StrokeGlow(EdgeSweep()) }.getOrNull()

    class ConicLight internal constructor(private val light: SeekLight) {
        fun setColor(color: Int) = light.setColor(color)

        fun setShape(
            height: Float,
            heightFalloff: Float,
            coneK: Float,
            secondConeK: Float,
            w0: Float,
            alpha1: Float,
            alpha2: Float,
        ) = light.setShape(SeekLight.Shape(height, heightFalloff, coneK, secondConeK, w0, alpha1, alpha2))

        fun draw(
            canvas: Canvas,
            progressStart: Float,
            centerY: Float,
            length: Float,
            fraction: Float,
            rtl: Boolean,
        ) = light.draw(canvas, progressStart, centerY, length, fraction, rtl)
    }

    fun newConicLight(): ConicLight? = runCatching { ConicLight(SeekLight()) }.getOrNull()

    fun createRim(): RuntimeShader? = runCatching { GlassRim.create() }.getOrNull()

    fun updateRim(
        shader: RuntimeShader,
        width: Float,
        height: Float,
        radius: Float,
        active: Boolean,
        dark: Boolean,
    ) = GlassRim.update(shader, width, height, radius, active, dark)

    class Spring internal constructor(start: Float, onUpdate: (Float) -> Unit) {
        private val holder = FloatValueHolder(start)
        private val animation =
            SpringAnimation(holder).apply {
                spring = SpringForce().setDampingRatio(1f)
                setMinimumVisibleChange(0.001f)
                addUpdateListener { _, value, _ -> onUpdate(value) }
            }
        private val update = onUpdate

        fun animateTo(target: Float, response: Float, bounce: Float = 0f) {
            animation.spring
                .setStiffness((2 * PI / response).pow(2).toFloat())
                .setDampingRatio(1f - bounce)
            if (!animation.isRunning) animation.setStartValue(holder.value)
            animation.animateToFinalPosition(target)
        }

        fun snapTo(value: Float) {
            animation.cancel()
            holder.value = value
            update(value)
        }
    }

    fun newSpring(start: Float, onUpdate: (Float) -> Unit): Spring = Spring(start, onUpdate)
}

@Composable
fun rememberCosExperience(): Boolean {
    val resolver = LocalContext.current.contentResolver
    var on by remember {
        mutableStateOf(Settings.Secure.getInt(resolver, CosHooks.SETTING, 0) == 1)
    }
    DisposableEffect(resolver) {
        val observer =
            object : ContentObserver(Handler(Looper.getMainLooper())) {
                override fun onChange(selfChange: Boolean) {
                    on = Settings.Secure.getInt(resolver, CosHooks.SETTING, 0) == 1
                }
            }
        resolver.registerContentObserver(Settings.Secure.getUriFor(CosHooks.SETTING), false, observer)
        onDispose { resolver.unregisterContentObserver(observer) }
    }
    SideEffect { GlassBackdrop.cos.value = on }
    return on
}
