package com.jgeraldo.mediaprojectionsample;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;

import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

public class RegionOverlayView extends View {

    private Paint overlayPaint;
    private Paint borderPaint;
    private Paint cornerPaint;
    private Paint gridPaint;
    private Paint guideTextPaint;

    private RectF selectedRegion;
    private RectF viewBounds;

    private static final float MIN_REGION_SIZE = 80f;
    private static final float HANDLE_SIZE = 32f;
    private static final float CORNER_RADIUS = 12f;

    private DragMode currentDragMode = DragMode.NONE;
    private float lastTouchX, lastTouchY;

    private OnRegionChangedListener listener;

    // Grid lines for rule-of-thirds
    private static final int GRID_COLS = 3;
    private static final int GRID_ROWS = 3;

    public enum DragMode {
        NONE, MOVE, RESIZE_TOP_LEFT, RESIZE_TOP_RIGHT,
        RESIZE_BOTTOM_LEFT, RESIZE_BOTTOM_RIGHT,
        RESIZE_TOP, RESIZE_BOTTOM, RESIZE_LEFT, RESIZE_RIGHT,
        DRAG_CREATE
    }

    public interface OnRegionChangedListener {
        void onRegionChanged(RectF region);
    }

    public RegionOverlayView(Context context) {
        super(context);
        init();
    }

    public RegionOverlayView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    private void init() {
        overlayPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        overlayPaint.setColor(Color.parseColor("#99000000"));
        overlayPaint.setStyle(Paint.Style.FILL);

        borderPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        borderPaint.setColor(Color.parseColor("#6366F1"));
        borderPaint.setStyle(Paint.Style.STROKE);
        borderPaint.setStrokeWidth(3f);

        cornerPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        cornerPaint.setColor(Color.parseColor("#818CF8"));
        cornerPaint.setStyle(Paint.Style.FILL);

        gridPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        gridPaint.setColor(Color.parseColor("#40FFFFFF"));
        gridPaint.setStyle(Paint.Style.STROKE);
        gridPaint.setStrokeWidth(1f);

        guideTextPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        guideTextPaint.setColor(Color.parseColor("#80FFFFFF"));
        guideTextPaint.setTextSize(36f);
        guideTextPaint.setTextAlign(Paint.Align.CENTER);
        guideTextPaint.setFakeBoldText(true);

        selectedRegion = new RectF();
        viewBounds = new RectF();
    }

    public void setOnRegionChangedListener(OnRegionChangedListener listener) {
        this.listener = listener;
    }

    public RectF getSelectedRegion() {
        return selectedRegion;
    }

    public void setSelectedRegion(RectF region) {
        this.selectedRegion.set(region);
        invalidate();
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        viewBounds.set(0, 0, w, h);

        // Default region: center 60% of view
        if (selectedRegion.isEmpty()) {
            float margin = 0.2f;
            float left = w * margin;
            float top = h * margin;
            float right = w * (1 - margin);
            float bottom = h * (1 - margin);
            selectedRegion.set(left, top, right, bottom);
        }
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        // Draw the semi-transparent overlay with a hole for the selected region
        // Top rectangle
        canvas.drawRect(0, 0, getWidth(), selectedRegion.top, overlayPaint);
        // Bottom rectangle
        canvas.drawRect(0, selectedRegion.bottom, getWidth(), getHeight(), overlayPaint);
        // Left rectangle
        canvas.drawRect(0, selectedRegion.top, selectedRegion.left, selectedRegion.bottom, overlayPaint);
        // Right rectangle
        canvas.drawRect(selectedRegion.right, selectedRegion.top, getWidth(), selectedRegion.bottom, overlayPaint);

        // Draw border
        canvas.drawRoundRect(selectedRegion, CORNER_RADIUS, CORNER_RADIUS, borderPaint);

        // Draw grid lines (rule of thirds)
        float cellW = selectedRegion.width() / GRID_COLS;
        float cellH = selectedRegion.height() / GRID_ROWS;
        for (int i = 1; i < GRID_COLS; i++) {
            float x = selectedRegion.left + cellW * i;
            canvas.drawLine(x, selectedRegion.top, x, selectedRegion.bottom, gridPaint);
        }
        for (int i = 1; i < GRID_ROWS; i++) {
            float y = selectedRegion.top + cellH * i;
            canvas.drawLine(selectedRegion.left, y, selectedRegion.right, y, gridPaint);
        }

        // Draw corner handles
        drawCornerHandle(canvas, selectedRegion.left, selectedRegion.top);
        drawCornerHandle(canvas, selectedRegion.right, selectedRegion.top);
        drawCornerHandle(canvas, selectedRegion.left, selectedRegion.bottom);
        drawCornerHandle(canvas, selectedRegion.right, selectedRegion.bottom);

        // Draw edge handles (midpoints)
        drawEdgeHandle(canvas, selectedRegion.centerX(), selectedRegion.top);
        drawEdgeHandle(canvas, selectedRegion.centerX(), selectedRegion.bottom);
        drawEdgeHandle(canvas, selectedRegion.left, selectedRegion.centerY());
        drawEdgeHandle(canvas, selectedRegion.right, selectedRegion.centerY());

        // Draw guide text if region is reasonably large
        if (selectedRegion.width() > 200 && selectedRegion.height() > 100) {
            String info = String.format("%d × %d",
                    (int) selectedRegion.width(), (int) selectedRegion.height());
            canvas.drawText(info, selectedRegion.centerX(),
                    selectedRegion.centerY() + 12, guideTextPaint);
        }
    }

    private void drawCornerHandle(Canvas canvas, float x, float y) {
        canvas.drawRoundRect(x - HANDLE_SIZE / 2, y - HANDLE_SIZE / 2,
                x + HANDLE_SIZE / 2, y + HANDLE_SIZE / 2,
                8, 8, cornerPaint);
        // Inner white dot
        Paint inner = new Paint(Paint.ANTI_ALIAS_FLAG);
        inner.setColor(Color.WHITE);
        canvas.drawCircle(x, y, 5, inner);
    }

    private void drawEdgeHandle(Canvas canvas, float x, float y) {
        Paint edgePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        edgePaint.setColor(Color.parseColor("#A5B4FC"));
        edgePaint.setStyle(Paint.Style.FILL);
        canvas.drawCircle(x, y, 10, edgePaint);
        Paint edgeBorder = new Paint(Paint.ANTI_ALIAS_FLAG);
        edgeBorder.setColor(Color.WHITE);
        edgeBorder.setStyle(Paint.Style.STROKE);
        edgeBorder.setStrokeWidth(2f);
        canvas.drawCircle(x, y, 10, edgeBorder);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        float x = event.getX();
        float y = event.getY();

        switch (event.getAction()) {
            case MotionEvent.ACTION_DOWN:
                currentDragMode = getDragMode(x, y);
                lastTouchX = x;
                lastTouchY = y;

                if (currentDragMode == DragMode.NONE && !selectedRegion.isEmpty()) {
                    // If tap outside region - create new region
                    if (!selectedRegion.contains(x, y)) {
                        currentDragMode = DragMode.DRAG_CREATE;
                        selectedRegion.set(x, y, x, y);
                    }
                }
                return true;

            case MotionEvent.ACTION_MOVE:
                float dx = x - lastTouchX;
                float dy = y - lastTouchY;

                switch (currentDragMode) {
                    case MOVE:
                        moveRegion(dx, dy);
                        break;
                    case RESIZE_TOP_LEFT:
                        selectedRegion.left = Math.min(x, selectedRegion.right - MIN_REGION_SIZE);
                        selectedRegion.top = Math.min(y, selectedRegion.bottom - MIN_REGION_SIZE);
                        break;
                    case RESIZE_TOP_RIGHT:
                        selectedRegion.right = Math.max(x, selectedRegion.left + MIN_REGION_SIZE);
                        selectedRegion.top = Math.min(y, selectedRegion.bottom - MIN_REGION_SIZE);
                        break;
                    case RESIZE_BOTTOM_LEFT:
                        selectedRegion.left = Math.min(x, selectedRegion.right - MIN_REGION_SIZE);
                        selectedRegion.bottom = Math.max(y, selectedRegion.top + MIN_REGION_SIZE);
                        break;
                    case RESIZE_BOTTOM_RIGHT:
                        selectedRegion.right = Math.max(x, selectedRegion.left + MIN_REGION_SIZE);
                        selectedRegion.bottom = Math.max(y, selectedRegion.top + MIN_REGION_SIZE);
                        break;
                    case RESIZE_TOP:
                        selectedRegion.top = Math.min(y, selectedRegion.bottom - MIN_REGION_SIZE);
                        break;
                    case RESIZE_BOTTOM:
                        selectedRegion.bottom = Math.max(y, selectedRegion.top + MIN_REGION_SIZE);
                        break;
                    case RESIZE_LEFT:
                        selectedRegion.left = Math.min(x, selectedRegion.right - MIN_REGION_SIZE);
                        break;
                    case RESIZE_RIGHT:
                        selectedRegion.right = Math.max(x, selectedRegion.left + MIN_REGION_SIZE);
                        break;
                    case DRAG_CREATE:
                        selectedRegion.right = x;
                        selectedRegion.bottom = y;
                        // Normalize in case of reverse drag
                        selectedRegion.sort();
                        break;
                }

                // Clamp to view bounds
                clampRegion();
                sanitizeRegion();

                lastTouchX = x;
                lastTouchY = y;
                invalidate();

                if (listener != null) {
                    listener.onRegionChanged(new RectF(selectedRegion));
                }
                return true;

            case MotionEvent.ACTION_UP:
                if (currentDragMode == DragMode.DRAG_CREATE) {
                    // If too small, reset to default
                    if (selectedRegion.width() < MIN_REGION_SIZE ||
                            selectedRegion.height() < MIN_REGION_SIZE) {
                        resetToDefaultRegion();
                    }
                }
                currentDragMode = DragMode.NONE;
                invalidate();
                return true;
        }

        return super.onTouchEvent(event);
    }

    private DragMode getDragMode(float x, float y) {
        float half = HANDLE_SIZE / 2 + 8; // touch padding

        // Corners
        if (isNear(x, y, selectedRegion.left, selectedRegion.top, half))
            return DragMode.RESIZE_TOP_LEFT;
        if (isNear(x, y, selectedRegion.right, selectedRegion.top, half))
            return DragMode.RESIZE_TOP_RIGHT;
        if (isNear(x, y, selectedRegion.left, selectedRegion.bottom, half))
            return DragMode.RESIZE_BOTTOM_LEFT;
        if (isNear(x, y, selectedRegion.right, selectedRegion.bottom, half))
            return DragMode.RESIZE_BOTTOM_RIGHT;

        // Edges
        if (isNear(x, y, selectedRegion.centerX(), selectedRegion.top, half))
            return DragMode.RESIZE_TOP;
        if (isNear(x, y, selectedRegion.centerX(), selectedRegion.bottom, half))
            return DragMode.RESIZE_BOTTOM;
        if (isNear(x, y, selectedRegion.left, selectedRegion.centerY(), half))
            return DragMode.RESIZE_LEFT;
        if (isNear(x, y, selectedRegion.right, selectedRegion.centerY(), half))
            return DragMode.RESIZE_RIGHT;

        // Inside
        if (selectedRegion.contains(x, y))
            return DragMode.MOVE;

        return DragMode.NONE;
    }

    private boolean isNear(float x1, float y1, float x2, float y2, float threshold) {
        return Math.abs(x1 - x2) <= threshold && Math.abs(y1 - y2) <= threshold;
    }

    private void moveRegion(float dx, float dy) {
        float newLeft = selectedRegion.left + dx;
        float newTop = selectedRegion.top + dy;
        float newRight = selectedRegion.right + dx;
        float newBottom = selectedRegion.bottom + dy;

        // Prevent moving outside view bounds
        if (newLeft >= 0 && newRight <= getWidth()) {
            selectedRegion.left = newLeft;
            selectedRegion.right = newRight;
        }
        if (newTop >= 0 && newBottom <= getHeight()) {
            selectedRegion.top = newTop;
            selectedRegion.bottom = newBottom;
        }
    }

    private void clampRegion() {
        selectedRegion.left = Math.max(0, selectedRegion.left);
        selectedRegion.top = Math.max(0, selectedRegion.top);
        selectedRegion.right = Math.min(getWidth(), selectedRegion.right);
        selectedRegion.bottom = Math.min(getHeight(), selectedRegion.bottom);
    }

    private void sanitizeRegion() {
        if (selectedRegion.right < selectedRegion.left + MIN_REGION_SIZE) {
            selectedRegion.right = selectedRegion.left + MIN_REGION_SIZE;
        }
        if (selectedRegion.bottom < selectedRegion.top + MIN_REGION_SIZE) {
            selectedRegion.bottom = selectedRegion.top + MIN_REGION_SIZE;
        }
    }

    private void resetToDefaultRegion() {
        float margin = 0.2f;
        selectedRegion.set(
                getWidth() * margin,
                getHeight() * margin,
                getWidth() * (1 - margin),
                getHeight() * (1 - margin)
        );
        invalidate();
        if (listener != null) {
            listener.onRegionChanged(new RectF(selectedRegion));
        }
    }

    /**
     * Returns the normalized region coordinates (0-1 range) for use with virtual display dimensions
     */
    public RectF getNormalizedRegion() {
        if (getWidth() == 0 || getHeight() == 0) return new RectF(0, 0, 1, 1);
        return new RectF(
                selectedRegion.left / getWidth(),
                selectedRegion.top / getHeight(),
                selectedRegion.right / getWidth(),
                selectedRegion.bottom / getHeight()
        );
    }
}
