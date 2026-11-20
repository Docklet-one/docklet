package com.docklet.shell.ui;

import android.app.Activity;
import android.app.Dialog;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

/**
 * 安装 / 更新 / 回滚 / 卸载共用弹窗：公共头部（图标 + 状态 + 名称）+ 进度 + 结果。
 * 运行态显示「取消」，结束态显示「完成」，两者占同一个位置，所以弹窗高度不变。
 */
public final class InstallDialog {

    private static final long AUTO_CLOSE_MS = 1600L;
    private static final long PROGRESS_AFTER_MS = 700L;
    private static final long MIN_TOTAL_MS = 1300L;
    private static final float DIALOG_WIDTH_DP = 320f;

    private final Dialog dialog;
    private final IconView tileIcon;
    private final TextView titleView;
    private final TextView subtitleView;
    private final TextView stageView;
    private final ProgressBar bar;
    private final TextView actionButton;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable autoClose;
    private final long startedAt = SystemClock.elapsedRealtime();
    private Runnable onCancel;
    private boolean finished;

    private InstallDialog(Activity activity, IconView.Type icon, String title, String subtitle) {
        dialog = new Dialog(activity);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setCancelable(false);

        LinearLayout content = new LinearLayout(activity);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(activity, 20), dp(activity, 20), dp(activity, 20), dp(activity, 16));
        GradientDrawable bg = UiKit.rounded(activity, UiKit.SURFACE, 24f);
        content.setBackground(bg);
        content.setElevation(dp(activity, 8));

        // ---- header: 42dp icon tile + title + subtitle ----
        LinearLayout header = new LinearLayout(activity);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);

        FrameLayout tile = new FrameLayout(activity);
        tile.setBackground(UiKit.rounded(activity, UiKit.ACCENT_SOFT, 13f));
        tileIcon = new IconView(activity).type(icon);
        tileIcon.setColor(UiKit.ACCENT_DEEP);
        tile.addView(tileIcon, new FrameLayout.LayoutParams(dp(activity, 22), dp(activity, 22), Gravity.CENTER));
        header.addView(tile, new LinearLayout.LayoutParams(dp(activity, 42), dp(activity, 42)));

        LinearLayout titles = new LinearLayout(activity);
        titles.setOrientation(LinearLayout.VERTICAL);
        titleView = UiKit.text(activity, title, 17f, UiKit.INK, true);
        subtitleView = UiKit.text(activity, subtitle, 12f, UiKit.MUTED, false);
        subtitleView.setMaxLines(1);
        subtitleView.setEllipsize(android.text.TextUtils.TruncateAt.END);
        titles.addView(titleView);
        LinearLayout.LayoutParams subParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT
        );
        subParams.topMargin = dp(activity, 2);
        titles.addView(subtitleView, subParams);
        LinearLayout.LayoutParams titlesParams = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f
        );
        titlesParams.leftMargin = dp(activity, 12);
        header.addView(titles, titlesParams);
        content.addView(header);

        // ---- progress + stage ----
        bar = new ProgressBar(activity, null, android.R.attr.progressBarStyleHorizontal);
        bar.setMax(100);
        bar.setProgressTintList(ColorStateList.valueOf(UiKit.ACCENT));
        bar.setProgressBackgroundTintList(ColorStateList.valueOf(UiKit.SURFACE_SOFT));
        LinearLayout.LayoutParams barParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(activity, 6)
        );
        barParams.topMargin = dp(activity, 18);
        content.addView(bar, barParams);

        stageView = UiKit.text(activity, "准备中…", 12f, UiKit.MUTED, false);
        LinearLayout.LayoutParams stageParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        );
        stageParams.topMargin = dp(activity, 10);
        content.addView(stageView, stageParams);

        // ---- one button slot: 取消 while running, 完成 when done ----
        actionButton = UiKit.secondaryButton(activity, "取消");
        actionButton.setOnClickListener(view -> {
            if (finished) {
                dismiss();
                return;
            }
            if (onCancel != null) {
                actionButton.setEnabled(false);
                actionButton.setText("正在取消…");
                onCancel.run();
            } else {
                dismiss();
            }
        });
        LinearLayout.LayoutParams actionParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(activity, 44)
        );
        actionParams.topMargin = dp(activity, 18);
        content.addView(actionButton, actionParams);

        dialog.setContentView(content);
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
            dialog.getWindow().setGravity(Gravity.CENTER);
        }
        autoClose = this::dismiss;
        dialog.show();
        // 尺寸在 show 之后再设：提前设 WRAP_CONTENT 时，
        // 部分 ROM 会按上一轮测量结果截断底部，按钮看起来被压住。
        if (dialog.getWindow() != null) {
            dialog.getWindow().setLayout(
                    dp(activity, DIALOG_WIDTH_DP),
                    ViewGroup.LayoutParams.WRAP_CONTENT
            );
        }
    }

    public static InstallDialog show(
            Activity activity,
            IconView.Type icon,
            String title,
            String subtitle
    ) {
        return new InstallDialog(activity, icon, title, subtitle);
    }

    /** 供下载过程检查是否被用户取消。 */
    public boolean isCancelled() {
        return cancelled;
    }

    private volatile boolean cancelled;

    public void onCancel(Runnable action) {
        this.onCancel = action;
    }

    public void stage(String text, int percent) {
        long elapsed = SystemClock.elapsedRealtime() - startedAt;
        stageView.setTextColor(UiKit.MUTED);
        if (elapsed < PROGRESS_AFTER_MS) {
            stageView.setText(withoutPercent(text));
            bar.setProgress(0);
            return;
        }
        stageView.setText(text);
        bar.setProgress(Math.max(0, Math.min(100, percent)));
    }

    public void success(IconView.Type icon, String title, String subtitle, String summary) {
        long elapsed = SystemClock.elapsedRealtime() - startedAt;
        long wait = Math.max(0L, MIN_TOTAL_MS - elapsed);
        handler.postDelayed(() -> {
            finished = true;
            applyResult(icon, title, subtitle, summary, UiKit.INK, UiKit.ACCENT_SOFT);
            handler.postDelayed(autoClose, AUTO_CLOSE_MS);
        }, wait);
    }

    public void failure(IconView.Type icon, String title, String subtitle, String summary) {
        finished = true;
        applyResult(icon, title, subtitle, summary, UiKit.DANGER, UiKit.DANGER_SOFT);
    }

    private void applyResult(
            IconView.Type icon,
            String title,
            String subtitle,
            String summary,
            int summaryColor,
            int tileBackground
    ) {
        handler.removeCallbacks(autoClose);
        titleView.setText(title);
        subtitleView.setText(subtitle);
        tileIcon.type(icon);
        if (tileIcon.getParent() instanceof FrameLayout) {
            ((FrameLayout) tileIcon.getParent()).setBackground(
                    UiKit.rounded(dialog.getContext(), tileBackground, 13f)
            );
        }
        bar.setVisibility(View.INVISIBLE);
        stageView.setText(summary);
        stageView.setTextColor(summaryColor);
        actionButton.setText("完成");
        actionButton.setEnabled(true);
    }

    public void dismiss() {
        handler.removeCallbacks(autoClose);
        if (dialog.isShowing()) dialog.dismiss();
    }

    private static String withoutPercent(String text) {
        if (text == null) return "";
        int cut = text.lastIndexOf(' ');
        if (cut > 0 && text.endsWith("%")) return text.substring(0, cut);
        return text;
    }

    private static int dp(Activity activity, float value) {
        return UiKit.dp(activity, value);
    }
}
