/*
 * Copyright (C) 2020-2024 Paranoid Android
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package co.aospa.launcher

import android.app.smartspace.SmartspaceTarget
import android.os.Bundle
import co.aospa.launcher.ParanoidLauncherModelDelegate.SmartspaceItem
import com.android.launcher3.model.BgDataModel
import com.android.launcher3.qsb.LauncherUnlockAnimationController
import com.android.launcher3.uioverrides.QuickstepLauncher
import com.android.systemui.plugins.shared.LauncherOverlayManager
import com.android.quickstep.SystemUiProxy

import com.google.android.systemui.smartspace.BcSmartspaceDataProvider

class ParanoidQuickstepLauncher : QuickstepLauncher() {

    private val mSmartspacePlugin = BcSmartspaceDataProvider()
    private val mUnlockAnimationController = LauncherUnlockAnimationController(this)

    companion object {
        private const val TAG = "ParanoidQuickstepLauncher"
    }

    override fun getDefaultOverlay(): LauncherOverlayManager {
        return OverlayCallbackImpl(this)
    }

    fun getSmartspacePlugin(): BcSmartspaceDataProvider {
        return mSmartspacePlugin
    }

    fun getLauncherUnlockAnimationController(): LauncherUnlockAnimationController {
        return mUnlockAnimationController
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        SystemUiProxy.INSTANCE.get(this).setLauncherUnlockAnimationController(this.javaClass.simpleName, mUnlockAnimationController)
    }

    override fun onDestroy() {
        super.onDestroy()
        SystemUiProxy.INSTANCE.get(this).setLauncherUnlockAnimationController("null", null)
    }

    override fun onOverlayVisibilityChanged(visible: Boolean) {
        super.onOverlayVisibilityChanged(visible)
        mUnlockAnimationController.updateSmartspaceState()
    }

    override fun onPageEndTransition() {
        super.onPageEndTransition()
        mUnlockAnimationController.updateSmartspaceState()
    }

    override fun bindExtraContainerItems(container: BgDataModel.FixedContainerItems) {
        if (container.containerId == -110) {
            val targets = container.items.map { item -> (item as SmartspaceItem).smartspaceTarget }
            mSmartspacePlugin.onTargetsAvailable(targets)
        }
        super.bindExtraContainerItems(container)
    }
}
