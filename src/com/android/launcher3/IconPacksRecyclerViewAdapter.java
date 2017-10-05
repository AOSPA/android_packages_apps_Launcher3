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

import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.graphics.drawable.Drawable;
import android.support.v7.widget.RecyclerView;
import android.util.Pair;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import java.util.List;

class IconPacksRecyclerViewAdapter extends RecyclerView.Adapter<IconPacksRecyclerViewAdapter.SimpleViewHolder> {

    private Pair<List<String>, List<String>> mIconPacks;
    private Launcher mLauncher;
    private boolean mNoIconPacks;

    IconPacksRecyclerViewAdapter(Launcher launcher, Pair<List<String>, List<String>> iconPacks, boolean noIconPacks) {

        mLauncher = launcher;
        mIconPacks = iconPacks;
        mNoIconPacks = noIconPacks;
    }

    @Override
    public SimpleViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {

        // inflate recycler view items layout
        View itemView = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.icon_item, parent, false);
        return new SimpleViewHolder(itemView);
    }

    @Override
    public void onBindViewHolder(SimpleViewHolder holder, int position) {

        ApplicationInfo applicationInfo;
        PackageManager packageManager = mLauncher.getPackageManager();

        Drawable icon = mLauncher.getResources().getDrawable(R.drawable.ic_google_play_dark, mLauncher.getTheme());

        if (!mNoIconPacks) {
            try {
                applicationInfo = packageManager.getApplicationInfo(mIconPacks.first.get(holder.getAdapterPosition()), 0);
                icon = packageManager.getApplicationIcon(applicationInfo);
            } catch (PackageManager.NameNotFoundException e) {
                e.printStackTrace();
            }
        }

        holder.iconPackIcon.setImageDrawable(icon);
        holder.iconPackLabel.setText(mIconPacks.second.get(holder.getAdapterPosition()));
    }

    @Override
    public int getItemCount() {

        //get array length
        return mIconPacks.second.size();
    }

    //simple view holder implementing click listener and with itemView as arguments
    class SimpleViewHolder extends RecyclerView.ViewHolder implements View.OnClickListener {

        private ImageView iconPackIcon;
        private TextView iconPackLabel;

        SimpleViewHolder(View itemView) {
            super(itemView);

            //get the views here
            iconPackIcon = (ImageView) itemView.findViewById(R.id.app_icon);
            iconPackLabel = (TextView) itemView.findViewById(R.id.app_label);

            //enable click
            itemView.setOnClickListener(this);
        }

        @Override
        public void onClick(View v) {

            if (mNoIconPacks) {

                RevealEditView.close(mLauncher, true, false, null);
            } else {
                mLauncher.openIconChooserForIconPack(mIconPacks.first.get(getAdapterPosition()));
            }
        }
    }
}