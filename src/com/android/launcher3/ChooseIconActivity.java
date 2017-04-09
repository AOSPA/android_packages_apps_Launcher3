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
import android.graphics.Bitmap;
import android.graphics.drawable.Drawable;
import android.graphics.PorterDuff.Mode;
import android.os.AsyncTask;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.GridView;
import android.widget.ImageView;
import android.widget.TextView;

import java.util.List;

public class ChooseIconActivity extends Activity {

    private String mCurrentPackageLabel;
    private String mCurrentPackageName;
    private String mIconPackPackageName;

    private GridView mMatchingIconsGrid;
    private GridView mIconGrid;
    private IconCache mIconCache;
    private IconsHandler mIconsHandler;

    private static ItemInfo sItemInfo;

    private float mIconMargin;
    private int mIconSize;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.all_icons_view);

        mCurrentPackageName = getIntent().getStringExtra("app_package");
        mCurrentPackageLabel = getIntent().getStringExtra("app_label");
        mIconPackPackageName = getIntent().getStringExtra("icon_pack_package");
        mIconsHandler = IconCache.getIconsHandler(this);

        mMatchingIconsGrid = (GridView) findViewById(R.id.matching_icons_grid);
        mIconGrid = (GridView) findViewById(R.id.icons_grid);
        mIconCache = LauncherAppState.getInstance().getIconCache();

        List<String> similarIcons = mIconsHandler.getMatchingDrawables(mCurrentPackageName);
        if (similarIcons.isEmpty()) {
            hideMatchingIcons();
        }

        mIconSize = getResources().getDimensionPixelSize(R.dimen.icon_pack_icon_size);
        mIconMargin = getResources().getDimensionPixelSize(R.dimen.icon_margin);

        new IconLoader().execute();
    }

    private void hideMatchingIcons() {
        TextView similarIconsText = (TextView) findViewById(R.id.similar_icons);
        similarIconsText.setVisibility(View.GONE);
        mMatchingIconsGrid.setVisibility(View.GONE);
    }

    public static void setItemInfo(ItemInfo info) {
        sItemInfo = info;
    }

    private class IconLoader extends AsyncTask<Void, Void, Void> {

        private GridAdapter mAllIconsGridAdapter;
        private GridAdapter mMatchingIconsGridAdapter;

        @Override
        protected Void doInBackground(Void... voids) {
            Activity activity = ChooseIconActivity.this;
            List<String> allDrawables = mIconsHandler.getAllDrawables(mIconPackPackageName);
            List<String> matchingDrawables = mIconsHandler.getMatchingDrawables(mCurrentPackageName);
            mAllIconsGridAdapter = new GridAdapter(allDrawables);
            mMatchingIconsGridAdapter = new GridAdapter(matchingDrawables);
            mIconGrid.setOnItemClickListener(new AdapterView.OnItemClickListener() {
                @Override
                public void onItemClick(AdapterView<?> parent, View v, int position, long id) {
                    Drawable icon = mIconsHandler.loadDrawable(allDrawables.get(position));
                    if (icon != null) {
                        mIconCache.addCustomInfoToDataBase(icon, sItemInfo, mCurrentPackageLabel);
                    }
                    activity.finish();
                }
            });
            mMatchingIconsGrid.setOnItemClickListener(new AdapterView.OnItemClickListener() {
                @Override
                public void onItemClick(AdapterView<?> parent, View v, int position, long id) {
                    Drawable icon = mIconsHandler.loadDrawable(matchingDrawables.get(position));
                    if (icon != null) {
                        mIconCache.addCustomInfoToDataBase(icon, sItemInfo, mCurrentPackageLabel);
                    }
                    activity.finish();
                }
            });
            return null;
        }

        @Override
        protected void onPostExecute(Void aVoid) {
            mIconGrid.setAlpha(0.0f);
            mIconGrid.animate().alpha(1.0f);
            mIconGrid.setAdapter(mAllIconsGridAdapter);
            mMatchingIconsGrid.setAlpha(0.0f);
            mMatchingIconsGrid.animate().alpha(1.0f);
            mMatchingIconsGrid.setAdapter(mMatchingIconsGridAdapter);
            super.onPostExecute(aVoid);
        }
    }

    private class GridAdapter extends BaseAdapter {

        private List<String> mDrawables;

        private GridAdapter(List<String> icons) {
            mDrawables = icons;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            ImageView icon;
            if (convertView == null) {
                icon = new ImageView(ChooseIconActivity.this);
                GridView.LayoutParams params = new GridView.LayoutParams(mIconSize, mIconSize);
                icon.setLayoutParams(params);
            } else {
                icon = (ImageView) convertView;
            }
            new LoadDrawableTask(icon, position).execute();
            return icon;
        }

        @Override
        public int getCount() {
            return mDrawables.size();
        }

        @Override
        public Object getItem(int position) {
            return mDrawables.get(position);
        }


        @Override
        public long getItemId(int position) {
            return position;
        }

        private class LoadDrawableTask extends AsyncTask<Void, Void, Drawable> {
            private int mPosition;
            private Drawable mDrawable;
            private ImageView mIcon;

            private LoadDrawableTask(ImageView icon, int position) {
                mIcon = icon;
                mPosition = position;
            }

            @Override
            protected Drawable doInBackground(Void[] voids) {
                if (mDrawables.size() <= mPosition) return mDrawable;
                String drawable = mDrawables.get(mPosition);
                try {
                    mDrawable = mIconsHandler.loadDrawable(drawable);
                } catch (OutOfMemoryError e) {
                    e.printStackTrace();
                }
                if (mDrawable == null) {
                    mDrawables.remove(drawable);
                }
                return mDrawable;
            }

            @Override
            protected void onPostExecute(Drawable drawable) {
                if (drawable != null) {
                    mIcon.setImageDrawable(drawable);
                } else {
                    GridAdapter.this.notifyDataSetChanged();
                }
                if (mDrawables.isEmpty()) {
                    hideMatchingIcons();
                }
                super.onPostExecute(drawable);
            }
        }
    }
}
