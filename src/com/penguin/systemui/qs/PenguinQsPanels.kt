/*
 * Copyright (C) 2026 The PenguinOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package com.penguin.systemui.qs

import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import com.android.compose.animation.scene.ContentScope
import com.android.systemui.penguin.LocalQsPanels
import com.android.systemui.penguin.QsEditMode
import com.android.systemui.penguin.QsPanels
import com.android.systemui.penguin.QsTileStyle
import com.android.systemui.qs.composefragment.ConnectivityFolderExpansion
import com.android.systemui.qs.panels.ui.compose.StyledQsEditMode
import com.android.systemui.qs.panels.ui.compose.infinitegrid.styledQsTileStyle
import com.android.systemui.qs.shared.style.LocalGlassBlurAllowed
import com.android.systemui.qs.shared.style.PenguinQsStyle
import com.android.systemui.qs.shared.style.QsPanelStyleRepository
import com.android.systemui.qs.ui.composable.LocalQsHostTransition
import com.android.systemui.qs.ui.composable.PenguinMediaExpansion
import com.android.systemui.qs.ui.composable.StyledOverlayBody
import com.android.systemui.qs.ui.composable.StyledOverlayEditor
import com.android.systemui.qs.ui.composable.StyledOverlayHost
import com.android.systemui.qs.ui.composable.StyledQuickSettingsContent
import com.android.systemui.qs.ui.composable.closeExpansions
import com.android.systemui.qs.ui.composable.qsHostTransition
import com.android.systemui.qs.ui.viewmodel.QuickSettingsContainerViewModel
import com.android.systemui.shade.domain.interactor.ShadeAnimationInteractor
import com.android.systemui.shade.ui.composable.StyledShadeHeader
import com.android.systemui.shade.ui.viewmodel.ShadeSceneContentViewModel
import javax.inject.Inject

class PenguinQsPanels
@Inject
constructor(
    styleRepository: QsPanelStyleRepository,
    private val shadeAnimationInteractor: ShadeAnimationInteractor,
) : QsPanels {

    init {
        PenguinQsStyle.repository = styleRepository
    }

    @Composable
    override fun ContentScope.Content(
        viewModel: QuickSettingsContainerViewModel,
        mediaInRow: Boolean,
        modifier: Modifier,
        mediaSquishiness: () -> Float,
    ) {
        val mirror = viewModel.brightnessSliderViewModel
        HideGlassWhileMirroring({ mirror.showMirror }) {
            StyledQuickSettingsContent(viewModel, mediaInRow, modifier, mediaSquishiness)
        }
    }

    @Composable
    override fun ContentScope.ShadeHeader(
        viewModel: ShadeSceneContentViewModel,
        mediaInRow: Boolean,
        horizontalPadding: Dp,
        visualOffset: Density.() -> Int,
    ) {
        val mirror = viewModel.qsContainerViewModel.brightnessSliderViewModel
        HideGlassWhileMirroring({ mirror.showMirror }) {
            StyledShadeHeader(viewModel, mediaInRow, horizontalPadding, visualOffset)
        }
    }

    @Composable
    override fun ContentScope.OverlayBody(
        viewModel: QuickSettingsContainerViewModel,
        buildNumber: @Composable ColumnScope.() -> Unit,
    ) {
        StyledOverlayBody(viewModel, buildNumber)
    }

    @Composable
    override fun ContentScope.OverlayEditor(
        viewModel: QuickSettingsContainerViewModel,
        modifier: Modifier,
    ): Boolean = StyledOverlayEditor(viewModel, modifier)

    @Composable
    override fun ContentScope.OverlayHost(content: @Composable () -> Unit) {
        val container = LocalQsPanels.current?.container
        if (container == null) {
            content()
        } else {
            StyledOverlayHost(container, shadeAnimationInteractor.isLaunchingActivity, content)
        }
    }

    @Composable
    override fun ContentScope.Host(content: @Composable () -> Unit) {
        CompositionLocalProvider(LocalQsHostTransition provides qsHostTransition()) { content() }
    }

    override val editMode: QsEditMode = StyledQsEditMode()

    @Composable override fun tileStyle(): QsTileStyle? = styledQsTileStyle()

    override fun onScrimClicked(): Boolean {
        val open = ConnectivityFolderExpansion.expanded || PenguinMediaExpansion.expanded
        if (open) closeExpansions()
        return open
    }

    @Composable
    private fun HideGlassWhileMirroring(mirroring: () -> Boolean, content: @Composable () -> Unit) {
        val outer = LocalGlassBlurAllowed.current
        val allowed = remember(outer) { { outer() && !mirroring() } }
        CompositionLocalProvider(LocalGlassBlurAllowed provides allowed) { content() }
    }
}
