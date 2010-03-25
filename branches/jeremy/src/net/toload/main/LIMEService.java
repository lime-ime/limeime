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

import android.gesture.GestureOverlayView;
import android.gesture.GestureOverlayView.OnGestureListener;
import android.inputmethodservice.InputMethodService;
import android.inputmethodservice.Keyboard;
import android.inputmethodservice.KeyboardView;
import android.inputmethodservice.Keyboard.Key;
import android.media.AudioManager;
import android.os.IBinder;
import android.os.RemoteException;
import android.os.Vibrator;
import android.preference.PreferenceManager;
import android.text.method.MetaKeyKeyListener;
import android.util.Log;
import android.view.GestureDetector;
import android.view.KeyCharacterMap;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.view.inputmethod.CompletionInfo;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputConnection;
import android.view.inputmethod.InputMethodManager;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;

import android.app.AlertDialog;
import android.app.Service;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.database.sqlite.SQLiteCursor;

/**
 * @author Art Hung
 */
public class LIMEService extends InputMethodService implements
		KeyboardView.OnKeyboardActionListener {

	static final boolean DEBUG = false;
	static final String PREF = "LIMEXY";
	
	static final int KEYBOARD_SWITCH_CODE = -9;

	static final boolean PROCESS_HARD_KEYS = true;

	private KeyboardView mInputView;
	private CandidateView mCandidateView;
	private CompletionInfo[] mCompletions;

	private StringBuilder mComposing = new StringBuilder();

	private boolean mPredictionOn;
	private boolean mCompletionOn;
	private boolean mCapsLock;
	private boolean mHasShift;
	//------------------------------------------------------------------------
	// Add by Jeremy '10, 3,12
	// new private variable mHasAlt for keeping state of alt. 
	private boolean mHasAlt = false;
	// '10, 3, 24 fix for continuous alt mode and alt-lock function.
	private boolean mTrackAlt =false;
	private boolean mAltLocked =false;
	//------------------------------------------------------------------------
	private boolean mEnglishOnly;
	private boolean mEnglishFlagShift;
	private boolean onIM = false;
	private boolean hasFirstMatched = false;
	private boolean hasRightShiftPress = false;

	private long mLastShiftTime;
	private long mMetaState;

	private int mLastDisplayWidth;

	private LIMEKeyboard mSymbolsKeyboard;
	private LIMEKeyboard mSymbolsShiftedKeyboard;
	private LIMEKeyboard mKeyboard;
	private LIMEKeyboard mNumberKeyboard;
	private LIMEKeyboard mNumberShiftKeyboard;
	private LIMEKeyboard mCJKeyboard;
	private LIMEKeyboard mCJShiftKeyboard;
	private LIMEKeyboard mCJNumberKeyboard;
	private LIMEKeyboard mCJNumberShiftKeyboard;
	private LIMEKeyboard mPhoneticKeyboard;
	private LIMEKeyboard mPhoneticShiftKeyboard;
	private LIMEKeyboard mDayiKeyboard;
	private LIMEKeyboard mDayiShiftKeyboard;
	private LIMEKeyboard mPhoneKeyboard;

	private LIMEKeyboard mCurKeyboard;

	private Mapping firstMatched;
	private Mapping tempMatched;

	private String mWordSeparators;
	private String misMatched;
	private LimeDB limedb;

	private LinkedList<Mapping> templist;
	private LinkedList<Mapping> userdiclist;

	private Vibrator mVibrator;
	private AudioManager mAudioManager;
	private final float FX_VOLUME = 1.0f;
	private static final int KEYCODE_ENTER = 10;
	private static final int KEYCODE_SPACE = ' ';

	private boolean hasVibration = false;
	private boolean hasSound = false;
	private boolean hasNumberKeypads = false;
	private boolean hasNumberMapping = false;
	private boolean hasSymbolMapping = false;
	private boolean hasKeyPress = false;
	private boolean hasQuickSwitch = false;

	private String keyboardSelection;

	private int keyDownCode = 0;
	private float keyDownX = 0;
	private float keyDownY = 0;
	private float keyUpX=0;
	private float keyUpY=0;
	
	private int previousKeyCode = 0;
	private final float moveLength = 15;
	private ISearchService SearchSrv = null;
	
	/*
	 * Construct SerConn 
	 */
	private ServiceConnection serConn = new ServiceConnection() {
		public void onServiceConnected(ComponentName name, IBinder service) {
			
			SearchSrv = ISearchService.Stub.asInterface(service);
			try {
				SearchSrv.initial();
			} catch (RemoteException e) {
				e.printStackTrace();
			}
		}
		public void onServiceDisconnected(ComponentName name) {}
	};
	
	/**
	 * Main initialization of the input method component. Be sure to call to
	 * super class.
	 */
	@Override
	public void onCreate() {

		super.onCreate();

		// Initial and Create Database
		mEnglishOnly = false;
		mEnglishFlagShift = false;

		// Startup Service
		if(SearchSrv == null){
			this.bindService(new Intent(ISearchService.class.getName()), serConn, Context.BIND_AUTO_CREATE);
		}
		
		mVibrator = (Vibrator) getApplication().getSystemService(
				Service.VIBRATOR_SERVICE);
		mAudioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);

		SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(this);
		hasVibration = sp.getBoolean("vibrate_on_keypress", false);
		hasSound = sp.getBoolean("sound_on_keypress", false);
		hasNumberKeypads = sp.getBoolean("display_number_keypads", false);
		hasNumberMapping = sp.getBoolean("accept_number_index", false);
		hasSymbolMapping = sp.getBoolean("accept_symbol_index", false);
		
		keyboardSelection = sp.getString("keyboard_list", "lime");
		
		// initial Input List
		userdiclist = new LinkedList<Mapping>();
		
	}

	/**
	 * This is the point where you can do all of your UI initialization. It is
	 * called after creation and any configuration change.
	 */
	@Override
	public void onInitializeInterface() {
		if (mKeyboard != null) {
			int displayWidth = getMaxWidth();
			if (displayWidth == mLastDisplayWidth)
				return;
			mLastDisplayWidth = displayWidth;
		}

		mEnglishOnly = false;
		mEnglishFlagShift = false;

		// Lime Standard Keyboard
		mKeyboard = new LIMEKeyboard(this, R.xml.lime);

		// Symbol/Number Keyboard
		mNumberKeyboard = new LIMEKeyboard(this, R.xml.lime_number);
		mNumberShiftKeyboard = new LIMEKeyboard(this, R.xml.lime_number_shift);

		// Initial Phone Keyboard
		mPhoneKeyboard = new LIMEKeyboard(this,R.xml.phone);

		// Initial Symbol Keyboard
		mSymbolsKeyboard = new LIMEKeyboard(this, R.xml.symbols);
		mSymbolsShiftedKeyboard = new LIMEKeyboard(this, R.xml.symbols_shift);

		// Initial CJ Keyboard
		mCJKeyboard = new LIMEKeyboard(this, R.xml.lime_cj);
		mCJShiftKeyboard = new LIMEKeyboard(this,R.xml.lime_cj_shift);
		mCJNumberKeyboard = new LIMEKeyboard(this, R.xml.lime_cj_number);
		mCJNumberShiftKeyboard = new LIMEKeyboard(this,R.xml.lime_cj_number_shift);

		// Initial Phonetic Keyboard
		mPhoneticKeyboard = new LIMEKeyboard(this, R.xml.lime_phonetic);
		mPhoneticShiftKeyboard = new LIMEKeyboard(this,R.xml.lime_phonetic_shift);

		// INitial Dayi Keyboard
		mDayiKeyboard = new LIMEKeyboard(this, R.xml.lime_dayi);
		mDayiShiftKeyboard = new LIMEKeyboard(this,R.xml.lime_dayi_shift);

	}

	/**
	 * Called by the framework when your view for creating input needs to be
	 * generated. This will be called the first time your input method is
	 * displayed, and every time it needs to be re-created such as due to a
	 * configuration change.
	 */
	@Override
	public View onCreateInputView() {
		mInputView = (KeyboardView) getLayoutInflater().inflate(R.layout.input, null);
		mInputView.setOnKeyboardActionListener(this);

		// initialKeyboard();

		onIM = true;
		return mInputView;
	}

	/**
	 * Called by the framework when your view for showing candidates needs to be
	 * generated, like {@link #onCreateInputView}.
	 */
	@Override
	public View onCreateCandidatesView() {
		mCandidateView = new CandidateView(this);
		mCandidateView.setService(this);
		return mCandidateView;
	}

	/**
	 * This is the main point where we do our initialization of the input method
	 * to begin operating on an application. At this point we have been bound to
	 * the client, and are now receiving all of the detailed information about
	 * the target of our edits.
	 */
	@Override
	public void onStartInput(EditorInfo attribute, boolean restarting) {
		super.onStartInput(attribute, restarting);
		
		initialKeyboard();
		
		userdiclist = new LinkedList<Mapping>();
		templist = new LinkedList<Mapping>();
		firstMatched = new Mapping();
		tempMatched = new Mapping();

		setCandidatesViewShown(false);

		// Reset our state. We want to do this even if restarting, because
		// the underlying state of the text editor could have changed in any
		// way.
		mComposing.setLength(0);
		updateCandidates();

		if (!restarting) {
			// Clear shift states.
			mMetaState = 0;
		}

		mPredictionOn = false;
		mCompletionOn = false;
		mCompletions = null;

		// We are now going to initialize our state based on the type of
		// text being edited.
		
		switch (attribute.inputType & EditorInfo.TYPE_MASK_CLASS) {
		case EditorInfo.TYPE_CLASS_NUMBER:
		case EditorInfo.TYPE_CLASS_DATETIME:
		case EditorInfo.TYPE_CLASS_PHONE:
			mCurKeyboard = mSymbolsKeyboard;
			onIM = false;
			break;
		case EditorInfo.TYPE_CLASS_TEXT:
			//initialKeyboard();
			mPredictionOn = true;
			onIM = true;

			// We now look for a few special variations of text that will
			// modify our behavior.
			int variation = attribute.inputType & EditorInfo.TYPE_MASK_VARIATION;
			if (variation == EditorInfo.TYPE_TEXT_VARIATION_PASSWORD
					|| variation == EditorInfo.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD) {
				// Do not display predictions / what the user is typing
				// when they are entering a password.
				mPredictionOn = false;
			}

			if (variation == EditorInfo.TYPE_TEXT_VARIATION_EMAIL_ADDRESS
					|| variation == EditorInfo.TYPE_TEXT_VARIATION_URI
					|| variation == EditorInfo.TYPE_TEXT_VARIATION_FILTER) {
				// Our predictions are not useful for e-mail addresses
				// or URIs.
				mPredictionOn = false;
			}

			// We also want to look at the current state of the editor
			// to decide whether our alphabetic keyboard should start out
			// shifted.
			//updateShiftKeyState(attribute);
			break;

		default:
			// For all unknown input types, default to the alphabetic
			// keyboard with no special features.
			//initialKeyboard();
			onIM = true;
			//updateShiftKeyState(attribute);
		}

		// Update the label on the enter key, depending on what the application
		// says it will do.
		mCurKeyboard.setImeOptions(getResources(), attribute.imeOptions);
	}

	/**
	 * This is called when the user is done editing a field. We can use this to
	 * reset our state.
	 */
	@Override
	public void onFinishInput() {

		super.onFinishInput();

		// initialKeyboard();

		if (mInputView != null) {
			mInputView.closing();
		}

		// Clear current composing text and candidates.
		mComposing.setLength(0);
		updateCandidates();

		setCandidatesViewShown(false);
		onIM = false;

		// Add Custom related words
		if(userdiclist.size() > 1){
			
			for(Mapping dicunit : userdiclist){
				if(dicunit.getId() == null){continue;}
				try {
					SearchSrv.addDictionary(dicunit.getId(), 
											dicunit.getCode(), 
											dicunit.getWord(), 
											dicunit.getPcode(), 
											dicunit.getPword(), 
											dicunit.getScore(), 
											dicunit.isDictionary());
				} catch (RemoteException e) {
					e.printStackTrace();
				}
			}
			try {
				SearchSrv.updateDictionary();
			} catch (RemoteException e) {
				e.printStackTrace();
			}
			userdiclist.clear();
		}
		
		this.setSuggestions(null, false, false);
	}

	@Override
	public void onStartInputView(EditorInfo attribute, boolean restarting) {
		super.onStartInputView(attribute, restarting);
		// Apply the selected keyboard to the input view.
		mInputView.setKeyboard(mCurKeyboard);
		mInputView.closing();

		SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(this);
		hasVibration = sp.getBoolean("vibrate_on_keypress", false);
		hasSound = sp.getBoolean("sound_on_keypress", false);
		hasNumberKeypads = sp.getBoolean("display_number_keypads", false);
		hasNumberMapping = sp.getBoolean("accept_number_index", false);
		hasSymbolMapping = sp.getBoolean("accept_symbol_index", false);
		keyboardSelection = sp.getString("keyboard_list", "lime");

		hasQuickSwitch = sp.getBoolean("switch_english_mode", false);
	}

	/**
	 * Deal with the editor reporting movement of its cursor.
	 */
	@Override
	public void onUpdateSelection(int oldSelStart, int oldSelEnd,
			int newSelStart, int newSelEnd, int candidatesStart,
			int candidatesEnd) {
		super.onUpdateSelection(oldSelStart, oldSelEnd, newSelStart, newSelEnd,
				candidatesStart, candidatesEnd);

		// If the current selection in the text view changes, we should
		// clear whatever candidate text we have.
		if (mComposing.length() > 0 && (newSelStart != candidatesEnd || newSelEnd != candidatesEnd)) {
			mComposing.setLength(0);
			updateCandidates();
			InputConnection ic = getCurrentInputConnection();
			if (ic != null) {
				ic.finishComposingText();
			}
		}
	}

	/**
	 * This tells us about completions that the editor has determined based on
	 * the current text in it. We want to use this in fullscreen mode to show
	 * the completions ourself, since the editor can not be seen in that
	 * situation.
	 */
	@Override
	public void onDisplayCompletions(CompletionInfo[] completions) {
		if (mCompletionOn) {
			mCompletions = completions;
			if (completions == null) {
				setSuggestions(null, false, false);
				return;
			}

			LinkedList<Mapping> stringList = new LinkedList<Mapping>();
			for (int i = 0; i < (completions != null ? completions.length : 0); i++) {
				CompletionInfo ci = completions[i];
				if (ci != null)
					try {
						stringList.addAll(SearchSrv.query(ci.getText().toString()));
					} catch (RemoteException e) {
						e.printStackTrace();
					}
			}
			setSuggestions(stringList, true, true);
		}
	}

	/**
	 * This translates incoming hard key events in to edit operations on an
	 * InputConnection. It is only needed when using the PROCESS_HARD_KEYS
	 * option.
	 */
	private boolean translateKeyDown(int keyCode, KeyEvent event) {
		//Log.i("translateKeyDown","metastate before handle keydown:" + String.valueOf(mMetaState)
		//	    + "MetaAltOn:"+ String.valueOf(mMetaState & MetaKeyKeyListener.META_ALT_ON) );
		mMetaState = MetaKeyKeyListener.handleKeyDown(mMetaState, keyCode, event);
		int c = event.getUnicodeChar(MetaKeyKeyListener.getMetaState(mMetaState));
		//Log.i("translateKeyDown","metastate before adjust:" + String.valueOf(mMetaState)
		//		+ "MetaAltOn:"+ String.valueOf(mMetaState & MetaKeyKeyListener.META_ALT_ON) );
		mMetaState = MetaKeyKeyListener.adjustMetaAfterKeypress(mMetaState);
		//Log.i("translateKeyDown","metastate after adjust:" + String.valueOf(mMetaState)
		//		+ "MetaAltOn:"+ String.valueOf(mMetaState & MetaKeyKeyListener.META_ALT_ON) );
		InputConnection ic = getCurrentInputConnection();
		
		
		
		if(keyCode == 59){
			c = -1;
		}
		if (c == 0 || ic == null) {
			return false;
		}

		boolean dead = false;

		if (c != -1 && (c & KeyCharacterMap.COMBINING_ACCENT) != 0) {
			dead = true;
			c = c & KeyCharacterMap.COMBINING_ACCENT_MASK;
		}

		if (mComposing.length() > 0) {
			char accent = mComposing.charAt(mComposing.length() - 1);
			int composed = KeyEvent.getDeadChar(accent, c);

			if (composed != 0) {
				c = composed;
				mComposing.setLength(mComposing.length() - 1);
			}
		}

		if(!hasRightShiftPress && mHasShift && c >= 97 && c <=122){
			c -= 32;
		}else if(hasRightShiftPress && c >= 97 && c <=122){
			c -= 32;
			hasRightShiftPress = false;
		}
		
		
		onKey(c, null);

		return true;
	}

	/**
	 * Use this to monitor key events being delivered to the application. We get
	 * first crack at them, and can either resume them or let them continue to
	 * the app.
	 */
	
	@Override
	public boolean onKeyDown(int keyCode, KeyEvent event) {
		
		//Log.i("OnKeyDown", "keyCode:" + keyCode + "Meta_ALT_ON:"
		//		+ String.valueOf(mMetaState & MetaKeyKeyListener.META_ALT_ON) );
			
		
			switch (keyCode) {
			case KeyEvent.KEYCODE_BACK:
				// The InputMethodService already takes care of the back
				// key for us, to dismiss the input method if it is shown.
				// However, our keyboard could be showing a pop-up window
				// that back should dismiss, so we first allow it to do that.
				if (event.getRepeatCount() == 0 && mInputView != null) {
					if (mInputView.handleBack()) {
						return true;
					}
				}
				break;
		
			case KeyEvent.KEYCODE_DEL:
				// Special handling of the delete key: if we currently are
				// composing text for the user, we want to modify that instead
				// of let the application to the delete itself.
				
				
				if (mComposing.length() > 0) {
					onKey(Keyboard.KEYCODE_DELETE, null);
					return true;
				}
	
				if (mCandidateView != null) {
					mCandidateView.clear();
				}
				mComposing.setLength(0);
				setCandidatesViewShown(false);
				
				//------------------------------------------------------------------------
				// Modified by Jeremy '10, 3,12
				// block milestone alt-del to delete whole line
				// clear alt state before processed by super
				InputConnection ic = getCurrentInputConnection();
				mHasAlt = false;
				if (ic != null) 
					ic.clearMetaKeyStates(KeyEvent.META_ALT_ON);
				//------------------------------------------------------------------------
				break;
	
			case KeyEvent.KEYCODE_ENTER:
				// Let the underlying text editor always handle these.
				return false;
			case KeyEvent.KEYCODE_ALT_LEFT:
				// Let the underlying text editor always handle these.
				mTrackAlt = true;

			case KeyEvent.KEYCODE_ALT_RIGHT:
				// Let the underlying text editor always handle these.
				mTrackAlt = true;
				
			default:
	
				// For all other keys, if we want to do transformations on
				// text being entered with a hard keyboard, we need to process
				// it and do the appropriate action.
	    
				if(keyCode == 60){
					this.hasRightShiftPress = true;
				}
			
				if (PROCESS_HARD_KEYS) {
					if (keyCode == KeyEvent.KEYCODE_SPACE
							&& (event.getMetaState()& KeyEvent.META_ALT_ON) != 0) {
						// A silly example: in our input method, Alt+Space
						// is a shortcut for 'android' in lower case.
						//InputConnection 
						ic = getCurrentInputConnection();
						if (ic != null) {
							// First, tell the editor that it is no longer in the
							// shift state, since we are consuming this.
							ic.clearMetaKeyStates(KeyEvent.META_ALT_ON);
							keyDownUp(KeyEvent.KEYCODE_A);
							keyDownUp(KeyEvent.KEYCODE_N);
							keyDownUp(KeyEvent.KEYCODE_D);
							keyDownUp(KeyEvent.KEYCODE_R);
							keyDownUp(KeyEvent.KEYCODE_O);
							keyDownUp(KeyEvent.KEYCODE_I);
							keyDownUp(KeyEvent.KEYCODE_D);
							// And we consume this event.
							return true;
						}
	
						if (mCandidateView != null) {
							mCandidateView.clear();
						}
						mComposing.setLength(0);
						setCandidatesViewShown(false);
					}
	
					
				
					if (mPredictionOn && translateKeyDown(keyCode, event)) {
						return true;
					}
				}
				
			}
		
		return super.onKeyDown(keyCode, event);
	}

	/**
	 * Use this to monitor key events being delivered to the application. We get
	 * first crack at them, and can either resume them or let them continue to
	 * the app.
	 */
	@Override
	public boolean onKeyUp(int keyCode, KeyEvent event) {
		//Log.i("OnKeyUp", "keyCode:" + keyCode + "KeyEvent.Alt:"
		//		+ String.valueOf(mMetaState & MetaKeyKeyListener.META_ALT_ON) );
		
		switch (keyCode) {
		//*/------------------------------------------------------------------------
		// Modified by Jeremy '10, 3,12
		// keep track of alt state with mHasAlt.
		// Modified '10, 3, 24 for bug fix and alc-lock implementation
		case KeyEvent.KEYCODE_ALT_LEFT:
			handleAlt();
			break;
		case KeyEvent.KEYCODE_ALT_RIGHT:
			handleAlt();
			break;
		default:
			if(mTrackAlt && !mAltLocked) {  // Clear mHasAlt if Metakey state if Alt not locked.
				InputConnection ic = getCurrentInputConnection();	
				ic.clearMetaKeyStates(KeyEvent.META_ALT_ON);
				mTrackAlt = false;
			}
		}
		//------------------------------------------------------------------------
		//*/
		// If we want to do transformations on text being entered with a hard
		// keyboard, we need to process the up events to update the meta key
		//* state we are tracking.
		if (PROCESS_HARD_KEYS) {
			if (mPredictionOn) {
				mMetaState = MetaKeyKeyListener.handleKeyUp(mMetaState,
						keyCode, event);
			}
		}
		
		return super.onKeyUp(keyCode, event);
	}

	/**
	 * Helper function to commit any text being composed in to the editor.
	 */
	private void commitTyped(InputConnection inputConnection) {

		try{
			if (mComposing.length() > 0 || (firstMatched != null && firstMatched.isDictionary())) {
	
				if (onIM) {
					if (firstMatched != null && firstMatched.getWord() != null && !firstMatched.getWord().equals("")) {
						int firstMatchedLength = firstMatched.getWord().length();
						if (firstMatched.getCode() == null || firstMatched.getCode().equals("")) {
							firstMatchedLength = 1;
						}
						inputConnection.commitText(firstMatched.getWord(), firstMatchedLength);
							try {
								SearchSrv.updateMapping(firstMatched.getId(), 
										firstMatched.getCode(), 
										firstMatched.getWord(), 
										firstMatched.getPcode(), 
										firstMatched.getPword(), 
										firstMatched.getScore(), 
										firstMatched.isDictionary());
							} catch (RemoteException e) {
								e.printStackTrace();
							}
						userdiclist.add(firstMatched);
						tempMatched = firstMatched;
						firstMatched = null;
						hasFirstMatched = true;
					} else if (firstMatched != null && firstMatched.getWord() != null
							&& firstMatched.getWord().equals("")) {
						inputConnection.commitText(misMatched, misMatched.length());
						firstMatched = null;
						hasFirstMatched = false;
	
						userdiclist.add(null);
					} else {
						inputConnection.commitText(mComposing, mComposing.length());
						hasFirstMatched = false;
						userdiclist.add(null);
					}
				} else {
					inputConnection.commitText(mComposing, mComposing.length());
					hasFirstMatched = false;
					userdiclist.add(null);
				}
	
				if (mCandidateView != null) {
					mCandidateView.clear();
				}
				mComposing.setLength(0);
				
				updateDictionaryView();
				
			}
		}catch(Exception e){
			e.printStackTrace();
		}
	}

	/**
	 * Helper to update the shift state of our keyboard based on the initial
	 * editor state.
	 */
	private void updateShiftKeyState(EditorInfo attr) {

		if (attr != null && mInputView != null) {
			int caps = 0;
			EditorInfo ei = getCurrentInputEditorInfo();
			if (ei != null && ei.inputType != EditorInfo.TYPE_NULL) {
				caps = getCurrentInputConnection().getCursorCapsMode(attr.inputType);
			}
			mInputView.setShifted(mCapsLock || caps != 0);
		}
	}

	private boolean isValidLetter(int code) {
		if (Character.isLetter(code)) {
			return true;
		} else {
			return false;
		}
	}

	private boolean isValidDigit(int code) {
		if (Character.isDigit(code)) {
			return true;
		} else {
			return false;
		}
	}

	private boolean isValidSymbol(int code) {
		String checkCode = String.valueOf((char) code);
		if (checkCode.matches(".*?[^A-Z]") && checkCode.matches(".*?[^a-z]")
				&& checkCode.matches(".*?[^0-9]") && code != 32 ) {
			return true;
		} else {
			return false;
		}
	}

	/**
	 * Helper to send a key down / key up pair to the current editor.
	 */
	private void keyDownUp(int keyEventCode) {
		getCurrentInputConnection().sendKeyEvent(
				new KeyEvent(KeyEvent.ACTION_DOWN, keyEventCode));
		getCurrentInputConnection().sendKeyEvent(
				new KeyEvent(KeyEvent.ACTION_UP, keyEventCode));
	}

	/**
	 * Helper to send a character to the editor as raw key events.
	 */
	private void sendKey(int keyCode) {

		switch (keyCode) {
		case '\n':
			keyDownUp(KeyEvent.KEYCODE_ENTER);
			break;
		default:

			if (keyCode == 32 && firstMatched == null && !hasFirstMatched) {
				getCurrentInputConnection().commitText(
						String.valueOf((char) keyCode), 1);
			} else {
				if (keyCode != 32 && firstMatched != null && !hasFirstMatched) {
					getCurrentInputConnection().commitText(
							String.valueOf((char) keyCode), 1);
				} else if (keyCode != 32) {
					getCurrentInputConnection().commitText(
							String.valueOf((char) keyCode), 1);
				} else if (keyCode == 32 && (!hasFirstMatched)) {
					getCurrentInputConnection().commitText(
							String.valueOf((char) keyCode), 1);
				} else if (keyCode == 32 && this.mComposing.length() == 0 && this.tempMatched != null
						&& !this.tempMatched.getCode().trim().equals("")) {
					// Press Space Button + has matched keyword then do nothing
				} else if (keyCode == 32 && this.mComposing.length() == 0 && this.tempMatched != null
						&& this.tempMatched.getCode().trim().equals("")) {
					// Press Space Button + no matched keyword consider as English append space at the end
					getCurrentInputConnection().commitText(String.valueOf((char) keyCode), 1);
				}
				hasFirstMatched = false;
			}
			break;
		}
	}

	// Implementation of KeyboardViewListener

	public void onKey(int primaryCode, int[] keyCodes) {
		
		// Handle English/Lime Keyboard switch
		if(mEnglishFlagShift == false && (primaryCode == Keyboard.KEYCODE_SHIFT) ){
			mEnglishFlagShift = true;
		}else{
			// Check if user input [Shift] + [Space] switch keyboard
			if(hasQuickSwitch == true){
				if(mEnglishFlagShift == true && primaryCode == 32){
					mEnglishOnly = !mEnglishOnly;
					if (mEnglishOnly) {
						Toast.makeText(this, R.string.typing_mode_english, Toast.LENGTH_SHORT).show();
					} else {
						Toast.makeText(this, R.string.typing_mode_mixed, Toast.LENGTH_SHORT).show();
					}
				}
				// Reset Shift Status
				mEnglishFlagShift = false;
			}
		}
		
	
		if (isWordSeparator(primaryCode)) {
			if (mComposing.length() > 0) {
				commitTyped(getCurrentInputConnection());
			}
			sendKey(primaryCode);
			//updateShiftKeyState(getCurrentInputEditorInfo());
		} else if (primaryCode == Keyboard.KEYCODE_DELETE) {
			handleBackspace();
		} else if (primaryCode == Keyboard.KEYCODE_SHIFT) {
			handleShift();
		} else if (primaryCode == Keyboard.KEYCODE_CANCEL) {
			handleClose();
			return;
		} else if (primaryCode == LIMEKeyboardView.KEYCODE_OPTIONS) {
			handleOptions();
		} else if (primaryCode == Keyboard.KEYCODE_MODE_CHANGE && mInputView != null) {
			switchKeyboard(primaryCode);
		}  else if (primaryCode == -9 && mInputView != null) {
			switchKeyboard(primaryCode);
		} else {
			handleCharacter(primaryCode, keyCodes);
		}
		
		
	}

	private AlertDialog mOptionsDialog;
	 // Contextual menu positions
    private static final int POS_SETTINGS = 0;
    private static final int POS_KEYBOARD = 1;
    private static final int POS_METHOD = 2;
    
    private void handleOptions() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setCancelable(true);
        builder.setIcon(R.drawable.sym_keyboard_done);
        builder.setNegativeButton(android.R.string.cancel, null);
        builder.setTitle(getResources().getString(R.string.ime_name));
        
        CharSequence itemSettings = getString(R.string.ime_setting);
        CharSequence itemKeyboadList = getString(R.string.keyboard_list);
        CharSequence itemInputMethod = getString(R.string.input_method);
        
        builder.setItems(new CharSequence[] {
                itemSettings, itemKeyboadList, itemInputMethod},
                new DialogInterface.OnClickListener() {

            public void onClick(DialogInterface di, int position) {
                di.dismiss();
                switch (position) {
                    case POS_SETTINGS:
                        launchSettings();
                        break;
                    case POS_KEYBOARD:
                        //
                    	showKeyboardPicker();
                        break;
                    case POS_METHOD:
                        ((InputMethodManager) getSystemService(INPUT_METHOD_SERVICE))
                            .showInputMethodPicker();
                        break;
                }
            }
        });
        
        mOptionsDialog = builder.create();
        Window window = mOptionsDialog.getWindow();
        WindowManager.LayoutParams lp = window.getAttributes();
        lp.token = mInputView.getWindowToken();
        lp.type = WindowManager.LayoutParams.TYPE_APPLICATION_ATTACHED_DIALOG;
        window.setAttributes(lp);
        window.addFlags(WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM);
        mOptionsDialog.show();
    }

    private void launchSettings() {
        handleClose();
        Intent intent = new Intent();
        intent.setClass(LIMEService.this, LIMEMenu.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(intent);
    }
    
    private void showKeyboardPicker(){
    	AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setCancelable(true);
        builder.setIcon(R.drawable.sym_keyboard_done);
        builder.setNegativeButton(android.R.string.cancel, null);
        builder.setTitle(getResources().getString(R.string.keyboard_list));
        
        final CharSequence[] items = getResources().getStringArray(R.array.keyboard);
        
        int curKB=0;
        
        if (keyboardSelection.equals("lime")){
			curKB=0;
		} else if(keyboardSelection.equals("phone")){
			curKB=1;
		} else if(keyboardSelection.equals("cj")){
			curKB=2;
		} else if(keyboardSelection.equals("dayi")){
			curKB=3;
		} else if(keyboardSelection.equals("phonetic")){
			curKB=4;
		}
		
        
        builder.setSingleChoiceItems(
        		items, 
        		curKB, 
        		new DialogInterface.OnClickListener() {

			public void onClick(DialogInterface di, int position) {
                di.dismiss(); 
                handlKeyboardSelection(position);      
            }
        });
        
        mOptionsDialog = builder.create();
        Window window = mOptionsDialog.getWindow();
        WindowManager.LayoutParams lp = window.getAttributes();
        lp.token = mInputView.getWindowToken();
        lp.type = WindowManager.LayoutParams.TYPE_APPLICATION_ATTACHED_DIALOG;
        window.setAttributes(lp);
        window.addFlags(WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM);
        mOptionsDialog.show();
        
    }
    
    private void handlKeyboardSelection(int position){
    	SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(this);
        SharedPreferences.Editor spe = sp.edit();
        
        if (position == 0){
        		keyboardSelection = "lime";
		} else if(position == 1){
			keyboardSelection = "phone";
		} else if(position == 2){
			keyboardSelection = "cj";
		} else if(position == 3){
			keyboardSelection = "dayi";
		} else if(position == 4){
			keyboardSelection = "phonetic";
		}
        
        spe.putString("keyboard_list", keyboardSelection);
        spe.commit();
        initialKeyboard();
    	
    }
    
	public void onText(CharSequence text) {

		InputConnection ic = getCurrentInputConnection();
		if (ic == null)
			return;
		ic.beginBatchEdit();
		if (mComposing.length() > 0) {
			commitTyped(ic);
		}
		if (firstMatched != null) {
			ic.commitText(this.firstMatched.getWord(), 0);
				try {
					SearchSrv.updateMapping(firstMatched.getId(), 
							firstMatched.getCode(), 
							firstMatched.getWord(), 
							firstMatched.getPcode(), 
							firstMatched.getPword(), 
							firstMatched.getScore(), 
							firstMatched.isDictionary());
				} catch (RemoteException e) {
					e.printStackTrace();
				}
		} else {
			ic.commitText(text, 0);
		}
		ic.endBatchEdit();
		//updateShiftKeyState(getCurrentInputEditorInfo());

	}

	/**
	 * Update the list of available candidates from the current composing text.
	 * This will need to be filled in by however you are determining candidates.
	 */
	private void updateCandidates() {

		if (mCandidateView != null) {
			mCandidateView.clear();
		}
		
		if (!mCompletionOn && mComposing.length() > 0) {
			
			LinkedList<Mapping> list = new LinkedList<Mapping>();
			
			try {
				list.addAll(SearchSrv.query(mComposing.toString()));
			} catch (RemoteException e) {
				e.printStackTrace();
			}
			
			if(list.size() > 0){
				setSuggestions(list, true, true);
			}else{
				setSuggestions(null, false, false);
			}
		}

	}

	/*
	 * Update dictionary view
	 */
	private void updateDictionaryView() {
		
		// If there is no Temp Matched word exist then not to display dictionary view
		try{
			if( tempMatched != null && tempMatched.getCode() != null && !tempMatched.getCode().equals("")){
				
				LinkedList<Mapping> list = new LinkedList<Mapping>();
				//Modified by Jeremy '10,3 ,12 for more specific related word
				//-----------------------------------------------------------
				if (tempMatched != null) {
					list.addAll(SearchSrv.queryUserDic(tempMatched.getCode(),
									tempMatched.getWord()));
				}
				//-----------------------------------------------------------

				if (list.size() > 0) {
					templist = (LinkedList) list;
					setSuggestions(list, true, true);
				} else {
					tempMatched = null;
					setSuggestions(null, false, false);
				}
			}
			
		}catch(Exception e){
			e.printStackTrace();
		}
		
	}
	public void setSuggestions(List<Mapping> suggestions, boolean completions,
			boolean typedWordValid) {

		if(suggestions != null && suggestions.size() > 0){
			
			setCandidatesViewShown(true);
			
			if (mCandidateView != null) {
				templist = (LinkedList) suggestions;
				try {
					if (suggestions.size() == 1) {
						firstMatched = suggestions.get(0);
					} else if (suggestions.size() > 1) {
						firstMatched = suggestions.get(1);
					} else {
						firstMatched = null;
					}
				} catch (Exception e) {
					e.printStackTrace();
				}
				mCandidateView.setSuggestions(suggestions, completions, typedWordValid);
			}
		}else{
			setCandidatesViewShown(false);
		}

	}

	private void handleBackspace() {

		final int length = mComposing.length();
		if (length > 1) {
			mComposing.delete(length - 1, length);
			getCurrentInputConnection().setComposingText(mComposing, 1);
			updateCandidates();
		} else if (length == 1) {
			if (mCandidateView != null) {
				mCandidateView.clear();
			}
			mComposing.setLength(0);
			setCandidatesViewShown(false);
			getCurrentInputConnection().commitText("", 0);
		} else {
			if (mCandidateView != null) {
				mCandidateView.clear();
			}
			mComposing.setLength(0);
			setCandidatesViewShown(false);
			keyDownUp(KeyEvent.KEYCODE_DEL);
		}
		//updateShiftKeyState(getCurrentInputEditorInfo());
		
	}


	private void updateShift(int primaryCode) {

		if (mInputView == null) { return; }

		// Check Shift Status	
		if(primaryCode != this.KEYBOARD_SWITCH_CODE || primaryCode != Keyboard.KEYCODE_MODE_CHANGE){
			if(mCapsLock != true && mHasShift){
				Keyboard current = mInputView.getKeyboard();
				if(current == mSymbolsShiftedKeyboard){
					mSymbolsKeyboard.setShifted(false);
					mInputView.setKeyboard(mSymbolsKeyboard);
				}else if(current == mCJShiftKeyboard){
					mCJKeyboard.setShifted(false);
					mInputView.setKeyboard(mCJKeyboard);
				}else if(current == mCJNumberShiftKeyboard){
					mCJNumberKeyboard.setShifted(false);
					mInputView.setKeyboard(mCJNumberKeyboard);
				}else if(current == mPhoneticShiftKeyboard){
					mPhoneticKeyboard.setShifted(false);
					mInputView.setKeyboard(mPhoneticKeyboard);
				}else if(current == mDayiShiftKeyboard){
					mDayiKeyboard.setShifted(false);
					mInputView.setKeyboard(mDayiKeyboard);
				}else if(current == mPhoneKeyboard){
					mInputView.setShifted(false);
				}else if(current == mKeyboard){
					mInputView.setShifted(false);
				}
				mHasShift = false;
				mCapsLock = false;
			}
		}
	}
	//
	// Modified '10, 3, 24 for bug fix and alc-lock implementation
	private void handleAlt(){
		if(mTrackAlt) { //alt pressed without combination with other key
			if(!mHasAlt){	// Alt-ON
				mHasAlt = true;
				mAltLocked = false;
			}else if(mHasAlt && mAltLocked){//Alt-off 
				InputConnection ic = getCurrentInputConnection();	
				ic.clearMetaKeyStates(KeyEvent.META_ALT_ON);
				mHasAlt = false;
				mAltLocked = false;
			}else if(mHasAlt && !mAltLocked){ //Alt-Locked
				mHasAlt = true;
				mAltLocked = true;
			}
		}
	}
	
	
	private void handleShift() {

		if (mInputView == null) { return; }

		// Check Shift Status
		checkToggleCapsLock();
		
		boolean currentShiftStatus = false;
		if(mCapsLock == true || mHasShift){
			currentShiftStatus = true;
		}
		
		Keyboard current = mInputView.getKeyboard();
		
		if(current == mSymbolsKeyboard){
			mSymbolsShiftedKeyboard.setShifted(true);
			mInputView.setKeyboard(mSymbolsShiftedKeyboard);
		}else if(current == mNumberKeyboard){
			mNumberShiftKeyboard.setShifted(true);
			mInputView.setKeyboard(mNumberShiftKeyboard);
		}else if(current == mNumberShiftKeyboard){
			mNumberKeyboard.setShifted(false);
			mInputView.setKeyboard(mNumberKeyboard);
		}else if(current == mSymbolsShiftedKeyboard){
			mSymbolsKeyboard.setShifted(false);
			mInputView.setKeyboard(mSymbolsKeyboard);
		}else if(current == mCJKeyboard){
			mCJShiftKeyboard.setShifted(true);
			mInputView.setKeyboard(mCJShiftKeyboard);
		}else if(current == mCJShiftKeyboard){
			mCJKeyboard.setShifted(false);
			mInputView.setKeyboard(mCJKeyboard);
		}else if(current == mCJNumberKeyboard){
			mCJNumberShiftKeyboard.setShifted(true);
			mInputView.setKeyboard(mCJNumberShiftKeyboard);
		}else if(current == mCJNumberShiftKeyboard){
			mCJNumberKeyboard.setShifted(false);
			mInputView.setKeyboard(mCJNumberKeyboard);
		}else if(current == mPhoneticKeyboard){
			mPhoneticShiftKeyboard.setShifted(true);
			mInputView.setKeyboard(mPhoneticShiftKeyboard);
		}else if(current == mPhoneticShiftKeyboard){
			mPhoneticKeyboard.setShifted(false);
			mInputView.setKeyboard(mPhoneticKeyboard);
		}else if(current == mDayiKeyboard){
			mDayiShiftKeyboard.setShifted(true);
			mInputView.setKeyboard(mDayiShiftKeyboard);
		}else if(current == mDayiShiftKeyboard){
			mDayiKeyboard.setShifted(false);
			mInputView.setKeyboard(mDayiKeyboard);
		}else if(current == mPhoneKeyboard){
			mInputView.setShifted(currentShiftStatus);
		}else if(current == mKeyboard){
			if(currentShiftStatus){
				mKeyboard.setShifted(true);
				mInputView.setKeyboard(mKeyboard);
			}else{
				mKeyboard.setShifted(false);
				mInputView.setKeyboard(mKeyboard);
			}
		}
	}

	private void switchKeyboard(int primaryCode) {

		Keyboard current = mInputView.getKeyboard();
		
		if (primaryCode == Keyboard.KEYCODE_MODE_CHANGE) {
			// Switch Keyboard between Symbol and Lime
			if(current != mSymbolsKeyboard && current != mSymbolsShiftedKeyboard) {
				current = mSymbolsKeyboard;
				onIM = false;
			} else{
				if (keyboardSelection.equals("lime")){
					if (hasNumberKeypads) {
						current = mNumberKeyboard;
					}else{
						current = mKeyboard;
					}
				} else if(keyboardSelection.equals("cj")){
					if (hasNumberKeypads) {
						current = mCJNumberKeyboard;
					}else{
						current = mCJKeyboard;
					}
				} else if(keyboardSelection.equals("phonetic")){
					current = mPhoneticKeyboard;
				} else if(keyboardSelection.equals("dayi")){
					current = mDayiKeyboard;
				} else if(keyboardSelection.equals("phone")){
					current = mPhoneKeyboard;
				}
				onIM = true;
			}
		}else if(primaryCode == KEYBOARD_SWITCH_CODE){


			if( current == mCJNumberKeyboard ||
				current == mCJNumberShiftKeyboard ||
				current == mCJKeyboard ||
				current == mCJShiftKeyboard ||
				current == mPhoneticKeyboard ||
				current == mPhoneticShiftKeyboard ||
				current == mDayiKeyboard ||
				current == mDayiShiftKeyboard ||
				current == mPhoneKeyboard){
				current = mKeyboard;
				Toast.makeText(this, R.string.typing_mode_english, Toast.LENGTH_SHORT).show();
				onIM = false;
			} else {
				if (keyboardSelection.equals("lime")){
					if (hasNumberKeypads) {
						current = mNumberKeyboard;
					}else{
						current = mKeyboard;
					}
					onIM = !onIM;
					if(onIM){
						Toast.makeText(this, R.string.typing_mode_mixed, Toast.LENGTH_SHORT).show();
					}else{
						Toast.makeText(this, R.string.typing_mode_english, Toast.LENGTH_SHORT).show();
					}
				} else if(keyboardSelection.equals("cj")){
					if (hasNumberKeypads) {
						current = mCJNumberKeyboard;
					}else{
						current = mCJKeyboard;
					}
					onIM = true;
					Toast.makeText(this, R.string.typing_mode_mixed, Toast.LENGTH_SHORT).show();
				} else if(keyboardSelection.equals("phonetic")){
					current = mPhoneticKeyboard;
					onIM = true;
					Toast.makeText(this, R.string.typing_mode_mixed, Toast.LENGTH_SHORT).show();
				} else if(keyboardSelection.equals("dayi")){
					current = mDayiKeyboard;
					onIM = true;
					Toast.makeText(this, R.string.typing_mode_mixed, Toast.LENGTH_SHORT).show();
				} else if(keyboardSelection.equals("phone")){
					current = mPhoneKeyboard;
					onIM = true;
					Toast.makeText(this, R.string.typing_mode_mixed, Toast.LENGTH_SHORT).show();
				}
			}
		}
		
		// Reset Shift Status
		mCapsLock = false;
		mHasShift = false;

		current.setShifted(false);
		mInputView.setKeyboard(current);
		
	}

	private void initialKeyboard() {

		if (mInputView == null) {
			mInputView = (KeyboardView) getLayoutInflater().inflate(R.layout.input, null);
			mInputView.setOnKeyboardActionListener(this);
		}

		if (keyboardSelection.equals("lime")) {
			if (hasNumberKeypads) {
				mInputView.setKeyboard(mNumberKeyboard);
				mCurKeyboard = mNumberKeyboard;
			} else {
				mInputView.setKeyboard(mKeyboard);
				mCurKeyboard = mKeyboard;
			}
		} else if (keyboardSelection.equals("cj")) {
			if (hasNumberKeypads) {
				mInputView.setKeyboard(mCJNumberKeyboard);
				mCurKeyboard = mCJNumberKeyboard;
			}else{
				mInputView.setKeyboard(mCJKeyboard);
				mCurKeyboard = mCJKeyboard;
			}
		} else if (keyboardSelection.equals("phonetic")) {
			mInputView.setKeyboard(mPhoneticKeyboard);
			mCurKeyboard = mPhoneticKeyboard;
		} else if (keyboardSelection.equals("dayi")) {
			mInputView.setKeyboard(mDayiKeyboard);
			mCurKeyboard = mDayiKeyboard;
		} else if (keyboardSelection.equals("phone")) {
			mInputView.setKeyboard(mPhoneKeyboard);
			mCurKeyboard = mPhoneKeyboard;
		}

	}

	/**
	 * This method construct candidate view and add key code to composing object
	 * @param primaryCode
	 * @param keyCodes
	 */
	private void handleCharacter(int primaryCode, int[] keyCodes) {
		
		// If keyboard type = phone then check the user selection
		if( keyboardSelection.equals("phone")){
			try{      
				SharedPreferences sp1 = getSharedPreferences(PREF, 0);
				String xyvalue = sp1.getString("xy", "");
				this.keyUpX = Float.parseFloat(xyvalue.split(",")[0]);
				this.keyUpY = Float.parseFloat(xyvalue.split(",")[1]);
			}catch(Exception e){
				e.printStackTrace();
			}
			
			float directionX = keyDownX - keyUpX;
			float directionY = keyDownY - keyUpY;

			int result = 0;
			// Only when keyboard type equal "Phone"
			if( (keyDownX - keyUpX) > moveLength || 
				(keyUpX - keyDownX) > moveLength ||
				(keyDownY - keyUpY) > moveLength || 
				(keyUpY - keyDownY) > moveLength ){
				
				if(keyDownCode == 40){
					result = handleSelection(directionX, directionY, new String[]{"[",")","(","]"});
				}else if(keyDownCode == 44){
					result = handleSelection(directionX, directionY, new String[]{"?",".",";","\\"});
				}else if(keyDownCode == 48){
					result = handleSelection(directionX, directionY, new String[]{"^","}","~","{"});
				}else if(keyDownCode == 49){
					result = handleSelection(directionX, directionY, new String[]{"!","1","@","#"});
				}else if(keyDownCode == 50){
					if(mHasShift){
						result = handleSelection(directionX, directionY, new String[]{"B","2","A","C"});
					}else{
						result = handleSelection(directionX, directionY, new String[]{"b","2","a","c"});
					}
				}else if(keyDownCode == 51){
					if(mHasShift){
						result = handleSelection(directionX, directionY, new String[]{"E","3","D","F"});
					}else{
						result = handleSelection(directionX, directionY, new String[]{"e","3","d","f"});
					}
				}else if(keyDownCode == 52){
					if(mHasShift){
						result = handleSelection(directionX, directionY, new String[]{"H","4","G","I"});
					}else{
						result = handleSelection(directionX, directionY, new String[]{"h","4","g","i"});
					}
				}else if(keyDownCode == 53){
					if(mHasShift){
						result = handleSelection(directionX, directionY, new String[]{"K","5","J","L"});
					}else{
						result = handleSelection(directionX, directionY, new String[]{"k","5","j","l"});
					}
				}else if(keyDownCode == 54){
					if(mHasShift){
						result = handleSelection(directionX, directionY, new String[]{"N","6","M","O"});
					}else{
						result = handleSelection(directionX, directionY, new String[]{"n","6","m","o"});
					}
				}else if(keyDownCode == 55){
					if(mHasShift){
						result = handleSelection(directionX, directionY, new String[]{"Q","S","P","R"});
					}else{
						result = handleSelection(directionX, directionY, new String[]{"q","s","p","r"});
					}
				}else if(keyDownCode == 56){
					if(mHasShift){
						result = handleSelection(directionX, directionY, new String[]{"U","8","T","V"});
					}else{
						result = handleSelection(directionX, directionY, new String[]{"u","8","t","v"});
					}
				}else if(keyDownCode == 57){
					if(mHasShift){
						result = handleSelection(directionX, directionY, new String[]{"X","Z","W","Y"});
					}else{
						result = handleSelection(directionX, directionY, new String[]{"x","z","w","y"});
					}
				}else if(keyDownCode == 61){
					result = handleSelection(directionX, directionY, new String[]{"-","/","+","*"});
				}
				primaryCode = result;
			}
			

			if (!mEnglishOnly) {
				if (isInputViewShown()) {
					if (mInputView.isShifted()) {
						primaryCode = Character.toUpperCase(primaryCode);
					}
				}
	
				if (!hasSymbolMapping && !hasNumberMapping
						&& isValidLetter(primaryCode) && onIM) {
					mComposing.append((char) primaryCode);
					getCurrentInputConnection().setComposingText(mComposing, 1);
					//updateShiftKeyState(getCurrentInputEditorInfo());
					updateCandidates();
					misMatched = mComposing.toString();
				} else if (!hasSymbolMapping
						&& hasNumberMapping
						&& (isValidLetter(primaryCode) || isValidDigit(primaryCode))
						&& onIM) {
					mComposing.append((char) primaryCode);
					getCurrentInputConnection().setComposingText(mComposing, 1);
					//updateShiftKeyState(getCurrentInputEditorInfo());
					updateCandidates();
					misMatched = mComposing.toString();
				} else if (hasSymbolMapping
						&& !hasNumberMapping
						&& (isValidLetter(primaryCode) || isValidSymbol(primaryCode))
						&& onIM) {
					mComposing.append((char) primaryCode);
					getCurrentInputConnection().setComposingText(mComposing, 1);
					//updateShiftKeyState(getCurrentInputEditorInfo());
					updateCandidates();
					misMatched = mComposing.toString();
				} else if (hasSymbolMapping
						&& hasNumberMapping
						&& (isValidSymbol(primaryCode)
								|| isValidLetter(primaryCode) || isValidDigit(primaryCode))
						&& onIM) {
					mComposing.append((char) primaryCode);
					getCurrentInputConnection().setComposingText(mComposing, 1);
					//updateShiftKeyState(getCurrentInputEditorInfo());
					updateCandidates();
					misMatched = mComposing.toString();
				} else {
					getCurrentInputConnection().commitText(
							mComposing + String.valueOf((char) primaryCode), 1);
				}
			} else {
				getCurrentInputConnection().commitText(
						String.valueOf((char) primaryCode), 1);
			}
			
		}else{
			if (!mEnglishOnly) {
				if (isInputViewShown()) {
					if (mInputView.isShifted()) {
						primaryCode = Character.toUpperCase(primaryCode);
					}
				}
				//------------------------------------------------------------------------
				// Add by Jeremy '10, 3,12
				// Process Alt combination key specific for moto milestone
				// '10, 3, 24 alt-lock implementation (not clear if alt-locked)
				InputConnection ic = getCurrentInputConnection();	
				
				if( (primaryCode == 'Q' || primaryCode =='q')&& mHasAlt){
					primaryCode = '1';
				}else if((primaryCode == 'W' || primaryCode =='w')&& mHasAlt){
					primaryCode = '2';
				}else if((primaryCode == 'E' || primaryCode =='e')&& mHasAlt){
					primaryCode = '3';
				}else if((primaryCode == 'R' || primaryCode =='r')&& mHasAlt){
					primaryCode = '4';
				}else if((primaryCode == 'T' || primaryCode =='t')&& mHasAlt){
					primaryCode = '5';
				}else if((primaryCode == 'Y' || primaryCode =='y')&& mHasAlt){
					primaryCode = '6';
				}else if((primaryCode == 'U' || primaryCode =='u')&& mHasAlt){
					primaryCode = '7';
				}else if((primaryCode == 'I' || primaryCode =='i')&& mHasAlt){
					primaryCode = '8';
				}else if((primaryCode == 'O' || primaryCode =='o')&& mHasAlt){
					primaryCode = '9';
				}else if((primaryCode == 'P' || primaryCode =='p')&& mHasAlt){
					primaryCode = '0';
				}else if((primaryCode == 'A' || primaryCode =='a')&& mHasAlt){
					primaryCode = 9; //TAB
				}else if((primaryCode == 'S' || primaryCode =='s')&& mHasAlt){
					primaryCode = '!';
				}else if((primaryCode == 'D' || primaryCode =='d')&& mHasAlt){
					primaryCode = '#';
				}else if((primaryCode == 'F' || primaryCode =='f')&& mHasAlt){
					primaryCode = '$';
				}else if((primaryCode == 'G' || primaryCode =='g')&& mHasAlt){
					primaryCode = '%';
				}else if((primaryCode == 'H' || primaryCode =='h')&& mHasAlt){
					primaryCode = '=';
				}else if((primaryCode == 'J' || primaryCode =='j')&& mHasAlt){
					primaryCode = '&';
				}else if((primaryCode == 'K' || primaryCode =='k')&& mHasAlt){
					primaryCode = '*';
				}else if((primaryCode == 'L' || primaryCode =='l')&& mHasAlt){
					primaryCode = '(';
				}else if((primaryCode == '?' )&& mHasAlt){
					primaryCode = ')';
				}else if((primaryCode == 'Z' || primaryCode =='z')&& mHasAlt){
					primaryCode = '<';
				}else if((primaryCode == 'X' || primaryCode =='x')&& mHasAlt){
					primaryCode = '>';
				}else if((primaryCode == 'C' || primaryCode =='c')&& mHasAlt){
					primaryCode = '_';
				}else if((primaryCode == 'V' || primaryCode =='v')&& mHasAlt){
					primaryCode = '-';
				}else if((primaryCode == 'B' || primaryCode =='b')&& mHasAlt){
					primaryCode = '+';
				}else if((primaryCode == 'N' || primaryCode =='n')&& mHasAlt){
					primaryCode = '"';
				}else if((primaryCode == 'M' || primaryCode =='m')&& mHasAlt){
					primaryCode = '\'';
				}else if( primaryCode ==','&& mHasAlt){
					primaryCode = ';';
				}else if(primaryCode =='.'&& mHasAlt){
					primaryCode = ':';
				}else if(primaryCode == '@' && mHasAlt){
					primaryCode = '~';
				}else if(primaryCode == '/' && mHasAlt){
					primaryCode = '^';
				}
				//------------------------------------------------------------------------
				
	
				if (!hasSymbolMapping && !hasNumberMapping
						&& isValidLetter(primaryCode) && onIM) {
					mComposing.append((char) primaryCode);
					getCurrentInputConnection().setComposingText(mComposing, 1);
					//updateShiftKeyState(getCurrentInputEditorInfo());
					updateCandidates();
					misMatched = mComposing.toString();
				} else if (!hasSymbolMapping
						&& hasNumberMapping
						&& (isValidLetter(primaryCode) || isValidDigit(primaryCode))
						&& onIM) {
					mComposing.append((char) primaryCode);
					getCurrentInputConnection().setComposingText(mComposing, 1);
					//updateShiftKeyState(getCurrentInputEditorInfo());
					updateCandidates();
					misMatched = mComposing.toString();
				} else if (hasSymbolMapping
						&& !hasNumberMapping
						&& (isValidLetter(primaryCode) || isValidSymbol(primaryCode))
						&& onIM) {
					mComposing.append((char) primaryCode);
					getCurrentInputConnection().setComposingText(mComposing, 1);
					//updateShiftKeyState(getCurrentInputEditorInfo());
					updateCandidates();
					misMatched = mComposing.toString();
				} else if (hasSymbolMapping
						&& hasNumberMapping
						&& (isValidSymbol(primaryCode)
								|| isValidLetter(primaryCode) || isValidDigit(primaryCode))
						&& onIM) {
					mComposing.append((char) primaryCode);
					getCurrentInputConnection().setComposingText(mComposing, 1);
					//updateShiftKeyState(getCurrentInputEditorInfo());
					updateCandidates();
					misMatched = mComposing.toString();
				} else {
					getCurrentInputConnection().commitText(
							mComposing + String.valueOf((char) primaryCode), 1);
				}
			} else {
				getCurrentInputConnection().commitText(
						String.valueOf((char) primaryCode), 1);
			}
		}
		updateShift(primaryCode);
	}

	private void handleClose() {
		requestHideSelf(0);
		mInputView.closing();
	}

	private void checkToggleCapsLock() {

		long now = System.currentTimeMillis();

		if (mLastShiftTime + 800 > now) {
			mCapsLock = !mCapsLock;
			mLastShiftTime = 0;
		} else {
			mLastShiftTime = now;
		}
		mHasShift = !mHasShift;

	}

	public boolean isWordSeparator(int code) {
		// String checkCode = String.valueOf((char)code);
		//if (code == 32 || code == 39 || code == 10) {
		if (code == 32 || code == 10) {
			return true;
		} else {
			return false;
		}
	}

	public void pickDefaultCandidate() {
		pickSuggestionManually(0);
	}

	public void pickSuggestionManually(int index) {

		if (templist != null) {
			firstMatched = templist.get(index);
		}
		if (mCompletionOn && mCompletions != null && index >= 0
				&& index < mCompletions.length) {
			CompletionInfo ci = mCompletions[index];
			getCurrentInputConnection().commitCompletion(ci);
			//updateShiftKeyState(getCurrentInputEditorInfo());
		} else if (mComposing.length() > 0) {
			commitTyped(getCurrentInputConnection());
			this.firstMatched = null;
			this.hasFirstMatched = false;
			updateDictionaryView();
		} else if (firstMatched != null && firstMatched.isDictionary()) {
			commitTyped(getCurrentInputConnection());
			updateDictionaryView();
		}
		
	}

	public void swipeRight() {
		if (mCompletionOn) {
			pickDefaultCandidate();
		}
	}

	public void swipeLeft() {
		if( !keyboardSelection.equals("phone")){
			handleBackspace();
		}
	}

	public void swipeDown() {
		handleClose();
	}

	public void swipeUp() {
	}

	/**
	 * First method to call after key press
	 */
	public void onPress(int primaryCode) {
		
		if (hasVibration) {
			mVibrator.vibrate(40);
		}
		if (hasSound) {
			int sound = AudioManager.FX_KEYPRESS_STANDARD;
			switch (primaryCode) {
			case Keyboard.KEYCODE_DELETE:
				sound = AudioManager.FX_KEYPRESS_DELETE;
				break;
			case KEYCODE_ENTER:
				sound = AudioManager.FX_KEYPRESS_RETURN;
				break;
			case KEYCODE_SPACE:
				sound = AudioManager.FX_KEYPRESS_SPACEBAR;
				break;
			}
			mAudioManager.playSoundEffect(sound, FX_VOLUME);
		}

		try{
			if( keyboardSelection.equals("phone")){
				keyDownCode = primaryCode;    

				SharedPreferences sp1 = getSharedPreferences(PREF, 0);
				String xyvalue = sp1.getString("xy", "");
				this.keyDownX = Float.parseFloat(xyvalue.split(",")[0]);
				this.keyDownY = Float.parseFloat(xyvalue.split(",")[1]);
			}
			
			hasKeyPress = true;
		}catch(Exception e){
			e.printStackTrace();
		}
	}

	/**
	 * Last method to execute when key release
	 */
	public void onRelease(int primaryCode) {
		// TODO Auto-generated method stub
	}

	private final int UP = 0;
	private final int DOWN = 1;
	private final int LEFT = 2;
	private final int RIGHT = 3;
	
	public int handleSelection(float x, float y, String keys[]) {

		int result = 0;
		int direction;
		if(Math.abs(x) > Math.abs(y)){
			// move horizontal
			if(x > 0){
				direction = this.LEFT;
			}else{
				direction = this.RIGHT;
			}
		}else{
			// move verticle
			if(y >0){
				direction = this.UP;
			}else{
				direction = this.DOWN;
			}
		}
		
		// Select Character to be import
		result = (int)keys[direction].hashCode();
		
		return result;
	}
	
	public boolean isValidTime(Date target){
		Calendar srcCal = Calendar.getInstance();
		srcCal.setTime(new Date());
		Calendar destCal = Calendar.getInstance();
		destCal.setTime(target);
		
		if(srcCal.getTimeInMillis() - destCal.getTimeInMillis() < 1800000){
			return true;
		}else{
			return false;
		}
		
	}
	
	
	

}
