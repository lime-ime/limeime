package org.limeime.ui.view;

import android.app.Activity;
import android.os.Build;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.core.widget.NestedScrollView;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.limeime.R;

public final class ScrollableTabHelper {

    private static final int SCROLLBAR_SIZE_DP = 6;

    private ScrollableTabHelper() {
    }

    public static void applyToNestedScrollView(@Nullable Activity activity,
                                               @NonNull NestedScrollView scrollView) {
        applyBottomNavInset(activity, scrollView);
        applySafeScrollbarDrawables(scrollView);
        scrollView.setScrollBarStyle(View.SCROLLBARS_INSIDE_OVERLAY);
        scrollView.setVerticalScrollBarEnabled(false);
        scrollView.setScrollbarFadingEnabled(true);
        installOverflowCheck(scrollView, () -> {
            boolean canScroll = false;
            if (scrollView.getChildCount() > 0) {
                View child = scrollView.getChildAt(0);
                int viewportHeight = scrollView.getHeight()
                        - scrollView.getPaddingTop()
                        - scrollView.getPaddingBottom();
                canScroll = child.getHeight() > viewportHeight;
            }
            setScrollbarVisibleWhenScrollable(scrollView, canScroll);
        });
    }

    private static void applySafeScrollbarDrawables(@NonNull View view) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return;

        view.setVerticalScrollbarThumbDrawable(
                ContextCompat.getDrawable(view.getContext(), R.drawable.settings_scrollbar_thumb));
        view.setVerticalScrollbarTrackDrawable(
                ContextCompat.getDrawable(view.getContext(), R.drawable.settings_scrollbar_track));
    }

    public static void applyToRecyclerView(@Nullable Activity activity,
                                           @NonNull RecyclerView recyclerView) {
        applyBottomNavInset(activity, recyclerView);
        recyclerView.setScrollBarStyle(View.SCROLLBARS_INSIDE_OVERLAY);
        recyclerView.setVerticalScrollBarEnabled(false);
        recyclerView.setScrollbarFadingEnabled(true);
        installOverflowCheck(recyclerView, () ->
                setScrollbarVisibleWhenScrollable(recyclerView, canRecyclerViewScroll(recyclerView)));
        recyclerView.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(@NonNull RecyclerView recyclerView, int dx, int dy) {
                setScrollbarVisibleWhenScrollable(recyclerView, canRecyclerViewScroll(recyclerView));
            }
        });
    }

    public static void refreshRecyclerViewScrollbar(@Nullable RecyclerView recyclerView) {
        if (recyclerView == null) return;
        recyclerView.post(() ->
                setScrollbarVisibleWhenScrollable(recyclerView, canRecyclerViewScroll(recyclerView)));
    }

    private static void applyBottomNavInset(@Nullable Activity activity,
                                            @NonNull View scrollable) {
        int baseLeft = scrollable.getPaddingLeft();
        int baseTop = scrollable.getPaddingTop();
        int baseRight = scrollable.getPaddingRight();
        int baseBottom = scrollable.getPaddingBottom();
        if (scrollable instanceof ViewGroup) {
            ((ViewGroup) scrollable).setClipToPadding(false);
        }

        View bottomNav = activity != null ? activity.findViewById(R.id.main_bottom_nav) : null;
        if (bottomNav == null) return;

        bottomNav.post(() -> {
            int navHeight = bottomNav.getHeight();
            if (navHeight <= 0) return;
            scrollable.setPadding(baseLeft, baseTop, baseRight, baseBottom + navHeight);
        });
    }

    private static void installOverflowCheck(@NonNull View view, @NonNull Runnable update) {
        view.addOnLayoutChangeListener((v, left, top, right, bottom,
                                        oldLeft, oldTop, oldRight, oldBottom) -> update.run());
        view.post(update);

        ViewTreeObserver observer = view.getViewTreeObserver();
        observer.addOnGlobalLayoutListener(update::run);
    }

    private static void setScrollbarVisibleWhenScrollable(@NonNull View view, boolean canScroll) {
        view.setVerticalScrollBarEnabled(canScroll);
        view.setScrollbarFadingEnabled(!canScroll);
        if (canScroll) {
            float density = view.getResources().getDisplayMetrics().density;
            view.setScrollBarSize(Math.max(1, Math.round(SCROLLBAR_SIZE_DP * density)));
        }
        view.invalidate();
    }

    private static boolean canRecyclerViewScroll(@NonNull RecyclerView recyclerView) {
        RecyclerView.Adapter<?> adapter = recyclerView.getAdapter();
        if (adapter == null || adapter.getItemCount() == 0) return false;

        RecyclerView.LayoutManager layoutManager = recyclerView.getLayoutManager();
        if (layoutManager instanceof LinearLayoutManager) {
            if (adapter.getItemCount() > recyclerView.getChildCount()) {
                return true;
            }
            LinearLayoutManager linearLayoutManager = (LinearLayoutManager) layoutManager;
            return linearLayoutManager.findFirstCompletelyVisibleItemPosition() > 0
                    || linearLayoutManager.findLastCompletelyVisibleItemPosition()
                    < adapter.getItemCount() - 1;
        }

        return recyclerView.canScrollVertically(1) || recyclerView.canScrollVertically(-1);
    }
}
