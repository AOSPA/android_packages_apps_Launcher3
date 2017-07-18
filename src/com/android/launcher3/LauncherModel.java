/*
 * Copyright (C) 2008 The Android Open Source Project
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

import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.ContentProviderOperation;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.content.pm.LauncherActivityInfo;
import android.net.Uri;
import android.os.Binder;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.Looper;
import android.os.Process;
import android.os.RemoteException;
import android.os.SystemClock;
import android.os.Trace;
import android.os.UserHandle;
import android.support.annotation.Nullable;
import android.text.TextUtils;
import android.util.Log;
import android.util.Pair;

import com.android.launcher3.compat.LauncherAppsCompat;
import com.android.launcher3.compat.PackageInstallerCompat.PackageInstallInfo;
import com.android.launcher3.compat.UserManagerCompat;
import com.android.launcher3.dynamicui.ExtractionUtils;
import com.android.launcher3.graphics.LauncherIcons;
import com.android.launcher3.model.AddWorkspaceItemsTask;
import com.android.launcher3.model.BgDataModel;
import com.android.launcher3.model.CacheDataUpdatedTask;
import com.android.launcher3.model.BaseModelUpdateTask;
import com.android.launcher3.model.LoaderResults;
import com.android.launcher3.model.LoaderTask;
import com.android.launcher3.model.ModelWriter;
import com.android.launcher3.model.PackageInstallStateChangedTask;
import com.android.launcher3.model.PackageItemInfo;
import com.android.launcher3.model.PackageUpdatedTask;
import com.android.launcher3.model.ShortcutsChangedTask;
import com.android.launcher3.model.UserLockStateChangedTask;
import com.android.launcher3.model.WidgetItem;
import com.android.launcher3.provider.LauncherDbUtils;
import com.android.launcher3.shortcuts.DeepShortcutManager;
import com.android.launcher3.shortcuts.ShortcutInfoCompat;
import com.android.launcher3.util.ComponentKey;
import com.android.launcher3.util.MultiHashMap;
import com.android.launcher3.util.PackageUserKey;
import com.android.launcher3.util.Preconditions;
import com.android.launcher3.util.Provider;
import com.android.launcher3.util.Thunk;
import com.android.launcher3.util.ViewOnDrawExecutor;

import java.io.FileDescriptor;
import java.io.PrintWriter;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.CancellationException;
import java.util.concurrent.Executor;

import com.qti.launcherunreadservice.IGetUnreadNumber;
import com.qti.launcherunreadservice.IUnreadNumberCallback;

/**
 * Maintains in-memory state of the Launcher. It is expected that there should be only one
 * LauncherModel object held in a static. Also provide APIs for updating the database state
 * for the Launcher.
 */
public class LauncherModel extends BroadcastReceiver
        implements LauncherAppsCompat.OnAppsChangedCallbackCompat {
    private static final boolean DEBUG_RECEIVER = false;

    static final String TAG = "Launcher.Model";

    private final MainThreadExecutor mUiExecutor = new MainThreadExecutor();
    @Thunk final LauncherAppState mApp;
    @Thunk final Object mLock = new Object();
    @Thunk
    LoaderTask mLoaderTask;
    @Thunk boolean mIsLoaderTaskRunning;

    @Thunk static final HandlerThread sWorkerThread = new HandlerThread("launcher-loader");
    static {
        sWorkerThread.start();
    }
    @Thunk static final Handler sWorker = new Handler(sWorkerThread.getLooper());

    // Indicates whether the current model data is valid or not.
    // We start off with everything not loaded. After that, we assume that
    // our monitoring of the package manager provides all updates and we never
    // need to do a requery. This is only ever touched from the loader thread.
    private boolean mModelLoaded;
    public boolean isModelLoaded() {
        synchronized (mLock) {
            return mModelLoaded && mLoaderTask == null;
        }
    }

    @Thunk WeakReference<Callbacks> mCallbacks;

    // < only access in worker thread >
    private final AllAppsList mBgAllAppsList;

    /**
     * All the static data should be accessed on the background thread, A lock should be acquired
     * on this object when accessing any data from this model.
     */
    static final BgDataModel sBgDataModel = new BgDataModel();

    // Runnable to check if the shortcuts permission has changed.
    private final Runnable mShortcutPermissionCheckRunnable = new Runnable() {
        @Override
        public void run() {
            if (mModelLoaded) {
                boolean hasShortcutHostPermission =
                        DeepShortcutManager.getInstance(mApp.getContext()).hasHostPermission();
                if (hasShortcutHostPermission != sBgDataModel.hasShortcutHostPermission) {
                    forceReload();
                }
            }
        }
    };

    /**
     * All the static data should be accessed on the background thread, A lock should be acquired
     * on this object when accessing any data from this model.
     */
    static final BgDataModel sBgDataModel = new BgDataModel();

    // </ only access in worker thread >

    private static final String LAUNCHER_UNREAD_SERVICE_PACKAGENAME =
            "com.qti.launcherunreadservice";
    private static final String LAUNCHER_UNREAD_SERVICE_CLASSNAME =
            "com.qti.launcherunreadservice.LauncherUnreadService";
    private final IconCache mIconCache;

    private final LauncherAppsCompat mLauncherApps;
    private final UserManagerCompat mUserManager;

    public interface Callbacks {
        public boolean setLoadOnResume();
        public int getCurrentWorkspaceScreen();
        public void clearPendingBinds();
        public void startBinding();
        public void bindItems(ArrayList<ItemInfo> shortcuts, int start, int end,
                              boolean forceAnimateIcons);
        public void bindScreens(ArrayList<Long> orderedScreenIds);
        public void finishFirstPageBind(ViewOnDrawExecutor executor);
        public void finishBindingItems();
        public void bindAppWidget(LauncherAppWidgetInfo info);
        public void bindAllApplications(ArrayList<AppInfo> apps);
        public void bindAppsAdded(ArrayList<Long> newScreens,
                                  ArrayList<ItemInfo> addNotAnimated,
                                  ArrayList<ItemInfo> addAnimated,
                                  ArrayList<AppInfo> addedApps);
        public void bindAppsUpdated(ArrayList<AppInfo> apps);
        public void bindPromiseAppProgressUpdated(PromiseAppInfo app);
        public void bindShortcutsChanged(ArrayList<ShortcutInfo> updated,
                ArrayList<ShortcutInfo> removed, UserHandle user);
        public void bindWidgetsRestored(ArrayList<LauncherAppWidgetInfo> widgets);
        public void bindRestoreItemsChange(HashSet<ItemInfo> updates);
        public void bindWorkspaceComponentsRemoved(
                HashSet<String> packageNames, HashSet<ComponentName> components,
                UserHandle user);
        public void bindAppInfosRemoved(ArrayList<AppInfo> appInfos);
        public void notifyWidgetProvidersChanged();
        public void bindAllWidgets(MultiHashMap<PackageItemInfo, WidgetItem> widgets);
        public void onPageBoundSynchronously(int page);
        public void executeOnNextDraw(ViewOnDrawExecutor executor);
        public void bindDeepShortcutMap(MultiHashMap<ComponentKey, String> deepShortcutMap);
    }

    //Mms/Call unread number data
    private HashMap<ComponentName, UnreadInfo> mUnreadMap =
            new HashMap<ComponentName, LauncherModel.UnreadInfo>();
    private IGetUnreadNumber mUnreadNumberService;
    private IBinder mToken = new Binder();
    private UnreadNumberChangeTask mUnreadUpdateTask = new UnreadNumberChangeTask();

    private IUnreadNumberCallback.Stub mUnreadNumberCallback = new IUnreadNumberCallback.Stub(){

        @Override
        public void onUnreadNumberChanged(ComponentName componentName, int unreadNum){
            synchronized (mUnreadMap) {
                final int cachedUnreadNum = getUnreadNumberOfComponent(componentName);
                if (cachedUnreadNum  != unreadNum){
                    mUnreadMap.put(componentName, new UnreadInfo(componentName, unreadNum));
                    postUnreadTask();
                }
            }
        }
    };

    private ServiceConnection mUnreadConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            mUnreadNumberService = IGetUnreadNumber.Stub.asInterface(service);
            try {
                if(mUnreadNumberService != null){
                    Log.d(TAG,"LauncherModel Unread onServiceConnected = "+mModelLoaded);
                    mUnreadNumberService.registerUnreadNumberCallback(mToken,
                            mUnreadNumberCallback);
                }
            } catch (RemoteException ex) {
                Log.e(TAG,"exception "+ex.toString());
            }
            updateUnreadNumber();
        }
        @Override
        public void onServiceDisconnected(ComponentName name) {
            mUnreadNumberService = null;
        }
    };

    LauncherModel(LauncherAppState app, IconCache iconCache, AppFilter appFilter) {
        mApp = app;
        mBgAllAppsList = new AllAppsList(iconCache, appFilter);
    }

    /** Runs the specified runnable immediately if called from the worker thread, otherwise it is
     * posted on the worker thread handler. */
    private static void runOnWorkerThread(Runnable r) {
        if (sWorkerThread.getThreadId() == Process.myTid()) {
            r.run();
        } else {
            // If we are not on the worker thread, then post to the worker handler
            sWorker.post(r);
        }
    }

    public void setPackageState(PackageInstallInfo installInfo) {
        enqueueModelUpdateTask(new PackageInstallStateChangedTask(installInfo));
    }

    /**
     * Updates the icons and label of all pending icons for the provided package name.
     */
    public void updateSessionDisplayInfo(final String packageName) {
        HashSet<String> packages = new HashSet<>();
        packages.add(packageName);
        enqueueModelUpdateTask(new CacheDataUpdatedTask(
                CacheDataUpdatedTask.OP_SESSION_UPDATE, Process.myUserHandle(), packages));
    }

    /**
     * Adds the provided items to the workspace.
     */
    public void addAndBindAddedWorkspaceItems(
            Provider<List<Pair<ItemInfo, Object>>> appsProvider) {
        enqueueModelUpdateTask(new AddWorkspaceItemsTask(appsProvider));
    }

    public ModelWriter getWriter(boolean hasVerticalHotseat) {
        return new ModelWriter(mApp.getContext(), sBgDataModel, hasVerticalHotseat);
    }

    static void checkItemInfoLocked(
            final long itemId, final ItemInfo item, StackTraceElement[] stackTrace) {
        ItemInfo modelItem = sBgDataModel.itemsIdMap.get(itemId);
        if (modelItem != null && item != modelItem) {
            // check all the data is consistent
            if (modelItem instanceof ShortcutInfo && item instanceof ShortcutInfo) {
                ShortcutInfo modelShortcut = (ShortcutInfo) modelItem;
                ShortcutInfo shortcut = (ShortcutInfo) item;
                if (modelShortcut.title.toString().equals(shortcut.title.toString()) &&
                        modelShortcut.intent.filterEquals(shortcut.intent) &&
                        modelShortcut.id == shortcut.id &&
                        modelShortcut.itemType == shortcut.itemType &&
                        modelShortcut.container == shortcut.container &&
                        modelShortcut.screenId == shortcut.screenId &&
                        modelShortcut.cellX == shortcut.cellX &&
                        modelShortcut.cellY == shortcut.cellY &&
                        modelShortcut.spanX == shortcut.spanX &&
                        modelShortcut.spanY == shortcut.spanY) {
                    // For all intents and purposes, this is the same object
                    return;
                }
            }

            // the modelItem needs to match up perfectly with item if our model is
            // to be consistent with the database-- for now, just require
            // modelItem == item or the equality check above
            String msg = "item: " + ((item != null) ? item.toString() : "null") +
                    "modelItem: " +
                    ((modelItem != null) ? modelItem.toString() : "null") +
                    "Error: ItemInfo passed to checkItemInfo doesn't match original";
            RuntimeException e = new RuntimeException(msg);
            if (stackTrace != null) {
                e.setStackTrace(stackTrace);
            }
            throw e;
        }
    }

    static void checkItemInfo(final ItemInfo item) {
        final StackTraceElement[] stackTrace = new Throwable().getStackTrace();
        final long itemId = item.id;
        Runnable r = new Runnable() {
            public void run() {
                synchronized (sBgDataModel) {
                    checkItemInfoLocked(itemId, item, stackTrace);
                }
            }
        };
        runOnWorkerThread(r);
    }

    /**
     * Update the order of the workspace screens in the database. The array list contains
     * a list of screen ids in the order that they should appear.
     */
    public static void updateWorkspaceScreenOrder(Context context, final ArrayList<Long> screens) {
        final ArrayList<Long> screensCopy = new ArrayList<Long>(screens);
        final ContentResolver cr = context.getContentResolver();
        final Uri uri = LauncherSettings.WorkspaceScreens.CONTENT_URI;

        // Remove any negative screen ids -- these aren't persisted
        Iterator<Long> iter = screensCopy.iterator();
        while (iter.hasNext()) {
            long id = iter.next();
            if (id < 0) {
                iter.remove();
            }
        }

        Runnable r = new Runnable() {
            @Override
            public void run() {
                ArrayList<ContentProviderOperation> ops = new ArrayList<ContentProviderOperation>();
                // Clear the table
                ops.add(ContentProviderOperation.newDelete(uri).build());
                int count = screensCopy.size();
                for (int i = 0; i < count; i++) {
                    ContentValues v = new ContentValues();
                    long screenId = screensCopy.get(i);
                    v.put(LauncherSettings.WorkspaceScreens._ID, screenId);
                    v.put(LauncherSettings.WorkspaceScreens.SCREEN_RANK, i);
                    ops.add(ContentProviderOperation.newInsert(uri).withValues(v).build());
                }

                try {
                    cr.applyBatch(LauncherProvider.AUTHORITY, ops);
                } catch (Exception ex) {
                    throw new RuntimeException(ex);
                }

                synchronized (sBgDataModel) {
                    sBgDataModel.workspaceScreens.clear();
                    sBgDataModel.workspaceScreens.addAll(screensCopy);
                }
            }
        };
        runOnWorkerThread(r);
    }

    /**
     * Set this as the current Launcher activity object for the loader.
     */
    public void initialize(Callbacks callbacks) {
        synchronized (mLock) {
            Preconditions.assertUIThread();
            mCallbacks = new WeakReference<>(callbacks);
        }
    }

    @Override
    public void onPackageChanged(String packageName, UserHandle user) {
        int op = PackageUpdatedTask.OP_UPDATE;
        enqueueModelUpdateTask(new PackageUpdatedTask(op, user, packageName));
    }

    @Override
    public void onPackageRemoved(String packageName, UserHandle user) {
        onPackagesRemoved(user, packageName);
    }

    public void onPackagesRemoved(UserHandle user, String... packages) {
        int op = PackageUpdatedTask.OP_REMOVE;
        enqueueModelUpdateTask(new PackageUpdatedTask(op, user, packages));
    }

    @Override
    public void onPackageAdded(String packageName, UserHandle user) {
        int op = PackageUpdatedTask.OP_ADD;
        enqueueModelUpdateTask(new PackageUpdatedTask(op, user, packageName));
    }

    @Override
    public void onPackagesAvailable(String[] packageNames, UserHandle user,
            boolean replacing) {
        enqueueModelUpdateTask(
                new PackageUpdatedTask(PackageUpdatedTask.OP_UPDATE, user, packageNames));
    }

    @Override
    public void onPackagesUnavailable(String[] packageNames, UserHandle user,
            boolean replacing) {
        if (!replacing) {
            enqueueModelUpdateTask(new PackageUpdatedTask(
                    PackageUpdatedTask.OP_UNAVAILABLE, user, packageNames));
        }
    }

    @Override
    public void onPackagesSuspended(String[] packageNames, UserHandle user) {
        enqueueModelUpdateTask(new PackageUpdatedTask(
                PackageUpdatedTask.OP_SUSPEND, user, packageNames));
    }

    @Override
    public void onPackagesUnsuspended(String[] packageNames, UserHandle user) {
        enqueueModelUpdateTask(new PackageUpdatedTask(
                PackageUpdatedTask.OP_UNSUSPEND, user, packageNames));
    }

    @Override
    public void onShortcutsChanged(String packageName, List<ShortcutInfoCompat> shortcuts,
            UserHandle user) {
        enqueueModelUpdateTask(new ShortcutsChangedTask(packageName, shortcuts, user, true));
    }

    public void updatePinnedShortcuts(String packageName, List<ShortcutInfoCompat> shortcuts,
            UserHandle user) {
        enqueueModelUpdateTask(new ShortcutsChangedTask(packageName, shortcuts, user, false));
    }

    /**
     * Call from the handler for ACTION_PACKAGE_ADDED, ACTION_PACKAGE_REMOVED and
     * ACTION_PACKAGE_CHANGED.
     */
    @Override
    public void onReceive(Context context, Intent intent) {
        if (DEBUG_RECEIVER) Log.d(TAG, "onReceive intent=" + intent);

        final String action = intent.getAction();
        if (Intent.ACTION_LOCALE_CHANGED.equals(action)) {
            // If we have changed locale we need to clear out the labels in all apps/workspace.
            forceReload();
        } else if (Intent.ACTION_MANAGED_PROFILE_ADDED.equals(action)
                || Intent.ACTION_MANAGED_PROFILE_REMOVED.equals(action)) {
            UserManagerCompat.getInstance(context).enableAndResetCache();
            forceReload();
        } else if (Intent.ACTION_MANAGED_PROFILE_AVAILABLE.equals(action) ||
                Intent.ACTION_MANAGED_PROFILE_UNAVAILABLE.equals(action) ||
                Intent.ACTION_MANAGED_PROFILE_UNLOCKED.equals(action)) {
            UserHandle user = intent.getParcelableExtra(Intent.EXTRA_USER);
            if (user != null) {
                if (Intent.ACTION_MANAGED_PROFILE_AVAILABLE.equals(action) ||
                        Intent.ACTION_MANAGED_PROFILE_UNAVAILABLE.equals(action)) {
                    enqueueModelUpdateTask(new PackageUpdatedTask(
                            PackageUpdatedTask.OP_USER_AVAILABILITY_CHANGE, user));
                }

                // ACTION_MANAGED_PROFILE_UNAVAILABLE sends the profile back to locked mode, so
                // we need to run the state change task again.
                if (Intent.ACTION_MANAGED_PROFILE_UNAVAILABLE.equals(action) ||
                        Intent.ACTION_MANAGED_PROFILE_UNLOCKED.equals(action)) {
                    enqueueModelUpdateTask(new UserLockStateChangedTask(user));
                }
            }
        } else if (Intent.ACTION_WALLPAPER_CHANGED.equals(action)) {
            ExtractionUtils.startColorExtractionServiceIfNecessary(context);
        }
    }

    /**
     * Reloads the workspace items from the DB and re-binds the workspace. This should generally
     * not be called as DB updates are automatically followed by UI update
     */
    public void forceReload() {
        synchronized (mLock) {
            // Stop any existing loaders first, so they don't set mModelLoaded to true later
            stopLoader();
            mModelLoaded = false;
        }

        // Do this here because if the launcher activity is running it will be restarted.
        // If it's not running startLoaderFromBackground will merely tell it that it needs
        // to reload.
        startLoaderFromBackground();
    }

    /**
     * When the launcher is in the background, it's possible for it to miss paired
     * configuration changes.  So whenever we trigger the loader from the background
     * tell the launcher that it needs to re-run the loader when it comes back instead
     * of doing it now.
     */
    public void startLoaderFromBackground() {
        Callbacks callbacks = getCallback();
        if (callbacks != null) {
            // Only actually run the loader if they're not paused.
            if (!callbacks.setLoadOnResume()) {
                startLoader(callbacks.getCurrentWorkspaceScreen());
            }
        }
    }

    public boolean isCurrentCallbacks(Callbacks callbacks) {
        return (mCallbacks != null && mCallbacks.get() == callbacks);
    }

    /**
     * Starts the loader. Tries to bind {@params synchronousBindPage} synchronously if possible.
     * @return true if the page could be bound synchronously.
     */
    public boolean startLoader(int synchronousBindPage) {
        // Enable queue before starting loader. It will get disabled in Launcher#finishBindingItems
        InstallShortcutReceiver.enableInstallQueue(InstallShortcutReceiver.FLAG_LOADER_RUNNING);
        synchronized (mLock) {
            // Don't bother to start the thread if we know it's not going to do anything
            if (mCallbacks != null && mCallbacks.get() != null) {
                final Callbacks oldCallbacks = mCallbacks.get();
                // Clear any pending bind-runnables from the synchronized load process.
                mUiExecutor.execute(new Runnable() {
                            public void run() {
                                oldCallbacks.clearPendingBinds();
                            }
                        });

                // If there is already one running, tell it to stop.
                stopLoader();
                LoaderResults loaderResults = new LoaderResults(mApp, sBgDataModel,
                        mBgAllAppsList, synchronousBindPage, mCallbacks);
                if (mModelLoaded && !mIsLoaderTaskRunning) {
                    // Divide the set of loaded items into those that we are binding synchronously,
                    // and everything else that is to be bound normally (asynchronously).
                    loaderResults.bindWorkspace();
                    // For now, continue posting the binding of AllApps as there are other
                    // issues that arise from that.
                    loaderResults.bindAllApps();
                    loaderResults.bindDeepShortcuts();
                    loaderResults.bindWidgets();
                    return true;
                } else {
                    startLoaderForResults(loaderResults);
                }
            }
        }
        return false;
    }

    /**
     * If there is already a loader task running, tell it to stop.
     */
    public void stopLoader() {
        synchronized (mLock) {
            LoaderTask oldTask = mLoaderTask;
            mLoaderTask = null;
            if (oldTask != null) {
                oldTask.stopLocked();
            }
        }
    }

    public void startLoaderForResults(LoaderResults results) {
        synchronized (mLock) {
            stopLoader();
            mLoaderTask = new LoaderTask(mApp, mBgAllAppsList, sBgDataModel, results);
            runOnWorkerThread(mLoaderTask);
        }
    }

    /**
     * Loads the workspace screen ids in an ordered list.
     */
    public static ArrayList<Long> loadWorkspaceScreensDb(Context context) {
        final ContentResolver contentResolver = context.getContentResolver();
        final Uri screensUri = LauncherSettings.WorkspaceScreens.CONTENT_URI;

        // Get screens ordered by rank.
        return LauncherDbUtils.getScreenIdsFromCursor(contentResolver.query(
                screensUri, null, null, null, LauncherSettings.WorkspaceScreens.SCREEN_RANK));
    }

    public void onInstallSessionCreated(final PackageInstallInfo sessionInfo) {
        enqueueModelUpdateTask(new BaseModelUpdateTask() {
            @Override
            public void execute(LauncherAppState app, BgDataModel dataModel, AllAppsList apps) {
                apps.addPromiseApp(app.getContext(), sessionInfo);
                if (!apps.added.isEmpty()) {
                    final ArrayList<AppInfo> arrayList = new ArrayList<>(apps.added);
                    apps.added.clear();
                    scheduleCallbackTask(new CallbackTask() {
                        @Override
                        public void execute(Callbacks callbacks) {
                            callbacks.bindAppsAdded(null, null, null, arrayList);
                        }
                    });
                }
            }
        });
    }

    public class LoaderTransaction implements AutoCloseable {

        private final LoaderTask mTask;

        private LoaderTransaction(LoaderTask task) throws CancellationException {
            synchronized (mLock) {
                if (mLoaderTask != task) {
                    throw new CancellationException("Loader already stopped");
                }
                mTask = task;
                mIsLoaderTaskRunning = true;
            }

            try {
                if (DEBUG_LOADERS) Log.d(TAG, "step 1.1: loading workspace");
                // Set to false in bindWorkspace()
                mIsLoadingAndBindingWorkspace = true;
                loadWorkspace();

                verifyNotStopped();
                if (DEBUG_LOADERS) Log.d(TAG, "step 1.2: bind workspace workspace");
                bindWorkspace(mPageToBindFirst);

                // Take a break
                if (DEBUG_LOADERS) Log.d(TAG, "step 1 completed, wait for idle");
                waitForIdle();
                verifyNotStopped();

                // second step
                if (DEBUG_LOADERS) Log.d(TAG, "step 2.1: loading all apps");
                loadAllApps();

                verifyNotStopped();
                if (DEBUG_LOADERS) Log.d(TAG, "step 2.2: Update icon cache");
                updateIconCache();

                // Take a break
                if (DEBUG_LOADERS) Log.d(TAG, "step 2 completed, wait for idle");
                waitForIdle();
                verifyNotStopped();

                // third step
                if (DEBUG_LOADERS) Log.d(TAG, "step 3.1: loading deep shortcuts");
                loadDeepShortcuts();

                verifyNotStopped();
                if (DEBUG_LOADERS) Log.d(TAG, "step 3.2: bind deep shortcuts");
                bindDeepShortcuts();

                // Take a break
                if (DEBUG_LOADERS) Log.d(TAG, "step 3 completed, wait for idle");
                waitForIdle();
                verifyNotStopped();

                // fourth step
                if (DEBUG_LOADERS) Log.d(TAG, "step 4.1: loading widgets");
                refreshAndBindWidgetsAndShortcuts(getCallback(), false /* bindFirst */,
                        null /* packageUser */);

                synchronized (mLock) {
                    // Everything loaded bind the data.
                    mModelLoaded = true;
                    mHasLoaderCompletedOnce = true;
                }
            } catch (CancellationException e) {
              // Loader stopped, ignore
            } finally {
                // Clear out this reference, otherwise we end up holding it until all of the
                // callback runnables are done.
                mContext = null;

                synchronized (mLock) {
                    // If we are still the last one to be scheduled, remove ourselves.
                    if (mLoaderTask == this) {
                        mLoaderTask = null;
                    }
                    mIsLoaderTaskRunning = false;
                    updateUnreadNumberAfterLoaded();
                }
            }
        }

        public void stopLocked() {
            synchronized (LoaderTask.this) {
                mStopped = true;
                this.notify();
                mModelLoaded = false;
            }
        }

        public void commit() {
            synchronized (mLock) {
                // Everything loaded bind the data.
                mModelLoaded = true;
            }
        }

        @Override
        public void close() {
            synchronized (mLock) {
                // If we are still the last one to be scheduled, remove ourselves.
                if (mLoaderTask == mTask) {
                    mLoaderTask = null;
                }
                mIsLoaderTaskRunning = false;
            }
        }
    }

    public LoaderTransaction beginLoader(LoaderTask task) throws CancellationException {
        return new LoaderTransaction(task);
    }

    /**
     * Refreshes the cached shortcuts if the shortcut permission has changed.
     * Current implementation simply reloads the workspace, but it can be optimized to
     * use partial updates similar to {@link UserManagerCompat}
     */
    public void refreshShortcutsIfRequired() {
        if (Utilities.ATLEAST_NOUGAT_MR1) {
            sWorker.removeCallbacks(mShortcutPermissionCheckRunnable);
            sWorker.post(mShortcutPermissionCheckRunnable);
        }
    }

    /**
     * Called when the icons for packages have been updated in the icon cache.
     */
    public void onPackageIconsUpdated(HashSet<String> updatedPackages, UserHandle user) {
        // If any package icon has changed (app was updated while launcher was dead),
        // update the corresponding shortcuts.
        enqueueModelUpdateTask(new CacheDataUpdatedTask(
                CacheDataUpdatedTask.OP_CACHE_UPDATE, user, updatedPackages));
    }

    public void enqueueModelUpdateTask(ModelUpdateTask task) {
        task.init(mApp, this, sBgDataModel, mBgAllAppsList, mUiExecutor);
        runOnWorkerThread(task);
    }

    /**
     * A task to be executed on the current callbacks on the UI thread.
     * If there is no current callbacks, the task is ignored.
     */
    public interface CallbackTask {

        void execute(Callbacks callbacks);
    }

    /**
     * A runnable which changes/updates the data model of the launcher based on certain events.
     */
    public interface ModelUpdateTask extends Runnable {

        /**
         * Called before the task is posted to initialize the internal state.
         */
        void init(LauncherAppState app, LauncherModel model,
                BgDataModel dataModel, AllAppsList allAppsList, Executor uiExecutor);

    }

    public void updateAndBindShortcutInfo(final ShortcutInfo si, final ShortcutInfoCompat info) {
        updateAndBindShortcutInfo(new Provider<ShortcutInfo>() {
            @Override
            public ShortcutInfo get() {
                si.updateFromDeepShortcutInfo(info, mApp.getContext());
                si.iconBitmap = LauncherIcons.createShortcutIcon(info, mApp.getContext());
                return si;
            }
        });
    }

    /**
     * Utility method to update a shortcut on the background thread.
     */
    public void updateAndBindShortcutInfo(final Provider<ShortcutInfo> shortcutProvider) {
        enqueueModelUpdateTask(new BaseModelUpdateTask() {
            @Override
            public void execute(LauncherAppState app, BgDataModel dataModel, AllAppsList apps) {
                ShortcutInfo info = shortcutProvider.get();
                ArrayList<ShortcutInfo> update = new ArrayList<>();
                update.add(info);
                bindUpdatedShortcuts(update, info.user);
            }
        });
    }

    public void refreshAndBindWidgetsAndShortcuts(@Nullable final PackageUserKey packageUser) {
        enqueueModelUpdateTask(new BaseModelUpdateTask() {
            @Override
            public void execute(LauncherAppState app, BgDataModel dataModel, AllAppsList apps) {
                dataModel.widgetsModel.update(app, packageUser);
                bindUpdatedWidgets(dataModel);
            }
        });
    }

    public void dumpState(String prefix, FileDescriptor fd, PrintWriter writer, String[] args) {
        if (args.length > 0 && TextUtils.equals(args[0], "--all")) {
            writer.println(prefix + "All apps list: size=" + mBgAllAppsList.data.size());
            for (AppInfo info : mBgAllAppsList.data) {
                writer.println(prefix + "   title=\"" + info.title + "\" iconBitmap=" + info.iconBitmap
                        + " componentName=" + info.componentName.getPackageName());
            }
        }
        sBgDataModel.dump(prefix, fd, writer, args);
    }

    public Callbacks getCallback() {
        return mCallbacks != null ? mCallbacks.get() : null;
    }

    /**
     * @return the looper for the worker thread which can be used to start background tasks.
     */
    public static Looper getWorkerLooper() {
        return sWorkerThread.getLooper();
    }

    /**
     * bind unread number service if service is not connected when onResume
     */
    public void bindUnreadService(){
        if (Utilities.isUnreadCountEnabled(mApp.getContext())
                && mUnreadNumberService == null) {
            Intent intent = new Intent();
            ComponentName componentName = new ComponentName(LAUNCHER_UNREAD_SERVICE_PACKAGENAME,
                    LAUNCHER_UNREAD_SERVICE_CLASSNAME);
            intent.setComponent(componentName);

            Intent requestIntent = Utilities.
                    createExplicitFromImplicitIntent(mApp.getContext(), intent);
            if (requestIntent != null) {
                final Intent eIntent = new Intent(requestIntent);
                mApp.getContext().bindService(eIntent, mUnreadConnection, Context.BIND_AUTO_CREATE);
            }
        }
    }

    /**
     * unbind unread number service when Launcher Activity is destroyed.
     */
    public void unbindUnreadService(){
        if (mUnreadNumberService != null){
            sWorker.removeCallbacks(mUnreadUpdateTask);
            try{
                mUnreadNumberService.unRegisterUnreadNumberCallback(mToken);
                mApp.getContext().unbindService(mUnreadConnection);
            }catch (Exception e){
                Log.e(TAG,"exception == "+e.toString());
            }
            mUnreadNumberService = null;
        }
    }

    public int getUnreadNumberOfComponent(ComponentName componentName) {
        synchronized (mUnreadMap) {
            if ((mUnreadMap != null) && mUnreadMap.containsKey(componentName)) {
                UnreadInfo info = mUnreadMap.get(componentName);
                return info != null ? info.mUnreadNum : -1;
            }
        }
        return -1;
    }

    private void updateUnreadNumberAfterLoaded(){
        runOnMainThread(new Runnable() {
            @Override
            public void run() {
                updateUnreadNumber();
            }
        });
    }

    private void updateUnreadNumber(){
        if (isModelLoaded() && mUnreadNumberService != null){
            try {
                Map unreadAppMap = mUnreadNumberService.GetUnreadNumber();
                updateUnreadIcon(unreadAppMap);
            }catch (RemoteException exception){
                exception.printStackTrace();
            }
        }
    }

    private void updateUnreadIcon(Map unreadMap) {
        Iterator iterator = unreadMap.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry entry = (Map.Entry) iterator.next();
            ComponentName componentName = (ComponentName) entry.getKey();
            if (componentName != null) {
                final int unreadNumber = (int) entry.getValue();
                synchronized (mUnreadMap) {
                    mUnreadMap.put(componentName, new UnreadInfo(componentName, unreadNumber));
                }
            }
        }
        postUnreadTask();
    }

    private void postUnreadTask() {
        sWorker.removeCallbacks(mUnreadUpdateTask);
        sWorker.post(mUnreadUpdateTask);
    }

    private class UnreadInfo {
        ComponentName mComponentName;
        int mUnreadNum;

        public UnreadInfo(ComponentName componentName, int unreadNum) {
            mComponentName = componentName;
            mUnreadNum = unreadNum;
        }
    }

    /**
     * notify launcher refresh ui
     *  1) refresh shortcuts ui
     *  2) refresh allApps ui
     */
    private class UnreadNumberChangeTask implements Runnable {
        public void run() {
            ArrayList<UnreadInfo> unreadInfos = new ArrayList<LauncherModel.UnreadInfo>();
            synchronized (mUnreadMap) {
                unreadInfos.addAll(mUnreadMap.values());
            }

            final Callbacks callbacks = getCallback();
            if (callbacks == null) {
                Log.w(TAG, "Nobody to tell about the new app.  Launcher is probably loading.");
                return;
            }

            final ArrayList<AppInfo> unreadChangeFinal = new ArrayList<AppInfo>();
            for (UnreadInfo uInfo : unreadInfos) {
                AppInfo info = mBgAllAppsList.unreadNumbersChanged(mApp.getContext(),
                        uInfo.mComponentName);
                if (info != null) {
                    unreadChangeFinal.add(info);
                }
            }

            // update the mainmenu icon
            if (unreadChangeFinal.isEmpty()) return;
            mHandler.post(new Runnable() {
                public void run() {
                    Callbacks cb = getCallback();
                    if (cb != null && callbacks == cb) {
                        cb.bindAppsUpdated(unreadChangeFinal);
                    }
                }
            });
            // update the workspace shortcuts icon
            final UserHandle user = android.os.Process.myUserHandle();
            final ArrayList<ShortcutInfo> updatedShortcuts = new ArrayList<>();
            synchronized (sBgDataModel) {
                for (ItemInfo info : sBgDataModel.itemsIdMap) {
                    if (info instanceof ShortcutInfo && user.equals(info.user)
                            && info.itemType == LauncherSettings.Favorites.ITEM_TYPE_APPLICATION) {
                        ShortcutInfo si = (ShortcutInfo) info;
                        ComponentName cn = si.getTargetComponent();
                        if (cn != null && unreadContains(unreadChangeFinal, cn)) {
                            mIconCache.getTitleAndIcon(si, false);
                            updatedShortcuts.add(si);
                        }
                    }
                }
            }

            if (!updatedShortcuts.isEmpty()) {
                mHandler.post(new Runnable() {

                    public void run() {
                        Callbacks cb = getCallback();
                        if (cb != null && callbacks == cb) {
                            cb.bindShortcutsChanged(updatedShortcuts,
                                    new ArrayList<ShortcutInfo>(), user);
                        }
                    }
                });
            }
        }
        private boolean unreadContains(ArrayList<AppInfo> unreadList, ComponentName cn) {
            for (AppInfo info : unreadList) {
                if (info.componentName.equals(cn)) {
                    return true;
                }
            }
            return false;
        }
    }
}
