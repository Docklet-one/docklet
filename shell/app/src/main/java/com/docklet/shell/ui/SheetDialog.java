package com.docklet.shell.ui;

import android.app.Activity;
import android.app.Dialog;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.view.Window;
import android.view.WindowInsets;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;

import com.docklet.shell.R;

/** Bottom sheet styled dialog with consistent rounded surface and insets. */
public final class SheetDialog extends Dialog {

    /** Dialogs currently on screen, so a page switch can close them all. */
    private static final java.util.Set<SheetDialog> OPEN =
            java.util.Collections.newSetFromMap(new java.util.WeakHashMap<>());

    public static void dismissOpen() {
        for (SheetDialog dialog : new java.util.ArrayList<>(OPEN)) {
            if (dialog.isShowing()) dialog.dismiss();
        }
        OPEN.clear();
    }

    private final LinearLayout sheet;
    private final FrameLayout contentHost;
    private final ScrollView scroll;
    private final LinearLayout footerHost;
    private final View handle;
    private int baseBottomPadding;
    private boolean hasFooter;
    private int navigationInset;

    public SheetDialog(Activity activity) {
        super(activity);
        requestWindowFeature(Window.FEATURE_NO_TITLE);

        sheet = new LinearLayout(activity);
        sheet.setOrientation(LinearLayout.VERTICAL);
        sheet.setPadding(0, 0, 0, 0);
        GradientDrawable background = new GradientDrawable();
        background.setColor(UiKit.SURFACE);
        float radius = UiKit.dp(activity, 28);
        background.setCornerRadii(new float[]{radius, radius, radius, radius, 0, 0, 0, 0});
        sheet.setBackground(background);

        handle = new View(activity);
        GradientDrawable handleBg = UiKit.rounded(activity, 0xFFE2D8CA, 999f);
        handle.setBackground(handleBg);
        LinearLayout.LayoutParams handleParams = new LinearLayout.LayoutParams(
                UiKit.dp(activity, 38), UiKit.dp(activity, 4)
        );
        handleParams.gravity = Gravity.CENTER_HORIZONTAL;
        handleParams.topMargin = UiKit.dp(activity, 9);
        handleParams.bottomMargin = UiKit.dp(activity, 5);
        sheet.addView(handle, handleParams);

        contentHost = new FrameLayout(activity);
        scroll = new ScrollView(activity);
        scroll.setFillViewport(false);
        scroll.setClipToPadding(false);
        scroll.setVerticalScrollBarEnabled(false);
        scroll.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ));
        scroll.addView(contentHost, new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ));
        sheet.addView(scroll, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ));

        footerHost = new LinearLayout(activity);
        footerHost.setOrientation(LinearLayout.VERTICAL);
        footerHost.setVisibility(View.GONE);
        sheet.addView(footerHost, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ));

        baseBottomPadding = UiKit.dp(activity, 36);
        applyContentPadding();
        updateFooterPadding();

        setContentView(sheet);
        setCanceledOnTouchOutside(true);

        Window window = getWindow();
        if (window != null) {
            window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            window.setGravity(Gravity.BOTTOM);
            window.setDimAmount(0.38f);
            window.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
            window.setWindowAnimations(R.style.DockletSheetAnimation);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                window.setDecorFitsSystemWindows(false);
            } else {
                window.getDecorView().setSystemUiVisibility(
                        View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                                | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                );
            }
        }

        sheet.setOnApplyWindowInsetsListener((view, insets) -> {
            int bottom;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                bottom = insets.getInsets(WindowInsets.Type.navigationBars()).bottom;
            } else {
                bottom = insets.getSystemWindowInsetBottom();
            }
            navigationInset = bottom;
            applyContentPadding();
            updateFooterPadding();
            return insets;
        });
    }

    public SheetDialog setBody(View body) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        );
        body.setLayoutParams(params);
        contentHost.removeAllViews();
        contentHost.addView(body);
        return this;
    }

    public SheetDialog setFooter(View footer) {
        footerHost.removeAllViews();
        footerHost.addView(footer, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ));
        footerHost.setVisibility(View.VISIBLE);
        hasFooter = true;
        applyContentPadding();
        updateFooterPadding();
        return this;
    }

    /** With a footer the buttons provide the bottom spacing, so the body keeps none. */
    private void applyContentPadding() {
        if (contentHost == null) return;
        int bottom = hasFooter ? 0 : baseBottomPadding + navigationInset;
        contentHost.setPadding(UiKit.dp(getContext(), 22), 0, UiKit.dp(getContext(), 22), bottom);
    }

    private void updateFooterPadding() {
        if (footerHost == null) return;
        footerHost.setPadding(
                UiKit.dp(getContext(), 22), UiKit.dp(getContext(), 6),
                UiKit.dp(getContext(), 22), baseBottomPadding + navigationInset
        );
    }

    @Override
    public void show() {
        super.show();
        Window window = getWindow();
        if (window != null) {
            window.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        }
        sheet.requestApplyInsets();
        sheet.post(this::fitToScreen);
    }

    private void fitToScreen() {
        Window window = getWindow();
        if (window == null) return;
        int width = getContext().getResources().getDisplayMetrics().widthPixels;
        int height = getContext().getResources().getDisplayMetrics().heightPixels;
        int maxHeight = height - UiKit.dp(getContext(), 48);
        sheet.measure(
                View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
        );
        if (sheet.getMeasuredHeight() <= maxHeight) return;
        int handleHeight = Math.max(handle.getHeight(), UiKit.dp(getContext(), 18));
        int footerHeight = footerHost.getVisibility() == View.VISIBLE
                ? footerHost.getMeasuredHeight() : 0;
        int scrollHeight = Math.max(
                UiKit.dp(getContext(), 120),
                maxHeight - handleHeight - footerHeight
        );
        scroll.getLayoutParams().height = scrollHeight;
        sheet.getLayoutParams().height = maxHeight;
        window.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, maxHeight);
        sheet.requestLayout();
    }

}
