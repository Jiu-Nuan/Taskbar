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

package com.farmerbb.taskbar.activity;

import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.graphics.RectF;
import android.net.Uri;
import android.os.Bundle;
import android.view.MotionEvent;
import android.view.View;
import android.widget.ImageView;

import androidx.appcompat.app.AppCompatActivity;

import com.farmerbb.taskbar.R;
import com.farmerbb.taskbar.widget.CropOverlayView;

import java.io.InputStream;

public class CropImageActivity extends AppCompatActivity {

    private ImageView imageView;
    private CropOverlayView cropOverlay;
    private Uri imageUri;
    private float lastX, lastY;
    private int activeHandle = -1; // 0=TL,1=TR,2=BL,3=BR,-1=moving
    private static final float HANDLE_RADIUS = 60f;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.tb_crop_image);

        imageView = findViewById(R.id.crop_image);
        cropOverlay = findViewById(R.id.crop_overlay);

        imageUri = getIntent().getData();
        if(imageUri == null) {
            finish();
            return;
        }

        try {
            InputStream is = getContentResolver().openInputStream(imageUri);
            BitmapFactory.Options opts = new BitmapFactory.Options();
            opts.inSampleSize = calculateInSampleSize(is);
            is.close();
            is = getContentResolver().openInputStream(imageUri);
            Bitmap bitmap = BitmapFactory.decodeStream(is);
            is.close();
            imageView.setImageBitmap(bitmap);
        } catch (Exception e) {
            finish();
            return;
        }

        cropOverlay.setOnTouchListener(new CropTouchListener());

        findViewById(R.id.btn_cancel).setOnClickListener(v -> {
            setResult(RESULT_CANCELED);
            finish();
        });

        findViewById(R.id.btn_crop).setOnClickListener(v -> {
            RectF cropRect = cropOverlay.getCropRect();
            imageView.setDrawingCacheEnabled(true);
            Bitmap full = imageView.getDrawingCache();
            if(full == null) {
                setResult(RESULT_CANCELED);
                finish();
                return;
            }

            // Map crop rect from overlay coordinates to image bitmap coordinates
            int vw = imageView.getWidth();
            int vh = imageView.getHeight();
            int bw = full.getWidth();
            int bh = full.getHeight();

            float scale;
            float offsetX = 0, offsetY = 0;
            if((float) bw / bh > (float) vw / vh) {
                scale = (float) vh / bh;
                offsetX = (vw - bw * scale) / 2f;
            } else {
                scale = (float) vw / bw;
                offsetY = (vh - bh * scale) / 2f;
            }

            int cropX = Math.round((cropRect.left - offsetX) / scale);
            int cropY = Math.round((cropRect.top - offsetY) / scale);
            int cropW = Math.round(cropRect.width() / scale);
            cropX = Math.max(0, Math.min(cropX, bw - 1));
            cropY = Math.max(0, Math.min(cropY, bh - 1));
            cropW = Math.min(cropW, bw - cropX);
            int cropH = cropW; // force square

            Bitmap cropped = Bitmap.createBitmap(full, cropX, cropY, cropW, cropH);

            Intent result = new Intent();
            result.putExtra("cropped_bitmap", cropped);
            setResult(RESULT_OK, result);
            finish();
        });
    }

    private int calculateInSampleSize(InputStream is) {
        BitmapFactory.Options opts = new BitmapFactory.Options();
        opts.inJustDecodeBounds = true;
        BitmapFactory.decodeStream(is, null, opts);
        int width = opts.outWidth;
        int height = opts.outHeight;
        int inSampleSize = 1;
        int maxDim = Math.max(width, height);
        if(maxDim > 2048) {
            inSampleSize = maxDim / 2048;
        }
        return inSampleSize;
    }

    private class CropTouchListener implements View.OnTouchListener {
        @Override
        public boolean onTouch(View v, MotionEvent event) {
            float x = event.getX();
            float y = event.getY();
            RectF rect = cropOverlay.getCropRect();
            float minSize = cropOverlay.getMinSize();

            switch(event.getAction()) {
                case MotionEvent.ACTION_DOWN:
                    activeHandle = getHandle(rect, x, y);
                    lastX = x;
                    lastY = y;
                    return true;

                case MotionEvent.ACTION_MOVE:
                    float dx = x - lastX;
                    float dy = y - lastY;
                    float left = rect.left, top = rect.top, right = rect.right, bottom = rect.bottom;
                    int vw = v.getWidth(), vh = v.getHeight();

                    switch(activeHandle) {
                        case 0: // TL
                            left = clamp(left + dx, 0, right - minSize);
                            top = clamp(top + dy, 0, bottom - minSize);
                            break;
                        case 1: // TR
                            right = clamp(right + dx, left + minSize, vw);
                            top = clamp(top + dy, 0, bottom - minSize);
                            break;
                        case 2: // BL
                            left = clamp(left + dx, 0, right - minSize);
                            bottom = clamp(bottom + dy, top + minSize, vh);
                            break;
                        case 3: // BR
                            right = clamp(right + dx, left + minSize, vw);
                            bottom = clamp(bottom + dy, top + minSize, vh);
                            break;
                        case -1: // moving
                            float w = rect.width();
                            float h = rect.height();
                            float newLeft = clamp(left + dx, 0, vw - w);
                            float newTop = clamp(top + dy, 0, vh - h);
                            left = newLeft;
                            top = newTop;
                            right = left + w;
                            bottom = top + h;
                            break;
                    }
                    cropOverlay.setCropRect(left, top, right, bottom);
                    lastX = x;
                    lastY = y;
                    return true;

                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    activeHandle = -1;
                    return true;
            }
            return false;
        }
    }

    private int getHandle(RectF rect, float x, float y) {
        if(dist(x, y, rect.left, rect.top) < HANDLE_RADIUS) return 0;
        if(dist(x, y, rect.right, rect.top) < HANDLE_RADIUS) return 1;
        if(dist(x, y, rect.left, rect.bottom) < HANDLE_RADIUS) return 2;
        if(dist(x, y, rect.right, rect.bottom) < HANDLE_RADIUS) return 3;
        if(rect.contains(x, y)) return -1;
        return -2; // outside
    }

    private static float dist(float x1, float y1, float x2, float y2) {
        return (float) Math.sqrt((x1 - x2) * (x1 - x2) + (y1 - y2) * (y1 - y2));
    }

    private static float clamp(float val, float min, float max) {
        return Math.max(min, Math.min(max, val));
    }
}
