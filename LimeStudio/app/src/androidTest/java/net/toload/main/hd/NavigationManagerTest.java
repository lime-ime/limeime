package net.toload.main.hd;

import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.core.app.ActivityScenario;

import net.toload.main.hd.ui.MainActivity;
import net.toload.main.hd.ui.NavigationManager;
import net.toload.main.hd.ui.view.ManageRelatedFragment;
import net.toload.main.hd.ui.view.SetupImFragment;

import org.junit.Test;
import org.junit.runner.RunWith;

import static org.junit.Assert.assertNotNull;

/**
 * Smoke tests for NavigationManager-driven fragment navigation.
 */
@RunWith(AndroidJUnit4.class)
public class NavigationManagerTest {

    @Test
    public void navigateToSetupAndRelatedFragments_doesNotCrash() {
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            scenario.onActivity(activity -> {
                NavigationManager navigationManager = activity.getNavigationManager();
                FragmentManager fm = activity.getSupportFragmentManager();

                // Navigate to Setup (position 0)
                navigationManager.navigateToFragment(0);
                fm.executePendingTransactions();
                Fragment setup = fm.findFragmentByTag("SetupImFragment");
                assertNotNull("SetupImFragment should be present", setup);

                // Navigate to Related (position 1)
                navigationManager.navigateToFragment(1);
                fm.executePendingTransactions();
                Fragment related = fm.findFragmentByTag("ManageRelatedFragment");
                assertNotNull("ManageRelatedFragment should be present", related);
            });
        }
    }
}
