/* Copyright 2019 Braden Farmer
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

package com.farmerbb.taskbar.ui;

import android.graphics.PixelFormat;
import android.view.WindowManager;

import com.farmerbb.taskbar.util.U;

import java.lang.reflect.Field;

public class ViewParams {
    public int width;
    public int height;
    public int gravity;
    public int flags;
    public int bottomMargin;
    public int offsetX;
    public int offsetY;

    public ViewParams(int width, int height, int gravity, int flags, int bottomMargin) {
        this(width, height, gravity, flags, bottomMargin, 0, 0);
    }

    public ViewParams(int width, int height, int gravity, int flags,
                       int bottomMargin, int offsetX, int offsetY) {
        this.width = width;
        this.height = height;
        this.gravity = gravity;
        this.flags = flags;
        this.bottomMargin = bottomMargin;
        this.offsetX = offsetX;
        this.offsetY = offsetY;
    }

    public WindowManager.LayoutParams toWindowManagerParams() {
        final WindowManager.LayoutParams wmParams = new WindowManager.LayoutParams(
                width,
                height,
                U.getOverlayType(),
                flags,
                PixelFormat.TRANSLUCENT
        );

        if(gravity > -1)
            wmParams.gravity = gravity;

        if(bottomMargin > -1)
            wmParams.y = bottomMargin;

        // Apply offset
        if(offsetX != 0)
            wmParams.x = offsetX;

        if(offsetY != 0)
            wmParams.y = (bottomMargin > -1 ? bottomMargin : 0) + offsetY;

        U.allowReflection();
        try {
            Class<?> layoutParamsClass = Class.forName("android.view.WindowManager$LayoutParams");

            Field privateFlags = layoutParamsClass.getField("privateFlags");
            Field noAnim = layoutParamsClass.getField("PRIVATE_FLAG_NO_MOVE_ANIMATION");

            int privateFlagsValue = privateFlags.getInt(wmParams);
            int noAnimFlag = noAnim.getInt(wmParams);
            privateFlagsValue |= noAnimFlag;

            privateFlags.setInt(wmParams, privateFlagsValue);
        } catch (Exception ignored) {}

        return wmParams;
    }

    public ViewParams updateWidth(int width) {
        return new ViewParams(width, height, gravity, flags, bottomMargin, offsetX, offsetY);
    }

    public ViewParams updateHeight(int height) {
        return new ViewParams(width, height, gravity, flags, bottomMargin, offsetX, offsetY);
    }

    public ViewParams updateBottomMargin(int bottomMargin) {
        return new ViewParams(width, height, gravity, flags, bottomMargin, offsetX, offsetY);
    }

    public ViewParams updateOffset(int offsetX, int offsetY) {
        return new ViewParams(width, height, gravity, flags, bottomMargin, offsetX, offsetY);
    }
}