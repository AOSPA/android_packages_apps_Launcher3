/*
 * Copyright (C) 2020 Paranoid Android
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

package co.aospa.launcher;

import android.app.smartspace.SmartspaceTarget;

import co.aospa.launcher.ParanoidLauncherModelDelegate.SmartspaceItem;

import com.android.launcher3.model.BgDataModel;
import com.android.launcher3.uioverrides.QuickstepLauncher;
import com.android.systemui.plugins.shared.LauncherOverlayManager;

import com.google.android.systemui.smartspace.BcSmartspaceDataProvider;

import java.util.List;
import java.util.stream.Collectors;

public class ParanoidLauncher extends QuickstepLauncher {

    public BcSmartspaceDataProvider mSmartspacePlugin = new BcSmartspaceDataProvider();

    @Override
    protected LauncherOverlayManager getDefaultOverlay() {
        return new OverlayCallbackImpl(this);
    }

    public BcSmartspaceDataProvider getSmartspacePlugin() {
        return mSmartspacePlugin;
    }

    @Override
    public void bindExtraContainerItems(BgDataModel.FixedContainerItems container) {
        if (container.containerId == -110) {
            List<SmartspaceTarget> targets = container.items.stream().map(item -> ((SmartspaceItem) item).getSmartspaceTarget()).collect(Collectors.toList());
            mSmartspacePlugin.onTargetsAvailable(targets);
        }
        super.bindExtraContainerItems(container);
    }

}
