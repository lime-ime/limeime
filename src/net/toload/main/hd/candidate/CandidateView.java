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

package net.toload.main.hd.candidate;

import android.content.Context;
import android.content.res.Resources;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.os.Handler;
import android.os.Message;
import android.util.AttributeSet;
import android.util.Log;
import android.view.Display;
import android.view.GestureDetector;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup.LayoutParams;
import android.view.WindowManager;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.PopupWindow;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.LinkedList;
import java.util.List;
import java.lang.Math;

import net.toload.main.hd.LIMEService;
import net.toload.main.hd.R;
import net.toload.main.hd.global.LIMEPreferenceManager;
import net.toload.main.hd.global.Mapping;

/**
 * @author Art Hung
 */
public class CandidateView extends View implements View.OnClickListener 
{
	
	private static final boolean DEBUG = false;
	private static final String TAG = "CandidateView";

    protected static final int OUT_OF_BOUNDS = -1;

    protected LIMEService mService;
    protected List<Mapping> mSuggestions;
    protected int mSelectedIndex;
    protected int mTouchX = OUT_OF_BOUNDS;
    protected Drawable mSelectionHighlight;
    //private boolean mTypedWordValid;
    private boolean mShowNumber; //Jeremy '11,5,25 for showing physical keyboard number or not.
    
    protected Rect mBgPadding;
    protected Rect cursorRect=null; //Jeremy '11,7,25 for store current cursor rect

    private static final int MAX_SUGGESTIONS = 200;
    private static final int SCROLL_PIXELS = 20;
    
    // Add by Jeremy '10, 3, 29.
    // Suggestions size. Set to MAX_GUGGESTIONS if larger then it.
    protected int mCount =0 ;
    //Composing view
	private TextView mComposingTextView;
	private PopupWindow mComposingTextPopup;
	//private int mDescent;
	private String mComposingText = "";
    
    protected int[] mWordWidth = new int[MAX_SUGGESTIONS];
    protected int[] mWordX = new int[MAX_SUGGESTIONS];

    protected static int X_GAP = 12;
    
    private static final List<Mapping> EMPTY_LIST = new LinkedList<Mapping>();

	

    private int currentX;
    protected int mColorNormal;
    protected int mColorInverted;
    protected int mColorDictionary;
    protected int mColorRecommended;
    protected int mColorOther;
    protected int mColorNumber;
    //private int mVerticalPadding;
    protected Paint mPaint;
    protected Paint nPaint;
    private Paint cPaint;
    private boolean mScrolled;
    protected int mTargetScrollX;
    private String mDisplaySelkey = "1234567890";
    
    protected int mTotalWidth;
    
    private boolean goLeft = false;
    private boolean goRight = false;
    private boolean hasSlide = false;
    private int bgcolor = 0;
    
    private View mCandidatePopupContainer;
    private PopupWindow  mCandidatePopup;
    
    protected int mScreenWidth;
    protected int mScreenHeight;
    
    protected GestureDetector mGestureDetector;
    private final Context mContext;
    private final boolean isAndroid3; //'11,8,11, Jeremy
    
    protected LIMEPreferenceManager mLIMEPref;
    
    private CandidateExpandedView mPopupCandidateView;
    private int mCloseButtonHeight;
    private ScrollView mPopupScrollView;
    private boolean candidateExpanded = false;
 
   // private Context ctx;

    /**
     * Construct a CandidateView for showing suggested words for completion.
     * @param context
     * @param attrs
     */
    public CandidateView(Context context, AttributeSet attrs) {

    	super(context, attrs);
    	
    	mContext = context;
    	 //Jeremy '11,8,11 detect API level and show keyboard number only in 3.0+ 
    	isAndroid3 = Integer.parseInt(android.os.Build.VERSION.SDK) >11; 
    	
    	mLIMEPref = new LIMEPreferenceManager(context);

    	Resources r = context.getResources();
    	
    	Display display = ((WindowManager) context.getSystemService(Context.WINDOW_SERVICE)).getDefaultDisplay(); 
    	mScreenWidth =  display.getWidth();
    	mScreenHeight = display.getHeight();
    	
    	mSelectionHighlight = r.getDrawable(
    			android.R.drawable.list_selector_background);
    	mSelectionHighlight.setState(new int[] {
    			android.R.attr.state_enabled,
    			android.R.attr.state_focused,
    			android.R.attr.state_window_focused,
    			android.R.attr.state_pressed
    	});
    	
    	bgcolor = r.getColor(R.color.candidate_background);

    	mColorNormal = r.getColor(R.color.candidate_normal);
    	mColorInverted = r.getColor(R.color.candidate_inverted);
    	mColorDictionary = r.getColor(R.color.candidate_dictionary);
    	mColorRecommended = r.getColor(R.color.candidate_recommended);
    	mColorOther = r.getColor(R.color.candidate_other);
    	mColorNumber = r.getColor(R.color.candidate_number);
    	//mVerticalPadding = r.getDimensionPixelSize(R.dimen.candidate_vertical_padding);
    
    	mPaint = new Paint();
    	mPaint.setColor(mColorNormal);
    	mPaint.setAntiAlias(true);
    	mPaint.setTextSize(r.getDimensionPixelSize(R.dimen.candidate_font_size));
    	mPaint.setStrokeWidth(0);

    	nPaint = new Paint();
    	nPaint.setColor(mColorNumber);
    	nPaint.setAntiAlias(true);
    	nPaint.setTextSize(r.getDimensionPixelSize(R.dimen.candidate_number_font_size));
    	nPaint.setStyle(Paint.Style.FILL_AND_STROKE);

    	
    	//mDescent = (int) mPaint.descent();

    	//final SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(context);

    	mGestureDetector = new GestureDetector(new GestureDetector.SimpleOnGestureListener() {
    		@Override
    		public boolean onScroll(MotionEvent e1, MotionEvent e2,
    				float distanceX, float distanceY) {
    			mScrolled = true;
    			
    			// Update full candidate list before scroll
    			checkHasMoreRecords();
    			
    			
    			
    			int sx = getScrollX();
    			sx += distanceX;
    			if (sx < 0) {
    				sx = 0;
    			}
    			if (sx + getWidth() > mTotalWidth) {                    
    				sx -= distanceX;
    			}

    			if(mLIMEPref.getParameterBoolean("candidate_switch", false)){
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

    			//invalidate();
    			return true;
    		}
    	});
    	/*setHorizontalFadingEdgeEnabled(true);
    	setWillNotDraw(false);
    	setHorizontalScrollBarEnabled(false);
    	setVerticalScrollBarEnabled(false);*/
    }
    final Handler mHandler = new Handler();
    // Create runnable for posting
    final Runnable mUpdateUI = new Runnable() {
    	public void run() { 

    		if (mSuggestions == null) {
    			setBackgroundColor(0);
    			hideCandidatePopup();
    			return;
    		}
    		setBackgroundColor(bgcolor);
    		if(mCandidatePopup != null && mCandidatePopup.isShowing()){
    			showCandidatePopup();

    		}else{
    			onDraw(null);
    			invalidate();
    		}
    		requestLayout();
    	}
    };
    final Runnable mShowComping = new Runnable() {
    	public void run() { 
    		if(mComposingTextPopup!=null
        			&& mComposingText != null && !mComposingText.trim().equals("")){
    			mComposingTextView.setText(mComposingText);
    		}
    	}

    };
    final Runnable mDissmissComping = new Runnable() {
    	public void run() { 
    		if(mComposingTextPopup!=null && mComposingTextPopup.isShowing()) {
    			mComposingTextPopup.dismiss();
    		}
    	}
    };
    final Runnable mDissmissCandidatePopup = new Runnable() {
    	public void run() { 
    		if(mCandidatePopup!=null && mCandidatePopup.isShowing()) 
    			mCandidatePopup.dismiss();
    		requestLayout();
    	}
    };
    
/*
 * 
	private static final int MSG_UPDATEUI= 1;   
	Handler mHandler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case MSG_UPDATEUI:
                	updateUI();
                    break;
               
            }
            
        }
    };*/
    
    
    
    public void showCandidatePopup(){

    	candidateExpanded =true;
    	requestLayout();
    	
    	checkHasMoreRecords();
    	if(mCandidatePopup == null){
    		if(DEBUG) 
    			Log.i(TAG, "showCandidatePopup(), creating popup windows");
    		mCandidatePopup = new PopupWindow(mContext);	
    		LayoutInflater inflater = (LayoutInflater) mContext.getSystemService(
    				Context.LAYOUT_INFLATER_SERVICE);
    		mCandidatePopupContainer = inflater.inflate(R.layout.candidatepopup, null);
		
        	mCandidatePopup.setContentView(mCandidatePopupContainer);
     	
        	View closeButton = mCandidatePopupContainer.findViewById(R.id.closeButton);
    		if (closeButton != null) closeButton.setOnClickListener(this);
    		
        	ImageButton btnClose = (ImageButton) mCandidatePopupContainer.findViewById(R.id.closeButton);
          	btnClose.measure(MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED),
                    MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED));
          	mCloseButtonHeight =btnClose.getMeasuredHeight();
          	
          	ScrollView sv=(ScrollView)mCandidatePopupContainer.findViewById(R.id.sv);
          	mPopupScrollView = sv;
          	
          	CandidateExpandedView popupCandidate = 
        		(CandidateExpandedView)mCandidatePopupContainer.findViewById(R.id.candidatePopup);
        	popupCandidate.setParentCandidateView(this);
        	popupCandidate.setParentScrollView(sv);
        	popupCandidate.setService(mService);
        	
        	mPopupCandidateView = popupCandidate;

          	
          
    	}

    	if(mSuggestions.size()==0) return;
		  	
    	mCandidatePopup.setContentView(mCandidatePopupContainer);
    	int [] offsetOnScreen = new int[2];
    	this.getLocationOnScreen(offsetOnScreen);
    	
    	mPopupCandidateView.setSuggestions(mSuggestions);
    	mPopupCandidateView.prepareLayout();
    	
    	mPopupCandidateView.measure(MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED),
                MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED));
    	
    	int popHeight = mScreenHeight - offsetOnScreen[1] ;
    	if(mPopupCandidateView.getMeasuredHeight()+mCloseButtonHeight < popHeight)
    		popHeight = mPopupCandidateView.getMeasuredHeight()+ mCloseButtonHeight;
    	
    	if(DEBUG)
        	Log.i(TAG, "showCandidatePopup(), screenHeight=" + mScreenHeight 
        			+ " offsetOnScreen[1] = " + offsetOnScreen[1]
        			+ " popHeight = " + popHeight   
        			+ " CandidateExpandedView.measureHeight" + mPopupCandidateView.getMeasuredHeight()
        			+ "showCandidatePopup(), popupwidth=" + mScreenWidth 
    				+ " btnClose.getMeasuredHeight() = " +	mCloseButtonHeight);
    	
        
        mPopupScrollView.setLayoutParams(new LinearLayout.LayoutParams(LinearLayout.LayoutParams.FILL_PARENT
    			, popHeight- mCloseButtonHeight));
    	mCandidatePopup.setWidth(mScreenWidth);
    	mCandidatePopup.setHeight(popHeight);// popHeight); //tv.getMeasuredHeight()+btnClose.getMeasuredHeight());
    	
    	
    	if(mCandidatePopup.isShowing())
    		mCandidatePopup.update(0, 0, mScreenWidth, popHeight );
    	else{
    		mCandidatePopup.setWidth(mScreenWidth);
        	mCandidatePopup.setHeight(popHeight);
    		mCandidatePopup.showAsDropDown(this, 0, -getHeight());
    		mPopupScrollView.scrollTo(0, 0);
    		//mCandidatePopup.showAtLocation(this, Gravity.NO_GRAVITY, 0, 0);
    	}
    	
    }
    
    public void setComposingText(String composingText){
    	if(mComposingTextPopup!=null
    			&& composingText != null && !composingText.trim().equals("")){
        	mComposingText = composingText;
        	mHandler.post(mShowComping); 	//mComposingTextView.setText(mComposingText);
    	}else{
    		mComposingText = "";
    		mHandler.post(mDissmissComping);//hideComposing();
    	}
    	
    	 if(DEBUG) Log.i("Canddateview:setComposingText()","mComposingText:"+mComposingText);
    }
    public String getComposingText(String ComposingText){
		return mComposingText;
 	
    }
    public void showComposing() {
    	if(DEBUG) Log.i("candidateview","showcomposing()");
    	
    	// Composing buffer textView
    	if(mComposingTextPopup==null){
    		LayoutInflater inflater 
    			= (LayoutInflater) mContext.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
    		mComposingTextPopup = new PopupWindow(mContext);
    		mComposingTextView = (TextView) inflater.inflate(R.layout.composingtext, null);
    		mComposingTextPopup.setWindowLayoutMode(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
    		mComposingTextPopup.setContentView(mComposingTextView);
    		mComposingTextPopup.setBackgroundDrawable(null);
    	}

    	if(cPaint == null){
    		cPaint = new Paint();
    		cPaint.setColor(mColorNormal);
    		cPaint.setAntiAlias(true);
    		cPaint.setTextSize(mContext.getResources().getDimensionPixelSize(R.dimen.composing_text_size));
    		cPaint.setStrokeWidth(0);
    	}
    	
        if (!mComposingText.equals("")) {	
            	mComposingTextPopup.setContentView(mComposingTextView);
                mComposingTextView.setText(mComposingText);
                mComposingTextView.measure(MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED), 
                        MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED));
                int wordWidth = (int) (cPaint.measureText(mComposingText, 0, mComposingText.length()));
                	
                final int popupWidth =  wordWidth
                        + mComposingTextView.getPaddingLeft() + mComposingTextView.getPaddingRight();
                final int popupHeight = mComposingTextView.getMeasuredHeight();
                //mPreviewText.setVisibility(INVISIBLE);
                
                
                //mHandler.removeMessages(MSG_REMOVE_COMPOSING);
                int [] offsetInWindow = new int[2];
                this.getLocationInWindow(offsetInWindow);
                int mPopupComposingY = offsetInWindow[1];
                int mPopupComposingX = 0;
                
               if(DEBUG) Log.i("CandiateView:showcomposing()", " candidateview offsetInWindow x:" 
                		+offsetInWindow[0] + ". y:" +offsetInWindow[1]);
                // Show popup windows at the location of cursor  Jeremy '11,7,25
                // Rely on onCursorUpdate() of inputmethod service, which is not implemented on standard android 
                // Working on htc implementations
                if(offsetInWindow[1] == 0 && cursorRect != null){
                	mPopupComposingX = cursorRect.left;
                	int [] offsetOnScreen = new int[2];
                	this.getLocationOnScreen(offsetOnScreen);
                	mPopupComposingY -= offsetOnScreen[1]- cursorRect.top -  popupHeight;
                	if(mPopupComposingY > -popupHeight){
                		mPopupComposingY -= 2* popupHeight;
                	}
                	if(mPopupComposingY > 0){
                		mPopupComposingY= -popupHeight;
                		
                	}
                	 if(DEBUG) Log.i("CandiateView:showcomposing()", " candidateview offsetOnScreen x:" 
                     		+offsetOnScreen[0] + ". y:" +offsetOnScreen[1]);
                }else{
                	mPopupComposingY -= popupHeight;
                }
                	
                //if(DEBUG) 
                	Log.i(TAG, "CandiateView:showcomposing():mPopupComposingX:" 
                 		+mPopupComposingX + ". mPopupComposingY:" +mPopupComposingY
                 		+"mComposingTextPopup.isShowing()=" + mComposingTextPopup.isShowing() );
                
                if (mComposingTextPopup.isShowing()) {              	
                	mComposingTextPopup.update(mPopupComposingX, mPopupComposingY, 
                            popupWidth, popupHeight);
                } else {
                	mComposingTextPopup.setWidth(popupWidth);
                	mComposingTextPopup.setHeight(popupHeight);
                	mComposingTextPopup.showAtLocation(this, Gravity.NO_GRAVITY, mPopupComposingX, 
                			mPopupComposingY );
                }
               // mComposingTextView.setVisibility(VISIBLE);


            }
        
    }

    
    public void hideComposing() {
    	if(DEBUG) Log.i(TAG, "hidecomposing()");
    	mHandler.post(mDissmissComping);
    	
    }
    
    
    public void hideCandidatePopup() {
    	if(DEBUG) 
    		Log.i(TAG, "hideCandidatePopup()");
    	
    	candidateExpanded = false;
    	mHandler.post(mDissmissCandidatePopup);
    
    
    	/*if(mCandidatePopup!=null && mCandidatePopup.isShowing()) 
    		mCandidatePopup.dismiss();
        requestLayout();*/
    }
    
    public boolean isCandidateExpanded(){
    	return candidateExpanded;
    }
    
    public boolean hasRoomForExpanding(){ 
    	int [] offsetOnScreen = new int[2];
    	this.getLocationOnScreen(offsetOnScreen);
    	return (mScreenHeight - offsetOnScreen[1]) > 2 * getHeight();
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
        int measuredWidth = resolveSize(mTotalWidth, widthMeasureSpec);
        
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
    	//if(DEBUG)
    		Log.i(TAG, "Candidateview:OnDraw():Suggestion mCount:" + mCount+" mSuggestions.size:" + mSuggestions.size());
    	
        //if (canvas != null) {
        //    super.onDraw(canvas);
        //}  // Jeremy '11,8,12 all draw by ourself.
        mTotalWidth = 0;
        if (mSuggestions == null) return;
        
        if (mBgPadding == null) {
            mBgPadding = new Rect(0, 0, 0, 0);
            if (getBackground() != null) {
                getBackground().getPadding(mBgPadding);
            }
        }
        
        //final int mCount = mSuggestions.size(); 
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
        final int count = mCount; //Cache count here '11,8,18
        for (int i = 0; i < count; i++) {
        	if(DEBUG)
        		Log.i(TAG, "Candidateview:OnDraw():updating:" + i );
        	if(count!=mCount || i >= mCount) return;  // mSuggestion is updated, force abort
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
        requestLayout();
       
        //Jeremy '11,8,11. If the candidate list is within 1 page and has more records, get full records first.
        if(mTotalWidth < this.getWidth() )  checkHasMoreRecords();
 
			
 
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
        		if(count!=mCount || i >= mCount) break;
        		if(DEBUG)
        			Log.i(TAG, "Candidateview:OnDraw():i:" + i + "  Drawing:" + mSuggestions.get(i).getWord() );
        		
        	
                //if ((i == 1 && !typedWordValid) || (i == 0 && typedWordValid)) {
                //    paint.setFakeBoldText(true);
                //    paint.setColor(mColorRecommended);
            	String suggestion = mSuggestions.get(i).getWord();
            	int mCount = i+1;
            	
                if(mSuggestions.get(i).isDictionary()){
                	npaint.setColor(mColorRecommended);
                    paint.setColor(mColorDictionary);
                }else{
                	npaint.setColor(mColorOther);
                    if(i == 0){
                    	if(mSelectedIndex == 0) paint.setColor(mColorInverted);
                    	else paint.setColor(mColorRecommended);
                    } else if (i != 0) {
                        paint.setColor(mColorOther);
                    }
                }
                canvas.drawText(suggestion, mWordX[i] + X_GAP, y, paint);
                if(mShowNumber){
                	//Jeremy '11,6,17 changed from <=10 to mDisplaySekley length. The length maybe 11 or 12 if shifted with space.
                	if(mCount <= mDisplaySelkey.length()){
                		//Jeremy '11,6,11 Drawing text using relative font dimensions.
                		canvas.drawText(mDisplaySelkey.substring(mCount-1, mCount), 
                				mWordX[i] + mWordWidth[i] - height * 0.3f ,  height * 0.4f, npaint);
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
        
        showComposing();
    }
    
    private boolean checkHasMoreRecords(){
    	if(mSuggestions!=null && mSuggestions.get(mSuggestions.size()-1).getCode() !=null
        		&& mSuggestions.get(mSuggestions.size()-1).getCode().equals("has_more_records")){
    		Thread UpadtingThread = new Thread(){
    			public void run() {
    				mService.pickSuggestionManually(mSuggestions.size()-1);
    			}
    		};
			UpadtingThread.start();
        	return true;
    	}
    	return false;
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
    public void setSuggestions(List<Mapping> suggestions){
    	setSuggestions(suggestions, false, false, "");
    }
    public void setSuggestions(List<Mapping> suggestions, boolean showNumber, boolean typedWordValid, String displaySelkey) {
    	mDisplaySelkey = displaySelkey;
    	setSuggestions(suggestions, showNumber, typedWordValid);
    	
    }
    public void setSuggestions(List<Mapping> suggestions, boolean showNumber, boolean typedWordValid) {
        //clear();
    	//Jeremy '11,8,14 moved from clear();
    	currentX = 0;
        mTouchX = OUT_OF_BOUNDS;
        mCount =0;
        mSelectedIndex =-1;
        //setComposingText("");
        
        mShowNumber = showNumber && isAndroid3;
        
        if(mShowNumber)
        	X_GAP = (int) (mContext.getResources().getDimensionPixelSize(R.dimen.candidate_font_size)*0.35f);//13;
        else 
        	X_GAP = (int) (mContext.getResources().getDimensionPixelSize(R.dimen.candidate_font_size)*0.25f);;
    
        if (suggestions != null) {
            mSuggestions = new LinkedList<Mapping>(suggestions);
	       
	        if(DEBUG)
	        	Log.i("CandidateView:setSuggestions()", "mSuggestions.get(0).getWord():" + mSuggestions.get(0).getWord()
            			+ " mSuggestions.get(1).getCode():" + mSuggestions.get(1).getCode().toLowerCase());
	           
	        
	        if(mSuggestions != null && mSuggestions.size() > 0){
	            //setBackgroundColor(bgcolor);
	            // Add by Jeremy '10, 3, 29
	            mCount = mSuggestions.size(); 
	            if(mCount > MAX_SUGGESTIONS) mCount = MAX_SUGGESTIONS;
	            
	                
	            if(mSuggestions.get(0).isDictionary()){
	            	// no default selection for related words
	            	mSelectedIndex = -1;
	            //}else if(mSuggestions.size() == 1){
	            	//mSelectedIndex = 0;
	            }else if(mCount > 1 && mSuggestions.get(1).getId() !=null && // the suggestion is not from relatedlist.
	            		mSuggestions.get(0).getWord().toLowerCase()
	            		.equals(mSuggestions.get(1).getCode().trim())) { // exact match
	            	// default selection on suggestions 1 (0 is typed English in mixed English mode)
	             	mSelectedIndex = 1;
	            }else if(mCount > 1 && mSuggestions.get(1).getId() !=null &&
	            		 mService.keyboardSelection.equals("phonetic")&&
	            		 (mSuggestions.get(0).getWord().trim().toLowerCase()
	     	            		.equals(mSuggestions.get(1).getCode().trim())||
	     	              mSuggestions.get(0).getWord().trim().toLowerCase()
	    	     	        	.equals(mSuggestions.get(1).getCode().trim().replaceAll("[3467]", ""))) ){
	            	mSelectedIndex = 1;
	            }else {
	            	mSelectedIndex = 0;
	            }
	        }else{
	        	 if(DEBUG)
	 	        	Log.i(TAG, "setSuggestions():mSuggestions=null");
	            //setBackgroundColor(0);
	        }
	        
	        //mTypedWordValid = typedWordValid;
	        //scrollTo(0, 0);
	        //mTargetScrollX = 0;
	        // Compute the total width
	       
	    	
        }else{
        	mSuggestions = new LinkedList<Mapping>();
        	//hideCandidatePopup();
        }
        
        mHandler.post(mUpdateUI);
        /*//Jeremy '11,8,18 moved to mHandler to be thread-safe.
        if(mCandidatePopup != null && mCandidatePopup.isShowing()){
    		showCandidatePopup();
    		
    	}else{
    		onDraw(null);
 	        invalidate();
    	}
        requestLayout();*/
      
    }

    

    public void clear() {
    	if(DEBUG) Log.i("Canddateview","clear()");
    	
    	mSuggestions = EMPTY_LIST;
        // Jeremy 11,8,14 close all popup on clear
        setComposingText("");
        mTargetScrollX = 0;
        hideComposing();
        hideCandidatePopup();
        
    }
    
    @Override
    public boolean onTouchEvent(MotionEvent me) {

        if (mGestureDetector!=null && mGestureDetector.onTouchEvent(me)) {
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
                	takeSelectedSuggestion();
                    mSelectedIndex = -1;
                }
            }
            invalidate();
            break;
        case MotionEvent.ACTION_UP:
            if (!mScrolled) {
                if (mSelectedIndex >= 0) {
                    takeSelectedSuggestion();
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
        //final int mCount = mSuggestions.size();
        int firstItem = 0; // Actually just before the first item, if at the boundary
        while (i < mCount) {
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
        //final int mCount = mSuggestions.size();
        int rightEdge = currentX + getWidth();
        while (i < mCount) {
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
        	mTargetScrollX = targetX;
            requestLayout();
            invalidate();
            mScrolled = true;
        }
    }
    
    //Add by Jeremy '10, 3, 29 for DPAD (physical keyboard) selection.
    public void selectNext() {
    	if (mSuggestions == null) return;
    	if(mSelectedIndex < mCount-1){
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
    
    public boolean takeSuggstionAtIndex(int index){
    	if(DEBUG){
    		Log.i(TAG, "takeSuggestion():mSelectedIndex:" + mSelectedIndex);
    	}
    	hideCandidatePopup();

    	if (mSuggestions != null && index >= 0 && index <= mSuggestions.size() ) {
    		mService.pickSuggestionManually(index);
    		return true;  // Selection picked
        }else
        	return false;
    }
    
    public boolean takeSelectedSuggestion(){
    	if(DEBUG){
    		Log.i(TAG, "takeSelectedSuggestion():mSelectedIndex:" + mSelectedIndex);
    	}
    	return takeSuggstionAtIndex(mSelectedIndex);
    		
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
        takeSelectedSuggestion();
        invalidate();
    }

    private void removeHighlight() {
        mTouchX = OUT_OF_BOUNDS;
        invalidate();
    }
    
    @Override
    public void onDetachedFromWindow() {
    	if(DEBUG) Log.i(TAG,"onDetachedFromWindow() ");
        super.onDetachedFromWindow();
        hideComposing();
        hideCandidatePopup();
    }
    
    public void onUpdateCursor(Rect newCursor) {
    	cursorRect = newCursor;
    	invalidate();
    }
	@Override
	public void onClick(View v) {
		hideCandidatePopup();
	}


}
