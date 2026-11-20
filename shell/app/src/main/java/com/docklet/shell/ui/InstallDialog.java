package com.docklet.shell.ui;

import android.app.Activity;
import android.app.Dialog;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

/**
 * 安装 / 更新 / 回滚共用的小弹窗：阶段文案 + 进度条 + 结果。
 * 成功时停 1 秒自动关闭，也可以点「完成」立刻关。
 */
public final class InstallDialog {

    private static final long AUTO_CLOSE_MS = 1000L;

    private final Dialog dialog;
    private final TextView stageView;
    private final ProgressBar bar;
    private final TextView doneButton;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable autoClose;

    private InstallDialog(Activity activity, String title, String subtitle) {
        dialog = new Dialog(activity);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setCancelable(false);

        LinearLayout content = new LinearLayout(activity);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(activity, 26), dp(activity, 26), dp(activity, 26), dp(activity, 22));
        GradientDrawable bg = UiKit.rounded(activity, UiKit.SURFACE, 24f);
        content.setBackground(bg);
        content.setElevation(dp(activity, 8));

        TextView titleView = UiKit.text(activity, title, 15f, UiKit.INK, true);
        titleView.setGravity(Gravity.CENTER);
        content.addView(titleView, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ));

        TextView subtitleView = UiKit.text(activity, subtitle, 11f, UiKit.MUTED, false);
        subtitleView.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams subtitleParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        );
        subtitleParams.topMargin = dp(activity, 6);
        content.addView(subtitleView, subtitleParams);

        bar = new ProgressBar(activity, null, android.R.attr.progressBarStyleHorizontal);
        bar.setMax(100);
        bar.setProgressTintList(android.content.res.ColorStateList.valueOf(UiKit.ACCENT));
        bar.setProgressBackgroundTintList(
                android.content.res.ColorStateList.valueOf(UiKit.SURFACE_SOFT)
        );
        LinearLayout.LayoutParams barParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(activity, 6)
        );
        barParams.topMargin = dp(activity, 16);
        content.addView(bar, barParams);

        stageView = UiKit.text(activity, "准备中…", 11f, UiKit.MUTED, false);
        stageView.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams stageParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        );
        stageParams.topMargin = dp(activity, 10);
        content.addView(stageView, stageParams);

        doneButton = UiKit.secondaryButton(activity, "完成");
        doneButton.setVisibility(android.view.View.GONE);
        doneButton.setOnClickListener(view -> dismiss());
        LinearLayout.LayoutParams doneParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(activity, 40)
        );
        doneParams.topMargin = dp(activity, 14);
        content.addView(doneButton, doneParams);

        dialog.setContentView(content);
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
            dialog.getWindow().setLayout(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
            );
        }
        autoClose = this::dismiss;
        dialog.show();
    }

    public static InstallDialog show(Activity activity, String title, String subtitle) {
        return new InstallDialog(activity, title, subtitle);
    }

    public void stage(String text, int percent) {
        stageView.setText(text);
        stageView.setTextColor(UiKit.MUTED);
        bar.setVisibility(android.view.View.VISIBLE);
        bar.setProgress(Math.max(0, Math.min(100, percent)));
    }

    public void success(String text) {
        handler.removeCallbacks(autoClose);
        bar.setProgress(100);
        stageView.setText("✓  " + text);
        stageView.setTextColor(UiKit.INK);
        bar.setVisibility(android.view.View.GONE);
        doneButton.setVisibility(android.view.View.VISIBLE);
        handler.postDelayed(autoClose, AUTO_CLOSE_MS);
    }

    public void failure(String text) {
        handler.removeCallbacks(autoClose);
        stageView.setText(text);
        stageView.setTextColor(UiKit.DANGER);
        bar.setVisibility(android.view.View.GONE);
        doneButton.setVisibility(android.view.View.VISIBLE);
    }

    public void dismiss() {
        handler.removeCallbacks(autoClose);
        if (dialog.isShowing()) dialog.dismiss();
    }

    private static int dp(Activity activity, float value) {
        return UiKit.dp(activity, value);
    }
}
