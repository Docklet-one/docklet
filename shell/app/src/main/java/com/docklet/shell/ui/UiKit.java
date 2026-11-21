package com.docklet.shell.ui;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.Locale;

/** Small design system for the native shell. */
public final class UiKit {

    public static final int BG = Color.rgb(246, 241, 232);
    public static final int SURFACE = Color.rgb(255, 253, 249);
    public static final int SURFACE_SOFT = Color.rgb(243, 236, 225);
    public static final int INK = Color.rgb(36, 31, 25);
    public static final int MUTED = Color.rgb(125, 115, 103);
    public static final int FAINT = Color.rgb(169, 158, 144);
    public static final int LINE = Color.rgb(233, 224, 211);
    public static final int ACCENT = Color.rgb(217, 121, 43);
    public static final int ACCENT_DEEP = Color.rgb(185, 94, 24);
    public static final int ACCENT_SOFT = Color.rgb(248, 230, 212);
    public static final int DANGER = Color.rgb(182, 74, 58);
    public static final int DANGER_SOFT = Color.rgb(249, 232, 228);
    public static final int GREEN = Color.rgb(77, 124, 97);
    public static final int GREEN_SOFT = Color.rgb(237, 244, 238);
    public static final int TEAL = Color.rgb(47, 111, 106);
    public static final int TEAL_SOFT = Color.rgb(222, 238, 234);
    public static final int GOLD = Color.rgb(181, 118, 27);
    public static final int GOLD_SOFT = Color.rgb(246, 235, 211);
    public static final int RIPPLE = 0x18000000;

    public static final String PLATFORM_VERSION = "1.1.0";
    /** 壳版本：直接取 build.gradle 的 versionName（去掉 -debug 后缀），
     *  避免和 versionName 脱节。 */
    public static final String SHELL_VERSION = com.docklet.shell.BuildConfig.VERSION_NAME.split("-")[0];

    private UiKit() {
    }

    public static int dp(Context context, float value) {
        return Math.round(TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP,
                value,
                context.getResources().getDisplayMetrics()
        ));
    }

    public static TextView text(Context context, CharSequence value, float sizeSp, int color) {
        return text(context, value, sizeSp, color, false);
    }

    public static TextView text(
            Context context,
            CharSequence value,
            float sizeSp,
            int color,
            boolean medium
    ) {
        TextView view = new TextView(context);
        view.setText(value);
        view.setTextSize(TypedValue.COMPLEX_UNIT_SP, sizeSp);
        view.setTextColor(color);
        view.setIncludeFontPadding(false);
        view.setTypeface(Typeface.create(medium ? "sans-serif-medium" : "sans-serif", Typeface.NORMAL));
        view.setLineSpacing(0f, 1.12f);
        return view;
    }

    public static TextView label(Context context, CharSequence value, float sizeSp, int color) {
        TextView view = text(context, value, sizeSp, color, true);
        view.setLetterSpacing(0.08f);
        return view;
    }

    public static GradientDrawable rounded(int color, float radiusDp) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(radiusDp);
        return drawable;
    }

    public static GradientDrawable rounded(Context context, int color, float radiusDp) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(dp(context, radiusDp));
        return drawable;
    }

    public static GradientDrawable stroked(Context context, int color, int stroke, float radiusDp) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(dp(context, radiusDp));
        drawable.setStroke(dp(context, 1), stroke);
        return drawable;
    }

    /**
     * 模块图标：模块自带图片优先，圆角靠背景轮廓 + clipToOutline（一行效果，不裁位图）。
     * 没有图片时回落到线条图标 + 淡色底。
     */
    public static void fillModuleIcon(
            android.widget.FrameLayout holder,
            com.docklet.platform.InstalledModule module,
            int sizeDp,
            float radiusDp
    ) {
        if (holder == null || module == null) return;
        android.content.Context context = holder.getContext();
        android.graphics.Bitmap bitmap =
                com.docklet.shell.runtime.ModuleIcons.thumbnail(context, module);
        if (bitmap != null) {
            android.widget.ImageView image = new android.widget.ImageView(context);
            image.setImageBitmap(bitmap);
            image.setScaleType(android.widget.ImageView.ScaleType.FIT_CENTER);
            image.setBackground(rounded(context, 0xFF000000, radiusDp));
            image.setClipToOutline(true);
            holder.addView(image, new android.widget.FrameLayout.LayoutParams(
                    android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                    android.view.ViewGroup.LayoutParams.MATCH_PARENT
            ));
            return;
        }
        int accent = parseColor(module.accent(), ACCENT);
        holder.setBackground(rounded(context, blend(accent, 0xFFFFFFFF, 0.14f), radiusDp));
        IconView glyph = new IconView(context).type(ModuleGlyph.of(module.glyph()));
        glyph.setColor(accent);
        int inner = dp(context, Math.max(14f, sizeDp * 0.52f));
        holder.addView(glyph, new android.widget.FrameLayout.LayoutParams(
                inner, inner, android.view.Gravity.CENTER
        ));
    }

    /** 品牌标记：和网页里 “D + 渐变底 + 圆角” 完全一样，纯代码画，不用图片。 */
    public static TextView brandMark(android.content.Context context, int sizeDp, float textSp) {
        TextView mark = text(context, "D", textSp, 0xFFFFF8ED, true);
        mark.setGravity(android.view.Gravity.CENTER);
        mark.setIncludeFontPadding(false);
        mark.setBackground(gradient(
                context,
                new int[]{0xFF3B2C1F, 0xFF241B13},
                sizeDp * 0.22f
        ));
        mark.setLayoutParams(new android.widget.LinearLayout.LayoutParams(
                dp(context, sizeDp), dp(context, sizeDp)
        ));
        return mark;
    }

    public static GradientDrawable gradient(Context context, int[] colors, float radiusDp) {
        GradientDrawable drawable = new GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                colors
        );
        drawable.setCornerRadius(dp(context, radiusDp));
        return drawable;
    }

    /** Left-to-right gradient, with rounded corners on the left side only. */
    public static GradientDrawable gradientStart(Context context, int[] colors, float radiusDp) {
        GradientDrawable drawable = new GradientDrawable(
                GradientDrawable.Orientation.LEFT_RIGHT,
                colors
        );
        float radius = dp(context, radiusDp);
        drawable.setCornerRadii(new float[]{radius, radius, 0, 0, 0, 0, radius, radius});
        return drawable;
    }

    public static Drawable ripple(Drawable content) {
        return new RippleDrawable(ColorStateList.valueOf(RIPPLE), content, null);
    }

    public static Drawable ripple(Context context, int background, float radiusDp) {
        return ripple(rounded(context, background, radiusDp));
    }

    public static void setMargins(View view, int leftDp, int topDp, int rightDp, int bottomDp) {
        Context context = view.getContext();
        ViewGroup.LayoutParams raw = view.getLayoutParams();
        if (!(raw instanceof ViewGroup.MarginLayoutParams)) return;
        ViewGroup.MarginLayoutParams params = (ViewGroup.MarginLayoutParams) raw;
        params.setMargins(dp(context, leftDp), dp(context, topDp), dp(context, rightDp), dp(context, bottomDp));
        view.setLayoutParams(params);
    }

    public static LinearLayout.LayoutParams lp(int width, int height) {
        return new LinearLayout.LayoutParams(width, height);
    }

    public static LinearLayout.LayoutParams lpWeight(int width, int height, float weight) {
        return new LinearLayout.LayoutParams(width, height, weight);
    }

    public static FrameLayout.LayoutParams flp(int width, int height) {
        return new FrameLayout.LayoutParams(width, height);
    }

    public static View spacer(Context context, int widthDp, int heightDp) {
        View view = new View(context);
        view.setLayoutParams(lp(dp(context, widthDp), dp(context, heightDp)));
        return view;
    }

    public static View divider(Context context) {
        View view = new View(context);
        view.setBackgroundColor(LINE);
        view.setLayoutParams(lp(ViewGroup.LayoutParams.MATCH_PARENT, dp(context, 1)));
        return view;
    }

    public static TextView primaryButton(Context context, CharSequence label) {
        TextView button = text(context, label, 14.5f, Color.rgb(255, 250, 242), true);
        button.setGravity(Gravity.CENTER);
        button.setBackground(ripple(gradient(context, new int[]{0xFFE18B3E, 0xFFD9792B}, 17f)));
        button.setPadding(dp(context, 18), dp(context, 14), dp(context, 18), dp(context, 14));
        button.setMinHeight(dp(context, 52));
        button.setClickable(true);
        button.setFocusable(true);
        button.setElevation(0);
        return button;
    }

    public static TextView secondaryButton(Context context, CharSequence label) {
        TextView button = text(context, label, 13.5f, INK, true);
        button.setGravity(Gravity.CENTER);
        button.setBackground(ripple(rounded(context, 0xFFEFE8DB, 16f)));
        button.setPadding(dp(context, 16), dp(context, 13), dp(context, 16), dp(context, 13));
        button.setMinHeight(dp(context, 48));
        button.setClickable(true);
        button.setFocusable(true);
        return button;
    }

    public static TextView textButton(Context context, CharSequence label, int color) {
        TextView button = text(context, label, 13f, color, true);
        button.setGravity(Gravity.CENTER);
        button.setBackground(ripple(context, Color.TRANSPARENT, 14f));
        button.setPadding(dp(context, 14), dp(context, 11), dp(context, 14), dp(context, 11));
        button.setClickable(true);
        button.setFocusable(true);
        return button;
    }

    public static FrameLayout iconButton(
            Context context,
            IconView icon,
            String description,
            View.OnClickListener listener
    ) {
        FrameLayout container = new FrameLayout(context);
        container.setLayoutParams(lp(dp(context, 44), dp(context, 44)));
        container.setBackground(ripple(stroked(context, SURFACE, LINE, 15f)));
        container.setClickable(true);
        container.setFocusable(true);
        container.setContentDescription(description);
        container.setOnClickListener(listener);
        icon.setColor(INK);
        FrameLayout.LayoutParams iconParams = new FrameLayout.LayoutParams(
                dp(context, 21), dp(context, 21), Gravity.CENTER
        );
        container.addView(icon, iconParams);
        return container;
    }

    public static TextView pill(Context context, CharSequence value, int textColor, int background) {
        TextView view = text(context, value, 10.5f, textColor, true);
        view.setGravity(Gravity.CENTER);
        view.setPadding(dp(context, 9), dp(context, 4), dp(context, 9), dp(context, 4));
        view.setBackground(rounded(context, background, 999f));
        return view;
    }

    public static int parseColor(String value, int fallback) {
        if (value == null) return fallback;
        try {
            return Color.parseColor(value);
        } catch (IllegalArgumentException error) {
            return fallback;
        }
    }

    public static int blend(int color, int background, float amount) {
        float keep = Math.max(0f, Math.min(1f, amount));
        int r = Math.round(Color.red(color) * keep + Color.red(background) * (1f - keep));
        int g = Math.round(Color.green(color) * keep + Color.green(background) * (1f - keep));
        int b = Math.round(Color.blue(color) * keep + Color.blue(background) * (1f - keep));
        return Color.rgb(r, g, b);
    }

    public static String formatBytes(long bytes) {
        if (bytes < 1024) return bytes + " B";
        double value = bytes;
        String[] units = {"KB", "MB", "GB"};
        int index = -1;
        while (value >= 1024 && index < units.length - 1) {
            value /= 1024;
            index += 1;
        }
        return String.format(Locale.US, value >= 10 ? "%.0f %s" : "%.1f %s", value, units[index]);
    }

    public static String initialOf(String name, String id) {
        if (name != null && !name.trim().isEmpty()) {
            return name.trim().substring(0, 1).toUpperCase(Locale.ROOT);
        }
        return id == null || id.isEmpty() ? "D" : id.substring(0, 1).toUpperCase(Locale.ROOT);
    }
}
