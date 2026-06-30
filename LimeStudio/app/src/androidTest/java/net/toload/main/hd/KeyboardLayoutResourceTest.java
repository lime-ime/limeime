/*
 * Copyright 2025, The LimeIME Open Source Project
 */
package net.toload.main.hd;

import android.content.Context;
import android.util.AttributeSet;
import android.util.Xml;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import net.toload.main.hd.keyboard.LIMEKeyboardView;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.xmlpull.v1.XmlPullParser;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.*;

/**
 * Tests keyboard XML resources whose static key definitions are user-visible
 * behavior.
 */
@RunWith(AndroidJUnit4.class)
public class KeyboardLayoutResourceTest {

    private static final String LIME_ATTR_NS = "http://schemas.android.com/apk/res-auto";
    private static final String ANDROID_ATTR_NS = "http://schemas.android.com/apk/res/android";

    @Test
    public void hsLayoutsUseLowercaseUnshiftedAndUppercaseShiftedLetterCodesAndLabels() {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();

        assertLetterKeyCodes(context, R.xml.lime_hs, false);
        assertLetterKeyCodes(context, R.xml.lime_hs_shift, true);
    }

    @Test
    public void customThemeCandidateEmojiIconsUseThemeTintInNormalState() {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();

        assertVectorPaintUsesOnlyColor(context, R.drawable.sym_candidate_emoji_pink, R.color.second_background_pink);
        assertVectorPaintUsesOnlyColor(context, R.drawable.sym_candidate_emoji_tech_blue, R.color.second_background_tech_blue);
        assertVectorPaintUsesOnlyColor(context, R.drawable.sym_candidate_emoji_fashion_purple, R.color.second_background_fashion_purple);
        assertVectorPaintUsesOnlyColor(context, R.drawable.sym_candidate_emoji_relax_green, R.color.second_background_relax_green);
    }

    @Test
    public void candidateEmojiButtonsDoNotUseStickyFocusedTint() {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();

        assertSelectorDoesNotContainFocusedState(context, R.drawable.btn_emoji_light);
        assertSelectorDoesNotContainFocusedState(context, R.drawable.btn_emoji_dark);
        assertSelectorDoesNotContainFocusedState(context, R.drawable.btn_emoji_pink);
        assertSelectorDoesNotContainFocusedState(context, R.drawable.btn_emoji_tech_blue);
        assertSelectorDoesNotContainFocusedState(context, R.drawable.btn_emoji_fashion_purple);
        assertSelectorDoesNotContainFocusedState(context, R.drawable.btn_emoji_relax_green);
    }

    @Test
    public void shiftedSymbolKeysDoNotShowChineseRootSubLabels() {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();

        assertNoSubLabelsOnShiftedSymbolKeys(context, R.xml.lime_phonetic_shift);
        assertNoSubLabelsOnShiftedSymbolKeys(context, R.xml.lime_ez_shift);
        assertNoSubLabelsOnShiftedSymbolKeys(context, R.xml.lime_et_41_shift);
        assertNoSubLabelsOnShiftedSymbolKeys(context, R.xml.lime_dayi_sym_shift);
    }

    @Test
    public void englishSymbolKeyShowsPhoneShortcutHintAndLongPressPolicyKeepsNormalSymbolTap() {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();

        assertEnglishPhoneShortcutKey(context, R.xml.lime_english);
        assertEnglishPhoneShortcutKey(context, R.xml.lime_english_shift);
        assertEnglishPhoneShortcutKey(context, R.xml.lime_english_number);
        assertEnglishPhoneShortcutKey(context, R.xml.lime_english_number_shift);
    }

    @Test
    public void array10AutoCommitRowHasTitleAndSummary() throws Exception {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();

        assertLayoutContainsTextResource(context, R.layout.fragment_im_detail, R.string.auto_commit);
        assertLayoutContainsTextResource(context, R.layout.fragment_im_detail, R.string.auto_commit_summary);
    }

    @Test
    public void vibrateLevelStoredValuesRemainCompatible() {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();

        assertArrayEquals(new String[]{"20", "30", "40", "50", "60"},
                context.getResources().getStringArray(R.array.vibrate_level_values));
    }

    @Test
    public void keypressSoundVolumeIncludesSystemDefaultAndCustomScalars() {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();

        assertArrayEquals(new String[]{"-1", "0.10", "0.25", "0.50", "0.75", "1.00"},
                context.getResources().getStringArray(R.array.keypress_sound_volume_values));
    }

    @Test
    public void settingsActionLayoutsUseThemeAccentInsteadOfFixedBlue() throws Exception {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();

        assertLayoutDoesNotReferenceColor(context, R.layout.fragment_db_manager, R.color.material_blue);
        assertLayoutDoesNotReferenceColor(context, R.layout.fragment_im_list, R.color.material_blue);
        assertLayoutDoesNotReferenceColor(context, R.layout.fragment_manage_im, R.color.material_blue);
        assertLayoutDoesNotReferenceColor(context, R.layout.fragment_manage_related, R.color.material_blue);
        assertLayoutDoesNotReferenceColor(context, R.layout.fragment_im_detail, R.color.material_blue);
        assertLayoutDoesNotReferenceColor(context, R.layout.fragment_setup, R.color.material_blue);
        assertLayoutDoesNotReferenceColor(context, R.layout.sheet_manage_im_add, R.color.material_blue);
        assertLayoutDoesNotReferenceColor(context, R.layout.sheet_manage_im_edit, R.color.material_blue);
        assertLayoutDoesNotReferenceColor(context, R.layout.sheet_manage_related_add, R.color.material_blue);
        assertLayoutDoesNotReferenceColor(context, R.layout.sheet_manage_related_edit, R.color.material_blue);
    }

    @Test
    public void computerSimpleLayoutUsesComputerNumpadDigitOrder() {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        // feat#N02: computer_simple is phone_simple with the digit grid in computer-numpad
        // order — 7 8 9 on top, then 4 5 6, then 1 2 3, with 0 on the bottom row. The
        // framing modifier keys (123, ABC, delete, +-*/=, space, done, return) stay put.
        List<List<Integer>> digitsPerRow = readDigitCodesPerRow(context, R.xml.computer_simple);

        assertEquals("computer_simple should have 4 rows", 4, digitsPerRow.size());
        assertEquals("top row should read 7 8 9",
                java.util.Arrays.asList(55, 56, 57), digitsPerRow.get(0));
        assertEquals("second row unchanged: 4 5 6",
                java.util.Arrays.asList(52, 53, 54), digitsPerRow.get(1));
        assertEquals("third row should read 1 2 3",
                java.util.Arrays.asList(49, 50, 51), digitsPerRow.get(2));
        assertEquals("0 stays on the bottom row",
                java.util.Collections.singletonList(48), digitsPerRow.get(3));
    }

    @Test
    public void et41PopupDigitsShowOnLongPressKeyLabels() {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();

        assertEt41LongPressDigitHint(context, '-', "5", "- 5\\nㄥ");
        assertEt41LongPressDigitHint(context, '=', "6", "= 6\\nㄦ");
    }

    private void assertEt41LongPressDigitHint(Context context, char code, String popupDigit,
                                              String expectedLabel) {
        try {
            XmlPullParser parser = context.getResources().getXml(R.xml.lime_et_41);
            int eventType;
            while ((eventType = parser.next()) != XmlPullParser.END_DOCUMENT) {
                if (eventType != XmlPullParser.START_TAG || !"Key".equals(parser.getName())) {
                    continue;
                }

                String value = parser.getAttributeValue(LIME_ATTR_NS, "codes");
                if (!String.valueOf((int) code).equals(value)) {
                    continue;
                }

                String normalizedLabel = parser.getAttributeValue(LIME_ATTR_NS, "keyLabel")
                        .replace("\\n", "\n");
                assertEquals("ET41 long-press digit should be visible on key label",
                        expectedLabel.replace("\\n", "\n"), normalizedLabel);
                assertEquals("ET41 key should keep popup digit",
                        popupDigit, parser.getAttributeValue(LIME_ATTR_NS, "popupCharacters"));
                AttributeSet attrs = Xml.asAttributeSet(parser);
                assertEquals("ET41 key should keep popup_template long-press",
                        R.xml.popup_template,
                        attrs.getAttributeResourceValue(LIME_ATTR_NS, "popupKeyboard", 0));
                return;
            }
            fail("ET41 should contain key code " + (int) code);
        } catch (Exception e) {
            fail("Unable to read ET41 keyboard XML resource: " + e.getMessage());
        }
    }

    // Returns, per <Row>, the codes of single-code digit keys (0-9). Also asserts every
    // digit key's keyLabel matches its code, so a key can't show "7" yet type something else.
    private List<List<Integer>> readDigitCodesPerRow(Context context, int layoutId) {
        List<List<Integer>> rows = new ArrayList<>();
        try {
            XmlPullParser parser = context.getResources().getXml(layoutId);
            int eventType;
            List<Integer> current = null;
            while ((eventType = parser.next()) != XmlPullParser.END_DOCUMENT) {
                if (eventType != XmlPullParser.START_TAG) {
                    continue;
                }
                if ("Row".equals(parser.getName())) {
                    current = new ArrayList<>();
                    rows.add(current);
                } else if ("Key".equals(parser.getName())) {
                    String value = parser.getAttributeValue(LIME_ATTR_NS, "codes");
                    if (value == null || value.isEmpty() || value.contains(",")) {
                        continue;
                    }
                    int code = Integer.parseInt(value);
                    if (code >= '0' && code <= '9') {
                        String label = parser.getAttributeValue(LIME_ATTR_NS, "keyLabel");
                        assertEquals("digit key label must match its code in layout "
                                        + context.getResources().getResourceEntryName(layoutId),
                                String.valueOf((char) code), label);
                        if (current != null) {
                            current.add(code);
                        }
                    }
                }
            }
        } catch (Exception e) {
            fail("Unable to read keyboard XML resource " + layoutId + ": " + e.getMessage());
        }
        return rows;
    }

    private void assertLayoutContainsTextResource(Context context, int layoutId, int textResId) throws Exception {
        XmlPullParser parser = context.getResources().getLayout(layoutId);
        while (parser.next() != XmlPullParser.END_DOCUMENT) {
            if (parser.getEventType() != XmlPullParser.START_TAG) {
                continue;
            }
            AttributeSet attrs = Xml.asAttributeSet(parser);
            int value = attrs.getAttributeResourceValue(ANDROID_ATTR_NS, "text", 0);
            if (value == textResId) {
                return;
            }
        }
        fail("Layout " + context.getResources().getResourceEntryName(layoutId)
                + " should contain text resource "
                + context.getResources().getResourceEntryName(textResId));
    }

    private void assertLayoutDoesNotReferenceColor(Context context, int layoutId, int colorId) throws Exception {
        XmlPullParser parser = context.getResources().getLayout(layoutId);
        while (parser.next() != XmlPullParser.END_DOCUMENT) {
            if (parser.getEventType() != XmlPullParser.START_TAG) {
                continue;
            }

            AttributeSet attrs = Xml.asAttributeSet(parser);
            for (int i = 0; i < attrs.getAttributeCount(); i++) {
                int value = attrs.getAttributeResourceValue(i, 0);
                assertNotEquals("Settings layout "
                                + context.getResources().getResourceEntryName(layoutId)
                                + " should use theme accent instead of fixed "
                                + context.getResources().getResourceEntryName(colorId)
                                + " on <" + parser.getName() + "> attribute "
                                + attrs.getAttributeName(i),
                        colorId, value);
            }
        }
    }

    private void assertLetterKeyCodes(Context context, int layoutId, boolean shouldBeUppercase) {
        List<KeyDefinition> letterKeys = readLetterKeys(context, layoutId);
        assertFalse("HS layout should contain Latin letter keys", letterKeys.isEmpty());

        for (KeyDefinition key : letterKeys) {
            if (shouldBeUppercase) {
                assertTrue("Shifted HS letter should emit uppercase code: " + key.code,
                        key.code >= 'A' && key.code <= 'Z');
                assertEquals("Shifted HS letter should show uppercase label",
                        key.label.toUpperCase(), key.label);
            } else {
                assertTrue("Unshifted HS letter should emit lowercase code: " + key.code,
                        key.code >= 'a' && key.code <= 'z');
                assertEquals("Unshifted HS letter should show lowercase label",
                        key.label.toLowerCase(), key.label);
            }
        }
    }

    private List<KeyDefinition> readLetterKeys(Context context, int layoutId) {
        List<KeyDefinition> keys = new ArrayList<>();
        try {
            XmlPullParser parser = context.getResources().getXml(layoutId);
            int eventType;
            while ((eventType = parser.next()) != XmlPullParser.END_DOCUMENT) {
                if (eventType != XmlPullParser.START_TAG || !"Key".equals(parser.getName())) {
                    continue;
                }

                String value = parser.getAttributeValue(LIME_ATTR_NS, "codes");
                if (value == null || value.isEmpty() || value.contains(",")) {
                    continue;
                }

                int code = Integer.parseInt(value);
                String label = parser.getAttributeValue(LIME_ATTR_NS, "keyLabel");
                if (label != null && label.length() == 1 &&
                        ((code >= 'A' && code <= 'Z') || (code >= 'a' && code <= 'z'))) {
                    keys.add(new KeyDefinition(code, label));
                }
            }
        } catch (Exception e) {
            fail("Unable to read keyboard XML resource " + layoutId + ": " + e.getMessage());
        }
        return keys;
    }

    private void assertNoSubLabelsOnShiftedSymbolKeys(Context context, int layoutId) {
        boolean sawSymbolKey = false;
        boolean sawAlphabetSubLabel = false;
        try {
            XmlPullParser parser = context.getResources().getXml(layoutId);
            int eventType;
            while ((eventType = parser.next()) != XmlPullParser.END_DOCUMENT) {
                if (eventType != XmlPullParser.START_TAG || !"Key".equals(parser.getName())) {
                    continue;
                }

                String value = parser.getAttributeValue(LIME_ATTR_NS, "codes");
                if (value == null || value.isEmpty() || value.contains(",")) {
                    continue;
                }

                int code = Integer.parseInt(value);
                String label = parser.getAttributeValue(LIME_ATTR_NS, "keyLabel");
                if (label == null) {
                    continue;
                }

                String normalizedLabel = label.replace("\\n", "\n");
                if (isUppercaseAsciiLetter(code)) {
                    if (normalizedLabel.contains("\n")) {
                        sawAlphabetSubLabel = true;
                    }
                    continue;
                }

                if (isPrintableNonAlphabetSymbol(code)) {
                    sawSymbolKey = true;
                    assertFalse("Shifted symbol key should not show root sub-label in layout "
                                    + layoutId + ": code=" + code + " label=" + label,
                            normalizedLabel.contains("\n"));
                }
            }
        } catch (Exception e) {
            fail("Unable to read keyboard XML resource " + layoutId + ": " + e.getMessage());
        }

        assertTrue("Shifted layout should contain printable symbol keys: " + layoutId, sawSymbolKey);
        assertTrue("Shifted alphabet roots should remain in layout: " + layoutId, sawAlphabetSubLabel);
    }

    private void assertEnglishPhoneShortcutKey(Context context, int layoutId) {
        try {
            android.content.res.XmlResourceParser parser = context.getResources().getXml(layoutId);
            int eventType;
            while ((eventType = parser.next()) != XmlPullParser.END_DOCUMENT) {
                if (eventType != XmlPullParser.START_TAG || !"Key".equals(parser.getName())) {
                    continue;
                }

                String value = parser.getAttributeValue(LIME_ATTR_NS, "codes");
                if (!String.valueOf(LIMEService.KEYCODE_SWITCH_TO_SYMBOL_MODE).equals(value)) {
                    continue;
                }

                String name = context.getResources().getResourceEntryName(layoutId);
                // feat#124: the symbol key keeps its "123" face (a @string resource) ...
                int labelResId = parser.getAttributeResourceValue(LIME_ATTR_NS, "keyLabel", 0);
                assertTrue("English symbol key should keep its 123 string-resource face in " + name,
                        labelResId != 0);
                assertEquals("English symbol key should keep its 123 face in " + name,
                        "123", context.getString(labelResId));
                // ... plus a popupKeyboard so the existing "..." minikeyboard hint renders at the
                // bottom and marks the key as the phone_simple long-press shortcut.
                int popupResId = parser.getAttributeResourceValue(LIME_ATTR_NS, "popupKeyboard", 0);
                assertTrue("English symbol key should carry a popupKeyboard hint in " + name,
                        popupResId != 0);
                assertTrue("English symbol key long press should use the phone_simple shortcut",
                        LIMEKeyboardView.isEnglishPhoneSimpleShortcutKey(
                                LIMEService.KEYCODE_SWITCH_TO_SYMBOL_MODE, popupResId));
                return;
            }
            fail("English layout should contain the normal symbol-mode key: "
                    + context.getResources().getResourceEntryName(layoutId));
        } catch (Exception e) {
            fail("Unable to read keyboard XML resource " + layoutId + ": " + e.getMessage());
        }
    }

    private boolean isPrintableNonAlphabetSymbol(int code) {
        return code >= 33 && code <= 126 && !isUppercaseAsciiLetter(code) && !isLowercaseAsciiLetter(code);
    }

    private boolean isUppercaseAsciiLetter(int code) {
        return code >= 'A' && code <= 'Z';
    }

    private boolean isLowercaseAsciiLetter(int code) {
        return code >= 'a' && code <= 'z';
    }

    private void assertVectorPaintUsesOnlyColor(Context context, int drawableId, int expectedColorId) {
        try {
            XmlPullParser parser = context.getResources().getXml(drawableId);
            int paintedPathCount = 0;
            int eventType;
            while ((eventType = parser.next()) != XmlPullParser.END_DOCUMENT) {
                if (eventType != XmlPullParser.START_TAG || !"path".equals(parser.getName())) {
                    continue;
                }

                paintedPathCount += assertPaintAttributeUsesOnlyColor(
                        parser, drawableId, "fillColor", expectedColorId);
                paintedPathCount += assertPaintAttributeUsesOnlyColor(
                        parser, drawableId, "strokeColor", expectedColorId);
            }
            assertTrue("Vector should contain painted paths: " + drawableId, paintedPathCount > 0);
        } catch (Exception e) {
            fail("Unable to read vector drawable " + drawableId + ": " + e.getMessage());
        }
    }

    private int assertPaintAttributeUsesOnlyColor(
            XmlPullParser parser, int drawableId, String attrName, int expectedColorId) {
        String value = parser.getAttributeValue(ANDROID_ATTR_NS, attrName);
        if (value == null || "@android:color/transparent".equals(value)) {
            return 0;
        }

        AttributeSet attributes = Xml.asAttributeSet(parser);
        int colorId = attributes.getAttributeResourceValue(ANDROID_ATTR_NS, attrName, 0);
        if (colorId == android.R.color.transparent) {
            return 0;
        }
        assertEquals("Drawable " + drawableId + " " + attrName + " should use theme color",
                expectedColorId, colorId);
        return 1;
    }

    private void assertSelectorDoesNotContainFocusedState(Context context, int drawableId) {
        try {
            XmlPullParser parser = context.getResources().getXml(drawableId);
            int eventType;
            while ((eventType = parser.next()) != XmlPullParser.END_DOCUMENT) {
                if (eventType != XmlPullParser.START_TAG || !"item".equals(parser.getName())) {
                    continue;
                }
                AttributeSet attrs = Xml.asAttributeSet(parser);
                boolean focused = attrs.getAttributeBooleanValue(ANDROID_ATTR_NS, "state_focused", false);
                assertFalse("Emoji button selector should not keep highlight tint on focus: " + drawableId,
                        focused);
            }
        } catch (Exception e) {
            fail("Unable to read selector drawable " + drawableId + ": " + e.getMessage());
        }
    }

    private static class KeyDefinition {
        final int code;
        final String label;

        KeyDefinition(int code, String label) {
            this.code = code;
            this.label = label;
        }
    }
}
