/*
 * Copyright (C) 2017 Paranoid Android
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

package com.android.launcher3;

import android.content.Intent;
import android.os.Bundle;
import android.view.Menu;
import android.view.View;

import com.android.launcher3.Launcher.LauncherOverlay;
import com.android.launcher3.Launcher.LauncherOverlayCallbacks;
import com.android.launcher3.allapps.AllAppsSearchBarController;
import com.android.launcher3.logging.UserEventDispatcher;
import com.android.launcher3.util.ComponentKey;

import com.google.android.libraries.launcherclient.LauncherClient;
import com.google.android.libraries.launcherclient.LauncherClientCallbacksAdapter;

import java.io.FileDescriptor;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;

public class LauncherTab {

    private Launcher mLauncher;

    private LauncherClient mLauncherClient;

    public LauncherTab(Launcher launcher) {
        mLauncher = launcher;
        launcher.setLauncherCallbacks(new LauncherTabCallbacks());
        launcher.setLauncherOverlay(new LauncherOverlays());

        mLauncherClient = new LauncherClient(launcher, new LauncherClientCallbacksAdapter(), true);
    }

    protected LauncherClient getClient() {
        return mLauncherClient;
    }

    public class LauncherTabCallbacks implements LauncherCallbacks {

        @Override
        public void preOnCreate() {
        }

        @Override
        public void onCreate(Bundle savedInstanceState) {
        }

        @Override
        public void preOnResume() {
        }

        @Override
        public void onResume() {
            mLauncherClient.onResume();
        }

        @Override
        public void onStart() {
        }

        @Override
        public void onStop() {
        }

        @Override
        public void onPause() {
            mLauncherClient.onPause();
        }

        @Override
        public void onDestroy() {
            mLauncherClient.onDestroy();
        }

        @Override
        public void onSaveInstanceState(Bundle outState) {
        }

        @Override
        public void onPostCreate(Bundle savedInstanceState) {
        }

        @Override
        public void onNewIntent(Intent intent) {
        }

        @Override
        public void onActivityResult(int requestCode, int resultCode, Intent data) {
        }

        @Override
        public void onRequestPermissionsResult(int requestCode, String[] permissions,
                int[] grantResults) {
        }

        @Override
        public void onWindowFocusChanged(boolean hasFocus) {
        }

        @Override
        public boolean onPrepareOptionsMenu(Menu menu) {
            return false;
        }

        @Override
        public void dump(String prefix, FileDescriptor fd, PrintWriter w, String[] args) {
        }

        @Override
        public void onHomeIntent() {
        }

        @Override
        public boolean handleBackPressed() {
            return false;
        }

        @Override
        public void onTrimMemory(int level) {
        }

        @Override
        public void onLauncherProviderChange() {
        }

        @Override
        public void finishBindingItems(boolean upgradePath) {
        }

        @Override
        public void bindAllApplications(ArrayList<AppInfo> apps) {
        }

        @Override
        public void onWorkspaceLockedChanged() {
        }

        @Override
        public void onInteractionBegin() {
        }

        @Override
        public void onInteractionEnd() {
        }

        @Override
        public boolean startSearch(String initialQuery, boolean selectInitialQuery,
                Bundle appSearchData) {
            return false;
        }

        @Override
        public boolean hasCustomContentToLeft() {
            return false;
        }

        @Override
        public void populateCustomContentContainer() {
        }

        @Override
        public UserEventDispatcher getUserEventDispatcher() {
            return null;
        }

        @Override
        public View getQsbBar() {
            return null;
        }

        @Override
        public Bundle getAdditionalSearchWidgetOptions() {
            return new Bundle();
        }

        @Override
        public boolean shouldMoveToDefaultScreenOnHomeIntent() {
            return true;
        }

        @Override
        public boolean hasSettings() {
            return false;
        }

        @Override
        public AllAppsSearchBarController getAllAppsSearchBarController() {
            return null;
        }

        @Override
        public List<ComponentKey> getPredictedApps() {
            return new ArrayList<>();
        }

        @Override
        public int getSearchBarHeight() {
            return SEARCH_BAR_HEIGHT_NORMAL;
        }

        @Override
        public void setLauncherSearchCallback(Object callbacks) {
        }

        @Override
        public void onAttachedToWindow() {
            mLauncherClient.onAttachedToWindow();
        }

        @Override
        public void onDetachedFromWindow() {
            mLauncherClient.onDetachedFromWindow();
        }

        @Override
        public boolean shouldShowDiscoveryBounce() {
            return false;
        }
    }

    private class LauncherOverlays implements LauncherOverlay {
        @Override
        public void onScrollInteractionBegin() {
            mLauncherClient.startMove();
        }

        @Override
        public void onScrollInteractionEnd() {
            mLauncherClient.endMove();
        }

        @Override
        public void onScrollChange(float progress, boolean rtl) {
            mLauncherClient.updateMove(progress);
        }

        @Override
        public void setOverlayCallbacks(LauncherOverlayCallbacks callbacks) {
        }
    }
}
