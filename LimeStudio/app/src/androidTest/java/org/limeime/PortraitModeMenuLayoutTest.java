package org.limeime;

import static org.junit.Assert.assertEquals;

import android.content.Context;
import android.view.ContextThemeWrapper;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.LinearLayout;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import com.google.android.material.button.MaterialButtonToggleGroup;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.limeime.ui.view.SegmentedHanPreference;

@RunWith(AndroidJUnit4.class)
public class PortraitModeMenuLayoutTest {

    @Test
    public void portraitModeChoicesStayOnOneLineWhenWidthIsEnough() {
        Context context = new ContextThemeWrapper(
                InstrumentationRegistry.getInstrumentation().getTargetContext(),
                R.style.LIMESettingsTheme);
        View panel = LayoutInflater.from(context).inflate(R.layout.keyboard_menu_panel, null);
        MaterialButtonToggleGroup group = panel.findViewById(R.id.portrait_mode_toggle_group);
        group.measure(View.MeasureSpec.makeMeasureSpec(800, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));
        group.layout(0, 0, group.getMeasuredWidth(), group.getMeasuredHeight());

        SegmentedHanPreference.stackIfClippedNow(group);

        assertEquals(LinearLayout.HORIZONTAL, group.getOrientation());
    }

    @Test
    public void portraitModeMovesChoicesBelowTitleBeforeStackingButtons() {
        Context context = new ContextThemeWrapper(
                InstrumentationRegistry.getInstrumentation().getTargetContext(),
                R.style.LIMESettingsTheme);
        View panel = LayoutInflater.from(context).inflate(R.layout.keyboard_menu_panel, null);
        LinearLayout row = panel.findViewById(R.id.menu_portrait_mode_block);
        MaterialButtonToggleGroup group = panel.findViewById(R.id.portrait_mode_toggle_group);
        row.setVisibility(View.VISIBLE);
        group.measure(View.MeasureSpec.makeMeasureSpec(200, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));
        group.layout(0, 0, group.getMeasuredWidth(), group.getMeasuredHeight());

        SegmentedHanPreference.stackIfClippedNow(group);

        assertEquals(LinearLayout.VERTICAL, row.getOrientation());
        assertEquals(LinearLayout.HORIZONTAL, group.getOrientation());
        assertEquals(LinearLayout.LayoutParams.MATCH_PARENT, group.getLayoutParams().width);
        assertEquals(0, ((LinearLayout.LayoutParams) group.getLayoutParams()).getMarginStart());
    }

    @Test
    public void controlsBelowTitlesUseFullAvailableWidth() {
        Context context = new ContextThemeWrapper(
                InstrumentationRegistry.getInstrumentation().getTargetContext(),
                R.style.LIMESettingsTheme);
        View panel = LayoutInflater.from(context).inflate(R.layout.keyboard_menu_panel, null);
        int[] groupIds = { R.id.han_toggle_group, R.id.split_toggle_group,
                R.id.landscape_split_toggle_group, R.id.numpad_anchor_toggle_group };

        for (int id : groupIds) {
            View group = panel.findViewById(id);
            LinearLayout.LayoutParams params = (LinearLayout.LayoutParams) group.getLayoutParams();
            assertEquals(LinearLayout.LayoutParams.MATCH_PARENT, params.width);
            assertEquals(0, params.getMarginStart());
        }
    }
}
