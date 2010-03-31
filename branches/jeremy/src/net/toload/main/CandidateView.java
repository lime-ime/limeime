
/* 
**
** Copyright 2008, The Android Open Source Project
**
** Licensed under the Apache License, Version 2.0 (the "License"); 
** you may not use this file except in compliance with the License. 
** You may obtain a copy of the License at 
**
**     http://www.apache.org/licenses/LICENSE-2.0 
**
** Unless required by applicable law or agreed to in writing, software 
** distributed under the License is distributed on an "AS IS" BASIS, 
** WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. 
** See the License for the specific language governing permissions and 
** limitations under the License.
*/

package net.toload.main;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.preference.PreferenceManager;
import android.util.Log;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.View;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

import net.toload.main.R;
import net.toload.main.R.color;
import net.toload.main.R.dimen;

/**
 * @author Art Hung
 */
public class CandidateView extends View {

    private static final int OUT_OF_BOUNDS = -1;

    private LIMEService mService;
    private List<Mapping> mSuggestions;
    private int mSelectedIndex;
    private int mTouchX = OUT_OF_BOUNDS;
    private Drawable mSelectionHighlight;
    private boolean mTypedWordValid;
    
    private Rect mBgPadding;

    private static final int MAX_SUGGESTIONS = 200;
    private static final int SCROLL_PIXELS = 20;
    
    // Add by Jeremy '10, 3, 29.
    // Suggestions size. Set to MAX_GUGGESTIONS if larger then it.
    private int count =0 ;
    
    private int[] mWordWidth = new int[MAX_SUGGESTIONS];
    private int[] mWordX = new int[MAX_SUGGESTIONS];

    private static final int X_GAP = 10;
    
    private static final List<Mapping> EMPTY_LIST = new LinkedList<Mapping>();

    private int currentX;
    private int mColorNormal;
    private int mColorDictionary;
    private int mColorRecommended;
    private int mColorOther;
    private int mVerticalPadding;
    private Paint mPaint;
    private boolean mScrolled;
    private int mTargetScrollX;
    
    private int mTotalWidth;
    
    private boolean goLeft = false;
    private boolean goRight = false;
    private boolean hasSlide = false;
    private int bgcolor = 0;
    
    private GestureDetector mGestureDetector;

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
        mVerticalPadding = r.getDimensionPixelSize(R.dimen.candidate_vertical_padding);
        
        mPaint = new Paint();
        mPaint.setColor(mColorNormal);
        mPaint.setAntiAlias(true);
        mPaint.setTextSize(r.getDimensionPixelSize(R.dimen.candidate_font_height));
        mPaint.setStrokeWidth(0);

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
        final int touchX = mTouchX;
        final int scrollX = getScrollX();
        final boolean scrolled = mScrolled;
        final boolean typedWordValid = mTypedWordValid;
        final int y = (int) (((height - mPaint.getTextSize()) / 2) - mPaint.ascent());

        // Modified by jeremy '10, 3, 29.  Update mselectedindex if touched and build wordX[i] and wordwidth[i]
        int x = 0;
        for (int i = 0; i < count; i++) {
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
        for (int i = 0; i < count; i++) {
        	String suggestion = mSuggestions.get(i).getWord();
            
            if (canvas != null) {
                //if ((i == 1 && !typedWordValid) || (i == 0 && typedWordValid)) {
                //    paint.setFakeBoldText(true);
                //    paint.setColor(mColorRecommended);
                
                if(mSuggestions.get(i).isDictionary()){
                	if(i == 0){
                    	paint.setColor(mColorDictionary);
                    } else if (i != 0) {
                        paint.setColor(mColorDictionary);
                    }
                }else{
                    if(i == 0){
                    	paint.setColor(mColorRecommended);
                    } else if (i != 0) {
                        paint.setColor(mColorOther);
                    }
                }
                canvas.drawText(suggestion, mWordX[i] + X_GAP, y, paint);
                paint.setColor(mColorOther); 
                canvas.drawLine(mWordX[i] + mWordWidth[i] + 0.5f, bgPadding.top, 
                		mWordX[i] + mWordWidth[i] + 0.5f, height + 1, paint);
                paint.setFakeBoldText(false);
            }
           
        }
       
        if (mTargetScrollX != getScrollX()) {
            scrollToTarget();
        }
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
    
    public void setSuggestions(List<Mapping> suggestions, boolean completions,
            boolean typedWordValid) {
        clear();
        if (suggestions != null) {
            mSuggestions = new LinkedList<Mapping>(suggestions);
        }
        
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
            setBackgroundColor(0);
        }
        
        
        
        
        mTypedWordValid = typedWordValid;
        scrollTo(0, 0);
        mTargetScrollX = 0;
        // Compute the total width
        onDraw(null);
        invalidate();
        requestLayout();
    }

    public void clear() {
    	currentX = 0;
        mSuggestions = EMPTY_LIST;
        mTouchX = OUT_OF_BOUNDS;
        mSelectedIndex = -1;
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
}
