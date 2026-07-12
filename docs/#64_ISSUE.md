# Issue #64: Main tab screens lack bottom scroll affordance/inset

## Problem statement

Issue #64 reports that, in LIME 6.1.1, some text or function settings in the main settings UI appear to be pushed out of the visible area when the phone uses larger display/font settings.

The issue scope is broader than the original two input-method management screenshots. It affects the four phone bottom-tab pages in the main settings activity except for the deeper IM/associated-phrase editor pages, which already have explicit bottom-navigation avoidance for their pagination bars:

- `設定`
- `輸入法`
- `喜好設定`
- `資料庫`

A follow-up comment from `jrywu` confirmed that the affected screens are scrollable, but no scrollbar is shown. This changes part of the issue from a content reachability problem to a scroll affordance and accessibility problem: users can reach hidden content only if they discover that the page can scroll. The screenshot also shows a separate bottom inset problem, where content can be visually clipped by the fixed bottom navigation area.

The provided screenshot also shows the bottom visible setting row clipped by the bottom navigation bar area. The row title `啟動實體鍵盤選取..` and the next line below it are cut off at the bottom edge, with the checkbox partially visible behind or immediately above the bottom navigation. This makes the screen look like content is being obscured, not merely like a long settings page without a scrollbar.

## Current observations

Relevant UI files:

- `LimeStudio/app/src/main/res/layout/activity_main.xml`
- `LimeStudio/app/src/main/res/menu/main_nav.xml`
- `LimeStudio/app/src/main/java/org/limeime/ui/LIMESettings.java`
- `LimeStudio/app/src/main/res/layout/fragment_setup.xml`
- `LimeStudio/app/src/main/res/layout/fragment_im_detail.xml`
- `LimeStudio/app/src/main/res/layout/fragment_im_list.xml`
- `LimeStudio/app/src/main/res/layout/fragment_lime_preference_host.xml`
- `LimeStudio/app/src/main/res/layout/fragment_db_manager.xml`
- `LimeStudio/app/src/main/java/org/limeime/ui/view/SetupFragment.java`
- `LimeStudio/app/src/main/java/org/limeime/ui/view/LimePreferenceFragment.java`
- `LimeStudio/app/src/main/java/org/limeime/ui/view/DbManagerFragment.java`
- `LimeStudio/app/src/main/java/org/limeime/ui/view/ImDetailFragment.java`
- `LimeStudio/app/src/main/java/org/limeime/ui/view/TwoPaneHostFragment.java`
- `LimeStudio/app/src/main/java/org/limeime/ui/view/ManageImFragment.java`
- `LimeStudio/app/src/main/java/org/limeime/ui/view/ManageRelatedFragment.java`

`activity_main.xml` uses a `CoordinatorLayout` with `main_fragment_container` set to `match_parent` and a phone-only `BottomNavigationView` anchored at the bottom. This means top-level fragments can extend behind the bottom navigation unless their own scroll containers provide sufficient bottom inset/padding.

`main_nav.xml` defines four phone tabs: `nav_setup` (`設定`), `nav_im` (`輸入法`), `nav_prefs` (`喜好設定`), and `nav_db` (`資料庫`). `LIMESettings.java` maps these tabs to `SetupFragment`, `TwoPaneHostFragment`, `LimePreferenceFragment`, and `DbManagerFragment`.

`fragment_setup.xml` and `fragment_db_manager.xml` both use full-height `NestedScrollView` containers with only fixed `24dp` bottom padding. That may not be enough when the bottom navigation is taller due to system gesture/navigation insets or font/display scaling.

`fragment_im_detail.xml` uses a `NestedScrollView` for the detail screen content. The scroll container is laid out correctly with `layout_height="0dp"` and `layout_weight="1"`, so overflowing content can scroll below the toolbar. However, the scroll view has no explicit id and no explicit scrollbar settings such as `android:scrollbars="vertical"`, `android:fadeScrollbars="false"`, or `android:scrollbarStyle`.

`ImDetailFragment.java` inflates the layout but does not configure the `NestedScrollView` programmatically. Because the scroll view has no id, the fragment currently cannot enable or tune scrollbar behavior from code without a layout change.

`fragment_im_list.xml` contains the input-method list in a `RecyclerView`. If the screenshots include the list screen as well as the detail screen, that list also lacks explicit vertical scrollbar configuration.

`fragment_lime_preference_host.xml` hosts `LIMEPreference.PrefsFragment` under a toolbar. The visible screenshot appears to be from this `喜好設定` tab: content continues underneath the fixed bottom navigation region, and the user has no visible scroll indicator to suggest that more content can be reached.

`ManageImFragment.java` and `ManageRelatedFragment.java` already contain code that posts against `main_bottom_nav` and pushes their `pagination_bar` above the activity bottom navigation. This supports treating the IM/associated-phrase editor pages as the exception, not the primary scope of this issue.

## Likely root cause

The top-level phone tabs are technically scrollable, but their scroll indicators rely on Android default scrollbar behavior. On modern Android themes/devices, default scrollbars may fade quickly, be visually subtle, or not appear in a way users notice. With large font or display scaling, the content overflow becomes more common, making the absence of a visible scroll affordance look like a layout clipping bug.

The shared structural issue is likely that the activity-level fragment container fills the full screen while the bottom navigation overlays the bottom of the content area. Each affected tab needs either a common activity-level inset solution or per-fragment bottom padding/inset handling so the final rows can scroll fully above the bottom navigation bar instead of being clipped behind it.

## Expected behavior

- If a page's content fits within one viewport, do not show a scrollbar.
- If a page's content is longer than one viewport, show a visible vertical scrollbar so users can immediately tell that more content is available.
- The scrollbar should not be used as a substitute for fixing clipping: the final row must still be able to scroll fully above the bottom navigation area.

## Proposed solution

1. Treat all four top-level phone tabs as in scope: `設定`, `輸入法`, `喜好設定`, and `資料庫`.
2. Prefer a shared fix for the activity content area or a reusable helper that applies bottom padding based on `main_bottom_nav` height and system window insets.
3. For affected `NestedScrollView`, `ScrollView`, `RecyclerView`, and preference-list containers, enable enough bottom padding and `clipToPadding="false"` where appropriate so the last row can scroll above the bottom navigation.
4. Enable conditional vertical scrollbar affordance on affected scroll containers:
   - `android:scrollbars="vertical"`
   - keep the scrollbar hidden when the content does not overflow
   - keep the scrollbar visible/persistent when the content is longer than one viewport
   - choose an appropriate `android:scrollbarStyle`, such as `insideInset` or `outsideOverlay`, after checking the visual result
5. Give key scroll containers stable ids if code needs to apply inset/scrollbar behavior programmatically.
6. Keep `fillViewport="true"` where used so short pages still fill the available pane while long pages scroll normally.
7. Keep the existing IM/associated-phrase editor pagination handling intact; those editor screens are not the reported gap.
8. Avoid reducing text size as the primary fix, because the reported scenario is tied to larger user font/display settings and should remain accessible.

### Bottom clipping implementation note

The preferred fix is to apply bottom padding/inset to the actual scrollable content, based on the runtime height of `main_bottom_nav` and any relevant system window inset. The scroll container should also use `clipToPadding="false"` where appropriate so the last item can scroll into the padded area instead of being hidden behind the navigation bar.

For `NestedScrollView`/`ScrollView`, calculate after layout whether the child content height is greater than the viewport height. Enable the vertical scrollbar and disable fading only when the page can actually scroll. For `RecyclerView` or the preference list, use the equivalent post-layout scrollability check, such as whether the list can scroll vertically.

## Follow-up questions

- Does the reporter use Android system font size/display size larger than default?
- Should the fix be centralized at the activity/container level, or applied directly to each tab fragment's scroll container?

## Verification plan

- Test on a device/emulator with default font/display settings and with large/maximum font and display settings.
- Open all four top-level phone tabs: `設定`, `輸入法`, `喜好設定`, and `資料庫`.
- Confirm all content remains reachable by scrolling.
- Confirm the last visible preference row can scroll fully above the bottom navigation bar and is not clipped at the bottom edge.
- Confirm no scrollbar is shown when a page fits within one viewport.
- Confirm a visible vertical scrollbar is shown when a page is longer than one viewport.
- Confirm the IM and associated-phrase editor pages still keep their pagination bars above the bottom navigation.
- Confirm the change does not introduce unwanted permanent scrollbar space or overlap in two-pane/tablet layouts.
