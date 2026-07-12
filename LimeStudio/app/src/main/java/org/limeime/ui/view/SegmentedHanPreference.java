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
 *
 */

package org.limeime.ui.view;

import android.content.Context;
import android.util.AttributeSet;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;

import androidx.annotation.NonNull;
import androidx.preference.Preference;
import androidx.preference.PreferenceViewHolder;

import com.google.android.material.button.MaterialButtonToggleGroup;

import org.limeime.R;

/**
 * 簡繁轉換 inline segmented control. Renders the existing {@code han_convert_option}
 * preference as an M3 {@link MaterialButtonToggleGroup} (無 / 繁轉簡 / 簡轉繁) instead
 * of a chooser dialog.
 *
 * <p><b>Behaviour parity:</b> the value is persisted as the SAME String ("0"/"1"/"2")
 * to the SAME key as the former {@code ListPreference}, via {@link #persistString}.
 * This keeps {@code SharedPreferences.OnSharedPreferenceChangeListener}
 * (LIMEPreference) and every reader ({@code getString(han_convert_option)}) behaving
 * exactly as before — only the on-screen presentation changes.
 */
public class SegmentedHanPreference extends Preference {

    private String currentValue = "0";

    public SegmentedHanPreference(Context context, AttributeSet attrs) {
        super(context, attrs);
        setLayoutResource(R.layout.preference_han_segmented);
    }

    @Override
    protected Object onGetDefaultValue(@NonNull android.content.res.TypedArray a, int index) {
        return a.getString(index);
    }

    @Override
    protected void onSetInitialValue(Object defaultValue) {
        String fallback = defaultValue instanceof String ? (String) defaultValue : "0";
        currentValue = getPersistedString(fallback);
    }

    @Override
    public void onBindViewHolder(@NonNull PreferenceViewHolder holder) {
        super.onBindViewHolder(holder);
        // The whole row is the control; tapping the background does nothing.
        holder.itemView.setClickable(false);

        MaterialButtonToggleGroup group =
                (MaterialButtonToggleGroup) holder.findViewById(R.id.han_toggle_group);
        if (group == null) return;

        group.clearOnButtonCheckedListeners();
        group.check(buttonIdFor(currentValue));
        group.addOnButtonCheckedListener((g, checkedId, isChecked) -> {
            if (!isChecked) return;
            String newValue = valueForButton(checkedId);
            if (newValue.equals(currentValue)) return;
            if (callChangeListener(newValue)) {
                currentValue = newValue;
                persistString(newValue);   // same String key/type as the old ListPreference
            } else {
                // Reject — revert the selection.
                g.check(buttonIdFor(currentValue));
            }
        });
        stackIfClipped(group);
    }

    /**
     * At very large system font / display sizes three side-by-side segments can't
     * fit multi-glyph Chinese labels, so the text ellipsizes. After layout, if any
     * child button's label is actually ellipsized, flip the whole toggle group to a
     * vertical stack (each button full width) so every label shows in full. Works
     * for any label/scale because it reacts to measured ellipsis, not a guessed
     * threshold. Shared by the 喜好設定 page and the keyboard long-press menu.
     */
    public static void stackIfClipped(@NonNull MaterialButtonToggleGroup group) {
        group.post(() -> {
            boolean clipped = false;
            for (int i = 0; i < group.getChildCount(); i++) {
                View child = group.getChildAt(i);
                if (child instanceof Button) {
                    android.text.Layout layout = ((Button) child).getLayout();
                    if (layout != null) {
                        int lines = layout.getLineCount();
                        if (lines > 0 && layout.getEllipsisCount(lines - 1) > 0) {
                            clipped = true;
                            break;
                        }
                    }
                }
            }
            if (!clipped || group.getOrientation() == LinearLayout.VERTICAL) return;
            group.setOrientation(LinearLayout.VERTICAL);
            for (int i = 0; i < group.getChildCount(); i++) {
                View child = group.getChildAt(i);
                LinearLayout.LayoutParams lp =
                        (LinearLayout.LayoutParams) child.getLayoutParams();
                lp.width = LinearLayout.LayoutParams.MATCH_PARENT;
                lp.height = LinearLayout.LayoutParams.WRAP_CONTENT;
                lp.weight = 0;
                child.setLayoutParams(lp);
            }
        });
    }

    private int buttonIdFor(String value) {
        switch (value == null ? "0" : value) {
            case "1": return R.id.han_opt_t2s;
            case "2": return R.id.han_opt_s2t;
            default:  return R.id.han_opt_none;
        }
    }

    private String valueForButton(int id) {
        if (id == R.id.han_opt_t2s) return "1";
        if (id == R.id.han_opt_s2t) return "2";
        return "0";
    }
}
