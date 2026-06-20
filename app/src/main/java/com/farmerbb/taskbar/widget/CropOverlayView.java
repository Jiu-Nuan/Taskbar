/* Copyright 2016 Braden Farmer
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.farmerbb.taskbar.widget;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.View;

public class CropOverlayView extends View {

    private final Paint dimPaint;
    private final Paint borderPaint;
    private RectF cropRect = new RectF();
    private float minSize;

    public CropOverlayView(Context context) {
        this(context, null);
    }

    public CropOverlayView(Context context, AttributeSet attrs) {
        super(context, attrs);

        dimPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        dimPaint.setColor(Color.argb(120, 0, 0, 0));
        dimPaint.setStyle(Paint.Style.FILL);

        borderPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        borderPaint.setColor(Color.WHITE);
        borderPaint.setStyle(Paint.Style.STROKE);
        borderPaint.setStrokeWidth(4f);
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        minSize = Math.min(w, h) * 0.2f;
        float size = Math.min(w, h) * 0.6f;
        float left = (w - size) / 2f;
        float top = (h - size) / 2f;
        cropRect.set(left, top, left + size, top + size);
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        int w = getWidth();
        int h = getHeight();

        // Draw dim overlay with hole
        canvas.drawRect(0, 0, w, cropRect.top, dimPaint);
        canvas.drawRect(0, cropRect.bottom, w, h, dimPaint);
        canvas.drawRect(0, cropRect.top, cropRect.left, cropRect.bottom, dimPaint);
        canvas.drawRect(cropRect.right, cropRect.top, w, cropRect.bottom, dimPaint);

        // Draw crop border
        canvas.drawRect(cropRect, borderPaint);

        // Draw corner indicators
        Paint cornerPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        cornerPaint.setColor(Color.WHITE);
        cornerPaint.setStyle(Paint.Style.STROKE);
        cornerPaint.setStrokeWidth(8f);

        float cornerLen = 40f;
        // Top-left
        canvas.drawLine(cropRect.left, cropRect.top, cropRect.left + cornerLen, cropRect.top, cornerPaint);
        canvas.drawLine(cropRect.left, cropRect.top, cropRect.left, cropRect.top + cornerLen, cornerPaint);
        // Top-right
        canvas.drawLine(cropRect.right - cornerLen, cropRect.top, cropRect.right, cropRect.top, cornerPaint);
        canvas.drawLine(cropRect.right, cropRect.top, cropRect.right, cropRect.top + cornerLen, cornerPaint);
        // Bottom-left
        canvas.drawLine(cropRect.left, cropRect.bottom - cornerLen, cropRect.left, cropRect.bottom, cornerPaint);
        canvas.drawLine(cropRect.left, cropRect.bottom, cropRect.left + cornerLen, cropRect.bottom, cornerPaint);
        // Bottom-right
        canvas.drawLine(cropRect.right - cornerLen, cropRect.bottom, cropRect.right, cropRect.bottom, cornerPaint);
        canvas.drawLine(cropRect.right, cropRect.bottom - cornerLen, cropRect.right, cropRect.bottom, cornerPaint);
    }

    public float getMinSize() { return minSize; }
    public RectF getCropRect() { return cropRect; }

    public void setCropRect(float left, float top, float right, float bottom) {
        cropRect.set(left, top, right, bottom);
        invalidate();
    }
}
