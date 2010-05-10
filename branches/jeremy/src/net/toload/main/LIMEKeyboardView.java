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

package net.toload.main;

import android.content.Context;
import android.content.SharedPreferences;
import android.inputmethodservice.Keyboard;
import android.inputmethodservice.KeyboardView;
import android.inputmethodservice.Keyboard.Key;
import android.inputmethodservice.KeyboardView.OnKeyboardActionListener;
import android.util.AttributeSet;
import android.util.Log;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.GestureDetector.OnGestureListener;

/**
 * @author Art Hung
 */
public class LIMEKeyboardView extends KeyboardView {

	static final int KEYCODE_OPTIONS = -100;
	static final int KEYCODE_SHIFT_LONGPRESS = -101;
	static final String PREF = "LIMEXY";
	
    private Keyboard mPhoneKeyboard;

	public LIMEKeyboardView(Context context, AttributeSet attrs) {
		super(context, attrs);
	}

	public LIMEKeyboardView(Context context, AttributeSet attrs, int defStyle) {
		super(context, attrs, defStyle);
	}

	@Override
	protected boolean onLongPress(Key key) {
		if (key.codes[0] == Keyboard.KEYCODE_CANCEL) {
			getOnKeyboardActionListener().onKey(KEYCODE_OPTIONS, null);
			return true;
		} else if (key.codes[0] == Keyboard.KEYCODE_SHIFT) {
            getOnKeyboardActionListener().onKey(KEYCODE_SHIFT_LONGPRESS, null);
            // This require API 4 > 1.5
            //invalidateAllKeys();
            return true;
		} else {
			return super.onLongPress(key);
		}
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * android.inputmethodservice.KeyboardView#onTouchEvent(android.view.MotionEvent
	 * )
	 */
	@Override
	public boolean onTouchEvent(MotionEvent me) {
		
		// Store Touch Position to Preference for data exchange between activity
		if(me.getAction() == MotionEvent.ACTION_DOWN){
			SharedPreferences sp1 = this.getContext().getSharedPreferences(PREF, 0);
							  sp1.edit().putString("xy", String.valueOf(me.getX())+","+String.valueOf(me.getY())).commit();
		}else if(me.getAction() == MotionEvent.ACTION_UP){
			SharedPreferences sp1 = this.getContext().getSharedPreferences(PREF, 0);
							  sp1.edit().putString("xy", String.valueOf(me.getX())+","+String.valueOf(me.getY())).commit();
		}
		return super.onTouchEvent(me);
	}
	
	
	  public void setPhoneKeyboard(Keyboard phoneKeyboard) {
	        mPhoneKeyboard = phoneKeyboard;
	    }

}
