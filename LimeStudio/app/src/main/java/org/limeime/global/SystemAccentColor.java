package org.limeime.global;

import android.content.Context;
import android.graphics.Color;
import android.provider.Settings;
import android.text.TextUtils;

import com.google.android.material.color.DynamicColorsOptions;

/**
 * Resolves Android's user-selected Material You palette for LIME's follow-system theme.
 */
public final class SystemAccentColor {
    private static final String THEME_CUSTOMIZATION = "theme_customization_overlay_packages";
    private static final String SYSTEM_PALETTE = "system_palette";
    private static final String ACCENT_COLOR = "accent_color";

    private SystemAccentColor() {
    }

    public static DynamicColorsOptions dynamicColorOptions(Context context) {
        DynamicColorsOptions.Builder builder = new DynamicColorsOptions.Builder();
        int seedColor = resolveSeedColor(context, 0);
        if (isUsableColor(seedColor)) {
            builder.setContentBasedSource(seedColor);
        }
        return builder.build();
    }

    public static int resolveSeedColor(Context context, int fallbackColor) {
        if (context == null) return fallbackColor;
        try {
            String value = Settings.Secure.getString(
                    context.getContentResolver(),
                    THEME_CUSTOMIZATION);
            int parsed = parseThemeCustomizationColor(value);
            return isUsableColor(parsed) ? parsed : fallbackColor;
        } catch (RuntimeException e) {
            return fallbackColor;
        }
    }

    static int parseThemeCustomizationColor(String value) {
        if (TextUtils.isEmpty(value)) return 0;

        int parsed = parseColorAfterKey(value, SYSTEM_PALETTE);
        if (isUsableColor(parsed)) return parsed;

        return parseColorAfterKey(value, ACCENT_COLOR);
    }

    private static int parseColorAfterKey(String value, String key) {
        int keyIndex = value.indexOf(key);
        if (keyIndex < 0) return 0;

        int index = keyIndex + key.length();
        int length = value.length();
        while (index < length) {
            char c = value.charAt(index);
            if (c == ':' || c == '=' || c == '"' || c == '\'' || Character.isWhitespace(c)) {
                index++;
                continue;
            }
            break;
        }

        int start = index;
        while (index < length) {
            char c = value.charAt(index);
            if (c == '#' || isHexDigit(c)) {
                index++;
                continue;
            }
            break;
        }
        if (index <= start) return 0;
        return parseColorToken(value.substring(start, index));
    }

    private static int parseColorToken(String token) {
        if (TextUtils.isEmpty(token)) return 0;

        String normalized = token.trim();
        if (normalized.startsWith("#")) {
            normalized = normalized.substring(1);
        }
        if (normalized.length() == 6) {
            normalized = "FF" + normalized;
        }
        if (normalized.length() != 8) return 0;

        try {
            long value = Long.parseLong(normalized, 16);
            return (int) value;
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private static boolean isHexDigit(char c) {
        return (c >= '0' && c <= '9')
                || (c >= 'a' && c <= 'f')
                || (c >= 'A' && c <= 'F');
    }

    private static boolean isUsableColor(int color) {
        return Color.alpha(color) != 0;
    }
}
