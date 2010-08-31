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


import android.inputmethodservice.InputMethodService;
import android.inputmethodservice.Keyboard;
import android.inputmethodservice.KeyboardView;
import android.media.AudioManager;
import android.os.Debug;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.os.RemoteException;
import android.os.SystemClock;
import android.os.Vibrator;
// MetaKeyKeyLister is buggy on locked metakey state
//import android.text.method.MetaKeyKeyListener;
import android.text.AutoText;
import android.text.TextUtils;
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

	static final boolean DEBUG =  false;
	static final String PREF = "LIMEXY";
	
	static final int KEYBOARD_SWITCH_CODE = -9;

	// Removed by Jeremy '10, 3, 26. We process hard keys all the time
	//static final boolean PROCESS_HARD_KEYS = true;

	private LIMEKeyboardView mInputView = null;
	private CandidateView mCandidateView = null;
	private CompletionInfo[] mCompletions;

	private StringBuilder mComposing = new StringBuilder();
    private WordComposer mWord = new WordComposer();

    
    private LIMEPreferenceManager mLIMEPref;
	private boolean mPredictionOn;
	private boolean mCompletionOn;
	private boolean mCapsLock;
	private boolean mAutoCap;
	private boolean mQuickFixes;
	private boolean mDefaultEnglish=false;
	private boolean mHasShift;
	//------------------------------------------------------------------------
	// Add by Jeremy '10, 3,12
	// new private variable mHasAlt for keeping state of alt.
	// Modified '10, 3, 26.  Process metakeystate with LIMEMetaKeyKeyLister.
	//private boolean mHasAlt = false;
	// '10, 3, 24 fix for continuous alt mode and alt-lock function.
	//private boolean mTrackAlt =false;
	//private boolean mAltLocked =false;
	//------------------------------------------------------------------------
	private boolean mEnglishOnly;
	private boolean mEnglishFlagShift;
	
	private static final int MSG_UPDATE_SUGGESTIONS = 0;
    private static final int MSG_UPDATE_SHIFT_STATE = 1;
    private static final int MSG_SHIFT_LONGPRESSED = 2;

	private boolean onIM = false;
	private boolean hasFirstMatched = false;
	// Removed by Jeremy '10, 3, 27.  
	//private boolean hasRightShiftPress = false;
	
	private boolean keydown = false;

	//private long mLastShiftTime;
	private long mMetaState;
    private boolean mJustAccepted;
    private CharSequence mJustRevertedSeparator;
	private int mImeOptions;

	
    LIMEKeyboardSwitcher mKeyboardSwitcher;
    
    private UserDictionary mUserDictionary;
    private ContactsDictionary mContactsDictionary;
    private ExpandableDictionary mAutoDictionary;
    
    private boolean mAutoSpace;
    private boolean mAutoCorrectOn;
    private boolean mShowSuggestions;
    private int     mCorrectionMode;
    private int     mOrientation;
    private boolean mPredicting;
    private String mLocale;
    private int mCommittedLength;
    private int mDeleteCount;
    private CharSequence mBestWord;
    
    private Suggest mSuggest;
    
    private String mSentenceSeparators;
    
    // How many continuous deletes at which to start deleting at a higher speed.
    private static final int DELETE_ACCELERATE_AT = 20;
    // Key events coming any faster than this are long-presses.
    private static final int QUICK_PRESS = 200;
    // Weight added to a user picking a new word from the suggestion strip
    static final int FREQUENCY_FOR_PICKED = 3;
    // Weight added to a user typing a new word that doesn't get corrected (or is reverted)
    static final int FREQUENCY_FOR_TYPED = 1;
    // A word that is frequently typed and get's promoted to the user dictionary, uses this
    // frequency.
    static final int FREQUENCY_FOR_AUTO_ADD = 250;

	private Mapping firstMatched;
	private Mapping tempMatched;

	private String mWordSeparators;
	private String misMatched;


	private LinkedList<Mapping> templist;
	private LinkedList<Mapping> userdiclist;

	private Vibrator mVibrator;
	private AudioManager mAudioManager;
	private final float FX_VOLUME = 1.0f;
	static final int KEYCODE_ENTER = 10;
	static final int KEYCODE_SPACE = ' ';

	private boolean hasVibration = false;
	private boolean hasSound = false;
	private boolean hasNumberKeypads = false;
	private boolean hasNumberMapping = false;
	private boolean hasSymbolMapping = false;
	private boolean hasKeyPress = false;
	private boolean hasQuickSwitch = false;
	
	
	// Hard Keyboad Shift + Space Status
	//private boolean hasShiftPress = false;
	//private boolean hasSpacePress = false;

	
	private String keyboardSelection;
	private List<String> keyboardList;
	private List<String> keyboardListCodes;

	private int keyDownCode = 0;
	private float keyDownX = 0;
	private float keyDownY = 0;
	private float keyUpX=0;
	private float keyUpY=0;
	
	private long mLastKeyTime;
	
	// To keep key press time
	private long keyPressTime = 0;
	
	//Keep keydown event
	KeyEvent mKeydownEvent = null;
	
	private final float moveLength = 15;
	private ISearchService SearchSrv = null;
	
	 Handler mHandler = new Handler() {
	        @Override
	        public void handleMessage(Message msg) {
	            switch (msg.what) {
	                case MSG_UPDATE_SUGGESTIONS:
	                	if(mEnglishOnly){
	                		updateSuggestions();
	                	}
	                	else{
	                		updateCandidates();
	                	}
	                    break;
	                case MSG_UPDATE_SHIFT_STATE:
	                    updateShiftKeyState(getCurrentInputEditorInfo());
	                    break;
	                
	            }
	        }
	    };
	
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
	    mKeyboardSwitcher = new LIMEKeyboardSwitcher(this);

		mEnglishOnly = false;
		mEnglishFlagShift = false;
		
		mLIMEPref = new LIMEPreferenceManager(this);

		// Startup Service
		if(SearchSrv == null){
			this.bindService(new Intent(ISearchService.class.getName()), serConn, Context.BIND_AUTO_CREATE);
		}
		
		// load preference settings
		loadSettings();
		final Configuration conf = getResources().getConfiguration();
		//initSuggest(conf.locale.toString());
		
		mVibrator = (Vibrator) getApplication().getSystemService(
				Service.VIBRATOR_SERVICE);
		mAudioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
		
	
		// initial Input List
		userdiclist = new LinkedList<Mapping>();
		
		// initial keyboard list
		keyboardList = new ArrayList<String>();
		keyboardListCodes = new ArrayList<String>();
		buildActiveKeyboardList();
		
	}
	
	 private void initSuggest(String locale) {
	        mLocale = locale;
	        mSuggest = new Suggest(this, SearchSrv);
	        mSuggest.setCorrectionMode(mCorrectionMode);
	        mUserDictionary = new UserDictionary(this);
	        mContactsDictionary = new ContactsDictionary(this);
	        mAutoDictionary = new AutoDictionary(this);
	        mSuggest.setUserDictionary(mUserDictionary);
	        mSuggest.setContactsDictionary(mContactsDictionary);
	        mSuggest.setAutoDictionary(mAutoDictionary);
	        mWordSeparators = getResources().getString(R.string.word_separators);
	        mSentenceSeparators = getResources().getString(R.string.sentence_separators);
	    }

	/**
	 * This is the point where you can do all of your UI initialization. It is
	 * called after creation and any configuration change.
	 */
	@Override
	public void onInitializeInterface() {
		
		

		mEnglishOnly = false;
		mEnglishFlagShift = false;
		
		if (mKeyboardSwitcher == null) {
	            mKeyboardSwitcher = new LIMEKeyboardSwitcher(this);
	    }
	    mKeyboardSwitcher.makeKeyboards(true);
	    super.onInitializeInterface();

	}

	
	 @Override
	    public void onConfigurationChanged(Configuration conf) {
		 
		 	if(DEBUG) Log.i("LIMEService:", "OnConfigurationChanged()");
		 	
	        if (!TextUtils.equals(conf.locale.toString(), mLocale)) {
	            initSuggest(conf.locale.toString());
	        }
	        // If orientation changed while predicting, commit the change
	        if (conf.orientation != mOrientation) {
	            commitTyped(getCurrentInputConnection());
	            mOrientation = conf.orientation;
	        }
	        if (mKeyboardSwitcher == null) {
	            mKeyboardSwitcher = new LIMEKeyboardSwitcher(this);
	        }
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
		mInputView = (LIMEKeyboardView) getLayoutInflater().inflate(R.layout.input, null);
		mKeyboardSwitcher.setInputView(mInputView);
        mKeyboardSwitcher.makeKeyboards(true);
        mInputView.setOnKeyboardActionListener(this);
        mKeyboardSwitcher.setKeyboardMode(LIMEKeyboardSwitcher.MODE_TEXT, 0);
		
		if(!mDefaultEnglish) setChnKeyboardMode();

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
        mWord.reset();
		if(onIM) {
			//updateCandidates();
			// Add Custom related words
			if(userdiclist.size() > 1) {	updateUserDict();}
			onIM = false;
		}

		setCandidatesViewShown(false);
			
		this.setSuggestions(null, false, false);
	}
	
	private void updateUserDict(){
		for(Mapping dicunit : userdiclist){
			if(dicunit == null || dicunit.getId() == null){continue;}
			try {
				SearchSrv.addUserDict(dicunit.getId(), 
										dicunit.getCode(), 
										dicunit.getWord(), 
										dicunit.getPword(), 
										dicunit.getScore(), 
										dicunit.isDictionary());
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

	@Override
	public void onStartInputView(EditorInfo attribute, boolean restarting) {
		super.onStartInputView(attribute, restarting);
		if(DEBUG) Log.i("LIMEService", "onStartInputView");
		if (mInputView == null) {
	            return;
	   }
		
		initOnStartInput(attribute, restarting);

	   
		
	}
	private void initOnStartInput(EditorInfo attribute, boolean restarting){
		

		TextEntryState.newSession(this);
		
		if (mInputView == null) {
			mInputView = (LIMEKeyboardView) getLayoutInflater().inflate(R.layout.input, null);
			mInputView.setOnKeyboardActionListener(this);
		}
		
		if (mKeyboardSwitcher == null) {
            mKeyboardSwitcher = new LIMEKeyboardSwitcher(this);
            mKeyboardSwitcher.setInputView(mInputView);
        }
		  
		mKeyboardSwitcher.makeKeyboards(false);
		
		userdiclist = new LinkedList<Mapping>();
		templist = new LinkedList<Mapping>();
		firstMatched = new Mapping();
		tempMatched = new Mapping();
			
			
		setCandidatesViewShown(false);

		// Reset our state. We want to do this even if restarting, because
		// the underlying state of the text editor could have changed in any
		// way.
		mComposing.setLength(0);
        mWord.reset();
        postUpdateSuggestions();

		if (!restarting) {
			// Clear shift states.
			mMetaState = 0;
		}

			
		loadSettings();
		
		buildActiveKeyboardList();

		mImeOptions = attribute.imeOptions;
		//initialKeyboard();
		boolean disableAutoCorrect = false;
		mPredictionOn = true;
		mCompletionOn = false;
		mCompletions = null;
		mCapsLock = false;
		mHasShift = false;
		mEnglishOnly = false;
		onIM = true;
		switch (attribute.inputType&EditorInfo.TYPE_MASK_CLASS) {
		  	case EditorInfo.TYPE_CLASS_NUMBER:
		    case EditorInfo.TYPE_CLASS_DATETIME:
		    	mEnglishOnly = true;
		        onIM = false;
		        mKeyboardSwitcher.setKeyboardMode(mKeyboardSwitcher.MODE_SYMBOLS, attribute.imeOptions);
		        break;
		    case EditorInfo.TYPE_CLASS_PHONE:
		       	mEnglishOnly = true;
		    	onIM = false;
		        mKeyboardSwitcher.setKeyboardMode(mKeyboardSwitcher.MODE_PHONE, attribute.imeOptions);
		    break;
		    case EditorInfo.TYPE_CLASS_TEXT:
		        if(mDefaultEnglish){
		        	mPredictionOn = true;
		        	mEnglishOnly = true;
			        onIM = false;
		        	mKeyboardSwitcher.setKeyboardMode(mKeyboardSwitcher.MODE_TEXT,attribute.imeOptions);
		        }
		        else{ 
		        	setChnKeyboardMode();
		        }
		        
		       // Make sure that passwords are not displayed in candidate view
		        int variation = attribute.inputType &  EditorInfo.TYPE_MASK_VARIATION;
		        if (variation == EditorInfo.TYPE_TEXT_VARIATION_PASSWORD ||
		        		variation == EditorInfo.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD ) {
		        	mEnglishOnly = true;
			        onIM = false;
		        	mPredictionOn = false;
		        	mKeyboardSwitcher.setKeyboardMode(mKeyboardSwitcher.MODE_TEXT,attribute.imeOptions);
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
		            mKeyboardSwitcher.setKeyboardMode(mKeyboardSwitcher.MODE_EMAIL, attribute.imeOptions);
		        } else if (variation == EditorInfo.TYPE_TEXT_VARIATION_URI) {
		            mPredictionOn = false;
		            mEnglishOnly = true;
		            onIM = false;
		            mKeyboardSwitcher.setKeyboardMode(mKeyboardSwitcher.MODE_URL, attribute.imeOptions);
		        } else if (variation == EditorInfo.TYPE_TEXT_VARIATION_SHORT_MESSAGE) {
		        	/* Cancel the short message mode 
		        	mKeyboardSwitcher.setKeyboardMode(mKeyboardSwitcher.MODE_IM, attribute.imeOptions);
		        	if(mDefaultEnglish){
		        		mPredictionOn = false;
		        		mEnglishOnly = true;
		        		onIM = false;
		        	}else{
		        		setChnKeyboardMode();
		        	}
		        	*/
		        } else if (variation == EditorInfo.TYPE_TEXT_VARIATION_FILTER) {
		        	mPredictionOn = false;
		        } else if (variation == EditorInfo.TYPE_TEXT_VARIATION_WEB_EDIT_TEXT) {
		         // If it's a browser edit field and auto correct is not ON explicitly, then
		         // disable auto correction, but keep suggestions on.
		        	if ((attribute.inputType & EditorInfo.TYPE_TEXT_FLAG_AUTO_CORRECT) == 0) {
		        		disableAutoCorrect = true;
	        		}
		        }

		        // If NO_SUGGESTIONS is set, don't do prediction.
		        if ((attribute.inputType & EditorInfo.TYPE_TEXT_FLAG_NO_SUGGESTIONS) != 0) {
		            mPredictionOn = false;
		            disableAutoCorrect = true;
		        }
		        // If it's not multiline and the autoCorrect flag is not set, then don't correct
		        if ((attribute.inputType & EditorInfo.TYPE_TEXT_FLAG_AUTO_CORRECT) == 0 &&
		        	(attribute.inputType & EditorInfo.TYPE_TEXT_FLAG_MULTI_LINE) == 0) {
		        	disableAutoCorrect = true;
		        }
		        if ((attribute.inputType&EditorInfo.TYPE_TEXT_FLAG_AUTO_COMPLETE) != 0) {
		        	mPredictionOn = false;
		        	mCompletionOn = true && isFullscreenMode();
		        }
		        updateShiftKeyState(attribute);
		        break;
		  default:
		        mKeyboardSwitcher.setKeyboardMode(mKeyboardSwitcher.MODE_TEXT, attribute.imeOptions);
		        if(!mDefaultEnglish) setChnKeyboardMode();
		}
		mInputView.closing();
		mComposing.setLength(0);
        mWord.reset();
		mPredicting = false;
		mDeleteCount = 0;
		       
		
		// Override auto correct
	     if (disableAutoCorrect) {
	    	 mAutoCorrectOn = false;
	         if (mCorrectionMode == Suggest.CORRECTION_FULL) {
	        	 mCorrectionMode = Suggest.CORRECTION_BASIC;
	         }
	     }
		 mInputView.setProximityCorrectionEnabled(true);
		 if (mSuggest != null) {
		     mSuggest.setCorrectionMode(mCorrectionMode);
		 }
		 mPredictionOn = mPredictionOn && mCorrectionMode > 0;
		
		 
		 updateShiftKeyState(getCurrentInputEditorInfo());
		 setCandidatesViewShown(false);
		 
		 if(DEBUG){
			 Log.i("LIMEService","initOnStartInput(): mPredictionOn:"
					 + mPredictionOn 
					 +"; mCorrection: " + mCorrectionMode 
					 +"; disableAutoCorrect: " + disableAutoCorrect
					 );
		 }

	}
	private void loadSettings() {
	        // Get the settings preferences
	        //SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(this);
	        hasVibration = mLIMEPref.getVibrateOnKeyPressed(); //sp.getBoolean("vibrate_on_keypress", false);
			hasSound = mLIMEPref.getSoundOnKeyPressed();// sp.getBoolean("sound_on_keypress", false);
			hasNumberKeypads = mLIMEPref.getShowNumberKeypard();// sp.getBoolean("display_number_keypads", false);
			keyboardSelection = mLIMEPref.getKeyboardSelection();//  sp.getString("keyboard_list", "lime");
			hasQuickSwitch = mLIMEPref.getSwitchEnglishModeHotKey();// sp.getBoolean("switch_english_mode", false);
	        mAutoCap = mLIMEPref.getAutoCaptalization();// sp.getBoolean("auto_cap", true);
	        mQuickFixes = mLIMEPref.getQuickFixes();// sp.getBoolean("quick_fixes", true);
	        mDefaultEnglish = mLIMEPref.getDefaultInEnglish(); //sp.getBoolean("default_in_english", false);
	        // If there is no auto text data, then quickfix is forced to "on", so that the other options
	        // will continue to work
	        if(mInputView !=null) 
	        	{if (AutoText.getSize(mInputView) < 1) mQuickFixes = true;}
	       
	        
	        mShowSuggestions = mLIMEPref.getShowEnlishgSuggestions();// sp.getBoolean("show_suggestions", true) & mQuickFixes;
	        boolean autoComplete = mLIMEPref.getAutoComplete();// sp.getBoolean("auto_complete", mShowSuggestions);
	        mAutoCorrectOn = mSuggest != null && (autoComplete || mQuickFixes);
	        mCorrectionMode = autoComplete
	               ? Suggest.CORRECTION_FULL
	               : (mQuickFixes ? Suggest.CORRECTION_BASIC : Suggest.CORRECTION_NONE);
	        
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
            mWord.reset();
            postUpdateSuggestions();
            if(mPredicting)  {TextEntryState.reset();}
			InputConnection ic = getCurrentInputConnection();
			if (ic != null) {
				ic.finishComposingText();
			}
		
        } else if (!mPredicting && !mJustAccepted
                && TextEntryState.getState() == TextEntryState.STATE_ACCEPTED_DEFAULT) {
            TextEntryState.reset();
        }
        mJustAccepted = false;
        updateShiftKeyState(getCurrentInputEditorInfo());
	}

	
    private boolean isCandidateStripVisible() {
        return isPredictionOn() && mShowSuggestions;
    }
    
	/**
	 * This tells us about completions that the editor has determined based on
	 * the current text in it. We want to use this in fullscreen mode to show
	 * the completions ourself, since the editor can not be seen in that
	 * situation.
	 */
	@Override
	public void onDisplayCompletions(CompletionInfo[] completions) {
		if(DEBUG) Log.i("LIMEService:", "onDisplayCompletions()");
		if (mEnglishOnly && mCompletionOn) {
            mCompletions = completions;
            if (completions == null) {
                setSuggestions(null, false, false);
                return;
            }
            
            LinkedList<Mapping> mappingList = new LinkedList<Mapping>();

			for (int i = 0; i < (completions != null ? completions.length : 0); i++) {
				CompletionInfo ci = completions[i];
				if (ci != null){
					Mapping cv = new Mapping();
					cv.setWord(ci.getText().toString());
					cv.setDictionary(true);
					mappingList.add(cv);
					/*
					try {
						stringList.addAll(SearchSrv.query(ci.getText().toString(), hasKeyPress));
					} catch (RemoteException e) {
						e.printStackTrace();
					}
					*/
				}
			}
			setSuggestions(mappingList, true, true);
        
		}
		
	}

	/**
	 * This translates incoming hard key events in to edit operations on an
	 * InputConnection. It is only needed when using the PROCESS_HARD_KEYS
	 * option.
	 */
	private boolean translateKeyDown(int keyCode, KeyEvent event) {
		
		int c = event.getUnicodeChar(LIMEMetaKeyKeyListener.getMetaState(mMetaState));

		InputConnection ic = getCurrentInputConnection();

		if (c == 0 || ic == null) {
			return false;
		}
		
		// Compact code by Jeremy '10, 3, 27
		if(keyCode == 59){  //Translate shift as -1
			c = -1;
		}
		if (c != -1 && (c & KeyCharacterMap.COMBINING_ACCENT) != 0) {
			c = c & KeyCharacterMap.COMBINING_ACCENT_MASK;
		}
		/*
		if (mComposing.length() > 0) {
			char accent = mComposing.charAt(mComposing.length() - 1);
			int composed = KeyEvent.getDeadChar(accent, c);

			if (composed != 0) {
				c = composed;
				mComposing.setLength(mComposing.length() - 1);
			}
		}*/
		
		int keycode [] ={c};
		onKey(c, keycode);
		return true;
	}

	
	private boolean waitingEnterUp = false;
	/**
	 * Use this to monitor key events being delivered to the application. We get
	 * first crack at them, and can either resume them or let them continue to
	 * the app.
	 */
	@Override
	public boolean onKeyDown(int keyCode, KeyEvent event) {
			
			hasKeyPress = false;
			
			mKeydownEvent = new KeyEvent(event);
			// 	Record key press time (key down, for physical keys)
			if(!keydown ){
				keyPressTime = System.currentTimeMillis();
				keydown =true;
			}
			
			
			waitingEnterUp = false;
			/*
			// Check if user press ALT+@ keys combination then display keyboard picker window
			try{
				if(keyCode == KeyEvent.KEYCODE_ALT_LEFT || keyCode == KeyEvent.KEYCODE_ALT_RIGHT){
					 hasAltPress = true;
				}else if(keyCode == KeyEvent.KEYCODE_AT){
					if(hasAltPress){
						this.showKeyboardPicker();
						hasAltPress = false;
					}else{
						hasAltPress = false;
					}
				}else if(keyCode != KeyEvent.KEYCODE_AT){
					 hasAltPress = false;
				}
				
			
			// Ignore error
			}catch(Exception e){}
			*/
			switch (keyCode) {
			
			// Add by Jeremy '10, 3, 29. DPAD selection on candidate view
			//  UP/Down to page up/down ??
			//case KeyEvent.KEYCODE_DPAD_UP:
			//case KeyEvent.KEYCODE_DPAD_DOWN:
			case KeyEvent.KEYCODE_DPAD_RIGHT:
				if (mCandidateView != null && mCandidateView.isShown()) {
					mCandidateView.selectNext();
					return true;
				}
			case KeyEvent.KEYCODE_DPAD_LEFT:
				if (mCandidateView != null && mCandidateView.isShown()) {
					mCandidateView.selectPrev();
					return true;
				}
			case KeyEvent.KEYCODE_DPAD_CENTER:
				if (mCandidateView != null && mCandidateView.isShown()) {
					mCandidateView.takeSelectedSuggestion();
					return true;
				}
			//Add by Jeremy '10,3,26, process metakey with LIMEMetaKeyKeyListner
			case KeyEvent.KEYCODE_SHIFT_LEFT:
			case KeyEvent.KEYCODE_SHIFT_RIGHT:
			case KeyEvent.KEYCODE_ALT_LEFT:
			case KeyEvent.KEYCODE_ALT_RIGHT:
				mMetaState = LIMEMetaKeyKeyListener.handleKeyDown(mMetaState,
						keyCode, event);
				break;
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
					//mCandidateView.hideComposing();
				}
				mComposing.setLength(0);
	            mWord.reset();
				setCandidatesViewShown(false);
				
				//------------------------------------------------------------------------
				// Remove '10, 3, 26. Replaced with LIMEMetaKeyKeyLister
				// Modified by Jeremy '10, 3,12
				// block milestone alt-del to delete whole line
				// clear alt state before processed by super
				//InputConnection ic = getCurrentInputConnection();
				//if (ic != null){ 
					//ic.clearMetaKeyStates(KeyEvent.META_ALT_ON);
				mMetaState = LIMEMetaKeyKeyListener.adjustMetaAfterKeypress(mMetaState);
				setInputConnectionMetaStateAsCurrentMetaKeyKeyListenerState();
				//------------------------------------------------------------------------
				break;
	
			case KeyEvent.KEYCODE_ENTER:
				// Let the underlying text editor always handle these, if return false from takeSelectedSuggestion().
				// Process enter for candidate view selection in OnKeyUp() to block the real enter afterware.
				//return false;
				if (mCandidateView != null && mCandidateView.isShown()) {
					// To block a real enter after suggestion selection.  We have to return true in OnKeyUp();
					waitingEnterUp = true;
					return mCandidateView.takeSelectedSuggestion();
				}
	
			case KeyEvent.KEYCODE_SPACE:
				// Add by Jeremy '10, 3, 31. Select current suggestion with space. 
				if (mCandidateView != null && mCandidateView.isShown()) {
					if(mCandidateView.takeSelectedSuggestion()){ return true;}// Commit selected suggestion succeed.
					else{ // dismiss the candidate view in the related word selection mode.
						setCandidatesViewShown(false);
						break;
					}
				}
				// Add by Jeremy '10, 3, 27. Send Alt-space to super for default popup SYM window.
				else //if( LIMEMetaKeyKeyListener.getMetaState(mMetaState, LIMEMetaKeyKeyListener.META_ALT_ON)>0 )
					break; 
			case KeyEvent.KEYCODE_AT:
				// do nothing until OnKeyUp
				//if(keyPressTime != 0 && System.currentTimeMillis() - keyPressTime > 700){
				//	switchChiEng();
				//}
				
				return true;
				
			default:
	
				// For all other keys, if we want to do transformations on
				// text being entered with a hard keyboard, we need to process
				// it and do the appropriate action.
				
				//Modified by Jeremy '10, 3, 27. 
				if ( ( (mEnglishOnly && mPredictionOn)|| (!mEnglishOnly && onIM))
						&& translateKeyDown(keyCode, event))				{
					return true;
				}
				
				
			}
	
	
		return super.onKeyDown(keyCode, event);
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
		
		keydown = false;
		
		switch (keyCode) {
		//*/------------------------------------------------------------------------
		// Modified by Jeremy '10, 3,12
		// keep track of alt state with mHasAlt.
		// Modified '10, 3, 24 for bug fix and alc-lock implementation
		case KeyEvent.KEYCODE_SHIFT_LEFT:
			//hasShiftPress = true;
		case KeyEvent.KEYCODE_SHIFT_RIGHT:
			//hasShiftPress = true;
		case KeyEvent.KEYCODE_ALT_LEFT:
		case KeyEvent.KEYCODE_ALT_RIGHT:
			mMetaState = LIMEMetaKeyKeyListener.handleKeyUp(mMetaState,
					keyCode, event);
			
			break;
		case KeyEvent.KEYCODE_ENTER:
			// Add by Jeremy '10, 3 ,29.  Pick selected selection if candidates shown.
			// Does not block real enter after select the suggestion. !! need fix here!!
			// Let the underlying text editor always handle these, if return false from takeSelectedSuggestion().
			//if (mCandidateView != null && mCandidateView.isShown()) {
			//	return mCandidateView.takeSelectedSuggestion();			
			//}		
			if(waitingEnterUp) {return true;};
			// Jeremy '10, 4, 12 bug fix on repeated enter.
			break;
		case KeyEvent.KEYCODE_AT:
			// alt-@ switch to next active keyboard.
			if( LIMEMetaKeyKeyListener.getMetaState(mMetaState, LIMEMetaKeyKeyListener.META_SHIFT_ON)>0 ){
				nextActiveKeyboard();
				mMetaState = LIMEMetaKeyKeyListener.adjustMetaAfterKeypress(mMetaState);
				setInputConnectionMetaStateAsCurrentMetaKeyKeyListenerState();
				return true;
			// Long press physical @ key to swtich chn/eng
			}else if( keyPressTime != 0 && System.currentTimeMillis() - keyPressTime > 700){
				switchChiEng();
				return true;
			//}else if( ( (mEnglishOnly && mPredictionOn)	|| (!mEnglishOnly && onIM))
			}else if( ( mEnglishOnly 	|| (!mEnglishOnly && onIM))
					&& translateKeyDown(keyCode, event)) {
				return true;
			}else{
				super.onKeyDown(keyCode, mKeydownEvent);
			}
		
			break;
		case KeyEvent.KEYCODE_SPACE:
			//hasSpacePress = true;
			
			// If user enable Quick Switch Mode control then check if has Shift+Space combination
			if(hasQuickSwitch){
				//if(hasSpacePress && hasShiftPress){
				if( LIMEMetaKeyKeyListener.getMetaState(mMetaState, LIMEMetaKeyKeyListener.META_SHIFT_ON)>0 ){
					this.switchChiEng();
					//hasShiftPress = false;
					//hasSpacePress = false;
				}//else{
					// If no shift press then reset
					//hasShiftPress = false;
					//hasSpacePress = false;
				//}
			}
			
		default:

			// Reset flags 
			//hasShiftPress = false;
			//hasSpacePress = false;
			
			
		}
		// Update metakeystate of IC maintained by MetaKeyKeyListerner
		setInputConnectionMetaStateAsCurrentMetaKeyKeyListenerState();
		
		if(DEBUG){
		Log.i("OnKeyUp", "keyCode:" + keyCode 
				+ " KeyEvent.Alt_ON:"
				+ String.valueOf(LIMEMetaKeyKeyListener.getMetaState(mMetaState, LIMEMetaKeyKeyListener.META_ALT_ON)) 
				+ " KeyEvent.Shift_ON:"
				+ String.valueOf(LIMEMetaKeyKeyListener.getMetaState(mMetaState, LIMEMetaKeyKeyListener.META_SHIFT_ON))
				);
	
		}
		return super.onKeyUp(keyCode, event);
	}

	/**
	 * Helper function to commit any text being composed in to the editor.
	 */
	private void commitTyped(InputConnection inputConnection) {
		if(DEBUG) Log.i("LIMEService:", "CommittedTyped()");
		try{
			if (mComposing.length() > 0 || (firstMatched != null && firstMatched.isDictionary())) {
	
				if (onIM) {
					if (firstMatched != null && firstMatched.getWord() != null && !firstMatched.getWord().equals("")) {
						int firstMatchedLength = firstMatched.getWord().length();
						
						if (firstMatched.getCode() == null || firstMatched.getCode().equals("")) {
							firstMatchedLength = 1;
						}
						
						String wordToCommit = firstMatched.getWord();
						
						if(firstMatched != null && firstMatched.getCode() != null && firstMatched.getWord() != null){
							if(firstMatched.getCode().toLowerCase().equals(firstMatched.getWord().toLowerCase())){
								firstMatchedLength = 1;
							}
						}
						
						
						if(DEBUG) Log.i("LIMEService","CommitedTyped Length:"+ firstMatchedLength);
						// Do hanConvert before commit
						// '10, 4, 17 Jeremy
						//inputConnection.setComposingText("", 1);
						inputConnection.commitText(SearchSrv.hanConvert(wordToCommit)
								, firstMatchedLength);
						
							try {
								SearchSrv.updateMapping(firstMatched.getId(), 
										firstMatched.getCode(), 
										firstMatched.getWord(), 
										firstMatched.getPword(), 
										firstMatched.getScore(), 
										firstMatched.isDictionary());
							} catch (RemoteException e) {
								e.printStackTrace();
							}
						userdiclist.add(firstMatched);
						// Update userdict for auto-learning feature
						//if(userdiclist.size() > 1) {	updateUserDict();}
						// Add by Jeremy '10, 4,1 . Reverse Lookup
						SearchSrv.rQuery(firstMatched.getWord());
						
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
					//mCandidateView.hideComposing();
				}
				mComposing.setLength(0);
	            mWord.reset();
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
	 public void updateShiftKeyState(EditorInfo attr) {
	        InputConnection ic = getCurrentInputConnection();
	        if (attr != null && mInputView != null 
	        		&& (mKeyboardSwitcher.isAlphabetMode() && !mKeyboardSwitcher.isSymbols())
	                && ic != null) {
	            int caps = 0;
	            EditorInfo ei = getCurrentInputEditorInfo();
	            if (mAutoCap && ei != null && ei.inputType != EditorInfo.TYPE_NULL) {
	                caps = ic.getCursorCapsMode(attr.inputType);
	            }
	            mInputView.setShifted(mCapsLock || caps != 0);
	        }else{
	        	if(!mCapsLock && mHasShift){
	        		mKeyboardSwitcher.toggleShift();
	        		mHasShift =false;
	        	}
	        }
	        
	    }
	 
	public boolean isEnglishOnlyMode(){
		return mEnglishOnly;
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
		//if ( code < 256 && code > 0x20 && checkCode.matches(".*?[^A-Z]") && checkCode.matches(".*?[^a-z]")
		//		&& checkCode.matches(".*?[^0-9]") ) {
		if( (code > 0x21 && code < 0x30 )  //!"#$%&'()*+`0./
			|| (code > 0x39 && code < 0x41 ) // :'<=>?@
			|| (code > 0x5A && code < 0x61 ) // [\]^_'
			|| (code > 0x7A && code < 0x7F ) // {|}~
				){
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
					// If user press space to input the word then system should close the composing view
					mCandidateView.hideComposing();
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

	
	
	public void onKey(int primaryCode, int[] keyCodes) {
		if(DEBUG){
			Log.i("OnKey", "Entering Onkey(); primaryCode:" + primaryCode +" mEnglishFlagShift:" + mEnglishFlagShift);
		}
		long when = SystemClock.uptimeMillis();
        if (primaryCode != Keyboard.KEYCODE_DELETE || 
                when > mLastKeyTime + QUICK_PRESS) {
            mDeleteCount = 0;
        }
        mLastKeyTime = when;
		// Handle English/Lime Keyboard switch
		if(mEnglishFlagShift == false && (primaryCode == Keyboard.KEYCODE_SHIFT) ){
			mEnglishFlagShift = true;
			if(DEBUG){ Log.i("OnKey", "mEnglishFlagShift:" + mEnglishFlagShift);}
		}
		if (primaryCode == Keyboard.KEYCODE_DELETE) {
			handleBackspace();
			mDeleteCount++;
		} else if (primaryCode == Keyboard.KEYCODE_SHIFT) {
			if(DEBUG){ Log.i("OnKey", "KEYCODE_SHIFT");}
			handleShift();
		} else if (primaryCode == Keyboard.KEYCODE_CANCEL) {
			handleClose();
			return;
		// Process long press on options and shift
		} else if (primaryCode == LIMEKeyboardView.KEYCODE_OPTIONS) {
			handleOptions();
		} else if (primaryCode == LIMEKeyboardView.KEYCODE_SHIFT_LONGPRESS) {
			if(DEBUG){ Log.i("OnKey", "KEYCODE_SHIFT_LONGPRESS");}
			
			//postShiftLongPressed();
			mInputView.closing();
			if (mCapsLock){
                handleShift();
            } else {
                toggleCapsLock();
            }
            
			 
		} else if (primaryCode == Keyboard.KEYCODE_MODE_CHANGE && mInputView != null) {
			switchKeyboard(primaryCode);
		}  else if (primaryCode == -9 && mInputView != null) {
			switchKeyboard(primaryCode);
		} else if (onIM && (primaryCode == 32 || primaryCode ==10) ) {
			if (mComposing.length() > 0) {
				commitTyped(getCurrentInputConnection());
			}
			sendKey(primaryCode);
			updateShiftKeyState(getCurrentInputEditorInfo());
		} else if (mEnglishOnly && isWordSeparator(primaryCode)) {
            handleSeparator(primaryCode);
		} else{
			handleCharacter(primaryCode, keyCodes);
		}
		// Cancel the just reverted state
        mJustRevertedSeparator = null;
		
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
    private void nextActiveKeyboard(){
    	
    	buildActiveKeyboardList();
    	int i;
    	CharSequence keyboardname = "";
    	for(i=0;i<keyboardListCodes.size();i++){
        	if(keyboardSelection.equals(keyboardListCodes.get(i))){ 
        		if(i==keyboardListCodes.size()-1){
        			keyboardSelection = keyboardListCodes.get(0);
        			keyboardname = keyboardList.get(0);
        		}else{
        			keyboardSelection = keyboardListCodes.get(i+1);
        			keyboardname = keyboardList.get(i+1);
        		}     		
        		break;
        	}
        }
    	  // cancel candidate view if it's shown
        if (mCandidateView != null) {
			mCandidateView.clear();
			//mCandidateView.hideComposing();
		}
		mComposing.setLength(0);
        mWord.reset();
		setCandidatesViewShown(false);		
		setChnKeyboardMode();
    	Toast.makeText(this, keyboardname , Toast.LENGTH_SHORT).show();
    }
    
    
    private void buildActiveKeyboardList(){
    	CharSequence[] items = getResources().getStringArray(R.array.keyboard);
    	CharSequence[] codes = getResources().getStringArray(R.array.keyboard_codes);
    	//SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(this);
    	//String keybaord_state_string = sp.getString("keyboard_state", "0;1;2;3;4;5;6");
    	String keybaord_state_string = mLIMEPref.getSelectedKeyboardState();
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
		
		//check if the selected keybaord is in active keybaord list.
		boolean matched = false;
		for( int i=0; i<keyboardListCodes.size(); i++){
			if(keyboardSelection.equals(keyboardListCodes.get(i))){
				matched = true;
				break;
			}
		}
		if(!matched){
			// if the selected keyboard is not in the active keyboard list.
			// set the keyboard to the first active keyboard
			keyboardSelection = keyboardListCodes.get(0);
		}
    	
    }
    /**
     * Add by Jeremy '10, 3, 24 for keyboard picker menu in options menu
     */
    private void showKeyboardPicker(){
    	
    	buildActiveKeyboardList();
    	
    	AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setCancelable(true);
        builder.setIcon(R.drawable.sym_keyboard_done);
        builder.setNegativeButton(android.R.string.cancel, null);
        builder.setTitle(getResources().getString(R.string.keyboard_list));
        
        CharSequence[] items  = new CharSequence[keyboardList.size()];//= getResources().getStringArray(R.array.keyboard);
        int curKB=0;
        for(int i=0;i<keyboardList.size();i++){
        	items[i]=keyboardList.get(i);
        	if(keyboardSelection.equals(keyboardListCodes.get(i))) 
        		curKB = i;
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
        // Jeremy '10, 4, 12
        // The IM is not initialialized. do nothing here if window=null.
        if(!(window == null)){
        	WindowManager.LayoutParams lp = window.getAttributes();
        	lp.token = mInputView.getWindowToken();
        	lp.type = WindowManager.LayoutParams.TYPE_APPLICATION_ATTACHED_DIALOG;
        	window.setAttributes(lp);
        	window.addFlags(WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM);
        	
        }
        mOptionsDialog.show();
        
        
    }
    
    private void handlKeyboardSelection(int position){
    	//SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(this);
        //SharedPreferences.Editor spe = sp.edit();

        keyboardSelection = keyboardListCodes.get(position);
        
        //spe.putString("keyboard_list", keyboardSelection);
        //spe.commit();
        mLIMEPref.setKeyboardSelection(keyboardSelection);
        
        // cancel candidate view if it's shown
        if (mCandidateView != null) {
			mCandidateView.clear();
			//mCandidateView.hideComposing();
		}
		mComposing.setLength(0);
        mWord.reset();
		setCandidatesViewShown(false);
        
		setChnKeyboardMode();
    	
    }
    
	public void onText(CharSequence text) {
		if(DEBUG) Log.i("LIMEService:", "OnText()");
		InputConnection ic = getCurrentInputConnection();
		if (ic == null)
			return;
		ic.beginBatchEdit();
		
        if (mPredicting) {
            commitTyped(ic);
            mJustRevertedSeparator = null;
        } else if (onIM){
       
        	if (mComposing.length() > 0) {
        		commitTyped(ic);
        	}
        	if (firstMatched != null) {
        		ic.commitText(this.firstMatched.getWord(), 0);
					try {
						SearchSrv.updateMapping(firstMatched.getId(), 
							firstMatched.getCode(), 
							firstMatched.getWord(), 
							//firstMatched.getPcode(), 
							firstMatched.getPword(), 
							firstMatched.getScore(), 
							firstMatched.isDictionary());
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
	
	 private void updateSuggestions() {
	        // Check if we have a suggestion engine attached.
	        if (mSuggest == null || !isPredictionOn()) {
	            return;
	        }
	        
	        if (!mPredicting) {
	            setSuggestions(null, false, false);
	            return;
	        }
	        List<Mapping> suggestions = mSuggest.getSuggestions(mInputView, mWord, false);
	        boolean correctionAvailable = mSuggest.hasMinimalCorrection();
	        //|| mCorrectionMode == mSuggest.CORRECTION_FULL;
	        CharSequence typedWord = mWord.getTypedWord();
	        // If we're in basic correct
	        boolean typedWordValid = mSuggest.isValidWord(typedWord) ||
	                (preferCapitalization() && mSuggest.isValidWord(typedWord.toString().toLowerCase()));
	        if (mCorrectionMode == Suggest.CORRECTION_FULL) {
	            correctionAvailable |= typedWordValid;
	        }
	        // Don't auto-correct words with multiple capital letter
	        correctionAvailable &= !mWord.isMostlyCaps();
	        
	        //if(suggestions != null && suggestions.size() > 0) {setCandidatesViewShown(true);}	
			
	        setSuggestions(suggestions, false, typedWordValid, correctionAvailable); 
	        if (suggestions.size() > 0) {
	            if (correctionAvailable && !typedWordValid && suggestions.size() > 1) {
	                mBestWord = suggestions.get(1).getWord();
	            } else {
	                mBestWord = typedWord;
	            }
	        } else {
	            mBestWord = null;
	        }
	        setCandidatesViewShown(isCandidateStripVisible() || mCompletionOn);
	    }
	
	/**
	 * Update the list of available candidates from the current composing text.
	 * This will need to be filled in by however you are determining candidates.
	 */
	private void updateCandidates() {

		if (mCandidateView != null) {
			mCandidateView.clear();
			//mCandidateView.hideComposing();
		}
		
		//if (!mCompletionOn && mComposing.length() > 0) {
		if (mComposing.length() > 0) {	
			LinkedList<Mapping> list = new LinkedList<Mapping>();
			
			try {
				String keyString = mComposing.toString(), charString="";
				
				list.addAll(SearchSrv.query(keyString, hasKeyPress));
	
				if(list.size() > 0){
					setSuggestions(list, true, true);
				}else{
					setSuggestions(null, false, false);
				}
				// Show composing window if keyToChar got different string.
				if(keyString!=null && !keyString.equals(""))
					charString = SearchSrv.keyToChar(keyString.toUpperCase());
				if (mCandidateView != null && !charString.toUpperCase().equals(keyString.toUpperCase())&&!charString.equals(""))
					{mCandidateView.setComposingText(charString);}
				else
					{mCandidateView.setComposingText("");}
	
			} catch (RemoteException e) {
				e.printStackTrace();
			}
			
		}
		
	}

	/*
	 * Update dictionary view
	 */
	private void updateDictionaryView() {
		
		// If there is no Temp Matched word exist then not to display dictionary view
		try{
			// Modified by Jeremy '10, 4,1.  getCode -> getWord
			//if( tempMatched != null && tempMatched.getCode() != null && !tempMatched.getCode().equals("")){
			if( tempMatched != null && tempMatched.getWord() != null && !tempMatched.getWord().equals("")){
				
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
		setSuggestions(suggestions, completions, typedWordValid, false);
	}
	
	public void setSuggestions(List<Mapping> suggestions, boolean completions,
			boolean typedWordValid, boolean haveMinimalSuggestion) {
		
		
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
				mCandidateView.setSuggestions(suggestions, completions, typedWordValid, haveMinimalSuggestion);
			}
		}else{
			if (mCandidateView != null) {
				setCandidatesViewShown(false);
				//mCandidateView.hideComposing();
			}
			
		}

	}

	private void handleBackspace() {

		final int length = mComposing.length();
		boolean deleteChar = false;
        InputConnection ic = getCurrentInputConnection();
        if (ic == null) return;
        if (mEnglishOnly){
        	if(mPredicting) {  
        		if (length > 0) {
        			mComposing.delete(length - 1, length);
        			mWord.deleteLast();
        			ic.setComposingText(mComposing, 1);
        		}
        		if (mComposing.length() == 0) {
                    mPredicting = false;
                	}
                postUpdateSuggestions();
            } else {
                ic.deleteSurroundingText(1, 0);
            }
        	TextEntryState.backspace();
            if (TextEntryState.getState() == TextEntryState.STATE_UNDO_COMMIT) {
                revertLastWord(deleteChar);
                return;
            } 
            mJustRevertedSeparator = null;
       
        } else if (onIM) {
        	if (length > 1) {
        		mComposing.delete(length - 1, length);
        		getCurrentInputConnection().setComposingText(mComposing, 1);
        		postUpdateSuggestions();
        	} else if (length == 1) {
        		// '10, 4, 5 Jeremy. Bug fix on delete last key in buffer.
        		getCurrentInputConnection().setComposingText("",0);
        		if (mCandidateView != null) {
        			mCandidateView.clear();
				//mCandidateView.hideComposing();
        		}
        		mComposing.setLength(0);
                mWord.reset();
        		setCandidatesViewShown(false);
        		getCurrentInputConnection().commitText("", 0);
        	} else {
        		if (mCandidateView != null) {
				mCandidateView.clear();
				//mCandidateView.hideComposing();
        		}
        		mComposing.setLength(0);
                mWord.reset();
        		setCandidatesViewShown(false);
        		//keyDownUp(KeyEvent.KEYCODE_DEL);
        		deleteChar = true;
        	}
        } else {
            deleteChar = true;
        }
        	
        if (deleteChar) {
            sendDownUpKeyEvents(KeyEvent.KEYCODE_DEL);
            if (mDeleteCount > DELETE_ACCELERATE_AT) {
                sendDownUpKeyEvents(KeyEvent.KEYCODE_DEL);
            }
        }
     
       
		//updateShiftKeyState(getCurrentInputEditorInfo());
		
	}
	
	public void revertLastWord(boolean deleteChar) {
        final int length = mComposing.length();
        if (!mPredicting && length > 0) {
            final InputConnection ic = getCurrentInputConnection();
            mPredicting = true;
            ic.beginBatchEdit();
            mJustRevertedSeparator = ic.getTextBeforeCursor(1, 0);
            if (deleteChar) ic.deleteSurroundingText(1, 0);
            int toDelete = mCommittedLength;
            CharSequence toTheLeft = ic.getTextBeforeCursor(mCommittedLength, 0);
            if (toTheLeft != null && toTheLeft.length() > 0 
                    && isWordSeparator(toTheLeft.charAt(0))) {
                toDelete--;
            }
            ic.deleteSurroundingText(toDelete, 0);
            ic.setComposingText(mComposing, 1);
            TextEntryState.backspace();
            ic.endBatchEdit();
            postUpdateSuggestions();
        } else {
            sendDownUpKeyEvents(KeyEvent.KEYCODE_DEL);
            mJustRevertedSeparator = null;
        }
    }
	private void postUpdateSuggestions() {
        mHandler.removeMessages(MSG_UPDATE_SUGGESTIONS);
        mHandler.sendMessageDelayed(mHandler.obtainMessage(MSG_UPDATE_SUGGESTIONS), 100);
    }
	
	private void postShiftLongPressed() {
        mHandler.removeMessages(MSG_SHIFT_LONGPRESSED);
        mHandler.sendMessageDelayed(mHandler.obtainMessage(MSG_SHIFT_LONGPRESSED), 100);
    }
	
	public void setCandidatesViewShown(boolean shown){
		super.setCandidatesViewShown(shown);
		if (mCandidateView != null) {
			if(shown) mCandidateView.showComposing();
			else mCandidateView.hideComposing();
		}
	}

	
	private void handleShift() {

		if (mInputView == null) { return; }
		
		if ((mKeyboardSwitcher.isAlphabetMode()&&!mKeyboardSwitcher.isSymbols())
				||(keyboardSelection.equals("lime") && !hasNumberKeypads &&mKeyboardSwitcher.isChinese())) {
            // Alphabet keyboard
            checkToggleCapsLock();
            mInputView.setShifted(mCapsLock || !mInputView.isShifted());
            mHasShift = mCapsLock || !mInputView.isShifted();
        } else {
        	if(mCapsLock){
        		 toggleCapsLock();
        		 mHasShift = false;
        	}else if(mHasShift){
        		toggleCapsLock();
       		 	mHasShift = true;
        	}else{
        		mKeyboardSwitcher.toggleShift();
        		mHasShift = mKeyboardSwitcher.isShifted();
        		
        	}
        }
	}

	private void switchKeyboard(int primaryCode) {
		
		if(mInputView==null) return;
		
		if(mCapsLock) toggleCapsLock();
			if (primaryCode == Keyboard.KEYCODE_MODE_CHANGE) {
			switchSymKeyboard();
		}else if(primaryCode == KEYBOARD_SWITCH_CODE){
			if (mCandidateView != null) {
				mCandidateView.clear();
			}
			mComposing.setLength(0);
            mWord.reset();
			setCandidatesViewShown(false);
			switchChiEng();
		}
		
		mHasShift = false;
		updateShiftKeyState(getCurrentInputEditorInfo());
		
	}
	private void switchSymKeyboard(){
		// Switch Keyboard between Symbol and Lime
		
		mKeyboardSwitcher.toggleSymbols();

	}
	
	private void switchChiEng() {
		
		
		mKeyboardSwitcher.toggleChinese();
		mEnglishOnly = !mKeyboardSwitcher.isChinese();
		

		if (mEnglishOnly) {
			onIM = false;	
			Toast.makeText(this, R.string.typing_mode_english, Toast.LENGTH_SHORT).show();	
			
		} else {
			onIM = true;
			Toast.makeText(this, R.string.typing_mode_mixed, Toast.LENGTH_SHORT).show();		
		}
		
}
	private void setChnKeyboardMode() {
		
		int mMode = mKeyboardSwitcher.MODE_TEXT_DEFAULT;
		if (keyboardSelection.equals("lime")) {
			if (hasNumberKeypads) {
				mMode = mKeyboardSwitcher.MODE_TEXT_DEFAULT_NUMBER;
			} else {
				mMode = mKeyboardSwitcher.MODE_TEXT_DEFAULT;
			}
			hasNumberMapping = mLIMEPref.getAllowNumberMapping();
			hasSymbolMapping = mLIMEPref.getAllowSymoblMapping();
			
		} else if (keyboardSelection.equals("cj")) {
			if (hasNumberKeypads) {
				mMode = mKeyboardSwitcher.MODE_TEXT_CJ_NUMBER;
			}else{
				mMode = mKeyboardSwitcher.MODE_TEXT_CJ;
			}
			hasNumberMapping = mLIMEPref.getAllowNumberMapping();
			hasSymbolMapping = mLIMEPref.getAllowSymoblMapping();
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
		} else if (keyboardSelection.equals("array")) {
			mMode = mKeyboardSwitcher.MODE_TEXT_ARRAY;
			// Should use number and symbol mapping
			hasNumberMapping = true;
			hasSymbolMapping = true;
		} else if (keyboardSelection.equals("phone")) {
			mMode = mKeyboardSwitcher.MODE_TEXT_PHONE;
			// Should use number and symbol mapping
			hasNumberMapping = true;
			hasSymbolMapping = true;
		}
	
		mKeyboardSwitcher.setKeyboardMode(mMode, mImeOptions);
		// Set db table name.	
		try {
			String tablename = new String(keyboardSelection);
			if(tablename.equals("lime") || tablename.equals("phone") ){
				tablename = "custom";
			}
			SearchSrv.setTablename(tablename);
		} catch (RemoteException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}
	
	 private boolean isCursorTouchingWord() {
	        InputConnection ic = getCurrentInputConnection();
	        if (ic == null) return false;
	        CharSequence toLeft = ic.getTextBeforeCursor(1, 0);
	        CharSequence toRight = ic.getTextAfterCursor(1, 0);
	        if (!TextUtils.isEmpty(toLeft)
	                && !isWordSeparator(toLeft.charAt(0))) {
	            return true;
	        }
	        if (!TextUtils.isEmpty(toRight) 
	                && !isWordSeparator(toRight.charAt(0))) {
	            return true;
	        }
	        return false;
	}
	    
	/**
	 * This method construct candidate view and add key code to composing object
	 * @param primaryCode
	 * @param keyCodes
	 */
	private void handleCharacter(int primaryCode, int[] keyCodes) {
		
		if(DEBUG){
			Log.i("LIMEService", "Entering handleCharacter(); primaryCode:" + primaryCode 
					+ " ;mEnglishOnly: " + mEnglishOnly
					+ " ;mPredictionOn: "+ isPredictionOn()
					);
		}
        
		
		// Caculate key press time to handle Eazy IM keys mapping
		// 1,2,3,4,5,6 map to -(45) =(43) [(91) ](93) ,(44) \(92)
		if(keyPressTime != 0 && (System.currentTimeMillis() - keyPressTime > 700) 
				&&  mKeyboardSwitcher.getKeyboardMode()==mKeyboardSwitcher.MODE_TEXT_EZ){
			if(primaryCode == 49){
				primaryCode = 45;
			}else if(primaryCode == 50){
				primaryCode = 61;
			}else if(primaryCode == 51){
				primaryCode = 91;
			}else if(primaryCode == 52){
				primaryCode = 93;
			}else if(primaryCode == 53){
				primaryCode = 44;
			}else if(primaryCode == 54){
				primaryCode = 92;
			}
		}
		
		// Adjust metakeystate on printed key pressed.
		mMetaState = LIMEMetaKeyKeyListener.adjustMetaAfterKeypress(mMetaState);
		
		// If keyboard type = phone then check the user selection
		if( keyboardSelection.equals("phone")){
			handleChacacterPhoneMode(primaryCode, keyCodes);
			
		}else{
			if (!mEnglishOnly) {
				// Shift keyboard already sent uppercase characters
				if (isInputViewShown()&&keyboardSelection.equals("lime")) {
					if (mInputView.isShifted()) {
						primaryCode = Character.toUpperCase(primaryCode);
					}
				}
				
				if(DEBUG){
					Log.i("HandleCharacter",
							"isValidLetter:" +isValidLetter(primaryCode)
							+" isValidDigit:" + isValidDigit(primaryCode)
							+" isValideSymbo:" + isValidSymbol(primaryCode)
							+" onIM:"+ onIM
							);
				}
	
				if (!hasSymbolMapping && !hasNumberMapping
						&& isValidLetter(primaryCode) && onIM) {
					mComposing.append((char) primaryCode);
					getCurrentInputConnection().setComposingText(mComposing, 1);
					//updateShiftKeyState(getCurrentInputEditorInfo());
					postUpdateSuggestions();
					misMatched = mComposing.toString();
				} else if (!hasSymbolMapping
						&& hasNumberMapping
						&& (isValidLetter(primaryCode) || isValidDigit(primaryCode))
						&& onIM) {
					mComposing.append((char) primaryCode);
					getCurrentInputConnection().setComposingText(mComposing, 1);
					//updateShiftKeyState(getCurrentInputEditorInfo());
					postUpdateSuggestions();
					misMatched = mComposing.toString();
				} else if (hasSymbolMapping
						&& !hasNumberMapping
						&& (isValidLetter(primaryCode) || isValidSymbol(primaryCode))
						&& onIM) {
					mComposing.append((char) primaryCode);
					getCurrentInputConnection().setComposingText(mComposing, 1);
					//updateShiftKeyState(getCurrentInputEditorInfo());
					postUpdateSuggestions();
					misMatched = mComposing.toString();
				} else if (hasSymbolMapping
						&& hasNumberMapping
						&& (isValidSymbol(primaryCode)
								|| isValidLetter(primaryCode) || isValidDigit(primaryCode))
						&& onIM) {
					mComposing.append((char) primaryCode);
					getCurrentInputConnection().setComposingText(mComposing, 1);
					//updateShiftKeyState(getCurrentInputEditorInfo());
					postUpdateSuggestions();
					misMatched = mComposing.toString();
				} else {
					getCurrentInputConnection().commitText(
							mComposing + String.valueOf((char) primaryCode), 1);
				}
			} else { // English 
				if (Character.isLetter(primaryCode) && isPredictionOn() && !isCursorTouchingWord()) {
		            if (!mPredicting) {
		                mPredicting = true;
		                mComposing.setLength(0);
		                mWord.reset();
		            }
		        }
				
				if (isInputViewShown()) {
					if (mInputView.isShifted()) {
			            if (keyCodes == null || keyCodes[0] < Character.MIN_CODE_POINT
			                    || keyCodes[0] > Character.MAX_CODE_POINT) {
			                return;
			            }
			            primaryCode = new String(keyCodes, 0, 1).toUpperCase().charAt(0);
			        }
				}
				if (mPredicting) {
		            if (mInputView.isShifted() && mComposing.length() == 0) {
		                mWord.setCapitalized(true);
		            }
		            mComposing.append((char) primaryCode);
		            mWord.add(primaryCode, keyCodes);
		            InputConnection ic = getCurrentInputConnection();
		            if (ic != null) {
		                ic.setComposingText(mComposing, 1);
		            }
		            postUpdateSuggestions();
		        } else {
		            sendKeyChar((char)primaryCode);
		        }
				
		        TextEntryState.typedCharacter((char) primaryCode, isWordSeparator(primaryCode));
			}
		}
		//updateShift(primaryCode);
		updateShiftKeyState(getCurrentInputEditorInfo());
	}
	
	private void handleChacacterPhoneMode(int primaryCode, int[] keyCodes) {
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
				postUpdateSuggestions();
				misMatched = mComposing.toString();
			} else if (!hasSymbolMapping
					&& hasNumberMapping
					&& (isValidLetter(primaryCode) || isValidDigit(primaryCode))
					&& onIM) {
				mComposing.append((char) primaryCode);
				getCurrentInputConnection().setComposingText(mComposing, 1);
				//updateShiftKeyState(getCurrentInputEditorInfo());
				postUpdateSuggestions();
				misMatched = mComposing.toString();
			} else if (hasSymbolMapping
					&& !hasNumberMapping
					&& (isValidLetter(primaryCode) || isValidSymbol(primaryCode))
					&& onIM) {
				mComposing.append((char) primaryCode);
				getCurrentInputConnection().setComposingText(mComposing, 1);
				//updateShiftKeyState(getCurrentInputEditorInfo());
				postUpdateSuggestions();
				misMatched = mComposing.toString();
			} else if (hasSymbolMapping
					&& hasNumberMapping
					&& (isValidSymbol(primaryCode)
							|| isValidLetter(primaryCode) || isValidDigit(primaryCode))
					&& onIM) {
				mComposing.append((char) primaryCode);
				getCurrentInputConnection().setComposingText(mComposing, 1);
				//updateShiftKeyState(getCurrentInputEditorInfo());
				postUpdateSuggestions();
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
		}
	}
	
	private void handleClose() {
		
		// cancel candidate view if it's shown
        if (mCandidateView != null) {
			mCandidateView.clear();
		}
		mComposing.setLength(0);
        mWord.reset();
		//setCandidatesViewShown(false);
		TextEntryState.endSession();
		
		requestHideSelf(0);
		mInputView.closing();
	}

	private void checkToggleCapsLock() {
		
		if (mInputView.isShifted()) {
            toggleCapsLock();
        }
		
    }
    
    private void toggleCapsLock() {
        mCapsLock = !mCapsLock;
        if ((mKeyboardSwitcher.isAlphabetMode()&&!mKeyboardSwitcher.isSymbols())
        		||(keyboardSelection.equals("lime") && !hasNumberKeypads && mKeyboardSwitcher.isChinese())) {
            ((LIMEKeyboard) mInputView.getKeyboard()).setShiftLocked(mCapsLock);
            mInputView.setKeyboard(mInputView.getKeyboard());//instead of invalidateAllKeys();
        }else  {
        	if(mCapsLock){
        		if(DEBUG){ Log.i("toggleCapsLock", "mCapsLock:true");}
        		if(!mKeyboardSwitcher.isShifted())	
        					mKeyboardSwitcher.toggleShift();
        		if(mKeyboardSwitcher.isShifted())
        					((LIMEKeyboard) mInputView.getKeyboard()).setShiftLocked(true);
        	}
        	else{
        		if(DEBUG){ Log.i("toggleCapsLock", "mCapsLock:false");}
        		if(mKeyboardSwitcher.isShifted()){
        			((LIMEKeyboard) mInputView.getKeyboard()).setShiftLocked(false);
        			mKeyboardSwitcher.toggleShift();
        		}
        		
        	}
        }
    }
    
    protected String getWordSeparators() {
        return mWordSeparators;
    }

	public boolean isWordSeparator(int code) {
		String separators = getWordSeparators();
        return separators.contains(String.valueOf((char)code));
	}
	
	public boolean isSentenceSeparator(int code) {
	        return mSentenceSeparators.contains(String.valueOf((char)code));
	}
	
	public void pickDefaultCandidate() {
		// Complete any pending candidate query first
        if (mHandler.hasMessages(MSG_UPDATE_SUGGESTIONS)) {
            mHandler.removeMessages(MSG_UPDATE_SUGGESTIONS);
            updateSuggestions();
        }
        if(mEnglishOnly) {
        	if (mBestWord != null) {
        		TextEntryState.acceptedDefault(mWord.getTypedWord(), mBestWord);
        		mJustAccepted = true;
        		pickSuggestion(mBestWord);
        	}
        }else
        	pickSuggestionManually(0);
	}
	
    public boolean preferCapitalization() {
        return mWord.isCapitalized();
    }
    
	private void pickSuggestion(CharSequence suggestion) {
        if (mCapsLock) {
            suggestion = suggestion.toString().toUpperCase();
        } else if (preferCapitalization() 
                || ( (mKeyboardSwitcher.isAlphabetMode() && !mKeyboardSwitcher.isSymbols()) 
                		&& mInputView.isShifted())) {
            suggestion = suggestion.toString().toUpperCase().charAt(0)
                    + suggestion.subSequence(1, suggestion.length()).toString();
        }
        InputConnection ic = getCurrentInputConnection();
        if (ic != null) {
            ic.commitText(suggestion, 1);
        }
        // Add the word to the auto dictionary if it's not a known word
        if (mAutoDictionary.isValidWord(suggestion) || !mSuggest.isValidWord(suggestion)) {
            mAutoDictionary.addWord(suggestion.toString(), FREQUENCY_FOR_PICKED);
        }
        mPredicting = false;
        mCommittedLength = suggestion.length();
        if (mCandidateView != null) {
            setSuggestions(null, false, false);
        }
        updateShiftKeyState(getCurrentInputEditorInfo());
    }
	
	private void handleSeparator(int primaryCode) {
        boolean pickedDefault = false;
        // Handle separator
        InputConnection ic = getCurrentInputConnection();
        if (ic != null) {
            ic.beginBatchEdit();
        }
        if (mPredicting) {
            // In certain languages where single quote is a separator, it's better
            // not to auto correct, but accept the typed word. For instance, 
            // in Italian dov' should not be expanded to dove' because the elision
            // requires the last vowel to be removed.
            if (mAutoCorrectOn && primaryCode != '\'' && 
                    (mJustRevertedSeparator == null 
                            || mJustRevertedSeparator.length() == 0 
                            || mJustRevertedSeparator.charAt(0) != primaryCode)) {
                pickDefaultCandidate();
                pickedDefault = true;
            } else {
                commitTyped(ic);
            }
        }
        sendKeyChar((char)primaryCode);
        TextEntryState.typedCharacter((char) primaryCode, true);
        if (TextEntryState.getState() == TextEntryState.STATE_PUNCTUATION_AFTER_ACCEPTED 
                && primaryCode != KEYCODE_ENTER) {
            swapPunctuationAndSpace();
        } else if (isPredictionOn() && primaryCode == ' ') { 
        //else if (TextEntryState.STATE_SPACE_AFTER_ACCEPTED) {
            doubleSpace();
        }
        if (pickedDefault && mBestWord != null) {
            TextEntryState.acceptedDefault(mWord.getTypedWord(), mBestWord);
        }
        updateShiftKeyState(getCurrentInputEditorInfo());
        if (ic != null) {
            ic.endBatchEdit();
        }
    }
	

    private boolean isPredictionOn() {
        boolean predictionOn = mPredictionOn;
        //if (isFullscreenMode()) predictionOn &= mPredictionLandscape;
        return predictionOn;
    }
	private void swapPunctuationAndSpace() {
        final InputConnection ic = getCurrentInputConnection();
        if (ic == null) return;
        CharSequence lastTwo = ic.getTextBeforeCursor(2, 0);
        if (lastTwo != null && lastTwo.length() == 2
                && lastTwo.charAt(0) == KEYCODE_SPACE && isSentenceSeparator(lastTwo.charAt(1))) {
            ic.beginBatchEdit();
            ic.deleteSurroundingText(2, 0);
            ic.commitText(lastTwo.charAt(1) + " ", 1);
            ic.endBatchEdit();
            updateShiftKeyState(getCurrentInputEditorInfo());
        }
    }
	
	private void doubleSpace() {
        //if (!mAutoPunctuate) return;
        if (mCorrectionMode == Suggest.CORRECTION_NONE) return;
        final InputConnection ic = getCurrentInputConnection();
        if (ic == null) return;
        CharSequence lastThree = ic.getTextBeforeCursor(3, 0);
        if (lastThree != null && lastThree.length() == 3
                && Character.isLetterOrDigit(lastThree.charAt(0))
                && lastThree.charAt(1) == KEYCODE_SPACE && lastThree.charAt(2) == KEYCODE_SPACE) {
            ic.beginBatchEdit();
            ic.deleteSurroundingText(2, 0);
            ic.commitText(". ", 1);
            ic.endBatchEdit();
            updateShiftKeyState(getCurrentInputEditorInfo());
        }
    }
	public void pickSuggestionManually(int index) {
		if(DEBUG) Log.i("LIMEService:", "pickSuggestionManually()");
		// After pick word from the list system have to close the composing view
		mCandidateView.hideComposing();
		
		if (templist != null) {
			firstMatched = templist.get(index);
		}
		
		if(mEnglishOnly){
			if (mCompletionOn && mCompletions != null && index >= 0
					&& index < mCompletions.length) {
				CompletionInfo ci = mCompletions[index];
				InputConnection ic = getCurrentInputConnection();
				if (ic != null) {
					ic.commitCompletion(ci);
				}
				mCommittedLength = firstMatched.getWord().length();
				if (mCandidateView != null) {
					mCandidateView.clear();
				}
				updateShiftKeyState(getCurrentInputEditorInfo());
				return;
			}
			pickSuggestion(firstMatched.getWord());
			TextEntryState.acceptedSuggestion(mComposing.toString(), firstMatched.getWord());
			// Follow it with a space
			if (mAutoSpace) {
				sendSpace();
			}
			// Fool the state watcher so that a subsequent backspace will not do a revert
			TextEntryState.typedCharacter((char) KEYCODE_SPACE, true);
		 
		}else {

			
			if (mComposing.length() > 0) {
				commitTyped(getCurrentInputConnection());
				this.firstMatched = null;
				this.hasFirstMatched = false;
				updateDictionaryView();
			} else if (firstMatched != null && firstMatched.isDictionary()) {
				commitTyped(getCurrentInputConnection());
				updateDictionaryView();
			}
		}
		//setCandidatesViewShown(false);
		
	}
	
	private void sendSpace() {
	        sendKeyChar((char)KEYCODE_SPACE);
	        updateShiftKeyState(getCurrentInputEditorInfo());
	        //onKey(KEY_SPACE[0], KEY_SPACE);
	}
	
	void promoteToUserDictionary(String word, int frequency) {
        if (mUserDictionary.isValidWord(word)) return;
        mUserDictionary.addWord(word, frequency);
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

	class AutoDictionary extends ExpandableDictionary {
        // If the user touches a typed word 2 times or more, it will become valid.
        private static final int VALIDITY_THRESHOLD = 2 * FREQUENCY_FOR_PICKED;
        // If the user touches a typed word 5 times or more, it will be added to the user dict.
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
            if (length < 2 || length > getMaxWordLength()) return;
            super.addWord(word, addFrequency);
            final int freq = getWordFrequency(word);
            if (freq > PROMOTION_THRESHOLD) {
                LIMEService.this.promoteToUserDictionary(word, FREQUENCY_FOR_AUTO_ADD);
            }
        }
    }
}
