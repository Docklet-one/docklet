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

    /** 成功后停留多久再自动关闭。 */
    private static final long AUTO_CLOSE_MS = 1600L;
    /** 前这段时间只显示“获取中…”，不显示百分比，避免一闪而过。 */
    private static final long PROGRESS_AFTER_MS = 700L;
    /** 整个弹窗至少显示这么久，避免 0.2 秒装完什么都看不见。 */
    private static final long MIN_TOTAL_MS = 1300L;
    private static final float DIALOG_WIDTH_DP = 320f;

    private final Dialog dialog;
    private final TextView stageView;
    private final ProgressBar bar;
    private final TextView doneButton;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable autoClose;
    private final long startedAt = android.os.SystemClock.elapsedRealtime();

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
        bar.setVisibility(android.view.View.INVISIBLE);
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
        doneButton.setVisibility(android.view.View.INVISIBLE);
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
                    dp(activity, DIALOG_WIDTH_DP),
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
        long elapsed = android.os.SystemClock.elapsedRealtime() - startedAt;
        stageView.setTextColor(UiKit.MUTED);
        bar.setVisibility(android.view.View.VISIBLE);
        doneButton.setVisibility(android.view.View.INVISIBLE);
        if (elapsed < PROGRESS_AFTER_MS) {
            // 刚开始只给阶段文案，百分比晚一点出现，读起来更像“在做事”。
            stageView.setText(withoutPercent(text));
            bar.setProgress(0);
            return;
        }
        stageView.setText(text);
        bar.setProgress(Math.max(0, Math.min(100, percent)));
    }

    private static String withoutPercent(String text) {
        if (text == null) return "";
        int cut = text.lastIndexOf(' ');
        if (cut > 0 && text.endsWith("%")) return text.substring(0, cut);
        return text;
    }

    public void success(String text) {
        long elapsed = android.os.SystemClock.elapsedRealtime() - startedAt;
        long wait = Math.max(0L, MIN_TOTAL_MS - elapsed);
        handler.postDelayed(() -> {
            bar.setProgress(100);
            stageView.setText("✓  " + text);
            stageView.setTextColor(UiKit.INK);
            bar.setVisibility(android.view.View.INVISIBLE);
            doneButton.setVisibility(android.view.View.VISIBLE);
            handler.postDelayed(autoClose, AUTO_CLOSE_MS);
        }, wait);
    }

    public void failure(String text) {
        handler.removeCallbacks(autoClose);
        stageView.setText(text);
        stageView.setTextColor(UiKit.DANGER);
        bar.setVisibility(android.view.View.INVISIBLE);
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
