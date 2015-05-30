

package net.toload.main.hd.candidate;

import android.content.Context;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;
import android.view.View.OnTouchListener;
import android.widget.LinearLayout;

import net.toload.main.hd.R;

public class CandidateViewContainer extends LinearLayout implements OnTouchListener {

    private View mButtonExpand;
    private View mButtonExpandLayout;
    private CandidateView mCandidates;
    
    
    public CandidateViewContainer(Context context, AttributeSet attrs) {
        super(context, attrs);
       
        
    }

    public void initViews() {
        if (mCandidates == null) {
            mButtonExpandLayout = findViewById(R.id.candidate_right_parent);
            mButtonExpand = findViewById(R.id.candidate_right);
            if (mButtonExpand != null) {
                mButtonExpand.setOnTouchListener(this);
            }
            mCandidates = (CandidateView) findViewById(R.id.candidates);
            
        }
    }

    @Override
    public void requestLayout() {
        if (mCandidates != null) {
            int availableWidth = mCandidates.getWidth();
            int neededWidth = mCandidates.computeHorizontalScrollRange();
         
            boolean rightVisible =  availableWidth < neededWidth;
            if(mCandidates.isCandidateExpanded())
            	rightVisible = false;
            
            if (mButtonExpandLayout != null) {
                mButtonExpandLayout.setVisibility(rightVisible ? VISIBLE : GONE);
            }
        }
        super.requestLayout();
    }

    public boolean onTouch(View v, MotionEvent event) {
        if (event.getAction() == MotionEvent.ACTION_DOWN) {
            if (v == mButtonExpand) {
            	
            	mCandidates.showCandidatePopup();
            	
            }
        }
        return false;
    }

    
}
