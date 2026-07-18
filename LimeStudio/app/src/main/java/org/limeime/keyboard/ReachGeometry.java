package org.limeime.keyboard;

/**
 * SPLIT_ONE_HAND_KB: pure reach-geometry math shared by the split keyboard,
 * one-handed mode and numpad anchoring (docs/SPLIT_ONE_HAND_KB.md).
 * Physical millimetres are converted with the display's xdpi. Every constant
 * is a calibration knob — tune after on-device trials, do not scatter copies.
 */
public final class ReachGeometry {
    private ReachGeometry() {}

    public static final float SPLIT_HALF_MAX_MM = 66f;
    public static final float SPLIT_KEY_MIN_MM = 9f;
    public static final float SPLIT_KEY_MAX_MM = 13f;
    public static final float SPLIT_ROW_MAX_MM = 12f;
    public static final float ONE_HAND_MAX_W_MM = 60f;
    public static final float ONE_HAND_GATE_MARGIN_MM = 4f;
    public static final float NUMPAD_KEY_MM = 14f;
    public static final float NUMPAD_ANCHOR_MAX_FRACTION = 0.40f;

    public static int mmToPx(float mm, float xdpi) {
        return Math.round(mm * xdpi / 25.4f);
    }

    /**
     * Reach-capped split key width. Never wider than the legacy
     * reserved-columns width, so small screens keep today's behavior exactly.
     */
    public static int splitKeyWidth(int displayWidthPx, int keysInRow, int reservedColumns, float xdpi) {
        int legacy = Math.round((float) displayWidthPx / (keysInRow + reservedColumns));
        if (xdpi <= 0 || keysInRow <= 0) return legacy;
        int columnsInHalf = (keysInRow + 1) / 2;
        int capped = mmToPx(SPLIT_HALF_MAX_MM, xdpi) / columnsInHalf;
        capped = Math.max(mmToPx(SPLIT_KEY_MIN_MM, xdpi),
                 Math.min(capped, mmToPx(SPLIT_KEY_MAX_MM, xdpi)));
        return Math.min(legacy, capped);
    }

    /** Gate: show/apply one-hand mode only when shrinking is meaningful (≈5.5"+). */
    public static boolean oneHandAvailable(int screenWidthPx, float xdpi) {
        return xdpi > 0
                && screenWidthPx > mmToPx(ONE_HAND_MAX_W_MM + ONE_HAND_GATE_MARGIN_MM, xdpi);
    }

    public static int oneHandWidth(int displayWidthPx, float xdpi) {
        return Math.min(displayWidthPx, mmToPx(ONE_HAND_MAX_W_MM, xdpi));
    }

    public static int numpadWidth(int displayWidthPx, int columns, float xdpi) {
        int byKeys = mmToPx(columns * NUMPAD_KEY_MM, xdpi);
        int cap = Math.round(displayWidthPx * NUMPAD_ANCHOR_MAX_FRACTION);
        return Math.min(byKeys, cap);
    }
}
