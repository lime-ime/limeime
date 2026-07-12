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

package net.toload.main.hd.ui.dialog;

import com.google.android.material.textfield.TextInputEditText;

/**
 * Shared score field helpers for manage add/edit sheets.
 */
final class ManageSheetScoreInput {

    private static final int MIN_SCORE = 0;
    private static final int MAX_SCORE = 9999;

    private ManageSheetScoreInput() {
    }

    static void setScore(TextInputEditText field, int score) {
        field.setText(String.valueOf(clamp(score)));
        field.setSelection(field.getText() != null ? field.getText().length() : 0);
    }

    static int readScore(TextInputEditText field, int fallback) {
        String value = field.getText() != null ? field.getText().toString().trim() : "";
        if (value.isEmpty()) {
            setScore(field, fallback);
            return clamp(fallback);
        }
        try {
            int score = clamp(Integer.parseInt(value));
            if (!value.equals(String.valueOf(score))) {
                setScore(field, score);
            }
            return score;
        } catch (NumberFormatException e) {
            setScore(field, fallback);
            return clamp(fallback);
        }
    }

    static int decrement(TextInputEditText field, int fallback) {
        int score = Math.max(MIN_SCORE, readScore(field, fallback) - 1);
        setScore(field, score);
        return score;
    }

    static int increment(TextInputEditText field, int fallback) {
        int score = Math.min(MAX_SCORE, readScore(field, fallback) + 1);
        setScore(field, score);
        return score;
    }

    private static int clamp(int score) {
        return Math.max(MIN_SCORE, Math.min(MAX_SCORE, score));
    }
}
