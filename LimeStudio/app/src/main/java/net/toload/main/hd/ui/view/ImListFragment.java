package net.toload.main.hd.ui.view;

import android.animation.Animator;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.app.Activity;
import android.os.Bundle;
import android.provider.Settings;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.LinearInterpolator;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.switchmaterial.SwitchMaterial;

import net.toload.main.hd.R;
import net.toload.main.hd.data.ImConfig;
import net.toload.main.hd.global.LIME;
import net.toload.main.hd.ui.LIMESettings;
import net.toload.main.hd.ui.controller.ManageImController;
import net.toload.main.hd.ui.viewmodel.ImNavigationViewModel;

import java.util.ArrayList;
import java.util.List;

/**
 * Fragment showing the list of available IMs with enable/disable toggles.
 * Hosted inside TwoPaneHostFragment's list pane.
 */
public class ImListFragment extends Fragment {

    private static final String TAG = "ImListFragment";

    private Activity activity;
    private ManageImController manageImController;
    private ImNavigationViewModel vm;
    private ImRowAdapter adapter;

    // Empty-IM-list FAB nudge (§5.1.1): a bobbing callout pill + two radar-pulse
    // rings + a breathing FAB, shown only while no IM is installed. Mirrors the
    // lime-settings-android im-empty-state-demo.html.
    private FloatingActionButton fabInstall;
    private View nudgeRing1;
    private View nudgeRing2;
    private View nudgeCallout;
    private AnimatorSet nudgeAnimators;
    private boolean nudgeActive;

    public static ImListFragment newInstance() {
        return new ImListFragment();
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        activity = getActivity();

        if (activity instanceof LIMESettings) {
            manageImController = ((LIMESettings) activity).getManageImController();
        } else {
            Log.w(TAG, "Activity is not LIMESettings; ManageImController unavailable");
        }

        // ViewModel is scoped to TwoPaneHostFragment (parent)
        vm = new ViewModelProvider(requireParentFragment()).get(ImNavigationViewModel.class);

        View rootView = inflater.inflate(R.layout.fragment_im_list, container, false);

        FloatingActionButton fab = rootView.findViewById(R.id.fab_install);
        fab.setOnClickListener(v -> vm.showInstall.setValue(true));

        // Empty-state FAB-nudge views (hidden until the installed list is empty).
        fabInstall = fab;
        nudgeRing1 = rootView.findViewById(R.id.nudge_ring_1);
        nudgeRing2 = rootView.findViewById(R.id.nudge_ring_2);
        nudgeCallout = rootView.findViewById(R.id.nudge_callout);

        // Push FAB above the activity's BottomNavigationView (fragment container fills full screen).
        // The nudge rings sit at the FAB's position; the callout floats one FAB-height + gap above
        // it so the caret points down at the FAB. All must track the same bottom-nav offset, else
        // the overlays land lower than the lifted FAB and overlap it.
        View bottomNav = requireActivity().findViewById(R.id.main_bottom_nav);
        if (bottomNav != null) {
            bottomNav.post(() -> {
                if (!isAdded()) return;
                int navHeight = bottomNav.getHeight();
                if (navHeight <= 0) return;
                float density = getResources().getDisplayMetrics().density;
                int fabBottom = navHeight + (int) (16 * density);
                setBottomMargin(fab, fabBottom);
                // Rings align with the FAB (same bottom margin → concentric).
                setBottomMargin(nudgeRing1, fabBottom);
                setBottomMargin(nudgeRing2, fabBottom);
                // Callout floats above the 56dp FAB with a 12dp gap.
                setBottomMargin(nudgeCallout, fabBottom + (int) ((56 + 12) * density));
            });
        }

        RecyclerView recyclerView = rootView.findViewById(R.id.im_list_recycler);
        recyclerView.setLayoutManager(new LinearLayoutManager(requireContext()));
        ScrollableTabHelper.applyToRecyclerView(activity, recyclerView);

        adapter = new ImRowAdapter(new ArrayList<>());
        recyclerView.setAdapter(adapter);

        loadImList();

        return rootView;
    }

    /** Re-query the IM config table and refresh the list. Safe to call from any thread. */
    public void refreshList() {
        loadImList();
    }

    private void loadImList() {
        final ManageImController ctrl = manageImController;
        if (ctrl == null) return;
        final Activity act = activity;

        new Thread(() -> {
            final List<ImConfig> rawList = ctrl.getImConfigFullNameList();
            // Filter out the internal emoji dataset — it is not a user-facing Chinese IM
            final List<ImConfig> list = new ArrayList<>();
            for (ImConfig im : rawList) {
                if (!"emoji".equals(im.getCode())) {
                    list.add(im);
                }
            }
            if (act == null) return;
            act.runOnUiThread(() -> {
                if (!isAdded() || activity == null) return;
                adapter.setData(list);
            });
        }).start();
    }

    private static void setBottomMargin(View v, int marginPx) {
        if (v == null || !(v.getLayoutParams() instanceof ViewGroup.MarginLayoutParams)) return;
        ViewGroup.MarginLayoutParams lp = (ViewGroup.MarginLayoutParams) v.getLayoutParams();
        lp.bottomMargin = marginPx;
        v.setLayoutParams(lp);
    }

    // -------- Empty-list FAB nudge (§5.1.1) --------

    /**
     * Show or hide the FAB nudge (callout pill + radar-pulse rings + breathing
     * FAB) based on whether the installed list is empty. The empty-state
     * placeholder itself is rendered by the adapter; this only drives the FAB
     * affordance. Honours the system "Remove animations" setting by holding a
     * static resting state instead of running the loops. Mirrors the
     * lime-settings-android im-empty-state-demo.html.
     */
    private void updateEmptyNudge(boolean empty) {
        if (nudgeCallout == null || nudgeRing1 == null || nudgeRing2 == null || fabInstall == null) {
            return;
        }
        if (empty == nudgeActive && empty) {
            return; // already running for the empty state
        }
        nudgeActive = empty;

        int vis = empty ? View.VISIBLE : View.GONE;
        nudgeCallout.setVisibility(vis);
        nudgeRing1.setVisibility(vis);
        nudgeRing2.setVisibility(vis);

        stopNudgeAnimators();
        if (!empty) {
            // Reset the FAB to its resting state when leaving the empty state.
            fabInstall.setScaleX(1f);
            fabInstall.setScaleY(1f);
            return;
        }

        if (isReduceMotionEnabled()) {
            // Static fallback: a faint, mid-expansion ring halo, no looping.
            applyRingReducedMotion(nudgeRing1);
            applyRingReducedMotion(nudgeRing2);
            fabInstall.setScaleX(1f);
            fabInstall.setScaleY(1f);
            return;
        }

        startNudgeAnimators();
    }

    private boolean isReduceMotionEnabled() {
        if (activity == null) return false;
        try {
            // Animator duration scale 0 ⇒ user has disabled animations system-wide.
            float scale = Settings.Global.getFloat(
                    activity.getContentResolver(),
                    Settings.Global.ANIMATOR_DURATION_SCALE, 1f);
            return scale == 0f;
        } catch (Exception e) {
            return false;
        }
    }

    private static void applyRingReducedMotion(View ring) {
        ring.setScaleX(1.9f);
        ring.setScaleY(1.9f);
        ring.setAlpha(0.18f);
    }

    /** A single expanding/fading radar ring (scale 1 → 2.6, alpha .45 → 0). */
    private Animator buildRingAnimator(View ring, long startDelay) {
        ring.setPivotX(ring.getLayoutParams().width / 2f);
        ring.setPivotY(ring.getLayoutParams().height / 2f);
        ValueAnimator a = ValueAnimator.ofFloat(0f, 1f);
        a.setDuration(2400);
        a.setStartDelay(startDelay);
        a.setRepeatCount(ValueAnimator.INFINITE);
        a.setInterpolator(new LinearInterpolator());
        a.addUpdateListener(anim -> {
            float t = (float) anim.getAnimatedValue();
            float scale = 1f + t * (2.6f - 1f);
            ring.setScaleX(scale);
            ring.setScaleY(scale);
            // Fade out over the first 70% of the cycle, then hold transparent.
            float alpha = t < 0.7f ? (0.45f * (1f - t / 0.7f)) : 0f;
            ring.setAlpha(alpha);
        });
        return a;
    }

    /** Gentle "breath" on the FAB so it reads as the nudge target. */
    private Animator buildFabBreathAnimator() {
        ObjectAnimator sx = ObjectAnimator.ofFloat(fabInstall, View.SCALE_X, 1f, 1.08f);
        ObjectAnimator sy = ObjectAnimator.ofFloat(fabInstall, View.SCALE_Y, 1f, 1.08f);
        for (ObjectAnimator o : new ObjectAnimator[]{sx, sy}) {
            o.setDuration(1200);
            o.setRepeatCount(ValueAnimator.INFINITE);
            o.setRepeatMode(ValueAnimator.REVERSE);
        }
        AnimatorSet set = new AnimatorSet();
        set.playTogether(sx, sy);
        return set;
    }

    /** Callout pill bobs toward the FAB (translateY 0 → 4dp). */
    private Animator buildCalloutBobAnimator() {
        float dy = 4f * (activity != null ? activity.getResources().getDisplayMetrics().density : 2f);
        ObjectAnimator a = ObjectAnimator.ofFloat(nudgeCallout, View.TRANSLATION_Y, 0f, dy);
        a.setDuration(900);
        a.setRepeatCount(ValueAnimator.INFINITE);
        a.setRepeatMode(ValueAnimator.REVERSE);
        return a;
    }

    private void startNudgeAnimators() {
        AnimatorSet set = new AnimatorSet();
        set.playTogether(
                buildRingAnimator(nudgeRing1, 0L),
                buildRingAnimator(nudgeRing2, 1200L),
                buildFabBreathAnimator(),
                buildCalloutBobAnimator());
        nudgeAnimators = set;
        set.start();
    }

    private void stopNudgeAnimators() {
        if (nudgeAnimators != null) {
            nudgeAnimators.cancel();
            nudgeAnimators = null;
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        // Refresh the list when returning from IM Detail (e.g., after Remove-IM)
        if (manageImController != null && adapter != null) {
            loadImList();
        }
    }

    @Override
    public void onDestroyView() {
        View root = getView();
        if (root != null) {
            RecyclerView rv = root.findViewById(R.id.im_list_recycler);
            if (rv != null) {
                rv.setAdapter(null);
            }
        }
        stopNudgeAnimators();
        super.onDestroyView();
        activity = null;
        manageImController = null;
        vm = null;
        adapter = null;
        fabInstall = null;
        nudgeRing1 = null;
        nudgeRing2 = null;
        nudgeCallout = null;
        nudgeActive = false;
    }

    /**
     * Returns true if {@code activeImCode} corresponds to an IM that is currently
     * enabled (not disabled) in the in-memory list. Used to decide whether a newly
     * enabled IM should become the active IM. Reads the adapter's in-memory list so
     * it is not subject to the async DB write performed by setImEnabled().
     */
    private boolean isActiveImEnabled(String activeImCode) {
        if (activeImCode == null || adapter == null) return false;
        List<ImConfig> list = adapter.getImList();
        if (list == null) return false;
        for (ImConfig im : list) {
            if (im == null || im.getCode() == null) continue;
            if (activeImCode.equals(im.getCode())) {
                return !im.isDisable();
            }
        }
        return false;
    }

    // -------- Adapter --------

    private class ImRowAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

        private static final int TYPE_IM = 0;
        private static final int TYPE_RELATED = 1;
        private static final int TYPE_HEADER = 2;
        private static final int TYPE_EMPTY = 3;

        private List<ImConfig> imList;

        ImRowAdapter(List<ImConfig> imList) {
            this.imList = imList;
        }

        List<ImConfig> getImList() {
            return imList;
        }

        boolean isEmptyInstalled() {
            return imList.isEmpty();
        }

        void setData(List<ImConfig> data) {
            this.imList = data != null ? data : new ArrayList<>();
            notifyDataSetChanged();
            View root = getView();
            if (root != null) {
                ScrollableTabHelper.refreshRecyclerViewScrollbar(root.findViewById(R.id.im_list_recycler));
            }
            // Empty installed list → swap in the placeholder and run the FAB nudge.
            updateEmptyNudge(imList.isEmpty());
        }

        // When no IM is installed, a single empty-state placeholder stands in for
        // the IM rows (the 關聯字庫 section still follows). Otherwise one row per IM.
        private int installedItemCount() {
            return imList.isEmpty() ? 1 : imList.size();
        }

        @Override
        public int getItemCount() {
            // header(installed) + (placeholder | IM rows) + header(related) + related row
            return 1 + installedItemCount() + 1 + 1;
        }

        @Override
        public int getItemViewType(int position) {
            if (position == 0) return TYPE_HEADER; // installed header
            int imEnd = 1 + installedItemCount();
            if (position < imEnd) return imList.isEmpty() ? TYPE_EMPTY : TYPE_IM;
            if (position == imEnd) return TYPE_HEADER; // related header
            return TYPE_RELATED;
        }

        @NonNull
        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            if (viewType == TYPE_HEADER) {
                android.widget.TextView tv = new android.widget.TextView(parent.getContext());
                tv.setPadding(32, 24, 32, 8);
                tv.setTypeface(null, android.graphics.Typeface.BOLD);
                tv.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 13);
                // Section headers use the accent colour (matches the demo's
                // --md-primary header label). §5.1.
                tv.setTextColor(resolveColorAttr(parent.getContext(),
                        com.google.android.material.R.attr.colorPrimary, 0xFF2196F3));
                tv.setLayoutParams(new android.view.ViewGroup.LayoutParams(
                        android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                        android.view.ViewGroup.LayoutParams.WRAP_CONTENT));
                return new HeaderViewHolder(tv);
            }
            if (viewType == TYPE_EMPTY) {
                View ev = LayoutInflater.from(parent.getContext())
                        .inflate(R.layout.item_im_empty_state, parent, false);
                return new EmptyViewHolder(ev);
            }
            View v = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_im_row, parent, false);
            if (viewType == TYPE_RELATED) {
                return new RelatedViewHolder(v);
            }
            return new ImViewHolder(v);
        }

        @Override
        public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
            if (holder instanceof HeaderViewHolder) {
                int labelRes = (position == 0) ? R.string.im_list_header_installed : R.string.im_list_header_related;
                ((HeaderViewHolder) holder).bind(labelRes);
            } else if (holder instanceof RelatedViewHolder) {
                ((RelatedViewHolder) holder).bind();
            } else if (holder instanceof EmptyViewHolder) {
                // Static placeholder; nothing to bind.
            } else if (holder instanceof ImViewHolder) {
                // position 0 is header, so IM data starts at position 1
                ((ImViewHolder) holder).bind(imList.get(position - 1));
            }
        }
    }

    /** Static empty-state placeholder shown when no IM is installed (§5.1.1). */
    private static class EmptyViewHolder extends RecyclerView.ViewHolder {
        EmptyViewHolder(@NonNull View itemView) {
            super(itemView);
            // Tile container colour comes from @color/im_empty_mark_background
            // (day/night aware) via the drawable — nothing to do at bind time.
        }
    }

    private static int resolveColorAttr(android.content.Context ctx, int attr, int fallback) {
        android.util.TypedValue tv = new android.util.TypedValue();
        if (ctx.getTheme().resolveAttribute(attr, tv, true)) {
            return tv.data != 0 ? tv.data : fallback;
        }
        return fallback;
    }

    private class HeaderViewHolder extends RecyclerView.ViewHolder {
        final android.widget.TextView tvHeader;

        HeaderViewHolder(@NonNull android.widget.TextView itemView) {
            super(itemView);
            tvHeader = itemView;
        }

        void bind(int labelRes) {
            tvHeader.setText(labelRes);
            tvHeader.setClickable(false);
        }
    }

    /**
     * The representative character shown in an installed IM's grey badge. The
     * rule is the <b>first character of the IM name</b>, with curated exceptions:
     *   注音 → ㄅ; 大易 → 易 (not 大); 倉頡-family (倉頡 / 四碼倉頡 / 倉頡五代 / 快倉) → 倉;
     *   行列10 → 10 (not 行). Keyed by tableNick (code); unknown tables fall back
     *   to the first name character. Identical to iOS. Spec §5.1.
     */
    private static String representativeCharacter(ImConfig im) {
        String code = im.getCode();
        if (code != null) {
            switch (code) {
                case "phonetic": return "ㄅ";
                case "cj":       // 倉頡
                case "cj4":      // 四碼倉頡
                case "cj5":      // 倉頡五代
                case "scj":      return "倉"; // 快倉
                case "dayi":     return "易"; // 大易 → 易 (not 大)
                case "array10":  return "10"; // 行列10 → 10 (not 行)
            }
        }
        String desc = im.getDesc();
        if (desc == null || desc.isEmpty()) {
            return "?";
        }
        return desc.substring(0, 1);
    }

    private class ImViewHolder extends RecyclerView.ViewHolder {
        final TextView tvBadge;
        final ImageView ivBadge;
        final TextView tvLabel;
        final SwitchMaterial switchEnabled;

        ImViewHolder(@NonNull View itemView) {
            super(itemView);
            tvBadge = itemView.findViewById(R.id.tv_im_badge);
            ivBadge = itemView.findViewById(R.id.iv_im_badge);
            tvLabel = itemView.findViewById(R.id.tv_im_label);
            switchEnabled = itemView.findViewById(R.id.switch_im_enabled);
        }

        void bind(ImConfig im) {
            tvLabel.setText(im.getDesc());
            // Character badge: show the glyph, hide the icon overlay.
            ivBadge.setVisibility(View.GONE);
            tvBadge.setVisibility(View.VISIBLE);
            tvBadge.setText(representativeCharacter(im));
            itemView.setAlpha(im.isDisable() ? LIME.HALF_ALPHA_VALUE : 1.0f);

            // Clear listener before setting state to avoid spurious callbacks
            switchEnabled.setOnCheckedChangeListener(null);
            switchEnabled.setChecked(!im.isDisable());
            switchEnabled.setOnCheckedChangeListener((btn, checked) -> {
                im.setDisable(!checked);
                itemView.setAlpha(checked ? 1.0f : LIME.HALF_ALPHA_VALUE);
                ManageImController ctrl = manageImController;
                if (ctrl != null) {
                    ctrl.setImEnabled(im.getId(), checked);
                    net.toload.main.hd.global.LIMEPreferenceManager pref =
                            new net.toload.main.hd.global.LIMEPreferenceManager(requireContext());
                    pref.syncIMActivatedState(ctrl.getImConfigFullNameList());
                    // When enabling an IM, make it the active IM if the currently
                    // persisted active IM is not (or no longer) an enabled one. This
                    // ensures the first IM installed/enabled on a fresh install becomes
                    // active instead of leaving activeIM pointing at a default IM whose
                    // keyboard config is not loaded (which falls back to the English
                    // keyboard). Uses the adapter's in-memory list to stay race-free
                    // against the async DB write in setImEnabled().
                    if (checked && !isActiveImEnabled(pref.getActiveIM())) {
                        pref.setActiveIM(im.getCode());
                    }
                }
            });

            itemView.setOnClickListener(v -> {
                ImNavigationViewModel vmRef = vm;
                if (vmRef != null) {
                    vmRef.selectedIm.setValue(im);
                }
            });
        }
    }

    private class RelatedViewHolder extends RecyclerView.ViewHolder {
        final TextView tvBadge;
        final ImageView ivBadge;
        final TextView tvLabel;
        final SwitchMaterial switchEnabled;

        RelatedViewHolder(@NonNull View itemView) {
            super(itemView);
            tvBadge = itemView.findViewById(R.id.tv_im_badge);
            ivBadge = itemView.findViewById(R.id.iv_im_badge);
            tvLabel = itemView.findViewById(R.id.tv_im_label);
            switchEnabled = itemView.findViewById(R.id.switch_im_enabled);
        }

        void bind() {
            tvLabel.setText(R.string.im_related_label);
            // 關聯字庫 shows the same grey tile with a chat glyph (iOS parity).
            tvBadge.setVisibility(View.GONE);
            ivBadge.setVisibility(View.VISIBLE);
            ivBadge.setImageResource(R.drawable.ic_chat_24);
            switchEnabled.setVisibility(View.GONE);
            itemView.setAlpha(1.0f);

            itemView.setOnClickListener(v -> {
                Fragment parent = getParentFragment();
                if (parent instanceof TwoPaneHostFragment) {
                    ImConfig synthetic = new ImConfig();
                    synthetic.setId(-1);
                    synthetic.setCode("related");
                    synthetic.setDesc(itemView.getResources().getString(R.string.im_related_heading));
                    ((TwoPaneHostFragment) parent).navigateToDetail(
                            ImDetailFragment.newInstance(synthetic));
                }
            });
        }
    }
}
