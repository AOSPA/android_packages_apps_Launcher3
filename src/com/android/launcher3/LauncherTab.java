package com.android.launcher3;

import com.android.launcher3.Launcher.LauncherOverlay;
import com.android.launcher3.Launcher.LauncherOverlayCallbacks;

import com.google.android.libraries.launcherclient.LauncherClient;
import com.google.android.libraries.launcherclient.LauncherClientCallbacksAdapter;

public class LauncherTab implements LauncherOverlay {

    private LauncherClient mLauncherClient;
    private LauncherOverlayCallbacks callbacks;

    private boolean mAttached = false;

    public LauncherTab(Launcher launcher) {
        final CallbacksAdapter cb = new CallbacksAdapter();
        mLauncherClient = new LauncherClient(launcher, cb, true);
    }

    @Override
    public void setOverlayCallbacks(LauncherOverlayCallbacks launcherOverlayCallbacks) {
        callbacks = launcherOverlayCallbacks;
    }

    @Override
    public void onScrollChange(float f, boolean z) {
        android.util.Log.d("LauncherTab", "onscrollchange fraction is: " + f);
        mLauncherClient.updateMove(f);
    }

    @Override
    public void onScrollInteractionBegin() {
        mLauncherClient.startMove();
    }

    @Override
    public void onScrollInteractionEnd() {
        mLauncherClient.endMove();
    }

    protected void onAttachedToWindow() {
        mLauncherClient.onAttachedToWindow();
    }

    protected void onDetachedFromWindow() {
        mLauncherClient.onDetachedFromWindow();
    }

    protected void onResume() {
        mLauncherClient.onResume();
    }

    protected void onPause() {
        mLauncherClient.onPause();
    }

    protected void onDestroy() {
        mLauncherClient.onDestroy();
    }

    private class CallbacksAdapter extends LauncherClientCallbacksAdapter {
        @Override
        public void onServiceStateChanged(boolean overlayAttached, boolean hotwordActive) {
            android.util.Log.d("CallbacksAdapter", "onServiceStateChanged: \n  overlayAttached: " + overlayAttached + "\n  hotwordActive: " + hotwordActive);
        }

        @Override
        public void onOverlayScrollChanged(float f) {
            android.util.Log.d("CallbacksAdapter", "onOverlayScrollChanged: \n  progress: " + f);
            if (callbacks != null) {
                callbacks.onScrollChanged(f);
            }
        }
    }
}
