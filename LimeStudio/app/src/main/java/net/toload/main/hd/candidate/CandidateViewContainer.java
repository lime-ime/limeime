

package net.toload.main.hd.candidate;

import android.content.Context;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;
import android.view.View.OnTouchListener;
import android.widget.LinearLayout;
import android.widget.TextView;

import net.toload.main.hd.R;

public class CandidateViewContainer extends LinearLayout implements OnTouchListener {

    private View mButtonExpand;
    private View mButtonExpandLayout;
    private CandidateView mCandidateView;
    private TextView mEmbeddedTextView;

    
    
    public CandidateViewContainer(Context context, AttributeSet attrs) {
        super(context, attrs);
       
        
    }

    public void initViews() {
        if (mCandidateView == null) {
            mButtonExpandLayout = findViewById(R.id.candidate_right_parent);
            mButtonExpand = findViewById(R.id.candidate_right);
            if (mButtonExpand != null) {
                mButtonExpand.setOnTouchListener(this);
            }
            mCandidateView = (CandidateView) findViewById(R.id.candidates);
            mEmbeddedTextView = (TextView) findViewById(R.id.embeddedComposing);
            mCandidateView.setEmbeddedComposingView(mEmbeddedTextView);
            
        }
    }

    @Override
    public void requestLayout() {
        if (mCandidateView != null) {
            int availableWidth = mCandidateView.getWidth();
            int neededWidth = mCandidateView.computeHorizontalScrollRange();
         
            boolean rightVisible =  availableWidth < neededWidth;
            if(mCandidateView.isCandidateExpanded())
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
            	
            	mCandidateView.showCandidatePopup();
            	
            }
        }
        return false;
    }

    
}
