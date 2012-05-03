

package net.toload.main.hd.candidate;

import net.toload.main.hd.R;
import android.content.Context;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;
import android.view.View.OnTouchListener;
import android.widget.LinearLayout;

public class CandidateInInputViewContainer extends LinearLayout  implements OnTouchListener {

    private View mButtonRight;
    private View mButtonRightLayout;
    private CandidateView mCandidates;

    
    public CandidateInInputViewContainer(Context context, AttributeSet attrs) {
        super(context, attrs);
       
        
    }

    public void initViews() {
        if (mCandidates == null) {
            mButtonRightLayout = findViewById(R.id.candidate_right_parent);
            mButtonRight = findViewById(R.id.candidate_right);
            if (mButtonRight != null) {
                mButtonRight.setOnTouchListener(this);
            }
            mCandidates = (CandidateView) findViewById(R.id.candidatesView);
            
        }
    }

    @Override
    public void requestLayout() {
        if (mCandidates != null) {
            int availableWidth = mCandidates.getWidth();
            int neededWidth = mCandidates.computeHorizontalScrollRange();
         
            boolean rightVisible =  availableWidth < neededWidth;
            if(mCandidates.isCandidateExpanded())//
            	rightVisible = false;
            
            if (mButtonRightLayout != null) {
                mButtonRightLayout.setVisibility(rightVisible ? VISIBLE : GONE);
            }
        }
        super.requestLayout();
    }

    public boolean onTouch(View v, MotionEvent event) {
        if (event.getAction() == MotionEvent.ACTION_DOWN) {
            if (v == mButtonRight) {
              	mCandidates.showCandidatePopup();
            	
            }
        }
        return false;
    }

    
}
