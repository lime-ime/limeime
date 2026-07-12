package org.limeime;

import static org.junit.Assert.assertEquals;

import org.limeime.global.LIMEUtilities;

import org.junit.Test;

public class NotificationIconSamplingTest {

    @Test
    public void notificationIconUsesLargestSafePowerOfTwoSample() {
        assertEquals(1, LIMEUtilities.calculateInSampleSize(48, 48, 48, 48));
        assertEquals(2, LIMEUtilities.calculateInSampleSize(144, 144, 48, 48));
        assertEquals(8, LIMEUtilities.calculateInSampleSize(512, 512, 64, 64));
    }
}
