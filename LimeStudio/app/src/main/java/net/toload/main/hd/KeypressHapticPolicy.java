package net.toload.main.hd;

import android.os.Build;

public final class KeypressHapticPolicy {

    private KeypressHapticPolicy() {
    }

    public static boolean shouldUseSystemKeyboardTapHaptic() {
        return shouldUseSystemKeyboardTapHaptic(
                Build.VERSION.SDK_INT,
                Build.MANUFACTURER,
                Build.BRAND);
    }

    public static boolean shouldHideVibrateLevelPreference() {
        return shouldUseSystemKeyboardTapHaptic();
    }

    static boolean shouldUseSystemKeyboardTapHaptic(int sdkInt, String manufacturer, String brand) {
        if (sdkInt < Build.VERSION_CODES.S) {
            return false;
        }
        if (isSamsung(manufacturer, brand)) {
            return false;
        }
        return isGoogleOrPixel(manufacturer, brand);
    }

    static boolean shouldHideVibrateLevelPreference(int sdkInt, String manufacturer, String brand) {
        return shouldUseSystemKeyboardTapHaptic(sdkInt, manufacturer, brand);
    }

    private static boolean isSamsung(String manufacturer, String brand) {
        return equalsIgnoreCase(manufacturer, "samsung") || equalsIgnoreCase(brand, "samsung");
    }

    private static boolean isGoogleOrPixel(String manufacturer, String brand) {
        return equalsIgnoreCase(manufacturer, "google")
                || equalsIgnoreCase(brand, "google")
                || equalsIgnoreCase(brand, "pixel");
    }

    private static boolean equalsIgnoreCase(String value, String expected) {
        return value != null && value.equalsIgnoreCase(expected);
    }
}
