/*
 * Copyright (C) 2016 The Android Open Source Project
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

import android.app.AlertDialog;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.util.Log;
import android.widget.EditText;

import com.android.launcher3.compat.UserHandleCompat;

public class RenameDropTarget extends ButtonDropTarget {

    private static String TAG = "RenameDropTarget";
    private static boolean DBG = false;
    private Context mCtx;

    public RenameDropTarget(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
        mCtx = context;
    }

    public RenameDropTarget(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        mCtx = context;
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();
        // Get the hover color
        mHoverColor = getResources().getColor(R.color.info_target_hover_tint);
        setDrawable(R.drawable.ic_info_launcher);
    }

    public static void startDetailsActivityForInfo(Object info, Launcher launcher) {
        ComponentName componentName = null;
        if (info instanceof AppInfo) {
            componentName = ((AppInfo) info).componentName;
        } else if (info instanceof ShortcutInfo) {
            componentName = ((ShortcutInfo) info).intent.getComponent();
        } else if (info instanceof PendingAddItemInfo) {
            componentName = ((PendingAddItemInfo) info).componentName;
        }
        final UserHandleCompat user;
        if (info instanceof ItemInfo) {
            user = ((ItemInfo) info).user;
        } else {
            user = UserHandleCompat.myUserHandle();
        }

        if (componentName != null) {
            launcher.startApplicationDetailsActivity(componentName, user);
        }
    }

    @Override
    protected boolean supportsDrop(DragSource source, Object info) {
        boolean isactive = isVisible(info);
        return isactive;
    }

    private boolean isVisible(Object info){
        return (info instanceof ShortcutInfo);
    }

    public static boolean supportsDrop(Context context, Object info) {
        return info instanceof AppInfo || info instanceof PendingAddItemInfo;
    }

    @Override
    void completeDrop(DragObject d) {
        startDetailsActivityForInfo(d.dragInfo, mLauncher);
    }

    @Override
    public boolean acceptDrop(DragObject d) {
        // acceptDrop is called just before onDrop. We do the work here, rather than
        // in onDrop, because it allows us to reject the drop (by returning false)
        // so that the object being dragged isn't removed from the drag source.
        ComponentName componentName = null;
        if (d.dragInfo instanceof ShortcutInfo) {
            final ShortcutInfo  curShortcutInfo = (ShortcutInfo) d.dragInfo;
            componentName = ((ShortcutInfo) d.dragInfo).intent.getComponent();
            String text = curShortcutInfo.title.toString();
            final EditText ed = new EditText(mCtx);
            ed.setSingleLine(true);
            ed.setText(text);
            ed.setSelection(text.length());
            new AlertDialog.Builder(mCtx)
                .setTitle(R.string.rename_desc_label)
                .setIcon(android.R.drawable.ic_dialog_info)
                .setView(ed)
                .setPositiveButton(R.string.rename_action,
                    new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            curShortcutInfo.intent.putExtra("isShortcutInfoRename", true);
                            mLauncher.updateTitleDb(curShortcutInfo,
                               ed.getText().toString());
                            mLauncher.getModel().forceReload();
                        }
                    }
                )
                .setNegativeButton(R.string.cancel_action,
                    new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            mLauncher.getModel().forceReload();
                        }
                    }
                )
                .show();
        }
        // There is no post-drop animation, so clean up the DragView now
        d.deferDragViewCleanupPostAnimation = false;
        return false;
    }
}
