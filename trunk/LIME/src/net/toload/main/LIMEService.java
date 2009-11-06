
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

import android.inputmethodservice.InputMethodService;
import android.inputmethodservice.Keyboard;
import android.inputmethodservice.KeyboardView;
import android.media.AudioManager;
import android.os.Vibrator;
import android.preference.PreferenceManager;
import android.text.method.MetaKeyKeyListener;
import android.util.Log;
import android.view.KeyCharacterMap;
import android.view.KeyEvent;
import android.view.View;
import android.view.inputmethod.CompletionInfo;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputConnection;
import android.view.inputmethod.InputMethodManager;

import java.util.ArrayList;
import java.util.List;


import android.app.Service;
import android.content.Context;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.database.sqlite.SQLiteCursor;

/**
 * @author Art Hung
 */
public class LIMEService extends InputMethodService 
        implements KeyboardView.OnKeyboardActionListener {
	
    static final boolean DEBUG = false;

    static final boolean PROCESS_HARD_KEYS = true;
    
    private KeyboardView mInputView;
    private CandidateView mCandidateView;
    private CompletionInfo[] mCompletions;
    
    private StringBuilder mComposing = new StringBuilder();
    
    private boolean mPredictionOn;
    private boolean mCompletionOn;
    private boolean mCapsLock;
    private boolean mHasShift;
	private boolean onIM = false;
	private boolean hasFirstMatched = false;
	
    private long mLastShiftTime;
    private long mMetaState;

    private int mLastDisplayWidth;
    
    private LIMEKeyboard mSymbolsKeyboard;
    private LIMEKeyboard mSymbolsShiftedKeyboard;
    private LIMEKeyboard mLimeKeyboard;
    private LIMEKeyboard mLimeNumberKeyboard;
    private LIMEKeyboard mLimeNumberShiftKeyboard;
    
    private LIMEKeyboard mCurKeyboard;

    private Mapping firstMatched;
    private Mapping tempMatched;
    private LimeDB limedb;
    
    private String mWordSeparators;
    private String misMatched;
    
    private ArrayList<Mapping> templist;
    private ArrayList<Mapping> inputlist;
    
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
	private boolean hasAutoLearn = false;
	private boolean hasAutoDictionary = false;
	
    /**
     * Main initialization of the input method component.  Be sure to call
     * to super class.
     */
    @Override public void onCreate() {
    	
        super.onCreate();
        
		// Initial and Create Database
		limedb = new LimeDB(this);
		mVibrator = (Vibrator) getApplication().getSystemService(Service.VIBRATOR_SERVICE);
		mAudioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
		
        SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(this);
        
		hasVibration = sp.getBoolean("vibrate_on_keypress", false);
		hasSound = sp.getBoolean("sound_on_keypress", false);
		hasNumberKeypads = sp.getBoolean("display_number_keypads", false);
		hasNumberMapping = sp.getBoolean("accept_number_index", false);
		hasSymbolMapping = sp.getBoolean("accept_symbol_index", false);
		hasAutoLearn = sp.getBoolean("candidate_learning", false);
		hasAutoDictionary = sp.getBoolean("candidate_dictionary", false);
		
		// initial Input List
		inputlist = new ArrayList<Mapping>();
    }
   
    /**
     * This is the point where you can do all of your UI initialization.  It
     * is called after creation and any configuration change.
     */
    @Override public void onInitializeInterface() {
        if (mLimeKeyboard != null) {
            int displayWidth = getMaxWidth();
            if (displayWidth == mLastDisplayWidth) return;
            mLastDisplayWidth = displayWidth;
        }
        
        mLimeKeyboard = new LIMEKeyboard(this, R.xml.lime);
        mLimeNumberKeyboard = new LIMEKeyboard(this, R.xml.lime_number);
        mLimeNumberShiftKeyboard = new LIMEKeyboard(this, R.xml.lime_number_shift);
        
        mSymbolsKeyboard = new LIMEKeyboard(this, R.xml.symbols);
        mSymbolsShiftedKeyboard = new LIMEKeyboard(this, R.xml.symbols_shift);
    }
    
    /**
     * Called by the framework when your view for creating input needs to
     * be generated.  This will be called the first time your input method
     * is displayed, and every time it needs to be re-created such as due to
     * a configuration change.
     */
    @Override public View onCreateInputView() {
        mInputView = (KeyboardView) getLayoutInflater().inflate(
                R.layout.input, null);
        mInputView.setOnKeyboardActionListener(this);
        if(hasNumberKeypads){
            mInputView.setKeyboard(mLimeNumberKeyboard);
        }else{
            mInputView.setKeyboard(mLimeKeyboard);
        }
        onIM = true;
        return mInputView;
    }

    /**
     * Called by the framework when your view for showing candidates needs to
     * be generated, like {@link #onCreateInputView}.
     */
    @Override public View onCreateCandidatesView() {
		       mCandidateView = new CandidateView(this);
		       mCandidateView.setService(this);
        return mCandidateView;

    }

    /**
     * This is the main point where we do our initialization of the input method
     * to begin operating on an application.  At this point we have been
     * bound to the client, and are now receiving all of the detailed information
     * about the target of our edits.
     */
    @Override public void onStartInput(EditorInfo attribute, boolean restarting) {
        super.onStartInput(attribute, restarting);
        
		inputlist = new ArrayList<Mapping>();
		templist = new ArrayList<Mapping>();
		firstMatched = new Mapping();
		tempMatched = new Mapping();
		
        setCandidatesViewShown(false);
        
        // Reset our state.  We want to do this even if restarting, because
        // the underlying state of the text editor could have changed in any way.
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
        switch (attribute.inputType&EditorInfo.TYPE_MASK_CLASS) {
            case EditorInfo.TYPE_CLASS_NUMBER:
            case EditorInfo.TYPE_CLASS_DATETIME:
            case EditorInfo.TYPE_CLASS_PHONE:
                mCurKeyboard = mSymbolsKeyboard;
                onIM = false;
                break;
            case EditorInfo.TYPE_CLASS_TEXT:
            	if(hasNumberKeypads){
                    mCurKeyboard = mLimeNumberKeyboard;
            	}else{
                    mCurKeyboard = mLimeKeyboard;
            	}
                mPredictionOn = true;
                onIM = true;
                
                // We now look for a few special variations of text that will
                // modify our behavior.
                int variation = attribute.inputType &  EditorInfo.TYPE_MASK_VARIATION;
                if (variation == EditorInfo.TYPE_TEXT_VARIATION_PASSWORD ||
                        variation == EditorInfo.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD) {
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
                updateShiftKeyState(attribute);
                break;
                
            default:
                // For all unknown input types, default to the alphabetic
                // keyboard with no special features.
            	if(hasNumberKeypads){
                    mCurKeyboard = mLimeNumberKeyboard;
            	}else{
                    mCurKeyboard = mLimeKeyboard;
            	}
            	onIM = true; 
                updateShiftKeyState(attribute);
        }
        
        // Update the label on the enter key, depending on what the application
        // says it will do.
        mCurKeyboard.setImeOptions(getResources(), attribute.imeOptions);
    }

    /**
     * This is called when the user is done editing a field.  We can use
     * this to reset our state.
     */
    @Override public void onFinishInput() {
    	
        super.onFinishInput();
        
        
        if(hasNumberKeypads){
            mCurKeyboard = mLimeNumberKeyboard;
        }else{
            mCurKeyboard = mLimeKeyboard;
        }
        if (mInputView != null) {
            mInputView.closing();
        }
        
        // Clear current composing text and candidates.
        mComposing.setLength(0);
        updateCandidates();
        
        setCandidatesViewShown(false);
        onIM = false;
        
        // Add Dictionary
        if(hasAutoDictionary){
            limedb.addDictionary(inputlist);
        }
        
        // Reset Inputlist
        inputlist = new ArrayList<Mapping>();
        
        this.setSuggestions(null, false, false);
    }
    
    @Override public void onStartInputView(EditorInfo attribute, boolean restarting) {
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
		hasAutoLearn = sp.getBoolean("candidate_learning", false);
		hasAutoDictionary = sp.getBoolean("candidate_dictionary", false);

    }
    
    /**
     * Deal with the editor reporting movement of its cursor.
     */
    @Override public void onUpdateSelection(int oldSelStart, int oldSelEnd,
            int newSelStart, int newSelEnd,
            int candidatesStart, int candidatesEnd) {
        super.onUpdateSelection(oldSelStart, oldSelEnd, newSelStart, newSelEnd,
                candidatesStart, candidatesEnd);
        
        // If the current selection in the text view changes, we should
        // clear whatever candidate text we have.
        if (mComposing.length() > 0 && (newSelStart != candidatesEnd
                || newSelEnd != candidatesEnd)) {
            mComposing.setLength(0);
            updateCandidates();
            InputConnection ic = getCurrentInputConnection();
            if (ic != null) {
                ic.finishComposingText();
            }
        }
    }

    /**
     * This tells us about completions that the editor has determined based
     * on the current text in it.  We want to use this in fullscreen mode
     * to show the completions ourself, since the editor can not be seen
     * in that situation.
     */
    @Override public void onDisplayCompletions(CompletionInfo[] completions) {
        if (mCompletionOn) {
            mCompletions = completions;
            if (completions == null) {
                setSuggestions(null, false, false);
                return;
            }
            
            ArrayList<Mapping> stringList = new ArrayList<Mapping>();
            for (int i=0; i<(completions != null ? completions.length : 0); i++) {
                CompletionInfo ci = completions[i];
                if (ci != null) stringList.addAll(limedb.getSuggestions(ci.getText().toString()));
                //(ci.getText().toString());
            }
            setSuggestions(stringList, true, true);
        }
    }
    
    /**
     * This translates incoming hard key events in to edit operations on an
     * InputConnection.  It is only needed when using the
     * PROCESS_HARD_KEYS option.
     */
    private boolean translateKeyDown(int keyCode, KeyEvent event) {
        mMetaState = MetaKeyKeyListener.handleKeyDown(mMetaState,
                keyCode, event);
        int c = event.getUnicodeChar(MetaKeyKeyListener.getMetaState(mMetaState));
        mMetaState = MetaKeyKeyListener.adjustMetaAfterKeypress(mMetaState);
        InputConnection ic = getCurrentInputConnection();
        if (c == 0 || ic == null) {
            return false;
        }
        
        boolean dead = false;

        if ((c & KeyCharacterMap.COMBINING_ACCENT) != 0) {
            dead = true;
            c = c & KeyCharacterMap.COMBINING_ACCENT_MASK;
        }
        
        if (mComposing.length() > 0) {
            char accent = mComposing.charAt(mComposing.length() -1 );
            int composed = KeyEvent.getDeadChar(accent, c);

            if (composed != 0) {
                c = composed;
                mComposing.setLength(mComposing.length()-1);
            }
        }
        //Log.i("ART","b mComposing.length():"+mComposing.length());
        
        onKey(c, null);
        
        return true;
    }
    
    /**
     * Use this to monitor key events being delivered to the application.
     * We get first crack at them, and can either resume them or let them
     * continue to the app.
     */
    @Override public boolean onKeyDown(int keyCode, KeyEvent event) {

    	//Log.i("ART","o1");
        switch (keyCode) {
            case KeyEvent.KEYCODE_BACK:
            	//Log.i("ART","o2");
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
            	//Log.i("ART","o3");
                // Special handling of the delete key: if we currently are
                // composing text for the user, we want to modify that instead
                // of let the application to the delete itself.
                if (mComposing.length() > 0) {
                    onKey(Keyboard.KEYCODE_DELETE, null);
                    return true;
                }
                break;
                
            case KeyEvent.KEYCODE_ENTER:
            	//Log.i("ART","o4");
                // Let the underlying text editor always handle these.
                return false;
                
            default:
                // For all other keys, if we want to do transformations on
                // text being entered with a hard keyboard, we need to process
                // it and do the appropriate action.
            	
                if (PROCESS_HARD_KEYS) {
                    if (keyCode == KeyEvent.KEYCODE_SPACE
                            && (event.getMetaState()&KeyEvent.META_ALT_ON) != 0) {
                    	//Log.i("ART","o5");
                        // A silly example: in our input method, Alt+Space
                        // is a shortcut for 'android' in lower case.
                        InputConnection ic = getCurrentInputConnection();
                        if (ic != null) {
                            // First, tell the editor that it is no longer in the
                            // shift state, since we are consuming this.
                        	//Log.i("ART","o6");
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
                    }
                    if (mPredictionOn && translateKeyDown(keyCode, event)) {
                        return true;
                    }
                }
        }
        
        return super.onKeyDown(keyCode, event);
    }

    /**
     * Use this to monitor key events being delivered to the application.
     * We get first crack at them, and can either resume them or let them
     * continue to the app.
     */
    @Override public boolean onKeyUp(int keyCode, KeyEvent event) {
        // If we want to do transformations on text being entered with a hard
        // keyboard, we need to process the up events to update the meta key
        // state we are tracking.
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
    	
        if (mComposing.length() > 0 || (firstMatched != null && firstMatched.isDictionary())) {
        	
        	//Log.i("ART","1");
        	if(onIM){
            	//Log.i("ART","2");
            	if(firstMatched != null && !firstMatched.getWord().equals("")){
                	//Log.i("ART","3");
            		int firstMatchedLength = firstMatched.getWord().length();
            		if(firstMatched.getCode() == null || firstMatched.getCode().equals("")){
            			firstMatchedLength = 1;
            		}
            		inputConnection.commitText(firstMatched.getWord(), firstMatchedLength);
                    if(hasAutoLearn){
                		limedb.addScore(firstMatched);
                    }
                    inputlist.add(firstMatched);
                	
                	tempMatched = firstMatched;
                    firstMatched = null;
                    hasFirstMatched = true;

            	}else if(firstMatched != null && firstMatched.getWord().equals("")){
                	//Log.i("ART","4");
            		inputConnection.commitText(misMatched, misMatched.length());
                    firstMatched = null;
                    hasFirstMatched = false;

                	inputlist.add(null);
            	}else{
                	//Log.i("ART","5");
                    inputConnection.commitText(mComposing, mComposing.length());
                    hasFirstMatched = false;
                	inputlist.add(null);
            	}
        	}else{
            	//Log.i("ART","6");
                inputConnection.commitText(mComposing, mComposing.length());
                hasFirstMatched = false;
            	inputlist.add(null);
        	}
        	
            updateCandidates();
        }
    }

    /**
     * Helper to update the shift state of our keyboard based on the initial
     * editor state.
     */
    private void updateShiftKeyState(EditorInfo attr) {
        if (attr != null 
                && mInputView != null && 
                (mLimeKeyboard == mInputView.getKeyboard() || 
                		mLimeNumberKeyboard == mInputView.getKeyboard())) {
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
    	String checkCode = String.valueOf((char)code);
		if(checkCode.matches(".*?[^A-Z]") && checkCode.matches(".*?[^a-z]") 
				&& checkCode.matches(".*?[^0-9]")
				&& code != 32
				&& code != 39){
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
            	
            	//Log.i("ART","SPACE:"+1);
            	if(keyCode == 32 && firstMatched == null && !hasFirstMatched){
                	//Log.i("ART","SPACE:"+2);
            	    getCurrentInputConnection().commitText(String.valueOf((char) keyCode), 1);
            	}else{
                	//Log.i("ART","SPACE:"+3);
            		if(keyCode != 32 && firstMatched != null && !hasFirstMatched){
                    	//Log.i("ART","SPACE:"+4);
            			getCurrentInputConnection().commitText(String.valueOf((char) keyCode), 1);
                    }else if(keyCode != 32){
                    	//Log.i("ART","SPACE:"+5);
                    	getCurrentInputConnection().commitText(String.valueOf((char) keyCode), 1);
                    }else if(keyCode == 32 && (!hasFirstMatched) ){
                    	//Log.i("ART","SPACE:"+6);
                    	getCurrentInputConnection().commitText(String.valueOf((char) keyCode), 1);                        
                    }else if(keyCode == 32 && this.mComposing.length() == 0){
                    	getCurrentInputConnection().commitText(String.valueOf((char) keyCode), 1);
                    }
            		hasFirstMatched = false;
            	}
            	
                break;
        }
    }

    // Implementation of KeyboardViewListener

    public void onKey(int primaryCode, int[] keyCodes) {
    	
        if (isWordSeparator(primaryCode)) {
        	//Log.i("ART","###########"+primaryCode);
            if (mComposing.length() > 0) {
            	//Log.i("ART","#1");
                commitTyped(getCurrentInputConnection());
            }
            sendKey(primaryCode);
            updateShiftKeyState(getCurrentInputEditorInfo());
        } else if (primaryCode == Keyboard.KEYCODE_DELETE) {
            handleBackspace();
        } else if (primaryCode == Keyboard.KEYCODE_SHIFT) {
            handleShift();
        } else if (primaryCode == Keyboard.KEYCODE_CANCEL) {
            handleClose();
            return;
        } else if (primaryCode == LIMEKeyboardView.KEYCODE_OPTIONS) {
            // Show a menu or somethin'
        } else if (primaryCode == Keyboard.KEYCODE_MODE_CHANGE && mInputView != null) {
            Keyboard current = mInputView.getKeyboard();

            if(hasNumberKeypads){
            	Log.i("ART","a");
            	if(current == mLimeNumberKeyboard){
                	//Log.i("ART","b");
                	current = mSymbolsKeyboard;
                	onIM = false;
                }else if(current == mSymbolsKeyboard || current == mSymbolsShiftedKeyboard){
                	//Log.i("ART","c");
                	current = mLimeNumberKeyboard;
                	onIM = true;
                }else{
                	//Log.i("ART","d");
                	current = mLimeNumberKeyboard;
                	onIM = true;
                }
            }else{
            	if(current == mLimeKeyboard){
                	//Log.i("ART","e");
                	current = mSymbolsKeyboard;
                	onIM = false;
                }else if(current == mSymbolsKeyboard || current == mSymbolsShiftedKeyboard){
                	//Log.i("ART","f");
                	current = mLimeKeyboard;
                	onIM = true;
                }else{
                	//Log.i("ART","g");
                	current = mLimeKeyboard;
                	onIM = true;
                }
            }

        	//Log.i("ART","h");
            
            mInputView.setKeyboard(current);
            if (current == mSymbolsKeyboard) {
                current.setShifted(false);
            }
        } else {
        	//Log.i("ART","i");
            handleCharacter(primaryCode, keyCodes);
        }
        
    }

    public void onText(CharSequence text) {
    	
        InputConnection ic = getCurrentInputConnection();
        if (ic == null) return;
        ic.beginBatchEdit();
        if (mComposing.length() > 0) {
            commitTyped(ic);
        }
        if(firstMatched != null){
            ic.commitText(this.firstMatched.getWord(), 0);
            if(hasAutoLearn){
                limedb.addScore(firstMatched);
            }
        }else{
        	ic.commitText(text, 0);
        }
        ic.endBatchEdit();
        updateShiftKeyState(getCurrentInputEditorInfo());
        
    }

    /**
     * Update the list of available candidates from the current composing
     * text.  This will need to be filled in by however you are determining
     * candidates.
     */
    private void updateCandidates() {
        if (!mCompletionOn) {
            if (mComposing.length() > 0) {
                ArrayList<Mapping> list = new ArrayList<Mapping>();
                //list.add(mComposing.toString());
                
                list.addAll(limedb.getSuggestions(mComposing.toString()));
                
                setSuggestions(list, true, true);
            } else {
                if(hasAutoDictionary){
	                ArrayList<Mapping> list = new ArrayList<Mapping>();
	                if(tempMatched != null){
	                    list.addAll(limedb.getDictionary(tempMatched.getCode()));
	                    tempMatched = null;
	                }
	                if(list.size() > 0){
	            		templist = (ArrayList)list;
	                	setSuggestions(list, true, true);
	                }else{
	                	setSuggestions(null, false, false);
	                }
                }else{
                	setSuggestions(null, false, false);
                }
            }
        }
        
    }
    
    public void setSuggestions(List<Mapping> suggestions, boolean completions, boolean typedWordValid) {
    	
    	/**
        if (suggestions != null && suggestions.size() > 0) {
            setCandidatesViewShown(true);
        } else if (isExtractViewShown()) {
            setCandidatesViewShown(true);
        }**/

        if (suggestions != null && suggestions.size() > 0) {
            setCandidatesViewShown(true);
        }
        if (mCandidateView != null) {
        	if(suggestions != null){
        		templist = (ArrayList)suggestions;
        		try{
		        	if(suggestions.size() == 1){
			        	firstMatched = suggestions.get(0);
		        	}else if(suggestions.size() > 1){
			        	firstMatched = suggestions.get(1);
        			}else{
			        	firstMatched = null;
		        	}
        		}catch(Exception e){e.printStackTrace();}
        	}
            mCandidateView.setSuggestions(suggestions, completions, typedWordValid);
        }
        
    }
    
    private void handleBackspace() {
    	
        final int length = mComposing.length();
        if (length > 1) {
            mComposing.delete(length - 1, length);
            getCurrentInputConnection().setComposingText(mComposing, 1);
            updateCandidates();
        } else if (length > 0) {
            mComposing.setLength(0);
            getCurrentInputConnection().commitText("", 0);
            updateCandidates();
        } else {
            keyDownUp(KeyEvent.KEYCODE_DEL);
        }
        updateShiftKeyState(getCurrentInputEditorInfo());
        
    }

    private void handleShift() {
    	
        if (mInputView == null) { return; }
        
        Keyboard currentKeyboard = mInputView.getKeyboard();

        
        if(hasNumberKeypads){

        	if (mLimeNumberKeyboard == currentKeyboard || mLimeNumberShiftKeyboard == currentKeyboard) {
                
        		checkToggleCapsLock();
                
                if(mHasShift && mLimeNumberKeyboard == currentKeyboard){
                    mInputView.setKeyboard(mLimeNumberShiftKeyboard);
                	mLimeNumberShiftKeyboard.setShifted(true);
                }else if( !mHasShift && mLimeNumberShiftKeyboard == currentKeyboard){
                	mInputView.setKeyboard(mLimeNumberKeyboard);
                	mLimeNumberKeyboard.setShifted(false);
                }
                
            } else if (currentKeyboard == mSymbolsKeyboard) {
                mSymbolsKeyboard.setShifted(true);
                mInputView.setKeyboard(mSymbolsShiftedKeyboard);
                mSymbolsShiftedKeyboard.setShifted(true);
            } else if (currentKeyboard == mSymbolsShiftedKeyboard) {
                mSymbolsShiftedKeyboard.setShifted(false);
                mInputView.setKeyboard(mSymbolsKeyboard);
                mSymbolsKeyboard.setShifted(false);
            }
        	
        }else{

        	if (mLimeKeyboard == currentKeyboard) {
                checkToggleCapsLock();
                mInputView.setShifted(mCapsLock || !mInputView.isShifted());
            } else if (currentKeyboard == mSymbolsKeyboard) {
                mSymbolsKeyboard.setShifted(true);
                mInputView.setKeyboard(mSymbolsShiftedKeyboard);
                mSymbolsShiftedKeyboard.setShifted(true);
            } else if (currentKeyboard == mSymbolsShiftedKeyboard) {
                mSymbolsShiftedKeyboard.setShifted(false);
                mInputView.setKeyboard(mSymbolsKeyboard);
                mSymbolsKeyboard.setShifted(false);
            }
        	
        }
        
        
    }
    
    private void handleCharacter(int primaryCode, int[] keyCodes) {

	    //Log.i("ART","a1");
        if (isInputViewShown()) {
    	    //Log.i("ART","a2");
            if (mInputView.isShifted()) {
                primaryCode = Character.toUpperCase(primaryCode);
            }
        }
        
        if (!hasSymbolMapping && !hasNumberMapping && isValidLetter(primaryCode) && onIM){
    	    //Log.i("ART","a3");
            mComposing.append((char) primaryCode);
            getCurrentInputConnection().setComposingText(mComposing, 1);
            updateShiftKeyState(getCurrentInputEditorInfo());
            updateCandidates();
            misMatched = mComposing.toString();
        }else if(!hasSymbolMapping && hasNumberMapping && (isValidLetter(primaryCode) || isValidDigit(primaryCode)) && onIM){
    	    //Log.i("ART","a4");
    	    mComposing.append((char) primaryCode);
            getCurrentInputConnection().setComposingText(mComposing, 1);
            updateShiftKeyState(getCurrentInputEditorInfo());
            updateCandidates();
            misMatched = mComposing.toString();
        }else if(hasSymbolMapping && !hasNumberMapping && (isValidLetter(primaryCode) || isValidSymbol(primaryCode)) && onIM){
    	    //Log.i("ART","a5");
    	    mComposing.append((char) primaryCode);
            getCurrentInputConnection().setComposingText(mComposing, 1);
            updateShiftKeyState(getCurrentInputEditorInfo());
            updateCandidates();
            misMatched = mComposing.toString();
        }else if(hasSymbolMapping && hasNumberMapping && (isValidSymbol(primaryCode) || isValidLetter(primaryCode) || isValidDigit(primaryCode)) && onIM){
    	    //Log.i("ART","a6");
    	    mComposing.append((char) primaryCode);
            getCurrentInputConnection().setComposingText(mComposing, 1);
            updateShiftKeyState(getCurrentInputEditorInfo());
            updateCandidates();
            misMatched = mComposing.toString();
    	}else{
    	    //Log.i("ART","a7");
            getCurrentInputConnection().commitText(
            		mComposing+String.valueOf((char) primaryCode), 1);
        }
        	
    }

    private void handleClose() {
    	
        commitTyped(getCurrentInputConnection());
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
    	
    	//String checkCode = String.valueOf((char)code);
		if( code == 32 || code == 39 || code == 10){
			return true;
        } else {
        	return false;
        }
		
    }

    public void pickDefaultCandidate() {
        pickSuggestionManually(0);
    }
    
    public void pickSuggestionManually(int index) {

    	if(templist != null){
    		firstMatched = templist.get(index);
    	}
        if (mCompletionOn && mCompletions != null && index >= 0
                && index < mCompletions.length) {
            CompletionInfo ci = mCompletions[index];
            getCurrentInputConnection().commitCompletion(ci);
            if (mCandidateView != null) {
                mCandidateView.clear();
            }
            updateShiftKeyState(getCurrentInputEditorInfo());
        } else if (mComposing.length() > 0) {
            commitTyped(getCurrentInputConnection());
        } else if (firstMatched != null && firstMatched.isDictionary()){
            commitTyped(getCurrentInputConnection());
        }
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
    
    public void onPress(int primaryCode) {
    	if(hasVibration){
        	mVibrator.vibrate(40);
    	}
    	if(hasSound){
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
    }
    
    public void onRelease(int primaryCode) {
    }
    
    
}
