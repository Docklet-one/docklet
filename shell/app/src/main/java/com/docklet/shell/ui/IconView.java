package com.docklet.shell.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.View;

/** Lightweight vector icons drawn with strokes, no icon dependency. */
public final class IconView extends View {

    public enum Type {
        PLUS,
        BACK,
        CLOSE,
        MORE,
        SETTINGS,
        GRID,
        ACTIVITY,
        SEARCH,
        CHEVRON_RIGHT,
        DOWNLOAD,
        FILE,
        TRASH,
        EDIT,
        CHECK,
        REFRESH,
        INFO,
        HOME,
        CALENDAR_HEART,
        SPARK,
        LIST,
        TIMER,
        BRUSH,
        CALC,
        QUIZ,
        NETWORK,
        STAR,
        HEART,
        MUSIC,
        GAME,
        BOOK,
        FOLDER,
        IMAGE,
        CHAT,
        CAMERA,
        LEAF,
        BRIEFCASE,
        FLASK,
        INBOX,
        MAIL,
        SHIELD,
        SIGNPOST,
        FOLDER_QUERY,
        CLOUD_DOWN
    }

    private final Paint stroke = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path path = new Path();
    private final RectF arc = new RectF();
    private Type type = Type.PLUS;

    public IconView(Context context) {
        this(context, null);
    }

    public IconView(Context context, AttributeSet attrs) {
        super(context, attrs);
        stroke.setStyle(Paint.Style.STROKE);
        stroke.setStrokeCap(Paint.Cap.ROUND);
        stroke.setStrokeJoin(Paint.Join.ROUND);
        stroke.setColor(UiKit.INK);
        fill.setStyle(Paint.Style.FILL);
        fill.setColor(UiKit.INK);
    }

    public IconView type(Type type) {
        this.type = type;
        invalidate();
        return this;
    }

    public void setColor(int color) {
        stroke.setColor(color);
        fill.setColor(color);
        invalidate();
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int desired = UiKit.dp(getContext(), 22);
        int width = resolveSize(desired, widthMeasureSpec);
        int height = resolveSize(desired, heightMeasureSpec);
        setMeasuredDimension(width, height);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float size = Math.min(getWidth(), getHeight());
        if (size <= 0f) return;
        float scale = size / 24f;
        float offsetX = (getWidth() - size) / 2f;
        float offsetY = (getHeight() - size) / 2f;
        canvas.save();
        canvas.translate(offsetX, offsetY);
        canvas.scale(scale, scale);
        stroke.setStrokeWidth(1.85f);
        stroke.setAlpha(255);
        fill.setAlpha(255);
        path.reset();

        switch (type) {
            case PLUS:
                line(canvas, 12, 5, 12, 19);
                line(canvas, 5, 12, 19, 12);
                break;
            case BACK:
                path.moveTo(14.5f, 5f);
                path.lineTo(7.5f, 12f);
                path.lineTo(14.5f, 19f);
                canvas.drawPath(path, stroke);
                break;
            case CLOSE:
                line(canvas, 6.5f, 6.5f, 17.5f, 17.5f);
                line(canvas, 17.5f, 6.5f, 6.5f, 17.5f);
                break;
            case MORE:
                canvas.drawCircle(6f, 12f, 1.45f, fill);
                canvas.drawCircle(12f, 12f, 1.45f, fill);
                canvas.drawCircle(18f, 12f, 1.45f, fill);
                break;
            case GRID:
                canvas.drawRoundRect(4f, 4f, 10f, 10f, 1.5f, 1.5f, stroke);
                canvas.drawRoundRect(14f, 4f, 20f, 10f, 1.5f, 1.5f, stroke);
                canvas.drawRoundRect(4f, 14f, 10f, 20f, 1.5f, 1.5f, stroke);
                canvas.drawRoundRect(14f, 14f, 20f, 20f, 1.5f, 1.5f, stroke);
                break;
            case ACTIVITY:
                path.moveTo(3f, 12f);
                path.lineTo(7f, 12f);
                path.lineTo(9.2f, 6f);
                path.lineTo(13.2f, 18f);
                path.lineTo(15.4f, 12f);
                path.lineTo(21f, 12f);
                canvas.drawPath(path, stroke);
                break;
            case SEARCH:
                canvas.drawCircle(10.8f, 10.8f, 6.3f, stroke);
                line(canvas, 15.5f, 15.5f, 20f, 20f);
                break;
            case SETTINGS:
                line(canvas, 4.5f, 7f, 19.5f, 7f);
                line(canvas, 4.5f, 12f, 19.5f, 12f);
                line(canvas, 4.5f, 17f, 19.5f, 17f);
                knob(canvas, 9f, 7f);
                knob(canvas, 15.5f, 12f);
                knob(canvas, 7f, 17f);
                break;
            case CHEVRON_RIGHT:
                path.moveTo(9f, 5.5f);
                path.lineTo(15.5f, 12f);
                path.lineTo(9f, 18.5f);
                canvas.drawPath(path, stroke);
                break;
            case DOWNLOAD:
                line(canvas, 12, 4, 12, 14.5f);
                path.moveTo(7.5f, 10.5f);
                path.lineTo(12f, 15f);
                path.lineTo(16.5f, 10.5f);
                canvas.drawPath(path, stroke);
                path.reset();
                path.moveTo(5f, 16.5f);
                path.lineTo(5f, 20f);
                path.lineTo(19f, 20f);
                path.lineTo(19f, 16.5f);
                canvas.drawPath(path, stroke);
                break;
            case FILE:
                path.moveTo(6f, 3.5f);
                path.lineTo(14f, 3.5f);
                path.lineTo(18.5f, 8f);
                path.lineTo(18.5f, 20.5f);
                path.lineTo(6f, 20.5f);
                path.close();
                canvas.drawPath(path, stroke);
                path.reset();
                path.moveTo(14f, 3.5f);
                path.lineTo(14f, 8f);
                path.lineTo(18.5f, 8f);
                canvas.drawPath(path, stroke);
                line(canvas, 8.5f, 13, 16f, 13);
                line(canvas, 8.5f, 16.5f, 14f, 16.5f);
                break;
            case EDIT:
                // Pencil: body with a tip at the lower left.
                path.moveTo(4.4f, 19.6f);
                path.lineTo(5.3f, 15.4f);
                path.lineTo(15.1f, 5.6f);
                path.lineTo(18.4f, 8.9f);
                path.lineTo(8.6f, 18.7f);
                path.close();
                canvas.drawPath(path, stroke);
                line(canvas, 13.9f, 6.8f, 17.2f, 10.1f);
                break;
            case TRASH:
                line(canvas, 5f, 7f, 19f, 7f);
                path.moveTo(9f, 7f);
                path.lineTo(9f, 4.5f);
                path.lineTo(15f, 4.5f);
                path.lineTo(15f, 7f);
                canvas.drawPath(path, stroke);
                path.reset();
                path.moveTo(7f, 7.5f);
                path.lineTo(8f, 20.5f);
                path.lineTo(16f, 20.5f);
                path.lineTo(17f, 7.5f);
                canvas.drawPath(path, stroke);
                line(canvas, 10.5f, 11, 10.5f, 17);
                line(canvas, 13.5f, 11, 13.5f, 17);
                break;
            case CHECK:
                path.moveTo(5.5f, 12.5f);
                path.lineTo(10f, 17f);
                path.lineTo(18.5f, 7.5f);
                canvas.drawPath(path, stroke);
                break;
            case REFRESH:
                arc.set(4.8f, 4.8f, 19.2f, 19.2f);
                canvas.drawArc(arc, 45f, 285f, false, stroke);
                // arrow head on the open end, lower right, pointing clockwise
                path.moveTo(15.4f, 13.4f);
                path.lineTo(19.6f, 15.6f);
                path.lineTo(17.2f, 19.4f);
                canvas.drawPath(path, stroke);
                break;
            case CLOUD_DOWN: {
                // Outline cloud (three lobes, bottom open) with the arrow through it.
                stroke.setStrokeWidth(2f);
                path.moveTo(7.2f, 16.6f);
                path.cubicTo(3.1f, 16.2f, 3.1f, 9.0f, 7.7f, 8.5f);
                path.cubicTo(8.6f, 3.6f, 16.4f, 3.4f, 17.3f, 9.5f);
                path.cubicTo(21.5f, 10.4f, 21.9f, 16.3f, 17.8f, 16.6f);
                canvas.drawPath(path, stroke);

                line(canvas, 12f, 10.6f, 12f, 18.2f);
                path.moveTo(9.3f, 15.4f);
                path.lineTo(12f, 18.2f);
                path.lineTo(14.7f, 15.4f);
                canvas.drawPath(path, stroke);
                stroke.setStrokeWidth(1.85f);
                break;
            }
            case INFO:
                canvas.drawCircle(12f, 12f, 8.4f, stroke);
                canvas.drawCircle(12f, 8.1f, 1.05f, fill);
                line(canvas, 12, 11, 12, 16.5f);
                break;
            case HOME:
                path.moveTo(4.5f, 11f);
                path.lineTo(12f, 4.5f);
                path.lineTo(19.5f, 11f);
                path.moveTo(6.5f, 9.5f);
                path.lineTo(6.5f, 19.5f);
                path.lineTo(17.5f, 19.5f);
                path.lineTo(17.5f, 9.5f);
                canvas.drawPath(path, stroke);
                break;
            case CALENDAR_HEART:
                stroke.setStrokeWidth(2.0f);
                canvas.drawRoundRect(3.8f, 5.8f, 20.2f, 20.2f, 1.8f, 1.8f, stroke);
                line(canvas, 3.8f, 9.6f, 20.2f, 9.6f);
                line(canvas, 7.8f, 3.4f, 7.8f, 7.6f);
                line(canvas, 16.2f, 3.4f, 16.2f, 7.6f);
                path.moveTo(12f, 17.1f);
                path.cubicTo(8.7f, 14.7f, 8.1f, 13.2f, 8.1f, 12.1f);
                path.cubicTo(8.1f, 10.7f, 9.2f, 9.8f, 10.5f, 9.8f);
                path.cubicTo(11.3f, 9.8f, 11.8f, 10.2f, 12f, 10.7f);
                path.cubicTo(12.2f, 10.2f, 12.7f, 9.8f, 13.5f, 9.8f);
                path.cubicTo(14.8f, 9.8f, 15.9f, 10.7f, 15.9f, 12.1f);
                path.cubicTo(15.9f, 13.2f, 15.3f, 14.7f, 12f, 17.1f);
                canvas.drawPath(path, stroke);
                stroke.setStrokeWidth(1.85f);
                break;
            case SIGNPOST:
                // Two direction boards on a post, matching the supplied signpost mark.
                canvas.drawRoundRect(4f, 5.2f, 18f, 10.2f, 1.1f, 1.1f, fill);
                path.moveTo(17.2f, 4.2f);
                path.lineTo(22f, 7.7f);
                path.lineTo(17.2f, 11.2f);
                path.close();
                canvas.drawPath(path, fill);
                canvas.drawRoundRect(6f, 13.2f, 20f, 18.2f, 1.1f, 1.1f, fill);
                path.reset();
                path.moveTo(6.8f, 12.2f);
                path.lineTo(2f, 15.7f);
                path.lineTo(6.8f, 19.2f);
                path.close();
                canvas.drawPath(path, fill);
                canvas.drawRoundRect(10.5f, 2.8f, 13.5f, 21f, 1.1f, 1.1f, fill);
                break;
            case SPARK:
                path.moveTo(12f, 3.5f);
                path.lineTo(14.2f, 9.8f);
                path.lineTo(20.5f, 12f);
                path.lineTo(14.2f, 14.2f);
                path.lineTo(12f, 20.5f);
                path.lineTo(9.8f, 14.2f);
                path.lineTo(3.5f, 12f);
                path.lineTo(9.8f, 9.8f);
                path.close();
                canvas.drawPath(path, stroke);
                break;
            case LIST:
                canvas.drawCircle(5.5f, 7f, 1.2f, fill);
                line(canvas, 9f, 7f, 19f, 7f);
                canvas.drawCircle(5.5f, 12f, 1.2f, fill);
                line(canvas, 9f, 12f, 19f, 12f);
                canvas.drawCircle(5.5f, 17f, 1.2f, fill);
                line(canvas, 9f, 17f, 19f, 17f);
                break;
            case TIMER:
                line(canvas, 9.5f, 3.5f, 14.5f, 3.5f);
                line(canvas, 12f, 3.5f, 12f, 5.2f);
                canvas.drawCircle(12f, 13f, 7.8f, stroke);
                line(canvas, 12f, 13f, 12f, 8.5f);
                line(canvas, 12f, 13f, 15.2f, 14.8f);
                break;
            case BRUSH:
                line(canvas, 4.5f, 19.5f, 13.8f, 10.2f);
                path.moveTo(12.5f, 8.8f);
                path.lineTo(17.5f, 3.8f);
                path.lineTo(20.2f, 6.5f);
                path.lineTo(15.2f, 11.5f);
                path.close();
                canvas.drawPath(path, stroke);
                path.reset();
                path.moveTo(4.5f, 19.5f);
                path.lineTo(9.2f, 18.2f);
                path.lineTo(5.8f, 14.8f);
                path.close();
                canvas.drawPath(path, fill);
                break;
            case CALC:
                arc.set(5f, 3f, 19f, 21f);
                canvas.drawRoundRect(arc, 2.5f, 2.5f, stroke);
                line(canvas, 8f, 7f, 16f, 7f);
                canvas.drawCircle(8.5f, 11.5f, 0.9f, fill);
                canvas.drawCircle(12f, 11.5f, 0.9f, fill);
                canvas.drawCircle(15.5f, 11.5f, 0.9f, fill);
                canvas.drawCircle(8.5f, 15f, 0.9f, fill);
                canvas.drawCircle(12f, 15f, 0.9f, fill);
                canvas.drawCircle(15.5f, 15f, 0.9f, fill);
                canvas.drawCircle(8.5f, 18.5f, 0.9f, fill);
                canvas.drawCircle(12f, 18.5f, 0.9f, fill);
                canvas.drawCircle(15.5f, 18.5f, 0.9f, fill);
                break;
            case QUIZ:
                arc.set(6.5f, 4.5f, 17.5f, 15.5f);
                canvas.drawArc(arc, 200f, 210f, false, stroke);
                line(canvas, 13.8f, 13.5f, 12f, 16.5f);
                canvas.drawCircle(12f, 19.3f, 1.1f, fill);
                break;
            case NETWORK:
                canvas.drawCircle(12f, 12f, 8.2f, stroke);
                line(canvas, 3.8f, 12f, 20.2f, 12f);
                arc.set(8f, 3.8f, 16f, 20.2f);
                canvas.drawOval(arc, stroke);
                break;
            case STAR:
                path.moveTo(12f, 3.5f);
                path.lineTo(14.4f, 9f);
                path.lineTo(20.3f, 9.7f);
                path.lineTo(15.8f, 13.9f);
                path.lineTo(17.1f, 19.8f);
                path.lineTo(12f, 16.7f);
                path.lineTo(6.9f, 19.8f);
                path.lineTo(8.2f, 13.9f);
                path.lineTo(3.7f, 9.7f);
                path.lineTo(9.6f, 9f);
                path.close();
                canvas.drawPath(path, stroke);
                break;
            case HEART:
                path.moveTo(12f, 20.2f);
                path.cubicTo(3.2f, 13.5f, 4.8f, 6.8f, 8.6f, 6.8f);
                path.cubicTo(10.5f, 6.8f, 11.7f, 8.5f, 12f, 10.2f);
                path.cubicTo(12.3f, 8.5f, 13.5f, 6.8f, 15.4f, 6.8f);
                path.cubicTo(19.2f, 6.8f, 20.8f, 13.5f, 12f, 20.2f);
                path.close();
                canvas.drawPath(path, stroke);
                break;
            case MUSIC:
                canvas.drawCircle(8f, 17.5f, 2.2f, fill);
                canvas.drawCircle(17f, 15.5f, 2.2f, fill);
                line(canvas, 10.2f, 17.5f, 10.2f, 6.2f);
                line(canvas, 19.2f, 15.5f, 19.2f, 4.2f);
                line(canvas, 10.2f, 6.2f, 19.2f, 4.2f);
                line(canvas, 10.2f, 9.2f, 19.2f, 7.2f);
                break;
            case GAME:
                canvas.drawRoundRect(3.5f, 7f, 20.5f, 17f, 4.5f, 4.5f, stroke);
                line(canvas, 7f, 12f, 10.8f, 12f);
                line(canvas, 8.9f, 10.1f, 8.9f, 13.9f);
                canvas.drawCircle(16f, 11.4f, 1.1f, fill);
                canvas.drawCircle(18f, 13.5f, 1.1f, fill);
                break;
            case BOOK:
                path.moveTo(4f, 6.2f);
                path.cubicTo(7.2f, 4.8f, 10.1f, 5.2f, 12f, 6.5f);
                path.cubicTo(13.9f, 5.2f, 16.8f, 4.8f, 20f, 6.2f);
                path.lineTo(20f, 19f);
                path.cubicTo(16.8f, 17.6f, 13.9f, 18f, 12f, 19.3f);
                path.cubicTo(10.1f, 18f, 7.2f, 17.6f, 4f, 19f);
                path.close();
                canvas.drawPath(path, stroke);
                line(canvas, 12f, 6.5f, 12f, 19.3f);
                break;
            case FOLDER:
                path.moveTo(4f, 7f);
                path.lineTo(9.5f, 7f);
                path.lineTo(11.5f, 9f);
                path.lineTo(20f, 9f);
                path.lineTo(20f, 19f);
                path.lineTo(4f, 19f);
                path.close();
                canvas.drawPath(path, stroke);
                line(canvas, 4f, 11f, 20f, 11f);
                break;
            case FOLDER_QUERY:
                // Folder outline with an open question mark, for unsorted apps.
                path.moveTo(3.4f, 6.3f);
                path.lineTo(9.0f, 6.3f);
                path.lineTo(10.9f, 8.5f);
                path.lineTo(20.6f, 8.5f);
                path.lineTo(20.6f, 20.2f);
                path.lineTo(3.4f, 20.2f);
                path.close();
                canvas.drawPath(path, stroke);
                path.reset();
                path.moveTo(13.9f, 13.4f);
                path.cubicTo(13.9f, 11.3f, 15.2f, 10.5f, 16.2f, 10.9f);
                path.cubicTo(17.4f, 11.3f, 17.6f, 12.6f, 16.8f, 13.5f);
                path.cubicTo(16.2f, 14.1f, 15.8f, 14.4f, 15.8f, 15.2f);
                canvas.drawPath(path, stroke);
                canvas.drawCircle(15.8f, 17.7f, 1.0f, fill);
                break;
            case IMAGE:
                canvas.drawRoundRect(4f, 5f, 20f, 19f, 2.5f, 2.5f, stroke);
                canvas.drawCircle(9f, 10f, 1.5f, fill);
                path.moveTo(6f, 16.5f);
                path.lineTo(10f, 12.2f);
                path.lineTo(13f, 15.2f);
                path.lineTo(16f, 12f);
                path.lineTo(19f, 16.5f);
                canvas.drawPath(path, stroke);
                break;
            case CHAT:
                canvas.drawRoundRect(4f, 4.8f, 20f, 16.5f, 3.2f, 3.2f, stroke);
                path.moveTo(8f, 16.5f);
                path.lineTo(8f, 20.2f);
                path.lineTo(12.2f, 16.5f);
                canvas.drawPath(path, stroke);
                canvas.drawCircle(8.5f, 10.8f, 1f, fill);
                canvas.drawCircle(12f, 10.8f, 1f, fill);
                canvas.drawCircle(15.5f, 10.8f, 1f, fill);
                break;
            case CAMERA:
                path.moveTo(4f, 8f);
                path.lineTo(8.5f, 8f);
                path.lineTo(9.8f, 5.8f);
                path.lineTo(14.2f, 5.8f);
                path.lineTo(15.5f, 8f);
                path.lineTo(20f, 8f);
                path.lineTo(20f, 19f);
                path.lineTo(4f, 19f);
                path.close();
                canvas.drawPath(path, stroke);
                canvas.drawCircle(12f, 13.2f, 3.8f, stroke);
                break;
            case LEAF:
                path.moveTo(19.8f, 4.2f);
                path.cubicTo(11.5f, 4.2f, 4.8f, 8.2f, 5.1f, 14.1f);
                path.cubicTo(5.3f, 17f, 7.3f, 19.2f, 10.2f, 19.2f);
                path.cubicTo(16.1f, 19.2f, 19.8f, 12.5f, 19.8f, 4.2f);
                path.close();
                canvas.drawPath(path, stroke);
                path.moveTo(5.5f, 18.8f);
                path.cubicTo(9f, 14.5f, 13f, 10.5f, 18f, 7f);
                canvas.drawPath(path, stroke);
                break;
            case BRIEFCASE:
                canvas.drawRoundRect(3.5f, 8f, 20.5f, 20f, 2.5f, 2.5f, stroke);
                path.moveTo(9f, 8f);
                path.lineTo(9f, 5.5f);
                path.lineTo(15f, 5.5f);
                path.lineTo(15f, 8f);
                canvas.drawPath(path, stroke);
                line(canvas, 3.5f, 13f, 20.5f, 13f);
                break;
            case MAIL:
                canvas.drawRoundRect(4f, 6.5f, 20f, 18.5f, 2.5f, 2.5f, stroke);
                path.moveTo(4.8f, 7.4f);
                path.lineTo(12f, 13.2f);
                path.lineTo(19.2f, 7.4f);
                canvas.drawPath(path, stroke);
                break;
            case SHIELD:
                path.moveTo(12f, 3.2f);
                path.lineTo(19.2f, 6f);
                path.lineTo(19.2f, 11.8f);
                path.cubicTo(19.2f, 16.2f, 16.2f, 19.2f, 12f, 20.8f);
                path.cubicTo(7.8f, 19.2f, 4.8f, 16.2f, 4.8f, 11.8f);
                path.lineTo(4.8f, 6f);
                path.close();
                canvas.drawPath(path, stroke);
                break;
            case INBOX:
                path.moveTo(4f, 5f);
                path.lineTo(20f, 5f);
                path.lineTo(20f, 12f);
                path.lineTo(16.2f, 16f);
                path.lineTo(7.8f, 16f);
                path.lineTo(4f, 12f);
                path.close();
                canvas.drawPath(path, stroke);
                path.moveTo(4.8f, 12f);
                path.lineTo(8.6f, 12f);
                path.lineTo(10f, 14f);
                path.lineTo(14f, 14f);
                path.lineTo(15.4f, 12f);
                path.lineTo(19.2f, 12f);
                canvas.drawPath(path, stroke);
                line(canvas, 4f, 19f, 20f, 19f);
                break;
            case FLASK:
                line(canvas, 9f, 3.5f, 15f, 3.5f);
                line(canvas, 10f, 3.5f, 10f, 9f);
                line(canvas, 14f, 3.5f, 14f, 9f);
                path.moveTo(10f, 9f);
                path.lineTo(5.2f, 18.2f);
                path.cubicTo(4.7f, 19.4f, 5.5f, 20.5f, 6.8f, 20.5f);
                path.lineTo(17.2f, 20.5f);
                path.cubicTo(18.5f, 20.5f, 19.3f, 19.4f, 18.8f, 18.2f);
                path.lineTo(14f, 9f);
                canvas.drawPath(path, stroke);
                line(canvas, 7.2f, 15.5f, 16.8f, 15.5f);
                break;
            default:
                break;
        }
        canvas.restore();
    }

    private void line(Canvas canvas, float x1, float y1, float x2, float y2) {
        canvas.drawLine(x1, y1, x2, y2, stroke);
    }

    private void knob(Canvas canvas, float x, float y) {
        fill.setColor(UiKit.SURFACE);
        canvas.drawCircle(x, y, 2.5f, fill);
        fill.setColor(stroke.getColor());
        stroke.setStyle(Paint.Style.STROKE);
        canvas.drawCircle(x, y, 2.5f, stroke);
    }
}
