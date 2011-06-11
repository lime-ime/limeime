/*    
**    Copyright 2010, The LimeIME Open Source Project
** 
**    Project Url: http://code.google.com/p/limeime/
**                 http://android.toload.net/
**
**    This program is free software: you can redistribute it and/or modify
**    it under the terms of the GNU General Public License as published by
**    the Free Software Foundation, either version 3 of the License, or
**    (at your option) any later version.

**    This program is distributed in the hope that it will be useful,
**    but WITHOUT ANY WARRANTY; without even the implied warranty of
**    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
**    GNU General Public License for more details.

**    You should have received a copy of the GNU General Public License
**    along with this program.  If not, see <http://www.gnu.org/licenses/>.
*/

package net.toload.main.hd;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.os.Handler;
import android.os.Message;
import android.preference.PreferenceManager;
import android.util.AttributeSet;
import android.util.Log;
import android.view.GestureDetector;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.View.MeasureSpec;
import android.view.ViewGroup.LayoutParams;
import android.widget.PopupWindow;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

import net.toload.main.hd.R;
import net.toload.main.hd.R.color;
import net.toload.main.hd.R.dimen;

/**
 * @author Art Hung
 */
public class CandidateView extends View {
	
	private static final boolean DEBUG = false;

    private static final int OUT_OF_BOUNDS = -1;

    private LIMEService mService;
    private List<Mapping> mSuggestions;
    private int mSelectedIndex;
    private int mTouchX = OUT_OF_BOUNDS;
    private Drawable mSelectionHighlight;
    private boolean mTypedWordValid;
    private boolean mShowNumber; //Jeremy '11,5,25 for showing physical keyboard number or not.
    
    private Rect mBgPadding;

    private static final int MAX_SUGGESTIONS = 200;
    private static final int SCROLL_PIXELS = 20;
    
    // Add by Jeremy '10, 3, 29.
    // Suggestions size. Set to MAX_GUGGESTIONS if larger then it.
    private int count =0 ;
    //Composing view
	private TextView mComposingTextView;
	private PopupWindow mComposingTextPopup;
	private int mDescent;
	private String mComposingText = "";
    
    private int[] mWordWidth = new int[MAX_SUGGESTIONS];
    private int[] mWordX = new int[MAX_SUGGESTIONS];

    private static int X_GAP = 12;
    
    private static final List<Mapping> EMPTY_LIST = new LinkedList<Mapping>();

	

    private int currentX;
    private int mColorNormal;
    private int mColorDictionary;
    private int mColorRecommended;
    private int mColorOther;
    private int mColorNumber;
    private int mVerticalPadding;
    private Paint mPaint;
    private Paint nPaint;
    private boolean mScrolled;
    private int mTargetScrollX;
    private String mDisplaySelkey = "1234567890";
    
    private int mTotalWidth;
    
    private boolean goLeft = false;
    private boolean goRight = false;
    private boolean hasSlide = false;
    private int bgcolor = 0;
    
    private GestureDetector mGestureDetector;
    
 
   // private Context ctx;

    /**
     * Construct a CandidateView for showing suggested words for completion.
     * @param context
     * @param attrs
     */
    public CandidateView(Context context) {
        
    	super(context);
    	
        mSelectionHighlight = context.getResources().getDrawable(
                android.R.drawable.list_selector_background);
        mSelectionHighlight.setState(new int[] {
                android.R.attr.state_enabled,
                android.R.attr.state_focused,
                android.R.attr.state_window_focused,
                android.R.attr.state_pressed
        });

        Resources r = context.getResources();
        bgcolor = r.getColor(R.color.candidate_background);
        
        mColorNormal = r.getColor(R.color.candidate_normal);
        mColorDictionary = r.getColor(R.color.candidate_dictionary);
        mColorRecommended = r.getColor(R.color.candidate_recommended);
        mColorOther = r.getColor(R.color.candidate_other);
        mColorNumber = r.getColor(R.color.candidate_number);
        mVerticalPadding = r.getDimensionPixelSize(R.dimen.candidate_vertical_padding);
        
        
     // Composing buffer textView
		LayoutInflater inflate 
			= (LayoutInflater) context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
	        mComposingTextPopup = new PopupWindow(context);
	        mComposingTextView = (TextView) inflate.inflate(R.layout.composingtext, null);
	        mComposingTextPopup.setWindowLayoutMode(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
	        mComposingTextPopup.setContentView(mComposingTextView);
	        mComposingTextPopup.setBackgroundDrawable(null);

	       
        
        mPaint = new Paint();
        mPaint.setColor(mColorNormal);
        mPaint.setAntiAlias(true);
        mPaint.setTextSize(r.getDimensionPixelSize(R.dimen.candidate_font_height));
        mPaint.setStrokeWidth(0);
        
        nPaint = new Paint();
        nPaint.setColor(mColorNumber);
        nPaint.setAntiAlias(true);
        nPaint.setTextSize(r.getDimensionPixelSize(R.dimen.candidate_number_font_height));
        nPaint.setStyle(Paint.Style.FILL_AND_STROKE);
        
        mDescent = (int) mPaint.descent();

        final SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(context);
        
        mGestureDetector = new GestureDetector(new GestureDetector.SimpleOnGestureListener() {
            @Override
            public boolean onScroll(MotionEvent e1, MotionEvent e2,
                    float distanceX, float distanceY) {
                mScrolled = true;
                int sx = getScrollX();
                sx += distanceX;
                if (sx < 0) {
                    sx = 0;
                }
                if (sx + getWidth() > mTotalWidth) {                    
                    sx -= distanceX;
                }

        		if(sp.getBoolean("candidate_switch", false)){
        			hasSlide = true;
                    mTargetScrollX = sx;
                    scrollTo(sx, getScrollY());
        		}else{
        			hasSlide = false;
                    if(distanceX < 0){
                    	goLeft = true;
                    	goRight = false;
                    }else if(distanceX > 0){
                    	goLeft = false;
                    	goRight = true;
                    }else{
                        mTargetScrollX = sx;
                    }
        		}
        		
                invalidate();
                return true;
            }
        });
        setHorizontalFadingEdgeEnabled(true);
        setWillNotDraw(false);
        setHorizontalScrollBarEnabled(false);
        setVerticalScrollBarEnabled(false);
    }
    

	private static final int MSG_REMOVE_COMPOSING = 1;   
	Handler mHandler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case MSG_REMOVE_COMPOSING:
                	mComposingTextView.setVisibility(GONE);
                    break;
               
            }
            
        }
    };
    
    public void setComposingText(String composingText){
    	if(composingText != null && !composingText.trim().equals("")){
        	mComposingText = composingText;
        	mComposingTextView.setText(mComposingText);
        	showComposing();
    	}else{
    		mComposingTextView.setVisibility(View.INVISIBLE);
    	}
    }
    public String getComposingText(String ComposingText){
		return mComposingText;
 	
    }
    public void showComposing() {
    	if(DEBUG) Log.i("candidateview","showcomposing()");
        if (!mComposingText.equals("")) {	
            	mComposingTextPopup.setContentView(mComposingTextView);
                mComposingTextView.setText(mComposingText);
                mComposingTextView.measure(MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED), 
                        MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED));
                int wordWidth = (int) (mPaint.measureText(mComposingText, 0, mComposingText.length()));
                final int popupWidth = wordWidth
                        + mComposingTextView.getPaddingLeft() + mComposingTextView.getPaddingRight();
                final int popupHeight = mComposingTextView.getMeasuredHeight();
                //mPreviewText.setVisibility(INVISIBLE);
                
                int mPopupComposingY = - popupHeight;
                //mHandler.removeMessages(MSG_REMOVE_COMPOSING);
                int [] offsetInWindow = new int[2];
                mComposingTextView.getLocationInWindow(offsetInWindow);
                if (mComposingTextPopup.isShowing()) {              	
                	mComposingTextPopup.update(0, mPopupComposingY + offsetInWindow[1], 
                            popupWidth, popupHeight);
                } else {
                	mComposingTextPopup.setWidth(popupWidth);
                	mComposingTextPopup.setHeight(popupHeight);
                	mComposingTextPopup.showAtLocation(this, Gravity.NO_GRAVITY, 0, 
                			mPopupComposingY + offsetInWindow[1]);
                }
                
               /* mComposingTextPopup.setWidth(popupWidth);
            	mComposingTextPopup.setHeight(popupHeight);
            	mComposingTextPopup.showAtLocation(this, Gravity.NO_GRAVITY, 0, 
            			mPopupCompoingY + offsetInWindow[1]);
                mComposingTextView.setVisibility(VISIBLE);*/
                mComposingTextView.setVisibility(VISIBLE);


            }
        
    }

    
    public void hideComposing() {
    	if(DEBUG) Log.i("candidateview","hidecomposing()");
  
      if(mComposingTextPopup!=null ){  
        if (mComposingTextPopup.isShowing()) {
            //mHandler.sendMessageDelayed(mHandler.obtainMessage(MSG_REMOVE_COMPOSING), 60);
        	  mComposingTextPopup.dismiss();
        }
      }
    }
    
    

    
    /**
     * A connection back to the service to communicate with the text field
     * @param listener
     */
    public void setService(LIMEService listener) {
        mService = listener;
    }
    
    @Override
    public int computeHorizontalScrollRange() {
        return mTotalWidth;
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int measuredWidth = resolveSize(50, widthMeasureSpec);
        
        // Get the desired height of the icon menu view (last row of items does
        // not have a divider below)
        Rect padding = new Rect();
        mSelectionHighlight.getPadding(padding);
        //final int desiredHeight = ((int)mPaint.getTextSize()) + mVerticalPadding
                //+ padding.top + padding.bottom;
        final int desiredHeight = ((int)mPaint.getTextSize());
        
        // Maximum possible width and desired height
        setMeasuredDimension(measuredWidth,
                resolveSize(desiredHeight, heightMeasureSpec));
    }

    /**
     * If the canvas is null, then only touch calculations are performed to pick the target
     * candidate.
     */
    @Override
    protected void onDraw(Canvas canvas) {
    	if(DEBUG){
    		Log.i("Candidateview:OnDraw", "Suggestion count:" + count+" mSuggestions.size:" + mSuggestions.size());
    	}
        if (canvas != null) {
            super.onDraw(canvas);
        }
        mTotalWidth = 0;
        if (mSuggestions == null) return;
        
        if (mBgPadding == null) {
            mBgPadding = new Rect(0, 0, 0, 0);
            if (getBackground() != null) {
                getBackground().getPadding(mBgPadding);
            }
        }
        
        //final int count = mSuggestions.size(); 
        final int height = getHeight();
        final Rect bgPadding = mBgPadding;
        final Paint paint = mPaint;
        final Paint npaint = nPaint;
        final int touchX = mTouchX;
        final int scrollX = getScrollX();
        final boolean scrolled = mScrolled;
        //final boolean typedWordValid = mTypedWordValid;
        final int y = (int) (((height - mPaint.getTextSize()) / 2) - mPaint.ascent());

        // Modified by jeremy '10, 3, 29.  Update mselectedindex if touched and build wordX[i] and wordwidth[i]
        int x = 0;
        for (int i = 0; i < count; i++) {
        	if(DEBUG){
        		Log.i("Candidateview:OnDraw", "updaingting:" + i );
        	}
        	String suggestion = mSuggestions.get(i).getWord();
            float textWidth = paint.measureText(suggestion);
            final int wordWidth = (int) textWidth + X_GAP * 2;

            mWordX[i] = x;
            mWordWidth[i] = wordWidth;

            if (touchX + scrollX >= x && touchX + scrollX < x + wordWidth && !scrolled) {
                mSelectedIndex = i;}
            x += wordWidth;
        }
        mTotalWidth = x;
       
 
        // Moved from above by jeremy '10 3, 29. Paint mselectedindex in highlight here
        if (canvas != null && mSelectedIndex >=0) {
            canvas.translate(mWordX[mSelectedIndex], 0);
            mSelectionHighlight.setBounds(0, bgPadding.top, mWordWidth[mSelectedIndex], height);
            mSelectionHighlight.draw(canvas);
            canvas.translate(-mWordX[mSelectedIndex], 0);
        }
        
        // Paint all the suggestions and lines.
        if (canvas != null) {
        	
        	for (int i = 0; i < count; i++) {
        		if(DEBUG){
        			Log.i("Candidateview:OnDraw","i:" + i + "  Drawing:" + mSuggestions.get(i).getWord() );
        		}
        	
                //if ((i == 1 && !typedWordValid) || (i == 0 && typedWordValid)) {
                //    paint.setFakeBoldText(true);
                //    paint.setColor(mColorRecommended);
            	String suggestion = mSuggestions.get(i).getWord();
            	int count = i+1;
            	
                if(mSuggestions.get(i).isDictionary()){
                	npaint.setColor(mColorRecommended);
                	if(i == 0){
                    	paint.setColor(mColorDictionary);
                    } else if (i != 0) {
                        paint.setColor(mColorDictionary);
                    }
                }else{
                	npaint.setColor(mColorOther);
                    if(i == 0){
                    	paint.setColor(mColorRecommended);
                    } else if (i != 0) {
                        paint.setColor(mColorOther);
                    }
                }
                canvas.drawText(suggestion, mWordX[i] + X_GAP, y, paint);
                if(mShowNumber){
                	if(count <= 10){
                		//Jeremy '11,6,11 Drawing text using relative font dimensions.
                		canvas.drawText(mDisplaySelkey.substring(count-1, count), mWordX[i] + mWordWidth[i] - height * 0.3f ,  height * 0.4f, npaint);
                	}
                }
                
                paint.setColor(mColorOther); 
                canvas.drawLine(mWordX[i] + mWordWidth[i] + 0.5f, bgPadding.top, 
                		mWordX[i] + mWordWidth[i] + 0.5f, height + 1, paint);
                paint.setFakeBoldText(false);
            }
           
        }
       
        if (mTargetScrollX != getScrollX()) {
            scrollToTarget();
        }
        
        //showComposing();
    }
    
    private void scrollToTarget() {
        int sx = getScrollX();
        if (mTargetScrollX > sx) {
            sx += SCROLL_PIXELS;
            if (sx >= mTargetScrollX) {
                sx = mTargetScrollX;
                requestLayout();
            }
        } else {
            sx -= SCROLL_PIXELS;
            if (sx <= mTargetScrollX) {
                sx = mTargetScrollX;
                requestLayout();
            }
        }
        scrollTo(sx, getScrollY());
        invalidate();
    }
    public void setSuggestions(List<Mapping> suggestions, boolean showNumber, boolean typedWordValid, String displaySelkey) {
    	mDisplaySelkey = displaySelkey;
    	setSuggestions(suggestions, showNumber, typedWordValid);
    	
    }
    public void setSuggestions(List<Mapping> suggestions, boolean showNumber, boolean typedWordValid) {
        clear();
        if(showNumber)
        	X_GAP = 13;
        else 
        	X_GAP = 10;
        mShowNumber = showNumber;
        if (suggestions != null) {
            mSuggestions = new LinkedList<Mapping>(suggestions);
	       
	        if(DEBUG)
	        	Log.i("setSuggestions:","mSuggestions.size:" + mSuggestions.size());
	       
	        
	        if(mSuggestions != null && mSuggestions.size() > 0){
	            setBackgroundColor(bgcolor);
	            // Add by Jeremy '10, 3, 29
	            count = mSuggestions.size(); 
	            if(count > MAX_SUGGESTIONS) count = MAX_SUGGESTIONS;
	            
	            if(mSuggestions.get(0).isDictionary()){
	            	// no default selection for related words
	            	mSelectedIndex = -1;
	            }else if(mSuggestions.size() == 1){
	            	mSelectedIndex = 0;
	            }else {
	            	// default selection on suggestions 1 (0 is typed English in mixed English mode)
	            	mSelectedIndex = 1;
	            }
	        }else{
	        	 if(DEBUG)
	 	        	Log.i("setSuggestions:","mSuggestions=null");
	            setBackgroundColor(0);
	        }
	        
	        mTypedWordValid = typedWordValid;
	        scrollTo(0, 0);
	        mTargetScrollX = 0;
	        // Compute the total width
	        onDraw(null);
	        invalidate();
	        requestLayout();
        }else{
        	mSuggestions = new LinkedList<Mapping>();
        }
    }

    public void clear() {
    	if(DEBUG) Log.i("Canddateview","cliear()");
    	currentX = 0;
        mSuggestions = EMPTY_LIST;
        // Jeremy '10, 4, 8
        count =0;
        mTouchX = OUT_OF_BOUNDS;
        mSelectedIndex = -1;
        //hideComposing();
        setComposingText("");
        invalidate();
    }
    
    @Override
    public boolean onTouchEvent(MotionEvent me) {

        if (mGestureDetector.onTouchEvent(me)) {
            return true;
        }

        int action = me.getAction();
        int x = (int) me.getX();
        int y = (int) me.getY();
        mTouchX = x;
        switch (action) {
        case MotionEvent.ACTION_DOWN:
            mScrolled = false;
            invalidate();
            break;
        case MotionEvent.ACTION_MOVE:
            if (y <= 0) {
                // Fling up!?
                if (mSelectedIndex >= 0) {
                    mService.pickSuggestionManually(mSelectedIndex);
                    mSelectedIndex = -1;
                }
            }
            invalidate();
            break;
        case MotionEvent.ACTION_UP:
            if (!mScrolled) {
                if (mSelectedIndex >= 0) {
                    mService.pickSuggestionManually(mSelectedIndex);
                }
            }
            mSelectedIndex = -1;
            removeHighlight();
            requestLayout();

            if(!hasSlide){
                if(goLeft){
                	scrollPrev();
                }
                if(goRight){
                	scrollNext();
                }
            }
            
            break;
        }
        return true;
    }
    
    public void scrollPrev() {
        int i = 0;
        //final int count = mSuggestions.size();
        int firstItem = 0; // Actually just before the first item, if at the boundary
        while (i < count) {
            if (mWordX[i] < currentX
                    && mWordX[i] + mWordWidth[i] >= currentX - 1) {
                firstItem = i;
                break;
            }
            i++;
        }
        int leftEdge = mWordX[firstItem] + mWordWidth[firstItem] - getWidth();
        if (leftEdge < 0) {
        	leftEdge = 0;
        	currentX = leftEdge;            
        }else{
        	currentX = leftEdge;      
        }
        updateScrollPosition(leftEdge);
    }
    
    
    public void scrollNext() {
        int i = 0;
        int targetX = currentX;
        //final int count = mSuggestions.size();
        int rightEdge = currentX + getWidth();
        while (i < count) {
            if (mWordX[i] <= rightEdge &&
                    mWordX[i] + mWordWidth[i] >= rightEdge) {
                targetX = Math.min(mWordX[i], mTotalWidth - getWidth());
                currentX = mWordX[i];
                break;
            }
            i++;
        }
        updateScrollPosition(targetX);
    }
    
    private void updateScrollPosition(int targetX) {
        if (targetX != mTouchX) {
            // TODO: Animate
        	mTargetScrollX = targetX;
            requestLayout();
            invalidate();
            mScrolled = true;
        }
    }
    
    //Add by Jeremy '10, 3, 29 for DPAD (physical keyboard) selection.
    public void selectNext() {
    	if (mSuggestions == null) return;
    	if(mSelectedIndex < count-1){
    		mSelectedIndex++;
    		if(mWordX[mSelectedIndex] + mWordWidth[mSelectedIndex] > currentX + getWidth()) scrollNext();
    	}
    	invalidate();
    }
    
    public void selectPrev() {
    	if (mSuggestions == null) return;
        if(mSelectedIndex > 0) {
        	mSelectedIndex--;
        	if(mWordX[mSelectedIndex] < currentX) scrollPrev();
        }
        invalidate();
    }
    public boolean takeSelectedSuggestion(){
    	if(DEBUG){
    		Log.i("candidateview:takeSeelctionSuggestion", "mSelectedIndex:" + mSelectedIndex);
    	}
    	if (mSuggestions != null &&(mSelectedIndex >= 0) ) {
    		mService.pickSuggestionManually(mSelectedIndex);
    		return true;  // Selection picked
        }else{
        	return false;
        }
    	
    }

    /**
     * For flick through from keyboard, call this method with the x coordinate of the flick 
     * gesture.
     * @param x
     */
    public void takeSuggestionAt(float x) {
    	
        mTouchX = (int) x;
        // To detect candidate
        onDraw(null);
        if (mSelectedIndex >= 0) {
            mService.pickSuggestionManually(mSelectedIndex);
        }
        invalidate();
    }

    private void removeHighlight() {
        mTouchX = OUT_OF_BOUNDS;
        invalidate();
    }
    
    @Override
    public void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        hideComposing();
    }
}
