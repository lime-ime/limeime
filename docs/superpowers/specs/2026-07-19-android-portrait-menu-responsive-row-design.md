# Android Portrait Menu Responsive Row

## Goal

Show the Android keyboard side menu's `直向鍵盤模式` title and segmented choices on one line whenever they fit, matching the iPhone behavior. Adapt only when the available width cannot show them without clipping.

## Design

Keep the existing title, icon, buttons, selection behavior, and preference persistence. Change only the portrait-mode block layout:

- Keep the icon/title and `MaterialButtonToggleGroup` in one horizontal row while the choices fit.
- If they do not fit, move the choices below the title and stretch them across the full content width without the old icon indentation.
- If the choices still do not fit at full width, stack the buttons vertically.
- Reuse and minimally extend the existing `SegmentedHanPreference` responsive-layout helper instead of adding a new component or dependency.

All other segmented controls in the keyboard menu use the full content width when shown below their titles, in portrait and landscape.

The numpad-specific hidden `分離` option remains supported; fit is evaluated from the buttons that are visible for the current keyboard.

## Verification

Add focused Android checks for the wide one-line row, the full-width fallback, and every similar segmented control's zero-indent layout. Run them on Samsung and Pixel, then run the relevant Android unit tests and an Android build.
