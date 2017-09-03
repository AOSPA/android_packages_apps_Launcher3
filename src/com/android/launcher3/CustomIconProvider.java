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
import android.content.res.AssetManager;
import android.content.res.Resources;
import android.content.res.XmlResourceParser;
import android.graphics.Bitmap;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.util.Log;

import com.android.launcher3.compat.LauncherActivityInfoCompat;

import org.xmlpull.v1.XmlPullParser;

public class CustomIconProvider extends IconProvider {

    private Context mContext;
    private IconsHandler mIconsHandler;

    public CustomIconProvider(Context context) {
        super();
        mContext = context;
        mIconsHandler = IconCache.getIconsHandler(context);
    }

    @Override
    public Drawable getIcon(LauncherActivityInfoCompat info, int iconDpi) {
        Drawable icon = Utilities.isRoundIconsPrefEnabled(mContext)? getRoundIcon(mContext, info.getApplicationInfo().packageName, iconDpi) : getIconFromHandler(info);
        return icon != null? icon : info.getIcon(iconDpi);
    }

	//get drawable icon for package
	private Drawable getIconFromHandler(LauncherActivityInfoCompat info) {
        Bitmap bm = mIconsHandler.getDrawableIconForPackage(info.getComponentName());
        if (bm == null) {
            return null;
        }
        return new BitmapDrawable(context.getResources(), Utilities.createIconBitmap(bm, context));
    }

	//get round icon for package if available
	private Drawable getRoundIcon(Context context, String packageName, int iconDpi) {

        PackageManager mPackageManager = context.getPackageManager();

        try {
            Resources resourcesForApplication = mPackageManager.getResourcesForApplication(packageName);
            AssetManager assets = resourcesForApplication.getAssets();
            XmlResourceParser parseXml = assets.openXmlResourceParser("AndroidManifest.xml");
            int eventType;
            while ((eventType = parseXml.nextToken()) != XmlPullParser.END_DOCUMENT)
                if (eventType == XmlPullParser.START_TAG && parseXml.getName().equals("application"))
                    for (int i = 0; i < parseXml.getAttributeCount(); i++)
                        if (parseXml.getAttributeName(i).equals("roundIcon"))
                            return resourcesForApplication.getDrawableForDensity(Integer.parseInt(parseXml.getAttributeValue(i).substring(1)), iconDpi, context.getTheme());
            parseXml.close();
        }
        catch (Exception ex) {
            Log.w("getRoundIcon", ex);
        }
        return null;
    }
}
