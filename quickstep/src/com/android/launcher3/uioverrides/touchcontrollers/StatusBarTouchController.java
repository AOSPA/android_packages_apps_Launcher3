/*
 * Copyright (C) 2018 The Android Open Source Project
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
package com.android.launcher3.uioverrides.touchcontrollers;

import static android.provider.Settings.Secure.STATUS_BAR_QUICK_QS_PULLDOWN;
import static android.view.MotionEvent.ACTION_CANCEL;
import static android.view.MotionEvent.ACTION_DOWN;
import static android.view.MotionEvent.ACTION_MOVE;
import static android.view.MotionEvent.ACTION_UP;
import static android.view.WindowManager.LayoutParams.FLAG_SLIPPERY;

import static com.android.launcher3.logging.StatsLogManager.LauncherEvent.LAUNCHER_SWIPE_DOWN_WORKSPACE_NOTISHADE_OPEN;

import android.app.StatusBarManager;
import android.database.ContentObserver;
import android.graphics.PointF;
import android.os.Handler;
import android.os.UserHandle;
import android.provider.Settings;
import android.util.SparseArray;
import android.view.MotionEvent;
import android.view.ViewConfiguration;
import android.view.Window;
import android.view.WindowManager;

import com.android.launcher3.AbstractFloatingView;
import com.android.launcher3.DeviceProfile;
import com.android.launcher3.Launcher;
import com.android.launcher3.LauncherState;
import com.android.launcher3.Utilities;
import com.android.launcher3.util.TouchController;
import com.android.quickstep.SystemUiProxy;

import java.io.PrintWriter;

/**
 * TouchController for handling touch events that get sent to the StatusBar. Once the
 * Once the event delta mDownY passes the touch slop, the events start getting forwarded.
 * All events are offset by initial Y value of the pointer.
 */
public class StatusBarTouchController implements TouchController {

    private static final String TAG = "StatusBarController";
    private static final String KEY_FASTER_SB_EXPANSION = "pref_faster_sb_expansion";

    private final Launcher mLauncher;
    private final SystemUiProxy mSystemUiProxy;
    private float mTouchSlop;
    private int mLastAction;
    private final SparseArray<PointF> mDownEvents;
    private final StatusBarManager mSbManager;
    private boolean mFasterSbExpansion;

    /* If {@code false}, this controller should not handle the input {@link MotionEvent}.*/
    private boolean mCanIntercept;

    private final ContentObserver mSettingObserver = new ContentObserver(new Handler()) {
        @Override
        public void onChange(boolean selfChange) {
            updateFasterSbExpansion();
        }
    };

    public StatusBarTouchController(Launcher l) {
        mLauncher = l;
        mSystemUiProxy = SystemUiProxy.INSTANCE.get(mLauncher);
        mDownEvents = new SparseArray<>();
        mSbManager = l.getSystemService(StatusBarManager.class);
        l.getContentResolver().registerContentObserver(
                Settings.Secure.getUriFor(STATUS_BAR_QUICK_QS_PULLDOWN), false, mSettingObserver);
        updateFasterSbExpansion();
    }

    @Override
    public void dump(String prefix, PrintWriter writer) {
        writer.println(prefix + "mCanIntercept:" + mCanIntercept);
        writer.println(prefix + "mLastAction:" + MotionEvent.actionToString(mLastAction));
        writer.println(prefix + "mSysUiProxy available:"
                + SystemUiProxy.INSTANCE.get(mLauncher).isActive());
        writer.println(prefix + "mFasterSbExpansion:" + mFasterSbExpansion);
        writer.println(prefix + "mTouchSlop:" + mTouchSlop);
    }

    public void onDestroy() {
        mLauncher.getContentResolver().unregisterContentObserver(mSettingObserver);
    }

    private void updateFasterSbExpansion() {
        mFasterSbExpansion = Settings.Secure.getIntForUser(mLauncher.getContentResolver(),
                STATUS_BAR_QUICK_QS_PULLDOWN, 1, UserHandle.USER_CURRENT) == 1;

        // Guard against TAPs by increasing the touch slop.
        int touchSlopMultiplier = mFasterSbExpansion ? 4 : 2;
        mTouchSlop = touchSlopMultiplier * ViewConfiguration.get(mLauncher).getScaledTouchSlop();
    }

    private void dispatchTouchEvent(MotionEvent ev) {
        mLastAction = ev.getActionMasked();
        if (handleFasterSbExpansion(ev)) {
            return;
        }
        if (mSystemUiProxy.isActive()) {
            mSystemUiProxy.onStatusBarTouchEvent(ev);
        }
    }

    private boolean handleFasterSbExpansion(MotionEvent ev) {
        if (!mFasterSbExpansion) {
            return false;
        }
        if (mLastAction == ACTION_DOWN) {
            float x = ev.getX();
            float w = mLauncher.getResources().getDisplayMetrics().widthPixels;
            float region = w * 0.25f; // Matches one finger QS expand region in SystemUI
            boolean expandQs = Utilities.isRtl(mLauncher.getResources())
                    ? (x < region) : (w - region < x);
            if (expandQs) {
                mSbManager.expandSettingsPanel();
            } else {
                mSbManager.expandNotificationsPanel();
            }
        }
        return true;
    }

    @Override
    public final boolean onControllerInterceptTouchEvent(MotionEvent ev) {
        int action = ev.getActionMasked();
        int idx = ev.getActionIndex();
        int pid = ev.getPointerId(idx);
        if (action == ACTION_DOWN) {
            mCanIntercept = canInterceptTouch(ev);
            if (!mCanIntercept) {
                return false;
            }
            mDownEvents.clear();
            mDownEvents.put(pid, new PointF(ev.getX(), ev.getY()));
        } else if (ev.getActionMasked() == MotionEvent.ACTION_POINTER_DOWN) {
            // Check!! should only set it only when threshold is not entered.
            mDownEvents.put(pid, new PointF(ev.getX(idx), ev.getY(idx)));
        }
        if (!mCanIntercept) {
            return false;
        }
        if (action == ACTION_MOVE) {
            float dy = ev.getY(idx) - mDownEvents.get(pid).y;
            float dx = ev.getX(idx) - mDownEvents.get(pid).x;
            // Currently input dispatcher will not do touch transfer if there are more than
            // one touch pointer. Hence, even if slope passed, only set the slippery flag
            // when there is single touch event. (context: InputDispatcher.cpp line 1445)
            if (dy > mTouchSlop && dy > Math.abs(dx) && ev.getPointerCount() == 1) {
                ev.setAction(ACTION_DOWN);
                dispatchTouchEvent(ev);
                setWindowSlippery(true);
                return true;
            }
            if (Math.abs(dx) > mTouchSlop) {
                mCanIntercept = false;
            }
        }
        return false;
    }

    @Override
    public final boolean onControllerTouchEvent(MotionEvent ev) {
        int action = ev.getAction();
        if (action == ACTION_UP || action == ACTION_CANCEL) {
            dispatchTouchEvent(ev);
            mLauncher.getStatsLogManager().logger()
                    .log(LAUNCHER_SWIPE_DOWN_WORKSPACE_NOTISHADE_OPEN);
            setWindowSlippery(false);
            return true;
        }
        return true;
    }

    /**
     * FLAG_SLIPPERY enables touches to slide out of a window into neighboring
     * windows in mid-gesture instead of being captured for the duration of
     * the gesture.
     *
     * This flag changes the behavior of touch focus for this window only.
     * Touches can slide out of the window but they cannot necessarily slide
     * back in (unless the other window with touch focus permits it).
     */
    private void setWindowSlippery(boolean enable) {
        Window w = mLauncher.getWindow();
        WindowManager.LayoutParams wlp = w.getAttributes();
        if (enable) {
            wlp.flags |= FLAG_SLIPPERY;
        } else {
            wlp.flags &= ~FLAG_SLIPPERY;
        }
        w.setAttributes(wlp);
    }

    private boolean canInterceptTouch(MotionEvent ev) {
        if (!mLauncher.isInState(LauncherState.NORMAL) ||
                AbstractFloatingView.getTopOpenViewWithType(mLauncher,
                        AbstractFloatingView.TYPE_STATUS_BAR_SWIPE_DOWN_DISALLOW) != null) {
            return false;
        } else {
            // For NORMAL state, only listen if the event originated above the navbar height
            DeviceProfile dp = mLauncher.getDeviceProfile();
            if (ev.getY() > (mLauncher.getDragLayer().getHeight() - dp.getInsets().bottom)) {
                return false;
            }
        }
        return mFasterSbExpansion || SystemUiProxy.INSTANCE.get(mLauncher).isActive();
    }
}
