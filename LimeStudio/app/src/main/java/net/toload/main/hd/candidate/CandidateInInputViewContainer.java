

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
import android.graphics.drawable.Drawable;
import android.support.v4.content.ContextCompat;
import android.util.AttributeSet;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.view.View.OnTouchListener;
import android.widget.ImageButton;
import android.widget.LinearLayout;

import net.toload.main.hd.R;

public class CandidateInInputViewContainer extends LinearLayout  implements OnTouchListener {

	private static final boolean DEBUG = false;
	private static final String TAG = "CandiInputViewContainer";
    private ImageButton mRightButton;
    private View mButtonRightExpand;
    private CandidateView mCandidates;

    Context ctx;
    
    public CandidateInInputViewContainer(Context context, AttributeSet attrs) {
        super(context, attrs);
        if(DEBUG)
    		Log.i(TAG,"CandidateInInputViewContainer() constructor");

        ctx = context;

    }

    public void initViews() {
    	if(DEBUG)
    		Log.i(TAG,"initViews()");
        if (mCandidates == null) {
            mButtonRightExpand = findViewById(R.id.candidate_right_parent);
            mRightButton = (ImageButton) findViewById(R.id.candidate_right);
            if (mRightButton != null) {
                mRightButton.setOnTouchListener(this);
            }
            mCandidates = (CandidateView) findViewById(R.id.candidatesView);
            
        }
    }

    @Override
    public void requestLayout() {
        if(DEBUG)
            Log.i(TAG,"requestLayout()");

    	if (mCandidates != null) {
            int availableWidth = mCandidates.getWidth();
            int neededWidth = mCandidates.computeHorizontalScrollRange();

            if(DEBUG)
                Log.i(TAG,"requestLayout() availableWidth:" + availableWidth+ " neededWidth:" + neededWidth);


            boolean showExpandButton =  availableWidth < neededWidth;
            boolean showVoiceInputButton = mCandidates.isEmpty();
            if(mCandidates.isCandidateExpanded())
            	showExpandButton = false;

            if(mRightButton != null){
                mRightButton.setImageResource(showVoiceInputButton ? R.drawable.sym_keyboard_voice_light : R.drawable.ic_suggest_expander );
            }

            if (mButtonRightExpand != null ) {
                mButtonRightExpand.setVisibility( (showVoiceInputButton || showExpandButton ) ? VISIBLE : GONE);
            }
        }
        super.requestLayout();
    }

    public boolean onTouch(View v, MotionEvent event) {
        if (event.getAction() == MotionEvent.ACTION_DOWN) {
            if (v == mRightButton) {
                if(mCandidates.isEmpty())
                    mCandidates.startVoiceInput();
                else
                    mCandidates.showCandidatePopup();
            	
            }
        }
        return false;
    }

    
}
