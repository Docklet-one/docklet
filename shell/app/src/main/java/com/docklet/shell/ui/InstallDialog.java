package com.docklet.shell.ui;

import android.app.Activity;
import android.content.res.ColorStateList;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

/**
 * 安装 / 更新 / 导入 / 卸载共用弹窗：公共外壳来自 BaseDialog，
 * 这里只加进度条、阶段文字和一个按钮槽位。
 */
public final class InstallDialog {

    private static final long AUTO_CLOSE_MS = 1600L;
    private static final long PROGRESS_AFTER_MS = 700L;
    private static final long MIN_TOTAL_MS = 1300L;

    private final BaseDialog base;
    private final TextView stageView;
    private final ProgressBar bar;
    private final TextView actionButton;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable autoClose;
    private final long startedAt = SystemClock.elapsedRealtime();
    private Runnable onCancel;
    private boolean finished;

    private InstallDialog(Activity activity, IconView.Type icon, String title, String subtitle) {
        base = BaseDialog.create(activity, icon, title, subtitle);
        autoClose = this::dismiss;

        LinearLayout body = base.body();

        bar = new ProgressBar(activity, null, android.R.attr.progressBarStyleHorizontal);
        bar.setMax(100);
        bar.setProgressTintList(ColorStateList.valueOf(UiKit.ACCENT));
        bar.setProgressBackgroundTintList(ColorStateList.valueOf(UiKit.SURFACE_SOFT));
        LinearLayout.LayoutParams barParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, UiKit.dp(activity, 6)
        );
        barParams.topMargin = UiKit.dp(activity, 4);
        body.addView(bar, barParams);

        stageView = UiKit.text(activity, "准备中…", 12f, UiKit.MUTED, false);
        LinearLayout.LayoutParams stageParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        );
        stageParams.topMargin = UiKit.dp(activity, 10);
        body.addView(stageView, stageParams);

        final TextView[] holder = new TextView[1];
        holder[0] = base.addFullWidthButton("取消", view -> {
            if (finished) {
                dismiss();
                return;
            }
            if (onCancel != null) {
                holder[0].setEnabled(false);
                holder[0].setText("正在取消…");
                onCancel.run();
            } else {
                dismiss();
            }
        });
        actionButton = holder[0];

        base.show();
    }

    public static InstallDialog show(
            Activity activity,
            IconView.Type icon,
            String title,
            String subtitle
    ) {
        return new InstallDialog(activity, icon, title, subtitle);
    }

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
        base.setTitle(title);
        base.setSubtitle(subtitle);
        base.setIcon(
                icon,
                tileBackground,
                summaryColor == UiKit.DANGER ? UiKit.DANGER : UiKit.ACCENT_DEEP
        );
        bar.setVisibility(View.INVISIBLE);
        stageView.setText(summary);
        stageView.setTextColor(summaryColor);
        actionButton.setText("完成");
        actionButton.setEnabled(true);
    }

    public void dismiss() {
        handler.removeCallbacks(autoClose);
        base.dismiss();
    }

    private static String withoutPercent(String text) {
        if (text == null) return "";
        int cut = text.lastIndexOf(' ');
        if (cut > 0 && text.endsWith("%")) return text.substring(0, cut);
        return text;
    }
}
