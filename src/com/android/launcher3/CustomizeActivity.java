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

import android.app.Activity;
import android.content.ContentResolver;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.database.ContentObserver;
import android.os.Bundle;
import android.os.Handler;
import android.preference.Preference;
import android.preference.PreferenceFragment;
import android.preference.PreferenceManager;
import android.preference.PreferenceCategory;
import android.preference.PreferenceScreen;
import android.preference.SwitchPreference;
import android.provider.Settings;
import android.provider.Settings.System;

public class CustomizeActivity extends Activity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Display the fragment as the main content.
        getFragmentManager().beginTransaction()
                .replace(android.R.id.content, new CustomizationFragment())
                .commit();
    }

    public static class CustomizationFragment extends PreferenceFragment implements OnSharedPreferenceChangeListener {

        private String mDefaultIconPack;

        private IconsHandler mIconsHandler;
        private PackageManager mPackageManager;
        private Preference mIconPack;

        private SwitchPreference mLeftPage;
        private SystemDisplayRotationLockObserver mRotationLockObserver;

        @Override
        public void onCreate(Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);
            getPreferenceManager().setSharedPreferencesName(LauncherFiles.SHARED_PREFERENCES_KEY);
            addPreferencesFromResource(R.xml.launcher_customization);

            PreferenceManager.getDefaultSharedPreferences(getActivity())
                    .registerOnSharedPreferenceChangeListener(this);
            mPackageManager = getActivity().getPackageManager();

            // Setup allow rotation preference
            Preference rotationPref = findPreference(Utilities.ALLOW_ROTATION_PREFERENCE_KEY);
            if (getResources().getBoolean(R.bool.allow_rotation)) {
                // Launcher supports rotation by default. No need to show this setting.
                getPreferenceScreen().removePreference(rotationPref);
            } else {
                ContentResolver resolver = getActivity().getContentResolver();
                mRotationLockObserver = new SystemDisplayRotationLockObserver(rotationPref, resolver);

                // Register a content observer to listen for system setting changes while
                // this UI is active.
                resolver.registerContentObserver(
                        Settings.System.getUriFor(System.ACCELEROMETER_ROTATION),
                        false, mRotationLockObserver);

                // Initialize the UI once
                mRotationLockObserver.onChange(true);
                rotationPref.setDefaultValue(Utilities.getAllowRotationDefaultValue(getActivity()));
            }

            mDefaultIconPack = getString(R.string.default_iconpack_title);
            mIconsHandler = IconCache.getIconsHandler(getActivity().getApplicationContext());
            mIconPack = (Preference) findPreference(Utilities.KEY_ICON_PACK);

            boolean state = Utilities.getPrefs(getActivity()).getBoolean(
                    Utilities.ACTION_LEFT_PAGE_CHANGED, true);

            mLeftPage = (SwitchPreference) findPreference(Utilities.KEY_LEFT_PAGE);
            mLeftPage.setChecked(state);

            reloadIconPackSummary();
        }

        @Override
        public void onPause() {
            super.onPause();
            mIconsHandler.hideDialog();
        }

        @Override
        public void onDestroy() {
            PreferenceManager.getDefaultSharedPreferences(getActivity())
                    .unregisterOnSharedPreferenceChangeListener(this);
            if (mRotationLockObserver != null) {
                getActivity().getContentResolver().unregisterContentObserver(mRotationLockObserver);
                mRotationLockObserver = null;
            }
            super.onDestroy();
        }

        @Override
        public boolean onPreferenceTreeClick(PreferenceScreen preferenceScreen, Preference pref) {
            if (pref == mIconPack) {
                mIconsHandler.showDialog(getActivity());
                return true;
            }
            if (pref == mLeftPage) {
                boolean state = Utilities.getPrefs(getActivity()).getBoolean(
                        Utilities.ACTION_LEFT_PAGE_CHANGED, true);
                Utilities.getPrefs(getActivity()).edit().putBoolean(
                        Utilities.ACTION_LEFT_PAGE_CHANGED, !state).commit();
                Intent intent = new Intent(Utilities.ACTION_LEFT_PAGE_CHANGED);
                getActivity().sendBroadcast(intent);
                return true;
            }
            return false;
        }

        @Override
        public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
            reloadIconPackSummary();
        }

        private void reloadIconPackSummary() {
            ApplicationInfo info = null;
            String iconPack = PreferenceManager.getDefaultSharedPreferences(getActivity())
                    .getString(Utilities.KEY_ICON_PACK, mDefaultIconPack);
            if (!mIconsHandler.isDefaultIconPack()) {
                try {
                    info = mPackageManager.getApplicationInfo(iconPack, 0);
                } catch (PackageManager.NameNotFoundException e) {
                }
                if (info != null) {
                    iconPack = mPackageManager.getApplicationLabel(info).toString();
                }
            }
            mIconPack.setSummary(iconPack);
        }
    }

    /**
     * Content observer which listens for system auto-rotate setting changes, and enables/disables
     * the launcher rotation setting accordingly.
     */
    private static class SystemDisplayRotationLockObserver extends ContentObserver {

        private final Preference mRotationPref;
        private final ContentResolver mResolver;

        public SystemDisplayRotationLockObserver(
                Preference rotationPref, ContentResolver resolver) {
            super(new Handler());
            mRotationPref = rotationPref;
            mResolver = resolver;
        }

        @Override
        public void onChange(boolean selfChange) {
            boolean enabled = Settings.System.getInt(mResolver,
                    Settings.System.ACCELEROMETER_ROTATION, 1) == 1;
            mRotationPref.setEnabled(enabled);
            mRotationPref.setSummary(enabled
                    ? R.string.allow_rotation_desc : R.string.allow_rotation_blocked_desc);
        }
    }
}
