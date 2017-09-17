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

import android.content.Context;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.ImageView;
import android.widget.TextView;

import java.util.List;

class MultiSelectRecyclerViewAdapter extends SelectableAdapter<MultiSelectRecyclerViewAdapter.ViewHolder> {

    private List<ResolveInfo> mResolveInfos;
    private ItemClickListener mClickListener;
    private PackageManager mPackageManager;

    MultiSelectRecyclerViewAdapter(Context context, List<ResolveInfo> resolveInfos, ItemClickListener clickListener) {
        mResolveInfos = resolveInfos;
        mClickListener = clickListener;
        mPackageManager = context.getPackageManager();
    }

    // Create new views
    @Override
    public MultiSelectRecyclerViewAdapter.ViewHolder onCreateViewHolder(ViewGroup parent,
                                                                        int viewType) {
        View itemLayoutView = LayoutInflater.from(parent.getContext()).inflate(
                R.layout.hide_item, null);

        return new ViewHolder(itemLayoutView, mClickListener);
    }

    @Override
    public void onBindViewHolder(ViewHolder viewHolder, int position) {

        String packageName = mResolveInfos.get(position).activityInfo.packageName;
        viewHolder.label.setText(mResolveInfos.get(position).loadLabel(mPackageManager));
        viewHolder.icon.setImageDrawable(mResolveInfos.get(position).loadIcon(mPackageManager));

        viewHolder.checkBox.setChecked(isSelected(packageName));
    }

    @Override
    public int getItemCount() {
        return mResolveInfos.size();
    }

    class ViewHolder extends RecyclerView.ViewHolder implements View.OnClickListener {

        private TextView label;
        private ImageView icon;
        private CheckBox checkBox;
        private ItemClickListener listener;

        ViewHolder(View itemLayoutView, ItemClickListener listener) {
            super(itemLayoutView);

            this.listener = listener;

            label = (TextView) itemLayoutView.findViewById(R.id.label);
            icon = (ImageView) itemLayoutView.findViewById(R.id.icon);
            checkBox = (CheckBox) itemLayoutView.findViewById(R.id.check);

            itemLayoutView.setOnClickListener(this);
        }

        @Override
        public void onClick(View v) {
            if (listener != null) {
                listener.onItemClicked(getAdapterPosition());
            }
        }
    }

    interface ItemClickListener {
        void onItemClicked(int position);
    }
}

