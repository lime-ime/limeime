
/*
 *
 *  *
 *  **    Copyright 2026, The LimeIME Open Source Project
 *  **
 *  **    Project Url: http://github.com/lime-ime/limeime/
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

package org.limeime.candidate;

import android.annotation.SuppressLint;
import android.content.Context;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;
import android.view.View.OnTouchListener;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.limeime.LIMEService;
import org.limeime.R;

public class CandidateViewContainer extends LinearLayout implements OnTouchListener {

    private ImageButton mButtonDismiss;
    private ImageButton mButtonExpand;
    private View mButtonExpandLayout;
    private View mActionRow;
    private CandidateView mCandidateView;


    public CandidateViewContainer(Context context, AttributeSet attrs) {
        super(context, attrs);
       
        
    }

    @SuppressLint("ClickableViewAccessibility")
    public void initViews() {
        if (mCandidateView == null) {
            mButtonDismiss = findViewById(R.id.candidate_dismiss);
            mButtonExpandLayout = findViewById(R.id.candidate_right_parent);
            mButtonExpand = findViewById(R.id.candidate_right);
            if (mButtonDismiss != null && mButtonDismiss.getParent() instanceof View) {
                mActionRow = (View) mButtonDismiss.getParent();
            }
            if (mButtonDismiss != null) {
                mButtonDismiss.setOnTouchListener(this);
            }
            if (mButtonExpand != null) {
                mButtonExpand.setOnTouchListener(this);
            }
            mCandidateView = findViewById(R.id.candidates);
            TextView mEmbeddedTextView = findViewById(R.id.embeddedComposing);

            mCandidateView.setEmbeddedComposingView(mEmbeddedTextView);
            if (getContext() instanceof LIMEService) {
                mCandidateView.setService((LIMEService) getContext());
            }
            mCandidateView.setBackgroundColor(mCandidateView.mColorBackground);
            if (mActionRow != null) {
                mActionRow.setBackgroundColor(CandidateInInputViewContainer.actionRowBackgroundColor(mCandidateView.mColorBackground));
            }
            if (mButtonExpandLayout != null) {
                mButtonExpandLayout.setBackgroundColor(CandidateInInputViewContainer.actionRowBackgroundColor(mCandidateView.mColorBackground));
            }
            if (mButtonDismiss != null) {
                mButtonDismiss.setPadding(0, 0, 0, 0);
                mButtonDismiss.setScaleType(ImageButton.ScaleType.CENTER);
                mButtonDismiss.setMinimumWidth(0);
                mButtonDismiss.setMinimumHeight(0);
                mButtonDismiss.setImageDrawable(mCandidateView.makeDismissButtonGlyph());
                mButtonDismiss.setBackgroundColor(CandidateInInputViewContainer.dismissButtonBackgroundColor());
                mButtonDismiss.post(() -> mCandidateView.storePopupDismissButtonWidth(mButtonDismiss));
            }
            mButtonExpand.setBackgroundColor(mCandidateView.mColorBackground);
            mButtonExpand.setImageDrawable(mCandidateView.mDrawableExpandDownButton);
        }
    }

    @Override
    public void requestLayout() {
        if (mCandidateView != null) {
            int availableWidth = mCandidateView.getWidth();
            int neededWidth = mCandidateView.computeHorizontalScrollRange();
         
            boolean rightVisible =  availableWidth < neededWidth;
            if(mCandidateView.isCandidateExpanded())
            	rightVisible = true;
            
            if (mButtonExpandLayout != null) {
                mButtonExpandLayout.setVisibility(rightVisible ? VISIBLE : GONE);
            }
            if (mButtonDismiss != null) {
                mButtonDismiss.setVisibility(mCandidateView.isEmpty() ? GONE : VISIBLE);
            }
        }
        super.requestLayout();
    }

    public boolean onTouch(View v, MotionEvent event) {
        if (event.getAction() == MotionEvent.ACTION_DOWN) {
            if (v == mButtonDismiss) {
                mCandidateView.dismissComposingFromCandidate();
            } else if (v == mButtonExpand) {
            	
            	mCandidateView.showCandidatePopup();
            	
            }
        }
        return false;
    }

    
}
