package com.android.launcher3.qsb;

import android.app.smartspace.SmartspaceAction;
import android.app.smartspace.SmartspaceTarget;
import android.app.smartspace.SmartspaceTargetEvent;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.util.AttributeSet;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.FrameLayout.LayoutParams;

import co.aospa.launcher.ParanoidLauncher;
import co.aospa.launcher.ParanoidLauncherModelDelegate;

import com.android.launcher3.BaseActivity;
import com.android.launcher3.BaseQuickstepLauncher;
import com.android.launcher3.Launcher;
import com.android.launcher3.R;
import com.android.launcher3.Utilities;
import com.android.launcher3.icons.GraphicsUtils;
import com.android.launcher3.uioverrides.plugins.PluginManagerWrapper;
import com.android.launcher3.views.ActivityContext;
import com.android.launcher3.views.OptionsPopupView;
import com.android.quickstep.SystemUiProxy;

import com.android.systemui.plugins.BcSmartspaceDataPlugin;
import com.android.systemui.plugins.PluginListener;

import com.google.android.systemui.smartspace.BcSmartspaceCard;
import com.google.android.systemui.smartspace.BcSmartspaceDataProvider;
import com.google.android.systemui.smartspace.BcSmartspaceEvent;
import com.google.android.systemui.smartspace.BcSmartspaceView;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class SmartspaceViewContainer extends FrameLayout implements PluginListener<BcSmartspaceDataPlugin> {

    public BcSmartspaceView mView;

    public SmartspaceViewContainer(Context context, AttributeSet attributeSet) {
        super(context, attributeSet);
        mView = (BcSmartspaceView) inflate(context, R.layout.smartspace_enhanced, null);
        LayoutParams layoutParams = new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
        layoutParams.gravity = Gravity.CENTER_VERTICAL;
        layoutParams.setMarginStart(getResources().getDimensionPixelSize(R.dimen.enhanced_smartspace_margin_start_launcher));
        addView(mView, layoutParams);
        mView.setPrimaryTextColor(GraphicsUtils.getAttrColor(context, R.attr.workspaceTextColor));
        ParanoidLauncher launcher = (ParanoidLauncher) ActivityContext.lookupContext(context);
        ParanoidLauncherModelDelegate delegate = (ParanoidLauncherModelDelegate) launcher.getModel().getModelDelegate();
        BcSmartspaceDataProvider plugin = launcher.getSmartspacePlugin();
        plugin.registerSmartspaceEventNotifier(event -> delegate.notifySmartspaceEvent(event));
        mView.registerDataProvider(plugin);
        SystemUiProxy.INSTANCE.get(context).setSmartspaceCallback(new LauncherSmartspaceCallback(this, launcher));
    }

    @Override
    public void onPluginConnected(BcSmartspaceDataPlugin plugin, Context context) {
        mView.registerDataProvider(plugin);
    }

    @Override
    public void onPluginDisconnected(BcSmartspaceDataPlugin plugin) {
        ParanoidLauncher launcher = (ParanoidLauncher) ActivityContext.lookupContext(getContext());
        mView.registerDataProvider(launcher.getSmartspacePlugin());
    }

    @Override
    public void onAttachedToWindow() {
        super.onAttachedToWindow();
        PluginManagerWrapper.INSTANCE.get(getContext()).addPluginListener(this, BcSmartspaceDataPlugin.class);
    }

    @Override
    public void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        PluginManagerWrapper.INSTANCE.get(getContext()).removePluginListener(this);
        SystemUiProxy.INSTANCE.get(getContext()).setSmartspaceCallback(null);
    }

    @Override
    public void setPadding(int left, int top, int right, int bottom) {
        super.setPadding(0, 0, 0, 0);
    }

    public BcSmartspaceView getSmartspaceView() {
        return mView;
    }
}
