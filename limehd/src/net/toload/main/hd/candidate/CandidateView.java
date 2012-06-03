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

import android.annotation.TargetApi;
import android.content.Context;
import android.content.res.Resources;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Point;
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

    private static final int MAX_SUGGESTIONS = 500;
    private static final int SCROLL_PIXELS = 20;
    
    // Add by Jeremy '10, 3, 29.
    // Suggestions size. Set to MAX_GUGGESTIONS if larger then it.
    protected int mCount =0 ;
    //Composing view
	private TextView mComposingTextView;
	private PopupWindow mComposingTextPopup;
	
	//private String mComposingText = "";
    
    protected int[] mWordWidth = new int[MAX_SUGGESTIONS];
    protected int[] mWordX = new int[MAX_SUGGESTIONS];

    protected static int X_GAP = 12;
    
    private static final List<Mapping> EMPTY_LIST = new LinkedList<Mapping>();

	
    protected int mHeight;
    private int currentX;
    protected final int mColorNormal;
    protected final int mColorInverted;
    protected final int mColorDictionary;
    protected final int mColorRecommended;
    protected final int mColorOther;
    protected final int mColorNumber;
    protected int mVerticalPadding;
    protected int mExpandButtonWidth;
    
    protected Paint mPaint;
    protected Paint nPaint;
    //private Paint cPaint;
    private boolean mScrolled;
    protected int mTargetScrollX;
    private String mDisplaySelkey = "1234567890";
    
    protected int mTotalWidth;
    
    private boolean goLeft = false;
    private boolean goRight = false;
    private boolean hasSlide = false;
    //private int bgcolor = 0;
    
    private View mCandidatePopupContainer;
    private PopupWindow  mCandidatePopupWindow;
    
    protected int mScreenWidth;
    protected int mScreenHeight;
    
    protected GestureDetector mGestureDetector;
    protected final Context mContext;
    private final boolean isAndroid3; //'11,8,11, Jeremy
    
    protected LIMEPreferenceManager mLIMEPref;
    
    private CandidateExpandedView mPopupCandidateView;
    private int mCloseButtonHeight;
    private ScrollView mPopupScrollView;
    private boolean candidateExpanded = false;

    private boolean waitingForMoreRecords = false;
    
    //private Rect padding = null;

    /**
     * Construct a CandidateView for showing suggested words for completion.
     * @param context
     * @param attrs
     */
    @TargetApi(13)
    @SuppressWarnings("deprecation")
	public CandidateView(Context context, AttributeSet attrs) {

    	super(context, attrs);
    	
    	mContext = context;
    	 //Jeremy '11,8,11 detect API level and show keyboard number only in 3.0+ 
    	isAndroid3 = android.os.Build.VERSION.SDK_INT >11; 
    	
    	mLIMEPref = new LIMEPreferenceManager(context);

    	Resources r = context.getResources();
    	
    	Display display = ((WindowManager) context.getSystemService(Context.WINDOW_SERVICE)).getDefaultDisplay();
    	if(android.os.Build.VERSION.SDK_INT < 13) {
    		mScreenWidth =  display.getWidth();
    		mScreenHeight = display.getHeight();
    	}else{
    		Point screenSize = new Point();
    		display.getSize(screenSize);
    		mScreenWidth = screenSize.x;
    		mScreenHeight = screenSize.y; 
    		
    	}
    		
    	
    	mSelectionHighlight = r.getDrawable(
    			R.drawable.list_selector_background);
    			//android.R.drawable.list_selector_background);
    	mSelectionHighlight.setState(new int[] {
    			android.R.attr.state_enabled,
    			android.R.attr.state_focused,
    			android.R.attr.state_window_focused,
    			android.R.attr.state_pressed
    	});
    	
    	//bgcolor = r.getColor(R.color.candidate_background);

    	mColorNormal = r.getColor(R.color.candidate_normal);
    	mColorInverted = r.getColor(R.color.candidate_inverted);
    	mColorDictionary = r.getColor(R.color.candidate_dictionary);
    	mColorRecommended = r.getColor(R.color.candidate_recommended);
    	mColorOther = r.getColor(R.color.candidate_other);
    	mColorNumber = r.getColor(R.color.candidate_number);
    	mVerticalPadding =(int)( r.getDimensionPixelSize(R.dimen.candidate_vertical_padding)*mLIMEPref.getFontSize());
    	mHeight = (int) (r.getDimensionPixelSize(R.dimen.candidate_stripe_height) *mLIMEPref.getFontSize()); 
    	mExpandButtonWidth = r.getDimensionPixelSize(R.dimen.candidate_expand_button_width);// *mLIMEPref.getFontSize());
    	
    	mPaint = new Paint();
    	mPaint.setColor(mColorNormal);
    	mPaint.setAntiAlias(true);
    	mPaint.setTextSize(r.getDimensionPixelSize(R.dimen.candidate_font_size)*mLIMEPref.getFontSize());
    	mPaint.setStrokeWidth(0);

    	nPaint = new Paint();
    	nPaint.setColor(mColorNumber);
    	nPaint.setAntiAlias(true);
    	nPaint.setTextSize(r.getDimensionPixelSize(R.dimen.candidate_number_font_size)*mLIMEPref.getFontSize());
    	nPaint.setStyle(Paint.Style.FILL_AND_STROKE);


    

    	//final SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(context);
    	//Jeremy '12,4,23 add mContext parameter.  The construstor without context is deprecated
    	mGestureDetector = new GestureDetector(mContext, new GestureDetector.SimpleOnGestureListener() {
    		@Override
    		public boolean onScroll(MotionEvent e1, MotionEvent e2,
    				float distanceX, float distanceY) {
    			
    			if(DEBUG)
    				Log.i(TAG, "onScroll(): distanceX = " + distanceX + "; distanceY = " + distanceY );
    				
    		
    			//Jeremy '12,4,8 filter out small scroll which is actually candidate selection.
    			if(Math.abs(distanceX) < mHeight/5 && Math.abs(distanceY) < mHeight/5 ) return true;
    			
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
    
    private final UIHandler mHandler = new UIHandler();
    class UIHandler extends Handler {
        private static final int MSG_UPDATE_UI = 1;
        private static final int MSG_UPDATE_COMPOSING = 2;
        private static final int MSG_HIDE_COMPOSING = 3;
        private static final int MSG_SHOW_CANDIDATE_POPUP = 4;
        private static final int MSG_HIDE_CANDIDATE_POPUP = 5;
        private static final int MSG_SET_COMPOSING = 6;
        @Override
        public void handleMessage(Message msg) {
        	if(DEBUG) Log.i(TAG,"UIHandler.handlMessage(): message:" + msg.what);
            switch (msg.what) {
                case MSG_UPDATE_UI:
                	doUpdateUI();
                    break;
                case MSG_UPDATE_COMPOSING:
                	doUpdateComposing();
                    break;
                case MSG_HIDE_COMPOSING: {
                	if(mComposingTextPopup!=null && mComposingTextPopup.isShowing()) {
            			mComposingTextPopup.dismiss();
            		}
                    break;
                }
                case MSG_SHOW_CANDIDATE_POPUP: {
                	doUpdateCandidatePopup();
                    break;
                }
                case MSG_HIDE_CANDIDATE_POPUP: {
                	doHideCandidatePopup();
                	break;
                }
                case MSG_SET_COMPOSING: {
                	String composingText = (String) msg.obj;
                	if(DEBUG) Log.i(TAG, "UIHandler.handleMessage(): compsoingText" + composingText);
                	doSetComposing(composingText);
                    break;
                }
            }
        }
		
        public void updateUI (int delay){
        	sendMessageDelayed(obtainMessage(MSG_UPDATE_UI, 0, 0, null), delay);
        	
        }

        public void setComposing (String text ,int delay){
        	sendMessageDelayed(obtainMessage(MSG_SET_COMPOSING, 0, 0, text), delay);
        }
        
        public void updateComposing (int delay){
        	sendMessageDelayed(obtainMessage(MSG_UPDATE_COMPOSING, 0, 0, null), delay);
        }
        public void  dismissComposing(int delay) {
        	sendMessageDelayed(obtainMessage(MSG_HIDE_COMPOSING, 0, 0, null), delay);	
        }

        public void  showCandidatePopup(int delay) {
        	sendMessageDelayed(obtainMessage(MSG_SHOW_CANDIDATE_POPUP, 0, 0, null), delay);	
        }

        public void  dismissCandidatePopup(int delay) {
        	sendMessageDelayed(obtainMessage(MSG_HIDE_CANDIDATE_POPUP, 0, 0, null), delay);	
        }
        
       
        
    }

    
    public void doUpdateUI() {
    	
    	if(DEBUG)
    		Log.i(TAG,"doUpdateUI()");
    	
    	if (mSuggestions == null) {
    		//setBackgroundColor(0);
    		hideCandidatePopup();
    		return;
    	}
    	//setBackgroundColor(bgcolor);
    	
    	
    	
    	if(mCandidatePopupWindow != null && mCandidatePopupWindow.isShowing()){
    		//doHideCandidatePopup();
    		doUpdateCandidatePopup();
    		

    	}else{
    		if(!waitingForMoreRecords){  // New suggestion list, reset scroll to (0,0);
    			scrollTo(0, 0);    
    	        mTargetScrollX = 0;
    		}
    		onDraw(null);
    		resetWidth();
    		invalidate();
    		requestLayout();

    		
    	}
    	waitingForMoreRecords = false;

    }
	protected void updateFontSize() {
		Resources r = mContext.getResources();
		float scaling = mLIMEPref.getFontSize();
    	//mHeight = (int) (r.getDimensionPixelSize(R.dimen.candidate_stripe_height) * scaling);
    	//mExpandButtonWidth =(int) ( r.getDimensionPixelSize(R.dimen.candidate_expand_button_width)  * scaling);
    	mVerticalPadding =(int)( r.getDimensionPixelSize(R.dimen.candidate_vertical_padding)* scaling);
    	mPaint.setTextSize(r.getDimensionPixelSize(R.dimen.candidate_font_size)* scaling);
    	nPaint.setTextSize(r.getDimensionPixelSize(R.dimen.candidate_number_font_size)* scaling);
    	if(DEBUG) Log.i(TAG, "updateFontSize(), scaling=" + scaling +", mVerticalPadding=" + mVerticalPadding);
	}
    private void doHideCandidatePopup() {
    	if(DEBUG)
    		Log.i(TAG,"doHideCandidatePopup()");
    	
    	if(mCandidatePopupWindow!=null && mCandidatePopupWindow.isShowing()) {
    		mCandidatePopupWindow.dismiss();
    		resetWidth();	
    	}
    	candidateExpanded = false;
    	invalidate();
    	requestLayout();
	}
	private void resetWidth() {
		if(DEBUG)
			Log.i(TAG,"resetWidth() mHieght:" + mHeight);
		int candiWidth = mScreenWidth;
		if(mTotalWidth > mScreenWidth) candiWidth -= mExpandButtonWidth;
		this.setLayoutParams(new LinearLayout.LayoutParams(
				candiWidth, mHeight));
	}
    public void doUpdateCandidatePopup(){
    	if(DEBUG) 
			Log.i(TAG, "doUpdateCandidatePopup(), mHeight:" + mHeight);
    	
    	//Jeremy '11,8.27 do vibrate and sound on candidateview expand button pressed.
    	if(!candidateExpanded)
    		mService.doVibrateSound(0);
    	
    	candidateExpanded =true;
    	requestLayout();
     	
    	checkHasMoreRecords();
    	    	
    	if(mCandidatePopupWindow == null){
    		
    		mCandidatePopupWindow = new PopupWindow(mContext);	
    		LayoutInflater inflater = (LayoutInflater) mContext.getSystemService(
    				Context.LAYOUT_INFLATER_SERVICE);
    		mCandidatePopupContainer = inflater.inflate(R.layout.candidatepopup, null);
		
        	mCandidatePopupWindow.setContentView(mCandidatePopupContainer);
     	
        	View closeButton = mCandidatePopupContainer.findViewById(R.id.closeButton);
    		if (closeButton != null) closeButton.setOnClickListener(this);
    		
        	ImageButton btnClose = (ImageButton) mCandidatePopupContainer.findViewById(R.id.closeButton);
          	btnClose.measure(MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED),
                    MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED));
          	mCloseButtonHeight =btnClose.getMeasuredHeight();
          	
          	mPopupScrollView=(ScrollView)mCandidatePopupContainer.findViewById(R.id.sv);
          	
          	CandidateExpandedView popupCandidate = 
        		(CandidateExpandedView)mCandidatePopupContainer.findViewById(R.id.candidatePopup);
        	popupCandidate.setParentCandidateView(this);
        	popupCandidate.setParentScrollView(mPopupScrollView);
        	popupCandidate.setService(mService);
        	
        	mPopupCandidateView = popupCandidate;

          	
          
    	}

    	if(mSuggestions.size()==0) return;
    	
    	
		  	
    	mCandidatePopupWindow.setContentView(mCandidatePopupContainer);
    	int [] offsetOnScreen = new int[2];
    	this.getLocationOnScreen(offsetOnScreen);
    	
    	mPopupCandidateView.setSuggestions(mSuggestions);
    	mPopupCandidateView.prepareLayout();
    	
    	mPopupCandidateView.measure(MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED),
                MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED));
    	
    	/*int popHeight =  3 * mHeight + mCloseButtonHeight;
    	boolean upperExpand = true;
    	if(offsetOnScreen[1] < popHeight){
    		popHeight = mScreenHeight - offsetOnScreen[1] ;
    		if(mPopupCandidateView.getMeasuredHeight()+mCloseButtonHeight < popHeight)
        		popHeight = mPopupCandidateView.getMeasuredHeight()+ mCloseButtonHeight;
    		upperExpand = false;
    	}else{
    		this.setLayoutParams(
    				new LinearLayout.LayoutParams(mScreenWidth - mExpandButtonWidth, popHeight));
    	}*/
    	
    	int popHeight = mScreenHeight - offsetOnScreen[1] ;
    	if(mPopupCandidateView.getMeasuredHeight()+mCloseButtonHeight < popHeight)
    		popHeight = mPopupCandidateView.getMeasuredHeight()+ mCloseButtonHeight;
    	
    	if(!hasRoomForExpanding() ){
    		popHeight =  3 * mHeight + mCloseButtonHeight;
    		
    		if(DEBUG)
    			Log.i(TAG, "doUpdateCandidatePopup(), " +
    					"no enough room for expanded view, expand self first. newHeight:" + popHeight);
    		
    		if(mPopupCandidateView.getMeasuredHeight()+mCloseButtonHeight < popHeight)
        		popHeight = mPopupCandidateView.getMeasuredHeight()+ mCloseButtonHeight;
    		this.setLayoutParams(
    				new LinearLayout.LayoutParams(mScreenWidth - mExpandButtonWidth, popHeight));
    	}
    	
    	if(DEBUG)
        	Log.i(TAG, "doUpdateCandidatePopup(), mHeight=" + mHeight 
        			+ ", getHeight() = " + getHeight()
        			+ ", mPopupCandidateView.getHeight() = " + mPopupCandidateView.getHeight()
        			+ ", mPopupScrollView.getHeight() = " + mPopupScrollView.getHeight()
        			+ ", offsetOnScreen[1] = " + offsetOnScreen[1]
        			+ ", popHeight = " + popHeight   
        			+ ", CandidateExpandedView.measureHeight = " + mPopupCandidateView.getMeasuredHeight()
    				+ ", btnClose.getMeasuredHeight() = " +	mCloseButtonHeight
    				);
    	
    	
    	if(mCandidatePopupWindow.isShowing()){
    		if(DEBUG)
    			Log.i(TAG,"doUpdateCandidatePopup(),mCandidatePopup.isShowing ");
    		mCandidatePopupWindow.update(mScreenWidth, popHeight);
    	}
    	else{
    		mCandidatePopupWindow.setWidth(mScreenWidth);
        	mCandidatePopupWindow.setHeight(popHeight);
    		mCandidatePopupWindow.showAsDropDown(this, 0,  -getHeight());
    		mPopupScrollView.scrollTo(0, 0);
    	}
    	
    	//Jeremy '12,5,31 do update layoutparams after popupWindow update or creation.
    	mPopupCandidateView.setLayoutParams(
    			new ScrollView.LayoutParams( ScrollView.LayoutParams.MATCH_PARENT
    			, popHeight- mCloseButtonHeight));
    	  
    	mPopupScrollView.setLayoutParams(
        		new LinearLayout.LayoutParams( LinearLayout.LayoutParams.MATCH_PARENT
    			, popHeight- mCloseButtonHeight));
    	
    

    }
    
    public void setComposingText(String composingText){
    	if(DEBUG) 
			Log.i(TAG,"setComposingText():composingText:"+composingText);
    	if(!composingText.trim().equals("")){
    		//mComposingText=composingText;
        	mHandler.setComposing(composingText, 0);
    		showComposing(composingText);
    	}else{
    		//mComposingText = "";
    		//mHandler.dismissComposing(0);
    		hideComposing();
    	}
    	
    
    }
    
    /**
     * Jeremy '12,6,2 separated from doupdateComposing 
     */
    
    public void doSetComposing(String composingText) {
    	if(DEBUG) 
    		Log.i(TAG,"doSetComposing():"+composingText + "this.isShown()" + this.isShown());
    	

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

    	
        if (composingText!=null ) {	
        	mComposingTextPopup.setContentView(mComposingTextView);
        	mComposingTextView.setText(composingText);
        	mComposingTextView.setTextSize(
        			mContext.getResources().getDimensionPixelSize(R.dimen.composing_text_size) 
        			*mLIMEPref.getFontSize());
        }else
        	return;
        
        mComposingTextView.invalidate();  //Jeremy '12,6,2 invalidate and measure so as to get correct height and width later. 
        mComposingTextView.setVisibility(VISIBLE);
        mComposingTextView.measure(
        		MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED), 
    			MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED));
        
    	
    	final int popupWidth =  mComposingTextView.getMeasuredWidth(); 
     	final int popupHeight = mComposingTextView.getMeasuredHeight();
    

    	int [] offsetInWindow = new int[2];
    	this.getLocationInWindow(offsetInWindow);
    	int mPopupComposingY = offsetInWindow[1];
    	int mPopupComposingX = 0;

    	mPopupComposingY -= popupHeight;
        
        if (!mComposingTextPopup.isShowing()) {              	
    		mComposingTextPopup.setWidth(popupWidth);
    		mComposingTextPopup.setHeight(popupHeight);
    		mComposingTextPopup.showAtLocation(this, Gravity.NO_GRAVITY, mPopupComposingX,mPopupComposingY );
    	}
    
       
    }
    /**
     *  Update composing to correct location with a delay after setComposing.
     */
   
    public void doUpdateComposing(){
           	if(DEBUG) 
        		Log.i(TAG,"doUpdateComposing(): this.isShown()" + this.isShown());
        	
    	
    	final int popupWidth =  mComposingTextView.getMeasuredWidth();  //Jeremy '12,6,2 use getWidth and getHeight instead
     	final int popupHeight = mComposingTextView.getMeasuredHeight();
    

    	int [] offsetInWindow = new int[2];
    	this.getLocationInWindow(offsetInWindow);
    	int mPopupComposingY = offsetInWindow[1];
    	int mPopupComposingX = 0;

    	
    			                                           
    	// Show popup windows at the location of cursor  Jeremy '11,7,25
    	// Rely on onCursorUpdate() of inputmethod service, which is not implemented on standard android 
    	// Working on htc implementations
    	// Not working on htc ics 4.0. removed by jeremy '12,4,3
    	/*
    	if( offsetInWindow[1] == 0 && cursorRect != null){
    		int [] offsetOnScreen = new int[2];
    		this.getLocationOnScreen(offsetOnScreen);
    		
    		if(DEBUG) 
    			Log.i(TAG, "doUpdateComposing(): candidateview offsetInWindow x:" 
    					+offsetInWindow[0] 
    					+ ", offsetinwindow y:" +offsetInWindow[1]
    				    + ", cursor.top=" + cursorRect.top
    				    + ", cursor.left=" + cursorRect.left);
    		
    		
    		mPopupComposingX =  cursorRect.right;
    		mPopupComposingY -= offsetOnScreen[1]- cursorRect.top -  popupHeight;
    		if(mPopupComposingY > -popupHeight){
    			mPopupComposingY -= 2* popupHeight;
    		}
    		if(mPopupComposingY > 0){
    			mPopupComposingY= -popupHeight;

    		}
    		if(DEBUG) 
    			Log.i(TAG, "doUpdateComposing(): candidateview offsetOnScreen x:" 
    				+offsetOnScreen[0] + ". y:" +offsetOnScreen[1]);
    	}else{*/
    	mPopupComposingY -= popupHeight;
    	//}

    	if(DEBUG) 
    		Log.i(TAG, "doUpdateComposing():mPopupComposingX:" +mPopupComposingX 
    				+ ". mPopupComposingY:" +mPopupComposingY
    				+". popupWidth = " + popupWidth
    				+". popupHeight = " + popupHeight
    				+ ". mComposingTextPopup.isShowing()=" + mComposingTextPopup.isShowing() );

    	if (mComposingTextPopup.isShowing()) {              	
    		mComposingTextPopup.update(mPopupComposingX, mPopupComposingY, 
    				popupWidth, popupHeight);
    	} else {
    		mComposingTextPopup.setWidth(popupWidth);
    		mComposingTextPopup.setHeight(popupHeight);
    		mComposingTextPopup.showAtLocation(this, Gravity.NO_GRAVITY, mPopupComposingX, 
    				mPopupComposingY );
    	}
    	


        
    }

    public void showComposing(String composingText) {
    	if(DEBUG) 
    		Log.i(TAG, "showComposing()");
    	//jeremy '12,6,3 moved the creation of mComposingTextPopup and mComposingTextView from doUpdateComposing
    	//Jeremy '12,4,8 to avoid fc when hard keyboard is engaged and candidateview is not shown
    	if(!this.isShown()) return;
    
    	mHandler.updateComposing(200); //Jeremy '12,6,3 dealy for 200ms after setcomposing
    	
    }
    
    public void hideComposing() {
    	if(DEBUG) 
    		Log.i(TAG, "hidecomposing()");
    	mHandler.dismissComposing(200); //Jeremy '12,6,3 the same delay as showComposing to avoid showed after hided
    	
    }
    public void showCandidatePopup(){
    	if(DEBUG) 
    		Log.i(TAG, "showCandidatePopup()");

    	mHandler.showCandidatePopup(0);
    
    	
    }
    
    public void hideCandidatePopup() {
    	if(DEBUG) 
    		Log.i(TAG, "hideCandidatePopup()");

    	mHandler.dismissCandidatePopup(0);
    
    }
    
  
    
    public boolean isCandidateExpanded(){
    	return candidateExpanded;
    }
    
    private boolean mHasroomForExpanding = true;
    public boolean hasRoomForExpanding(){ 
    	if(!mCandidatePopupWindow.isShowing()){
    		int [] offsetOnScreen = new int[2];
    		this.getLocationOnScreen(offsetOnScreen);
    		mHasroomForExpanding =  (mScreenHeight - offsetOnScreen[1]) > 2 * mHeight;
    	}
    	return mHasroomForExpanding;
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
    	if(DEBUG)
    		Log.i(TAG,"onMeasure()");
        int measuredWidth = resolveSize(mTotalWidth, widthMeasureSpec);
        
        // Get the desired height of the icon menu view (last row of items does
        // not have a divider below)
        //if(padding == null) padding = new Rect();
        //mSelectionHighlight.getPadding(padding);
        //final int desiredHeight = ((int)mPaint.getTextSize()) + mVerticalPadding
                //+ padding.top + padding.bottom;
        final int desiredHeight = mHeight;//((int)mPaint.getTextSize());
        
        // Maximum possible width and desired height
        setMeasuredDimension(measuredWidth,
                resolveSize(desiredHeight, heightMeasureSpec));
    }

    /**
     * If the canvas is null, then only touch calculations are performed to pick the target
     * candidate.
     */
    @Override
    protected synchronized void onDraw(Canvas canvas) {
    	
        if (mSuggestions == null) return;
        if(DEBUG)
    		Log.i(TAG, "Candidateview:OnDraw():Suggestion mCount:" + mCount+" mSuggestions.size:" + mSuggestions.size());
        mTotalWidth = 0;
        
        updateFontSize();
        
        if (mBgPadding == null) {
            mBgPadding = new Rect(0, 0, 0, 0);
            if (getBackground() != null) {
                getBackground().getPadding(mBgPadding);
            }
        }
        
        //final int mCount = mSuggestions.size(); 
        final int height = mHeight;//= getHeight();
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
        	//if(DEBUG)
        	//	Log.i(TAG, "Candidateview:OnDraw():updating:" + i );
        	if(count!=mCount || mSuggestions.size()==0) return;  // mSuggestion is updated, force abort
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
        		if(count!=mCount || mSuggestions.size()==0) break;
        		//if(DEBUG)
        		//	Log.i(TAG, "Candidateview:OnDraw():i:" + i + "  Drawing:" + mSuggestions.get(i).getWord() );
        		
        	
                //if ((i == 1 && !typedWordValid) || (i == 0 && typedWordValid)) {
                //    paint.setFakeBoldText(true);
                //    paint.setColor(mColorRecommended);
            	String suggestion = mSuggestions.get(i).getWord();
            	int c = i+1;
            	
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
                	if(c <= mDisplaySelkey.length()){
                		//Jeremy '11,6,11 Drawing text using relative font dimensions.
                		canvas.drawText(mDisplaySelkey.substring(c-1, c), 
                				mWordX[i] + mWordWidth[i] - height * 0.3f ,  height * 0.4f, npaint);
                	}
                }
                
                paint.setColor(mColorOther); 
                canvas.drawLine(mWordX[i] + mWordWidth[i] + 0.5f, bgPadding.top, 
                		mWordX[i] + mWordWidth[i] + 0.5f, height + 1, paint);
                paint.setFakeBoldText(false);
            }
        	
        	if (mTargetScrollX != getScrollX()) {
                scrollToTarget();
            }
            
            //showComposing(mComposingText);
        	
        }
       
        
    }
    
    private boolean checkHasMoreRecords(){
    	if(mSuggestions!=null && mSuggestions.size()>0 &&
    			mSuggestions.get(mSuggestions.size()-1).getCode() !=null
        		&& mSuggestions.get(mSuggestions.size()-1).getCode().equals("has_more_records")){
    		waitingForMoreRecords=true;
    		Thread UpadtingThread = new Thread(){
    			public void run() {
    				mService.requestFullRecords();
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
    public synchronized void setSuggestions(List<Mapping> suggestions, boolean showNumber, boolean typedWordValid) {
        //clear();
    	//Jeremy '11,8,14 moved from clear();
    	if(DEBUG)
    		Log.i(TAG,"setSuggestions()");
    	Resources res = mContext.getResources();
    	mHeight = (int) (res.getDimensionPixelSize(R.dimen.candidate_stripe_height) *mLIMEPref.getFontSize()); //move from constructor by Jeremy '12,5,6
    	currentX = 0;
        mTouchX = OUT_OF_BOUNDS;
        mCount =0;
        mSelectedIndex =-1;
      
        if(mLIMEPref.getDisablePhysicalSelKeyOption()){
        	showNumber = true;
        }
        //TODO: isAndroid3 should be replace as something which can detect working on tablets but not phones.
        mShowNumber = showNumber && isAndroid3;  
        
        if(mShowNumber)
        	X_GAP = (int) (res.getDimensionPixelSize(R.dimen.candidate_font_size)*0.35f);//13;
        else 
        	X_GAP = (int) (res.getDimensionPixelSize(R.dimen.candidate_font_size)*0.25f);;
    
        if (suggestions != null) {
            mSuggestions = new LinkedList<Mapping>(suggestions);    
	        	        
	        if(mSuggestions != null && mSuggestions.size() > 0){
	            //setBackgroundColor(bgcolor);
	            // Add by Jeremy '10, 3, 29
	            mCount = mSuggestions.size(); 
	            if(mCount > MAX_SUGGESTIONS) mCount = MAX_SUGGESTIONS;
	            
	          if(DEBUG)
	        	Log.i(TAG, "setSuggestions():mSuggestions.size():" + mSuggestions.size()
            			+ " mCount=" + mCount);
		                
	            if(mSuggestions.get(0).isDictionary()){
	            	// no default selection for related words
	            	mSelectedIndex = -1;
/*	            }else if(mCount > 1 && mSuggestions.get(1).getId() !=null && // the suggestion is not from relatedlist.
	            		mSuggestions.get(0).getWord().toLowerCase()
	            		.equals(mSuggestions.get(1).getCode().trim())) { // exact match
	            	// default selection on suggestions 1 (0 is typed English in mixed English mode)
	             	mSelectedIndex = 1;
	            }else if(mCount > 1 && mSuggestions.get(1).getId() !=null &&
	            		 mService.activeIM.equals("phonetic")&&
	            		 (mSuggestions.get(0).getWord().trim().toLowerCase()
	     	            		.equals(mSuggestions.get(1).getCode().trim())||
	     	              mSuggestions.get(0).getWord().trim().toLowerCase()
	    	     	        	.equals(mSuggestions.get(1).getCode().trim().replaceAll("[3467]", ""))) ){
*/	            
	            //Jeremy '12,5,31 If mSuggestions.get(0).getRelated() is true means no exact match result found, 
	            //set default candidate as mixed English code, 
	            //otherwise set default suggestion to the first one of the result list.
	            }else if(mCount > 1 && !mSuggestions.get(0).getRelated()){ 
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
        	hideCandidatePopup();
        }
        
        mHandler.updateUI(0);
        
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
    	if(DEBUG) Log.i(TAG, "clear()");
    	//mHeight =0; //Jeremy '12,5,6 hide candidate bar when candidateview is fixed.
    	mSuggestions = EMPTY_LIST;
        // Jeremy 11,8,14 close all popup on clear
        setComposingText("");
        mTargetScrollX = 0;
        hideComposing();
        hideCandidatePopup();
        mHandler.updateUI(0);
        mHeight = (int) (mContext.getResources().getDimensionPixelSize(
        		R.dimen.candidate_stripe_height) *mLIMEPref.getFontSize()); //restore the height Jeremy '12,5,24

    }
    
    //Jeremy '12,5,6 hide candidate bar when candidateview is fixed.
    public void forceHide() {
    	clear();
    	mHeight =0;
    }
    
    @Override
    public boolean onTouchEvent(MotionEvent me) {
    	if(DEBUG)
    		Log.i(TAG,"OnTouchEvent() action = " + me.getAction());
        if (mGestureDetector!=null && mGestureDetector.onTouchEvent(me)) {
        	if(DEBUG)
        		Log.i(TAG,"OnTouchEvent() event processed by mGestureDetector");
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
                	takeSelectedSuggestion(true);
                    mSelectedIndex = -1;
                }
            }
            invalidate();
            break;
        case MotionEvent.ACTION_UP:
        	if(DEBUG)
        		Log.i(TAG,"OnTouchEvent():MotionEvent.ACTION_UP, mScrolled="+mScrolled +"; mSelectedIndex = " + mSelectedIndex);
            if (!mScrolled) {
                if (mSelectedIndex >= 0) {
                    takeSelectedSuggestion(true);
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
    	if(mCandidatePopupWindow!=null && mCandidatePopupWindow.isShowing()){
    		mPopupCandidateView.selectNext();
    	}else{
    		if(mSelectedIndex < mCount-1){
    			mSelectedIndex++;
    			if(mWordX[mSelectedIndex] + mWordWidth[mSelectedIndex] > currentX + getWidth()) scrollNext();
    		}
    		invalidate();
    	}
    }
    
    public void selectPrev() {
    	if (mSuggestions == null) return;
    	if(mCandidatePopupWindow!=null && mCandidatePopupWindow.isShowing()){
    		mPopupCandidateView.selectPrev();
    	}else{
    		if(mSelectedIndex > 0) {
    			mSelectedIndex--;
    			if(mWordX[mSelectedIndex] < currentX) scrollPrev();
    		}
    		invalidate();
    	}
    }
    //Jeremy '11,8,28
    public void selectNextRow(){
    	if (mSuggestions == null) return;
    	if(mCandidatePopupWindow!=null && mCandidatePopupWindow.isShowing())
    		mPopupCandidateView.selectNextRow();
    	else if(mScreenWidth < mTotalWidth)
    		showCandidatePopup();
    	
    }
    public void selectPrevRow() {
    	if (mSuggestions == null) return;
    	if(mCandidatePopupWindow!=null && mCandidatePopupWindow.isShowing())
    		mPopupCandidateView.selectPrevRow();
    	
    }
    
    public boolean takeSuggstionAtIndex(int index){
    	if(DEBUG){
    		Log.i(TAG, "takeSuggestion():mSelectedIndex:" + mSelectedIndex);
    	}
    
    	
    	if (mSuggestions != null && index >= 0 && index <= mSuggestions.size() ) {
    		mService.pickCandidateManually(index);
    		return true;  // Selection picked
        }else
        	return false;
    }
    
    public boolean takeSelectedSuggestion(){
    	return this.takeSelectedSuggestion(false);
    }
    public boolean takeSelectedSuggestion(boolean vibrateSound){
    	if(DEBUG){
    		Log.i(TAG, "takeSelectedSuggestion():mSelectedIndex:" + mSelectedIndex);
    	}
    	//Jeremy '11,9,1 do vibrate and sound on suggestion picked from candidateview
    	if(vibrateSound) mService.doVibrateSound(0);
    	hideComposing(); //Jeremy '12,5,6 
    	if(mCandidatePopupWindow!=null && mCandidatePopupWindow.isShowing()){
    		hideCandidatePopup();
    		return takeSuggstionAtIndex(mPopupCandidateView.mSelectedIndex);
    	}else
    		return takeSuggstionAtIndex(mSelectedIndex);
    		
    }

    /**
     * For flick through from keyboard, call this method with the x coordinate of the flick 
     * gesture.
     * @param x
     */
    /*public void takeSuggestionAt(float x) {
    	
        mTouchX = (int) x;
        // To detect candidate
        onDraw(null);
        takeSelectedSuggestion();
        invalidate();
    }*/

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
		//Jeremy '11,8.27 do vibrate and sound on candidateexpandedview close button pressed.
    	mService.doVibrateSound(0);

		hideCandidatePopup();
	}


}
