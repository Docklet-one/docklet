package com.docklet.shell.ui;

import android.app.Activity;
import android.app.Dialog;
import android.graphics.Color;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * 所有居中弹窗的公共外壳：圆角卡片 + 图标/标题/副标题 + 内容区 + 底部按钮。
 * 尺寸、透明背景、居中这些样板只在这里写一次。
 */
public final class BaseDialog {

    public static final float WIDTH_DP = 320f;

    private final Activity activity;
    private final Dialog dialog;
    private final LinearLayout content;
    private final LinearLayout body;
    private LinearLayout actions;
    private TextView titleView;
    private TextView subtitleView;
    private IconView iconView;
    private FrameLayout iconTile;

    private BaseDialog(Activity activity, IconView.Type icon, String title, String subtitle) {
        this.activity = activity;
        this.dialog = new Dialog(activity);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setCancelable(true);

        content = new LinearLayout(activity);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(20), dp(20), dp(20), dp(20));
        content.setBackground(UiKit.rounded(activity, UiKit.SURFACE, 24f));
        content.setElevation(dp(8));

        LinearLayout head = new LinearLayout(activity);
        head.setOrientation(LinearLayout.HORIZONTAL);
        head.setGravity(Gravity.CENTER_VERTICAL);
        FrameLayout tile = new FrameLayout(activity);
        tile.setBackground(UiKit.rounded(activity, UiKit.ACCENT_SOFT, 13f));
        iconTile = tile;
        iconView = new IconView(activity).type(icon);
        iconView.setColor(UiKit.ACCENT_DEEP);
        tile.addView(iconView, new FrameLayout.LayoutParams(dp(22), dp(22), Gravity.CENTER));
        head.addView(tile, new LinearLayout.LayoutParams(dp(42), dp(42)));

        LinearLayout titles = new LinearLayout(activity);
        titles.setOrientation(LinearLayout.VERTICAL);
        titleView = UiKit.text(activity, title == null ? "" : title, 17f, UiKit.INK, true);
        titles.addView(titleView);
        subtitleView = UiKit.text(activity, subtitle == null ? "" : subtitle, 12f, UiKit.MUTED, false);
        subtitleView.setMaxLines(1);
        subtitleView.setEllipsize(android.text.TextUtils.TruncateAt.END);
        titles.addView(subtitleView);
        LinearLayout.LayoutParams titlesParams = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f
        );
        titlesParams.leftMargin = dp(12);
        head.addView(titles, titlesParams);
        content.addView(head);

        body = new LinearLayout(activity);
        body.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams bodyParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        );
        bodyParams.topMargin = dp(14);
        content.addView(body, bodyParams);
    }

    public static BaseDialog create(
            Activity activity,
            IconView.Type icon,
            String title,
            String subtitle
    ) {
        return new BaseDialog(activity, icon, title, subtitle);
    }

    /** 图标底色与颜色。 */
    public BaseDialog tint(int softColor, int iconColor) {
        if (iconTile != null) {
            iconTile.setBackground(UiKit.rounded(activity, softColor, 13f));
        }
        if (iconView != null) iconView.setColor(iconColor);
        return this;
    }

    public BaseDialog setTitle(String title) {
        if (titleView != null) titleView.setText(title);
        return this;
    }

    public BaseDialog setSubtitle(String subtitle) {
        if (subtitleView != null) subtitleView.setText(subtitle == null ? "" : subtitle);
        return this;
    }

    public BaseDialog setIcon(IconView.Type type, int softColor, int iconColor) {
        if (iconView != null) iconView.type(type);
        if (iconTile != null) iconTile.setBackground(UiKit.rounded(activity, softColor, 13f));
        if (iconView != null) iconView.setColor(iconColor);
        return this;
    }

    public LinearLayout body() {
        return body;
    }

    public Activity activity() {
        return activity;
    }

    /** 底部按钮区（水平等宽）。返回按钮容器，可自行 addView。 */
    public LinearLayout actions() {
        if (actions == null) {
            actions = new LinearLayout(activity);
            actions.setOrientation(LinearLayout.HORIZONTAL);
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
            );
            params.topMargin = dp(18);
            content.addView(actions, params);
        }
        return actions;
    }

    public TextView addButton(String label, boolean primary, View.OnClickListener click) {
        TextView button = primary
                ? UiKit.primaryButton(activity, label)
                : UiKit.secondaryButton(activity, label);
        button.setOnClickListener(click);
        LinearLayout bar = actions();
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                0, dp(44), 1f
        );
        if (bar.getChildCount() > 0) params.leftMargin = dp(10);
        bar.addView(button, params);
        return button;
    }

    public TextView addFullWidthButton(String label, View.OnClickListener click) {
        TextView button = UiKit.secondaryButton(activity, label);
        button.setOnClickListener(click);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(44)
        );
        params.topMargin = dp(18);
        content.addView(button, params);
        return button;
    }

    public void show() {
        dialog.setContentView(content);
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
            dialog.getWindow().setGravity(Gravity.CENTER);
        }
        dialog.show();
        if (dialog.getWindow() != null) {
            dialog.getWindow().setLayout(dp(WIDTH_DP), ViewGroup.LayoutParams.WRAP_CONTENT);
        }
    }

    public void dismiss() {
        try {
            dialog.dismiss();
        } catch (Exception ignored) {
            // 已经关掉了
        }
    }

    public boolean isShowing() {
        return dialog.isShowing();
    }

    public Dialog raw() {
        return dialog;
    }

    private int dp(float value) {
        return UiKit.dp(activity, value);
    }
}
