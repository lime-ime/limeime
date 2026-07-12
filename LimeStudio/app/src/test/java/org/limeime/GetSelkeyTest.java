package org.limeime;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

// Exhaustive branch coverage for the selkey path after the imkeys migration. resolveSelkey is the
// BEHAVIOUR-PRESERVING extraction of getSelkey's original inline logic (flags passed straight
// through), so these assert the exact original outputs AND exercise the HARDCODED fallback
// (null/invalid configured selkey → each curated set).
public class GetSelkeyTest {

    private static final String SEL      = "1234567890";
    private static final String SYM_DAYI = "'[]-\\^&*()";   // number-only, dayi, std-phonetic
    private static final String SYM_GEN  = "!@#$%^&*()";    // number+symbol, non-dayi/non-std-phonetic

    // ---- resolveSelkey validity branches ----

    @Test public void validSelkey_noRootCollision_nonPhonetic_keptAsIs() {
        assertEquals(SYM_GEN, SearchServer.resolveSelkey(SYM_GEN, false, false, false, false, false));
        assertEquals(SEL, SearchServer.resolveSelkey(SEL, false, false, false, false, false)); // no number root
    }

    @Test public void letterInSelkey_invalid_fallsToHardcoded() {
        assertEquals(SEL, SearchServer.resolveSelkey("abcdefghij", false, false, false, false, false));
    }

    @Test public void digitInSelkey_whenNumberRoot_invalid_fallsToHardcoded() {
        assertEquals(SYM_DAYI, SearchServer.resolveSelkey(SEL, true, false, false, false, false));
    }

    @Test public void nullOrWrongLengthSelkey_invalid_fallsToHardcoded() {
        assertEquals(SEL, SearchServer.resolveSelkey(null, false, false, false, false, false));
        assertEquals(SEL, SearchServer.resolveSelkey("123", false, false, false, false, false));
    }

    @Test public void phonetic_forcesHardcoded_evenWhenSelkeyValid() {
        assertEquals(SEL, SearchServer.resolveSelkey(SEL, false, false, true, false, false)); // eten26/hsu → digits
    }

    // ---- resolveSelkey HARDCODED FALLBACK — every curated set ----
    @Test public void hardcodedFallback_allThreeSets() {
        assertEquals(SYM_DAYI, SearchServer.resolveSelkey(null, true,  true,  false, true,  false)); // num+sym → dayi
        assertEquals(SYM_DAYI, SearchServer.resolveSelkey(null, true,  true,  true,  false, true));  // num+sym → std-phonetic
        assertEquals(SYM_GEN,  SearchServer.resolveSelkey(null, true,  true,  false, false, false)); // num+sym → generic
        assertEquals(SYM_DAYI, SearchServer.resolveSelkey(null, true,  false, false, false, false)); // number-only
        assertEquals(SEL,      SearchServer.resolveSelkey(null, false, false, false, false, false)); // neither
    }

    // ---- per built-in IM (documents IM → selkey via each IM's real flags) ----
    @Test public void perIM_selkeys_matchOriginal() {
        assertEquals(SEL,      SearchServer.resolveSelkey(SEL, false, false, false, false, false)); // cangjie  F/F
        assertEquals(SYM_DAYI, SearchServer.resolveSelkey(SEL, true,  true,  false, true,  false)); // dayi     T/T
        assertEquals(SYM_DAYI, SearchServer.resolveSelkey(SEL, true,  false, false, false, false)); // array10  T/F
        assertEquals(SYM_GEN,  SearchServer.resolveSelkey(SEL, true,  true,  false, false, false)); // array/hs T/T
        assertEquals(SEL,      SearchServer.resolveSelkey(SEL, false, true,  false, false, false)); // wb       F/T
        assertEquals(SYM_DAYI, SearchServer.resolveSelkey(SEL, true,  true,  true,  false, true));  // phonetic std
        assertEquals(SEL,      SearchServer.resolveSelkey(SEL, false, false, true,  false, false)); // phonetic eten26/hsu
    }

    // ---- mixedModeSelkeyUsesSpace: every branch ----
    @Test public void mixedModeSelkeyUsesSpace_branches() {
        assertTrue(LIMEService.mixedModeSelkeyUsesSpace(true, "array", "phonetic"));     // symbol IM → space
        assertFalse(LIMEService.mixedModeSelkeyUsesSpace(false, "array", "phonetic"));   // no symbol → backtick
        assertFalse(LIMEService.mixedModeSelkeyUsesSpace(true, "dayi", "phonetic"));     // dayi excluded
        assertFalse(LIMEService.mixedModeSelkeyUsesSpace(true, "phonetic", "phonetic")); // std phonetic excluded
        assertTrue(LIMEService.mixedModeSelkeyUsesSpace(true, "phonetic", "et26"));      // non-std phonetic
    }
}
