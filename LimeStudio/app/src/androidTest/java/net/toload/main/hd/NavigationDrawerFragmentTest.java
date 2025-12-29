/*
 * Copyright 2025, The LimeIME Open Source Project
 */
package net.toload.main.hd;

import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import net.toload.main.hd.ui.MainActivity;
import net.toload.main.hd.ui.NavigationManager;

import org.junit.Test;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;

/**
 * Tests for NavigationDrawerFragment & NavigationManager callbacks.
 */
@RunWith(AndroidJUnit4.class)
public class NavigationDrawerFragmentTest {

    @Test
    public void testNavigationManagerAvailableFromActivity() {
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            scenario.onActivity(activity -> {
                NavigationManager navManager = activity.getNavigationManager();
                assertNotNull("NavigationManager should be available", navManager);
            });
        }
    }

    @Test
    public void testNavigationManagerImplementsCallbacks() {
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            scenario.onActivity(activity -> {
                NavigationManager navManager = activity.getNavigationManager();
                Class<?>[] interfaces = navManager.getClass().getInterfaces();
                boolean implementsCallbacks = false;
                for (Class<?> iface : interfaces) {
                    if (iface.getSimpleName().contains("NavigationDrawerCallbacks")) {
                        implementsCallbacks = true;
                        break;
                    }
                }
                assertTrue("NavigationManager should implement NavigationDrawerCallbacks", implementsCallbacks);
            });
        }
    }

    @Test
    public void testNavigationManagerSetImListAndSelectionStateApis() throws Exception {
        Class<?> mgrClass = Class.forName("net.toload.main.hd.ui.NavigationManager");
        boolean hasSetImList = false, hasSelectionState = false;
        for (java.lang.reflect.Method m : mgrClass.getMethods()) {
            String n = m.getName().toLowerCase();
            if (n.contains("setimlist")) hasSetImList = true;
            if (n.contains("setselected") || n.contains("getselected") || n.contains("state")) hasSelectionState = true;
        }
        assertTrue("NavigationManager.setImList (or equivalent) present", hasSetImList);
        assertTrue("NavigationManager selection state APIs present", hasSelectionState);

        // Navigation menu item rendering support (inner class/type)
        boolean hasMenuItemType = false;
        for (Class<?> inner : mgrClass.getDeclaredClasses()) {
            if (inner.getSimpleName().toLowerCase().contains("navigationmenuitem")) { hasMenuItemType = true; break; }
        }
        assertTrue("NavigationManager should define a NavigationMenuItem type", hasMenuItemType);
    }

    @Test
    public void testNavigationManagerNavigatesAndPersistsSelection() {
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            scenario.onActivity(activity -> {
                NavigationManager navManager = activity.getNavigationManager();
                net.toload.main.hd.data.Im im = new net.toload.main.hd.data.Im();
                im.setCode("phonetic");
                im.setDesc("Phonetic");
                java.util.List<net.toload.main.hd.data.Im> list = new java.util.ArrayList<>();
                list.add(im);
                navManager.setImList(list);

                navManager.navigateToFragment(2);
                activity.getSupportFragmentManager().executePendingTransactions();

                androidx.fragment.app.Fragment fragment = activity.getSupportFragmentManager()
                        .findFragmentByTag("ManageImFragment_phonetic");
                assertNotNull("ManageImFragment should be shown for IM index 0", fragment);
                assertEquals("Phonetic", navManager.getCurrentTitle());
            });

            scenario.recreate();
            scenario.onActivity(activity -> {
                androidx.fragment.app.Fragment fragment = activity.getSupportFragmentManager()
                        .findFragmentByTag("ManageImFragment_phonetic");
                assertNotNull("ManageImFragment should survive recreation", fragment);
            });
        }
    }
}
