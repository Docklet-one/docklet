package com.docklet.shell.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.view.View;

/** Small abstract "stacked modules" illustration for the empty state. */
public final class EmptyArtView extends View {

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint stroke = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rect = new RectF();
    private final Path path = new Path();

    public EmptyArtView(Context context) {
        super(context);
        stroke.setStyle(Paint.Style.STROKE);
        stroke.setStrokeWidth(UiKit.dp(context, 1.2f));
        stroke.setColor(UiKit.LINE);
        setLayerType(View.LAYER_TYPE_SOFTWARE, null);
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        setMeasuredDimension(
                resolveSize(UiKit.dp(getContext(), 214), widthMeasureSpec),
                resolveSize(UiKit.dp(getContext(), 150), heightMeasureSpec)
        );
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float w = getWidth();
        float h = getHeight();
        float unit = Math.min(w / 214f, h / 150f);
        float cardW = 84f * unit;
        float cardH = 84f * unit;
        float centerX = w / 2f;
        float centerY = h / 2f + 4f * unit;

        paint.setColor(0xFFEDE4D6);
        rect.set(centerX - cardW / 2f - 40f * unit, centerY - cardH / 2f - 18f * unit,
                centerX + cardW / 2f - 40f * unit, centerY + cardH / 2f - 18f * unit);
        canvas.save();
        canvas.rotate(-9f, rect.centerX(), rect.centerY());
        canvas.drawRoundRect(rect, 18f * unit, 18f * unit, paint);
        canvas.restore();

        paint.setColor(0xFFE5D5C0);
        rect.set(centerX - cardW / 2f + 38f * unit, centerY - cardH / 2f - 10f * unit,
                centerX + cardW / 2f + 38f * unit, centerY + cardH / 2f - 10f * unit);
        canvas.save();
        canvas.rotate(8f, rect.centerX(), rect.centerY());
        canvas.drawRoundRect(rect, 18f * unit, 18f * unit, paint);
        canvas.restore();

        rect.set(centerX - cardW / 2f, centerY - cardH / 2f,
                centerX + cardW / 2f, centerY + cardH / 2f);
        paint.setColor(UiKit.SURFACE);
        canvas.drawRoundRect(rect, 20f * unit, 20f * unit, paint);
        canvas.drawRoundRect(rect, 20f * unit, 20f * unit, stroke);

        paint.setColor(UiKit.ACCENT_SOFT);
        canvas.drawCircle(centerX, centerY - 8f * unit, 23f * unit, paint);

        stroke.setColor(UiKit.ACCENT);
        stroke.setStrokeWidth(2.6f * unit);
        stroke.setStrokeCap(Paint.Cap.ROUND);
        canvas.drawLine(centerX - 8f * unit, centerY - 8f * unit,
                centerX + 8f * unit, centerY - 8f * unit, stroke);
        canvas.drawLine(centerX, centerY - 16f * unit,
                centerX, centerY, stroke);

        stroke.setColor(UiKit.FAINT);
        stroke.setStrokeWidth(1.5f * unit);
        paint.setColor(UiKit.FAINT);
        paint.setTextAlign(Paint.Align.CENTER);
        paint.setTextSize(7.5f * unit);
        paint.setTypeface(android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.NORMAL));
        canvas.drawText("MODULE", centerX, centerY + 25f * unit, paint);

        paint.setColor(0x22D9792B);
        canvas.drawCircle(centerX + 34f * unit, centerY + 31f * unit, 14f * unit, paint);
        paint.setColor(UiKit.ACCENT);
        canvas.drawCircle(centerX + 34f * unit, centerY + 31f * unit, 3.2f * unit, paint);

        path.reset();
        path.moveTo(centerX - 47f * unit, centerY + 48f * unit);
        path.cubicTo(
                centerX - 20f * unit, centerY + 62f * unit,
                centerX + 28f * unit, centerY + 62f * unit,
                centerX + 53f * unit, centerY + 48f * unit
        );
        stroke.setColor(0x33C7B8A5);
        stroke.setStrokeWidth(1.4f * unit);
        canvas.drawPath(path, stroke);
    }
}
