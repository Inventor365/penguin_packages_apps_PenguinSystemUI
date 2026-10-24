/*
 * Copyright (C) 2024 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.systemui.statusbar.quickactions.popups.ui.compose

import android.view.ViewTreeObserver
import android.view.WindowManager
import android.view.WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.changedToDownIgnoreConsumed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import com.android.systemui.qs.shared.style.LocalGlassBlurAlpha
import com.android.systemui.res.R
import com.android.systemui.statusbar.quickactions.alarm.ui.compose.AlarmPopup
import com.android.systemui.statusbar.quickactions.av.ui.compose.AvControlsChipPopup
import com.android.systemui.statusbar.quickactions.flashlight.ui.compose.FlashlightPopup
import com.android.systemui.statusbar.quickactions.livescore.ui.compose.LiveScorePopup
import com.android.systemui.statusbar.quickactions.dynamicisland.media.ui.compose.MediaControlPopup
import com.android.systemui.statusbar.quickactions.dynamicisland.media.ui.compose.LyricsCard
import com.android.systemui.statusbar.quickactions.popups.ui.model.PopupChipId
import com.android.systemui.statusbar.quickactions.popups.ui.model.PopupChipModel
import com.android.systemui.statusbar.quickactions.popups.ui.model.PopupContentModel
import com.android.systemui.statusbar.quickactions.screenrecord.ui.compose.ScreenRecordPopup
import com.android.systemui.statusbar.quickactions.sharescreen.ui.compose.ShareScreenPrivacyIndicatorPopup
import com.android.systemui.statusbar.quickactions.stopwatch.ui.compose.StopwatchPopup
import kotlin.math.abs
import kotlin.math.pow
import kotlin.math.sign
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

val LocalPopupSwipeLock = compositionLocalOf<MutableState<Boolean>> { mutableStateOf(false) }

@Composable
fun StatusBarPopup(
    viewModel: PopupChipModel.Shown,
    isVisible: Boolean,
    chipBoundsInScreen: Rect? = null,
    canPage: Boolean = false,
    pageIndex: Int = 0,
    pageCount: Int = 1,
    pageDirection: Int = 1,
    onPage: (Int) -> Unit = {},
) {
    val density = Density(LocalContext.current)
    val statusBarHeightPx =
        with(density) { dimensionResource(R.dimen.status_bar_height).roundToPx() }
    val edgePaddingPx = with(density) { PopupEdgePadding.roundToPx() }
    val cameraBottomPx = LocalView.current.rootWindowInsets?.displayCutout?.let { cutout ->
        cutout.boundingRectTop.takeUnless { it.isEmpty }?.bottom
    }
    val popupTopPx = chipBoundsInScreen?.top?.toInt()
    val cameraClearance =
        if (popupTopPx != null && cameraBottomPx != null) {
            with(density) { (cameraBottomPx - popupTopPx).coerceAtLeast(0).toDp() }
        } else {
            0.dp
        }
    val positionProvider =
        remember(popupTopPx, statusBarHeightPx, edgePaddingPx) {
            object : PopupPositionProvider {
                override fun calculatePosition(
                    anchorBounds: IntRect,
                    windowSize: IntSize,
                    layoutDirection: LayoutDirection,
                    popupContentSize: IntSize,
                ): IntOffset =
                    IntOffset(
                        x = anchorBounds.left + (anchorBounds.width - popupContentSize.width) / 2,
                        y =
                            if (popupTopPx != null) popupTopPx - edgePaddingPx
                            else anchorBounds.top + statusBarHeightPx,
                    )
            }
        }
    Popup(
        popupPositionProvider = positionProvider,
        properties =
            PopupProperties(
                focusable = true,
                dismissOnBackPress = true,
                dismissOnClickOutside = true,
            ),
        onDismissRequest = { viewModel.hidePopup() },
    ) {
        val popupView = LocalView.current
        DisposableEffect(popupView) {
            val root = popupView.rootView
            val lp = root.layoutParams as? WindowManager.LayoutParams
            if (lp != null && lp.layoutInDisplayCutoutMode != LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS) {
                lp.layoutInDisplayCutoutMode = LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
                root.context.getSystemService(WindowManager::class.java)?.updateViewLayout(root, lp)
            }
            onDispose {}
        }
        var popupBoundsInScreen by remember { mutableStateOf<Rect?>(null) }

        val transformOrigin by remember {
            derivedStateOf {
                val chip = chipBoundsInScreen
                val popup = popupBoundsInScreen
                if (chip == null || popup == null || popup.width <= 0f) {
                    TransformOrigin(0.5f, 0f)
                } else {
                    val pivotX =
                        ((chip.center.x - popup.left) / popup.width).coerceIn(0.05f, 0.95f)
                    val pivotY =
                        if (popup.height > 0f) {
                            ((chip.center.y - popup.top) / popup.height).coerceIn(0f, 0.3f)
                        } else {
                            0f
                        }
                    TransformOrigin(pivotX, pivotY)
                }
            }
        }

        val initialScaleFromChip by remember {
            derivedStateOf {
                val chip = chipBoundsInScreen
                val popup = popupBoundsInScreen
                if (chip == null || popup == null || popup.width <= 0f || popup.height <= 0f) {
                    Offset(0.4f, 0.4f)
                } else {
                    Offset(
                        x = (chip.width / popup.width).coerceIn(0.2f, 1f),
                        y = (chip.height / popup.height).coerceIn(0.15f, 1f),
                    )
                }
            }
        }

        val scaleX = remember { Animatable(initialScaleFromChip.x) }
        val scaleY = remember { Animatable(initialScaleFromChip.y) }
        val alpha = remember { Animatable(0f) }
        val translationY = remember { Animatable(-24f) }

        LaunchedEffect(isVisible, popupBoundsInScreen != null) {
            if (isVisible && popupBoundsInScreen != null) {
                scaleX.snapTo(initialScaleFromChip.x)
                scaleY.snapTo(initialScaleFromChip.y)
                alpha.snapTo(0f)
                translationY.snapTo(-24f)
                coroutineScope {
                    launch {
                        scaleX.animateTo(
                            targetValue = 1f,
                            animationSpec =
                                spring(
                                    dampingRatio = 0.76f,
                                    stiffness = 540f,
                                ),
                        )
                    }
                    launch {
                        scaleY.animateTo(
                            targetValue = 1f,
                            animationSpec =
                                spring(
                                    dampingRatio = 0.78f,
                                    stiffness = 540f,
                                ),
                        )
                    }
                    launch {
                        translationY.animateTo(
                            targetValue = 0f,
                            animationSpec =
                                spring(
                                    dampingRatio = 0.76f,
                                    stiffness = 540f,
                                ),
                        )
                    }
                    launch {
                        alpha.animateTo(
                            1f,
                            animationSpec = tween(170, easing = FastOutSlowInEasing),
                        )
                    }
                }
            } else if (!isVisible) {
                coroutineScope {
                    launch {
                        scaleX.animateTo(
                            targetValue = initialScaleFromChip.x,
                            animationSpec =
                                spring(
                                    dampingRatio = 0.85f,
                                    stiffness = Spring.StiffnessMedium,
                                ),
                        )
                    }
                    launch {
                        scaleY.animateTo(
                            targetValue = initialScaleFromChip.y,
                            animationSpec =
                                spring(
                                    dampingRatio = 0.85f,
                                    stiffness = Spring.StiffnessMedium,
                                ),
                        )
                    }
                    launch {
                        translationY.animateTo(-16f, animationSpec = spring(stiffness = Spring.StiffnessMedium))
                    }
                    launch { alpha.animateTo(0f, animationSpec = tween(150)) }
                }
            }
        }

        DisposableEffect(popupView) {
            val listener = ViewTreeObserver.OnWindowFocusChangeListener { hasFocus ->
                if (!hasFocus) {
                    viewModel.hidePopup()
                }
            }
            popupView.viewTreeObserver.addOnWindowFocusChangeListener(listener)
            onDispose {
                popupView.viewTreeObserver.removeOnWindowFocusChangeListener(listener)
            }
        }

        AnimatedVisibility(
            visible = isVisible,
            enter = fadeIn(animationSpec = tween(50)),
            exit = fadeOut(animationSpec = tween(150)),
        ) {
            val pagerScope = rememberCoroutineScope()
            val pageFollow = remember { Animatable(0f) }
            var pageAccumulator by remember { mutableFloatStateOf(0f) }
            val pageThresholdPx = with(density) { 56.dp.toPx() }
            val maxFollowPx = with(density) { 40.dp.toPx() }
            val currentOnPage by rememberUpdatedState(onPage)

            CompositionLocalProvider(
                LocalGlassBlurAlpha provides { alpha.value }
            ) {
                PopupMagneticSwipeWrapper(
                    allowHorizontalDismiss = !canPage,
                    onDismiss = { viewModel.hidePopup() },
                ) {
                    Box(
                        modifier =
                            Modifier.padding(PopupEdgePadding)
                                .wrapContentSize()
                                .onGloballyPositioned { coordinates ->
                                    popupBoundsInScreen = coordinates.boundsInScreen(popupView)
                                }
                                .graphicsLayer {
                                    this.scaleX = scaleX.value
                                    this.scaleY = scaleY.value
                                    this.alpha = alpha.value
                                    this.translationY = translationY.value
                                    this.transformOrigin = transformOrigin
                                }
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            Box(
                                modifier =
                                    Modifier.then(
                                            if (canPage) {
                                                Modifier.pointerInput(canPage) {
                                                    detectHorizontalDragGestures(
                                                        onDragEnd = {
                                                            val direction =
                                                                when {
                                                                    pageAccumulator <= -pageThresholdPx -> 1
                                                                    pageAccumulator >= pageThresholdPx -> -1
                                                                    else -> 0
                                                                }
                                                            pageAccumulator = 0f
                                                            pagerScope.launch {
                                                                pageFollow.animateTo(
                                                                    0f,
                                                                    spring(
                                                                        dampingRatio =
                                                                            Spring.DampingRatioNoBouncy,
                                                                        stiffness = Spring.StiffnessMedium,
                                                                    ),
                                                                )
                                                            }
                                                            if (direction != 0) {
                                                                currentOnPage(direction)
                                                            }
                                                        },
                                                        onDragCancel = {
                                                            pageAccumulator = 0f
                                                            pagerScope.launch {
                                                                pageFollow.animateTo(
                                                                    0f,
                                                                    spring(
                                                                        dampingRatio =
                                                                            Spring.DampingRatioMediumBouncy,
                                                                        stiffness = Spring.StiffnessMedium,
                                                                    ),
                                                                )
                                                            }
                                                        },
                                                        onHorizontalDrag = { change, dragAmount ->
                                                            pageAccumulator += dragAmount
                                                            val target =
                                                                (pageAccumulator * 0.5f)
                                                                    .coerceIn(-maxFollowPx, maxFollowPx)
                                                            pagerScope.launch { pageFollow.snapTo(target) }
                                                            change.consume()
                                                        },
                                                    )
                                                }
                                            } else {
                                                Modifier
                                            }
                                        )
                                        .graphicsLayer { translationX = pageFollow.value }
                            ) {
                                AnimatedContent(
                                    targetState = viewModel,
                                    contentAlignment = Alignment.TopCenter,
                                    contentKey = { it.chipId },
                                    transitionSpec = {
                                        val dir = if (pageDirection >= 0) 1 else -1
                                        (slideInHorizontally(animationSpec = tween(260, easing = FastOutSlowInEasing)) { w -> dir * w } +
                                            fadeIn(animationSpec = tween(180))) togetherWith
                                            (slideOutHorizontally(animationSpec = tween(220, easing = FastOutSlowInEasing)) { w -> -dir * w } +
                                                fadeOut(animationSpec = tween(130))) using
                                            SizeTransform(clip = false) { _, _ ->
                                                spring(dampingRatio = 0.9f, stiffness = 420f)
                                            }
                                    },
                                    label = "island_popup_pager",
                                ) { chip ->
                                    CompositionLocalProvider(
                                        LocalPopupCameraClearance provides cameraClearance
                                    ) {
                                        IslandPopupContent(chip)
                                    }
                                }
                            }

                            if (pageCount > 1) {
                                IslandPagerDots(
                                    pageCount = pageCount,
                                    currentIndex = pageIndex,
                                    onSelectOffset = { delta ->
                                        if (delta != 0) currentOnPage(delta)
                                    },
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun IslandPagerDots(
    pageCount: Int,
    currentIndex: Int,
    onSelectOffset: (Int) -> Unit,
) {
    Row(
        modifier =
            Modifier.clip(CircleShape)
                .background(Color.Black.copy(alpha = 0.55f))
                .padding(horizontal = 10.dp, vertical = 5.dp),
        horizontalArrangement = Arrangement.spacedBy(5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val safeCurrent = currentIndex.coerceIn(0, (pageCount - 1).coerceAtLeast(0))
        for (i in 0 until pageCount) {
            val isSelected = i == safeCurrent
            val dotWidth by animateDpAsState(
                targetValue = if (isSelected) 16.dp else 6.dp,
                animationSpec = spring(dampingRatio = 0.78f, stiffness = 500f),
                label = "islandDotWidth",
            )
            Box(
                modifier =
                    Modifier.width(dotWidth)
                        .height(6.dp)
                        .clip(CircleShape)
                        .background(
                            if (isSelected) Color.White
                            else Color.White.copy(alpha = 0.38f)
                        )
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = { onSelectOffset(i - safeCurrent) },
                        )
            )
        }
    }
}

@Composable
private fun PopupMagneticSwipeWrapper(
    allowHorizontalDismiss: Boolean,
    onDismiss: () -> Unit,
    content: @Composable () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val haptics = LocalHapticFeedback.current
    val viewConfig = LocalViewConfiguration.current
    val currentOnDismiss by rememberUpdatedState(onDismiss)

    val offsetX = remember { Animatable(0f) }
    val offsetY = remember { Animatable(0f) }
    var itemWidth by remember { mutableFloatStateOf(1f) }
    var itemHeight by remember { mutableFloatStateOf(1f) }
    val swipeLock = remember { mutableStateOf(false) }

    val thresholdX = itemWidth * 0.35f
    val thresholdY = (itemHeight * 0.25f).coerceIn(80f, 180f)

    val progressX = if (thresholdX > 0f) (abs(offsetX.value) / thresholdX).coerceIn(0f, 1f) else 0f
    val progressY = if (thresholdY > 0f) (abs(offsetY.value) / thresholdY).coerceIn(0f, 1f) else 0f
    val progress = maxOf(progressX, progressY)

    CompositionLocalProvider(LocalPopupSwipeLock provides swipeLock) {
        Box(
            modifier =
                Modifier.onGloballyPositioned { coords ->
                        itemWidth = coords.size.width.toFloat().coerceAtLeast(1f)
                        itemHeight = coords.size.height.toFloat().coerceAtLeast(1f)
                    }
                    .pointerInput(allowHorizontalDismiss) {
                        val touchSlop = viewConfig.touchSlop
                        awaitEachGesture {
                            val down =
                                awaitPointerEvent(PointerEventPass.Initial)
                                    .changes
                                    .firstOrNull { it.changedToDownIgnoreConsumed() }
                                    ?: return@awaitEachGesture

                            val pointerId = down.id
                            val velocityTracker = VelocityTracker()
                            velocityTracker.addPosition(down.uptimeMillis, down.position)

                            var totalDragX = 0f
                            var totalDragY = 0f
                            var axis = 0 // 0 = undecided, 1 = horizontal, 2 = vertical-up
                            var hadThresholdHaptic = false

                            while (true) {
                                val event = awaitPointerEvent(PointerEventPass.Main)
                                val change =
                                    event.changes.firstOrNull { it.id == pointerId } ?: break
                                if (!change.pressed) {
                                    velocityTracker.addPosition(change.uptimeMillis, change.position)
                                    break
                                }
                                if (axis == 0 && change.isConsumed) {
                                    break
                                }

                                val delta = change.positionChange()
                                totalDragX += delta.x
                                totalDragY += delta.y
                                velocityTracker.addPosition(change.uptimeMillis, change.position)

                                if (axis == 0) {
                                    val dist = abs(totalDragX) + abs(totalDragY)
                                    if (dist > touchSlop) {
                                        val isHorizontal = abs(totalDragX) >= abs(totalDragY)
                                        if (isHorizontal && (!allowHorizontalDismiss || swipeLock.value)) {
                                            break
                                        }
                                        axis =
                                            if (isHorizontal && allowHorizontalDismiss) {
                                                1
                                            } else if (totalDragY < 0f) {
                                                2
                                            } else {
                                                break
                                            }
                                    } else {
                                        continue
                                    }
                                }

                                change.consume()
                                if (axis == 1) {
                                    val mag = magneticPopupOffset(totalDragX, itemWidth * 0.35f)
                                    scope.launch { offsetX.snapTo(mag) }
                                    val crossed = abs(mag) >= itemWidth * 0.35f * 0.85f
                                    if (crossed && !hadThresholdHaptic) {
                                        hadThresholdHaptic = true
                                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                    } else if (!crossed) {
                                        hadThresholdHaptic = false
                                    }
                                } else if (axis == 2) {
                                    val clampedY = totalDragY.coerceAtMost(0f)
                                    val mag =
                                        magneticPopupOffset(
                                            clampedY,
                                            (itemHeight * 0.25f).coerceIn(80f, 180f),
                                        )
                                    scope.launch { offsetY.snapTo(mag) }
                                    val crossed =
                                        abs(mag) >=
                                            (itemHeight * 0.25f).coerceIn(80f, 180f) * 0.85f
                                    if (crossed && !hadThresholdHaptic) {
                                        hadThresholdHaptic = true
                                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                    } else if (!crossed) {
                                        hadThresholdHaptic = false
                                    }
                                }
                            }

                            if (axis == 1) {
                                val velocityX = velocityTracker.calculateVelocity().x
                                val cur = offsetX.value
                                val thresh = itemWidth * 0.35f
                                val shouldDismiss =
                                    abs(cur) > thresh * 0.85f ||
                                        (abs(velocityX) > 700f &&
                                            abs(cur) > thresh * 0.25f &&
                                            sign(velocityX) == sign(cur))
                                if (shouldDismiss) {
                                    currentOnDismiss()
                                } else {
                                    scope.launch {
                                        offsetX.animateTo(
                                            0f,
                                            spring(dampingRatio = 0.65f, stiffness = 500f),
                                        )
                                    }
                                }
                            } else if (axis == 2) {
                                val velocityY = velocityTracker.calculateVelocity().y
                                val cur = offsetY.value
                                val thresh = (itemHeight * 0.25f).coerceIn(80f, 180f)
                                val shouldDismiss =
                                    abs(cur) > thresh * 0.75f ||
                                        (velocityY < -500f && abs(cur) > thresh * 0.2f)
                                if (shouldDismiss) {
                                    currentOnDismiss()
                                } else {
                                    scope.launch {
                                        offsetY.animateTo(
                                            0f,
                                            spring(dampingRatio = 0.65f, stiffness = 500f),
                                        )
                                    }
                                }
                            }
                        }
                    }
                    .graphicsLayer {
                        translationX = offsetX.value
                        translationY = offsetY.value
                        val scale = 1f - 0.08f * progress
                        scaleX = scale
                        scaleY = scale
                        alpha = 1f - 0.35f * progress
                    }
        ) {
            content()
        }
    }
}

private fun magneticPopupOffset(raw: Float, threshold: Float): Float {
    val absRaw = abs(raw)
    val s = sign(raw)
    return if (absRaw <= threshold) {
        s * threshold * (absRaw / threshold).pow(1.1f) * 0.85f
    } else {
        val base = threshold * 0.85f
        val excess = absRaw - threshold
        s * (base + excess * 0.5f)
    }
}

private fun LayoutCoordinates.boundsInScreen(view: android.view.View): Rect {
    val location = IntArray(2)
    view.getLocationOnScreen(location)
    return boundsInRoot().translate(Offset(location[0].toFloat(), location[1].toFloat()))
}

/** Renders the per-feature card for [viewModel]; the swipe pager slides between these. */
@Composable
private fun IslandPopupContent(viewModel: PopupChipModel.Shown) {
    when (val popupContent = viewModel.popupContent) {
        is PopupContentModel.Media -> {
            val model = popupContent.model
            val useWaveform = popupContent.useWaveform
            val hasLyrics = !model.lyrics.isNullOrBlank() || !model.syncedLyrics.isNullOrBlank()
            if (hasLyrics) {
                androidx.compose.foundation.layout.Column(
                    horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally,
                    verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp),
                ) {
                    MediaControlPopup(model = model, useWaveform = useWaveform)
                    CompositionLocalProvider(LocalPopupCameraClearance provides 0.dp) {
                        LyricsCard(model = model)
                    }
                }
            } else {
                MediaControlPopup(model = model, useWaveform = useWaveform)
            }
        }
        is PopupContentModel.ScreenRecord -> ScreenRecordPopup(model = popupContent.model)
        is PopupContentModel.LiveScore -> LiveScorePopup(model = popupContent.model)
        is PopupContentModel.Flashlight ->
            FlashlightPopup(
                model = popupContent.model,
                hapticsViewModelFactory = popupContent.hapticsViewModelFactory,
            )
        is PopupContentModel.Stopwatch -> StopwatchPopup(model = popupContent.model)
        is PopupContentModel.Alarm -> AlarmPopup(model = popupContent.model)
        is PopupContentModel.SystemEvent -> SystemEventPopup(model = popupContent)
        // The AOSP privacy popups take their own view models here, which the island does not
        // carry, so those chips open nothing from it.
        PopupContentModel.None -> Unit
    }
}

private val PopupEdgePadding = 8.dp
