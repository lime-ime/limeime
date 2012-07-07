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
	//private int mVerticalPadding;
	private int mTouchX = OUT_OF_BOUNDS;
	private int mTouchY = OUT_OF_BOUNDS;
	private int mSelRow; //Jeremy '11,8,28
	private int mSelCol; //Jeremy '11,8,28
	//private int mScrollY;
	private int[][] mWordX = new int[MAX_SUGGESTIONS][MAX_SUGGESTIONS];
	private int[][] mWordWidth = new int[MAX_SUGGESTIONS][MAX_SUGGESTIONS];
	private int[] mRowSize = new int[MAX_SUGGESTIONS];
	private int[] mRowStartingIndex = new int[MAX_SUGGESTIONS];
	private int mRows=0;
	private int mHeight; // built own mHeight and get from resources.
	private int mTotalHeight;
	private ScrollView mParentScroolView;

	public CandidateExpandedView(Context context, AttributeSet attrs) {
		super(context, attrs);
		//this.mGestureDetector = null;
		//mVerticalPadding =(int)( context.getResources()
		//		.getDimensionPixelSize(R.dimen.candidate_vertical_padding) *mLIMEPref.getFontSize());
		mHeight = (int) (context.getResources().
				getDimensionPixelSize(R.dimen.candidate_stripe_height) *mLIMEPref.getFontSize()); 
	
    	
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
        
        final int height = mHeight;
        final Rect bgPadding = mBgPadding;
        final Paint paint = mPaint;
        final Paint npaint = nPaint;
        
        // Update mSelectedIndex from touch x and y;
        if(mTouchX!=OUT_OF_BOUNDS && mTouchY!=OUT_OF_BOUNDS ){
        	//Jeremy '11,8,23 mTouchY is already relative to view origin, no need to add mScrollY
        	mSelRow =(int) (mTouchY )// + mScrollY)
        				/ (height + mVerticalPadding);
        	
        	for(int i=0; i<mRowSize[mSelRow]; i++){
        		if (mTouchX >= mWordX[mSelRow][i] && mTouchX < mWordX[mSelRow][i]+ mWordWidth[mSelRow][i] ){ 
        			mSelectedIndex = mRowStartingIndex[mSelRow] +i;
        			mSelCol = i;
        			break;
        		}
        	}
        	if(DEBUG)
        		Log.i(TAG, "onDraw(): new mSelectedIndex =" + mSelectedIndex 
        				+ ", mSelRow=" + mSelRow
        				+ ", mSelCol=" + mSelCol);
        }
      
        // Paint all the suggestions and lines.
        if (canvas != null) {
        	
        	// Calculate column and row of mSelectedIndex
        	/*int selRow =0;
        	int selCol = mSelectedIndex;
        	if(mSelectedIndex >= mRowSize[0]){
        		
        		for(int i=0; i < mRows+1; i++){
        			//if(DEBUG)
        				Log.i(TAG, "onDraw(): mRowStartingIndex[" +i +"]=" + mRowStartingIndex[i]);
        			selRow = i;
        			if(mSelectedIndex < mRowStartingIndex[i]+ mRowSize[i] ) break;
        		}
        		//selRow = i;
        		selCol = mSelectedIndex - mRowStartingIndex[selRow];
        	}*/
        	if(DEBUG)
        		Log.i(TAG, "onDraw(): mSelectedIndex=" + mSelectedIndex + " at row:" + mSelRow + ", column:" + mSelCol);
        	
        	// Draw highlight on SelectedIndex
        	// 29/Aug/2011, Art just ignore if there is an error. 
        	try{
	            if (canvas != null && mSelectedIndex >=0) {
	                canvas.translate(mWordX[mSelRow][mSelCol], mSelRow * (height+ mVerticalPadding));
	                mSelectionHighlight.setBounds(0, bgPadding.top, mWordWidth[mSelRow][mSelCol], height);
	                mSelectionHighlight.draw(canvas);
	                canvas.translate(-mWordX[mSelRow][mSelCol], -mSelRow * (height+ mVerticalPadding));
	            }
        	}catch(ArrayIndexOutOfBoundsException e){
        		e.printStackTrace();
        	}
            
            int y = (int) (((height - mPaint.getTextSize()) / 2) - mPaint.ascent());
            int index = 0; //index in mSuggestions
        	for (int i = 0; i < mRows; i++) {
        		if(i!=0) y += height + mVerticalPadding;

        		for(int j = 0; j < mRowSize[i]; j++){
      
        			String suggestion = mSuggestions.get(index).getWord();
        			index++;
        			
        			//if(DEBUG)
        			//	Log.i(TAG, "Candidateview:OnDraw():index:" + index + "  Drawing:" + suggestion );

        			if(mSuggestions.get(i).isDictionary()){
        				//npaint.setColor(mColorRecommended);
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
		if(DEBUG)
			Log.i(TAG, "prepareLayout()");
		
		if(mSuggestions == null || mSuggestions.size()==0) return;
			
		if(DEBUG)
			Log.i(TAG, "prepareLayout():mSuggestions.size()" + mSuggestions.size());
		
		updateFontSize();
		
		final int height = mHeight;
        final Paint paint = mPaint;
		int x = 0;
		int row=0;
		int indexInRow=0;
		mRowStartingIndex[0]=0;
		
		final int count = mCount;
		for (int i = 0; i < count; i++) {
			//if(DEBUG)
			//	Log.i(TAG, "prepareLayout():updating:" + i +", indexInRox=" + indexInRow );

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
		mRows = row+1;
		mTotalHeight = (height + mVerticalPadding) * (mRows);
	    if(DEBUG) 
	    	Log.i(TAG, "prepareLayout(): mRows=" + mRows + ", mTotalHeight=" + mTotalHeight);
	}
	
	@Override
	public void setSuggestions(List<Mapping> suggestions) {
		if(DEBUG) Log.i(TAG, "setSuggestions(), suggestions.size()=" + suggestions.size());
		if(mCandidateView!=null && mCandidateView.mSuggestions!=null){
			mSuggestions = mCandidateView.mSuggestions;
			mCount = mCandidateView.mCount;
			mSelectedIndex = mCandidateView.mSelectedIndex;
			mTouchX = OUT_OF_BOUNDS;
			mTouchY = OUT_OF_BOUNDS;
			if(mSelectedIndex==-1){
				mSelCol = -1;
				mSelRow = -1;
			}else{
				mSelCol = mSelectedIndex;
				mSelRow = 0;
			}
			 
		}
		prepareLayout();
		requestLayout();
		invalidate();
		
	}

	@Override
	public boolean onTouchEvent(MotionEvent me) {
		if(DEBUG) 
			Log.i(TAG, "onTouchEvent(): x =" + me.getX() + ", y=" + me.getY() 
					+ ", ScroolY=" +mParentScroolView.getScrollY() );
		int action = me.getAction();
		int x = (int) me.getX();
		int y = (int) me.getY();
		mTouchX = x;
		mTouchY = y;
		//mScrollY = mParentScroolView.getScrollY();
		switch (action) {
		case MotionEvent.ACTION_DOWN:
			if(DEBUG) Log.i(TAG, "onTouchEvent(), Action_DONW");
			invalidate();
			break;
		case MotionEvent.ACTION_MOVE:
			if(DEBUG) Log.i(TAG, "onTouchEvent(), Action_MOVE");
			invalidate();
			break;
		case MotionEvent.ACTION_UP:
			if(DEBUG) Log.i(TAG, "onTouchEvent(), Action_UP");
			//invalidate();
			mCandidateView.takeSelectedSuggestion(true);//takeSuggstionAtIndex(mSelectedIndex);
            
			break;
		}
		return true;
	}

	private void scrollToRow(int row){
		int selY = row*(mHeight + mVerticalPadding);
		int scrollY = mParentScroolView.getScrollY();
		int scrollHeight = mParentScroolView.getHeight();
		if(DEBUG) Log.i(TAG, "scrollToRow(), row=" + row
					+ ", selected row y=" + selY
					+ ", ScrollViewHeight" + scrollHeight
					+ ", ScollY="+scrollY);
		
		if(selY <  scrollY || selY > (scrollY + scrollHeight))
			mParentScroolView.scrollTo(0, row*((mHeight + mVerticalPadding)));
		
	}
	
	@Override
	public void selectNext() {
		if (mSuggestions == null) return;
		if(mSelectedIndex == -1){
			mSelectedIndex=0;
			mSelRow=0;
			mSelCol=0;
		}else if(mSelectedIndex < mCount-1){
    		mSelectedIndex++;
    		if(mSelectedIndex >= mRowStartingIndex[mSelRow] + mRowSize[mSelRow]){
    			mSelRow++;
    			mSelCol=0;
    			scrollToRow(mSelRow);
    		}else
    			mSelCol++;
    			
    		invalidate();
    	}
	}

	@Override
	public void selectPrev() {
		if (mSuggestions == null) return;
    	if(mSelectedIndex > 0) {
    		mSelectedIndex--;
    		if(mSelectedIndex < mRowStartingIndex[mSelRow] ){
    			mSelRow--;  
    			mSelCol = mRowSize[mSelRow]-1;
    			scrollToRow(mSelRow);
    		}else
    			mSelCol--;
    		invalidate();
    	}
	}

	@Override
	public void selectNextRow() {
		if (mSuggestions == null) return;
		if(mSelRow < mRows-1){
			mSelRow ++;

			if(DEBUG)
				Log.i(TAG,"selectNextRow(): newRow=" + mSelRow 
					+ ", mSelCol=" + mSelCol
					+ ", mRowStartingIndex[mSelRow]=" + mRowStartingIndex[mSelRow]
					+ ", + mRowSize[mSelRow]" + + mRowSize[mSelRow]);
			if(mSelCol >  mRowSize[mSelRow]-1)
				mSelCol = mRowSize[mSelRow]-1;
			else if(mSelCol == -1)
				mSelCol =0;
			
			mSelectedIndex = mRowStartingIndex[mSelRow] + mSelCol;
			scrollToRow(mSelRow);
			invalidate();
		}
		
	}

	@Override
	public void selectPrevRow() {
		if (mSuggestions == null) return;
		if(mSelRow >0) {
			mSelRow--;
			if(mSelCol >  mRowSize[mSelRow]-1)
				mSelCol = mRowSize[mSelRow]-1;
			
			mSelectedIndex = mRowStartingIndex[mSelRow] + mSelCol;
			scrollToRow(mSelRow);
			invalidate();
		}
			
	}
}
