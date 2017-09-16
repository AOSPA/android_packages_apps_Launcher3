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

import android.app.ActionBar;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.view.Menu;
import android.view.MenuItem;

import java.util.Collections;
import java.util.List;
import java.util.Set;

public class MultiSelectRecyclerViewActivity extends Activity implements MultiSelectRecyclerViewAdapter.ViewHolder.ClickListener {

    private List<ResolveInfo> mInstalledPackages;
    private ActionBar mActionBar;
    private MultiSelectRecyclerViewAdapter mAdapter;

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {

        getMenuInflater().inflate(R.menu.hide_menu, menu);

        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle item selection
        switch (item.getItemId()) {

            case R.id.hide:
                updateHiddenApps();
                break;
        }
        return super.onOptionsItemSelected(item);
    }

    private void updateHiddenApps() {

        mAdapter.addSelectionsToHideList(this);
        LauncherAppState appState = LauncherAppState.getInstance();
        if (appState != null) {
            appState.getModel().forceReload();
        }

        navigateUpTo(new Intent(this, Launcher.class));
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_multiselect);
        mActionBar = getActionBar();

        Set<String> mSelectedApps = PreferenceManager.getDefaultSharedPreferences(this).getStringSet(Utilities.KEY_HIDDEN_APPS_SET, null);
        if (mSelectedApps != null) {
            if (mSelectedApps.size() != 0) {
                mActionBar.setTitle(String.valueOf(mSelectedApps.size()) + getString(R.string.hide_app_selected));
            } else {
                mActionBar.setTitle(getString(R.string.hidden_app));
            }
        }

        mInstalledPackages = getInstalledApps();
        RecyclerView recyclerView = (RecyclerView) findViewById(R.id.my_recycler_view);
        recyclerView.setHasFixedSize(true);
        recyclerView.setLayoutManager(new LinearLayoutManager(this, LinearLayoutManager.VERTICAL, false));
        mAdapter = new MultiSelectRecyclerViewAdapter(MultiSelectRecyclerViewActivity.this, mInstalledPackages, this);
        recyclerView.setAdapter(mAdapter);
    }

    @Override
    public void onItemClicked(int position) {

        mAdapter.toggleSelection(mActionBar, position, mInstalledPackages.get(position).activityInfo.packageName);
    }

    private List<ResolveInfo> getInstalledApps() {
        //get a list of installed apps.
        PackageManager packageManager = getPackageManager();
        Intent intent = new Intent(Intent.ACTION_MAIN, null);
        intent.addCategory(Intent.CATEGORY_LAUNCHER);

        List<ResolveInfo> installedApps = packageManager.queryIntentActivities(intent, PackageManager.GET_META_DATA);
        Collections.sort(installedApps, new ResolveInfo.DisplayNameComparator(packageManager));
        return installedApps;
    }
}