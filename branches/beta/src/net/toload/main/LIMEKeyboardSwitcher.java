/*
 * Copyright (C) 2008 Google Inc.
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

import java.util.HashMap;
import java.util.Map;

public class LIMEKeyboardSwitcher {

    public static final int MODE_TEXT = 1;
    public static final int MODE_TEXT_DEFAULT = 10;
    public static final int MODE_TEXT_DEFAULT_NUMBER = 11;
    public static final int MODE_TEXT_CJ = 12;
    public static final int MODE_TEXT_CJ_NUMBER = 13;
    public static final int MODE_TEXT_PHONETIC = 14;
    public static final int MODE_TEXT_DAYI = 15;
    public static final int MODE_TEXT_EZ = 16;
    public static final int MODE_TEXT_PHONE = 17;
    public static final int MODE_SYMBOLS = 2;
    public static final int MODE_PHONE = 3;
    public static final int MODE_URL = 4;
    public static final int MODE_EMAIL = 5;
    public static final int MODE_IM = 6;
    
    public static final int MODE_TEXT_QWERTY = 0;
    public static final int MODE_TEXT_ALPHA = 1;
    public static final int MODE_TEXT_COUNT = 2;
    

    public static final int KEYBOARDMODE_NORMAL = R.id.mode_normal;
    public static final int KEYBOARDMODE_URL = R.id.mode_url;
    public static final int KEYBOARDMODE_EMAIL = R.id.mode_email;
    public static final int KEYBOARDMODE_IM = R.id.mode_im;
    
    public static final int IM_KEYBOARD = 0;
    
   

    private static final int SYMBOLS_MODE_STATE_NONE = 0;
    private static final int SYMBOLS_MODE_STATE_BEGIN = 1;
    private static final int SYMBOLS_MODE_STATE_SYMBOL = 2;

    LIMEKeyboardView mInputView;
    LIMEService mContext;
    
    

    private KeyboardId mCurrentId;
    private Map<KeyboardId, LIMEKeyboard> mKeyboards;
    
    private int mMode = KEYBOARDMODE_NORMAL;
    private int mChnMode = MODE_TEXT_DEFAULT;
    private int mEngMode = MODE_TEXT;
    private int mImeOptions;
    private int mTextMode = MODE_TEXT_QWERTY;
    
    private boolean mIsShifted;
    private boolean mIsSymbols;
    private boolean mIsChinese=true;
    private boolean mIsAlphabet;
    private boolean mPreferSymbols;
    private int mSymbolsModeState = SYMBOLS_MODE_STATE_NONE;

    private int mLastDisplayWidth;

    LIMEKeyboardSwitcher(LIMEService context) {
        mContext = context;
        mKeyboards = new HashMap<KeyboardId, LIMEKeyboard>();
     }

    void setInputView(LIMEKeyboardView inputView) {
        mInputView = inputView;
    }
    
    void makeKeyboards(boolean forceCreate) {
        if (forceCreate) mKeyboards.clear();
        // Configuration change is coming after the keyboard gets recreated. So don't rely on that.
        // If keyboards have already been made, check if we have a screen width change and 
        // create the keyboard layouts again at the correct orientation
        int displayWidth = mContext.getMaxWidth();
        if (displayWidth == mLastDisplayWidth) return;
        mLastDisplayWidth = displayWidth;
        if (!forceCreate) mKeyboards.clear();
     }

    /**
     * Represents the parameters necessary to construct a new LatinKeyboard,
     * which also serve as a unique identifier for each keyboard type.
     */
    private static class KeyboardId {
        public int mXml;
        public int mMode;
        public boolean mEnableShiftLock;

        public KeyboardId(int xml, int mode, boolean enableShiftLock) {
            this.mXml = xml;
            this.mMode = mode;
            this.mEnableShiftLock = enableShiftLock;
        }

        public KeyboardId(int xml) {
            this(xml, 0, false);
        }

        public boolean equals(Object other) {
            return other instanceof KeyboardId && equals((KeyboardId) other);
        }

        public boolean equals(KeyboardId other) {
            return other.mXml == this.mXml && other.mMode == this.mMode;
        }

        public int hashCode() {
            return (mXml + 1) * (mMode + 1) * (mEnableShiftLock ? 2 : 1);
        }
    }

    void setKeyboardMode(int mode, int imeOptions) {
        mSymbolsModeState = SYMBOLS_MODE_STATE_NONE;
        mPreferSymbols = mode == MODE_SYMBOLS;
        mIsChinese = (mode == MODE_TEXT_DEFAULT || mode == MODE_TEXT_PHONETIC ||mode == MODE_TEXT_DAYI
        		||mode == MODE_TEXT_EZ ||mode == MODE_TEXT_CJ || mode == MODE_TEXT_PHONE);
        mIsAlphabet = ( mode == MODE_TEXT || mode== MODE_URL || mode == MODE_EMAIL || mode == MODE_IM );
        if(mIsChinese) mChnMode = mode;
        if(mIsAlphabet) mEngMode = mode;
        
        if(mIsChinese){
        	setKeyboardMode(mode == MODE_SYMBOLS ? mChnMode : mode, imeOptions,
                mPreferSymbols, false);
        	}
        else{
        	setKeyboardMode(mode == MODE_SYMBOLS ? mEngMode : mode, imeOptions,
                    mPreferSymbols, false);
        }
    }

    void setKeyboardMode(int mode, int imeOptions, boolean isSymbols, boolean isShift) {
    	
    	if(mInputView == null) return;
        
        mImeOptions = imeOptions;
        mIsSymbols = isSymbols;
        mIsShifted = isShift;
        //mInputView.setPreviewEnabled(true);
        KeyboardId id = getKeyboardId(mode, imeOptions, isSymbols, mIsShifted);
        LIMEKeyboard keyboard = getKeyboard(id);

        if (mode == MODE_PHONE) {
            mInputView.setPhoneKeyboard(keyboard);
            //mInputView.setPreviewEnabled(true);
        }

        mCurrentId = id;
        mInputView.setKeyboard(keyboard);
        keyboard.setShifted(mIsShifted);
        if(isAlphabetMode()||(isChinese()&& mChnMode == MODE_TEXT_DEFAULT)){ 
        	keyboard.setShiftLocked(keyboard.isShiftLocked());
        	mInputView.setKeyboard(mInputView.getKeyboard()); //instead of invalidateAllKeys();
        }
        keyboard.setImeOptions(mContext.getResources(), mMode, imeOptions);

    }

    private LIMEKeyboard getKeyboard(KeyboardId id) {
        if (!mKeyboards.containsKey(id)) {
        	LIMEKeyboard keyboard = new LIMEKeyboard(
                mContext, id.mXml, id.mMode);
            if (id.mEnableShiftLock) {
                keyboard.enableShiftLock();
            }
            mKeyboards.put(id, keyboard);
        }
        return mKeyboards.get(id);
    }

    private KeyboardId getKeyboardId(int mode, int imeOptions, boolean isSymbols, boolean isShifted) {
        if (isSymbols) {
        	if(isShifted) return new KeyboardId(R.xml.symbols_shift, 0, true );
        	else return new KeyboardId(R.xml.symbols);
        }

        switch (mode) {
            case MODE_TEXT:
                return new KeyboardId(R.xml.lime_english, KEYBOARDMODE_NORMAL, true);
            case MODE_TEXT_DEFAULT:
                return new KeyboardId(R.xml.lime, 0, true);
            case MODE_TEXT_DEFAULT_NUMBER:
                if(isShifted) return new KeyboardId(R.xml.lime_number_shift, 0, true );    
                else return new KeyboardId(R.xml.lime_number);
            case MODE_TEXT_CJ:
                if(isShifted) return new KeyboardId(R.xml.lime_cj_shift, 0, true);    
                else return new KeyboardId(R.xml.lime_cj);
            case MODE_TEXT_CJ_NUMBER:
                if(isShifted) return new KeyboardId(R.xml.lime_cj_number_shift, 0, true);    
                else return new KeyboardId(R.xml.lime_cj_number);
            case MODE_TEXT_DAYI:
                if(isShifted) return new KeyboardId(R.xml.lime_dayi_shift, 0, true);    
                else return new KeyboardId(R.xml.lime_dayi);
            case MODE_TEXT_PHONETIC:
                if(isShifted) return new KeyboardId(R.xml.lime_phonetic_shift, 0, true);    
                else return new KeyboardId(R.xml.lime_phonetic);
            case MODE_TEXT_EZ:
                if(isShifted) return new KeyboardId(R.xml.lime_ez_shift, 0, true);    
                else return new KeyboardId(R.xml.lime_ez);
            case MODE_TEXT_PHONE:
                return new KeyboardId(R.xml.phone, 0, false);
            case MODE_SYMBOLS:
            	if(isShifted) return new KeyboardId(R.xml.symbols_shift, 0, true);
            	else return new KeyboardId(R.xml.symbols);
            case MODE_PHONE:
                return new KeyboardId(R.xml.phone);
            case MODE_URL:
                return new KeyboardId(R.xml.lime_english, KEYBOARDMODE_URL, true);
            case MODE_EMAIL:
                return new KeyboardId(R.xml.lime_english, KEYBOARDMODE_EMAIL, true);
            case MODE_IM:
            	return new KeyboardId(R.xml.lime_english, KEYBOARDMODE_IM, true);
        }
        return null;
    }

    int getKeyboardMode() {
        return mMode;
    }
    
    boolean isTextMode() {
        return mMode == MODE_TEXT;
    }
    
    int getTextMode() {
        return mTextMode;
    }
    
    void setTextMode(int position) {
        if (position < MODE_TEXT_COUNT && position >= 0) {
            mTextMode = position;
        }
        if (isTextMode()) {
            setKeyboardMode(MODE_TEXT, mImeOptions);
        }
    }

    int getTextModeCount() {
        return MODE_TEXT_COUNT;
    }

    boolean isAlphabetMode() {
    	return mIsAlphabet;
    	
    	/*KeyboardId current = mCurrentId;
        return current.mMode == KEYBOARDMODE_NORMAL
            || current.mMode == KEYBOARDMODE_URL
            || current.mMode == KEYBOARDMODE_EMAIL
            || current.mMode == KEYBOARDMODE_IM;
        */
    }

    void toggleShift() {
    	mIsShifted= !mIsShifted;
    	if(mIsChinese)
    		setKeyboardMode(mChnMode, mImeOptions, mIsSymbols, mIsShifted);
    	else{
    		setKeyboardMode(mEngMode, mImeOptions, mIsSymbols, mIsShifted);
    	}

    }
    
    void toggleChinese() {
    	//mIsChinese = !mIsChinese;
    	//mIsShifted = false;
    	if(!mIsChinese)
    		setKeyboardMode(mChnMode, mImeOptions);//, mIsSymbols, mIsShifted);
    	else
    		setKeyboardMode(mEngMode, mImeOptions);//, mIsSymbols, mIsShifted);
    }
    
    void toggleSymbols() {
    	mIsSymbols = !mIsSymbols;
    	mIsShifted = false;
    	if(mIsChinese)
    		setKeyboardMode(mChnMode, mImeOptions, mIsSymbols, mIsShifted);
    	else
    		setKeyboardMode(mEngMode, mImeOptions, mIsSymbols, mIsShifted);
        if (mIsSymbols && !mPreferSymbols) {
            mSymbolsModeState = SYMBOLS_MODE_STATE_BEGIN;
        } else {
            mSymbolsModeState = SYMBOLS_MODE_STATE_NONE;
        }
    }
    public boolean isChinese(){
    	return mIsChinese;
    }
    public boolean isSymbols(){
    	return mIsSymbols;
    }
    public boolean isShifted(){
    	return mIsShifted;
    }
    /**
     * Updates state machine to figure out when to automatically switch back to alpha mode.
     * Returns true if the keyboard needs to switch back 
     */
    boolean onKey(int key) {
        // Switch back to alpha mode if user types one or more non-space/enter characters
        // followed by a space/enter
        switch (mSymbolsModeState) {
            case SYMBOLS_MODE_STATE_BEGIN:
                if (key != LIMEService.KEYCODE_SPACE && key != LIMEService.KEYCODE_ENTER && key > 0) {
                    mSymbolsModeState = SYMBOLS_MODE_STATE_SYMBOL;
                }
                break;
            case SYMBOLS_MODE_STATE_SYMBOL:
                if (key == LIMEService.KEYCODE_ENTER || key == LIMEService.KEYCODE_SPACE) return true;
                break;
        }
        return false;
    }
}
