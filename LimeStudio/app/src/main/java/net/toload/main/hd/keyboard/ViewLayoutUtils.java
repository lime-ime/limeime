/*
 *
 *  **    Copyright 2015, The LimeIME Open Source Project
 *  **
 *  **    Project Url: http://github.com/jrywu/limeime/
 *  **                 http://android.toload.net/
 *  **
 *  **    This program is free software: you can redistribute it and/or modify
 *  **    it under the terms of the GNU General Public License as published by
 *  **    the Free Software Foundation, either version 3 of the License, or
 *  **    (at your option) any later version.
 *
 *  **    This program is distributed in the hope that it will be useful,
 *  **    but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  **    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  **    GNU General Public License for more details.
 *
 *  **    You should have received a copy of the GNU General Public License
 *  **    along with this program.  If not, see <http://www.gnu.org/licenses/>.
 *
 */

package net.toload.main.hd.keyboard;/*
 **    Copyright 2015, The LimeIME Open Source Project
 **
 **    Project Url: http://github.com/jrywu/limeime/
 **                 http://android.toload.net/
 **
 **    This program is free software: you can redistribute it and/or modify
 **    it under the terms of the GNU General Public License as published by
 **    the Free Software Foundation, either version 3 of the License, or
 **    (at your option) any later version.

 **    This program is distributed in the hope that it will be useful,
 **    but WITHOUT ANY WARRANTY; without even the implied warranty of
 **    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 **    GNU General Public License for more details.

 **    You should have received a copy of the GNU General Public License
 **    along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.RelativeLayout;

public class ViewLayoutUtils {
    private ViewLayoutUtils() {
        // This utility class is not publicly instantiable.
    }
    public static ViewGroup.MarginLayoutParams newLayoutParam(ViewGroup placer, int width, int height) {
        if (placer instanceof FrameLayout) {
            return new FrameLayout.LayoutParams(width, height);
        } else if (placer instanceof RelativeLayout) {
            return new RelativeLayout.LayoutParams(width, height);
        } else if (placer == null) {
            throw new NullPointerException("placer is null");
        } else {
            throw new IllegalArgumentException("placer is neither FrameLayout nor RelativeLayout: "
                    + placer.getClass().getName());
        }
    }
    public static void placeViewAt(View view, int x, int y, int w, int h) {
        final ViewGroup.LayoutParams lp = view.getLayoutParams();
        if (lp instanceof ViewGroup.MarginLayoutParams) {
            final ViewGroup.MarginLayoutParams marginLayoutParams = (ViewGroup.MarginLayoutParams)lp;
            marginLayoutParams.width = w;
            marginLayoutParams.height = h;
            marginLayoutParams.setMargins(x, y, 0, 0);
        }
    }
}
