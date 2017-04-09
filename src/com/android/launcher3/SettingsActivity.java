/*
 * Copyright (C) 2015 The Android Open Source Project
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
import android.database.ContentObserver;
import android.os.Bundle;
import android.os.Handler;
import android.preference.ListPreference;
import android.preference.Preference;
import android.preference.PreferenceFragment;
import android.provider.Settings;
import android.provider.Settings.System;

/**
 * Settings activity for Launcher. Currently implements the following setting: Allow rotation
 */
public class SettingsActivity extends Activity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Display the fragment as the main content.
        getFragmentManager().beginTransaction()
                .replace(android.R.id.content, new LauncherSettingsFragment())
                .commit();
    }

    /**
     * This fragment shows the launcher preferences.
     */
    public static class LauncherSettingsFragment extends PreferenceFragment {

        private SystemDisplayRotationLockObserver mRotationLockObserver;

        @Override
        public void onCreate(Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);
            getPreferenceManager().setSharedPreferencesName(LauncherFiles.SHARED_PREFERENCES_KEY);
            addPreferencesFromResource(R.xml.launcher_preferences);

            ListPreference iconsPack = (ListPreference) findPreference(Utilities.KEY_ICON_PACK);
            iconsPack.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
                @Override
                public boolean onPreferenceChange(Preference preference, Object newValue) {
                    String listValue = (String) newValue;
                    IconCache.getIconsHandler(getActivity().getApplicationContext())
                            .loadIconPack(listValue);
                    getPreferenceManager().getDefaultSharedPreferences(
                            getActivity().getApplicationContext()).edit()
                            .putString(Utilities.KEY_ICON_PACK, listValue).commit();
                    LauncherAppState.getInstance().getIconCache().flush();
                    LauncherAppState.getInstance().getModel().forceReload();
                    return true;
                 }
            });
            setListPreferenceIconsPacksData(iconsPack);

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
        }

        @Override
        public void onDestroy() {
            if (mRotationLockObserver != null) {
                getActivity().getContentResolver().unregisterContentObserver(mRotationLockObserver);
                mRotationLockObserver = null;
            }
            super.onDestroy();
        }

        protected void setListPreferenceIconsPacksData(ListPreference lp) {
            IconsHandler iph = IconCache.getIconsHandler(getActivity().getApplicationContext());
            CharSequence[] entries = new CharSequence[iph.getIconPacks().size()+1];
            CharSequence[] entryValues = new CharSequence[iph.getIconPacks().size()+1];
            int i = 0;
            entries[0] = this.getString(R.string.icons_pack_default_name);
            entryValues[0] = "default";
            for (String packageIconsPack : iph.getIconPacks().keySet()) {
                entries[++i] = iph.getIconPacks().get(packageIconsPack);
                entryValues[i] = packageIconsPack;
            }
            lp.setEntries(entries);
            lp.setDefaultValue("default");
            lp.setEntryValues(entryValues);
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
