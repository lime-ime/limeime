package net.toload.main.hd.candidate;

import java.util.List;

import net.toload.main.hd.R;
import net.toload.main.hd.global.Mapping;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.util.AttributeSet;
import android.util.Log;
import android.view.MotionEvent;
import android.widget.ScrollView;


public class CandidateExpandedView extends CandidateView {
	
	private final boolean DEBUG = false;
	private final String TAG = "CandidateExpandedView";
	
    private static final int MAX_SUGGESTIONS = 200;
	
	private CandidateView mCandidateView;
	private List<Mapping> mSuggestions;
	private int mVerticalPadding;
	private int mTouchX = OUT_OF_BOUNDS;
	private int mTouchY = OUT_OF_BOUNDS;
	private int mScrollY;
	private int[][] mWordX = new int[MAX_SUGGESTIONS][MAX_SUGGESTIONS];
	private int[][] mWordWidth = new int[MAX_SUGGESTIONS][MAX_SUGGESTIONS];
	private int[] mRowSize = new int[MAX_SUGGESTIONS];
	private int[] mRowStartingIndex = new int[MAX_SUGGESTIONS];
	private int mRows=0;
	private int mTotalHeight;
	private ScrollView mParentScroolView;

	public CandidateExpandedView(Context context, AttributeSet attrs) {
		super(context, attrs);
		//this.mGestureDetector = null;
		mVerticalPadding = context.getResources()
				.getDimensionPixelSize(R.dimen.candidate_vertical_padding);
		
    	
	}
	
	public void setParentScrollView(ScrollView v){
		mParentScroolView = v;
	}
	                                         
	

	@Override
	protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
		 int desireWidth = resolveSize(mCandidateView.getWidth(), widthMeasureSpec);
		 int desiredHeight = resolveSize(mTotalHeight, heightMeasureSpec);
		 
		  // Maximum possible width and desired height
	     setMeasuredDimension(desireWidth, desiredHeight);
		 
	}

	@Override
	protected void onDraw(Canvas canvas) {
		if(mSuggestions == null) return;
		if(DEBUG)
    		Log.i(TAG, "OnDraw() mSuggestions.size:" + mSuggestions.size());
    	
    	//mTotalWidth = 0;
    	if (mBgPadding == null) {
    		mBgPadding = new Rect(0, 0, 0, 0);
    		if (getBackground() != null) {
    			getBackground().getPadding(mBgPadding);
    			
    		}
    	}
    	
    	
    	
    	if(DEBUG)
    		Log.i(TAG, "OnDraw():mBgPadding.Top=" + mBgPadding.top 
				+", mBgPadding.Right=" + mBgPadding.right);
        
        final int height = mCandidateView.getHeight();
        final Rect bgPadding = mBgPadding;
        final Paint paint = mPaint;
        final Paint npaint = nPaint;
        
        // Update mSelectedIndex from touch x and y;
        if(mTouchX!=OUT_OF_BOUNDS && mTouchY!=OUT_OF_BOUNDS ){
        	int touchRow =(int) (mTouchY + mScrollY)/ (height + mVerticalPadding);
        	for(int i=0; i<mRowSize[touchRow]; i++){
        		if (mTouchX >= mWordX[touchRow][i] && mTouchX < mWordX[touchRow][i]+ mWordWidth[touchRow][i] ) 
        			mSelectedIndex = mRowStartingIndex[touchRow] +i;
        	}
        	if(DEBUG)
        		Log.i(TAG, "onDraw(): new mSelectedIndex =" + mSelectedIndex + ", touchRow=" + touchRow);
        }
      
        // Paint all the suggestions and lines.
        if (canvas != null) {
        	
        	// Calculate column and row of mSelectedIndex
        	int selRow =0;
        	int selCol = mSelectedIndex;
        	if(mSelectedIndex >= mRowSize[0]){
        		
        		for(int i=0; i < mRows+1; i++){
        			if(DEBUG)
        				Log.i(TAG, "onDraw(): mRowStartingIndex[" +i +"]=" + mRowStartingIndex[i]);
        			selRow = i;
        			if(mSelectedIndex < mRowStartingIndex[i]+ mRowSize[i] ) break;
        		}
        		//selRow = i;
        		selCol = mSelectedIndex - mRowStartingIndex[selRow];
        	}
        	if(DEBUG)
        		Log.i(TAG, "onDraw(): mSelectedIndex in at row:" + selRow + ", column:" + selCol);
        	
        	// Draw highlight on SelectedIndex
            if (canvas != null && mSelectedIndex >=0) {
                canvas.translate(mWordX[selRow][selCol], selRow * (height+ mVerticalPadding));
                mSelectionHighlight.setBounds(0, bgPadding.top, mWordWidth[selRow][selCol], height);
                mSelectionHighlight.draw(canvas);
                canvas.translate(-mWordX[selRow][selCol], -selRow * (height+ mVerticalPadding));
            }
            
            int y = (int) (((height - mPaint.getTextSize()) / 2) - mPaint.ascent());
            int index = 0; //index in mSuggestions
        	for (int i = 0; i < mRows+1; i++) {
        		if(i!=0) y += height + mVerticalPadding;

        		for(int j = 0; j < mRowSize[i]; j++){
      
        			String suggestion = mSuggestions.get(index).getWord();
        			index++;
        			
        			if(DEBUG)
        				Log.i(TAG, "Candidateview:OnDraw():index:" + index + "  Drawing:" + suggestion );

        			if(mSuggestions.get(i).isDictionary()){
        				npaint.setColor(mColorRecommended);
        				paint.setColor(mColorDictionary);
        			}else{
        				npaint.setColor(mColorOther);
        				if(i == 0 && j==0){
        					if(mSelectedIndex == 0) paint.setColor(mColorInverted);
        					else paint.setColor(mColorRecommended);
        				} else 
        					paint.setColor(mColorOther);
        				
        			}
        			canvas.drawText(suggestion, mWordX[i][j] + X_GAP, y, paint);


        			paint.setColor(mColorOther); 
        			float lineX = mWordX[i][j] + mWordWidth[i][j] + 0.5f;
        			canvas.drawLine(lineX, bgPadding.top + (height + mVerticalPadding)*i, lineX, 
        					(height + mVerticalPadding)*(i+1) - mVerticalPadding + 1, paint);
        			paint.setFakeBoldText(false);
        		}
        	}
        	
        	
        }
       
       
        
	}

	
	
	public void setParentCandidateView(CandidateView v){
		mCandidateView =v;	
	}
	
	public void prepareLayout()
	{	
		
		if(mSuggestions == null || mSuggestions.size()==0) return;
		
		if(DEBUG)
			Log.i(TAG, "prepareLayout():mSuggestions.size()" + mSuggestions.size());
		
		final int height = mCandidateView.getHeight();
        final Paint paint = mPaint;
		int x = 0;
		int row=0;
		int indexInRow=0;
		mRowStartingIndex[0]=0;

		for (int i = 0; i < count; i++) {
			if(DEBUG)
				Log.i(TAG, "prepareLayout():updating:" + i +", indexInRox=" + indexInRow );

			String suggestion = mSuggestions.get(i).getWord();
			float textWidth = paint.measureText(suggestion);
			final int wordWidth = (int) textWidth + X_GAP * 2;

			if(x + wordWidth > mScreenWidth){
				mRowSize[row]=indexInRow;
				row++;
				mRowStartingIndex[row] = i;
				indexInRow =0;
				x=0;
				if(DEBUG) 
					Log.i(TAG, "prepareLayout():mRowSize[" + (row-1) +"]=" + mRowSize[row-1] );
				if(DEBUG) 
					Log.i(TAG, "prepareLayout():mRowStartingIndex[" + row +"]=" + mRowStartingIndex[row] );
			}
			

			mWordX[row][indexInRow] = x;
			mWordWidth[row][indexInRow] = wordWidth;
			x += wordWidth;

			if(DEBUG) 
				Log.i(TAG, "prepareLayout():mWorx[" + row +"][" +indexInRow +"]=" + mWordX[row][indexInRow] );
			if(DEBUG) 
				Log.i(TAG, "prepareLayout():mWordWidth[" + row +"][" +indexInRow +"]=" + mWordWidth[row][indexInRow] );
			
			if(i== count-1){ 
				mRowSize[row]=indexInRow +1;
				if(DEBUG)
					Log.i(TAG, "prepareLayout():mRowSize[" + (row) +"]=" + mRowSize[row] );
			}
				
			indexInRow++;
			


		}
		//mTotalWidth = x;
		mRows = row;
		mTotalHeight = (height + mVerticalPadding) * (mRows+1);
	    if(DEBUG) 
	    	Log.i(TAG, "prepareLayout(): mRows=" + mRows + ", mTotalHeight=" + mTotalHeight);
	}
	
	@Override
	public void setSuggestions(List<Mapping> suggestions) {
		if(DEBUG) Log.i(TAG, "setSuggestions(), suggestions.size()=" + suggestions.size());
		if(mCandidateView!=null && mCandidateView.mSuggestions!=null){
			mSuggestions = mCandidateView.mSuggestions;
			count = mCandidateView.count;
			mSelectedIndex = mCandidateView.mSelectedIndex;
			mTouchX = OUT_OF_BOUNDS;
			mTouchY = OUT_OF_BOUNDS;
		}
		prepareLayout();
		requestLayout();
		invalidate();
		
	}

	@Override
	public boolean onTouchEvent(MotionEvent me) {
		Log.i(TAG, "onTouchEvent(): x =" + me.getX() + ", y=" + me.getY() + ", ScroolY=" +mParentScroolView.getScrollY() );
		int action = me.getAction();
		int x = (int) me.getX();
		int y = (int) me.getY();
		mTouchX = x;
		mTouchY = y;
		mScrollY = mParentScroolView.getScrollY();
		switch (action) {
		case MotionEvent.ACTION_DOWN:
			invalidate();
			break;
		case MotionEvent.ACTION_MOVE:

			invalidate();
			break;
		case MotionEvent.ACTION_UP:
			
			invalidate();
			mCandidateView.takeSuggstionAtIndex(mSelectedIndex);
            
			break;
		}
		return true;
	}
}
