package com.docklet.shell.ui;

import android.content.Context;
import android.util.AttributeSet;
import android.view.View;
import android.view.ViewGroup;

/** A simple wrapping row group: children keep their natural width and flow to the next line. */
public final class FlowLayout extends ViewGroup {

    private int gapX;
    private int gapY;

    public FlowLayout(Context context) {
        super(context);
    }

    public FlowLayout(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public void setGap(int x, int y) {
        gapX = Math.max(0, x);
        gapY = Math.max(0, y);
        requestLayout();
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int widthSize = MeasureSpec.getSize(widthMeasureSpec);
        int widthMode = MeasureSpec.getMode(widthMeasureSpec);
        int available = Math.max(0, widthSize - getPaddingLeft() - getPaddingRight());

        int lineWidth = 0;
        int lineHeight = 0;
        int contentHeight = 0;
        int contentWidth = 0;

        for (int i = 0; i < getChildCount(); i += 1) {
            View child = getChildAt(i);
            if (child.getVisibility() == GONE) continue;
            measureChild(child, widthMeasureSpec, heightMeasureSpec);
            int childWidth = child.getMeasuredWidth();
            int childHeight = child.getMeasuredHeight();
            int needed = lineWidth == 0 ? childWidth : lineWidth + gapX + childWidth;
            if (lineWidth > 0 && needed > available) {
                contentWidth = Math.max(contentWidth, lineWidth);
                contentHeight += lineHeight + gapY;
                lineWidth = childWidth;
                lineHeight = childHeight;
            } else {
                lineWidth = needed;
                lineHeight = Math.max(lineHeight, childHeight);
            }
        }

        contentWidth = Math.max(contentWidth, lineWidth);
        contentHeight += lineHeight;
        int measuredWidth = widthMode == MeasureSpec.EXACTLY
                ? widthSize
                : Math.min(widthSize, contentWidth + getPaddingLeft() + getPaddingRight());
        int measuredHeight = resolveSize(
                contentHeight + getPaddingTop() + getPaddingBottom(),
                heightMeasureSpec
        );
        setMeasuredDimension(measuredWidth, measuredHeight);
    }

    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        int available = Math.max(0, getWidth() - getPaddingLeft() - getPaddingRight());
        int x = getPaddingLeft();
        int y = getPaddingTop();
        int lineHeight = 0;

        for (int i = 0; i < getChildCount(); i += 1) {
            View child = getChildAt(i);
            if (child.getVisibility() == GONE) continue;
            int childWidth = child.getMeasuredWidth();
            int childHeight = child.getMeasuredHeight();
            if (x > getPaddingLeft() && x + childWidth > getPaddingLeft() + available) {
                x = getPaddingLeft();
                y += lineHeight + gapY;
                lineHeight = 0;
            }
            child.layout(x, y, x + childWidth, y + childHeight);
            x += childWidth + gapX;
            lineHeight = Math.max(lineHeight, childHeight);
        }
    }

    @Override
    protected LayoutParams generateDefaultLayoutParams() {
        return new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
    }

    @Override
    public LayoutParams generateLayoutParams(AttributeSet attrs) {
        return new LayoutParams(getContext(), attrs);
    }

    @Override
    protected LayoutParams generateLayoutParams(LayoutParams p) {
        return new LayoutParams(p);
    }

    @Override
    protected boolean checkLayoutParams(LayoutParams p) {
        return p != null;
    }
}
