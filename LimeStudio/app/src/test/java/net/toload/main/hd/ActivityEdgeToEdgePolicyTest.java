package net.toload.main.hd;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.os.Build;

import net.toload.main.hd.ui.LIMESettings;

import org.junit.Test;

public class ActivityEdgeToEdgePolicyTest {

    @Test
    public void androidXHelperIsUsedOnlyWhereEdgeToEdgeIsEnforced() {
        assertFalse(LIMESettings.shouldUseEdgeToEdgeHelper(Build.VERSION_CODES.N_MR1));
        assertFalse(LIMESettings.shouldUseEdgeToEdgeHelper(Build.VERSION_CODES.UPSIDE_DOWN_CAKE));
        assertTrue(LIMESettings.shouldUseEdgeToEdgeHelper(Build.VERSION_CODES.VANILLA_ICE_CREAM));
    }
}
