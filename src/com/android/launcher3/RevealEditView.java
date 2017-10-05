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

import android.animation.Animator;
import android.content.Intent;
import android.graphics.Color;
import android.support.v4.content.ContextCompat;
import android.view.View;
import android.view.ViewAnimationUtils;

class RevealEditView {

    static void show(final Launcher launcher, final View editView) {

        final int statusBarColor = ContextCompat.getColor(launcher, R.color.app_action_color_dark);

        int x = editView.getRight() / 2;
        int y = editView.getBottom();

        int startRadius = 0;
        int endRadius = (int) Math.hypot(editView.getWidth(), editView.getHeight());

        Animator anim = ViewAnimationUtils.createCircularReveal(editView, x, y, startRadius, endRadius);

        anim.addListener(new Animator.AnimatorListener() {
            @Override
            public void onAnimationStart(Animator animator) {

                editView.setVisibility(View.VISIBLE);
            }

            @Override
            public void onAnimationEnd(Animator animator) {

                launcher.getWindow().setStatusBarColor(statusBarColor);
            }

            @Override
            public void onAnimationCancel(Animator animator) {

            }

            @Override
            public void onAnimationRepeat(Animator animator) {

            }
        });
        anim.start();
    }

    static void close(final Launcher launcher, final View editView, final boolean isIconPackChooser, final String iconPack) {

        int x = editView.getRight() / 2;
        int y = editView.getBottom();

        int startRadius = Math.max(editView.getWidth(), editView.getHeight());
        int endRadius = 0;

        Animator anim = ViewAnimationUtils.createCircularReveal(editView, x, y, startRadius, endRadius);
        anim.addListener(new Animator.AnimatorListener() {
            @Override
            public void onAnimationStart(Animator animator) {

                launcher.closeKeyboard();
                launcher.getWindow().setStatusBarColor(Color.TRANSPARENT);
            }

            @Override
            public void onAnimationEnd(Animator animator) {

                // reload workspace
                LauncherAppState.getInstanceNoCreate().getModel().forceReload();

                editView.setVisibility(View.INVISIBLE);

                if (isIconPackChooser) {
                    Intent intent = new Intent(launcher, ChooseIconActivity.class);
                    ChooseIconActivity.setItemInfo(launcher.getItemInfoForEdit());
                    intent.putExtra("app_package", launcher.getItemInfoForEdit().getTargetComponent().getPackageName());
                    intent.putExtra("app_label", launcher.getPackageLabelForEdit());
                    intent.putExtra("icon_pack_package", iconPack);
                    launcher.startActivity(intent);
                }
            }

            @Override
            public void onAnimationCancel(Animator animator) {

            }

            @Override
            public void onAnimationRepeat(Animator animator) {

            }
        });
        anim.start();
    }
}
