package org.limeime.ui.view;

import android.app.Activity;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.preference.PreferenceFragmentCompat;

import com.google.android.material.appbar.MaterialToolbar;

import org.limeime.R;
import org.limeime.SearchServer;
import org.limeime.ui.LIMEPreference;
import org.limeime.ui.LIMESettings;

public class LimePreferenceFragment extends Fragment {

    private MaterialToolbar toolbar;
    private CharSequence rootTitle;
    private OnBackPressedCallback backCallback;

    public static LimePreferenceFragment newInstance() {
        return new LimePreferenceFragment();
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_lime_preference_host, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        toolbar = view.findViewById(R.id.lime_preference_toolbar);
        rootTitle = toolbar.getTitle();

        // Route both the toolbar back-chevron AND the system Back button through
        // a single OnBackPressedCallback. This avoids the first-tap race where a
        // freshly-attached MaterialToolbar navigation button isn't ready to receive
        // touch events. The dispatcher hands the click off to the Activity's main
        // back-press machinery, which is always live.
        backCallback = new OnBackPressedCallback(false) {
            @Override
            public void handleOnBackPressed() {
                getChildFragmentManager().popBackStackImmediate();
            }
        };
        requireActivity().getOnBackPressedDispatcher().addCallback(getViewLifecycleOwner(), backCallback);

        // Direct pop on toolbar back-chevron click — no dispatcher relay.
        // Bypasses any other OnBackPressedCallbacks that may be registered higher
        // up the dispatcher chain, and removes one layer of indirection where the
        // first tap can be lost.
        toolbar.setNavigationContentDescription("Back");
        toolbar.setNavigationOnClickListener(v -> {
            android.util.Log.d("LimePrefBack",
                    "nav-icon click: depth=" + getChildFragmentManager().getBackStackEntryCount()
                    + " icon=" + (toolbar.getNavigationIcon() != null)
                    + " viewAttached=" + toolbar.isAttachedToWindow()
                    + " enabled=" + toolbar.isEnabled());
            // Post the pop to the next frame so it runs AFTER the click event
            // finishes dispatching. popBackStackImmediate from inside a click
            // listener can re-enter the FragmentManager while it's still settling
            // the previous forward-nav transaction, causing the first pop to silently
            // no-op.
            v.post(() -> {
                int d = getChildFragmentManager().getBackStackEntryCount();
                android.util.Log.d("LimePrefBack", "posted pop attempt: depth=" + d);
                if (d > 0) {
                    boolean popped = getChildFragmentManager().popBackStackImmediate();
                    android.util.Log.d("LimePrefBack", "popBackStackImmediate returned=" + popped);
                }
            });
        });

        if (savedInstanceState == null) {
            getChildFragmentManager().beginTransaction()
                    .replace(R.id.lime_preference_host, new LIMEPreference.PrefsFragment())
                    .commit();
        }

        // Update toolbar icon + title (and enable/disable the back callback)
        // whenever the child back-stack changes — pushing into a nested screen
        // or popping back to the root. Defer the sync via view.post(...) so it
        // runs AFTER the FragmentManager has finished its transaction (the
        // listener can fire mid-transaction with the new fragment not yet laid
        // out, leaving the toolbar's navigation button in a non-clickable state).
        getChildFragmentManager().addOnBackStackChangedListener(() -> {
            View v = getView();
            if (v != null) {
                v.post(this::syncToolbarToBackStack);
            } else {
                syncToolbarToBackStack();
            }
        });
        syncToolbarToBackStack();
    }

    /** Push toolbar state to match the currently-visible nested screen depth. */
    public void syncToolbarToBackStack() {
        if (toolbar == null) return;
        int depth = getChildFragmentManager().getBackStackEntryCount();
        if (backCallback != null) {
            backCallback.setEnabled(depth > 0);
        }
        if (depth == 0) {
            toolbar.setTitle(rootTitle);
            toolbar.setNavigationIcon(null);
            return;
        }
        toolbar.setNavigationIcon(R.drawable.ic_arrow_back);
        // Pull the sub-screen title from the currently hosted PreferenceFragmentCompat.
        Fragment top = getChildFragmentManager().findFragmentById(R.id.lime_preference_host);
        if (top instanceof PreferenceFragmentCompat) {
            PreferenceFragmentCompat pf = (PreferenceFragmentCompat) top;
            if (pf.getPreferenceScreen() != null && pf.getPreferenceScreen().getTitle() != null) {
                toolbar.setTitle(pf.getPreferenceScreen().getTitle());
            }
        }
    }

    @Override
    public void onPause() {
        super.onPause();
        // Mirror LIMEPreference.onPause() — flush the cache when leaving prefs
        Activity act = getActivity();
        if (act instanceof LIMESettings) {
            org.limeime.ui.controller.ManageImController ctrl =
                    ((LIMESettings) act).getManageImController();
            if (ctrl != null) {
                SearchServer ss = ctrl.getSearchServer();
                if (ss != null) ss.initialCache();
            }
        }
    }
}
