/*
 *
 *  *
 *  **    Copyright 2026, The LimeIME Open Source Project
 *  **
 *  **    Project Url: http://github.com/lime-ime/limeime/
 *  **
 *  **    This program is free software: you can redistribute it and/or modify
 *  **    it under the terms of the GNU General Public License as published by
 *  **    the Free Software Foundation, either version 3 of the License, or
 *  **    (at your option) any later version.
 *  *
 *  **    This program is distributed in the hope that it will be useful,
 *  **    but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  **    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  **    GNU General Public License for more details.
 *  *
 *  **    You should have received a copy of the GNU General Public License
 *  **    along with this program.  If not, see <http://www.gnu.org/licenses/>.
 *  *
 *
 */

package org.limeime.ui.dialog;

import android.app.Dialog;
import android.os.Build;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;

import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;

/**
 * Shared keyboard-inset handling for manage add/edit bottom sheets.
 */
final class ImeAwareBottomSheet {

    private ImeAwareBottomSheet() {
    }

    static void applyInsets(@NonNull View contentView) {
        final int initialLeft = contentView.getPaddingLeft();
        final int initialTop = contentView.getPaddingTop();
        final int initialRight = contentView.getPaddingRight();
        final int initialBottom = contentView.getPaddingBottom();

        ViewCompat.setOnApplyWindowInsetsListener(contentView, (view, insets) -> {
            Insets ime = insets.getInsets(WindowInsetsCompat.Type.ime());
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            int bottomInset = Math.max(ime.bottom, bars.bottom);
            view.setPadding(initialLeft, initialTop, initialRight, initialBottom + bottomInset);
            return insets;
        });
        ViewCompat.requestApplyInsets(contentView);
    }

    static void expandForIme(@NonNull BottomSheetDialogFragment fragment) {
        Dialog dialog = fragment.getDialog();
        if (dialog == null) {
            return;
        }

        Window window = dialog.getWindow();
        if (window != null) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                WindowCompat.setDecorFitsSystemWindows(window, false);
            } else {
                setLegacyAdjustResize(window);
            }
        }

        if (!(dialog instanceof BottomSheetDialog)) {
            return;
        }

        FrameLayout bottomSheet = dialog.findViewById(com.google.android.material.R.id.design_bottom_sheet);
        if (bottomSheet == null) {
            return;
        }

        ViewGroup.LayoutParams params = bottomSheet.getLayoutParams();
        params.height = ViewGroup.LayoutParams.MATCH_PARENT;
        bottomSheet.setLayoutParams(params);

        BottomSheetBehavior<FrameLayout> behavior = ((BottomSheetDialog) dialog).getBehavior();
        behavior.setFitToContents(false);
        behavior.setExpandedOffset(0);
        behavior.setSkipCollapsed(true);
        behavior.setState(BottomSheetBehavior.STATE_EXPANDED);
    }

    @SuppressWarnings("deprecation")
    private static void setLegacyAdjustResize(@NonNull Window window) {
        window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
    }
}
