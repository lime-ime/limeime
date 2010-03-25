/*
 * Copyright (C) 2008-2009 Google Inc.
 * 
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 * 
 * http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
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
	static final String PREF = "LIMEXY";

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


}
