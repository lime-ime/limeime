package org.limeime.keyboard;

public final class KeyLabelFit {
    private KeyLabelFit() {}

    public static float scale(float availableWidth, float availableHeight,
                              float contentWidth, float contentHeight, float minimumScale) {
        float widthScale = contentWidth > 0 ? availableWidth / contentWidth : 1f;
        float heightScale = contentHeight > 0 ? availableHeight / contentHeight : 1f;
        return Math.max(minimumScale, Math.min(1f, Math.min(widthScale, heightScale)));
    }

    public static boolean useVerticalLabels(boolean portrait, int keyWidth, int keyHeight,
                                            int subLabelLength, boolean hasSecondSubLabel) {
        return portrait || keyHeight > keyWidth || subLabelLength > 2 || hasSecondSubLabel;
    }
}
