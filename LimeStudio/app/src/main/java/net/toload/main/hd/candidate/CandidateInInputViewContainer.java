/*
 *
 *  *
 *  **    Copyright 2025, The LimeIME Open Source Project
 *  **
 *  **    Project Url: http://github.com/lime-ime/limeime/
 *  **                 http://android.toload.net/
 *  **
 *  **    This program is free software: you can redistribute it and/or modify
 *  **    it under the terms of the GNU General Public License as published by
 *  **    the Free Software Foundation, either version 3 of the License, or
 *  **    (at your option) any later version.
 *  *
 *  **    This program is distributed in the hope that it will be useful,
 *  **    but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  **    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  **    GNU General Public License for more details.
 *  *
 *  **    You should have received a copy of the GNU General Public License
 *  **    along with this program.  If not, see <http://www.gnu.org/licenses/>.
 *  *
 *
 */

package net.toload.main.hd.candidate;

import android.content.Context;
import android.graphics.Color;
import android.util.AttributeSet;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.LinearLayout;

import net.toload.main.hd.LIMEService;
import net.toload.main.hd.R;
import net.toload.main.hd.global.LIME;

public class CandidateInInputViewContainer extends LinearLayout  implements View.OnClickListener {

    private static final boolean DEBUG = false;
    private static final String TAG = "CandiInputViewContainer";
    private ImageButton mDismissButton;
    private ImageButton mEmojiButton;
    private ImageButton mRightButton;
    private ImageButton mKeyboardButton;
    private View mActionRow;
    private View mRightButtonParent;
    private CandidateView mCandidateView;
    private LIMEService mService;
    private static final long IDLE_TOOLS_REVEAL_DELAY_MS = 120L;
    private boolean mIdleToolsRevealReady = true;
    private final Runnable mRevealIdleToolsRunnable = new Runnable() {
        @Override
        public void run() {
            mIdleToolsRevealReady = true;
            requestLayout();
            updateCandidateViewWidthConstraint();
        }
    };

    Context ctx;

    public CandidateInInputViewContainer(Context context, AttributeSet attrs) {
        super(context, attrs);
        if (DEBUG)
            Log.i(TAG, "CandidateInInputViewContainer() constructor");

        ctx = context;
        
        // Allow popup to extend beyond container bounds
        setClipChildren(false);
        setClipToPadding(false);
    }

    public void initViews() {
        if (DEBUG)
            Log.i(TAG, "initViews()");
        if (mCandidateView == null) {
            View mButtonRightExpand = findViewById(R.id.candidate_right_parent);
            mRightButtonParent = mButtonRightExpand;
            // Ensure buttons are laid out in correct order
            if (mButtonRightExpand instanceof ViewGroup) {
                android.view.ViewGroup vg = (android.view.ViewGroup) mButtonRightExpand;
                vg.setClipChildren(false);
                vg.setClipToPadding(false);
            }
            mDismissButton = findViewById(R.id.candidate_dismiss);
            mEmojiButton = findViewById(R.id.candidate_emoji);
            mRightButton = findViewById(R.id.candidate_right);
            mKeyboardButton = findViewById(R.id.candidate_keyboard);
            if (mEmojiButton != null && mEmojiButton.getParent() instanceof View) {
                mActionRow = (View) mEmojiButton.getParent();
            }

            if (mDismissButton != null) {
                mDismissButton.setOnClickListener(this);
            }
            if (mEmojiButton != null) {
                mEmojiButton.setOnClickListener(this);
            }
            if (mRightButton != null) {
                mRightButton.setOnClickListener(this);
            }
            if (mRightButtonParent != null) {
                mRightButtonParent.setOnClickListener(this);
            }
            if (mKeyboardButton != null) {
                mKeyboardButton.setOnClickListener(this);
                if (DEBUG) {
                    Log.i(TAG, "Keyboard button initialized: " + mKeyboardButton);
                }
            } else {
                if (DEBUG) {
                    Log.w(TAG, "Keyboard button not found!");
                }
            }
            mCandidateView = findViewById(R.id.candidatesView);
            //View mKeyboardView = findViewById(R.id.keyboard);

            assert mCandidateView != null;
            mCandidateView.setBackgroundColor(mCandidateView.mColorBackground);
            if (mActionRow != null) {
                mActionRow.setBackgroundColor(actionRowBackgroundColor(mCandidateView.mColorBackground));
            }
            if (mRightButtonParent != null) {
                mRightButtonParent.setBackgroundColor(actionRowBackgroundColor(mCandidateView.mColorBackground));
            }
            if (mDismissButton != null) {
                mDismissButton.setPadding(0, 0, 0, 0);
                mDismissButton.setScaleType(ImageButton.ScaleType.CENTER);
                mDismissButton.setMinimumWidth(0);
                mDismissButton.setMinimumHeight(0);
                mDismissButton.setImageDrawable(mCandidateView.makeDismissButtonGlyph());
                mDismissButton.setBackgroundColor(dismissButtonBackgroundColor());
                mDismissButton.post(() -> mCandidateView.storePopupDismissButtonWidth(mDismissButton));
            }
            if (mEmojiButton != null) {
                mEmojiButton.setPadding(0, 0, 0, 0);
                mEmojiButton.setScaleType(ImageButton.ScaleType.CENTER);
                mEmojiButton.setMinimumWidth(0);
                mEmojiButton.setMinimumHeight(0);
                mEmojiButton.clearColorFilter();
                mEmojiButton.setImageDrawable(mCandidateView.mDrawableEmojiInput);
                mEmojiButton.setBackgroundColor(actionButtonBackgroundColor());
            }
            if (mRightButton != null) {
                mRightButton.setPadding(0, 0, 0, 0);
                mRightButton.setScaleType(ImageButton.ScaleType.CENTER);
                mRightButton.setMinimumWidth(0);
                mRightButton.setMinimumHeight(0);
                mRightButton.setBackgroundColor(actionButtonBackgroundColor());
            }
            if (mKeyboardButton != null) {
                mKeyboardButton.setBackgroundColor(mCandidateView.mColorBackground);
                if (mCandidateView.mDrawableKeyboardShow != null) {
                    mKeyboardButton.setImageDrawable(mCandidateView.mDrawableKeyboardShow);
                }
            }
           
        }
    }
    
    public void setService(LIMEService service) {
        mService = service;
    }

    // @Override
    // protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
    //     // First, let children measure themselves normally
    //     super.onMeasure(widthMeasureSpec, heightMeasureSpec);
        
    //     // If CandidateView has a valid height, calculate container height
    //     if (mCandidateView != null && mCandidateView.mHeight > 0) {
    //         // Calculate container height based on keyboard visibility
    //         // Base height: CandidateView height + bottom gesture bar padding + top padding
    //         // Top padding is added when popup expands upward to keep CandidateView at the bottom
    //         // and create space above for the popup
    //         int containerHeight = mCandidateView.mHeight + getPaddingBottom() + getPaddingTop();
            
    //         // Add keyboard height if keyboard view is shown
    //         if (mKeyboardView != null && mKeyboardView.getVisibility() == View.VISIBLE) {
    //             // Measure keyboard view if not already measured
    //             if (mKeyboardView.getMeasuredHeight() == 0) {
    //                 int keyboardHeightSpec = MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED);
    //                 mKeyboardView.measure(widthMeasureSpec, keyboardHeightSpec);
    //             }
    //             containerHeight += mKeyboardView.getMeasuredHeight();
    //         }
            
    //         // Note: We don't add mPopupUpwardHeight separately because it's already accounted for
    //         // in getPaddingTop() when expanding upward. The top padding creates the space above
    //         // CandidateView for the popup, and including it in container height ensures the
    //         // container is tall enough to accommodate the popup.
            
    //         if (DEBUG) {
    //             Log.i(TAG, "onMeasure(): containerHeight=" + containerHeight
    //                     + ", paddingTop=" + getPaddingTop()
    //                     + ", paddingBottom=" + getPaddingBottom()
    //                     + ", popupExpandingUpward=" + mPopupExpandingUpward);
    //         }
            
    //         // Set measured dimension with exact height
    //         setMeasuredDimension(getMeasuredWidth(), containerHeight);
    //     }
    // }

    @Override
    public void requestLayout() {
        if (DEBUG) Log.i(TAG, "requestLayout()");
        
        // Update button visibility and layout
        if (mCandidateView != null) {
            boolean showKeyboardButton = (mService != null) && mService.isKeyboardViewHidden();
            boolean isEmpty = mCandidateView.isEmpty();
            boolean showIdleTools = updateIdleToolsRevealState(isEmpty);
            boolean showActiveChrome = shouldShowActiveChrome(isEmpty, showIdleTools, mIdleToolsRevealReady);
            
            if (mDismissButton != null) {
                mDismissButton.setVisibility(showActiveChrome ? View.VISIBLE : View.GONE);
            }
            if (mEmojiButton != null) {
                mEmojiButton.setVisibility(showIdleTools ? View.VISIBLE : View.GONE);
            }

            // Update keyboard button visibility
            if (mKeyboardButton != null) {
                mKeyboardButton.setVisibility(showKeyboardButton ? View.VISIBLE : View.GONE);
            }

            // When empty: show voice input button
            // When not empty: show expand button based on keyboard visibility
            if (mRightButton != null) {
                mRightButton.clearColorFilter();
                mRightButton.setVisibility((showIdleTools || showActiveChrome) ? View.VISIBLE : View.GONE);
                if (showIdleTools) {
                    mRightButton.setImageDrawable(mCandidateView.mDrawableVoiceInput);
                } else {
                    // Show up arrow when keyboard is hidden, down arrow when keyboard is shown
                    boolean isKeyboardHidden = (mService != null) && mService.isKeyboardViewHidden();
                    mRightButton.setImageDrawable(shouldShowCollapseGlyph(isEmpty, mCandidateView.isCandidateExpanded(), isKeyboardHidden) ?
                        mCandidateView.mDrawableExpandUpButton : 
                            mCandidateView.mDrawableExpandDownButton);
                }
            }
            if (mRightButtonParent != null) {
                mRightButtonParent.setVisibility((showIdleTools || showActiveChrome) ? View.VISIBLE : View.GONE);
            }
        }
        
        super.requestLayout();
    }

    static int actionRowBackgroundColor(int candidateBackground) {
        return candidateBackground;
    }

    public static int actionButtonBackgroundColor() {
        return Color.TRANSPARENT;
    }

    public static int dismissButtonBackgroundColor() {
        return Color.TRANSPARENT;
    }

    static boolean isRightActionClick(View clicked, View rightButton, View rightButtonParent) {
        return clicked == rightButton || clicked == rightButtonParent;
    }

    static boolean isRightEdgeActionTap(float x, float y, int containerWidth, int candidateRowHeight, int actionWidth) {
        return containerWidth > 0
                && candidateRowHeight > 0
                && actionWidth > 0
                && y >= 0
                && y <= candidateRowHeight
                && x >= containerWidth - actionWidth;
    }

    static boolean shouldShowCollapseGlyph(boolean isEmpty, boolean isExpanded, boolean isKeyboardHidden) {
        return !isEmpty && isExpanded && !isKeyboardHidden;
    }

    static boolean shouldShowIdleTools(boolean isEmpty, boolean idleRevealReady, boolean composingOrSearching) {
        return isEmpty && idleRevealReady && !composingOrSearching;
    }

    static boolean shouldShowActiveChrome(boolean isEmpty, boolean showIdleTools, boolean idleRevealReady) {
        return !isEmpty || (isEmpty && !showIdleTools && !idleRevealReady);
    }

    private boolean updateIdleToolsRevealState(boolean isEmpty) {
        boolean composingOrSearching = isComposingOrSearching();
        if (!isEmpty || composingOrSearching) {
            removeCallbacks(mRevealIdleToolsRunnable);
            mIdleToolsRevealReady = false;
        } else if (!mIdleToolsRevealReady) {
            removeCallbacks(mRevealIdleToolsRunnable);
            postDelayed(mRevealIdleToolsRunnable, IDLE_TOOLS_REVEAL_DELAY_MS);
        }
        return shouldShowIdleTools(isEmpty, mIdleToolsRevealReady, composingOrSearching);
    }

    private boolean isComposingOrSearching() {
        return mService != null && mService.isComposingOrSearchingCandidates();
    }
    
    /**
     * Update CandidateView width constraint to leave space for visible buttons.
     * This should be called when suggestions change (set/clear) to ensure buttons don't get pushed off-screen.
     */
    public void updateCandidateViewWidthConstraint() {
        post(() -> {
            int containerWidth = getWidth();
            if (containerWidth > 0 && mCandidateView != null) {
                boolean isEmpty = mCandidateView.isEmpty();
                boolean showIdleTools = shouldShowIdleTools(
                        isEmpty,
                        mIdleToolsRevealReady,
                        isComposingOrSearching());
                boolean showActiveChrome = shouldShowActiveChrome(isEmpty, showIdleTools, mIdleToolsRevealReady);
                if (mDismissButton != null) {
                    mDismissButton.setVisibility(showActiveChrome ? View.VISIBLE : View.GONE);
                }
                if (mEmojiButton != null) {
                    mEmojiButton.setVisibility(showIdleTools ? View.VISIBLE : View.GONE);
                }
                if (mRightButton != null) {
                    mRightButton.setVisibility((showIdleTools || showActiveChrome) ? View.VISIBLE : View.GONE);
                }
                if (mRightButtonParent != null) {
                    mRightButtonParent.setVisibility((showIdleTools || showActiveChrome) ? View.VISIBLE : View.GONE);
                }
                ViewGroup.LayoutParams params = mCandidateView.getLayoutParams();
                if (params instanceof LinearLayout.LayoutParams) {
                    LinearLayout.LayoutParams llParams = (LinearLayout.LayoutParams) params;
                    
                    // Calculate buttons width - always use dimension resource for consistency
                    int buttonsWidth = 0;
                    int buttonWidth = getResources().getDimensionPixelSize(R.dimen.candidate_expand_button_width);
                    int dismissWidth = getResources().getDimensionPixelSize(R.dimen.candidate_dismiss_button_width);
                    boolean dismissVisible = mDismissButton != null && mDismissButton.getVisibility() == View.VISIBLE;
                    boolean emojiVisible = mEmojiButton != null && mEmojiButton.getVisibility() == View.VISIBLE;
                    boolean keyboardVisible = mKeyboardButton != null && mKeyboardButton.getVisibility() == View.VISIBLE;
                    boolean rightVisible = mRightButton != null && mRightButton.getVisibility() == View.VISIBLE;
                    
                    if (DEBUG) {
                        Log.i(TAG, "Width constraint: containerWidth=" + containerWidth + 
                              ", keyboardVisible=" + keyboardVisible + 
                              ", dismissVisible=" + dismissVisible +
                              ", emojiVisible=" + emojiVisible +
                              ", rightVisible=" + rightVisible +
                              ", buttonWidth=" + buttonWidth);
                    }
                    
                    if (dismissVisible) {
                        buttonsWidth += dismissWidth;
                    }
                    if (emojiVisible) {
                        buttonsWidth += buttonWidth;
                    }
                    if (keyboardVisible) {
                        buttonsWidth += buttonWidth;
                    }
                    if (rightVisible) {
                        buttonsWidth += buttonWidth;
                    }
                    
                    int maxCandidateWidth = containerWidth - buttonsWidth;
                    if (DEBUG) {
                        Log.i(TAG, "Width constraint: buttonsWidth=" + buttonsWidth + 
                              ", maxCandidateWidth=" + maxCandidateWidth +
                              ", current width=" + llParams.width +
                              ", current weight=" + llParams.weight);
                    }
                    
                    if (maxCandidateWidth > 0) {
                        // Constrain width to leave space for buttons
                        if (llParams.width != maxCandidateWidth || llParams.weight != 0) {
                            llParams.width = maxCandidateWidth;
                            llParams.weight = 0;
                            mCandidateView.setLayoutParams(llParams);
                            if (DEBUG) {
                                Log.i(TAG, "Constrained CandidateView width to " + maxCandidateWidth);
                            }
                        }
                    } else {
                        // Fallback to weight-based layout if calculation fails
                        if (llParams.width != 0 || llParams.weight != 1.0f) {
                            llParams.width = 0;
                            llParams.weight = 1.0f;
                            mCandidateView.setLayoutParams(llParams);
                            if (DEBUG) {
                                Log.i(TAG, "Reset CandidateView to weight-based layout");
                            }
                        }
                    }
                }
            }
        });
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent ev) {
        if (ev.getActionMasked() == MotionEvent.ACTION_UP
                && mCandidateView != null
                && !mCandidateView.isEmpty()) {
            int candidateRowHeight = candidateRowHeight();
            int actionWidth = getResources().getDimensionPixelSize(R.dimen.candidate_expand_button_width);
            if (isRightEdgeActionTap(ev.getX(), ev.getY(), getWidth(), candidateRowHeight, actionWidth)) {
                toggleCandidatePopup();
                performClick();
                return true;
            }
        }
        return super.dispatchTouchEvent(ev);
    }

    private int candidateRowHeight() {
        if (mActionRow != null && mActionRow.getHeight() > 0) {
            return mActionRow.getHeight();
        }
        if (mCandidateView != null && mCandidateView.getHeight() > 0) {
            return mCandidateView.getHeight();
        }
        return 0;
    }

    @Override
    public void onClick(View v) {
        if (v == mDismissButton) {
            if (mCandidateView != null) {
                mCandidateView.dismissComposingFromCandidate();
            }
        } else if (v == mEmojiButton) {
            if (mService != null) {
                mService.onKey(LIME.KEYCODE_EMOJI_PANEL, null, 0, 0);
            }
        } else if (v == mKeyboardButton) {
            // Restore keyboard view when keyboard button is clicked
            // Use forceRestore=true to restore even when candidates/composing text is present
            if (mService != null) {
                // Restore keyboard view
                mService.restoreKeyboardViewIfHidden(true);
                // Request layout to update button visibility
                post(this::requestLayout);
            }
        } else if (isRightActionClick(v, mRightButton, mRightButtonParent)) {
            if (isShowingIdleTools())
                mCandidateView.startVoiceInput();
            else if (!mCandidateView.isEmpty())
                toggleCandidatePopup();
        }
    }

    private boolean isShowingIdleTools() {
        return mCandidateView != null
                && shouldShowIdleTools(mCandidateView.isEmpty(), mIdleToolsRevealReady, isComposingOrSearching());
    }

    private void toggleCandidatePopup() {
        if (mCandidateView.isCandidateExpanded()) {
            mCandidateView.hideCandidatePopup();
        } else {
            mCandidateView.showCandidatePopup();
        }
        post(this::requestLayout);
    }
}
