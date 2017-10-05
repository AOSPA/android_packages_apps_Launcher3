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

    //simple recycler view adapter with activity and int array as arguments
    IconPacksRecyclerViewAdapter(Launcher launcher, Pair<List<String>, List<String>> iconPacks) {

        mLauncher = launcher;
        mIconPacks = iconPacks;
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

        try {
            applicationInfo = packageManager.getApplicationInfo(mIconPacks.first.get(holder.getAdapterPosition()), 0);
            holder.iconPackIcon.setImageDrawable(packageManager.getApplicationIcon(applicationInfo));
        } catch (PackageManager.NameNotFoundException e) {
            e.printStackTrace();
        }

        holder.iconPackLabel.setText(mIconPacks.second.get(holder.getAdapterPosition()));
    }

    @Override
    public int getItemCount() {

        //get array length
        return mIconPacks.first.size();
    }

    //simple view holder implementing click and long click listeners and with activity and itemView as arguments
    class SimpleViewHolder extends RecyclerView.ViewHolder implements View.OnClickListener {

        private ImageView iconPackIcon;
        private TextView iconPackLabel;

        SimpleViewHolder(View itemView) {
            super(itemView);

            //get the views here
            iconPackIcon = (ImageView) itemView.findViewById(R.id.app_icon);
            iconPackLabel = (TextView) itemView.findViewById(R.id.app_label);

            //enable click and on long click
            itemView.setOnClickListener(this);
        }

        //add click
        @Override
        public void onClick(View v) {

            mLauncher.openIconChooserForIconPack(mIconPacks.first.get(getAdapterPosition()));
        }
    }
}