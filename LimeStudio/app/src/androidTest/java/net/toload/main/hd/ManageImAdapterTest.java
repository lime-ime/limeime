/*
 * Copyright 2025, The LimeIME Open Source Project
 */
package net.toload.main.hd;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import org.junit.Test;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;

/**
 * Tests for ManageImAdapter basic API presence.
 */
@RunWith(AndroidJUnit4.class)
public class ManageImAdapterTest {

    @Test
    public void testManageImAdapterClassExists() {
        try {
            Class<?> cls = Class.forName("net.toload.main.hd.ui.adapter.ManageImAdapter");
            assertNotNull(cls);
        } catch (ClassNotFoundException e) {
            fail("ManageImAdapter class not found");
        }
    }

    @Test
    public void testManageImAdapterDiffUtilAndClickApis() throws Exception {
        Class<?> cls = Class.forName("net.toload.main.hd.ui.adapter.ManageImAdapter");

        boolean hasDiffCallback = false;
        for (java.lang.reflect.Field f : cls.getDeclaredFields()) {
            if (f.getType().getName().contains("DiffUtil") || f.getName().toLowerCase().contains("diff")) {
                hasDiffCallback = true; break;
            }
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
        assertTrue("ManageImAdapter should define DiffUtil callback", hasDiffCallback);

        boolean hasOnBindOrTruncate = false;
        for (java.lang.reflect.Method m : cls.getDeclaredMethods()) {
            String n = m.getName().toLowerCase();
            if (n.contains("bind") || n.contains("truncate") || n.contains("ellips")) { hasOnBindOrTruncate = true; break; }
        }
        assertTrue("ManageImAdapter should have bind/truncate logic", hasOnBindOrTruncate);

        boolean hasClick = false;
        for (java.lang.reflect.Method m : cls.getMethods()) {
            if (m.getName().toLowerCase().contains("click") || m.getName().toLowerCase().contains("listener")) { hasClick = true; break; }
        }
        assertTrue("ManageImAdapter should expose click/listener handling", hasClick);
    }
}
