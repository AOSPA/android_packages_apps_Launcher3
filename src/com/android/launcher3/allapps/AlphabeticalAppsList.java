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
package com.android.launcher3.allapps;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.support.v7.widget.RecyclerView;
import android.util.Log;
import com.android.launcher3.AppInfo;
import com.android.launcher3.Launcher;
import com.android.launcher3.LauncherAppState;
import com.android.launcher3.compat.AlphabeticIndexCompat;
import com.android.launcher3.compat.UserHandleCompat;
import com.android.launcher3.ItemInfo;
import com.android.launcher3.model.AbstractUserComparator;
import com.android.launcher3.hideapp.HideAppInfo;
import com.android.launcher3.hideapp.HideAppService;
import com.android.launcher3.model.AppNameComparator;
import com.android.launcher3.R;
import com.android.launcher3.util.ComponentKey;
import com.android.launcher3.xml.ComponentInfo;
import com.android.launcher3.xml.ComponentParserImpl;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.IOException;
import java.text.Collator;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

/**
 * The alphabetically sorted list of applications.
 */
public class AlphabeticalAppsList {

    public static final String TAG = "AlphabeticalAppsList";
    private static final boolean DEBUG = false;
    private static final boolean DEBUG_PREDICTIONS = false;

    private static final int FAST_SCROLL_FRACTION_DISTRIBUTE_BY_ROWS_FRACTION = 0;
    private static final int FAST_SCROLL_FRACTION_DISTRIBUTE_BY_NUM_SECTIONS = 1;

    private static final int ALL_APP_ALPHABETICAL_MODE = 1;
    private static final int ALL_APP_INSTALLATION_TIME_MODE = 2;
    private static String ALL_APP_SORT_MODE_KEY="all_app_sort_mode";

    private final int mFastScrollDistributionMode = FAST_SCROLL_FRACTION_DISTRIBUTE_BY_NUM_SECTIONS;

    /**
     * Info about a section in the alphabetic list
     */
    public static class SectionInfo {
        // The number of applications in this section
        public int numApps;
        // The section break AdapterItem for this section
        public AdapterItem sectionBreakItem;
        // The first app AdapterItem for this section
        public AdapterItem firstAppItem;
    }

    /**
     * Info about a fast scroller section, depending if sections are merged, the fast scroller
     * sections will not be the same set as the section headers.
     */
    public static class FastScrollSectionInfo {
        // The section name
        public String sectionName;
        // The AdapterItem to scroll to for this section
        public AdapterItem fastScrollToItem;
        // The touch fraction that should map to this fast scroll section info
        public float touchFraction;

        public FastScrollSectionInfo(String sectionName) {
            this.sectionName = sectionName;
        }
    }

    /**
     * Info about a particular adapter item (can be either section or app)
     */
    public static class AdapterItem {
        /** Common properties */
        // The index of this adapter item in the list
        public int position;
        // The type of this item
        public int viewType;

        /** Section & App properties */
        // The section for this item
        public SectionInfo sectionInfo;

        /** App-only properties */
        // The section name of this app.  Note that there can be multiple items with different
        // sectionNames in the same section
        public String sectionName = null;
        // The index of this app in the section
        public int sectionAppIndex = -1;
        // The row that this item shows up on
        public int rowIndex;
        // The index of this app in the row
        public int rowAppIndex;
        // The associated AppInfo for the app
        public AppInfo appInfo = null;
        // The index of this app not including sections
        public int appIndex = -1;

        public static AdapterItem asSectionBreak(int pos, SectionInfo section) {
            AdapterItem item = new AdapterItem();
            item.viewType = AllAppsGridAdapter.SECTION_BREAK_VIEW_TYPE;
            item.position = pos;
            item.sectionInfo = section;
            section.sectionBreakItem = item;
            return item;
        }

        public static AdapterItem asPredictedApp(int pos, SectionInfo section, String sectionName,
                int sectionAppIndex, AppInfo appInfo, int appIndex) {
            AdapterItem item = asApp(pos, section, sectionName, sectionAppIndex, appInfo, appIndex);
            item.viewType = AllAppsGridAdapter.PREDICTION_ICON_VIEW_TYPE;
            return item;
        }

        public static AdapterItem asApp(int pos, SectionInfo section, String sectionName,
                int sectionAppIndex, AppInfo appInfo, int appIndex) {
            AdapterItem item = new AdapterItem();
            item.viewType = AllAppsGridAdapter.ICON_VIEW_TYPE;
            item.position = pos;
            item.sectionInfo = section;
            item.sectionName = sectionName;
            item.sectionAppIndex = sectionAppIndex;
            item.appInfo = appInfo;
            item.appIndex = appIndex;
            return item;
        }

        public static AdapterItem asEmptySearch(int pos) {
            AdapterItem item = new AdapterItem();
            item.viewType = AllAppsGridAdapter.EMPTY_SEARCH_VIEW_TYPE;
            item.position = pos;
            return item;
        }

        public static AdapterItem asDivider(int pos) {
            AdapterItem item = new AdapterItem();
            item.viewType = AllAppsGridAdapter.SEARCH_MARKET_DIVIDER_VIEW_TYPE;
            item.position = pos;
            return item;
        }

        public static AdapterItem asMarketSearch(int pos) {
            AdapterItem item = new AdapterItem();
            item.viewType = AllAppsGridAdapter.SEARCH_MARKET_VIEW_TYPE;
            item.position = pos;
            return item;
        }
    }

    /**
     * Common interface for different merging strategies.
     */
    public interface MergeAlgorithm {
        boolean continueMerging(SectionInfo section, SectionInfo withSection,
                int sectionAppCount, int numAppsPerRow, int mergeCount);
    }

    private Launcher mLauncher;

    // The set of apps from the system not including predictions
    private final List<AppInfo> mApps = new ArrayList<>();
    private final HashMap<ComponentKey, AppInfo> mComponentToAppMap = new HashMap<>();

    // The set of filtered apps with the current filter
    private List<AppInfo> mFilteredApps = new ArrayList<>();
    // The current set of adapter items
    private List<AdapterItem> mAdapterItems = new ArrayList<>();
    // The set of sections for the apps with the current filter
    private List<SectionInfo> mSections = new ArrayList<>();
    // The set of sections that we allow fast-scrolling to (includes non-merged sections)
    private List<FastScrollSectionInfo> mFastScrollerSections = new ArrayList<>();
    // The set of predicted app component names
    private List<ComponentKey> mPredictedAppComponents = new ArrayList<>();
    // The set of predicted apps resolved from the component names and the current set of apps
    private List<AppInfo> mPredictedApps = new ArrayList<>();
    // The of ordered component names as a result of a search query
    private ArrayList<ComponentKey> mSearchResults;
    private HashMap<CharSequence, String> mCachedSectionNames = new HashMap<>();
    private RecyclerView.Adapter mAdapter;
    private AlphabeticIndexCompat mIndexer;
    private AppNameComparator mAppNameComparator;
    private MergeAlgorithm mMergeAlgorithm;
    private int mNumAppsPerRow;
    private int mNumPredictedAppsPerRow;
    private int mNumAppRowsInAdapter;
    private int  mAllAppListSortMode;
    private SharedPreferences mAllAppListPreferences;
    private boolean isHideAppsMode = false;
    private List<HideAppInfo> mHideApps = new ArrayList<HideAppInfo>() ;
    private List<AppInfo> mRemoveApps = new ArrayList<>();

    public AlphabeticalAppsList(Context context) {
        mLauncher = (Launcher) context;
        mIndexer = new AlphabeticIndexCompat(context);
        mAppNameComparator = new AppNameComparator(context);
        mAllAppListPreferences = mLauncher.getSharedPreferences(ALL_APP_SORT_MODE_KEY,
                Context.MODE_PRIVATE);
        mAllAppListSortMode = mAllAppListPreferences.getInt(ALL_APP_SORT_MODE_KEY,
                ALL_APP_ALPHABETICAL_MODE);
    }

    /**
     * Sets the number of apps per row.
     */
    public void setNumAppsPerRow(int numAppsPerRow, int numPredictedAppsPerRow,
            MergeAlgorithm mergeAlgorithm) {
        mNumAppsPerRow = numAppsPerRow;
        mNumPredictedAppsPerRow = numPredictedAppsPerRow;
        mMergeAlgorithm = mergeAlgorithm;

        updateAdapterItems();
    }

    /**
     * Sets the adapter to notify when this dataset changes.
     */
    public void setAdapter(RecyclerView.Adapter adapter) {
        mAdapter = adapter;
    }
    public RecyclerView.Adapter getAdapter() {
        return mAdapter;
    }

    /**
     * Returns all the apps.
     */
    public List<AppInfo> getApps() {
        return mApps;
    }

    /**
     * Returns sections of all the current filtered applications.
     */
    public List<SectionInfo> getSections() {
        return mSections;
    }

    /**
     * Returns fast scroller sections of all the current filtered applications.
     */
    public List<FastScrollSectionInfo> getFastScrollerSections() {
        return mFastScrollerSections;
    }

    /**
     * Returns the current filtered list of applications broken down into their sections.
     */
    public List<AdapterItem> getAdapterItems() {
        return mAdapterItems;
    }

    public List<AppInfo> getRemoveApps(){
        return mRemoveApps;
    }

    public boolean isHideApp(AppInfo item){
        for(HideAppInfo info : mHideApps){
            if(item.componentName.getPackageName().equals(info.getComponentPackage()) &&
               item.componentName.getClassName().equals(info.getComponentClass())){
                    return true;
                }
            }
        return false;
    }

    public void removeHideapp(){
        List<AppInfo> removepack = new ArrayList<>();
        for(AppInfo item : mApps){
            for(HideAppInfo info : mHideApps){
                if(item.componentName.getPackageName().equals(info.getComponentPackage()) &&
                item.componentName.getClassName().equals(info.getComponentClass())){
                    removepack.add(item);
                }
            }
        }
        if(mHideApps.size() == 0){
            mRemoveApps.clear();
        }
        if(removepack.size() > 0){
            mRemoveApps.clear();
            mRemoveApps.addAll(removepack);
            removeApps(mRemoveApps);
        }
    }

    public List<HideAppInfo>  readHideAppList() throws Exception{
        List<HideAppInfo> hideapps = new ArrayList<HideAppInfo>() ;

        File xmlFile = new File(mLauncher.getFilesDir(), "hide.xml");
        if (xmlFile == null || (xmlFile != null && !xmlFile.exists())) {
            return hideapps; // hide list is empty, directly return hide list
        }
        FileInputStream inputStream = new FileInputStream(xmlFile);
        try {
            hideapps = HideAppService.read(inputStream);
        } catch (Exception e) {
            e.printStackTrace();
        }
        mHideApps.clear();
        mHideApps.addAll(hideapps);
        return hideapps;
    }
    public void saveHideAppList() throws Exception{
        File xmlFile = new File(mLauncher.getFilesDir(), "hide.xml");
        FileOutputStream outStream = new FileOutputStream(xmlFile);
        HideAppService.save(mHideApps, outStream);
    }

    public void showHideapp(){
        addApps(mRemoveApps);
    }


    /**
     * Returns the number of rows of applications (not including predictions)
     */
    public int getNumAppRows() {
        return mNumAppRowsInAdapter;
    }

    /**
     * Returns the number of applications in this list.
     */
    public int getNumFilteredApps() {
        return mFilteredApps.size();
    }

    /**
     * Returns whether there are is a filter set.
     */
    public boolean hasFilter() {
        return (mSearchResults != null);
    }

    /**
     * Returns whether there are no filtered results.
     */
    public boolean hasNoFilteredResults() {
        return (mSearchResults != null) && mFilteredApps.isEmpty();
    }

    public boolean hasPredictedComponents() {
        return (mPredictedAppComponents != null && mPredictedAppComponents.size() > 0);
    }

    /**
     * Sets the sorted list of filtered components.
     */
    public boolean setOrderedFilter(ArrayList<ComponentKey> f) {
        if (mSearchResults != f) {
            boolean same = mSearchResults != null && mSearchResults.equals(f);
            mSearchResults = f;
            updateAdapterItems();
            return !same;
        }
        return false;
    }

    /**
     * Sets the current set of predicted apps.  Since this can be called before we get the full set
     * of applications, we should merge the results only in onAppsUpdated() which is idempotent.
     */
    public void setPredictedApps(List<ComponentKey> apps) {
        mPredictedAppComponents.clear();
        mPredictedAppComponents.addAll(apps);
        onAppsUpdated();
    }

    /**
     * Sets the current set of apps.
     */
    public void setApps(List<AppInfo> apps) {
        mComponentToAppMap.clear();
        addApps(apps);
    }

    /**
     * Adds new apps to the list.
     */
    public void addApps(List<AppInfo> apps) {
        updateApps(apps);
        if(!isHideAppsMode){
            try {
                readHideAppList();
                removeHideapp();
            } catch (Exception e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
            }
        }
    }

    /**
     * Updates existing apps in the list
     */
    public void updateApps(List<AppInfo> apps) {
        for (AppInfo app : apps) {
            mComponentToAppMap.put(app.toComponentKey(), app);
        }
        onAppsUpdated();
    }

    /**
     * Removes some apps from the list.
     */
    public void removeApps(List<AppInfo> apps) {
        for (AppInfo app : apps) {
            mComponentToAppMap.remove(app.toComponentKey());
        }
        onAppsUpdated();
    }
    public void lockPreloadingApps() {
        List<ComponentInfo> custom_app_list = null;
        try {
            InputStream is = mLauncher.getAssets().open("custom_main_menu.xml");
            ComponentParserImpl pbp = new ComponentParserImpl();
            custom_app_list = pbp.parse(is);
        } catch (IOException e) {
            e.printStackTrace();
        } catch (Exception e) {
            e.printStackTrace();
        }
        if (custom_app_list != null && !custom_app_list.isEmpty()) {
            for (int i = 0; i < custom_app_list.size(); i++) {
                for (int j = 0; j < mApps.size(); j++) {
                    AppInfo info = mApps.get(j);
                    ComponentInfo cinfo = custom_app_list.get(i);
                    if (null != info && null != cinfo && null != info.componentName &&
                            info.componentName.getPackageName().
                            equals(cinfo.getComponentPackage()) &&
                            info.componentName.getClassName().
                            equals(cinfo.getComponentClass())) {
                        int row = cinfo.getRow() + 1;
                        int column = cinfo.getColumn() + 1;
                        int lockpos = mNumAppsPerRow * (row - 1) + column;
                        if (column <= mNumAppsPerRow && lockpos <= mApps.size()) {
                            Collections.swap(mApps, lockpos - 1, j);
                        }
                    }
                }
            }
        }
    }

    public void saveAllAppSortPreferences(){
        Editor editor = mAllAppListPreferences.edit();
        editor.putInt(ALL_APP_SORT_MODE_KEY, new Integer(mAllAppListSortMode));
        editor.commit();
    }

    public void sortLetterApp(List<AppInfo> apps) {
        mAllAppListSortMode = ALL_APP_ALPHABETICAL_MODE;
        saveAllAppSortPreferences();
        Collections.sort(apps, mAppNameComparator.getAppInfoComparator());
        if(!isHideAppsMode){
            updateAdapterItems();
        }
    }

    public void sortInstallTimeApp(List<AppInfo> apps) {
        mAllAppListSortMode = ALL_APP_INSTALLATION_TIME_MODE;
        saveAllAppSortPreferences();
        Collections.sort(apps, new AbstractUserComparator<ItemInfo>(mLauncher) {
            public int compare(ItemInfo obj1, ItemInfo obj2) {
                AppInfo a = (AppInfo) obj1;
                AppInfo b = (AppInfo) obj2;

                long firstInstallTime1 = a.getfirstInstallTime();
                long firstInstallTime2 = b.getfirstInstallTime();

                if (firstInstallTime1 > firstInstallTime2) {
                    return 1;
                } else if (firstInstallTime1 < firstInstallTime2) {
                    return -1;
                } else {
                    int result = compareTitles(a, b);
                    if (result == 0) {
                        return super.compare(a, b);
                    }
                    return result;
                }
            }
        });
        if(!isHideAppsMode){
            updateAdapterItems();
        }
    }

    int compareTitles(AppInfo a, AppInfo b) {
        // Ensure that we de-prioritize any titles that don't start with a linguistic letter or
        // digit
        String titleA = a.title.toString();
        String titleB = b.title.toString();
        boolean aStartsWithLetter = (titleA.length() > 0) &&
                Character.isLetterOrDigit(titleA.codePointAt(0));
        boolean bStartsWithLetter = (titleB.length() > 0) &&
                Character.isLetterOrDigit(titleB.codePointAt(0));
        if (aStartsWithLetter && !bStartsWithLetter) {
            return -1;
        } else if (!aStartsWithLetter && bStartsWithLetter) {
            return 1;
        }

        // Order by the title in the current locale
        int result = Collator.getInstance().compare(titleA, titleB);
        if (result == 0) {
            AppInfo aAppInfo = (AppInfo) a;
            AppInfo bAppInfo = (AppInfo) b;
            // If two apps have the same title, then order by the component name
            result = aAppInfo.componentName.compareTo(bAppInfo.componentName);
            if (result == 0) {
                // If the two apps are the same component, then prioritize by the order
                // that
                // the app user was created (prioritizing the main user's apps)
                return result;
            }
        }
        return result;
    }

    public void  setHideAppsMode(boolean mode){
        isHideAppsMode = mode;
        mAdapter.notifyDataSetChanged();
    }

    public boolean  getHideAppsMode(){
        return isHideAppsMode;
    }
    public List<HideAppInfo>  getHideApps(){
        return mHideApps;
    }

    public void setHideApps(List<HideAppInfo> hideApps) {
        mHideApps = hideApps;
    }

    /**
     * Updates internals when the set of apps are updated.
     */
    private void onAppsUpdated() {
        // Sort the list of apps
        mApps.clear();
        mApps.addAll(mComponentToAppMap.values());
        // As a special case for some languages (currently only Simplified Chinese), we may need to
        // coalesce sections
        Locale curLocale = mLauncher.getResources().getConfiguration().locale;
        TreeMap<String, ArrayList<AppInfo>> sectionMap = null;
        boolean localeRequiresSectionSorting = curLocale.equals(Locale.SIMPLIFIED_CHINESE);
        if (localeRequiresSectionSorting) {
            // Compute the section headers.  We use a TreeMap with the section name comparator to
            // ensure that the sections are ordered when we iterate over it later
            sectionMap = new TreeMap<>(mAppNameComparator.getSectionNameComparator());
            for (AppInfo info : mApps) {
                // Add the section to the cache
                String sectionName = getAndUpdateCachedSectionName(info.title);

                // Add it to the mapping
                ArrayList<AppInfo> sectionApps = sectionMap.get(sectionName);
                if (sectionApps == null) {
                    sectionApps = new ArrayList<>();
                    sectionMap.put(sectionName, sectionApps);
                }
                sectionApps.add(info);
            }

            // Add each of the section apps to the list in order
            List<AppInfo> allApps = new ArrayList<>(mApps.size());
            for (Map.Entry<String, ArrayList<AppInfo>> entry : sectionMap.entrySet()) {
                allApps.addAll(entry.getValue());
            }

            mApps.clear();
            mApps.addAll(allApps);
        } else {
            // Just compute the section headers for use below
            for (AppInfo info : mApps) {
                // Add the section to the cache
                getAndUpdateCachedSectionName(info.title);
            }
        }
        if (mAllAppListSortMode == ALL_APP_ALPHABETICAL_MODE) {
            Collections.sort(mApps, mAppNameComparator.getAppInfoComparator());
        } else if (mAllAppListSortMode == ALL_APP_INSTALLATION_TIME_MODE) {
            sortInstallTimeApp(mApps);
        }
        if (LauncherAppState.isCustomWorkspace()) {
            lockPreloadingApps();
        }
        if(isHideAppsMode){
            List<AppInfo> noHideApps = new ArrayList<>();
            for (AppInfo info : mApps) {
                if(!isHideApp(info)){
                    noHideApps.add(info);
                }
            }
            if(mAllAppListSortMode == ALL_APP_ALPHABETICAL_MODE){
                sortLetterApp(mRemoveApps);
            } else if(mAllAppListSortMode == ALL_APP_INSTALLATION_TIME_MODE){
                sortInstallTimeApp(mRemoveApps);
            }
            mApps.clear();
            mApps.addAll(mRemoveApps);
            mApps.addAll(noHideApps);
        }

        // Recompose the set of adapter items from the current set of apps
        updateAdapterItems();
    }

    /**
     * Updates the set of filtered apps with the current filter.  At this point, we expect
     * mCachedSectionNames to have been calculated for the set of all apps in mApps.
     */
    private void updateAdapterItems() {
        SectionInfo lastSectionInfo = null;
        String lastSectionName = null;
        FastScrollSectionInfo lastFastScrollerSectionInfo = null;
        int position = 0;
        int appIndex = 0;

        // Prepare to update the list of sections, filtered apps, etc.
        mFilteredApps.clear();
        mFastScrollerSections.clear();
        mAdapterItems.clear();
        mSections.clear();

        if (DEBUG_PREDICTIONS) {
            if (mPredictedAppComponents.isEmpty() && !mApps.isEmpty()) {
                mPredictedAppComponents.add(new ComponentKey(mApps.get(0).componentName,
                        UserHandleCompat.myUserHandle()));
                mPredictedAppComponents.add(new ComponentKey(mApps.get(0).componentName,
                        UserHandleCompat.myUserHandle()));
                mPredictedAppComponents.add(new ComponentKey(mApps.get(0).componentName,
                        UserHandleCompat.myUserHandle()));
                mPredictedAppComponents.add(new ComponentKey(mApps.get(0).componentName,
                        UserHandleCompat.myUserHandle()));
            }
        }

        // Process the predicted app components
        mPredictedApps.clear();
        if (mPredictedAppComponents != null && !mPredictedAppComponents.isEmpty() && !hasFilter()) {
            for (ComponentKey ck : mPredictedAppComponents) {
                AppInfo info = mComponentToAppMap.get(ck);
                if (info != null) {
                    mPredictedApps.add(info);
                } else {
                    if (LauncherAppState.isDogfoodBuild()) {
                        Log.e(TAG, "Predicted app not found: " + ck.flattenToString(mLauncher));
                    }
                }
                // Stop at the number of predicted apps
                if (mPredictedApps.size() == mNumPredictedAppsPerRow) {
                    break;
                }
            }

            if (!mPredictedApps.isEmpty()) {
                // Add a section for the predictions
                lastSectionInfo = new SectionInfo();
                lastFastScrollerSectionInfo = new FastScrollSectionInfo("");
                AdapterItem sectionItem = AdapterItem.asSectionBreak(position++, lastSectionInfo);
                mSections.add(lastSectionInfo);
                mFastScrollerSections.add(lastFastScrollerSectionInfo);
                mAdapterItems.add(sectionItem);

                // Add the predicted app items
                for (AppInfo info : mPredictedApps) {
                    AdapterItem appItem = AdapterItem.asPredictedApp(position++, lastSectionInfo,
                            "", lastSectionInfo.numApps++, info, appIndex++);
                    if (lastSectionInfo.firstAppItem == null) {
                        lastSectionInfo.firstAppItem = appItem;
                        lastFastScrollerSectionInfo.fastScrollToItem = appItem;
                    }
                    mAdapterItems.add(appItem);
                    mFilteredApps.add(info);
                }
            }
        }

        // Recreate the filtered and sectioned apps (for convenience for the grid layout) from the
        // ordered set of sections
        for (AppInfo info : getFiltersAppInfos()) {
            String sectionName = getAndUpdateCachedSectionName(info.title);

            // Create a new section if the section names do not match
            if (lastSectionInfo == null || !sectionName.equals(lastSectionName)) {
                lastSectionName = sectionName;
                lastSectionInfo = new SectionInfo();
                lastFastScrollerSectionInfo = new FastScrollSectionInfo(sectionName);
                mSections.add(lastSectionInfo);
                mFastScrollerSections.add(lastFastScrollerSectionInfo);

                // Create a new section item to break the flow of items in the list
                if (!hasFilter()) {
                    AdapterItem sectionItem = AdapterItem.asSectionBreak(position++, lastSectionInfo);
                    mAdapterItems.add(sectionItem);
                }
            }

            // Create an app item
            AdapterItem appItem = AdapterItem.asApp(position++, lastSectionInfo, sectionName,
                    lastSectionInfo.numApps++, info, appIndex++);
            if (lastSectionInfo.firstAppItem == null) {
                lastSectionInfo.firstAppItem = appItem;
                lastFastScrollerSectionInfo.fastScrollToItem = appItem;
            }
            mAdapterItems.add(appItem);
            mFilteredApps.add(info);
        }

        // Append the search market item if we are currently searching
        if (hasFilter()) {
            if (hasNoFilteredResults()) {
                mAdapterItems.add(AdapterItem.asEmptySearch(position++));
            } else {
                mAdapterItems.add(AdapterItem.asDivider(position++));
            }
            mAdapterItems.add(AdapterItem.asMarketSearch(position++));
        }

        // Merge multiple sections together as requested by the merge strategy for this device
        mergeSections();

        if (mNumAppsPerRow != 0) {
            // Update the number of rows in the adapter after we do all the merging (otherwise, we
            // would have to shift the values again)
            int numAppsInSection = 0;
            int numAppsInRow = 0;
            int rowIndex = -1;
            for (AdapterItem item : mAdapterItems) {
                item.rowIndex = 0;
                if (item.viewType == AllAppsGridAdapter.SECTION_BREAK_VIEW_TYPE) {
                    numAppsInSection = 0;
                } else if (item.viewType == AllAppsGridAdapter.ICON_VIEW_TYPE ||
                        item.viewType == AllAppsGridAdapter.PREDICTION_ICON_VIEW_TYPE) {
                    if (numAppsInSection % mNumAppsPerRow == 0) {
                        numAppsInRow = 0;
                        rowIndex++;
                    }
                    item.rowIndex = rowIndex;
                    item.rowAppIndex = numAppsInRow;
                    numAppsInSection++;
                    numAppsInRow++;
                }
            }
            mNumAppRowsInAdapter = rowIndex + 1;

            // Pre-calculate all the fast scroller fractions
            switch (mFastScrollDistributionMode) {
                case FAST_SCROLL_FRACTION_DISTRIBUTE_BY_ROWS_FRACTION:
                    float rowFraction = 1f / mNumAppRowsInAdapter;
                    for (FastScrollSectionInfo info : mFastScrollerSections) {
                        AdapterItem item = info.fastScrollToItem;
                        if (item.viewType != AllAppsGridAdapter.ICON_VIEW_TYPE &&
                                item.viewType != AllAppsGridAdapter.PREDICTION_ICON_VIEW_TYPE) {
                            info.touchFraction = 0f;
                            continue;
                        }

                        float subRowFraction = item.rowAppIndex * (rowFraction / mNumAppsPerRow);
                        info.touchFraction = item.rowIndex * rowFraction + subRowFraction;
                    }
                    break;
                case FAST_SCROLL_FRACTION_DISTRIBUTE_BY_NUM_SECTIONS:
                    float perSectionTouchFraction = 1f / mFastScrollerSections.size();
                    float cumulativeTouchFraction = 0f;
                    for (FastScrollSectionInfo info : mFastScrollerSections) {
                        AdapterItem item = info.fastScrollToItem;
                        if (item.viewType != AllAppsGridAdapter.ICON_VIEW_TYPE &&
                                item.viewType != AllAppsGridAdapter.PREDICTION_ICON_VIEW_TYPE) {
                            info.touchFraction = 0f;
                            continue;
                        }
                        info.touchFraction = cumulativeTouchFraction;
                        cumulativeTouchFraction += perSectionTouchFraction;
                    }
                    break;
            }
        }

        // Refresh the recycler view
        if (mAdapter != null) {
            mAdapter.notifyDataSetChanged();
        }
    }

    private List<AppInfo> getFiltersAppInfos() {
        if (mSearchResults == null) {
            return mApps;
        }

        ArrayList<AppInfo> result = new ArrayList<>();
        for (ComponentKey key : mSearchResults) {
            AppInfo match = mComponentToAppMap.get(key);
            if (match != null) {
                result.add(match);
            }
        }
        return result;
    }

    /**
     * Merges multiple sections to reduce visual raggedness.
     */
    private void mergeSections() {
        // Ignore merging until we have an algorithm and a valid row size
        if (mMergeAlgorithm == null || mNumAppsPerRow == 0) {
            return;
        }

        // Go through each section and try and merge some of the sections
        if (!hasFilter()) {
            int sectionAppCount = 0;
            for (int i = 0; i < mSections.size() - 1; i++) {
                SectionInfo section = mSections.get(i);
                sectionAppCount = section.numApps;
                int mergeCount = 1;

                // Merge rows based on the current strategy
                while (i < (mSections.size() - 1) &&
                        mMergeAlgorithm.continueMerging(section, mSections.get(i + 1),
                                sectionAppCount, mNumAppsPerRow, mergeCount)) {
                    SectionInfo nextSection = mSections.remove(i + 1);

                    // Remove the next section break
                    mAdapterItems.remove(nextSection.sectionBreakItem);
                    int pos = mAdapterItems.indexOf(section.firstAppItem);

                    // Point the section for these new apps to the merged section
                    int nextPos = pos + section.numApps;
                    for (int j = nextPos; j < (nextPos + nextSection.numApps); j++) {
                        AdapterItem item = mAdapterItems.get(j);
                        item.sectionInfo = section;
                        item.sectionAppIndex += section.numApps;
                    }

                    // Update the following adapter items of the removed section item
                    pos = mAdapterItems.indexOf(nextSection.firstAppItem);
                    for (int j = pos; j < mAdapterItems.size(); j++) {
                        AdapterItem item = mAdapterItems.get(j);
                        item.position--;
                    }
                    section.numApps += nextSection.numApps;
                    sectionAppCount += nextSection.numApps;

                    if (DEBUG) {
                        Log.d(TAG, "Merging: " + nextSection.firstAppItem.sectionName +
                                " to " + section.firstAppItem.sectionName +
                                " mergedNumRows: " + (sectionAppCount / mNumAppsPerRow));
                    }
                    mergeCount++;
                }
            }
        }
    }

    /**
     * Returns the cached section name for the given title, recomputing and updating the cache if
     * the title has no cached section name.
     */
    private String getAndUpdateCachedSectionName(CharSequence title) {
        String sectionName = mCachedSectionNames.get(title);
        if (sectionName == null) {
            sectionName = mIndexer.computeSectionName(title);
            mCachedSectionNames.put(title, sectionName);
        }
        return sectionName;
    }
}
