/*
 * Copyright (C) 2026 The PenguinOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package com.penguin.systemui.dagger;

import com.android.systemui.dagger.GlobalModule;
import com.android.systemui.dagger.GlobalRootComponent;

import dagger.Component;

import javax.inject.Singleton;

@Singleton
@Component(modules = {GlobalModule.class})
public interface PenguinGlobalRootComponent extends GlobalRootComponent {

    @Component.Builder
    interface Builder extends GlobalRootComponent.Builder {
        PenguinGlobalRootComponent build();
    }

    @Override
    PenguinSysUIComponent.Builder getSysUIComponent();
}
