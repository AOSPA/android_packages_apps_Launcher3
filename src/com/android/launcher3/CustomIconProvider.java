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
import android.graphics.drawable.Drawable;

import com.android.launcher3.compat.LauncherActivityInfoCompat;

class CustomIconProvider extends IconProvider {

    private Context mContext;
    private IconsHandler mIconsHandler;

    CustomIconProvider(Context context) {
        super();
        mContext = context;
        mIconsHandler = IconCache.getIconsHandler(context);
    }

    @Override
    public Drawable getIcon(LauncherActivityInfoCompat info, int iconDpi) {

        Drawable drawable = Utilities.isRoundIconsPrefEnabled(mContext) ? mIconsHandler.getRoundIcon(mContext, info.getComponentName().getPackageName(), iconDpi) : mIconsHandler.getIconFromHandler(mContext, info);

        return drawable != null ? drawable : info.getIcon(iconDpi);
    }
}
