package org.limeime;

import static org.junit.Assert.*;

import org.junit.Test;
import org.limeime.keyboard.ReachGeometry;

public class ReachGeometryTest {

    // 13"-class tablet landscape: 2560 px @ 264 xdpi. Legacy width 2560/17 = 151 px;
    // reach cap clamps the key to 13 mm = 135 px.
    @Test
    public void splitKeyWidthCapsOnLargeTablet() {
        assertEquals(135, ReachGeometry.splitKeyWidth(2560, 10, 7, 264f));
    }

    // Phone landscape: 1080 px @ 420 xdpi — legacy (83 px ≈ 5 mm) is already narrower
    // than the reach band, so the cap must not change it.
    @Test
    public void splitKeyWidthUnchangedOnPhone() {
        assertEquals(Math.round(1080f / 13f), ReachGeometry.splitKeyWidth(1080, 10, 3, 420f));
    }

    // Defensive: xdpi <= 0 (bogus DisplayMetrics) falls back to the legacy formula.
    @Test
    public void splitKeyWidthFallsBackToLegacyWithoutDpi() {
        assertEquals(Math.round(2560f / 17f), ReachGeometry.splitKeyWidth(2560, 10, 7, 0f));
    }

    // 6.7"-class portrait: 1290 px @ 460 xdpi ≈ 71 mm wide → gate (64 mm) passes,
    // one-hand width = 60 mm.
    @Test
    public void oneHandGateAndWidth() {
        assertTrue(ReachGeometry.oneHandAvailable(1290, 460f));
        assertEquals(ReachGeometry.mmToPx(60f, 460f), ReachGeometry.oneHandWidth(1290, 460f));
    }

    // mini-class: 1080 px @ 440 xdpi ≈ 62.3 mm → below the 64 mm gate.
    @Test
    public void oneHandGateExcludesNarrowPhones() {
        assertFalse(ReachGeometry.oneHandAvailable(1080, 440f));
    }

    // Large tablet: 5 × 14 mm = 70 mm wins under the 40% cap; small tablet: the
    // 40% cap wins.
    @Test
    public void numpadWidthClamps() {
        assertEquals(ReachGeometry.mmToPx(70f, 264f), ReachGeometry.numpadWidth(2560, 5, 264f));
        assertEquals(Math.round(1488 * 0.40f), ReachGeometry.numpadWidth(1488, 5, 326f));
    }
}
