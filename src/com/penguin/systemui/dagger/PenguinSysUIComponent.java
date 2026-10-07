/*
 * Copyright (C) 2026 The PenguinOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package com.penguin.systemui.dagger;

import com.android.systemui.bundle.phone.PodModulePhone;
import com.android.systemui.controls.dagger.StartControlsStartableModule;
import com.android.systemui.dagger.DefaultComponentBinder;
import com.android.systemui.dagger.DependencyProvider;
import com.android.systemui.dagger.ReferenceSystemUIModule;
import com.android.systemui.dagger.SysUIComponent;
import com.android.systemui.dagger.SysUISingleton;
import com.android.systemui.dagger.SystemUICoreStartableModule;
import com.android.systemui.dagger.SystemUIModule;
import com.android.systemui.keyguard.CustomizationProvider;
import com.android.systemui.notifications.intelligence.rules.ui.NotificationRulesDefaultModule;
import com.android.systemui.settings.MultiUserUtilsModule;
import com.android.systemui.statusbar.NotificationInsetsModule;
import com.android.systemui.statusbar.QsFrameTranslateModule;
import com.android.systemui.unfold.SysUIUnfoldModule;
import com.android.systemui.util.StartBinderLoggerModule;
import com.android.systemui.wallpapers.dagger.WallpaperModule;

import dagger.Subcomponent;

@SysUISingleton
@Subcomponent(modules = {
        DefaultComponentBinder.class,
        DependencyProvider.class,
        MultiUserUtilsModule.class,
        NotificationInsetsModule.class,
        NotificationRulesDefaultModule.class,
        PenguinSystemUIModule.class,
        QsFrameTranslateModule.class,
        ReferenceSystemUIModule.class,
        StartControlsStartableModule.class,
        StartBinderLoggerModule.class,
        SystemUIModule.class,
        PodModulePhone.class,
        SystemUICoreStartableModule.class,
        SysUIUnfoldModule.class,
        WallpaperModule.class})
public interface PenguinSysUIComponent extends SysUIComponent {

    @SysUISingleton
    @Subcomponent.Builder
    interface Builder extends SysUIComponent.Builder {
        PenguinSysUIComponent build();
    }

    void inject(CustomizationProvider customizationProvider);
}
