# Android Portrait Menu Responsive Row

## Goal

Show the Android keyboard side menu's `直向鍵盤模式` title and segmented choices on one line whenever they fit, matching the iPhone behavior. Stack them only when the available width cannot show the controls without clipping.

## Design

Keep the existing title, icon, buttons, selection behavior, and preference persistence. Change only the portrait-mode block layout:

- The icon/title area and `MaterialButtonToggleGroup` share one horizontal container by default.
- After layout, measure whether the complete row fits without clipped button labels.
- If it does not fit, switch the container to vertical orientation and give the toggle group the existing indented, full-width stacked placement.
- Reuse and minimally extend the existing `SegmentedHanPreference` responsive-layout helper instead of adding a new component or dependency.

The numpad-specific hidden `分離` option remains supported; fit is evaluated from the buttons that are visible for the current keyboard.

## Verification

Add one focused Android layout/helper check that fails while the portrait block is permanently vertical and passes when it defaults to horizontal with a responsive fallback. Run the focused test, the relevant Android unit tests, and an Android build. Visually verify a normal phone width stays on one line and a constrained width or enlarged font stacks without clipped labels.
