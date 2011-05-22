/*    
 **    Copyright 2010, The LimeIME Open Source Project
 ** 
 **    Project Url: http://code.google.com/p/limeime/
 **                 http://android.toload.net/
 **
 **    This program is free software: you can redistribute it and/or modifyf
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

import android.inputmethodservice.InputMethodService;
import android.inputmethodservice.Keyboard;
import android.inputmethodservice.KeyboardView;
import android.media.AudioManager;
import android.os.Debug;
import android.os.Handler;
import android.os.IBinder;
import android.os.RemoteException;
import android.os.Vibrator;
import android.preference.PreferenceManager; // MetaKeyKeyLister is buggy on locked metakey state
//import android.text.method.MetaKeyKeyListener;
import android.text.InputType;
import android.text.AutoText;
import android.text.TextUtils;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.KeyCharacterMap;
import android.view.KeyEvent;
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
import android.content.res.Configuration;

/**
 * @author Art Hung
 */
public class LIMEService extends InputMethodService implements KeyboardView.OnKeyboardActionListener {

	static final boolean DEBUG = false;
	static final String PREF = "LIMEXY";

	static final int KEYBOARD_SWITCH_CODE = -9;

	private LIMEKeyboardView mInputView = null;
	private CandidateView mCandidateView = null;
	private CompletionInfo[] mCompletions;

	private StringBuilder mComposing = new StringBuilder();

	private boolean isModeURL = false;
	private boolean isModePassword = false;
	private boolean mPredictionOn;
	private boolean mCompletionOn;
	private boolean mCapsLock;
	private boolean mAutoCap;
	private boolean mQuickFixes;
	private boolean mHasShift;

	private boolean mEnglishOnly;
	private boolean mEnglishFlagShift;
	private boolean mEnglishIMStart;

	private boolean onIM = true;
	private boolean hasFirstMatched = false;

	// if getMapping result has record then set to 'true'
	private boolean hasMappingList = false;

	private boolean keydown = false;

	private long mMetaState;
	private boolean mJustAccepted;
	private CharSequence mJustRevertedSeparator;
	private int mImeOptions;

	private int mLastDisplayWidth;

	LIMEKeyboardSwitcher mKeyboardSwitcher;

	private UserDictionary mUserDictionary;
	private ContactsDictionary mContactsDictionary;
	private ExpandableDictionary mAutoDictionary;

	private boolean mAutoSpace;
	private boolean mAutoCorrectOn;
	private boolean mShowSuggestions;
	private int mCorrectionMode;
	private int mOrientation;
	private boolean mPredicting;
	private String mLocale;
	private int mDeleteCount;

	private Suggest mSuggest;

	private String mSentenceSeparators;

	private Mapping firstMatched;
	private Mapping tempMatched;
	
	private StringBuffer tempEnglishWord;
	private List<Mapping> tempEnglishList;
	
	private boolean isPressPhysicalKeyboard;

	private String mWordSeparators;
	private String misMatched;
	private LimeDB limedb;

	private LinkedList<Mapping> templist;
	private LinkedList<Mapping> userdiclist;

	private Vibrator mVibrator;
	private AudioManager mAudioManager;

	private final float FX_VOLUME = 1.0f;
	static final int KEYCODE_ENTER = 10;
	static final int KEYCODE_SPACE = ' ';

	private boolean hasVibration = false;
	private boolean hasSound = false;
	//private boolean hasNumberKeypads = false;
	private boolean hasNumberMapping = false;
	private boolean hasSymbolMapping = false;
	private boolean hasKeyPress = false;
	private boolean hasQuickSwitch = false;

	// Hard Keyboad Shift + Space Status
	private boolean hasShiftPress = false;
	private boolean hasCtrlPress = false; // Jeremy '11,5,13

	//private boolean hasSpacePress = false;

	// Hard Keyboad Shift + Space Status
	private boolean hasAltPress = false;

	private String keyboardSelection;
	private List<String> keyboardList;
	private List<String> keyboardListCodes;

	private int keyDownCode = 0;
	private float keyDownX = 0;
	private float keyDownY = 0;
	private float keyUpX = 0;
	private float keyUpY = 0;

	// To keep key press time
	private long keyPressTime = 0;

	// Keep keydown event
	KeyEvent mKeydownEvent = null;

	private int previousKeyCode = 0;
	private final float moveLength = 15;
	private ISearchService SearchSrv = null;

	static final int FREQUENCY_FOR_AUTO_ADD = 250;

	// Weight added to a user picking a new word from the suggestion strip
	static final int FREQUENCY_FOR_PICKED = 3;
	
	// Replace Keycode.KEYCODE_CTRL_LEFT/RIGHT on android 3.x 
	// for backward compatibility of 2.x
	private final int MY_KEYCODE_CTRL_LEFT =113;
	private final int MY_KEYCODE_CTRL_RIGHT =114;
	
	private LIMEPreferenceManager mLIMEPref;

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

		public void onServiceDisconnected(ComponentName name) {
		}
	};

	/**
	 * Main initialization of the input method component. Be sure to call to
	 * super class.
	 */
	@Override
	public void onCreate() {

		super.onCreate();

		mEnglishOnly = false;
		mEnglishFlagShift = false;

		// Startup Service
		if (SearchSrv == null) {
			this.bindService(new Intent(ISearchService.class.getName()), serConn, Context.BIND_AUTO_CREATE);
		}

		mVibrator = (Vibrator) getApplication().getSystemService(
				Service.VIBRATOR_SERVICE);
		mAudioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);

		SharedPreferences sp = PreferenceManager
				.getDefaultSharedPreferences(this);
		hasVibration = sp.getBoolean("vibrate_on_keypress", false);
		hasSound = sp.getBoolean("sound_on_keypress", false);
		mEnglishIMStart = sp.getBoolean("default_in_english", false);
		//hasNumberKeypads = sp.getBoolean("display_number_keypads", false);
		keyboardSelection = sp.getString("keyboard_list", "custom");

		// initial Input List
		userdiclist = new LinkedList<Mapping>();

		// initial keyboard list
		keyboardList = new ArrayList<String>();
		keyboardListCodes = new ArrayList<String>();
		buildActiveKeyboardList();
		
		// Construct Preference Access Tool
		mLIMEPref = new LIMEPreferenceManager(this);

	}

	private void initSuggest(String locale) {
		mLocale = locale;
		// mSuggest = new Suggest(this, R.raw.main);
		mSuggest.setCorrectionMode(mCorrectionMode);
		mUserDictionary = new UserDictionary(this);
		mContactsDictionary = new ContactsDictionary(this);
		mAutoDictionary = new AutoDictionary(this);
		mSuggest.setUserDictionary(mUserDictionary);
		mSuggest.setContactsDictionary(mContactsDictionary);
		mSuggest.setAutoDictionary(mAutoDictionary);
		mWordSeparators = getResources().getString(R.string.word_separators);
		mSentenceSeparators = getResources().getString(
				R.string.sentence_separators);
	}

	/**
	 * This is the point where you can do all of your UI initialization. It is
	 * called after creation and any configuration change.
	 */
	@Override
	public void onInitializeInterface() {

		mEnglishOnly = false;
		mEnglishFlagShift = false;

		initialViewAndSwitcher();
		
		mKeyboardSwitcher.makeKeyboards(true);
		super.onInitializeInterface();

	}
	
	@Override
	public void onConfigurationChanged(Configuration conf) {
		
		if (DEBUG)
			Log.i("LIMEService:", "OnConfigurationChanged()");

		if (!TextUtils.equals(conf.locale.toString(), mLocale)) {
			// initSuggest(conf.locale.toString());
		}
		// If orientation changed while predicting, commit the change
		if (conf.orientation != mOrientation) {
			commitTyped(getCurrentInputConnection());
			mOrientation = conf.orientation;
		}
		initialViewAndSwitcher();
		mKeyboardSwitcher.makeKeyboards(true);
		super.onConfigurationChanged(conf);

	}

	/**
	 * Called by the framework when your view for creating input needs to be
	 * generated. This will be called the first time your input method is
	 * displayed, and every time it needs to be re-created such as due to a
	 * configuration change.
	 */
	@Override
	public View onCreateInputView() {
		
		mInputView = (LIMEKeyboardView) getLayoutInflater().inflate( R.layout.input, null);
		mKeyboardSwitcher.setInputView(mInputView);
		mKeyboardSwitcher.makeKeyboards(true);
		mInputView.setOnKeyboardActionListener(this);
		 
		mKeyboardSwitcher.setKeyboardMode(keyboardSelection, 0, EditorInfo.IME_ACTION_NEXT, true, false, false);
		
		//mKeyboardSwitcher.setKeyboardMode( LIMEKeyboardSwitcher.MODE_TEXT_DEFAULT, 0);

		initialKeyboard();

		onIM = true;
		return mInputView;
	}

	@Override
	public View onCreateCandidatesView() {

		mKeyboardSwitcher.makeKeyboards(true);
		mCandidateView = new CandidateView(this);
		mCandidateView.setService(this);

		return mCandidateView;
	}
	
	
	// Jeremy '11,5,14
	//Override fullscreen editing mode settings for larger screen (width > 480).
	
	
	 @Override
	    public boolean onEvaluateFullscreenMode(){
		 if((getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE) 
			 &&!(this.getMaxWidth()>480))
			 return true; 
		 else
			 return false;
	    }

	
	/**
	 * This is called when the user is done editing a field. We can use this to
	 * reset our state.
	 */
	@Override
	public void onFinishInput() {
		if(DEBUG) { Log.i("LimeService","onFinishInput()");}
		super.onFinishInput();

		// initialKeyboard();

		if (mInputView != null) {
			mInputView.closing();
		}

		// Clear current composing text and candidates.
		mComposing.setLength(0);
		updateCandidates();

		setCandidatesViewShown(false);

		// Log.i("ART","onIM2");
		// onIM = false;

		// Add Custom related words
		if (userdiclist.size() > 0) {
			// Log.i("ART","Process userdict update ");
			updateUserDict();
		}

		this.setSuggestions(null, false, false);

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
	    initOnStartInput(attribute, restarting);
	}
	
	@Override
	public void onStartInputView(EditorInfo attribute, boolean restarting) {
		super.onStartInputView(attribute, restarting);
		if (DEBUG)
			Log.i("LIMEService", "onStartInputView");
		if (mInputView == null) {
			return;
		}
		initOnStartInput(attribute, restarting);

	   
		
	}
	private void initOnStartInput(EditorInfo attribute, boolean restarting){
		super.onStartInputView(attribute, restarting);
		if (DEBUG)
			Log.i("LIMEService", "onStartInputView");
		if (mInputView == null) {
			return;
		}

		mKeyboardSwitcher.makeKeyboards(false);

		TextEntryState.newSession(this);
		loadSettings();
		//mImeOptions = attribute.imeOptions;
		mImeOptions = attribute.imeOptions;
		
		initialKeyboard();
		boolean disableAutoCorrect = false;
		mPredictionOn = false;
		mCompletionOn = false;
		mCompletions = null;
		mCapsLock = false;
		mHasShift = false;
		mEnglishOnly = false;
		isModeURL = false;
		isModePassword = false;

		tempEnglishWord = new StringBuffer();
		tempEnglishList = new LinkedList<Mapping>();
		
		onIM = true;
		
		switch (attribute.inputType & EditorInfo.TYPE_MASK_CLASS) {
		case EditorInfo.TYPE_CLASS_NUMBER:
		case EditorInfo.TYPE_CLASS_DATETIME:
			mEnglishOnly = true;
			onIM = false;
			mKeyboardSwitcher.setKeyboardMode(keyboardSelection, 0, mImeOptions, false, false, false);
			break;
		case EditorInfo.TYPE_CLASS_PHONE:
			mEnglishOnly = true;
			onIM = false;
			mKeyboardSwitcher.setKeyboardMode(keyboardSelection, mKeyboardSwitcher.MODE_PHONE, mImeOptions, false, false, false);
			break;
		case EditorInfo.TYPE_CLASS_TEXT:

				// Make sure that passwords are not displayed in candidate view
				int variation = attribute.inputType
						& EditorInfo.TYPE_MASK_VARIATION;
				if (variation == EditorInfo.TYPE_TEXT_VARIATION_PASSWORD
						|| variation == EditorInfo.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD) {
					mPredictionOn = false;
					isModePassword = true;
				}
				if (variation == EditorInfo.TYPE_TEXT_VARIATION_EMAIL_ADDRESS
						|| variation == EditorInfo.TYPE_TEXT_VARIATION_PERSON_NAME) {
					mAutoSpace = false;
				} else {
					mAutoSpace = true;
				}
				if (variation == EditorInfo.TYPE_TEXT_VARIATION_EMAIL_ADDRESS) {
					mEnglishOnly = true;
					onIM = false;
					mPredictionOn = false;
					mKeyboardSwitcher.setKeyboardMode(keyboardSelection, mKeyboardSwitcher.MODE_EMAIL, mImeOptions, false, false, false);
				} else if (variation == EditorInfo.TYPE_TEXT_VARIATION_URI) {
					mPredictionOn = false;
					mEnglishOnly = true;
					onIM = false;
					isModeURL = true;
					mKeyboardSwitcher.setKeyboardMode(keyboardSelection, mKeyboardSwitcher.MODE_URL, mImeOptions, false, false, false);
				} else if (variation == EditorInfo.TYPE_TEXT_VARIATION_SHORT_MESSAGE) {
					mKeyboardSwitcher.setKeyboardMode(keyboardSelection, 0, mImeOptions, true, false, false);
				} else if (variation == EditorInfo.TYPE_TEXT_VARIATION_FILTER) {
					mPredictionOn = false;
				} else if (variation == EditorInfo.TYPE_TEXT_VARIATION_WEB_EDIT_TEXT) {
					if ((attribute.inputType & EditorInfo.TYPE_TEXT_FLAG_AUTO_CORRECT) == 0) {
						disableAutoCorrect = true;
					}
						if((attribute.inputType & EditorInfo.TYPE_TEXT_FLAG_MULTI_LINE) != 0 ||
								(attribute.inputType & EditorInfo.TYPE_CLASS_TEXT) == 0) {
							mKeyboardSwitcher.setKeyboardMode(keyboardSelection, 0, EditorInfo.IME_ACTION_NONE, true, false, false);
						}else{
							mKeyboardSwitcher.setKeyboardMode(keyboardSelection, 0, EditorInfo.IME_ACTION_NEXT, true, false, false);
						}
				}
	
				// If NO_SUGGESTIONS is set, don't do prediction.
				if ((attribute.inputType & EditorInfo.TYPE_TEXT_FLAG_NO_SUGGESTIONS) != 0) {
					mPredictionOn = false;
					disableAutoCorrect = true;
				}
				// If it's not multiline and the autoCorrect flag is not set, then
				// don't correct
				if ((attribute.inputType & EditorInfo.TYPE_TEXT_FLAG_AUTO_CORRECT) == 0
						&& (attribute.inputType & EditorInfo.TYPE_TEXT_FLAG_MULTI_LINE) == 0) {
					disableAutoCorrect = true;
				}
				if ((attribute.inputType & EditorInfo.TYPE_TEXT_FLAG_AUTO_COMPLETE) != 0) {
					mPredictionOn = false;
					// mCompletionOn = true && isFullscreenMode();
				}
				
				// updateShiftKeyState(attribute);
			break;
		default:
			// mKeyboardSwitcher.setKeyboardMode(mKeyboardSwitcher.MODE_TEXT,
			// attribute.imeOptions);
			updateShiftKeyState(attribute);
		}
		
		mInputView.closing();
		mComposing.setLength(0);
		mPredicting = false;
		mDeleteCount = 0;

		/*
		 * // Override auto correct if (disableAutoCorrect) { mAutoCorrectOn =
		 * false; if (mCorrectionMode == Suggest.CORRECTION_FULL) {
		 * mCorrectionMode = Suggest.CORRECTION_BASIC; } }
		 * mInputView.setProximityCorrectionEnabled(true); if (mSuggest != null)
		 * { mSuggest.setCorrectionMode(mCorrectionMode); } mPredictionOn =
		 * mPredictionOn && mCorrectionMode > 0;
		 */
		updateShiftKeyState(getCurrentInputEditorInfo());
		setCandidatesViewShown(false);

		if(mEnglishIMStart && !isModeURL){
			switchChiEngNoToast();
		}
		 	
		// Log.i("ART","onStartInputView:"+onIM);

	}

	private void loadSettings() {
		// Get the settings preferences
		SharedPreferences sp = PreferenceManager
				.getDefaultSharedPreferences(this);
		hasVibration = sp.getBoolean("vibrate_on_keypress", false);
		hasSound = sp.getBoolean("sound_on_keypress", false);
		mEnglishIMStart = sp.getBoolean("default_in_english", false);
		//hasNumberKeypads = sp.getBoolean("display_number_keypads", false);
		keyboardSelection = sp.getString("keyboard_list", "custom");
		hasQuickSwitch = sp.getBoolean("switch_english_mode", false);
		mAutoCap = true; // sp.getBoolean(PREF_AUTO_CAP, true);
		mQuickFixes = true;// sp.getBoolean(PREF_QUICK_FIXES, true);
		// If there is no auto text data, then quickfix is forced to "on", so
		// that the other options
		// will continue to work
		if (AutoText.getSize(mInputView) < 1)
			mQuickFixes = true;
		mShowSuggestions = true & mQuickFixes;// sp.getBoolean(PREF_SHOW_SUGGESTIONS,
		// true) & mQuickFixes;
		boolean autoComplete = true;// sp.getBoolean(PREF_AUTO_COMPLETE,
		// getResources().getBoolean(R.bool.enable_autocorrect)) &
		// mShowSuggestions;
		mAutoCorrectOn = mSuggest != null && (autoComplete || mQuickFixes);
		mCorrectionMode = autoComplete ? Suggest.CORRECTION_FULL
				: (mQuickFixes ? Suggest.CORRECTION_BASIC
						: Suggest.CORRECTION_NONE);
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
		if (mComposing.length() > 0
				&& (newSelStart != candidatesEnd || newSelEnd != candidatesEnd)) {
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
		if (DEBUG)
			Log.i("LIMEService:", "onDisplayCompletions()");
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
						stringList.addAll(SearchSrv.query(ci.getText()
								.toString(), hasKeyPress));
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
		// move to HandleCharacter '10, 3,26
		// mMetaState = LIMEMetaKeyKeyListener.handleKeyDown(mMetaState,
		// keyCode, event);
		// mMetaState =
		// LIMEMetaKeyKeyListener.adjustMetaAfterKeypress(mMetaState);

		int c = event.getUnicodeChar(LIMEMetaKeyKeyListener
				.getMetaState(mMetaState));

		InputConnection ic = getCurrentInputConnection();

		if (c == 0 || ic == null) {
			return false;
		}

		// Compact code by Jeremy '10, 3, 27
		if (keyCode == 59) { // Translate shift as -1
			c = -1;
		}
		if (c != -1 && (c & KeyCharacterMap.COMBINING_ACCENT) != 0) {
			c = c & KeyCharacterMap.COMBINING_ACCENT_MASK;
		}
		onKey(c, null);
		return true;
	}

	private boolean waitingEnterUp = false;

	/**
	 * Physical KeyBoard Event Handler Use this to monitor key events being
	 * delivered to the application. We get first crack at them, and can either
	 * resume them or let them continue to the app.
	 */
	@Override
	public boolean onKeyDown(int keyCode, KeyEvent event) {

		if (DEBUG) {
			Log.i("OnKeyDown", "keyCode:"+ keyCode
					+ ";hasCtrlPress:"
					+ hasCtrlPress
					);}
		hasKeyPress = false;
		
		// For system to identify the source of character (Software KB/ Physical KB)
		isPressPhysicalKeyboard = true;

		mKeydownEvent = new KeyEvent(event);
		// Record key press time (key down, for physical keys)
		if (!keydown) {
			keyPressTime = System.currentTimeMillis();
			keydown = true;
		}

		waitingEnterUp = false;
		/*
		 * // Check if user press ALT+@ keys combination then display keyboard
		 * picker window try{ if(keyCode == KeyEvent.KEYCODE_ALT_LEFT || keyCode
		 * == KeyEvent.KEYCODE_ALT_RIGHT){ hasAltPress = true; }else if(keyCode
		 * == KeyEvent.KEYCODE_AT){ if(hasAltPress){ this.showKeyboardPicker();
		 * hasAltPress = false; }else{ hasAltPress = false; } }else if(keyCode
		 * != KeyEvent.KEYCODE_AT){ hasAltPress = false; }
		 * 
		 * 
		 * // Ignore error }catch(Exception e){}
		 */
		switch (keyCode) {

		// Add by Jeremy '10, 3, 29. DPAD selection on candidate view
		// UP/Down to page up/down ??
		// case KeyEvent.KEYCODE_DPAD_UP:
		// case KeyEvent.KEYCODE_DPAD_DOWN:
		case KeyEvent.KEYCODE_DPAD_RIGHT:
			// Log.i("ART","select:"+1);
			if (mCandidateView != null && mCandidateView.isShown()) {
				mCandidateView.selectNext();
				return true;
			}
			break;
		case KeyEvent.KEYCODE_DPAD_LEFT:
			// Log.i("ART","select:"+2);
			if (mCandidateView != null && mCandidateView.isShown()) {
				mCandidateView.selectPrev();
				return true;
			}
			break;
		case KeyEvent.KEYCODE_DPAD_CENTER:
			// Log.i("ART","select:"+3);
			if (mCandidateView != null && mCandidateView.isShown()) {
				mCandidateView.takeSelectedSuggestion();
				return true;
			}
			break;
			// Add by Jeremy '10,3,26, process metakey with
			// LIMEMetaKeyKeyListner
		case KeyEvent.KEYCODE_SHIFT_LEFT:
		case KeyEvent.KEYCODE_SHIFT_RIGHT:
			hasShiftPress=true;
			mMetaState = LIMEMetaKeyKeyListener.handleKeyDown(mMetaState, keyCode, event);
			break;
		case KeyEvent.KEYCODE_ALT_LEFT:
		case KeyEvent.KEYCODE_ALT_RIGHT:
			// Log.i("ART","select:"+4);
			mMetaState = LIMEMetaKeyKeyListener.handleKeyDown(mMetaState, keyCode, event);
			break;
		case MY_KEYCODE_CTRL_LEFT:
		case MY_KEYCODE_CTRL_RIGHT:
			hasCtrlPress =true;
			break;
		case KeyEvent.KEYCODE_BACK:
			// Log.i("ART","select:"+5);
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

			// Log.i("ART","select:"+6);

			// ------------------------------------------------------------------------
			/*if(mLIMEPref.getEnglishEnable()){
				if(tempEnglishWord != null && tempEnglishWord.length() > 0){
					tempEnglishWord.deleteCharAt(tempEnglishWord.length()-1);
					updateEnglishDictionaryView();
				}
		 	}*/
			
			if(mLIMEPref.getEnglishEnable()){
				if (mComposing.length() > 0 || tempEnglishWord.length() > 0) {
					onKey(Keyboard.KEYCODE_DELETE, null);
					return true;
				}
			}else{
				if (mComposing.length() > 0) {
					onKey(Keyboard.KEYCODE_DELETE, null);
					return true;
				}
			}
			
			if (mCandidateView != null) {
				mCandidateView.clear();
				// mCandidateView.hideComposing();
			}
			mComposing.setLength(0);
			setCandidatesViewShown(false);

			// ------------------------------------------------------------------------
			// Remove '10, 3, 26. Replaced with LIMEMetaKeyKeyLister
			// Modified by Jeremy '10, 3,12
			// block milestone alt-del to delete whole line
			// clear alt state before processed by super
			// InputConnection ic = getCurrentInputConnection();
			// if (ic != null){
			// ic.clearMetaKeyStates(KeyEvent.META_ALT_ON);
			mMetaState = LIMEMetaKeyKeyListener
					.adjustMetaAfterKeypress(mMetaState);
			setInputConnectionMetaStateAsCurrentMetaKeyKeyListenerState();
			
			
			break;

		case KeyEvent.KEYCODE_ENTER:
			// Log.i("ART","select:"+7);
			// Let the underlying text editor always handle these, if return
			// false from takeSelectedSuggestion().
			// Process enter for candidate view selection in OnKeyUp() to block
			// the real enter afterward.
			// return false;
			//Log.i("ART", "physical keyboard:"+ keyCode);
			if (mCandidateView != null && mCandidateView.isShown()) {
				// To block a real enter after suggestion selection. We have to
				// return true in OnKeyUp();
				waitingEnterUp = true;
				return mCandidateView.takeSelectedSuggestion();
			}

		case KeyEvent.KEYCODE_SPACE:
			
			SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(this);
			hasQuickSwitch = sp.getBoolean("switch_english_mode", false);

			if( (hasQuickSwitch  && hasShiftPress)|| hasCtrlPress ) {
					return true;
			}else{
				if(!mLIMEPref.getEnglishEnable() || !mEnglishOnly ){ //add !mEnglishOnly by Jeremy '11,5,12
					if (mCandidateView != null && mCandidateView.isShown()) {					
						if (mCandidateView.takeSelectedSuggestion()) {
							return true;
						}else {
							setCandidatesViewShown(false);
							break;
						}
					}else{
						
						break;
					}
				}else{
					
					if(tempEnglishList != null && tempEnglishList.size() > 1 && tempEnglishWord != null && tempEnglishWord.length() > 0){
						this.pickSuggestionManually(0); // Jeremy '11,5,12 testing (1)->(0)
						resetTempEnglishWord();
						return true;
					}
					
					/*else if(tempEnglishList != null && tempEnglishList.size() == 1){
						resetTempEnglishWord();
						
					}*/
					this.updateEnglishDictionaryView();
					break;
				}
			}
			
			
		case KeyEvent.KEYCODE_AT:
			return true;
		default:
			// Log.i("ART","select:"+10);

			// For all other keys, if we want to do transformations on
			// text being entered with a hard keyboard, we need to process
			// it and do the appropriate action.

			// Modified by Jeremy '10, 3, 27.
			// Log.i("ART","mEnglishOnly:"+mEnglishOnly);
			// Log.i("ART","mPredictionOn:"+mPredictionOn);
			// Log.i("ART","onIM:"+onIM);
			if (((mEnglishOnly && mPredictionOn) || (!mEnglishOnly && onIM))
					&& translateKeyDown(keyCode, event)) {
				// Log.i("ART","select:A"+10);
				return true;
			}
			// Log.i("ART","select:B"+10);

		}

		/*Log.i("ART", "Super onKeyDown:"+keyCode + " / " + event.getAction());
		Log.i("ART", "Super onKeyDown:"+keyCode + " / " + event);
		Log.i("ART", "Super onKeyDown UP:"+keyCode + " / " + KeyEvent.ACTION_UP);
		Log.i("ART", "Super onKeyDown DOWN:"+keyCode + " / " + KeyEvent.ACTION_DOWN);
		Log.i("ART", "Super onKeyDown MULTIPLE:"+keyCode + " / " + KeyEvent.ACTION_MULTIPLE);*/
		/*
		 * Handle when user input English characters
		 */
		Log.i("ART","English Only Physical Keyboard :" + (char)event.getUnicodeChar(LIMEMetaKeyKeyListener.getMetaState(mMetaState)) );
		
		int primaryKey = event.getUnicodeChar(LIMEMetaKeyKeyListener.getMetaState(mMetaState));
		char t = (char)primaryKey;
		
		//Log.i("ART","Test Physical Key:"+Character.isLetter(t));
		/*
		if(!Character.isLetter(t)){
			tempEnglishList.clear();
			tempEnglishWord.delete(0, tempEnglishWord.length());
			this.updateEnglishDictionaryView();
		}
		*/
		if(mLIMEPref.getEnglishEnable() && Character.isLetter(t)){
				this.tempEnglishWord.append(t); 
				this.updateEnglishDictionaryView();
				return super.onKeyDown(keyCode, event); 
		}else{
			// Chcek if input character not valid English Character then reset temp english string
			resetTempEnglishWord();
			resetCandidateBar();
			return super.onKeyDown(keyCode, event);
		}
	}
	
	private void resetCandidateBar(){
		Mapping empty = new Mapping();
				empty.setWord("");
				empty.setDictionary(true);
				
		LinkedList<Mapping> list = new LinkedList<Mapping>();
		    				list.add(empty);
		setSuggestions(null, false, false);
	}
	
	private void resetTempEnglishWord(){
		tempEnglishWord.delete(0, tempEnglishWord.length());
		tempEnglishList.clear();
	}

	private void setInputConnectionMetaStateAsCurrentMetaKeyKeyListenerState() {
		InputConnection ic = getCurrentInputConnection();
		if (ic != null) {
			int clearStatesFlags = 0;
			if (LIMEMetaKeyKeyListener.getMetaState(mMetaState,
					LIMEMetaKeyKeyListener.META_ALT_ON) == 0)
				clearStatesFlags += KeyEvent.META_ALT_ON;
			if (LIMEMetaKeyKeyListener.getMetaState(mMetaState,
					LIMEMetaKeyKeyListener.META_SHIFT_ON) == 0)
				clearStatesFlags += KeyEvent.META_SHIFT_ON;
			if (LIMEMetaKeyKeyListener.getMetaState(mMetaState,
					LIMEMetaKeyKeyListener.META_SYM_ON) == 0)
				clearStatesFlags += KeyEvent.META_SYM_ON;
			ic.clearMetaKeyStates(clearStatesFlags);
		}
	}

	/**
	 * Use this to monitor key events being delivered to the application. We get
	 * first crack at them, and can either resume them or let them continue to
	 * the app.
	 */
	@Override
	public boolean onKeyUp(int keyCode, KeyEvent event) {
		if (DEBUG) {
			Log.i("OnKeyUp", "keyCode:"
					+ keyCode
					+ ";hasCtrlPress:"
					+ hasCtrlPress
	/*				+ " KeyEvent.Alt_ON:"
					+ String.valueOf(LIMEMetaKeyKeyListener.getMetaState(
							mMetaState, LIMEMetaKeyKeyListener.META_ALT_ON))
					+ " KeyEvent.Shift_ON:"
					+ String.valueOf(LIMEMetaKeyKeyListener.getMetaState(
							mMetaState, LIMEMetaKeyKeyListener.META_SHIFT_ON))
	*/
							);

		}
		keydown = false;

		switch (keyCode) {
		// */------------------------------------------------------------------------
		// Modified by Jeremy '10, 3,12
		// keep track of alt state with mHasAlt.
		// Modified '10, 3, 24 for bug fix and alt-lock implementation
		case KeyEvent.KEYCODE_SHIFT_LEFT:
		case KeyEvent.KEYCODE_SHIFT_RIGHT:
			hasShiftPress = false;
			if(hasCtrlPress)  //'11,5,14 Jeremy ctrl-shift switch to next available keyboard
			{
				nextActiveKeyboard(); 
				return true;
			}
			mMetaState = LIMEMetaKeyKeyListener.handleKeyUp(mMetaState,	keyCode, event);
			break;
		case KeyEvent.KEYCODE_ALT_LEFT:
		case KeyEvent.KEYCODE_ALT_RIGHT:
			mMetaState = LIMEMetaKeyKeyListener.handleKeyUp(mMetaState,	keyCode, event);
			break;
		case MY_KEYCODE_CTRL_LEFT:
		case MY_KEYCODE_CTRL_RIGHT:
			hasCtrlPress = false;
			break;
		case KeyEvent.KEYCODE_ENTER:
			// Add by Jeremy '10, 3 ,29. Pick selected selection if candidates
			// shown.
			// Does not block real enter after select the suggestion. !! need
			// fix here!!
			// Let the underlying text editor always handle these, if return
			// false from takeSelectedSuggestion().
			// if (mCandidateView != null && mCandidateView.isShown()) {
			// return mCandidateView.takeSelectedSuggestion();
			// }
			//Log.i("ART", "physical keyboard onkeyup:"+ keyCode);
			if (waitingEnterUp) {
				return true;
			}
			;
			// Jeremy '10, 4, 12 bug fix on repeated enter.
			break;
		case KeyEvent.KEYCODE_AT:
			// alt-@ switch to next active keyboard.
			if (LIMEMetaKeyKeyListener.getMetaState(mMetaState,
					LIMEMetaKeyKeyListener.META_SHIFT_ON) > 0) {
				nextActiveKeyboard();
				mMetaState = LIMEMetaKeyKeyListener
						.adjustMetaAfterKeypress(mMetaState);
				setInputConnectionMetaStateAsCurrentMetaKeyKeyListenerState();
				return true;
				// Long press physical @ key to swtich chn/eng
			} else if (keyPressTime != 0
					&& System.currentTimeMillis() - keyPressTime > 700) {
				switchChiEng();
				return true;
			} else if (((mEnglishOnly && mPredictionOn) || (!mEnglishOnly && onIM))
					&& translateKeyDown(keyCode, event)) {
				return true;
			} else {
				translateKeyDown(keyCode, event);
				super.onKeyDown(keyCode, mKeydownEvent);
			}

			break;
		case KeyEvent.KEYCODE_SPACE:
			SharedPreferences sp = PreferenceManager
					.getDefaultSharedPreferences(this);
			hasQuickSwitch = sp.getBoolean("switch_english_mode", false);

			// If user enable Quick Switch Mode control then check if has
			// Shift+Space combination
			
			if ( (hasQuickSwitch && hasShiftPress)|| hasCtrlPress ) { //'11,5,13 Jeremy added Ctrl-space switch chi/eng	
					this.switchChiEng();		
					return true;	
			}

		default:

		}
		// Update metakeystate of IC maintained by MetaKeyKeyListerner
		setInputConnectionMetaStateAsCurrentMetaKeyKeyListenerState();
		
		
		return super.onKeyUp(keyCode, event);
	}

	/**
	 * Helper function to commit any text being composed in to the editor.
	 */
	private void commitTyped(InputConnection inputConnection) {
		if (DEBUG)
			Log.i("LIMEService:", "CommittedTyped()");
		try {
			if (mComposing.length() > 0
					|| (firstMatched != null && firstMatched.isDictionary())) {

				if (onIM) {
					if (firstMatched != null && firstMatched.getWord() != null
							&& !firstMatched.getWord().equals("")) {
						int firstMatchedLength = firstMatched.getWord()
								.length();

						if (firstMatched.getCode() == null
								|| firstMatched.getCode().equals("")) {
							firstMatchedLength = 1;
						}

						String wordToCommit = firstMatched.getWord();

						if (firstMatched != null
								&& firstMatched.getCode() != null
								&& firstMatched.getWord() != null) {
							if (firstMatched.getCode().toLowerCase().equals(
									firstMatched.getWord().toLowerCase())) {
								firstMatchedLength = 1;

								// if end with code then append " " space
								// wordToCommit += " ";
							}
						}

						if (DEBUG)
							Log.i("LIMEService", "CommitedTyped Length:"
									+ firstMatchedLength);
						// Do hanConvert before commit
						// '10, 4, 17 Jeremy
						// inputConnection.setComposingText("", 1);
						inputConnection.commitText(SearchSrv
								.hanConvert(wordToCommit), firstMatchedLength);
						
						try {
							SearchSrv.updateMapping(firstMatched.getId(),
									firstMatched.getCode(), firstMatched
											.getWord(),
									firstMatched.getPword(), firstMatched
											.getScore(), firstMatched
											.isDictionary());
						} catch (RemoteException e) {
							e.printStackTrace();
						}
						userdiclist.add(firstMatched);
						// Update userdict for auto-learning feature
						// if(userdiclist.size() > 1) { updateUserDict();}
						
						// Add by Jeremy '10, 4,1 . Reverse Lookup
						SearchSrv.rQuery(firstMatched.getWord());

						tempMatched = firstMatched;
						firstMatched = null;
						hasFirstMatched = true;
					} else if (firstMatched != null
							&& firstMatched.getWord() != null
							&& firstMatched.getWord().equals("")) {
						inputConnection.commitText(misMatched, misMatched
								.length());
						firstMatched = null;
						hasFirstMatched = false;

						userdiclist.add(null);
					} else {
						inputConnection.commitText(mComposing, mComposing
								.length());
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
					// mCandidateView.hideComposing();
				}
				mComposing.setLength(0);
				// updateDictionaryView();

			}
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	private void updateUserDict() {
		for (Mapping dicunit : userdiclist) {
			if (dicunit == null) {
				continue;
			}
			if (dicunit.getId() == null) {
				continue;
			}
			if (dicunit.getCode() == null) {
				continue;
			}
			try {
				SearchSrv.addUserDict(dicunit.getId(), dicunit.getCode(),
						dicunit.getWord(), dicunit.getPword(), dicunit
								.getScore(), dicunit.isDictionary());
			} catch (RemoteException e) {
				e.printStackTrace();
			}
		}
		try {
			SearchSrv.updateUserDict();
		} catch (RemoteException e) {
			e.printStackTrace();
		}
		userdiclist.clear();
	}

	/**
	 * Helper to update the shift state of our keyboard based on the initial
	 * editor state.
	 */
	public void updateShiftKeyState(EditorInfo attr) {
		InputConnection ic = getCurrentInputConnection();
		if (attr != null && mInputView != null
				&& mKeyboardSwitcher.isAlphabetMode() && ic != null) {
			int caps = 0;
			EditorInfo ei = getCurrentInputEditorInfo();
			if (mAutoCap && ei != null && ei.inputType != EditorInfo.TYPE_NULL) {
				caps = ic.getCursorCapsMode(attr.inputType);
			}
			mInputView.setShifted(mCapsLock || caps != 0);
		} else {
			if (!mCapsLock && mHasShift) {
				mKeyboardSwitcher.toggleShift();
				mHasShift = false;
			}
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
		// code has to < 256, a ascii character
		if (code < 256 && checkCode.matches(".*?[^A-Z]")
				&& checkCode.matches(".*?[^a-z]")
				&& checkCode.matches(".*?[^0-9]") && code != 32) {
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
				} else if (keyCode == 32 && this.mComposing.length() == 0
						&& this.tempMatched != null
						&& !this.tempMatched.getCode().trim().equals("")) {
					// Press Space Button + has matched keyword then do nothing
				} else if (keyCode == 32 && this.mComposing.length() == 0
						&& this.tempMatched != null
						&& this.tempMatched.getCode().trim().equals("")) {
					// Press Space Button + no matched keyword consider as
					// English append space at the end
					getCurrentInputConnection().commitText(
							String.valueOf((char) keyCode), 1);
				}
				hasFirstMatched = false;
			}
			break;
		}
	}

	public void onKey(int primaryCode, int[] keyCodes) {
		if (DEBUG) {
			Log.i("OnKey", "Entering Onkey(); primaryCode:" + primaryCode
					+ " mEnglishFlagShift:" + mEnglishFlagShift);
		}
		
		// To identify the source of character (Software keyboard or physical keyboard)
		if(mLIMEPref.getEnglishEnable() && primaryCode != Keyboard.KEYCODE_DELETE){
			isPressPhysicalKeyboard = false;
			
			// Chcek if input character not valid English Character then reset temp english string
			if(!Character.isLetter(primaryCode)&& mEnglishOnly){
				resetTempEnglishWord();
				resetCandidateBar();
			}
		}
		
		// Handle English/Lime Keyboard switch
		if (mEnglishFlagShift == false
				&& (primaryCode == Keyboard.KEYCODE_SHIFT)) {
			mEnglishFlagShift = true;
			if (DEBUG) {
				Log.i("OnKey", "mEnglishFlagShift:" + mEnglishFlagShift);
			}
		}
		if (primaryCode == Keyboard.KEYCODE_DELETE) {
			handleBackspace();
		} else if (primaryCode == Keyboard.KEYCODE_SHIFT) {
			if (DEBUG) {
				Log.i("OnKey", "KEYCODE_SHIFT");
			}
			handleShift();
		} else if (primaryCode == Keyboard.KEYCODE_CANCEL) {
			handleClose();
			return;
			// Process long press on options and shift
		} else if (primaryCode == LIMEKeyboardView.KEYCODE_OPTIONS) {
			handleOptions();
		} else if (primaryCode == LIMEKeyboardView.KEYCODE_SHIFT_LONGPRESS) {
			if (DEBUG) {
				Log.i("OnKey", "KEYCODE_SHIFT_LONGPRESS");
			}
			if (mCapsLock) {
				handleShift();
			} else {
				toggleCapsLock();
			}

		} else if (primaryCode == Keyboard.KEYCODE_MODE_CHANGE
				&& mInputView != null) {
			switchKeyboard(primaryCode);
		} else if (primaryCode == -9 && mInputView != null) {
			switchKeyboard(primaryCode);
			//Jeremy '11,5,15 Fixed softkeybaord enter key behavior
		} else if (primaryCode == 32 || primaryCode ==KEYCODE_ENTER ) {
			if (primaryCode == KEYCODE_ENTER && isModeURL) 
					getCurrentInputConnection().sendKeyEvent(new KeyEvent(KeyEvent.ACTION_UP, 66));
			else { 
			if (onIM && (mComposing.length() > 0)) 
				commitTyped(getCurrentInputConnection());
			sendKey(primaryCode);
			
			}
				
		} else {

			/*if(mLIMEPref.getEnglishEnable()){
				if(!Character.isLetter(primaryCode)){
					resetTempEnglishWord();
					this.updateEnglishDictionaryView();
				}
			}*/
			
			// if (isWordSeparator(primaryCode)) {
			// if (mComposing.length() > 0) {
			// commitTyped(getCurrentInputConnection());
			// }
			// sendKey(primaryCode);
			// updateShiftKeyState(getCurrentInputEditorInfo());
			// }
			// else{
			handleCharacter(primaryCode, keyCodes);
			// }
		}
		

	}

	private AlertDialog mOptionsDialog;
	// Contextual menu positions
	private static final int POS_SETTINGS = 0;
	private static final int POS_KEYBOARD = 1;
	private static final int POS_METHOD = 2;

	/**
	 * Add by Jeremy '10, 3, 24 for options menu in soft keyboard
	 */
	private void handleOptions() {
		AlertDialog.Builder builder = new AlertDialog.Builder(this);
		builder.setCancelable(true);
		builder.setIcon(R.drawable.sym_keyboard_done);
		builder.setNegativeButton(android.R.string.cancel, null);
		builder.setTitle(getResources().getString(R.string.ime_name));

		CharSequence itemSettings = getString(R.string.lime_setting_preference);
		CharSequence itemKeyboadList = getString(R.string.keyboard_list);
		CharSequence itemInputMethod = getString(R.string.input_method);

		builder.setItems(new CharSequence[] { itemSettings, itemKeyboadList,
				itemInputMethod }, new DialogInterface.OnClickListener() {

			public void onClick(DialogInterface di, int position) {
				di.dismiss();
				switch (position) {
				case POS_SETTINGS:
					launchSettings();
					break;
				case POS_KEYBOARD:
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
		intent.setClass(LIMEService.this, LIMEPreference.class);
		intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
		startActivity(intent);
	}

	private void nextActiveKeyboard() {

		buildActiveKeyboardList();
		int i;
		CharSequence keyboardname = "";
		for (i = 0; i < keyboardListCodes.size(); i++) {
			if (keyboardSelection.equals(keyboardListCodes.get(i))) {
				if (i == keyboardListCodes.size() - 1) {
					keyboardSelection = keyboardListCodes.get(0);
					keyboardname = keyboardList.get(0);
				} else {
					keyboardSelection = keyboardListCodes.get(i + 1);
					keyboardname = keyboardList.get(i + 1);
				}
				break;
			}
		}
		// cancel candidate view if it's shown
		if (mCandidateView != null) {
			mCandidateView.clear();
			// mCandidateView.hideComposing();
		}
		mComposing.setLength(0);
		setCandidatesViewShown(false);
		initialKeyboard();
		Toast.makeText(this, keyboardname, Toast.LENGTH_SHORT/2).show();
	}

	private void buildActiveKeyboardList() {
		CharSequence[] items = getResources().getStringArray(R.array.keyboard);
		CharSequence[] codes = getResources().getStringArray(R.array.keyboard_codes);
		SharedPreferences sp = PreferenceManager
				.getDefaultSharedPreferences(this);
		String keybaord_state_string = sp.getString("keyboard_state",
				"0;1;2;3;4;5;6;7;8");
		String[] s = keybaord_state_string.toString().split(";");

		keyboardList.clear();
		keyboardListCodes.clear();

		for (int i = 0; i < s.length; i++) {
			int index = Integer.parseInt(s[i]);

			if (index < items.length) {
				keyboardList.add(items[index].toString());
				keyboardListCodes.add(codes[index].toString());
			} else {
				break;
			}
		}

		// check if the selected keybaord is in active keybaord list.
		boolean matched = false;
		for (int i = 0; i < keyboardListCodes.size(); i++) {
			if (keyboardSelection.equals(keyboardListCodes.get(i))) {
				matched = true;
				break;
			}
		}
		if (!matched) {
			// if the selected keyboard is not in the active keyboard list.
			// set the keyboard to the first active keyboard
			keyboardSelection = keyboardListCodes.get(0);
		}

	}

	/**
	 * Add by Jeremy '10, 3, 24 for keyboard picker menu in options menu
	 */
	private void showKeyboardPicker() {

		buildActiveKeyboardList();

		AlertDialog.Builder builder = new AlertDialog.Builder(this);
		builder.setCancelable(true);
		builder.setIcon(R.drawable.sym_keyboard_done);
		builder.setNegativeButton(android.R.string.cancel, null);
		builder.setTitle(getResources().getString(R.string.keyboard_list));

		CharSequence[] items = new CharSequence[keyboardList.size()];// =
		// getResources().getStringArray(R.array.keyboard);
		int curKB = 0;
		for (int i = 0; i < keyboardList.size(); i++) {
			items[i] = keyboardList.get(i);
			if (keyboardSelection.equals(keyboardListCodes.get(i)))
				curKB = i;
		}

		builder.setSingleChoiceItems(items, curKB,
				new DialogInterface.OnClickListener() {

					public void onClick(DialogInterface di, int position) {
						di.dismiss();
						handlKeyboardSelection(position);
					}
				});

		mOptionsDialog = builder.create();
		Window window = mOptionsDialog.getWindow();
		// Jeremy '10, 4, 12
		// The IM is not initialialized. do nothing here if window=null.
		if (!(window == null)) {
			WindowManager.LayoutParams lp = window.getAttributes();
			lp.token = mInputView.getWindowToken();
			lp.type = WindowManager.LayoutParams.TYPE_APPLICATION_ATTACHED_DIALOG;
			window.setAttributes(lp);
			window.addFlags(WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM);

		}
		mOptionsDialog.show();

	}

	private void handlKeyboardSelection(int position) {
		SharedPreferences sp = PreferenceManager
				.getDefaultSharedPreferences(this);
		SharedPreferences.Editor spe = sp.edit();

		keyboardSelection = keyboardListCodes.get(position);

		spe.putString("keyboard_list", keyboardSelection);
		spe.commit();

		// cancel candidate view if it's shown
		if (mCandidateView != null) {
			mCandidateView.clear();
			// mCandidateView.hideComposing();
		}
		mComposing.setLength(0);
		setCandidatesViewShown(false);

		initialKeyboard();

		try {
			mKeyboardSwitcher.setKeyboardList(SearchSrv.getKeyboardList());
			mKeyboardSwitcher.setImList(SearchSrv.getImList());
			mKeyboardSwitcher.clearKeyboards();
		} catch (RemoteException e) {
			e.printStackTrace();
		}

	}

	public void onText(CharSequence text) {
		if (DEBUG)
			Log.i("LIMEService:", "OnText()");
		InputConnection ic = getCurrentInputConnection();
		if (ic == null)
			return;
		ic.beginBatchEdit();

		if (mPredicting) {
			commitTyped(ic);
			mJustRevertedSeparator = null;
		} else if (onIM) {

			if (mComposing.length() > 0) {
				commitTyped(ic);
			}
			if (firstMatched != null) {
				ic.commitText(this.firstMatched.getWord(), 0);
				try {
					SearchSrv.updateMapping(firstMatched.getId(), firstMatched
							.getCode(), firstMatched.getWord(), firstMatched
							.getPword(), firstMatched.getScore(), firstMatched
							.isDictionary());
				} catch (RemoteException e) {
					e.printStackTrace();
				}
			}
		} else {
			ic.commitText(text, 0);
		}
		ic.endBatchEdit();
		updateShiftKeyState(getCurrentInputEditorInfo());
	}

	/**
	 * Update the list of available candidates from the current composing text.
	 * This will need to be filled in by however you are determining candidates.
	 */
	private void updateCandidates() {

		// Log.i("ART", "Update Candidate mCompletionOn:"+ mCompletionOn);
		// Log.i("ART", "Update Candidate mComposing:"+ mComposing);
		// Log.i("ART", "Update Candidate mComposing:"+ mComposing.length());
		if (mCandidateView != null) {
			mCandidateView.clear();
			// mCandidateView.hideComposing();
		}

		if (!mCompletionOn && mComposing.length() > 0) {

			LinkedList<Mapping> list = new LinkedList<Mapping>();

			try {
				String keyString = mComposing.toString(), charString = "";

				list.addAll(SearchSrv.query(keyString, hasKeyPress));

				//Log.i("ART", "->" + list.size());
				if (list.size() > 0) {
					setSuggestions(list, true, true);
				} else {
					setSuggestions(null, false, false);
				}

				// Show composing window if keyToChar got different string.
				if (SearchSrv.getTablename() != null
						&& (SearchSrv.getTablename().equals("phonetic")
								|| SearchSrv.getTablename().equals("cj") || SearchSrv
								.getTablename().equals("scj"))) {
					if (keyString != null && !keyString.equals("")) {

						if (keyString.length() < 7) {
							charString = SearchSrv.keyToChar(keyString
									.toLowerCase());
							if (mCandidateView != null
									&& !charString.toUpperCase().equals(
											keyString.toUpperCase())
									&& !charString.equals("")) {
								if (charString != null
										&& !charString.trim().equals("")) {
									mCandidateView.setComposingText(charString);
								}
							}
						}

					} else {
						if (mCandidateView == null) {
							mCandidateView = new CandidateView(this);
						}
						mCandidateView.clear();
					}
				}

			} catch (RemoteException e) {
				e.printStackTrace();
			}

		}
		else 
			setSuggestions(null, false, false);
	}

	/*
	 * Update English dictionary view
	 */
	@SuppressWarnings("unchecked")
	private void updateEnglishDictionaryView() {

		if(mLIMEPref.getEnglishEnable()){
			
			try {

				LinkedList<Mapping> list = new LinkedList<Mapping>();
				
				Mapping empty = new Mapping();
						empty.setWord("");
						empty.setDictionary(true);
						
						
				Log.i("ART","CACHE STRING -> " + tempEnglishWord.toString());
				if(tempEnglishWord == null || tempEnglishWord.length() == 0){
				    list.add(empty);
					setSuggestions(list, false, false);
				}else{
					InputConnection ic = getCurrentInputConnection(); 
					boolean after = false;
					try{
						char c = ic.getTextAfterCursor(1, 1).charAt(0);
						if(!Character.isLetterOrDigit(c)){
							after = true;
						}
					}catch(StringIndexOutOfBoundsException e){
						after = true;
					}
	
					boolean matchedtemp = false;
					
					if(tempEnglishWord.length() > 0){
						try{
							if(tempEnglishWord.toString().equalsIgnoreCase(ic.getTextBeforeCursor(tempEnglishWord.toString().length(), 1).toString())){
								matchedtemp = true;
							}
						}catch(StringIndexOutOfBoundsException e){}
					}
	
					Log.i("ART","English Pre After:" + after);
					Log.i("ART","English Pre matchedtemp:" + matchedtemp);
					Log.i("ART","English Pre tempEnglishWord:" + tempEnglishWord);
					
					if(after || matchedtemp){
						
						tempEnglishList.clear();
	
						Mapping temp = new Mapping();
					      	    temp.setWord(tempEnglishWord.toString());
					            temp.setDictionary(true);
							            
						List<Mapping> templist = SearchSrv.queryDictionary(tempEnglishWord.toString());
						
						if (templist.size() > 0) {
						    list.add(temp);
						    list.addAll(templist);
							setSuggestions(list, true, true);
						    tempEnglishList.addAll(list);
						}else{
						    list.add(empty);
							setSuggestions(list, false, false);
							/*if(!matchedtemp){
								resetTempEnglishWord();
							}*/
						}
					}
				
				}
				
			} catch (Exception e) {
				e.printStackTrace();
			}
		}
	}
	
	/*
	 * Update dictionary view
	 */
	private void updateDictionaryView() {

		// Also use this to control whether need to display the english suggestions words.
		
		// If there is no Temp Matched word exist then not to display dictionary
		try {
			// Modified by Jeremy '10, 4,1. getCode -> getWord
			// if( tempMatched != null && tempMatched.getCode() != null &&
			// !tempMatched.getCode().equals("")){
			if (tempMatched != null && tempMatched.getWord() != null
					&& !tempMatched.getWord().equals("")) {

				LinkedList<Mapping> list = new LinkedList<Mapping>();
				// Modified by Jeremy '10,3 ,12 for more specific related word
				// -----------------------------------------------------------
				if (tempMatched != null && hasMappingList) {
					list.addAll(SearchSrv.queryUserDic(tempMatched.getWord()));
				}
				// -----------------------------------------------------------

				if (list.size() > 0) {
					templist = (LinkedList) list;
					setSuggestions(list, true, true);
				} else {
					tempMatched = null;
					setSuggestions(null, false, false);
				}
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	public void setSuggestions(List<Mapping> suggestions, boolean completions,
			boolean typedWordValid) {

		if (suggestions != null && suggestions.size() > 0) {

			setCandidatesViewShown(true);

			hasMappingList = true;

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
				mCandidateView.setSuggestions(suggestions, completions,
						typedWordValid);
			}
		} else {
			hasMappingList = false;
			if (mCandidateView != null) {
				setCandidatesViewShown(false);
			}
		}
	}

	private void handleBackspace() {

		final int length = mComposing.length();
		if (length > 1) {
			mComposing.delete(length - 1, length);
			getCurrentInputConnection().setComposingText(mComposing, 1);
			updateCandidates();
		} else if (length == 1) {
			// '10, 4, 5 Jeremy. Bug fix on delete last key in buffer.
			getCurrentInputConnection().setComposingText("", 0);
			if (mCandidateView != null) {
				mCandidateView.clear();
				// mCandidateView.hideComposing();
			}
			mComposing.setLength(0);
			setCandidatesViewShown(false);
			getCurrentInputConnection().commitText("", 0);
		} else {
			if (mCandidateView != null) {
				mCandidateView.clear();
				// mCandidateView.hideComposing();
			}
			try{
				if(mLIMEPref.getEnglishEnable()){
					if(tempEnglishWord != null && tempEnglishWord.length() > 0){
						tempEnglishWord.deleteCharAt(tempEnglishWord.length()-1);
						updateEnglishDictionaryView();
					} 
					keyDownUp(KeyEvent.KEYCODE_DEL);
				}else{
					mComposing.setLength(0);
					setCandidatesViewShown(false);
					keyDownUp(KeyEvent.KEYCODE_DEL);
				}
			}catch(Exception e){
				Log.i("ART","->"+e);
			}
		}
		// updateShiftKeyState(getCurrentInputEditorInfo());

	}

	public void setCandidatesViewShown(boolean shown) {
		super.setCandidatesViewShown(shown);
		if (mCandidateView != null) {
			if (shown)
				mCandidateView.showComposing();
			else
				mCandidateView.hideComposing();
		}
	}

	private void handleShift() {

		if (mInputView == null) {
			return;
		}

		if (mKeyboardSwitcher.isAlphabetMode()) {
			// Alphabet keyboard
			checkToggleCapsLock();
			mInputView.setShifted(mCapsLock || !mInputView.isShifted());
			mHasShift = mCapsLock || !mInputView.isShifted();
			if(mHasShift){
				mKeyboardSwitcher.toggleShift();
			}
		} else {
			if (mCapsLock) {
				toggleCapsLock();
				mHasShift = false;
			} else if (mHasShift) {
				toggleCapsLock();
				mHasShift = true;
			} else {
				mKeyboardSwitcher.toggleShift();
				mHasShift = mKeyboardSwitcher.isShifted();

			}
		}
	}

	private void switchKeyboard(int primaryCode) {

		if (mCapsLock)
			toggleCapsLock();

		if (mCandidateView != null) {
			mCandidateView.clear();
		}
		mComposing.setLength(0);
		setCandidatesViewShown(false);

		if (primaryCode == Keyboard.KEYCODE_MODE_CHANGE) {
			switchSymKeyboard();
		} else if (primaryCode == KEYBOARD_SWITCH_CODE) {
			switchChiEng();
		}

		mHasShift = false;
		updateShiftKeyState(getCurrentInputEditorInfo());

	}

	private void switchSymKeyboard() {
		// Switch Keyboard between Symbol and Lime

		mKeyboardSwitcher.toggleSymbols();

	}
	private void switchChiEngNoToast() {
		// mEnglishOnly = !mEnglishOnly;
		// cancel candidate view if it's shown

		// if(mCapsLock) toggleCapsLock();

		mKeyboardSwitcher.toggleChinese();
		mEnglishOnly = !mKeyboardSwitcher.isChinese();

		if (mEnglishOnly) {
			onIM = false;
		} else {
			onIM = true;
		}

	}
	
	private void switchChiEng() {
		// mEnglishOnly = !mEnglishOnly;
		// cancel candidate view if it's shown

		// if(mCapsLock) toggleCapsLock();

		mKeyboardSwitcher.toggleChinese();
		mEnglishOnly = !mKeyboardSwitcher.isChinese();

		if (mEnglishOnly) {
			onIM = false;
			Toast.makeText(this, R.string.typing_mode_english, Toast.LENGTH_SHORT/2).show();
		} else {
			onIM = true;
			Toast.makeText(this, R.string.typing_mode_mixed, Toast.LENGTH_SHORT/2).show();
		}

	}

	/*private int getKeyboardMode(String code) {

		int mMode = mKeyboardSwitcher.MODE_TEXT_DEFAULT;
		if (code.equals("custom")) {
			SharedPreferences sp = PreferenceManager
					.getDefaultSharedPreferences(this);
			hasNumberMapping = sp.getBoolean("accept_number_index", false);
			hasSymbolMapping = sp.getBoolean("accept_symbol_index", false);

		} else if (code.equals("cj")) {
			SharedPreferences sp = PreferenceManager
					.getDefaultSharedPreferences(this);
			hasNumberMapping = sp.getBoolean("accept_number_index", false);
			hasSymbolMapping = sp.getBoolean("accept_symbol_index", false);
		} else if (code.equals("scj")) {
			SharedPreferences sp = PreferenceManager
					.getDefaultSharedPreferences(this);
			hasNumberMapping = sp.getBoolean("accept_number_index", false);
			hasSymbolMapping = sp.getBoolean("accept_symbol_index", false);
		} else if (code.equals("phonetic")) {
			mMode = mKeyboardSwitcher.MODE_TEXT_PHONETIC;

			// Should use number and symbol mapping
			hasNumberMapping = true;
			hasSymbolMapping = true;
		} else if (code.equals("ez")) {
			mMode = mKeyboardSwitcher.MODE_TEXT_EZ;

			// Should use number and symbol mapping
			hasNumberMapping = true;
			hasSymbolMapping = true;
		} else if (code.equals("array")) {
			mMode = mKeyboardSwitcher.MODE_TEXT_ARRAY;

			// Should use number and symbol mapping
			hasNumberMapping = true;
			hasSymbolMapping = true;
		}  else if (code.equals("array10")) {
			mMode = mKeyboardSwitcher.MODE_TEXT_ARRAY10;

			// Should use number and symbol mapping
			hasNumberMapping = true;
			hasSymbolMapping = true;
		}  else if (code.equals("dayi")) {
			mMode = mKeyboardSwitcher.MODE_TEXT_DAYI;

			// Should use number and symbol mapping
			hasNumberMapping = true;
			hasSymbolMapping = true;
		} else if (code.equals("phone")) {
			mMode = mKeyboardSwitcher.MODE_TEXT_PHONE;
			// Should use number and symbol mapping
			hasNumberMapping = true;
			hasSymbolMapping = true;
		}

		return mMode;
	}*/
	
	private void initialViewAndSwitcher(){

		//Check if mInputView == null;
		if (mInputView == null) {
			mInputView = (LIMEKeyboardView) getLayoutInflater().inflate(R.layout.input, null);
			mInputView.setOnKeyboardActionListener(this);
		}

		// Checkif mKeyboardSwitcher == null
		if (mKeyboardSwitcher == null) {
			mKeyboardSwitcher = new LIMEKeyboardSwitcher(this);
			mKeyboardSwitcher.setInputView(mInputView);
		}
		
		if(mKeyboardSwitcher.getKeyboardSize() == 0 && SearchSrv != null){
			try {
				mKeyboardSwitcher.setKeyboardList(SearchSrv.getKeyboardList());
				mKeyboardSwitcher.setImList(SearchSrv.getImList());
			} catch (RemoteException e) {
				e.printStackTrace();
			}
		}

	}

	private void initialKeyboard() {

		Log.i("ART", "Run Initial Keyboard");
		buildActiveKeyboardList();
		initialViewAndSwitcher();
		
		int mMode = mKeyboardSwitcher.MODE_TEXT_DEFAULT;
		
		if(keyboardSelection.equals("custom") || keyboardSelection.equals("cj") || keyboardSelection.equals("scj")){
			mKeyboardSwitcher.setKeyboardMode(keyboardSelection, mKeyboardSwitcher.MODE_IM, mImeOptions, true, false, false);
			SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(this);
			hasNumberMapping = sp.getBoolean("accept_number_index", false);
			hasSymbolMapping = sp.getBoolean("accept_symbol_index", false);
		}else{
			if(keyboardSelection.equals("phonetic") || 
					keyboardSelection.equals("ez") || 
					keyboardSelection.equals("dayi") ||
					keyboardSelection.equals("array") ||
					keyboardSelection.equals("array10") ){
				mKeyboardSwitcher.setKeyboardMode(keyboardSelection, mKeyboardSwitcher.MODE_IM, mImeOptions, true, false, false);
				hasNumberMapping = true;
				hasSymbolMapping = true;
			}else{
				mKeyboardSwitcher.setKeyboardMode(keyboardSelection, mKeyboardSwitcher.MODE_IM, mImeOptions, true, false, false);
			}
		}
		
		try {
			String tablename = new String(keyboardSelection);
			if (tablename.equals("custom") || tablename.equals("phone")) {
				tablename = "custom";
			}
			SearchSrv.setTablename(tablename);
		} catch (RemoteException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}
	
	/*private void initialKeyboard() {

		buildActiveKeyboardList();

		if (mInputView == null) {
			mInputView = (LIMEKeyboardView) getLayoutInflater().inflate(
					R.layout.input, null);
			mInputView.setOnKeyboardActionListener(this);
		}

		if (mKeyboardSwitcher == null) {
			mKeyboardSwitcher = new LIMEKeyboardSwitcher(this);
			mKeyboardSwitcher.setInputView(mInputView);
		}

		int mMode = mKeyboardSwitcher.MODE_TEXT_DEFAULT;
		if (keyboardSelection.equals("custom")) {
			if (hasNumberKeypads) {
				mMode = mKeyboardSwitcher.MODE_TEXT_DEFAULT_NUMBER;
			} else {
				mMode = mKeyboardSwitcher.MODE_TEXT_DEFAULT;
			}

			SharedPreferences sp = PreferenceManager
					.getDefaultSharedPreferences(this);
			hasNumberMapping = sp.getBoolean("accept_number_index", false);
			hasSymbolMapping = sp.getBoolean("accept_symbol_index", false);

		} else if (keyboardSelection.equals("cj")) {
			if (hasNumberKeypads) {
				mMode = mKeyboardSwitcher.MODE_TEXT_CJ_NUMBER;
			} else {
				mMode = mKeyboardSwitcher.MODE_TEXT_CJ;
			}

			SharedPreferences sp = PreferenceManager
					.getDefaultSharedPreferences(this);
			hasNumberMapping = sp.getBoolean("accept_number_index", false);
			hasSymbolMapping = sp.getBoolean("accept_symbol_index", false);
		} else if (keyboardSelection.equals("scj")) {
			if (hasNumberKeypads) {
				mMode = mKeyboardSwitcher.MODE_TEXT_SCJ_NUMBER;
			} else {
				mMode = mKeyboardSwitcher.MODE_TEXT_SCJ;
			}

			SharedPreferences sp = PreferenceManager
					.getDefaultSharedPreferences(this);
			hasNumberMapping = sp.getBoolean("accept_number_index", false);
			hasSymbolMapping = sp.getBoolean("accept_symbol_index", false);
		} else if (keyboardSelection.equals("phonetic")) {
			mMode = mKeyboardSwitcher.MODE_TEXT_PHONETIC;

			// Should use number and symbol mapping
			hasNumberMapping = true;
			hasSymbolMapping = true;
		} else if (keyboardSelection.equals("ez")) {
			mMode = mKeyboardSwitcher.MODE_TEXT_EZ;

			// Should use number and symbol mapping
			hasNumberMapping = true;
			hasSymbolMapping = true;
		} else if (keyboardSelection.equals("dayi")) {
			mMode = mKeyboardSwitcher.MODE_TEXT_DAYI;

			// Should use number and symbol mapping
			hasNumberMapping = true;
			hasSymbolMapping = true;
		} else if (keyboardSelection.equals("phone")) {
			mMode = mKeyboardSwitcher.MODE_TEXT_PHONE;
			// Should use number and symbol mapping
			hasNumberMapping = true;
			hasSymbolMapping = true;
		}
		//mKeyboardSwitcher.setKeyboardMode(mMode, 0);
		mKeyboardSwitcher.setKeyboardMode(mMode, this.mImeOptions);
		// Reset Shift Status
		// mCapsLock = false;
		// mHasShift = false;
		// mCurKeyboard.setShifted(false);
		// Set db table name.
		try {
			String tablename = new String(keyboardSelection);
			if (tablename.equals("custom") || tablename.equals("phone")) {
				tablename = "custom";
			}
			SearchSrv.setTablename(tablename);
		} catch (RemoteException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}*/

	/**
	 * This method construct candidate view and add key code to composing object
	 * 
	 * @param primaryCode
	 * @param keyCodes
	 */
	private void handleCharacter(int primaryCode, int[] keyCodes) {

		//Log.i("ART","handleCharacter :" + primaryCode);
		
		// Use the code -99 to represent the Action Move Downward
		if(primaryCode == -99){
			getCurrentInputConnection().sendKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN, 20));
		}else{
		
			// Caculate key press time to handle Eazy IM keys mapping
			// 1,2,3,4,5,6 map to -(45) =(43) [(91) ](93) ,(44) \(92)
			if (keyPressTime != 0
					&& (System.currentTimeMillis() - keyPressTime > 700)
					&& mKeyboardSwitcher.getKeyboardMode() == mKeyboardSwitcher.MODE_TEXT_EZ) {
				if (primaryCode == 49) {
					primaryCode = 45;
				} else if (primaryCode == 50) {
					primaryCode = 61;
				} else if (primaryCode == 51) {
					primaryCode = 91;
				} else if (primaryCode == 52) {
					primaryCode = 93;
				} else if (primaryCode == 53) {
					primaryCode = 44;
				} else if (primaryCode == 54) {
					primaryCode = 92;
				}
			}
	
			// Adjust metakeystate on printed key pressed.
			mMetaState = LIMEMetaKeyKeyListener.adjustMetaAfterKeypress(mMetaState);
	
			// If keyboard type = phone then check the user selection
			//if (mKeyboardSwitcher.getImKeyboard(keyboardSelection).equals("phone")) {
				/*
				// User use Phone Keyboard
				try {
					SharedPreferences sp1 = getSharedPreferences(PREF, 0);
					String xyvalue = sp1.getString("xy", "");
					this.keyUpX = Float.parseFloat(xyvalue.split(",")[0]);
					this.keyUpY = Float.parseFloat(xyvalue.split(",")[1]);
				} catch (Exception e) {
					e.printStackTrace();
				}
	
				float directionX = keyDownX - keyUpX;
				float directionY = keyDownY - keyUpY;
	
				int result = 0;
				// Only when keyboard type equal "Phone"
				if ((keyDownX - keyUpX) > moveLength
						|| (keyUpX - keyDownX) > moveLength
						|| (keyDownY - keyUpY) > moveLength
						|| (keyUpY - keyDownY) > moveLength) {
	
					if (keyDownCode == 40) {
						result = handleSelection(directionX, directionY,
								new String[] { "[", ")", "(", "]" });
					} else if (keyDownCode == 44) {
						result = handleSelection(directionX, directionY,
								new String[] { "?", ".", ";", "\\" });
					} else if (keyDownCode == 48) {
						result = handleSelection(directionX, directionY,
								new String[] { "^", "}", "~", "{" });
					} else if (keyDownCode == 49) {
						result = handleSelection(directionX, directionY,
								new String[] { "!", "1", "@", "#" });
					} else if (keyDownCode == 50) {
						if (mHasShift) {
							result = handleSelection(directionX, directionY,
									new String[] { "B", "2", "A", "C" });
						} else {
							result = handleSelection(directionX, directionY,
									new String[] { "b", "2", "a", "c" });
						}
					} else if (keyDownCode == 51) {
						if (mHasShift) {
							result = handleSelection(directionX, directionY,
									new String[] { "E", "3", "D", "F" });
						} else {
							result = handleSelection(directionX, directionY,
									new String[] { "e", "3", "d", "f" });
						}
					} else if (keyDownCode == 52) {
						if (mHasShift) {
							result = handleSelection(directionX, directionY,
									new String[] { "H", "4", "G", "I" });
						} else {
							result = handleSelection(directionX, directionY,
									new String[] { "h", "4", "g", "i" });
						}
					} else if (keyDownCode == 53) {
						if (mHasShift) {
							result = handleSelection(directionX, directionY,
									new String[] { "K", "5", "J", "L" });
						} else {
							result = handleSelection(directionX, directionY,
									new String[] { "k", "5", "j", "l" });
						}
					} else if (keyDownCode == 54) {
						if (mHasShift) {
							result = handleSelection(directionX, directionY,
									new String[] { "N", "6", "M", "O" });
						} else {
							result = handleSelection(directionX, directionY,
									new String[] { "n", "6", "m", "o" });
						}
					} else if (keyDownCode == 55) {
						if (mHasShift) {
							result = handleSelection(directionX, directionY,
									new String[] { "Q", "S", "P", "R" });
						} else {
							result = handleSelection(directionX, directionY,
									new String[] { "q", "s", "p", "r" });
						}
					} else if (keyDownCode == 56) {
						if (mHasShift) {
							result = handleSelection(directionX, directionY,
									new String[] { "U", "8", "T", "V" });
						} else {
							result = handleSelection(directionX, directionY,
									new String[] { "u", "8", "t", "v" });
						}
					} else if (keyDownCode == 57) {
						if (mHasShift) {
							result = handleSelection(directionX, directionY,
									new String[] { "X", "Z", "W", "Y" });
						} else {
							result = handleSelection(directionX, directionY,
									new String[] { "x", "z", "w", "y" });
						}
					} else if (keyDownCode == 61) {
						result = handleSelection(directionX, directionY,
								new String[] { "-", "/", "+", "*" });
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
						// updateShiftKeyState(getCurrentInputEditorInfo());
						updateCandidates();
						misMatched = mComposing.toString();
					} else if (!hasSymbolMapping
							&& hasNumberMapping
							&& (isValidLetter(primaryCode) || isValidDigit(primaryCode))
							&& onIM) {
						mComposing.append((char) primaryCode);
						getCurrentInputConnection().setComposingText(mComposing, 1);
						// updateShiftKeyState(getCurrentInputEditorInfo());
						updateCandidates();
						misMatched = mComposing.toString();
					} else if (hasSymbolMapping
							&& !hasNumberMapping
							&& (isValidLetter(primaryCode) || isValidSymbol(primaryCode))
							&& onIM) {
						mComposing.append((char) primaryCode);
						getCurrentInputConnection().setComposingText(mComposing, 1);
						// updateShiftKeyState(getCurrentInputEditorInfo());
						updateCandidates();
						misMatched = mComposing.toString();
					} else if (hasSymbolMapping
							&& hasNumberMapping
							&& (isValidSymbol(primaryCode)
									|| isValidLetter(primaryCode) || isValidDigit(primaryCode))
							&& onIM) {
						mComposing.append((char) primaryCode);
						getCurrentInputConnection().setComposingText(mComposing, 1);
						// updateShiftKeyState(getCurrentInputEditorInfo());
						updateCandidates();
						misMatched = mComposing.toString();
					} else {
						getCurrentInputConnection().commitText(
								mComposing + String.valueOf((char) primaryCode), 1);
					}
				} else {
					if (isInputViewShown()) {
						if (mInputView.isShifted()) {
							primaryCode = Character.toUpperCase(primaryCode);
						}
					}
					getCurrentInputConnection().commitText(
							String.valueOf((char) primaryCode), 1);
				}*/
	
			//} else {
				// *** NOT PHONE KEYBOARD***
				// If user not user PHONE Keyboard then use this one
				if (!mEnglishOnly) {
					// Shift keyboard already sent uppercase characters
					/*
					 * if (isInputViewShown()) { if (mInputView.isShifted()) {
					 * primaryCode = Character.toUpperCase(primaryCode); } }
					 */
					if (DEBUG) {
						Log.i("HandleCharacter", "isValidLetter:"
								+ isValidLetter(primaryCode) + " isValidDigit:"
								+ isValidDigit(primaryCode) + " isValideSymbo:"
								+ isValidSymbol(primaryCode) + " onIM:" + onIM);
					}
	
					if (!hasSymbolMapping && !hasNumberMapping
							&& isValidLetter(primaryCode) && onIM) {
						mComposing.append((char) primaryCode);
						getCurrentInputConnection().setComposingText(mComposing, 1);
						// updateShiftKeyState(getCurrentInputEditorInfo());
						updateCandidates();
						misMatched = mComposing.toString();
					} else if (!hasSymbolMapping
							&& hasNumberMapping
							&& (isValidLetter(primaryCode) || isValidDigit(primaryCode))
							&& onIM) {
						mComposing.append((char) primaryCode);
						getCurrentInputConnection().setComposingText(mComposing, 1);
						updateCandidates();
						misMatched = mComposing.toString();
					} else if (hasSymbolMapping
							&& !hasNumberMapping
							&& (isValidLetter(primaryCode) || isValidSymbol(primaryCode))
							&& onIM) {
						mComposing.append((char) primaryCode);
						getCurrentInputConnection().setComposingText(mComposing, 1);
						updateCandidates();
						misMatched = mComposing.toString();
					} else if (hasSymbolMapping
							&& hasNumberMapping
							&& (isValidSymbol(primaryCode)
									|| isValidLetter(primaryCode) || isValidDigit(primaryCode))
							&& onIM) {
						if(primaryCode != 10){
							mComposing.append((char) primaryCode);
							getCurrentInputConnection().setComposingText(mComposing, 1);
							updateCandidates();
							misMatched = mComposing.toString();
						}else{
							if(!mCandidateView.takeSelectedSuggestion()){
								if(!isModePassword && !isModeURL){
									getCurrentInputConnection().commitText(String.valueOf((char) primaryCode), 1);
								}
							}
						}
						
					} else {
						if(!mEnglishOnly || !onIM){
							if(!mCandidateView.takeSelectedSuggestion()){
								getCurrentInputConnection().commitText(
										mComposing + String.valueOf((char) primaryCode), 1);
							}
						}else{
							getCurrentInputConnection().commitText(
									mComposing + String.valueOf((char) primaryCode), 1);
						}
					}
				} else {
					/*
					 * Handle when user input English Characters
					 */
					Log.i("ART","English Only Software Keyboard :"+String.valueOf((char) primaryCode));

					if (isInputViewShown()) {
						if (mInputView.isShifted()) {
							primaryCode = Character.toUpperCase(primaryCode);
						}
					}

					if(mLIMEPref.getEnglishEnable()){
						if(Character.isLetter((char)primaryCode)){
							this.tempEnglishWord.append((char)primaryCode);
							this.updateEnglishDictionaryView();
						}
						/*else{
							resetTempEnglishWord();
							this.updateEnglishDictionaryView();
						}*/
					}

					/*
					if(primaryCode != 10 && primaryCode != -99 && !isEnterNext){
						getCurrentInputConnection().commitText(String.valueOf((char) primaryCode), 1);
					}else{
						getCurrentInputConnection().sendKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN, 20));
						isEnterNext = false;
					}*/
					getCurrentInputConnection().commitText(String.valueOf((char) primaryCode), 1);
				}
			//}
		}
		
		// updateShift(primaryCode);
		updateShiftKeyState(getCurrentInputEditorInfo());
	}

	private void handleClose() {

		// cancel candidate view if it's shown
		if (mCandidateView != null) {
			mCandidateView.clear();
		}
		mComposing.setLength(0);
		// setCandidatesViewShown(false);

		requestHideSelf(0);
		mInputView.closing();
	}

	private void checkToggleCapsLock() {

		if (mInputView.getKeyboard().isShifted()) {
			toggleCapsLock();
		}

	}

	private void toggleCapsLock() {
		mCapsLock = !mCapsLock;
		if (mKeyboardSwitcher.isAlphabetMode()) {
			((LIMEKeyboard) mInputView.getKeyboard()).setShiftLocked(mCapsLock);
		} else {
			if (mCapsLock) {
				if (DEBUG) {
					Log.i("toggleCapsLock", "mCapsLock:true");
				}
				if (!mKeyboardSwitcher.isShifted())
					mKeyboardSwitcher.toggleShift();
				((LIMEKeyboard) mInputView.getKeyboard()).setShiftLocked(true);
			} else {
				if (DEBUG) {
					Log.i("toggleCapsLock", "mCapsLock:false");
				}
				((LIMEKeyboard) mInputView.getKeyboard()).setShiftLocked(false);
				if (mKeyboardSwitcher.isShifted())
					mKeyboardSwitcher.toggleShift();
				// ((LIMEKeyboard) mInputView.getKeyboard()).setShifted(false);

			}
		}
	}

	public boolean isWordSeparator(int code) {
		// String checkCode = String.valueOf((char)code);
		// if (code == 32 || code == 39 || code == 10) {
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
		if (DEBUG)
			Log.i("LIMEService:", "pickSuggestionManually():" +
					"Pick up word at index : " + index);
		
		if (templist != null && templist.size() > 0) {
			firstMatched = templist.get(index);
		}

		if (mCompletionOn && mCompletions != null && index >= 0
				&& index < mCompletions.length && mEnglishOnly) {
			CompletionInfo ci = mCompletions[index];
			getCurrentInputConnection().commitCompletion(ci);
			if (DEBUG) 
				Log.i("LIMEService:", "pickSuggestionManually():mCompletionOn:" + mCompletionOn);
			
		} else if (mComposing.length() > 0 && !mEnglishOnly) {
			//Log.i("ART","When user pick suggested word which is not from dictionary");
			commitTyped(getCurrentInputConnection());
			this.firstMatched = null;
			this.hasFirstMatched = false;
			templist.clear();
			updateDictionaryView();
		} else if (firstMatched != null && firstMatched.isDictionary() && !mEnglishOnly) {
			//Log.i("ART","When user pick suggested word which is from dictionary");
			commitTyped(getCurrentInputConnection());
			updateDictionaryView();
		}else{
			if(mLIMEPref.getEnglishEnable() && tempEnglishList != null && tempEnglishList.size() > 0 ){
				if(index > 0){
					getCurrentInputConnection().commitText(this.tempEnglishList.get(index).getWord().substring(tempEnglishWord.length()) + " ", 0);	
					//getCurrentInputConnection().commitText(this.tempEnglishList.get(index).getWord().substring(tempEnglishWord.length()) + " ", this.tempEnglishList.get(index).getWord().length()+1);	
				}else if(index == 0){					
					// Only when using physical keyboard and press "Space" will append "Space" at the end of string.
					if(isPressPhysicalKeyboard){
						getCurrentInputConnection().commitText(" ", 0);	
					}
				}
				resetTempEnglishWord();
				
				Mapping temp = new Mapping();
	      	    temp.setWord("");
	            temp.setDictionary(true);
			    //List list = new LinkedList();
			    //	 list.add(temp);   // Jeremy '11,5,14.  setSuggestion null to off candidateview
				setSuggestions(null, false, false);
			}
		}

	}

	void promoteToUserDictionary(String word, int frequency) {
		if (mUserDictionary.isValidWord(word))
			return;
		mUserDictionary.addWord(word, frequency);
	}

	public void swipeRight() {
		if (mCompletionOn) {
			pickDefaultCandidate();
		}
	}

	public void swipeLeft() {
			handleBackspace();
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

		// Record key press time (press down)
		keyPressTime = System.currentTimeMillis();

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

		try {

			/*if (!mKeyboardSwitcher.getImKeyboard(keyboardSelection).equals("phone")) {
				keyDownCode = primaryCode;

				SharedPreferences sp1 = getSharedPreferences(PREF, 0);
				String xyvalue = sp1.getString("xy", "");
				this.keyDownX = Float.parseFloat(xyvalue.split(",")[0]);
				this.keyDownY = Float.parseFloat(xyvalue.split(",")[1]);
			}*/

			hasKeyPress = true;
		} catch (Exception e) {
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
		if (Math.abs(x) > Math.abs(y)) {
			// move horizontal
			if (x > 0) {
				direction = this.LEFT;
			} else {
				direction = this.RIGHT;
			}
		} else {
			// move verticle
			if (y > 0) {
				direction = this.UP;
			} else {
				direction = this.DOWN;
			}
		}

		// Select Character to be import
		result = (int) keys[direction].hashCode();

		return result;
	}

	public boolean isValidTime(Date target) {
		Calendar srcCal = Calendar.getInstance();
		srcCal.setTime(new Date());
		Calendar destCal = Calendar.getInstance();
		destCal.setTime(target);

		if (srcCal.getTimeInMillis() - destCal.getTimeInMillis() < 1800000) {
			return true;
		} else {
			return false;
		}

	}

	class AutoDictionary extends ExpandableDictionary {
		// If the user touches a typed word 2 times or more, it will become
		// valid.
		private static final int VALIDITY_THRESHOLD = 2 * FREQUENCY_FOR_PICKED;
		// If the user touches a typed word 5 times or more, it will be added to
		// the user dict.
		private static final int PROMOTION_THRESHOLD = 5 * FREQUENCY_FOR_PICKED;

		public AutoDictionary(Context context) {
			super(context);
		}

		@Override
		public boolean isValidWord(CharSequence word) {
			final int frequency = getWordFrequency(word);
			return frequency > VALIDITY_THRESHOLD;
		}

		@Override
		public void addWord(String word, int addFrequency) {
			final int length = word.length();
			// Don't add very short or very long words.
			if (length < 2 || length > getMaxWordLength())
				return;
			super.addWord(word, addFrequency);
			final int freq = getWordFrequency(word);
			if (freq > PROMOTION_THRESHOLD) {
				LIMEService.this.promoteToUserDictionary(word,
						FREQUENCY_FOR_AUTO_ADD);
			}
		}
	}
	
}
