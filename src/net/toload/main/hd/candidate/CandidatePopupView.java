package net.toload.main.hd.candidate;

import java.util.List;

import net.toload.main.hd.global.Mapping;

import android.content.Context;
import android.graphics.Canvas;
import android.util.AttributeSet;
import android.view.MotionEvent;


public class CandidatePopupView extends CandidateView {
	
	private CandidateView mCandidateView;
	private List<Mapping> mSuggestions;

	public CandidatePopupView(Context context, AttributeSet attrs) {
		super(context, attrs);
		this.mGestureDetector = null;
		setHorizontalFadingEdgeEnabled(false);
    	setWillNotDraw(false);
    	setHorizontalScrollBarEnabled(false);
    	setVerticalScrollBarEnabled(false);

	}

	@Override
	protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
		 int desireWidth = resolveSize(mCandidateView.getWidth(), widthMeasureSpec);
		 int desiredHeight = resolveSize(500, heightMeasureSpec);
		 
		  // Maximum possible width and desired height
	        setMeasuredDimension(desireWidth, desiredHeight);
		 
	}

	@Override
	protected void onDraw(Canvas canvas) {
		// TODO Auto-generated method stub
		super.onDraw(canvas);
	}

	@Override
	public boolean onTouchEvent(MotionEvent me) {
		// TODO Auto-generated method stub
		return super.onTouchEvent(me);
	}
	
	public void setParentCandidateView(CandidateView v){
		mCandidateView =v;
		mSuggestions = v.mSuggestions;
		
	}
}
