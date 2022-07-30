package com.android.launcher3.qsb;

import android.graphics.Rect;

import com.android.launcher3.BaseQuickstepLauncher;

import com.android.systemui.shared.system.smartspace.ISmartspaceCallback;
import com.android.systemui.shared.system.smartspace.SmartspaceState;

import com.google.android.systemui.smartspace.BcSmartspaceView;

import java.util.concurrent.CountDownLatch;

public class LauncherSmartspaceCallback extends ISmartspaceCallback.Stub {

    public final BaseQuickstepLauncher mLauncher;
    public final SmartspaceViewContainer mContainer;
    public final BcSmartspaceView mView;

    public LauncherSmartspaceCallback(SmartspaceViewContainer container, BaseQuickstepLauncher launcher) {
        mContainer = container;
        mLauncher = launcher;
        mView = container.getSmartspaceView();
    }

    @Override
    public SmartspaceState getSmartspaceState() {
        final CountDownLatch countDownLatch = new CountDownLatch(1);
        final Rect rect = new Rect();
        mView.post(() -> {
            mView.getBoundsOnScreen(rect);
            countDownLatch.countDown();
        });
        try {
            countDownLatch.await();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        SmartspaceState smartspaceState = new SmartspaceState();
        smartspaceState.setBoundsOnScreen(rect);
        return smartspaceState;
    }

    @Override
    public void setSelectedPage(int i) {
        // do nothing
    }

    @Override
    public void setVisibility(int visibility) {
        mView.post(() -> {
            mView.setVisibility(visibility);
            mLauncher.onUiChangedWhileSleeping();
        });
    }
}
