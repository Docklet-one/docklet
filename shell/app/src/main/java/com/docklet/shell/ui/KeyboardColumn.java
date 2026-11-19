package com.docklet.shell.ui;

import android.content.Context;
import android.os.Build;
import android.view.View;
import android.view.WindowInsets;
import android.widget.LinearLayout;

/**
 * A vertical column whose content shrinks for the keyboard while the bottom bar
 * stays put. While the keyboard is open the bottom bar is collapsed, so the
 * content reaches the keyboard edge with no dead band above it.
 */
public final class KeyboardColumn extends LinearLayout {

    private final LinearLayout content;

    public KeyboardColumn(Context context) {
        super(context);
        setOrientation(VERTICAL);
        content = new LinearLayout(context);
        content.setOrientation(VERTICAL);
        addView(content, new LayoutParams(
                LayoutParams.MATCH_PARENT, 0, 1f
        ));
        setOnApplyWindowInsetsListener((view, insets) -> {
            int ime = Build.VERSION.SDK_INT >= Build.VERSION_CODES.R
                    ? insets.getInsets(WindowInsets.Type.ime()).bottom
                    : insets.getSystemWindowInsetBottom();
            if (content.getPaddingBottom() != ime) {
                content.setPadding(0, 0, 0, ime);
                // No room left for the bottom bar while typing: collapse it so
                // the content ends exactly at the keyboard instead of leaving
                // an empty strip.
                int fixedVisibility = ime > 0 ? GONE : VISIBLE;
                for (int i = 0; i < getChildCount(); i += 1) {
                    View child = getChildAt(i);
                    if (child != content) child.setVisibility(fixedVisibility);
                }
            }
            return insets;
        });
    }

    /** The area that gets shorter while the keyboard is open. */
    public LinearLayout content() {
        return content;
    }
}
