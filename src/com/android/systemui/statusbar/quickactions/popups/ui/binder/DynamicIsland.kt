/*
 * Copyright (C) 2026 The PenguinOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package com.android.systemui.statusbar.quickactions.popups.ui.binder

import android.content.Context
import android.database.ContentObserver
import android.graphics.Color
import android.graphics.Rect
import android.os.Handler
import android.os.Looper
import android.os.UserHandle
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver
import android.widget.LinearLayout
import android.widget.TextView
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.ComposeView
import com.android.compose.theme.PlatformTheme
import com.android.systemui.penguin.StatusBarIsland
import com.android.systemui.res.R
import com.android.systemui.statusbar.phone.KeyguardStatusBarView
import com.android.systemui.statusbar.phone.StatusIconContainer
import com.android.systemui.statusbar.quickactions.popups.shared.DynamicIslandFeatureSettings
import com.android.systemui.statusbar.quickactions.popups.shared.DynamicIslandFeatureSettings.readDynamicIslandFeatureEnabled
import com.android.systemui.statusbar.quickactions.popups.ui.compose.StatusBarDynamicIslandContainer
import com.android.systemui.statusbar.quickactions.popups.ui.viewmodel.DynamicIslandChipsViewModelStore
import javax.inject.Inject

class DynamicIsland
@Inject
constructor(
    private val chipsViewModelStore: DynamicIslandChipsViewModelStore,
    private val keyguardViewBinder: KeyguardDynamicIslandViewBinder,
) : StatusBarIsland {

    override fun attachKeyguard(statusBar: View) {
        keyguardViewBinder.bind(statusBar as KeyguardStatusBarView)
    }

    override fun attach(statusBar: View, onBoundsChanged: (Rect) -> Unit) {
        val context = statusBar.context
        val overlap = StatusIconOverlap(statusBar)

        overlap.enabled = isDynamicIslandSettingEnabled(context)
        val settingObserver =
            object : ContentObserver(Handler(Looper.getMainLooper())) {
                override fun onChange(selfChange: Boolean) {
                    overlap.enabled = isDynamicIslandSettingEnabled(context)
                    if (!overlap.enabled) overlap.reset()
                }
            }
        context.contentResolver.registerContentObserver(
            Settings.System.getUriFor(Settings.System.STATUS_BAR_SHOW_DYNAMIC_ISLAND),
            false,
            settingObserver,
            UserHandle.USER_ALL,
        )
        statusBar.addOnAttachStateChangeListener(
            object : View.OnAttachStateChangeListener {
                override fun onViewAttachedToWindow(v: View) {}

                override fun onViewDetachedFromWindow(v: View) {
                    context.contentResolver.unregisterContentObserver(settingObserver)
                }
            }
        )

        val reportBounds = { bounds: Rect ->
            onBoundsChanged(bounds)
            publishIslandBounds(context, bounds)
            overlap.collapse(bounds)
        }

        val centeredArea = statusBar.requireViewById<ViewGroup>(R.id.centered_area)
        val composeView =
            ComposeView(context).apply {
                layoutParams =
                    LinearLayout.LayoutParams(
                            ViewGroup.LayoutParams.WRAP_CONTENT,
                            ViewGroup.LayoutParams.WRAP_CONTENT,
                        )
                        .apply { gravity = Gravity.CENTER }
                setContent {
                    PlatformTheme {
                        // the display.
                        val islandViewModel =
                            remember(context.displayId) {
                                chipsViewModelStore.forDisplay(context.displayId)
                            }
                        StatusBarDynamicIslandContainer(
                            chips = islandViewModel.shownPopupChips,
                            onMediaControlPopupVisibilityChanged = {},
                            onIslandBoundsChanged = reportBounds,
                        )
                    }
                }
            }
        centeredArea.addView(composeView)

        centeredArea.viewTreeObserver.addOnGlobalLayoutListener {
            if (
                centeredArea.visibility != View.VISIBLE || composeView.visibility != View.VISIBLE
            ) {
                onBoundsChanged(Rect())
                overlap.collapse(Rect())
            }
        }
    }

    private class StatusIconOverlap(statusBar: View) {
        private val statusIcons: StatusIconContainer? = statusBar.findViewById(R.id.statusIcons)
        private val dotParent: ViewGroup? =
            statusBar.findViewById(R.id.status_bar_end_side_content)
        private var dot: View? = null
        private var pendingCheck: Runnable? = null
        private var lastBounds = Rect()
        var enabled = false

        init {
            val icons = statusIcons
            if (icons != null && dotParent != null) {
                val listener =
                    ViewTreeObserver.OnGlobalLayoutListener { apply(icons, dotParent, lastBounds) }
                icons.viewTreeObserver.addOnGlobalLayoutListener(listener)
                icons.addOnAttachStateChangeListener(
                    object : View.OnAttachStateChangeListener {
                        override fun onViewAttachedToWindow(v: View) {}

                        override fun onViewDetachedFromWindow(v: View) {
                            icons.viewTreeObserver.removeOnGlobalLayoutListener(listener)
                        }
                    }
                )
            }
        }

        fun collapse(islandBounds: Rect) {
            val icons = statusIcons ?: return
            val parent = dotParent ?: return
            if (!enabled) return
            lastBounds = islandBounds
            pendingCheck?.let { icons.removeCallbacks(it) }
            val runnable = Runnable { apply(icons, parent, islandBounds) }
            pendingCheck = runnable
            icons.postDelayed(runnable, 300L)
        }

        private fun apply(icons: StatusIconContainer, parent: ViewGroup, islandBounds: Rect) {
            if (!enabled) return
            val childRect = Rect()
            val loc = IntArray(2)
            var anyOverlap = false

            for (i in 0 until icons.childCount) {
                val child = icons.getChildAt(i)
                if (child.width == 0) continue
                child.getLocationInWindow(loc)
                childRect.set(loc[0], loc[1], loc[0] + child.width, loc[1] + child.height)
                val overlaps = Rect.intersects(childRect, islandBounds)
                val newVisibility = if (overlaps) View.INVISIBLE else View.VISIBLE
                if (child.visibility != newVisibility) {
                    child.visibility = newVisibility
                }
                if (overlaps) anyOverlap = true
            }

            // Added as a sibling of StatusIconContainer, not a child of it -- StatusIconContainer
            // expects its children to be status-icon views and can crash if a plain View is added.
            val dot =
                dot
                    ?: TextView(parent.context)
                        .apply {
                            text = "•"
                            textSize = 10f
                            setTextColor(Color.WHITE)
                            gravity = Gravity.CENTER
                            parent.addView(this, 0)
                        }
                        .also { dot = it }
            val dotVisibility = if (anyOverlap) View.VISIBLE else View.GONE
            if (dot.visibility != dotVisibility) {
                dot.visibility = dotVisibility
            }
        }

        fun reset() {
            statusIcons?.let {
                for (i in 0 until it.childCount) {
                    it.getChildAt(i).visibility = View.VISIBLE
                }
            }
            dot?.visibility = View.GONE
        }
    }

    private companion object {
        const val ISLAND_BOUNDS_SETTING = "penguin_island_bounds"

        val boundsHandler = Handler(Looper.getMainLooper())
        var publishedBounds: String? = null

        fun publishIslandBounds(context: Context, bounds: Rect) {
            val value = if (bounds.isEmpty) "" else bounds.flattenToString()
            boundsHandler.removeCallbacksAndMessages(null)
            boundsHandler.postDelayed(
                {
                    if (value != publishedBounds) {
                        publishedBounds = value
                        Settings.Secure.putString(
                            context.contentResolver,
                            ISLAND_BOUNDS_SETTING,
                            value,
                        )
                    }
                },
                100,
            )
        }

        fun isDynamicIslandSettingEnabled(context: Context): Boolean =
            context.contentResolver.readDynamicIslandFeatureEnabled(
                Settings.System.STATUS_BAR_SHOW_DYNAMIC_ISLAND,
                DynamicIslandFeatureSettings.ENABLED_DEFAULT,
            )
    }
}
