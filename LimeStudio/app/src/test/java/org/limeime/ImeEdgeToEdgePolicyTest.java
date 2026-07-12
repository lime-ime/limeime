package org.limeime;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.os.Build;

import org.junit.Test;

import java.lang.reflect.Method;

public class ImeEdgeToEdgePolicyTest {

    @Test
    public void imeEdgeToEdgeIsForcedOnlyWhereAndroidRequiresIt() throws Exception {
        Method method = LIMEService.class.getDeclaredMethod("shouldForceImeEdgeToEdge", int.class);
        method.setAccessible(true);

        assertFalse((Boolean) method.invoke(null, Build.VERSION_CODES.Q));
        assertTrue((Boolean) method.invoke(null, Build.VERSION_CODES.VANILLA_ICE_CREAM));
    }
}
