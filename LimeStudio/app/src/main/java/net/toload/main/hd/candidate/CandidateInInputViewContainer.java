

/*
 *
 *  *
 *  **    Copyright 2015, The LimeIME Open Source Project
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
import android.util.AttributeSet;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.LinearLayout;

import net.toload.main.hd.LIMEService;
import net.toload.main.hd.R;

public class CandidateInInputViewContainer extends LinearLayout  implements View.OnClickListener {

    private static final boolean DEBUG = false;
    private static final String TAG = "CandiInputViewContainer";
    private ImageButton mRightButton;
    private ImageButton mKeyboardButton;
    private CandidateView mCandidateView;
    private LIMEService mService;

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
            // Ensure buttons are laid out in correct order
            if (mButtonRightExpand instanceof ViewGroup) {
                android.view.ViewGroup vg = (android.view.ViewGroup) mButtonRightExpand;
                vg.setClipChildren(false);
                vg.setClipToPadding(false);
            }
            mRightButton = findViewById(R.id.candidate_right);
            mKeyboardButton = findViewById(R.id.candidate_keyboard);

            if (mRightButton != null) {
                mRightButton.setOnClickListener(this);
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
            if (mRightButton != null) {
                mRightButton.setBackgroundColor(mCandidateView.mColorBackground);
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
            
            // Update keyboard button visibility
            if (mKeyboardButton != null) {
                mKeyboardButton.setVisibility(showKeyboardButton ? View.VISIBLE : View.GONE);
            }

            // When empty: show voice input button
            // When not empty: show expand button based on keyboard visibility
            if (mRightButton != null) {
                if (isEmpty) {
                    mRightButton.setImageDrawable(mCandidateView.mDrawableVoiceInput);
                } else {
                    // Show up arrow when keyboard is hidden, down arrow when keyboard is shown
                    boolean isKeyboardHidden = (mService != null) && mService.isKeyboardViewHidden();
                    mRightButton.setImageDrawable(isKeyboardHidden ? 
                        mCandidateView.mDrawableExpandUpButton : 
                        mCandidateView.mDrawableExpandDownButton);
                }
            }
        }
        
        super.requestLayout();
    }
    
    /**
     * Update CandidateView width constraint to leave space for visible buttons.
     * This should be called when suggestions change (set/clear) to ensure buttons don't get pushed off-screen.
     */
    public void updateCandidateViewWidthConstraint() {
        post(() -> {
            int containerWidth = getWidth();
            if (containerWidth > 0 && mCandidateView != null) {
                ViewGroup.LayoutParams params = mCandidateView.getLayoutParams();
                if (params instanceof LinearLayout.LayoutParams) {
                    LinearLayout.LayoutParams llParams = (LinearLayout.LayoutParams) params;
                    
                    // Calculate buttons width - always use dimension resource for consistency
                    int buttonsWidth = 0;
                    int buttonWidth = getResources().getDimensionPixelSize(R.dimen.candidate_expand_button_width);
                    boolean keyboardVisible = mKeyboardButton != null && mKeyboardButton.getVisibility() == View.VISIBLE;
                    boolean rightVisible = mRightButton != null && mRightButton.getVisibility() == View.VISIBLE;
                    
                    if (DEBUG) {
                        Log.i(TAG, "Width constraint: containerWidth=" + containerWidth + 
                              ", keyboardVisible=" + keyboardVisible + 
                              ", rightVisible=" + rightVisible +
                              ", buttonWidth=" + buttonWidth);
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
    public void onClick(View v) {
        if (v == mKeyboardButton) {
            // Restore keyboard view when keyboard button is clicked
            // Use forceRestore=true to restore even when candidates/composing text is present
            if (mService != null) {
                // Restore keyboard view
                mService.restoreKeyboardViewIfHidden(true);
                // Request layout to update button visibility
                post(this::requestLayout);
            }
        } else if (v == mRightButton) {
            if (mCandidateView.isEmpty())
                mCandidateView.startVoiceInput();
            else
                mCandidateView.showCandidatePopup();
        }
    }
}
