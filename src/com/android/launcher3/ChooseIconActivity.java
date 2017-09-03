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
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.support.v4.graphics.ColorUtils;
import android.support.v4.graphics.drawable.DrawableCompat;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.GridLayoutManager;
import android.support.v7.widget.GridLayoutManager.SpanSizeLookup;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.Toolbar;
import android.text.Editable;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.TextWatcher;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;

import com.android.launcher3.graphics.TintedDrawableSpan;

import java.util.ArrayList;
import java.util.List;

public class ChooseIconActivity extends AppCompatActivity {

    private String mCurrentPackageLabel;
    private String mCurrentPackageName;
    private String mIconPackPackageName;

    private GridLayoutManager mGridLayout;
    private ProgressBar mProgressBar;
    private RecyclerView mIconsGrid;
    private IconCache mIconCache;
    private IconsHandler mIconsHandler;

    private static ItemInfo sItemInfo;

    private final ViewGroup nullParent = null;
    private GridAdapter mGridAdapter;
    private EditText editTextSearch;
    private List<String> allIcons, matchingIcons;

    @Override
    public void onBackPressed() {
        if (editTextSearch.hasFocus()) {

            //hide soft keyboard
            InputMethodManager imm = (InputMethodManager)getSystemService(Context.INPUT_METHOD_SERVICE);
            if (getCurrentFocus() != null) {
                imm.hideSoftInputFromWindow(getCurrentFocus().getWindowToken(), 0);
            }

            //clear focus
            editTextSearch.clearComposingText();
            editTextSearch.clearFocus();
        } else {
            finish();
        }
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.all_icons_view);

        //set the toolbar
        final Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        //provide back navigation
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }

        toolbar.setNavigationOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                onBackPressed();
            }
        });

        editTextSearch = findViewById(R.id.edtSearch);

        mCurrentPackageName = getIntent().getStringExtra("app_package");
        mCurrentPackageLabel = getIntent().getStringExtra("app_label");
        mIconPackPackageName = getIntent().getStringExtra("icon_pack_package");

        mIconCache = LauncherAppState.getInstance().getIconCache();
        mIconsHandler = IconCache.getIconsHandler(this);

        int itemSpacing = getResources().getDimensionPixelSize(R.dimen.grid_item_spacing);
        mIconsGrid = (RecyclerView) findViewById(R.id.icons_grid);
        mIconsGrid.setHasFixedSize(true);
        mIconsGrid.addItemDecoration(new GridItemSpacer(itemSpacing));
        mGridLayout = new GridLayoutManager(this, 4);
        mIconsGrid.setLayoutManager(mGridLayout);
        mIconsGrid.setAlpha(0.0f);
        toolbar.setAlpha(0.0f);
        editTextSearch.setAlpha(0.0f);

        mProgressBar = (ProgressBar) findViewById(R.id.icons_grid_progress);

        final int accent = Utilities.getColorAccent(this);
        final int opaqueAccent = ColorUtils.setAlphaComponent(accent, 100);
        tintWidget(editTextSearch, opaqueAccent);

        // Update the hint to contain the icon.
        // Prefix the original hint with two spaces. The first space gets replaced by the icon
        // using span. The second space is used for a singe space character between the hint
        // and the icon.
        SpannableString spanned = new SpannableString("  " + editTextSearch.getHint());
        spanned.setSpan(new TintedDrawableSpan(this, R.drawable.ic_allapps_search), 0, 1, Spannable.SPAN_EXCLUSIVE_INCLUSIVE);
        editTextSearch.setHint(spanned);

        new Thread(new Runnable() {
            @Override
            public void run() {
                final Activity activity = ChooseIconActivity.this;
                allIcons = mIconsHandler.getAllDrawables(mIconPackPackageName);
                matchingIcons = mIconsHandler.getMatchingDrawables(mCurrentPackageName);
                activity.runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        mGridAdapter = new GridAdapter(allIcons, matchingIcons);
                        mIconsGrid.setAdapter(mGridAdapter);
                        mProgressBar.setVisibility(View.GONE);
                        mIconsGrid.animate().alpha(1.0f);
                        toolbar.animate().alpha(1.0f);

                        editTextSearch.animate().alpha(1.0f);
                        editTextSearch.addTextChangedListener(new TextWatcher() {
                            @Override
                            public void beforeTextChanged(CharSequence charSequence, int i, int i1, int i2) {

                            }

                            @Override
                            public void onTextChanged(CharSequence charSequence, int i, int i1, int i2) {

                            }

                            @Override
                            public void afterTextChanged(Editable editable) {

                                String mQuery = editable.toString();
                                IconsSearchUtils.filter(mQuery, matchingIcons, allIcons, mGridAdapter);
                            }
                        });

                        editTextSearch.setOnFocusChangeListener(new View.OnFocusChangeListener() {
                            @Override
                            public void onFocusChange(View view, boolean hasFocus) {

                                if (hasFocus) tintWidget(editTextSearch, accent);
                            }
                        });
                    }
                });
            }
        }).start();
    }

    public static void setItemInfo(ItemInfo info) {
        sItemInfo = info;
    }

    private void tintWidget(View view, int color) {
        Drawable wrappedDrawable = DrawableCompat.wrap(view.getBackground());
        DrawableCompat.setTint(wrappedDrawable.mutate(), color);
        view.setBackground(wrappedDrawable);
    }

    private class GridAdapter extends RecyclerView.Adapter<GridAdapter.ViewHolder> {

        private static final int TYPE_MATCHING_HEADER = 0;
        private static final int TYPE_MATCHING_ICONS = 1;
        private static final int TYPE_ALL_HEADER = 2;
        private static final int TYPE_ALL_ICONS = 3;

        private boolean mNoMatchingDrawables;

        private List<String> mAllDrawables = new ArrayList<>();
        private List<String> mMatchingDrawables = new ArrayList<>();

        private GridAdapter(List<String> allDrawables, List<String> matchingDrawables) {
            mAllDrawables.add(null);
            mAllDrawables.addAll(allDrawables);
            mMatchingDrawables.add(null);
            mMatchingDrawables.addAll(matchingDrawables);
            mGridLayout.setSpanSizeLookup(mSpanSizeLookup);
            mNoMatchingDrawables = matchingDrawables.isEmpty();
            if (mNoMatchingDrawables) {
                mMatchingDrawables.clear();
            }
        }

        void filterList(List<String> filteredAllDrawables, List<String> filteredMatchingDrawables) {

            mAllDrawables = filteredAllDrawables;
            mMatchingDrawables = filteredMatchingDrawables;
            notifyDataSetChanged();
        }

        @Override
        public int getItemViewType(int position) {
            if (!mNoMatchingDrawables && position < mMatchingDrawables.size() &&
                    mMatchingDrawables.get(position) == null) {
                return TYPE_MATCHING_HEADER;
            }

            if (!mNoMatchingDrawables && position > TYPE_MATCHING_HEADER &&
                    position < mMatchingDrawables.size()) {
                return TYPE_MATCHING_ICONS;
            }

            if (position == mMatchingDrawables.size()) {
                return TYPE_ALL_HEADER;
            }
            return TYPE_ALL_ICONS;
        }

        @Override
        public int getItemCount() {
            return mAllDrawables.size();
        }

        @Override
        public ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {

            if (viewType == TYPE_MATCHING_HEADER) {
                TextView text = (TextView) getLayoutInflater().inflate(
                        R.layout.all_icons_view_header, nullParent);
                text.setText(R.string.similar_icons);
                return new ViewHolder(text);
            }
            if (viewType == TYPE_ALL_HEADER) {
                TextView text = (TextView) getLayoutInflater().inflate(
                        R.layout.all_icons_view_header, nullParent);
                text.setText(R.string.all_icons);
                return new ViewHolder(text);
            }
            return new ViewHolder(getLayoutInflater().inflate(R.layout.icon_item, nullParent));
        }

        @Override
        public void onBindViewHolder(ViewHolder holder, final int position) {
            if (holder.getItemViewType() != TYPE_MATCHING_HEADER
                    && holder.getItemViewType() != TYPE_ALL_HEADER) {
                boolean drawablesMatching = holder.getItemViewType() == TYPE_MATCHING_ICONS;
                final List<String> drawables = drawablesMatching ?
                        mMatchingDrawables : mAllDrawables;
                if (position >= drawables.size()) {
                    return;
                }
                holder.itemView.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        Drawable icon = mIconsHandler.loadDrawable(
                                mIconPackPackageName, drawables.get(position), true);
                        if (icon != null) {
                            mIconCache.addCustomInfoToDataBase(icon, sItemInfo, mCurrentPackageLabel);
                        }
                        ChooseIconActivity.this.finish();
                    }
                });
                Drawable icon = null;
                String drawable = drawables.get(position);
                try {
                    icon = mIconsHandler.loadDrawable(mIconPackPackageName, drawable, true);
                } catch (OutOfMemoryError e) {
                    // time for a new device?
                    e.printStackTrace();
                }
                if (icon != null) {
                    holder.icon.setImageDrawable(icon);
                    holder.label.setText(drawable);
                }
            }
        }

        private final SpanSizeLookup mSpanSizeLookup = new SpanSizeLookup() {
            @Override
            public int getSpanSize(int position) {
                return getItemViewType(position) == TYPE_MATCHING_HEADER
                        || getItemViewType(position) == TYPE_ALL_HEADER ?
                        4 : 1;
            }
        };

        private class ViewHolder extends RecyclerView.ViewHolder {

            private TextView label;
            private ImageView icon;

            private ViewHolder(View v) {
                super(v);
                icon = itemView.findViewById(R.id.icon);
                label = itemView.findViewById(R.id.name);
            }
        }
    }

    private class GridItemSpacer extends RecyclerView.ItemDecoration {
        private int spacing;

        private GridItemSpacer(int spacing) {
            this.spacing = spacing;
        }

        @Override
        public void getItemOffsets(Rect outRect, View view, RecyclerView parent,
                 RecyclerView.State state) {
            outRect.top = spacing;
        }
    }
}
