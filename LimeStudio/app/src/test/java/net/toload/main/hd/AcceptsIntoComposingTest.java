package net.toload.main.hd;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

// Exhaustive branch coverage for the imkeys-migration acceptance path:
//   isKeyInImkeys       — ',' '.' rule / empty / membership (case-insensitive)
//   acceptsIntoComposing — imkeys path (root / non-root / phonetic-space) + all 5 fallback branches
// acceptsIntoComposing / isKeyInImkeys are package-private static (pure), so every branch is
// directly reachable here.
public class AcceptsIntoComposingTest {

    private static final int SPACE = LIMEService.MY_KEYCODE_SPACE;   // 32

    // ---- isKeyInImkeys: every branch ----
    @Test public void isKeyInImkeys_commaDot_alwaysRoot() {
        assertTrue(LIMEService.isKeyInImkeys(',', ""));     // ',' '.' rule fires before the empty check
        assertTrue(LIMEService.isKeyInImkeys('.', "abc"));
    }
    @Test public void isKeyInImkeys_empty_false() {
        assertFalse(LIMEService.isKeyInImkeys('a', ""));
        assertFalse(LIMEService.isKeyInImkeys('a', null));
    }
    @Test public void isKeyInImkeys_membership_caseInsensitive() {
        assertTrue(LIMEService.isKeyInImkeys('a', "abc"));   // contains
        assertTrue(LIMEService.isKeyInImkeys('A', "abc"));   // case-insensitive
        assertFalse(LIMEService.isKeyInImkeys('z', "abc"));  // not contained
    }

    // ---- acceptsIntoComposing: imkeys path (imkeys non-empty) ----
    @Test public void imkeysPath_rootComposes_nonRootRejected() {
        assertTrue(LIMEService.acceptsIntoComposing('a', "abc", false, false, false));   // root
        assertFalse(LIMEService.acceptsIntoComposing('z', "abc", false, false, false));  // non-root
        assertTrue(LIMEService.acceptsIntoComposing(',', "abc", false, false, false));   // ',' '.' root
    }
    @Test public void imkeysPath_phoneticSpace() {
        assertTrue(LIMEService.acceptsIntoComposing(SPACE, "abc", false, false, true));   // phonetic space
        assertFalse(LIMEService.acceptsIntoComposing(SPACE, "abc", false, false, false)); // space, not phonetic, not a root
    }
    // array10 letter-leak fix: digits compose, letters do NOT
    @Test public void imkeysPath_array10_lettersRejected() {
        String a10 = "1234567890";
        assertFalse(LIMEService.acceptsIntoComposing('a', a10, false, true, false));
        assertFalse(LIMEService.acceptsIntoComposing('A', a10, false, true, false));
        assertTrue(LIMEService.acceptsIntoComposing('0', a10, false, true, false));
        assertTrue(LIMEService.acceptsIntoComposing(',', a10, false, true, false));
        assertFalse(LIMEService.acceptsIntoComposing(';', a10, false, true, false));
    }
    // array (ARRAY_KEY): letters + ,.;/ are roots; bare digits are NOT
    @Test public void imkeysPath_array_bareDigitRejected() {
        String arr = "qazwsxedcrfvtgbyhnujmik,ol.p;/";
        assertTrue(LIMEService.acceptsIntoComposing('w', arr, true, true, false));
        assertFalse(LIMEService.acceptsIntoComposing('0', arr, true, true, false));
    }
    // eten26 fix: phonetic roots must be the TYPE-SPECIFIC keymap (ETEN26_KEY = letters + ,.),
    // NOT the stored BPMF string. Guards against the BPMF leak where currentImKeys=BPMF made
    // eten26/hsu wrongly compose digits / ; / - . Flags are F/F for eten26 (standardPhonetic=false).
    @Test public void imkeysPath_eten26_rejectsDigitsAndSymbols() {
        String et26 = "qazwsxedcrfvtgbyhnujmikolp,.";              // ETEN26_KEY
        assertTrue(LIMEService.acceptsIntoComposing('q', et26, false, false, true));   // letter root
        assertTrue(LIMEService.acceptsIntoComposing(',', et26, false, false, true));   // ,/. always root
        assertTrue(LIMEService.acceptsIntoComposing('.', et26, false, false, true));
        assertTrue(LIMEService.acceptsIntoComposing(SPACE, et26, false, false, true)); // phonetic space
        assertFalse(LIMEService.acceptsIntoComposing('3', et26, false, false, true));  // BPMF digit — NOT an eten26 root
        assertFalse(LIMEService.acceptsIntoComposing(';', et26, false, false, true));  // BPMF ';' — NOT a root
        assertFalse(LIMEService.acceptsIntoComposing('/', et26, false, false, true));  // BPMF '/' — NOT a root
    }

    // ---- acceptsIntoComposing: custom-no-imkeys fallback (imkeys empty) — all 5 branches ----
    @Test public void fallback_commaDot_whenNoSymbol() {                      // branch: !hasSymbol && (,|.)
        assertTrue(LIMEService.acceptsIntoComposing(',', "", false, true, false));
        assertTrue(LIMEService.acceptsIntoComposing('.', "", false, false, false));
    }
    @Test public void fallback_letterOnly_noSymbol_noNumber() {               // branch: !hasSymbol && !hasNumber
        assertTrue(LIMEService.acceptsIntoComposing('a', "", false, false, false));    // letter
        assertFalse(LIMEService.acceptsIntoComposing('0', "", false, false, false));   // digit not
        assertFalse(LIMEService.acceptsIntoComposing(';', "", false, false, false));   // symbol not
        assertTrue(LIMEService.acceptsIntoComposing(SPACE, "", false, false, true));   // phonetic space
    }
    @Test public void fallback_number_noSymbol_hasNumber() {                  // branch: !hasSymbol (hasNumber)
        assertTrue(LIMEService.acceptsIntoComposing('a', "", false, true, false));     // letter
        assertTrue(LIMEService.acceptsIntoComposing('0', "", false, true, false));     // digit
        assertFalse(LIMEService.acceptsIntoComposing(';', "", false, true, false));    // symbol not
    }
    @Test public void fallback_symbol_hasSymbol_noNumber() {                  // branch: !hasNumber (hasSymbol)
        assertTrue(LIMEService.acceptsIntoComposing('a', "", true, false, false));     // letter
        assertTrue(LIMEService.acceptsIntoComposing(';', "", true, false, false));     // symbol
        assertFalse(LIMEService.acceptsIntoComposing('0', "", true, false, false));    // digit not
        assertTrue(LIMEService.acceptsIntoComposing(SPACE, "", true, false, true));    // phonetic space
    }
    @Test public void fallback_numberAndSymbol() {                           // branch: else (hasSymbol && hasNumber)
        assertTrue(LIMEService.acceptsIntoComposing('0', "", true, true, false));      // digit
        assertTrue(LIMEService.acceptsIntoComposing(';', "", true, true, false));      // symbol
        assertTrue(LIMEService.acceptsIntoComposing('a', "", true, true, false));      // letter
    }
}
