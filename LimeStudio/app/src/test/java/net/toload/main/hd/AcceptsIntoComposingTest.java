package net.toload.main.hd;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class AcceptsIntoComposingTest {
    @Test
    public void cangjieAcceptsLettersCommaAndDotOnly() {
        assertTrue(LIMEService.acceptsIntoComposing('a', false, false, false));
        assertTrue(LIMEService.acceptsIntoComposing('A', false, false, false));
        assertTrue(LIMEService.acceptsIntoComposing(',', false, false, false));
        assertTrue(LIMEService.acceptsIntoComposing('.', false, false, false));

        assertFalse(LIMEService.acceptsIntoComposing(';', false, false, false));
        assertFalse(LIMEService.acceptsIntoComposing('/', false, false, false));
    }

    @Test
    public void dayiAcceptsLettersDigitsAndAsciiSymbols() {
        assertTrue(LIMEService.acceptsIntoComposing('a', true, true, false));
        assertTrue(LIMEService.acceptsIntoComposing('A', true, true, false));
        assertTrue(LIMEService.acceptsIntoComposing('0', true, true, false));
        assertTrue(LIMEService.acceptsIntoComposing('9', true, true, false));
        assertTrue(LIMEService.acceptsIntoComposing(',', true, true, false));
        assertTrue(LIMEService.acceptsIntoComposing('.', true, true, false));
        assertTrue(LIMEService.acceptsIntoComposing(';', true, true, false));
        assertTrue(LIMEService.acceptsIntoComposing('/', true, true, false));
    }

    @Test
    public void array10AcceptsLettersDigitsCommaAndDotOnly() {
        assertTrue(LIMEService.acceptsIntoComposing('a', false, true, false));
        assertTrue(LIMEService.acceptsIntoComposing('A', false, true, false));
        assertTrue(LIMEService.acceptsIntoComposing('0', false, true, false));
        assertTrue(LIMEService.acceptsIntoComposing('9', false, true, false));
        assertTrue(LIMEService.acceptsIntoComposing(',', false, true, false));
        assertTrue(LIMEService.acceptsIntoComposing('.', false, true, false));

        assertFalse(LIMEService.acceptsIntoComposing(';', false, true, false));
    }
}
