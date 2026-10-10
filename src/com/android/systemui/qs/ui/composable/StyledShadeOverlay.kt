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

package com.android.systemui.qs.ui.composable

import androidx.compose.foundation.layout.ColumnScope
import com.android.systemui.qs.shared.style.panelStyle
import androidx.compose.foundation.layout.Arrangement
import com.android.systemui.qs.composefragment.BrightnessLayout
import com.android.systemui.qs.shared.style.LocalGlassBlurAllowed
import kotlinx.coroutines.flow.StateFlow
import com.android.systemui.qs.shared.style.LocalGlassBlurAlpha
import com.android.systemui.qs.composefragment.VolumeLayout
import com.android.systemui.qs.shared.ui.QuickSettings
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredHeight
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.systemGestureExclusion
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onLayoutRectChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleStartEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.android.compose.PlatformSliderDefaults
import com.android.compose.animation.scene.ContentScope
import com.android.compose.animation.scene.ElementKey
import com.android.compose.animation.scene.UserAction
import com.android.compose.animation.scene.UserActionResult
import com.android.compose.animation.scene.content.state.TransitionState
import com.android.compose.gesture.gesturesDisabled
import com.android.compose.lifecycle.DisposableEffectWithLifecycle
import com.android.compose.lifecycle.LaunchedEffectWithLifecycle
import com.android.compose.modifiers.thenIf
import com.android.systemui.brightness.ui.compose.BrightnessSliderContainer
import com.android.systemui.brightness.ui.compose.BrightnessSliderDimensions
import com.android.systemui.brightness.ui.compose.ContainerColors
import com.android.systemui.compose.modifiers.sysuiResTag
import com.android.systemui.dagger.SysUISingleton
import com.android.systemui.development.ui.compose.BuildNumber
import com.android.systemui.development.ui.viewmodel.BuildNumberViewModel
import com.android.systemui.lifecycle.rememberViewModel
import com.android.systemui.media.remedia.ui.compose.Media
import com.android.systemui.media.remedia.ui.compose.MediaPresentationStyle
import com.android.systemui.notifications.ui.composable.SnoozableHeadsUpNotificationPlaceholder
import com.android.systemui.qs.composefragment.ui.GridAnchor
import com.android.systemui.qs.flags.QsDetailedView
import com.android.systemui.qs.panels.ui.compose.EditMode
import com.android.systemui.qs.panels.ui.compose.TileDetails
import com.android.systemui.qs.panels.ui.compose.TileGrid
import com.android.systemui.qs.panels.ui.compose.toolbar.Toolbar
import com.android.systemui.qs.panels.ui.viewmodel.toolbar.ToolbarViewModel
import com.android.systemui.qs.tiles.dialog.AudioDetailsViewModel
import com.android.systemui.qs.shared.style.QsPanelStyle
import com.android.systemui.qs.ui.composable.QuickSettingsShade.systemGestureExclusionInShade
import com.android.systemui.qs.ui.viewmodel.QuickSettingsContainerViewModel
import com.android.systemui.qs.ui.viewmodel.QuickSettingsShadeOverlayActionsViewModel
import com.android.systemui.qs.ui.viewmodel.QuickSettingsShadeOverlayContentViewModel
import com.android.systemui.res.R
import com.android.systemui.scene.shared.model.Overlays
import com.android.systemui.scene.ui.composable.LocalSceneContainerPreloadedResources
import com.android.systemui.scene.ui.composable.Overlay
import com.android.systemui.shade.ui.composable.ChipHighlightModel
import com.android.systemui.shade.ui.composable.OverlayShade
import com.android.systemui.shade.ui.composable.OverlayShadeHeader
import com.android.systemui.shade.ui.composable.QuickSettingsOverlayHeader
import com.android.systemui.shade.ui.composable.QuickSettingsOverlayPrivacyChip
import com.android.systemui.statusbar.notification.stack.shared.model.ShadeScrimBounds
import com.android.systemui.statusbar.notification.stack.shared.model.ShadeScrimShape
import com.android.systemui.statusbar.notification.stack.ui.view.NotificationScrollView
import com.android.systemui.statusbar.notification.stack.ui.viewmodel.NotificationsPlaceholderViewModel
import com.android.systemui.volume.panel.component.volume.slider.ui.viewmodel.AudioStreamSliderViewModel
import com.android.systemui.volume.panel.component.volume.ui.composable.VolumeSlider
import com.android.systemui.volume.panel.component.volume.ui.composable.VolumeSliderDimensions
import dagger.Lazy
import javax.inject.Inject
import kotlin.math.roundToInt
import kotlinx.coroutines.flow.Flow
import com.android.systemui.qs.shared.style.LocalGlassBlurAllowed

@Composable
fun ContentScope.StyledOverlayBody(
    qsContainerViewModel: QuickSettingsContainerViewModel,
    buildNumber: @Composable ColumnScope.() -> Unit,
) {

    if (qsContainerViewModel.panelStyle == QsPanelStyle.Penguin) {
        Column(modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState())) {
            PenguinGridQuickSettings(viewModel = qsContainerViewModel)
            VerticalSeparator(QuickSettingsShade.Dimensions.VerticalPadding)
        }
    } else if (qsContainerViewModel.panelStyle == QsPanelStyle.OneUi) {
        Column(modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState())) {
            OneUiGridQuickSettings(viewModel = qsContainerViewModel)
            VerticalSeparator(QuickSettingsShade.Dimensions.VerticalPadding)
        }
    } else if (qsContainerViewModel.panelStyle != QsPanelStyle.Default) {
        Column(modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState())) {
            QuickSettingsContent(
                viewModel = qsContainerViewModel,
                mediaInRow = false,
            )
            VerticalSeparator(QuickSettingsShade.Dimensions.VerticalPadding)
        }
    } else {
        Column(modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState())) {
            val isIdle = layoutState.transitionState is TransitionState.Idle
            val horizontalSpacing = dimensionResource(id = R.dimen.qs_tile_margin_horizontal)
            val showMedia = qsContainerViewModel.showMedia
            val top2Specs = remember(qsContainerViewModel.tileGridViewModel.tileViewModels) {
                qsContainerViewModel.tileGridViewModel.tileViewModels.take(2).map { it.spec }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(horizontalSpacing),
                verticalAlignment = Alignment.Top,
            ) {
                Box(modifier = Modifier.weight(1f)) {
                    if (showMedia) {
                        Media(
                            viewModelFactory = qsContainerViewModel.mediaViewModelFactory,
                            presentationStyle = MediaPresentationStyle.Default,
                            behavior = QuickSettingsContainerViewModel.mediaUiBehavior,
                            onDismissed = qsContainerViewModel::onMediaSwipeToDismiss,
                            modifier = Modifier.fillMaxWidth(),
                            location = Media.Location.QS,
                        )
                    } else {
                        var listening by remember { mutableStateOf(false) }
                        LifecycleStartEffect(Unit) {
                            listening = true
                            onStopOrDispose { listening = false }
                        }
                        TileGrid(
                            viewModel = qsContainerViewModel.tileGridViewModel,
                            includeSpecs = top2Specs,
                            columnsOverride = 1,
                            forceLargeTiles = true,
                            listening = { listening },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
                Box(modifier = Modifier.weight(1f)) {
                    Element(key = QuickSettings.Elements.BrightnessSlider, modifier = Modifier) {
                        val tileHeight = dimensionResource(id = R.dimen.common_tile_default_tile_height)
                        val tileSpacing = dimensionResource(id = R.dimen.qs_tile_margin_vertical)
                        val headerHeight = tileHeight * 2 + tileSpacing
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(
                                horizontalSpacing,
                                Alignment.CenterHorizontally
                            ),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            BrightnessLayout(enable = isIdle, sliderHeight = headerHeight)
                            VolumeLayout(enable = isIdle, sliderHeight = headerHeight)
                        }
                    }
                }
            }

            VerticalSeparator(dimensionResource(R.dimen.qs_tile_margin_vertical))

            val excludeSpecs = top2Specs

            GridAnchor()
            TileGrid(
                viewModel = qsContainerViewModel.tileGridViewModel,
                excludeSpecs = excludeSpecs,
                modifier = Modifier.fillMaxWidth(),
                enableRevealEffect = TileRevealFlag.isEnabled,
            )

            buildNumber()
            VerticalSeparator(QuickSettingsShade.Dimensions.VerticalPadding)
        }
    }
}

@Composable
fun ContentScope.StyledOverlayEditor(
    containerViewModel: QuickSettingsContainerViewModel,
    modifier: Modifier,
): Boolean {
    val editorModifier =
        modifier.fillMaxWidth().padding(vertical = QuickSettingsShade.Dimensions.VerticalPadding)
    when {
        containerViewModel.panelStyle == QsPanelStyle.OneUi && OneUiToggleEditing.active ->
            OneUiTogglesEditor(viewModel = containerViewModel, modifier = editorModifier)
        containerViewModel.panelStyle == QsPanelStyle.OneUi ->
            OneUiGridEditor(viewModel = containerViewModel, modifier = editorModifier)
        containerViewModel.panelStyle == QsPanelStyle.Penguin ->
            PenguinGridEditor(viewModel = containerViewModel, modifier = editorModifier)
        else -> return false
    }
    return true
}

@Composable
fun ContentScope.StyledOverlayHost(
    containerViewModel: QuickSettingsContainerViewModel,
    launchingActivity: StateFlow<Boolean>,
    content: @Composable () -> Unit,
) {
    val launching by launchingActivity.collectAsStateWithLifecycle()
    val launchFade by
        animateFloatAsState(
            if (launching) 0f else 1f,
            animationSpec = tween(durationMillis = 120),
            label = "Launch fade",
        )
    val isEditing by containerViewModel.editModeViewModel.isEditing.collectAsStateWithLifecycle()
    LaunchedEffect(isEditing) { if (!isEditing) OneUiToggleEditing.active = false }
    val mirrorShowing by
        rememberUpdatedState(containerViewModel.brightnessSliderViewModel.showMirror)
    val allowed = remember { { !mirrorShowing } }
    val fade =
        remember(layoutState) {
            {
                val state = layoutState.transitionState
                val transition = layoutState.currentTransition
                val shown = contentKey == state.currentScene || contentKey in state.currentOverlays
                launchFade *
                    when {
                        transition?.toContent == contentKey -> transition.progress
                        transition?.fromContent == contentKey -> 1f - transition.progress
                        shown -> 1f
                        else -> 0f
                    }
            }
        }
    CompositionLocalProvider(
        LocalGlassBlurAllowed provides allowed,
        LocalGlassBlurAlpha provides fade,
    ) {
        Box(Modifier.graphicsLayer { alpha = launchFade }, propagateMinConstraints = true) {
            content()
        }
    }
}

@Composable
private fun VerticalSeparator(height: Dp) {
    Spacer(Modifier.height(height = height))
}
