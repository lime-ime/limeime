/*
 *
 *  *
 *  **    Copyright 2015, The LimeIME Open Source Project
 *  **
 *  **    Project Url: http://github.com/lime-ime/limeime/
 *  **                 http://android.toload.net/
 *  **
 *  **    This program is free software: you can redistribute it and/or modify
 *  **    it under the terms of the GNU General Public License as published by
 *  **    the Free Software Foundation, either version 3 of the License, or
 *  **    (at your option) any later version.
 *  *
 *  **    This program is distributed in the hope that it will be useful,
 *  **    but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  **    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  **    GNU General Public License for more details.
 *  *
 *  **    You should have received a copy of the GNU General Public License
 *  **    along with this program.  If not, see <http://www.gnu.org/licenses/>.
 *  *
 *
 */

/*
 * Jeremy '11,8,8
 * Derive from gingerbread Latin IME LatinKeyboardBaseView, 
 * modified to compatible with pre 2.2 devices, and disable
 * fling selection of popup minikeybaord on large screen.
 * 
 */

package net.toload.main.hd.keyboard;

import android.content.Context;
import android.content.res.Resources;
import android.content.res.TypedArray;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Paint.Align;
import android.graphics.PorterDuff;
import android.graphics.Rect;
import android.graphics.Region.Op;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.inputmethodservice.Keyboard;
import android.os.Handler;
import android.os.Message;
import android.util.AttributeSet;
import android.util.Log;
import android.util.TypedValue;
import android.view.GestureDetector;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup.LayoutParams;
import android.widget.PopupWindow;
import android.widget.TextView;
import net.toload.main.hd.R;
import net.toload.main.hd.keyboard.LIMEBaseKeyboard.Key;

import java.lang.ref.WeakReference;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class LIMEKeyboardBaseView extends View implements View.OnClickListener {
    private static final String TAG = "LIMEKeyboardBaseView";
    private static final boolean DEBUG = false;
    private static final boolean mShowTouchPoints = false;


    /**
     * Listener for virtual keyboard events.
     */
    public interface OnKeyboardActionListener {

        /**
         * Called when the user presses a key. This is sent before the {@link #onKey} is called.
         * For keys that repeat, this is only called once.
         * @param primaryCode the unicode of the key being pressed. If the touch is not on a valid
         * key, the value will be zero.
         */
        void onPress(int primaryCode);

        /**
         * Called when the user releases a key. This is sent after the {@link #onKey} is called.
         * For keys that repeat, this is only called once.
         * @param primaryCode the code of the key that was released
         */
        void onRelease(int primaryCode);

        /**
         * Send a key press to the listener.
         * @param primaryCode this is the key that was pressed
         * @param keyCodes the codes for all the possible alternative keys
         * with the primary code being the first. If the primary key code is
         * a single character such as an alphabet or number or symbol, the alternatives
         * will include other characters that may be on the same key or adjacent keys.
         * These codes are useful to correct for accidental presses of a key adjacent to
         * the intended key.
         */
        void onKey(int primaryCode, int[] keyCodes);

        /**
         * Sends a sequence of characters to the listener.
         * @param text the sequence of characters to be displayed.
         */
        void onText(CharSequence text);

        /**
         * Called when the user quickly moves the finger from right to left.
         */
        void swipeLeft();

        /**
         * Called when the user quickly moves the finger from left to right.
         */
        void swipeRight();

        /**
         * Called when the user quickly moves the finger from up to down.
         */
        void swipeDown();

        /**
         * Called when the user quickly moves the finger from down to up.
         */
        void swipeUp();
    }


    // Miscellaneous constants
    /* package */
    static final int NOT_A_KEY = -1;
    private static final int[] KEY_DELETE = { Keyboard.KEYCODE_DELETE };
    private static final int[] LONG_PRESSABLE_STATE_SET = {android.R.attr.state_long_pressable};
    private static final int NUMBER_HINT_VERTICAL_ADJUSTMENT_PIXEL = -1;

    //themed context
    private final Context mContext;

    // XML attribute
    private int mKeyTextSize;
    private int mKeyTextColorNormal;
    private int mKeyTextColorPressed; //Jeremy '15,5,13
    private int mFunctionKeyTextColorNormal;
    private int mFunctionKeyTextColorPressed; //Jeremy '15,5,13
    private int mKeySubLabelTextColorNormal; //Jeremy '12,4,29
    private int mKeySubLabelTextColorPressed; //Jeremy '15,5,13
    private Typeface mKeyTextStyle = Typeface.DEFAULT;
    private int mLabelTextSize;
    private int mSmallLabelTextSize;
    private int mSubLabelTextSize;
    private int mSymbolColorScheme = 0;
    private int mShadowColor;
    private float mShadowRadius;

    private float mBackgroundDimAmount;
    private int mSpacePreviewTopPadding;
    private int mPreviewTopPadding;


    private int mDelayBeforePreview;
    private int mDelayAfterPreview;

    // Main keyboard
    private LIMEBaseKeyboard mKeyboard;


    // Key preview popup
    private TextView mPreviewText;
    private PopupWindow mPreviewPopup;
    private int mPreviewTextSizeLarge;
    private int[] mOffsetInWindow;
    private int mPreviewOffset;
    private int mPreviewHeight;

    // Working variable
    private final int[] mCoordinates = new int[2];

    private int mPaddingLeft = 0;
    private int mPaddingTop = 0;

    private Key[] mKeys;

    private int mCurrentKeyIndex = NOT_A_KEY;
    private int mPopupPreviewOffsetX;
    private int mPopupPreviewOffsetY;
    private int mWindowY;

    // Popup mini keyboard

    private Map<Key,View> mMiniKeyboardCache;

    private PopupWindow mPopupKeyboard;

    private boolean mMiniKeyboardOnScreen;
    private View mPopupParent;

    private Drawable mKeyBackground;

    private static final int REPEAT_INTERVAL = 50; // ~20 keys per second
    private static final int REPEAT_START_DELAY = 400;
    private static final int LONGPRESS_TIMEOUT = ViewConfiguration.getLongPressTimeout();

    private static int MAX_NEARBY_KEYS = 12;
    private int[] mDistances = new int[MAX_NEARBY_KEYS];

    // For multi-tap
    private int mLastSentIndex;
    private int mTapCount;
    private long mLastTapTime;
    private boolean mInMultiTap;
    private static final int MULTITAP_INTERVAL = 800; // milliseconds
    private StringBuilder mPreviewLabel = new StringBuilder(1);

    // Drawing
    /**
     * Whether the keyboard bitmap needs to be redrawn before it's blitted. *
     */
    private boolean mDrawPending;
    /**
     * The dirty region in the keyboard bitmap
     */
    private final Rect mDirtyRect = new Rect();
    /**
     * The keyboard bitmap for faster updates
     */
    private Bitmap mBuffer;
    /**
     * Notes if the keyboard just changed, so that we could possibly reallocate the mBuffer.
     */
    private boolean mKeyboardChanged;
    /**
     * The canvas for the above mutable keyboard bitmap
     */
    private Canvas mCanvas;

    // This map caches key label text height in pixel as value and key label text size as map key.
    private final HashMap<Integer, Integer> mTextHeightCache = new HashMap<>();
    private final HashMap<Integer, Integer> mTextWidthCache = new HashMap<>();

    private Drawable mPopupHint;//Jeremy /11,8,11

    /** The accessibility manager for accessibility support *
    private AccessibilityManager mAccessibilityManager;
    /** The audio manager for accessibility support *
    private AudioManager mAudioManager;
    /** Whether the requirement of a headset to hear passwords if accessibility is enabled is announced. *
    private boolean mHeadsetRequiredToHearPasswordsAnnounced;
*/
    /** Listener for {@link OnKeyboardActionListener}. */
    private OnKeyboardActionListener mKeyboardActionListener;

    private int mVerticalCorrection;
    private int mProximityThreshold;

     private boolean mShowPreview = true;

    private int mLastX;
    private int mLastY;
    private int mStartX;
    private int mStartY;

    private boolean mProximityCorrectOn;

    private Paint mPaint;
    private Rect mPadding;

    private long mDownTime;
    private long mLastMoveTime;
    private int mLastKey;
    private int mLastCodeX;
    private int mLastCodeY;
    private int mCurrentKey = NOT_A_KEY;
    private int mDownKey = NOT_A_KEY;
    private long mLastKeyTime;
    private long mCurrentKeyTime;
    private int[] mKeyIndices = new int[12];
    private GestureDetector mGestureDetector;
    private int mRepeatKeyIndex = NOT_A_KEY;
    private int mPopupLayout;
    private boolean mAbortKey;
    private Key mInvalidatedKey;
    private Rect mClipRegion = new Rect(0, 0, 0, 0);
    private boolean mPossiblePoly;
    private SwipeTracker mSwipeTracker = new SwipeTracker();
    private int mSwipeThreshold;
    private boolean mDisambiguateSwipe;

    // Variables for dealing with multiple pointers
    private int mOldPointerCount = 1;
    private float mOldPointerX;
    private float mOldPointerY;


    private static final int MSG_SHOW_PREVIEW = 1;
    private static final int MSG_REMOVE_PREVIEW = 2;
    private static final int MSG_REPEAT = 3;
    private static final int MSG_LONGPRESS = 4;

    private static final int DEBOUNCE_TIME = 70;


    //private LIMEPreferenceManager mLIMEPref;

    UIHandler mHandler = new UIHandler(this);
    static class UIHandler extends  Handler {

        public UIHandler(LIMEKeyboardBaseView keyboardBaseView){
            mLIMEKeyboardBaseViewWeakReference = new WeakReference<>(keyboardBaseView);
        }
        private final WeakReference<LIMEKeyboardBaseView> mLIMEKeyboardBaseViewWeakReference;
        @Override
        public void handleMessage(Message msg) {
            LIMEKeyboardBaseView mLIMEKeyboardBaseView = mLIMEKeyboardBaseViewWeakReference.get();
            if(mLIMEKeyboardBaseView == null) return;
            switch (msg.what) {
                case MSG_SHOW_PREVIEW:
                    if(DEBUG) Log.i(TAG, "handleMessage()  MSG_SHOW_PREVIEW");
                    mLIMEKeyboardBaseView.showKey(msg.arg1);
                    break;
                case MSG_REMOVE_PREVIEW:
                    if(DEBUG) Log.i(TAG, "handleMessage()  MSG_REMOVE_PREVIEW");
                    if(mLIMEKeyboardBaseView.mPreviewPopup.isShowing())    //mPreviewPopup.dismiss();
                        mLIMEKeyboardBaseView.mPreviewText.setVisibility(INVISIBLE);
                    mLIMEKeyboardBaseView.mPreviewPopup.dismiss();
                    break;
                case MSG_REPEAT:
                    if(DEBUG) Log.i(TAG, "handleMessage()  MSG_REPEAT");
                    if (mLIMEKeyboardBaseView.repeatKey()) {
                        Message repeat = Message.obtain(this, MSG_REPEAT);
                        sendMessageDelayed(repeat, REPEAT_INTERVAL);
                    }
                    break;
                case MSG_LONGPRESS:
                    if(DEBUG) Log.i(TAG, "handleMessage()  MSG_LONGPRESS");
                    mLIMEKeyboardBaseView.openPopupIfRequired();
                    break;
            }
        }
    }


    public LIMEKeyboardBaseView(Context context, AttributeSet attrs) {
        this(context, attrs, R.attr.LIMEKeyboardBaseView);

    }

    public LIMEKeyboardBaseView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);

        mContext = context;

        TypedArray a = context.getTheme().obtainStyledAttributes(
                attrs, R.styleable.LIMEKeyboardBaseView, defStyle, R.style.LIMEKeyboardBaseView);
        LayoutInflater inflate =
                (LayoutInflater) context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        int previewLayout = 0;
        int keyTextSize = 0;


        int n = a.getIndexCount();

        for (int i = 0; i < n; i++) {
            int attr = a.getIndex(i);

            switch (attr) {
                case R.styleable.LIMEKeyboardBaseView_keyBackground:
                    mKeyBackground = a.getDrawable(attr);
                    break;
                case R.styleable.LIMEKeyboardBaseView_verticalCorrection:
                    mVerticalCorrection = a.getDimensionPixelOffset(attr, 0);
                    break;
                case R.styleable.LIMEKeyboardBaseView_keyPreviewLayout:
                    previewLayout = a.getResourceId(attr, 0);
                    break;
                case R.styleable.LIMEKeyboardBaseView_keyPreviewOffset:
                    mPreviewOffset = a.getDimensionPixelOffset(attr, 0);
                    break;
                case R.styleable.LIMEKeyboardBaseView_keyPreviewHeight:
                    mPreviewHeight = a.getDimensionPixelSize(attr, 80);
                    break;
                case R.styleable.LIMEKeyboardBaseView_keyTextSize:
                    mKeyTextSize = a.getDimensionPixelSize(attr, 18);
                    break;
                case R.styleable.LIMEKeyboardBaseView_functionKeyTextColorNormal:
                    mFunctionKeyTextColorNormal = a.getColor(attr, 0xFF000000);
                    break;
                case R.styleable.LIMEKeyboardBaseView_functionKeyTextColorPressed:
                    mFunctionKeyTextColorPressed = a.getColor(attr, 0xFF000000);
                    break;
                case R.styleable.LIMEKeyboardBaseView_keyTextColorNormal:
                    mKeyTextColorNormal = a.getColor(attr, 0xFF000000);
                    break;
                case R.styleable.LIMEKeyboardBaseView_keyTextColorPressed:
                    mKeyTextColorPressed = a.getColor(attr, 0xFF000000);
                    break;
                case R.styleable.LIMEKeyboardBaseView_keySubLabelTextColorNormal:
                    mKeySubLabelTextColorNormal = a.getColor(attr, 0xFF000000);
                    break;
                case R.styleable.LIMEKeyboardBaseView_keySubLabelTextColorPressed:
                    mKeySubLabelTextColorPressed = a.getColor(attr, 0xFF000000);
                    break;
                case R.styleable.LIMEKeyboardBaseView_labelTextSize:
                    mLabelTextSize = a.getDimensionPixelSize(attr, 14);
                    break;
                //Jeremy '11,8,11, Extended for sub-label display
                case R.styleable.LIMEKeyboardBaseView_smallLabelTextSize:
                    mSmallLabelTextSize = a.getDimensionPixelSize(attr, 14);
                    break;
                //Jeremy '11,8,11, Extended for sub-label display
                case R.styleable.LIMEKeyboardBaseView_subLabelTextSize:
                    mSubLabelTextSize = a.getDimensionPixelSize(attr, 14);
                    break;
                case R.styleable.LIMEKeyboardBaseView_popupLayout:
                    mPopupLayout = a.getResourceId(attr, 0);
                    break;
                case R.styleable.LIMEKeyboardBaseView_popupHint:
                    mPopupHint = a.getDrawable(attr);
                    break;
                case R.styleable.LIMEKeyboardBaseView_shadowColor:
                    mShadowColor = a.getColor(attr, 0);
                    break;
                case R.styleable.LIMEKeyboardBaseView_shadowRadius:
                    mShadowRadius = a.getFloat(attr, 0f);
                    break;
                case R.styleable.LIMEKeyboardBaseView_spacePreviewTopPadding:  //Jeremy 15,7,13
                    mSpacePreviewTopPadding = a.getDimensionPixelSize(attr, 10);
                    break;
                case R.styleable.LIMEKeyboardBaseView_previewTopPadding:  //Jeremy 15,7,13
                    mPreviewTopPadding = a.getDimensionPixelSize(attr, 0);
                    break;
                case R.styleable.LIMEKeyboardBaseView_backgroundDimAmount:
                    mBackgroundDimAmount = a.getFloat(attr, 0.5f);
                    break;
                //case android.R.styleable.
                case R.styleable.LIMEKeyboardBaseView_keyTextStyle:
                    int textStyle = a.getInt(attr, 0);
                    switch (textStyle) {
                        case 0:
                            mKeyTextStyle = Typeface.DEFAULT;
                            break;
                        case 1:
                            mKeyTextStyle = Typeface.DEFAULT_BOLD;
                            break;
                        default:
                            mKeyTextStyle = Typeface.defaultFromStyle(textStyle);
                            break;
                    }
                    break;
                case R.styleable.LIMEKeyboardBaseView_symbolColorScheme:
                    mSymbolColorScheme = a.getInt(attr, 0);
                    break;
            }
        }

        final Resources res = getResources();

        mPreviewPopup = new PopupWindow(context);
        if (previewLayout != 0) {
            mPreviewText = (TextView) inflate.inflate(previewLayout, null);
            mPreviewTextSizeLarge = (int) res.getDimension(R.dimen.key_preview_text_size_large);
            mPreviewPopup.setContentView(mPreviewText);
            mPreviewPopup.setBackgroundDrawable(null);
        } else {
            mShowPreview = false;
        }
        mPreviewPopup.setTouchable(false);
        //mPreviewPopup.setAnimationStyle(R.style.KeyPreviewAnimation);
        mDelayBeforePreview = res.getInteger(R.integer.config_delay_before_preview);
        mDelayAfterPreview = res.getInteger(R.integer.config_delay_after_preview);

        mPopupKeyboard = new PopupWindow(context);
        //mPopupKeyboard.setBackgroundDrawable(null);
        mPopupKeyboard.setAnimationStyle(R.style.MiniKeyboardAnimation);

        mPopupParent = this;

        mPaint = new Paint();
        mPaint.setAntiAlias(true);
        mPaint.setTextSize(keyTextSize);
        mPaint.setTextAlign(Align.CENTER);
        mPaint.setAlpha(255);

        mPadding = new Rect(0, 0, 0, 0);
        mMiniKeyboardCache = new HashMap<>();
        mKeyBackground.getPadding(mPadding);

        mSwipeThreshold = (int) (500 * res.getDisplayMetrics().density);
        mDisambiguateSwipe = res.getBoolean(R.bool.config_swipeDisambiguation);

        //mAccessibilityManager = AccessibilityManager.getInstance(context);
       // mAudioManager = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);

        resetMultiTap();
        initGestureDetector();
    }

    public int getmSymbolColorScheme() {
        return mSymbolColorScheme;
    }

    private void initGestureDetector() {
        mGestureDetector = new GestureDetector(getContext(), new GestureDetector.SimpleOnGestureListener() {
            @Override
            public boolean onFling(MotionEvent me1, MotionEvent me2,
                                   float velocityX, float velocityY) {
                if (mPossiblePoly) return false;
                final float absX = Math.abs(velocityX);
                final float absY = Math.abs(velocityY);
                float deltaX = me2.getX() - me1.getX();
                float deltaY = me2.getY() - me1.getY();
                int travelX = getWidth() / 2; // Half the keyboard width
                int travelY = getHeight() / 2; // Half the keyboard height
                mSwipeTracker.computeCurrentVelocity(1000);
                final float endingVelocityX = mSwipeTracker.getXVelocity();
                final float endingVelocityY = mSwipeTracker.getYVelocity();
                boolean sendDownKey = false;
                if (velocityX > mSwipeThreshold && absY < absX && deltaX > travelX) {
                    if (mDisambiguateSwipe && endingVelocityX < velocityX / 4) {
                        sendDownKey = true;
                    } else {
                        swipeRight();
                        return true;
                    }
                } else if (velocityX < -mSwipeThreshold && absY < absX && deltaX < -travelX) {
                    if (mDisambiguateSwipe && endingVelocityX > velocityX / 4) {
                        sendDownKey = true;
                    } else {
                        swipeLeft();
                        return true;
                    }
                } else if (velocityY < -mSwipeThreshold && absX < absY && deltaY < -travelY) {
                    if (mDisambiguateSwipe && endingVelocityY > velocityY / 4) {
                        sendDownKey = true;
                    } else {
                        swipeUp();
                        return true;
                    }
                } else if (velocityY > mSwipeThreshold && absX < absY / 2 && deltaY > travelY) {
                    if (mDisambiguateSwipe && endingVelocityY < velocityY / 4) {
                        sendDownKey = true;
                    } else {
                        swipeDown();
                        return true;
                    }
                }

                if (sendDownKey) {
                    detectAndSendKey(mDownKey, mStartX, mStartY, me1.getEventTime());
                }
                return false;
            }
        });

        mGestureDetector.setIsLongpressEnabled(false);
    }

    public boolean hasDistinctMultitouch(){
        return true;
    }

    public void setOnKeyboardActionListener(OnKeyboardActionListener listener) {
        mKeyboardActionListener = listener;
    }

    /**
     * Returns the {@link OnKeyboardActionListener} object.
     *
     * @return the listener attached to this keyboard
     */
    protected OnKeyboardActionListener getOnKeyboardActionListener() {
        return mKeyboardActionListener;
    }

    /**
     * Attaches a keyboard to this view. The keyboard can be switched at any time and the
     * view will re-layout itself to accommodate the keyboard.
     * //* @see Keyboard
     *
     * @param keyboard the keyboard to display in this view
     * @see #getKeyboard()
     */
    public void setKeyboard(LIMEBaseKeyboard keyboard) {
        if (mKeyboard != null) {
            showPreview(NOT_A_KEY);
        }
        // Remove any pending messages, except dismissing preview
        removeMessages();
        mKeyboard = keyboard;

        List<Key> keys = mKeyboard.getKeys();
        mKeys = keys.toArray(new Key[keys.size()]);

        requestLayout();
        // Hint to reallocate the buffer if the size changed
        mKeyboardChanged = true;
        invalidateAllKeys();
        computeProximityThreshold(keyboard);
        mMiniKeyboardCache.clear();
        // Switching to a different keyboard should abort any pending keys so that the key up
        // doesn't get delivered to the old or new keyboard
        mAbortKey = true; // Until the next ACTION_DOWN
    }

    /**
     * Returns the current keyboard being displayed by this view.
     *
     * @return the currently attached keyboard
     * //* @see #setKeyboard(Keyboard)
     */
    public LIMEBaseKeyboard getKeyboard() {
        return mKeyboard;
    }

    /**
     * Sets the state of the shift key of the keyboard, if any.
     *
     * @param shifted whether or not to enable the state of the shift key
     * @return true if the shift key state changed, false if there was no change
     */
    public boolean setShifted(boolean shifted) {
        if (mKeyboard != null) {
            if (mKeyboard.setShifted(shifted)) {
                // The whole keyboard probably needs to be redrawn
                invalidateAllKeys();
                return true;
            }
        }
        return false;
    }

    /**
     * Returns the state of the shift key of the keyboard, if any.
     *
     * @return true if the shift is in a pressed state, false otherwise. If there is
     * no shift key on the keyboard or there is no keyboard attached, it returns false.
     */
    public boolean isShifted() {

        return mKeyboard != null && mKeyboard.isShifted();
    }

    /**
     * Enables or disables the key feedback popup. This is a popup that shows a magnified
     * version of the depressed key. By default the preview is enabled.
     *
     * @param previewEnabled whether or not to enable the key feedback popup
     * @see #isPreviewEnabled()
     */
    public void setPreviewEnabled(boolean previewEnabled) {
        mShowPreview = previewEnabled;
    }

    /**
     * Returns the enabled state of the key feedback popup.
     *
     * @return whether or not the key feedback popup is enabled
     * @see #setPreviewEnabled(boolean)
     */
    public boolean isPreviewEnabled() {
        return mShowPreview;
    }



    public void setPopupParent(View v) {
        mPopupParent = v;
    }

    public void setPopupOffset(int x, int y) {
        mPopupPreviewOffsetX = x;
        mPopupPreviewOffsetY = y;
        if (mPreviewPopup.isShowing()) {
            mPreviewPopup.dismiss();
        }
    }

    /**
     * When enabled, calls to {@link OnKeyboardActionListener#onKey} will include key
     * codes for adjacent keys.  When disabled, only the primary key code will be
     * reported.
     *
     * @param enabled whether or not the proximity correction is enabled
     */
    public void setProximityCorrectionEnabled(boolean enabled) {
        mProximityCorrectOn = enabled;
    }

    /**
     * Returns true if proximity correction is enabled.
     */
    public boolean isProximityCorrectionEnabled() {
        return mProximityCorrectOn;
    }

    /**
     * Popup keyboard close button clicked.
     */
    public void onClick(View v) {
        dismissPopupKeyboard();
    }

    protected CharSequence adjustCase(CharSequence label) {
        if (mKeyboard.isShifted() && label != null && label.length() <= 3
                && Character.isLowerCase(label.charAt(0))) {
            label = label.toString().toUpperCase();
        }
        return label;
    }

    @Override
    public void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        // Round up a little
        if (mKeyboard == null) {
            setMeasuredDimension(
                    getPaddingLeft() + getPaddingRight(), getPaddingTop() + getPaddingBottom());
        } else {
            int width = mKeyboard.getMinWidth() + getPaddingLeft() + getPaddingRight();
            if (MeasureSpec.getSize(widthMeasureSpec) < width + 10) {
                width = MeasureSpec.getSize(widthMeasureSpec);
            }
            setMeasuredDimension(
                    width, mKeyboard.getHeight() + getPaddingTop() + getPaddingBottom());
        }
    }

    /**
     * Compute the average distance between adjacent keys (horizontally and vertically)
     * and square it to get the proximity threshold. We use a square here and in computing
     * the touch distance from a key's center to avoid taking a square root.
     *
     */
    private void computeProximityThreshold(LIMEBaseKeyboard keyboard) {
        if (keyboard == null) return;
        final Key[] keys = mKeys;
        if (keys == null) return;
        int length = keys.length;
        int dimensionSum = 0;
        for (Key key : keys) {
            dimensionSum += Math.min(key.width, key.height) + key.gap;
        }
        if (dimensionSum < 0 || length == 0) return;
        mProximityThreshold = (int) (dimensionSum * 1.4f / length);
        mProximityThreshold *= mProximityThreshold; // Square it
    }

    @Override
    public void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);

        if (mKeyboard != null) {
            mKeyboard.resize(w, h);
        }
        // Release the buffer, if any and it will be reallocated on the next draw
        mBuffer = null;
    }

    @Override
    public void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (mDrawPending || mBuffer == null || mKeyboardChanged) {
            onBufferDraw();
        }
        canvas.drawBitmap(mBuffer, 0, 0, null);
    }

    private void onBufferDraw() {
        if (mBuffer == null || mKeyboardChanged) {
            if (mBuffer == null || (mBuffer.getWidth() != getWidth() || mBuffer.getHeight() != getHeight())) {
                // Make sure our bitmap is at least 1x1
                final int width = Math.max(1, getWidth());
                final int height = Math.max(1, getHeight());
                mBuffer = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
                mCanvas = new Canvas(mBuffer);
            }
            invalidateAllKeys();
            mKeyboardChanged = false;
        }
        final Canvas canvas = mCanvas;
        canvas.clipRect(mDirtyRect, Op.REPLACE);

        if (mKeyboard == null) return;

        final Paint paint = mPaint;
        final Drawable keyBackground = mKeyBackground;
        final Rect clipRegion = mClipRegion;
        final Rect padding = mPadding;
        final int kbdPaddingLeft = getPaddingLeft();
        final int kbdPaddingTop = getPaddingTop();
        final Key[] keys = mKeys;
        final Key invalidKey = mInvalidatedKey;


        boolean drawSingleKey = false;
        if (invalidKey != null && canvas.getClipBounds(clipRegion)) {
            // Is clipRegion completely contained within the invalidated key?
            if (invalidKey.x + kbdPaddingLeft - 1 <= clipRegion.left &&
                    invalidKey.y + kbdPaddingTop - 1 <= clipRegion.top &&
                    invalidKey.x + invalidKey.width + kbdPaddingLeft + 1 >= clipRegion.right &&
                    invalidKey.y + invalidKey.height + kbdPaddingTop + 1 >= clipRegion.bottom) {
                drawSingleKey = true;
            }
        }
        canvas.drawColor(0x00000000, PorterDuff.Mode.CLEAR);
        //final int keyCount = keys.length;
        for (final Key key : keys) {
            if (drawSingleKey && invalidKey != key) {
                continue;
            }
            int[] drawableState = key.getCurrentDrawableState();
            keyBackground.setState(drawableState);


            // Switch the character to uppercase if shift is pressed
            String label = key.label == null ? null : adjustCase(key.label).toString();

            final Rect bounds = keyBackground.getBounds();
            if (key.width != bounds.right || key.height != bounds.bottom) {
                keyBackground.setBounds(0, 0, key.width, key.height);
            }
            canvas.translate(key.x + kbdPaddingLeft, key.y + kbdPaddingTop);
            keyBackground.draw(canvas);

            boolean shouldDrawIcon = true;
            if (label != null) {
                // For characters, use large font. For labels like "Done", use small font.
                final int labelSize;

                if (DEBUG)
                    Log.i(TAG, "onBufferDraw():" + label
                            + " keySizeScale = " + mKeyboard.getKeySizeScale() + " "
                            + " labelSizeScale = " + key.getLabelSizeScale());
                //Jeremy '11,8,11, Extended for sub-label display
                //Jeremy '11,9,4 Scale label size
                float keySizeScale = mKeyboard.getKeySizeScale();
                float labelSizeScale = key.getLabelSizeScale();

                //Jeremy '12,6,7 moved to LIMEbasekeyboard
                /*if(key.height < mKeyboard.getKeyHeight())  //Jeremy '12,5,21 scaled the label size if the key height is smaller than default key height
					labelSizeScale =  (float)(key.height) / (float)(mKeyboard.getKeyHeight());

				if(key.width < mKeyboard.getKeyWidth())  //Jeremy '12,5,26 scaled the label size if the key width is smaller than default key width
					labelSizeScale *=  (float)(key.width) / (float)(mKeyboard.getKeyWidth());*/

                boolean hasSubLabel = label.contains("\n");
                boolean hasSecondSubLabel = false;
                String subLabel = "", secondSubLabel = "";
                if (hasSubLabel) {
                    String labelA[] = label.split("\n");
                    if (labelA.length > 0) label = labelA[1];
                    subLabel = labelA[0];

                    hasSecondSubLabel = subLabel.contains("\t");
                    if (hasSecondSubLabel) {
                        String subLabelA[] = subLabel.split("\t");
                        if (subLabelA.length > 0) subLabel = subLabelA[0];
                        secondSubLabel = subLabelA[1];
                    }
                }
                if (hasSubLabel) {
                    if (label.length() > 1) { //Jeremy '12,6,6 shrink the font size for more characters on label
                        labelSize = (int) (mSmallLabelTextSize * keySizeScale * labelSizeScale * 0.8f);
                        paint.setTypeface(Typeface.DEFAULT_BOLD);
                    } else {
                        labelSize = (int) (mSmallLabelTextSize * keySizeScale * labelSizeScale);
                        paint.setTypeface(Typeface.DEFAULT_BOLD);
                    }
                } else if (label.length() > 1 && key.codes.length < 2) {
                    labelSize = (int) (mLabelTextSize * keySizeScale * labelSizeScale);
                    paint.setTypeface(Typeface.DEFAULT_BOLD);
                } else {
                    labelSize = (int) (mKeyTextSize * keySizeScale * labelSizeScale);
                    paint.setTypeface(mKeyTextStyle);
                }
                paint.setTextSize(labelSize);


                final int labelHeight;
                final int labelWidth;
                String KEY_LABEL_HEIGHT_REFERENCE_CHAR = "W";
                if (mTextHeightCache.get(labelSize) != null) {
                    labelHeight = mTextHeightCache.get(labelSize);
                    labelWidth = mTextWidthCache.get(labelSize);
                } else {
                    Rect textBounds = new Rect();
                    paint.getTextBounds(KEY_LABEL_HEIGHT_REFERENCE_CHAR, 0, 1, textBounds);
                    labelHeight = textBounds.height();
                    labelWidth = textBounds.width();
                    mTextHeightCache.put(labelSize, labelHeight);
                    mTextWidthCache.put(labelSize, labelWidth);
                }

                // Draw a drop shadow for the text
                if (mShadowRadius > 0) paint.setShadowLayer(mShadowRadius, 0, 0, mShadowColor);
                final int centerX = (key.width + padding.left - padding.right) / 2;
                final int centerY = (key.height + padding.top - padding.bottom) / 2;
                final int keyColor = key.isFunctionalKey()
                        ? (key.pressed ? mFunctionKeyTextColorPressed : mFunctionKeyTextColorNormal)
                        : (key.pressed ? mKeyTextColorPressed : mKeyTextColorNormal);
                final int subKeyColor = key.isFunctionalKey()
                        ? (key.pressed ? mFunctionKeyTextColorPressed : mFunctionKeyTextColorNormal)
                        :(key.pressed ? mKeySubLabelTextColorPressed : mKeySubLabelTextColorNormal);

                float KEY_LABEL_VERTICAL_ADJUSTMENT_FACTOR = 0.55f;
                float baseline = centerY
                        + labelHeight * KEY_LABEL_VERTICAL_ADJUSTMENT_FACTOR;
                if (hasSubLabel) {
                    final int subLabelSize = (int) (mSubLabelTextSize * keySizeScale * labelSizeScale);
                    final int subLabelHeight;
                    final int subLabelWidth;
                    paint.setTypeface(Typeface.DEFAULT_BOLD);

                    paint.setTextSize(subLabelSize);
                    if (mTextHeightCache.get(subLabelSize) != null) {
                        subLabelHeight = mTextHeightCache.get(subLabelSize);
                        subLabelWidth = mTextWidthCache.get(subLabelSize);
                    } else {

                        Rect textBounds = new Rect();
                        paint.getTextBounds(KEY_LABEL_HEIGHT_REFERENCE_CHAR, 0, 1, textBounds);
                        subLabelHeight = textBounds.height();
                        subLabelWidth = textBounds.width();
                        mTextHeightCache.put(subLabelSize, subLabelHeight);
                        mTextWidthCache.put(subLabelSize, subLabelWidth);
                    }

                    //portrait keyboard
                    if (key.height > key.width || subLabel.length() >2 || hasSecondSubLabel) {
                        baseline = (key.height + padding.top - padding.bottom) * 2 / 3
                                + labelHeight * KEY_LABEL_VERTICAL_ADJUSTMENT_FACTOR;
                        float subBaseline = (key.height + padding.top - padding.bottom) / 4
                                + subLabelHeight * KEY_LABEL_VERTICAL_ADJUSTMENT_FACTOR;
                        paint.setColor(subKeyColor);

                        if (hasSecondSubLabel) {
                            canvas.drawText(subLabel, centerX / 2, subBaseline, paint);

                            paint.setColor(keyColor);
                            canvas.drawText(secondSubLabel, centerX / 2 * 3, subBaseline, paint);
                        } else
                            canvas.drawText(subLabel, centerX, subBaseline, paint);

                        paint.setTextSize(labelSize);
                        paint.setTypeface(mKeyTextStyle);
                        paint.setColor(keyColor);
                        canvas.drawText(label, centerX, baseline, paint);

                    } else {    //landscape keyboard
                        paint.setColor(subKeyColor);
                        //if (subLabel.length() > 2)  // draw sub keys as portrait keys in two rows.
                        //    paint.setTextSize(subLabelSize * 2 / 3);  //123 EN  in landscape is usually to wide.
                        /*if (hasSecondSubLabel) {
                                                    canvas.drawText(subLabel, centerX - subLabelWidth * 2, baseline, paint);
                                                    paint.setColor(keyColor);
                                                    canvas.drawText(secondSubLabel, centerX - subLabelWidth, baseline, paint);
                                                } else*/
                        canvas.drawText(subLabel, centerX - subLabelWidth, baseline, paint);

                        paint.setTextSize(labelSize);
                        paint.setTypeface(mKeyTextStyle);
                        paint.setColor(keyColor);
                        canvas.drawText(label, centerX + labelWidth/2, baseline, paint);

                    }

                } else {
                    paint.setColor(keyColor);
                    canvas.drawText(label, centerX, baseline, paint);
                }
                // Turn off drop shadow
                if (mShadowRadius > 0) paint.setShadowLayer(0, 0, 0, 0);

                // Usually don't draw icon if label is not null, but we draw icon for the number
                // hint and popup hint.
                shouldDrawIcon = shouldDrawLabelAndIcon(key);
            }
            if (shouldDrawIcon) {
                Drawable icon = key.icon;
                if (icon == null)
                    icon = mPopupHint;
                else {
                    icon.setState(drawableState);
                }


                // Special handing for the upper-right number hint icons
                final int drawableWidth;
                final int drawableHeight;
                final int drawableX;
                final int drawableY;
                if (shouldDrawIconFully(key)) {
                    drawableWidth = key.width;
                    drawableHeight = key.height;
                    drawableX = 0;
                    drawableY = NUMBER_HINT_VERTICAL_ADJUSTMENT_PIXEL;
                } else {

                    drawableHeight = key.height; // icon.getIntrinsicHeight();
                    drawableWidth = icon.getIntrinsicWidth() * drawableHeight / icon.getIntrinsicHeight()  ;
                    drawableX = (key.width + padding.left - padding.right - drawableWidth) / 2;
                    drawableY = (key.height + padding.top - padding.bottom - drawableHeight) / 2;
                }
                canvas.translate(drawableX, drawableY);
                icon.setBounds(0, 0, drawableWidth, drawableHeight);
                icon.draw(canvas);
                canvas.translate(-drawableX, -drawableY);
            }
            canvas.translate(-key.x - kbdPaddingLeft, -key.y - kbdPaddingTop);
        }
        mInvalidatedKey = null;
        // Overlay a dark rectangle to dim the keyboard
        if (mMiniKeyboardOnScreen) {
            paint.setColor((int) (mBackgroundDimAmount * 0xFF) << 24);
            canvas.drawRect(0, 0, getWidth(), getHeight(), paint);
        }
        if (DEBUG && mShowTouchPoints) {
            paint.setAlpha(128);
            paint.setColor(0xFFFF0000);
            canvas.drawCircle(mStartX, mStartY, 3, paint);
            canvas.drawLine(mStartX, mStartY, mLastX, mLastY, paint);
            paint.setColor(0xFF0000FF);
            canvas.drawCircle(mLastX, mLastY, 3, paint);
            paint.setColor(0xFF00FF00);
            canvas.drawCircle((mStartX + mLastX) / 2, (mStartY + mLastY) / 2, 2, paint);
        }


        mDrawPending = false;
        mDirtyRect.setEmpty();
    }

    private int getKeyIndices(int x, int y, int[] allKeys) {
        final Key[] keys = mKeys;
        int primaryIndex = NOT_A_KEY;
        int closestKey = NOT_A_KEY;
        int closestKeyDist = mProximityThreshold + 1;
        java.util.Arrays.fill(mDistances, Integer.MAX_VALUE);
        int [] nearestKeyIndices = mKeyboard.getNearestKeys(x, y);
        final int keyCount = nearestKeyIndices.length;
        for (int i = 0; i < keyCount; i++) {
            final Key key = keys[nearestKeyIndices[i]];
            int dist = 0;
            boolean isInside = key.isInside(x,y);
            if (isInside) {
                primaryIndex = nearestKeyIndices[i];
            }

            if (((mProximityCorrectOn
                    && (dist = key.squaredDistanceFrom(x, y)) < mProximityThreshold)
                    || isInside)
                    && key.codes[0] > 32) {
                // Find insertion point
                final int nCodes = key.codes.length;
                if (dist < closestKeyDist) {
                    closestKeyDist = dist;
                    closestKey = nearestKeyIndices[i];
                }

                if (allKeys == null) continue;

                for (int j = 0; j < mDistances.length; j++) {
                    if (mDistances[j] > dist) {
                        // Make space for nCodes codes
                        System.arraycopy(mDistances, j, mDistances, j + nCodes,
                                mDistances.length - j - nCodes);
                        System.arraycopy(allKeys, j, allKeys, j + nCodes,
                                allKeys.length - j - nCodes);
                        for (int c = 0; c < nCodes; c++) {
                            allKeys[j + c] = key.codes[c];
                            mDistances[j + c] = dist;
                        }
                        break;
                    }
                }
            }
        }
        if (primaryIndex == NOT_A_KEY) {
            primaryIndex = closestKey;
        }
        return primaryIndex;
    }

    private void detectAndSendKey(int index, int x, int y, long eventTime) {
        if (index != NOT_A_KEY && index < mKeys.length) {
            final Key key = mKeys[index];
            if (key.text != null) {
                mKeyboardActionListener.onText(key.text);
                mKeyboardActionListener.onRelease(NOT_A_KEY);
            } else {
                int code = key.codes[0];
                //TextEntryState.keyPressedAt(key, x, y);
                int[] codes = new int[MAX_NEARBY_KEYS];
                Arrays.fill(codes, NOT_A_KEY);
                getKeyIndices(x, y, codes);
                // Multi-tap
                if (mInMultiTap) {
                    if (mTapCount != -1) {
                        mKeyboardActionListener.onKey(Keyboard.KEYCODE_DELETE, KEY_DELETE);
                    } else {
                        mTapCount = 0;
                    }
                    code = key.codes[mTapCount];
                }
                mKeyboardActionListener.onKey(code, codes);
                mKeyboardActionListener.onRelease(code);
            }
            mLastSentIndex = index;
            mLastTapTime = eventTime;
        }
    }


    /**
     * Handle multi-tap keys by producing the key label for the current multi-tap state.
     */
    private CharSequence getPreviewText(Key key) {
        if (mInMultiTap) {
            // Multi-tap
            mPreviewLabel.setLength(0);
            mPreviewLabel.append((char) key.codes[mTapCount < 0 ? 0 : mTapCount]);
            return adjustCase(mPreviewLabel);
        } else {
            return adjustCase(key.label);
        }
    }



    public void showPreview(int keyIndex) {

        int oldKeyIndex = mCurrentKeyIndex;
        final PopupWindow previewPopup = mPreviewPopup;
        mCurrentKeyIndex = keyIndex;

        if(DEBUG)
            Log.i(TAG,"showPreview() keyindex =" + keyIndex + "oldKeyIndex = " + oldKeyIndex);

        // Release the old key and press the new key
        final Key[] keys = mKeys;

        if (oldKeyIndex != mCurrentKeyIndex) {
            if (oldKeyIndex != NOT_A_KEY && keys.length > oldKeyIndex) {
                Key oldKey = keys[oldKeyIndex];
                oldKey.onReleased(mCurrentKeyIndex == NOT_A_KEY);
                invalidateKey(oldKeyIndex);

            }
            if (mCurrentKeyIndex != NOT_A_KEY && keys.length > mCurrentKeyIndex) {
                Key newKey = keys[mCurrentKeyIndex];
                newKey.onPressed();
                invalidateKey(mCurrentKeyIndex);

            }
        }
        final boolean hidePreviewOrShowSpaceKeyPreview  =
                (mCurrentKeyIndex!= NOT_A_KEY && keys[mCurrentKeyIndex].codes[0] == ' ')
                ||(oldKeyIndex!= NOT_A_KEY && keys[oldKeyIndex].codes[0] == ' ');
        // If key changed and preview is on ...
        if (oldKeyIndex != mCurrentKeyIndex && mShowPreview &&
                !(mCurrentKeyIndex!= NOT_A_KEY && keys[mCurrentKeyIndex].isFunctionalKey()) 
                || hidePreviewOrShowSpaceKeyPreview ) {
            mHandler.removeMessages(MSG_SHOW_PREVIEW);
            if (previewPopup.isShowing()) {
                if (keyIndex == NOT_A_KEY) {
                    mHandler.sendMessageDelayed(mHandler
                                    .obtainMessage(MSG_REMOVE_PREVIEW),
                            mDelayAfterPreview);
                }
            }
            if (keyIndex != NOT_A_KEY) {
                if (previewPopup.isShowing() && mPreviewText.getVisibility() == VISIBLE) {
                    // Show right away, if it's already visible and finger is moving around
                    showKey(keyIndex);
                } else {
                    mHandler.sendMessageDelayed(
                            mHandler.obtainMessage(MSG_SHOW_PREVIEW, keyIndex, 0),
                            mDelayBeforePreview);
                }
            }
        }
    }

    private void showKey(final int keyIndex) {
        if(DEBUG)
            Log.i(TAG,"showKey() keyIndex =" + keyIndex);
        final PopupWindow previewPopup = mPreviewPopup;
        final Key[] keys = mKeys;
        if (keyIndex < 0 || keyIndex >= mKeys.length) return;
        Key key = keys[keyIndex];
        // Should not draw hint icon in key preview
        if (key.icon != null && !hasPopupKeyboard(key)  || key.codes[0]==' ' ) {
            mPreviewText.setCompoundDrawables(null,
                    key.iconPreview != null ? key.iconPreview : key.icon, null, null);
            mPreviewText.setText(null);
        } else if(key.label !=null) {
            mPreviewText.setCompoundDrawables(null, null, null, null);
            mPreviewText.setText(adjustCase(getPreviewText(key)));
            if (key.label.length() > 1 && key.codes.length < 2) {
                mPreviewText.setTextSize(TypedValue.COMPLEX_UNIT_PX, mKeyTextSize
                        * key.getLabelSizeScale() * mKeyboard.getKeySizeScale()); //Jeremy '12,6,7 scale the preview key text size
                mPreviewText.setTypeface(Typeface.DEFAULT_BOLD);
            } else {
                mPreviewText.setTextSize(TypedValue.COMPLEX_UNIT_PX, mPreviewTextSizeLarge
                        * key.getLabelSizeScale() * mKeyboard.getKeySizeScale());//Jeremy '12,6,7 scale the preview key text size
                mPreviewText.setTypeface(mKeyTextStyle);
            }
        }
        mPreviewText.setPadding (mPreviewText.getPaddingLeft(),  //Jeremy '15,7,13
                ((key.codes[0] == ' ') ? mSpacePreviewTopPadding : mPreviewTopPadding),
                mPreviewText.getPaddingRight(), 0);
        mPreviewText.measure(MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED),
                MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED));
        int popupWidth = Math.max(mPreviewText.getMeasuredWidth(), key.width
                + mPreviewText.getPaddingLeft() + mPreviewText.getPaddingRight());
        //Jeremy '15,7,13 minus key.height from popupHeight if it's space key for sliding IM switching preview
        final int popupHeight = (int) (mPreviewHeight * mKeyboard.getKeySizeScale() - (( key.codes[0] == ' ')? key.height :0) ) ;

        LayoutParams lp = mPreviewText.getLayoutParams();
        if (lp != null) {
            lp.width = popupWidth;
            lp.height = popupHeight;
        }
        /*
        if (!mPreviewCentered) {
            mPopupPreviewX = key.x - mPreviewText.getPaddingLeft() + mPaddingLeft;
            mPopupPreviewY = key.y - popupHeight + mPreviewOffset;
        } else {
            // TODO: Fix this if centering is brought back
            mPopupPreviewX = 160 - mPreviewText.getMeasuredWidth() / 2;
            mPopupPreviewY = - mPreviewText.getMeasuredHeight();
        }
        */
        mHandler.removeMessages(MSG_REMOVE_PREVIEW);

        int popupPreviewX = key.x - (popupWidth - key.width) / 2;
        //Jeremy '15,7,13 add key.height to cover whole key if it's not space key
        int popupPreviewY = (key.y +  (( key.codes[0] == ' ')?0: key.height) - popupHeight + mPreviewOffset);

        if (mOffsetInWindow == null) {
            mOffsetInWindow = new int[2];
            getLocationInWindow(mOffsetInWindow);
            mOffsetInWindow[0] += mPopupPreviewOffsetX; // Offset may be zero
            mOffsetInWindow[1] += mPopupPreviewOffsetY; // Offset may be zero
            int[] windowLocation = new int[2];
            getLocationOnScreen(windowLocation);
            mWindowY = windowLocation[1];
        }
        // Set the preview background state
        mPreviewText.getBackground().setState(
                key.popupResId != 0 ? LONG_PRESSABLE_STATE_SET : EMPTY_STATE_SET);
        popupPreviewX += mOffsetInWindow[0];
        popupPreviewY += mOffsetInWindow[1];

        // If the popup cannot be shown above the key, put it on the side
        if (popupPreviewY + mWindowY < 0) {
            // If the key you're pressing is on the left side of the keyboard, show the popup on
            // the right, offset by enough to see at least one key to the left/right.
            if (key.x + key.width <= getWidth() / 2) {
                popupPreviewX += (int) (key.width * 2.5);
            } else {
                popupPreviewX -= (int) (key.width * 2.5);
            }
            popupPreviewY += popupHeight;
        }

        if (previewPopup.isShowing()) {
            previewPopup.update(popupPreviewX, popupPreviewY, popupWidth, popupHeight);
        } else {
            previewPopup.setWidth(popupWidth);
            previewPopup.setHeight(popupHeight);
            previewPopup.showAtLocation(mPopupParent, Gravity.NO_GRAVITY,
                    popupPreviewX, popupPreviewY);
        }
        // Record popup preview position to display mini-keyboard later at the same positon
        //mPopupPreviewDisplayedY = popupPreviewY;
        mPreviewText.setVisibility(VISIBLE);
    }

    /**
     * Requests a redraw of the entire keyboard. Calling {@link #invalidate} is not sufficient
     * because the keyboard renders the keys to an off-screen buffer and an invalidate() only
     * draws the cached buffer.
     *
     * @see #invalidateKey
     */
    public void invalidateAllKeys() {
        mDirtyRect.union(0, 0, getWidth(), getHeight());
        mDrawPending = true;
        invalidate();
    }

    /**
     * Invalidates a key so that it will be redrawn on the next repaint. Use this method if only
     * one key is changing it's content. Any changes that affect the position or size of the key
     * may not be honored.
     * @param keyIndex the index of the key in the attached {@link Keyboard}.
     * @see #invalidateAllKeys
     */
    public void invalidateKey(int keyIndex) {
        if (mKeys == null)  return;
        if (keyIndex < 0 || keyIndex >= mKeys.length) {
            return;
        }
        final Key key = mKeys[keyIndex];

        mInvalidatedKey = key;

        mDirtyRect.union(key.x + getPaddingLeft(), key.y + getPaddingTop(),
                key.x + key.width + getPaddingLeft(), key.y + key.height + getPaddingTop());
        onBufferDraw();
        invalidate(key.x + getPaddingLeft(), key.y + getPaddingTop(),
                key.x + key.width + getPaddingLeft(), key.y + key.height + getPaddingTop());
    }

    private boolean openPopupIfRequired() {
        // Check if we have a popup layout specified first.
        if (mPopupLayout == 0) {
            return false;
        }
        if (mCurrentKey < 0 || mCurrentKey >= mKeys.length) {
            return false;
        }

        Key popupKey = mKeys[mCurrentKey];
        boolean result = onLongPress(popupKey);
        if (result) {
            mAbortKey = true;
            showPreview(NOT_A_KEY);
        }
        return result;
    }




    /**
     * Called when a key is long pressed. By default this will open any popup keyboard associated
     * with this key through the attributes popupLayout and popupCharacters.
     *
     * @param popupKey the key that was long pressed
     * @return true if the long press is handled, false otherwise. Subclasses should call the
     * method on the base class if the subclass doesn't wish to handle the call.
     */
    protected boolean onLongPress(Key popupKey) {
        int popupKeyboardId = popupKey.popupResId;

        if (popupKeyboardId != 0) {
            View mMiniKeyboardContainer = mMiniKeyboardCache.get(popupKey);
            LIMEKeyboardBaseView mMiniKeyboard;
            if (mMiniKeyboardContainer == null) {
                LayoutInflater inflater = (LayoutInflater) getContext().getSystemService(
                        Context.LAYOUT_INFLATER_SERVICE);
                mMiniKeyboardContainer = inflater.inflate(mPopupLayout, null);
                mMiniKeyboard = (LIMEKeyboardBaseView) mMiniKeyboardContainer.findViewById(
                        R.id.LIMEPopupKeyboard);
                //View closeButton = mMiniKeyboardContainer.findViewById(R.id.LIMEPopupKeyboard);
                //if (closeButton != null) closeButton.setOnClickListener(this);
                mMiniKeyboard.setOnKeyboardActionListener(new OnKeyboardActionListener() {
                    public void onKey(int primaryCode, int[] keyCodes) {
                        mKeyboardActionListener.onKey(primaryCode, keyCodes);
                        dismissPopupKeyboard();
                    }

                    public void onText(CharSequence text) {
                        mKeyboardActionListener.onText(text);
                        dismissPopupKeyboard();
                    }

                    public void swipeLeft() { }
                    public void swipeRight() { }
                    public void swipeUp() { }
                    public void swipeDown() { }
                    public void onPress(int primaryCode) {
                        mKeyboardActionListener.onPress(primaryCode);
                    }
                    public void onRelease(int primaryCode) {
                        mKeyboardActionListener.onRelease(primaryCode);
                    }
                });
                //mInputView.setSuggest(mSuggest);
               LIMEBaseKeyboard keyboard;
                if (popupKey.popupCharacters != null) {
                    keyboard = new LIMEBaseKeyboard(mContext, popupKeyboardId, popupKey.popupCharacters,
                            -1, getPaddingLeft() + getPaddingRight(),
                            LIMEKeyboardBaseView.this.mKeyboard.getKeySizeScale());
                } else {
                    keyboard = new LIMEBaseKeyboard(mContext, popupKeyboardId
                            , LIMEKeyboardBaseView.this.mKeyboard.getKeySizeScale(), 0, 0); //Jeremy '12,5,21 never show arrow keys in popup keyboard
                }
                mMiniKeyboard.setKeyboard(keyboard);
                mMiniKeyboard.setPopupParent(this);
                mMiniKeyboardContainer.measure(
                        MeasureSpec.makeMeasureSpec(getWidth(), MeasureSpec.AT_MOST),
                        MeasureSpec.makeMeasureSpec(getHeight(), MeasureSpec.AT_MOST));

                mMiniKeyboardCache.put(popupKey, mMiniKeyboardContainer);
            } else {
                mMiniKeyboard = (LIMEKeyboardBaseView) mMiniKeyboardContainer.findViewById(
                        R.id.LIMEPopupKeyboard);
            }
            getLocationInWindow(mCoordinates);
            int PopupX = popupKey.x + mPaddingLeft;
            int PopupY = popupKey.y + mPaddingTop;
            PopupX = PopupX + popupKey.width - mMiniKeyboardContainer.getMeasuredWidth();
            PopupY = PopupY - mMiniKeyboardContainer.getMeasuredHeight();
            final int x = PopupX + mMiniKeyboardContainer.getPaddingRight() + mCoordinates[0];
            final int y = PopupY + mMiniKeyboardContainer.getPaddingBottom() + mCoordinates[1];
            mMiniKeyboard.setPopupOffset(x < 0 ? 0 : x, y);
            mMiniKeyboard.setShifted(isShifted());
            mPopupKeyboard.setContentView(mMiniKeyboardContainer);
            mPopupKeyboard.setWidth(mMiniKeyboardContainer.getMeasuredWidth());
            mPopupKeyboard.setHeight(mMiniKeyboardContainer.getMeasuredHeight());
            mPopupKeyboard.showAtLocation(this, Gravity.NO_GRAVITY, x, y);
            mMiniKeyboardOnScreen = true;

            invalidateAllKeys();
            return true;
        }
        return false;
    }



    @Override
    public boolean onTouchEvent(MotionEvent me) {
        // Convert multi-pointer up/down events to single up/down events to
        // deal with the typical multi-pointer behavior of two-thumb typing
        final int pointerCount = me.getPointerCount();
        final int action = me.getAction();
        boolean result;
        final long now = me.getEventTime();

        if (pointerCount != mOldPointerCount) {
            if (pointerCount == 1) {
                // Send a down event for the latest pointer
                MotionEvent down = MotionEvent.obtain(now, now, MotionEvent.ACTION_DOWN,
                        me.getX(), me.getY(), me.getMetaState());
                result = onModifiedTouchEvent(down, false);
                down.recycle();
                // If it's an up action, then deliver the up as well.
                if (action == MotionEvent.ACTION_UP) {
                    result = onModifiedTouchEvent(me, true);
                }
            } else {
                // Send an up event for the last pointer
                MotionEvent up = MotionEvent.obtain(now, now, MotionEvent.ACTION_UP,
                        mOldPointerX, mOldPointerY, me.getMetaState());
                result = onModifiedTouchEvent(up, true);
                up.recycle();
            }
        } else {
            if (pointerCount == 1) {
                result = onModifiedTouchEvent(me, false);
                mOldPointerX = me.getX();
                mOldPointerY = me.getY();
            } else {
                // Don't do anything when 2 pointers are down and moving.
                result = true;
            }
        }
        mOldPointerCount = pointerCount;

        return result;
    }

    private boolean onModifiedTouchEvent(MotionEvent me, boolean possiblePoly) {
        int touchX = (int) me.getX() - mPaddingLeft;
        int touchY = (int) me.getY() - mPaddingTop;
        if (touchY >= -mVerticalCorrection)
            touchY += mVerticalCorrection;
        final int action = me.getAction();
        final long eventTime = me.getEventTime();
        int keyIndex = getKeyIndices(touchX, touchY, null);
        mPossiblePoly = possiblePoly;

        // Track the last few movements to look for spurious swipes.
        if (action == MotionEvent.ACTION_DOWN) mSwipeTracker.clear();
        mSwipeTracker.addMovement(me);

        // Ignore all motion events until a DOWN.
        if (mAbortKey
                && action != MotionEvent.ACTION_DOWN && action != MotionEvent.ACTION_CANCEL) {
            return true;
        }

        if (mGestureDetector.onTouchEvent(me)) {
            showPreview(NOT_A_KEY);
            mHandler.removeMessages(MSG_REPEAT);
            mHandler.removeMessages(MSG_LONGPRESS);
            return true;
        }

        // Needs to be called after the gesture detector gets a turn, as it may have
        // displayed the mini keyboard
        if (mMiniKeyboardOnScreen && action != MotionEvent.ACTION_CANCEL) {
            return true;
        }

        switch (action) {
            case MotionEvent.ACTION_DOWN:
                mAbortKey = false;
                mStartX = touchX;
                mStartY = touchY;
                mLastCodeX = touchX;
                mLastCodeY = touchY;
                mLastKeyTime = 0;
                mCurrentKeyTime = 0;
                mLastKey = NOT_A_KEY;
                mCurrentKey = keyIndex;
                mDownKey = keyIndex;
                mDownTime = me.getEventTime();
                mLastMoveTime = mDownTime;
                checkMultiTap(eventTime, keyIndex);
                mKeyboardActionListener.onPress(keyIndex != NOT_A_KEY ?
                        mKeys[keyIndex].codes[0] : 0);
                if (mCurrentKey >= 0 && mKeys[mCurrentKey].repeatable) {
                    mRepeatKeyIndex = mCurrentKey;
                    Message msg = mHandler.obtainMessage(MSG_REPEAT);
                    mHandler.sendMessageDelayed(msg, REPEAT_START_DELAY);
                    repeatKey();
                    // Delivering the key could have caused an abort
                    if (mAbortKey) {
                        mRepeatKeyIndex = NOT_A_KEY;
                        break;
                    }
                }
                if (mCurrentKey != NOT_A_KEY) {
                    Message msg = mHandler.obtainMessage(MSG_LONGPRESS, me);
                    mHandler.sendMessageDelayed(msg, LONGPRESS_TIMEOUT);
                }
                showPreview(keyIndex);
                break;

            case MotionEvent.ACTION_MOVE:
                boolean continueLongPress = false;
                if (keyIndex != NOT_A_KEY) {
                    if (mCurrentKey == NOT_A_KEY) {
                        mCurrentKey = keyIndex;
                        mCurrentKeyTime = eventTime - mDownTime;
                    } else {
                        if (keyIndex == mCurrentKey) {
                            mCurrentKeyTime += eventTime - mLastMoveTime;
                            continueLongPress = true;
                        } else if (mRepeatKeyIndex == NOT_A_KEY) {
                            resetMultiTap();
                            mLastKey = mCurrentKey;
                            mLastCodeX = mLastX;
                            mLastCodeY = mLastY;
                            mLastKeyTime =
                                    mCurrentKeyTime + eventTime - mLastMoveTime;
                            mCurrentKey = keyIndex;
                            mCurrentKeyTime = 0;
                        }
                    }
                }
                if (!continueLongPress) {
                    // Cancel old longpress
                    mHandler.removeMessages(MSG_LONGPRESS);
                    // Start new longpress if key has changed
                    if (keyIndex != NOT_A_KEY) {
                        Message msg = mHandler.obtainMessage(MSG_LONGPRESS, me);
                        mHandler.sendMessageDelayed(msg, LONGPRESS_TIMEOUT);
                    }
                }
                showPreview(mCurrentKey);
                mLastMoveTime = eventTime;
                break;

            case MotionEvent.ACTION_UP:
                removeMessages();
                if (keyIndex == mCurrentKey) {
                    mCurrentKeyTime += eventTime - mLastMoveTime;
                } else {
                    resetMultiTap();
                    mLastKey = mCurrentKey;
                    mLastKeyTime = mCurrentKeyTime + eventTime - mLastMoveTime;
                    mCurrentKey = keyIndex;
                    mCurrentKeyTime = 0;
                }
                if (mCurrentKeyTime < mLastKeyTime && mCurrentKeyTime < DEBOUNCE_TIME
                        && mLastKey != NOT_A_KEY) {
                    mCurrentKey = mLastKey;
                    touchX = mLastCodeX;
                    touchY = mLastCodeY;
                }
                showPreview(NOT_A_KEY);
                Arrays.fill(mKeyIndices, NOT_A_KEY);
                // If we're not on a repeating key (which sends on a DOWN event)
                if (mRepeatKeyIndex == NOT_A_KEY && !mMiniKeyboardOnScreen && !mAbortKey) {
                    detectAndSendKey(mCurrentKey, touchX, touchY, eventTime);
                }
                invalidateKey(keyIndex);
                mRepeatKeyIndex = NOT_A_KEY;
                break;
            case MotionEvent.ACTION_CANCEL:
                removeMessages();
                dismissPopupKeyboard();
                mAbortKey = true;
                showPreview(NOT_A_KEY);
                invalidateKey(mCurrentKey);
                break;
        }
        mLastX = touchX;
        mLastY = touchY;
        return true;
    }




    private boolean shouldDrawIconFully(Key key) {
        return (hasPopupKeyboard(key));
        //return isNumberAtEdgeOfPopupChars(key) || isLatinF1Key(key);
        //|| LIMEKeyboard.hasPuncOrSmileysPopup(key);

    }

    private boolean shouldDrawLabelAndIcon(Key key) {
        return hasPopupKeyboard(key) ||  key.icon != null;

    }

    private boolean hasPopupKeyboard(Key key) {
        return key.popupResId != 0;
    }


   private static boolean isAsciiDigit(char c) {
        return (c < 0x80) && Character.isDigit(c);
    }


    private void resetMultiTap() {
        mLastSentIndex = NOT_A_KEY;
        mTapCount = 0;
        mLastTapTime = -1;
        mInMultiTap = false;
    }

    private void checkMultiTap(long eventTime, int keyIndex) {
        if (keyIndex == NOT_A_KEY) return;
        Key key = mKeys[keyIndex];
        if (key.codes.length > 1) {
            mInMultiTap = true;
            if (eventTime < mLastTapTime + MULTITAP_INTERVAL
                    && keyIndex == mLastSentIndex) {
                mTapCount = (mTapCount + 1) % key.codes.length;
                return;
            } else {
                mTapCount = -1;
                return;
            }
        }
        if (eventTime > mLastTapTime + MULTITAP_INTERVAL || keyIndex != mLastSentIndex) {
            resetMultiTap();
        }
    }

    private static class SwipeTracker {

        static final int NUM_PAST = 4;
        static final int LONGEST_PAST_TIME = 200;

        final float mPastX[] = new float[NUM_PAST];
        final float mPastY[] = new float[NUM_PAST];
        final long mPastTime[] = new long[NUM_PAST];

        float mYVelocity;
        float mXVelocity;

        public void clear() {
            mPastTime[0] = 0;
        }

        public void addMovement(MotionEvent ev) {
            long time = ev.getEventTime();
            final int N = ev.getHistorySize();
            for (int i=0; i<N; i++) {
                addPoint(ev.getHistoricalX(i), ev.getHistoricalY(i),
                        ev.getHistoricalEventTime(i));
            }
            addPoint(ev.getX(), ev.getY(), time);
        }

        private void addPoint(float x, float y, long time) {
            int drop = -1;
            int i;
            final long[] pastTime = mPastTime;
            for (i=0; i<NUM_PAST; i++) {
                if (pastTime[i] == 0) {
                    break;
                } else if (pastTime[i] < time-LONGEST_PAST_TIME) {
                    drop = i;
                }
            }
            if (i == NUM_PAST && drop < 0) {
                drop = 0;
            }
            if (drop == i) drop--;
            final float[] pastX = mPastX;
            final float[] pastY = mPastY;
            if (drop >= 0) {
                final int start = drop+1;
                final int count = NUM_PAST-drop-1;
                System.arraycopy(pastX, start, pastX, 0, count);
                System.arraycopy(pastY, start, pastY, 0, count);
                System.arraycopy(pastTime, start, pastTime, 0, count);
                i -= (drop+1);
            }
            pastX[i] = x;
            pastY[i] = y;
            pastTime[i] = time;
            i++;
            if (i < NUM_PAST) {
                pastTime[i] = 0;
            }
        }

        public void computeCurrentVelocity(int units) {
            computeCurrentVelocity(units, Float.MAX_VALUE);
        }

        public void computeCurrentVelocity(int units, float maxVelocity) {
            final float[] pastX = mPastX;
            final float[] pastY = mPastY;
            final long[] pastTime = mPastTime;

            final float oldestX = pastX[0];
            final float oldestY = pastY[0];
            final long oldestTime = pastTime[0];
            float accumX = 0;
            float accumY = 0;
            int N=0;
            while (N < NUM_PAST) {
                if (pastTime[N] == 0) {
                    break;
                }
                N++;
            }

            for (int i=1; i < N; i++) {
                final int dur = (int)(pastTime[i] - oldestTime);
                if (dur == 0) continue;
                float dist = pastX[i] - oldestX;
                float vel = (dist/dur) * units;   // pixels/frame.
                if (accumX == 0) accumX = vel;
                else accumX = (accumX + vel) * .5f;

                dist = pastY[i] - oldestY;
                vel = (dist/dur) * units;   // pixels/frame.
                if (accumY == 0) accumY = vel;
                else accumY = (accumY + vel) * .5f;
            }
            mXVelocity = accumX < 0.0f ? Math.max(accumX, -maxVelocity)
                    : Math.min(accumX, maxVelocity);
            mYVelocity = accumY < 0.0f ? Math.max(accumY, -maxVelocity)
                    : Math.min(accumY, maxVelocity);
        }

        public float getXVelocity() {
            return mXVelocity;
        }

        public float getYVelocity() {
            return mYVelocity;
        }
    }

    private boolean repeatKey() {
        Key key = mKeys[mRepeatKeyIndex];
        detectAndSendKey(mCurrentKey, key.x, key.y, mLastTapTime);
        return true;
    }
    protected void swipeRight() {
        mKeyboardActionListener.swipeRight();
    }

    protected void swipeLeft() {
        mKeyboardActionListener.swipeLeft();
    }

    protected void swipeUp() {
        mKeyboardActionListener.swipeUp();
    }

    protected void swipeDown() {
        mKeyboardActionListener.swipeDown();
    }

    public void closing() {
        removeMessages();
        if (mPreviewPopup.isShowing()) {
            mHandler.sendMessage(mHandler
                    .obtainMessage(MSG_REMOVE_PREVIEW));
        }

        dismissPopupKeyboard();
        mBuffer = null;
        mCanvas = null;
        mMiniKeyboardCache.clear();
    }

    private void removeMessages() {
        mHandler.removeMessages(MSG_REPEAT);
        mHandler.removeMessages(MSG_LONGPRESS);
        mHandler.removeMessages(MSG_SHOW_PREVIEW);
    }

    @Override
    public void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        closing();
    }

    private void dismissPopupKeyboard() {
        if (mPopupKeyboard.isShowing()) {
            mPopupKeyboard.dismiss();
            mMiniKeyboardOnScreen = false;
            invalidateAllKeys();
        }


    }

    public boolean handleBack() {
        if (mPopupKeyboard.isShowing()) {
            dismissPopupKeyboard();
            return true;
        }
        return false;
    }
}
