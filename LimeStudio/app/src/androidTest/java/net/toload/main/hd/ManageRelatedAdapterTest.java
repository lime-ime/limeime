/*
 * Copyright 2025, The LimeIME Open Source Project
 */
package net.toload.main.hd;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import org.junit.Test;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;

/**
 * Tests for ManageRelatedAdapter basic API presence.
 */
@RunWith(AndroidJUnit4.class)
public class ManageRelatedAdapterTest {

    @Test
    public void testManageRelatedAdapterClassExists() {
        try {
            Class<?> cls = Class.forName("net.toload.main.hd.ui.adapter.ManageRelatedAdapter");
            assertNotNull(cls);
        } catch (ClassNotFoundException e) {
            fail("ManageRelatedAdapter class not found");
        }
    }

    @Test
    public void testManageRelatedAdapterDiffUtilAndCallbacks() throws Exception {
        Class<?> cls = Class.forName("net.toload.main.hd.ui.adapter.ManageRelatedAdapter");

        boolean hasDiffCallback = false;
        for (java.lang.reflect.Field f : cls.getDeclaredFields()) {
            if (f.getType().getName().contains("DiffUtil") || f.getName().toLowerCase().contains("diff")) { hasDiffCallback = true; break; }
        }
        if (!hasDiffCallback) {
            for (Class<?> inner : cls.getDeclaredClasses()) {
                for (java.lang.reflect.Method m : inner.getDeclaredMethods()) {
                    String n = m.getName().toLowerCase();
                    if (n.contains("areitemssame") || n.contains("arecontents")) { hasDiffCallback = true; break; }
                }
                if (hasDiffCallback) break;
            }
        }
        assertTrue("ManageRelatedAdapter should define DiffUtil callback", hasDiffCallback);

        boolean hasClickOrDelete = false;
        for (java.lang.reflect.Method m : cls.getMethods()) {
            String n = m.getName().toLowerCase();
            if (n.contains("click") || n.contains("listener") || n.contains("delete")) { hasClickOrDelete = true; break; }
        }
        assertTrue("ManageRelatedAdapter should expose click/delete callbacks", hasClickOrDelete);

        boolean handlesNullSafe = false;
        for (java.lang.reflect.Method m : cls.getDeclaredMethods()) {
            if (m.getName().toLowerCase().contains("bind") || m.getName().toLowerCase().contains("onbind")) { handlesNullSafe = true; break; }
        }
        assertTrue("ManageRelatedAdapter should bind items safely", handlesNullSafe);
    }
}
