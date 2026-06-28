/*
 *
 *  *
 *  **    Copyright 2025, The LimeIME Open Source Project
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

package net.toload.main.hd.keyboard;

import net.toload.main.hd.R;
import net.toload.main.hd.keyboard.LIMEBaseKeyboard.Key;

import android.annotation.SuppressLint;
import android.content.Context;
import androidx.annotation.NonNull;
import android.util.AttributeSet;
import android.util.Log;
import android.view.MotionEvent;

public class LIMEKeyboardView extends LIMEKeyboardBaseView {
	static final boolean DEBUG = false;
	static final String TAG = "LIMEKeyboardView";

	public static final int KEYCODE_OPTIONS = -100;
	//public static final int KEYCODE_SHIFT_LONGPRESS = -101;
	public static final int KEYCODE_SPACE_LONGPRESS = -102;
    public static final int KEYCODE_NEXT_IM = -104;
    public static final int KEYCODE_PREV_IM = -105;
    public static final int KEYCODE_PHONE_SIMPLE_LONGPRESS = -106;

	//static final String PREF = "LIMEXY";
	
	//private boolean mLongPressProcessed;
	
   // private Keyboard mPhoneKeyboard;
  
    private final int mKeyHeight;
    private final int mSpaceCaretDeadZone;
    private final int mSpaceCaretStepPx;
    private int mSpaceCaretPointerId = -1;
    private int mSpaceCaretStartX;
    private int mLastSpaceCaretStep;
    private boolean mSpaceCaretMoved;
    private boolean mSpaceCaretCancelled;

	public LIMEKeyboardView(Context context, AttributeSet attrs) {
		super(context, attrs);
		mKeyHeight = context.getResources().getDimensionPixelSize(R.dimen.key_height);
        mSpaceCaretDeadZone = context.getResources().getDimensionPixelSize(R.dimen.space_caret_dead_zone);
        mSpaceCaretStepPx = context.getResources().getDimensionPixelSize(R.dimen.space_caret_step);
	}

	public LIMEKeyboardView(Context context, AttributeSet attrs, int defStyle) {
		super(context, attrs, defStyle);
		mKeyHeight = context.getResources().getDimensionPixelSize(R.dimen.key_height);
        mSpaceCaretDeadZone = context.getResources().getDimensionPixelSize(R.dimen.space_caret_dead_zone);
        mSpaceCaretStepPx = context.getResources().getDimensionPixelSize(R.dimen.space_caret_step);
	}

    // feat#124: the English-layout symbol key keeps its "123" face (@string/label_symbol_key)
    // but carries a popupKeyboard so the existing "..." minikeyboard hint renders at the bottom.
    // Only English layouts give the -2 (mode-change) key a popup, so popupResId != 0 uniquely
    // identifies the phone_simple long-press shortcut.
    public static boolean isEnglishPhoneSimpleShortcutKey(int primaryCode, int popupResId) {
        return primaryCode == LIMEBaseKeyboard.KEYCODE_MODE_CHANGE && popupResId != 0;
    }

	@Override
	protected boolean onLongPress(Key key) {
		if(DEBUG)
			Log.i(TAG, "onLongPress, keycode = "+ key.codes[0] 
				+"; spaceDragDiff = " +((LIMEKeyboard) this.getKeyboard()).getSpaceDragDiff()
				+"; key_height = " + mKeyHeight
					);
		if (key.codes[0] == LIMEBaseKeyboard.KEYCODE_DONE) {
			getOnKeyboardActionListener().onKey(KEYCODE_OPTIONS, null,0,0);
			return true;
        } else if (isEnglishPhoneSimpleShortcutKey(key.codes[0], key.popupResId)) {
            getOnKeyboardActionListener().onKey(KEYCODE_PHONE_SIMPLE_LONGPRESS, null,0,0);
            return true;
		}else if (key.codes[0] == LIMEKeyboard.KEYCODE_SPACE
				&& Math.abs(((LIMEKeyboard) this.getKeyboard()).getSpaceDragDiff() ) < mKeyHeight/5){ //Jeremy '12,4,23 avoid small move blocking the long press.
			getOnKeyboardActionListener().onKey(KEYCODE_SPACE_LONGPRESS, null,0,0);
			return true;
		} else {
			return super.onLongPress(key);
		}
	}
	

	/*
	 *
	 */
	@SuppressLint("ClickableViewAccessibility")
    @Override
	public boolean onTouchEvent(@NonNull MotionEvent me) {
		if(DEBUG) Log.i(TAG, "OnTouchEvent(), me.getActionMasked() =" + me.getActionMasked());
		LIMEKeyboard keyboard = (LIMEKeyboard) getKeyboard();
        final int action = me.getActionMasked();
		if (action == MotionEvent.ACTION_DOWN) {
			if(DEBUG) Log.i(TAG, "OnTouchEvent(), ACTION_DOWN");
			keyboard.keyReleased();
            mSpaceCaretPointerId = me.getPointerId(0);
            mSpaceCaretStartX = (int) me.getX(0);
            mLastSpaceCaretStep = 0;
            mSpaceCaretMoved = false;
            mSpaceCaretCancelled = false;
            if (!isTouchOnSpaceKey((int) me.getX(0), (int) me.getY(0))) {
                mSpaceCaretPointerId = -1;
            }
		}

        if (action == MotionEvent.ACTION_MOVE && handleSpaceCaretMove(me, keyboard)) {
            return true;
        }

        if (isEndingActiveSpaceCaret(action, me)) {
            final boolean consumed = mSpaceCaretMoved;
            resetSpaceCaretState();
            if (consumed) {
                keyboard.keyReleased();
                return true;
            }
        }

		return super.onTouchEvent(me);
	}

    private boolean handleSpaceCaretMove(@NonNull MotionEvent me, LIMEKeyboard keyboard) {
        if (mSpaceCaretPointerId == -1) {
            return false;
        }
        final int pointerIndex = me.findPointerIndex(mSpaceCaretPointerId);
        if (pointerIndex < 0) {
            return false;
        }

        final int dx = (int) me.getX(pointerIndex) - mSpaceCaretStartX;
        if (Math.abs(dx) < mSpaceCaretDeadZone) {
            return false;
        }

        if (!mSpaceCaretCancelled) {
            mSpaceCaretCancelled = true;
            mSpaceCaretMoved = true;
            MotionEvent cancelEvent = MotionEvent.obtain(me);
            cancelEvent.setAction(MotionEvent.ACTION_CANCEL);
            super.onTouchEvent(cancelEvent);
            cancelEvent.recycle();
            keyboard.keyReleased();
        }

        final int step = (dx < 0 ? -1 : 1) * stepsForSpaceDisplacement(Math.abs(dx));
        final int delta = step - mLastSpaceCaretStep;
        if (delta != 0) {
            mLastSpaceCaretStep = step;
            mSpaceCaretMoved = true;
            getOnKeyboardActionListener().moveCaretBy(delta);
        }
        return true;
    }

    private boolean isEndingActiveSpaceCaret(int action, @NonNull MotionEvent me) {
        if (mSpaceCaretPointerId == -1) {
            return false;
        }
        if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
            return true;
        }
        if (action == MotionEvent.ACTION_POINTER_UP) {
            return me.getPointerId(me.getActionIndex()) == mSpaceCaretPointerId;
        }
        return false;
    }

    private void resetSpaceCaretState() {
        mSpaceCaretPointerId = -1;
        mSpaceCaretStartX = 0;
        mLastSpaceCaretStep = 0;
        mSpaceCaretMoved = false;
        mSpaceCaretCancelled = false;
    }

    private boolean isTouchOnSpaceKey(int x, int y) {
        if (getKeyboard() == null) {
            return false;
        }
        for (Key key : getKeyboard().getKeys()) {
            if (key.codes != null && key.codes.length > 0
                    && key.codes[0] == LIMEBaseKeyboard.KEYCODE_SPACE
                    && x >= key.x && x < key.x + key.width
                    && y >= key.y && y < key.y + key.height) {
                return true;
            }
        }
        return false;
    }

    private int stepsForSpaceDisplacement(int absDx) {
        int travel = absDx - mSpaceCaretDeadZone;
        if (travel <= 0) {
            return 0;
        }

        final float density = getResources().getDisplayMetrics().density;
        final float t1 = 60f * density;
        final float t2 = 140f * density;
        final float step = mSpaceCaretStepPx;

        final float steps;
        if (travel <= t1) {
            steps = travel / step;
        } else if (travel <= t2) {
            steps = t1 / step + (travel - t1) / (step / 2f);
        } else {
            steps = t1 / step + (t2 - t1) / (step / 2f) + (travel - t2) / (step / 4f);
        }
        return (int) steps;
    }
	

}
