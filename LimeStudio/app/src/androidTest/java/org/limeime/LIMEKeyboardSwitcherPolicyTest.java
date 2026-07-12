/*
 * Copyright 2026, The LimeIME Open Source Project
 */
package org.limeime;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.limeime.data.Keyboard;

import org.junit.Test;
import org.junit.runner.RunWith;

import static org.junit.Assert.*;

/**
 * Tests keyboard selection policy that should not depend on per-IM legacy
 * English layout fields.
 */
@RunWith(AndroidJUnit4.class)
public class LIMEKeyboardSwitcherPolicyTest {

    @Test
    public void englishModeIgnoresKeyboardTableEnglishLayoutFields() throws Exception {
        Keyboard keyboard = new Keyboard();
        keyboard.setEngkb("lime_abc");
        keyboard.setEngshiftkb("lime_abc_shift");

        assertEquals("lime_english", LIMEKeyboardSwitcher.resolveEnglishLayoutId(keyboard, false, false));
        assertEquals("lime_english_shift", LIMEKeyboardSwitcher.resolveEnglishLayoutId(keyboard, false, true));
        assertEquals("lime_english_number", LIMEKeyboardSwitcher.resolveEnglishLayoutId(keyboard, true, false));
        assertEquals("lime_english_number_shift", LIMEKeyboardSwitcher.resolveEnglishLayoutId(keyboard, true, true));
    }

    @Test
    public void cangjieSemicolonLayoutIdsAliasToCurrentCangjieResources() {
        assertEquals("lime_cj", LIMEKeyboardSwitcher.resolveCjSemicolonSourceLayoutId("lime_cj_semi"));
        assertEquals("lime_cj_shift", LIMEKeyboardSwitcher.resolveCjSemicolonSourceLayoutId("lime_cj_semi_shift"));
        assertEquals("lime_cj_number", LIMEKeyboardSwitcher.resolveCjSemicolonSourceLayoutId("lime_cj_number_semi"));
        assertEquals("lime_cj_number_shift", LIMEKeyboardSwitcher.resolveCjSemicolonSourceLayoutId("lime_cj_number_semi_shift"));

        assertTrue(LIMEKeyboardSwitcher.isCjSemicolonLayoutId("lime_cj_semi"));
        assertTrue(LIMEKeyboardSwitcher.isCjSemicolonLayoutId("lime_cj_number_semi_shift"));
        assertFalse(LIMEKeyboardSwitcher.isCjSemicolonLayoutId("lime_cj"));
        assertFalse(LIMEKeyboardSwitcher.isCjSemicolonLayoutId("lime_cj_number"));
    }

    @Test
    public void cangjieSemicolonKeyboardCodesForceSymbolMapping() {
        assertTrue(LIMEService.hasSymbolMappingForKeyboard(false, "cj_semi"));
        assertTrue(LIMEService.hasSymbolMappingForKeyboard(false, "cj_num_semi"));
        assertFalse(LIMEService.hasSymbolMappingForKeyboard(false, "cjnum"));
        assertTrue(LIMEService.hasSymbolMappingForKeyboard(true, "cjnum"));
    }
}
