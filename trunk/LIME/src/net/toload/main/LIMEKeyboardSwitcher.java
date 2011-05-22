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
import java.util.List;
import java.util.Map;

import android.util.Log;

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
    public static final int MODE_TEXT_ARRAY = 18;
    public static final int MODE_TEXT_SCJ = 19;
    public static final int MODE_TEXT_SCJ_NUMBER = 20;
    public static final int MODE_TEXT_ARRAY10 = 21;
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
    private boolean mIsAlphabet=false;
    private boolean mPreferSymbols;
    private int mSymbolsModeState = SYMBOLS_MODE_STATE_NONE;

    private int mLastDisplayWidth;
    
    private String imtype = null;
    
    private HashMap<String, KeyboardObj> kbHm;
    private HashMap<String, String> imHm;

    LIMEKeyboardSwitcher(LIMEService context) {
        mContext = context;
        mKeyboards = new HashMap<KeyboardId, LIMEKeyboard>();
    }
    
    int getKeyboardSize(){
    	if(kbHm != null){
    		return kbHm.size();
    	}
		return 0;
    }
    
    void setKeyboardList(List<KeyboardObj> list){
    	kbHm = new HashMap();
    	for(KeyboardObj o : list){
    		kbHm.put(o.getCode(), o);
    	}    	
    }

    String getImKeyboard(String code){
    	if(imHm != null && imHm.get(code) != null){
    		return imHm.get(code);
    	}
    	return "";
    }
    
    void setImList(List<ImObj> list){
    	imHm = new HashMap();
    	for(ImObj o : list){
    		imHm.put(o.getCode(), o.getKeyboard());
    	}    	
    }

    void setInputView(LIMEKeyboardView inputView) {
        mInputView = inputView;
    }
    
    void clearKeyboards(){
    	if(mKeyboards != null){
        	mKeyboards.clear();
    	}
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
    
    /*void setKeyboardMode(int mode, int imeOptions) {
        mSymbolsModeState = SYMBOLS_MODE_STATE_NONE;
        mPreferSymbols = mode == MODE_SYMBOLS;
        mIsChinese = (mode == MODE_TEXT_DEFAULT ||mode == MODE_TEXT_DEFAULT_NUMBER 
        		|| mode == MODE_TEXT_PHONETIC ||mode == MODE_TEXT_DAYI 
        		||mode == MODE_TEXT_EZ ||mode == MODE_TEXT_SCJ ||mode == MODE_TEXT_SCJ_NUMBER ||mode == MODE_TEXT_CJ ||mode == MODE_TEXT_CJ_NUMBER || mode == MODE_TEXT_PHONE);
        mIsAlphabet = ( mode == MODE_TEXT || mode== MODE_URL 
        		|| mode == MODE_EMAIL || mode == MODE_IM || mode == MODE_PHONE );
        if(mIsChinese) mChnMode = mode;
        if(mIsAlphabet) mEngMode = mode;
        
        if(mIsChinese){
        	this.setKeyboardMode(imtype, mode, imeOptions, true, mPreferSymbols, false, false);
        	//setKeyboardMode(mode == MODE_SYMBOLS ? mChnMode : mode, imeOptions,
            //    mPreferSymbols, false);
        }else{
        	this.setKeyboardMode(imtype, mode, imeOptions, false, mPreferSymbols, false, false);
        	//setKeyboardMode(mode == MODE_SYMBOLS ? mEngMode : mode, imeOptions,
            //        mPreferSymbols, false);
        }
    }*/

    /*void setKeyboardMode(int mode, int imeOptions, boolean isSymbols, boolean isShift) {
    	
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
        
         
        keyboard.setShiftLocked(keyboard.isShiftLocked());
        keyboard.setShifted(mIsShifted);
        mInputView.setKeyboard(mInputView.getKeyboard()); //instead of invalidateAllKeys();
        
        keyboard.setImeOptions(mContext.getResources(), mMode, imeOptions);

    }*/

    private LIMEKeyboard getKeyboard(KeyboardId id) {

    	Log.i("ART","getKeyboard mContext:"+mContext);
    	Log.i("ART","getKeyboard id:"+id);
    	Log.i("ART","getKeyboard mKeyboards:"+mKeyboards);
    	
	    if(id != null){
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
	    return null;
    }
    
    private int getKeyboardXMLID(String value){
        int result = mContext.getResources().getIdentifier(value, "xml", mContext.getPackageName());
        return result;
    }
    
    void setKeyboardMode(String code, int mode, int imeOptions, boolean isIm, boolean isSymbol, boolean isShift) {

    	/*Log.i("ART","KBMODE code:"+code);
    	Log.i("ART","KBMODE mode:"+mode);
    	Log.i("ART","KBMODE imOphtions:"+imeOptions);
    	Log.i("ART","KBMODE isIM:"+isIm);
    	Log.i("ART","KBMODE isSymbol:"+isSymbol);
    	Log.i("ART","KBMODE isShift:"+isShift);*/
    	imtype = code;
    	
    	String imcode = imHm.get(code); 
    	
    	if(imcode == null || imcode.equals("") || imcode.equals("custom")){
    		imcode = "lime";
    	}
    	
    	KeyboardObj kobj = kbHm.get(imcode);
    	KeyboardId kid = null;
    	
    	if(kobj != null){

            mIsChinese = false;
            
    		switch (mode) {
	            case MODE_PHONE:

	            	Log.i("ART","KBMODE ->: phone");
	                kid = new KeyboardId(getKeyboardXMLID("phone_number"));
	                break;
	            case MODE_URL:
	            	Log.i("ART","KBMODE ->: url");
	            	kid = new KeyboardId(getKeyboardXMLID("lime_url"), KEYBOARDMODE_URL, true);
	                break;
	            case MODE_EMAIL:
	            	Log.i("ART","KBMODE ->: email");
	            	kid = new KeyboardId(getKeyboardXMLID("lime_email"), KEYBOARDMODE_EMAIL, true);
	                break;
	            default:
	            	if(isIm && !isSymbol){
	            		if(isShift){
	    	            	Log.i("ART","KBMODE ->: " + kobj.getImshiftkb());
	                    	kid = new KeyboardId(getKeyboardXMLID(kobj.getImshiftkb()), 0, true );
	            		}else{
	    	            	Log.i("ART","KBMODE ->: " + kobj.getImkb());
	                    	kid = new KeyboardId(getKeyboardXMLID(kobj.getImkb()), 0, true );
	            		}
		                mIsChinese = true;
	            	}else if(isSymbol){
	            		if(isShift){
	    	            	Log.i("ART","KBMODE ->: " + kobj.getExtendedshiftkb());
	                    	kid = new KeyboardId(getKeyboardXMLID(kobj.getSymbolshiftkb()), 0, true );
	            		}else{
	    	            	Log.i("ART","KBMODE ->: " + kobj.getExtendedkb());
	                    	kid = new KeyboardId(getKeyboardXMLID(kobj.getSymbolkb()), 0, true );
	            		}
	            	}else if(!isIm && !isSymbol){
	            		if(isShift){
	    	            	Log.i("ART","KBMODE ->: " + kobj.getEngshiftkb());
	                    	kid = new KeyboardId(getKeyboardXMLID(kobj.getEngshiftkb()), 0, true );
	            		}else{
	    	            	Log.i("ART","KBMODE ->: " + kobj.getEngkb());
	                    	kid = new KeyboardId(getKeyboardXMLID(kobj.getEngkb()), 0, true );
	            		}
	            	}
	            	
    		}
    		
	    	if(mInputView == null) return;
	        
	        
	        LIMEKeyboard keyboard = getKeyboard(kid);
	
	        mCurrentId = kid;
	        mInputView.setKeyboard(keyboard);
	        
	         
	        keyboard.setShiftLocked(keyboard.isShiftLocked());
	        keyboard.setShifted(mIsShifted);
	        mInputView.setKeyboard(mInputView.getKeyboard()); //instead of invalidateAllKeys();
	        
	        keyboard.setImeOptions(mContext.getResources(), mMode, imeOptions);
    	}
    }
 

   /* private KeyboardId getKeyboardId(int mode, int imeOptions, boolean isSymbols, boolean isShifted) {
    	

        Log.i("ART","Package Name : "+mContext.getPackageName());
        if (isSymbols) {
        	//if(isShifted) return new KeyboardId(R.xml.symbols_shift, 0, true );
        	if(isShifted) return new KeyboardId(getKeyboardXMLID("symbols_shift"), 0, true );
        	else return new KeyboardId(R.xml.symbols);
        }
        
        switch (mode) {
            case MODE_TEXT:
                return new KeyboardId(getKeyboardXMLID("lime_english"), KEYBOARDMODE_NORMAL, true);
                //return new KeyboardId(R.xml.lime_english, KEYBOARDMODE_NORMAL, true);
            case MODE_TEXT_DEFAULT:
                return new KeyboardId(getKeyboardXMLID("lime"), 0, true);
                //return new KeyboardId(R.xml.lime, 0, true);
            case MODE_TEXT_DEFAULT_NUMBER:
                if(isShifted) return new KeyboardId(getKeyboardXMLID("lime_number_shift"), 0, true );    
                else return new KeyboardId(getKeyboardXMLID("lime_number"));
                //if(isShifted) return new KeyboardId(R.xml.lime_number_shift, 0, true );    
               // else return new KeyboardId(R.xml.lime_number);
            case MODE_TEXT_CJ:
                if(isShifted) return new KeyboardId(getKeyboardXMLID("lime_cj_shift"), 0, true);    
                else return new KeyboardId(getKeyboardXMLID("lime_cj"));
                //if(isShifted) return new KeyboardId(R.xml.lime_cj_shift, 0, true);    
                //else return new KeyboardId(R.xml.lime_cj);
            case MODE_TEXT_CJ_NUMBER:
                if(isShifted) return new KeyboardId(getKeyboardXMLID("lime_cj_number_shift"), 0, true);    
                else return new KeyboardId(getKeyboardXMLID("lime_cj_number"));
                //if(isShifted) return new KeyboardId(R.xml.lime_cj_number_shift, 0, true);    
                //else return new KeyboardId(R.xml.lime_cj_number);
            case MODE_TEXT_SCJ:
                if(isShifted) return new KeyboardId(getKeyboardXMLID("lime_cj_shift"), 0, true);    
                else return new KeyboardId(getKeyboardXMLID("lime_cj"));
                //if(isShifted) return new KeyboardId(R.xml.lime_cj_shift, 0, true);    
                //else return new KeyboardId(R.xml.lime_cj);
            case MODE_TEXT_SCJ_NUMBER:
                if(isShifted) return new KeyboardId(getKeyboardXMLID("lime_cj_number_shift"), 0, true);    
                else return new KeyboardId(getKeyboardXMLID("lime_cj_number"));
                //if(isShifted) return new KeyboardId(R.xml.lime_cj_number_shift, 0, true);    
                //else return new KeyboardId(R.xml.lime_cj_number);
            case MODE_TEXT_DAYI:
                if(isShifted) return new KeyboardId(getKeyboardXMLID("lime_dayi_shift"), 0, true);    
                else return new KeyboardId(getKeyboardXMLID("lime_dayi"));
                //if(isShifted) return new KeyboardId(R.xml.lime_dayi_shift, 0, true);    
               // else return new KeyboardId(R.xml.lime_dayi);
           case MODE_TEXT_ARRAY:
               if(isShifted) return new KeyboardId(R.xml.lime_dayi_shift, 0, true);    
                else return new KeyboardId(R.xml.lime_dayi);
           case MODE_TEXT_ARRAY10:
               if(isShifted) return new KeyboardId(R.xml.phone, 0, true);    
                else return new KeyboardId(R.xml.phone);
            case MODE_TEXT_PHONETIC:
                if(isShifted) return new KeyboardId(getKeyboardXMLID("lime_phonetic_shift"), 0, true);    
                else return new KeyboardId(getKeyboardXMLID("lime_phonetic"));
                //if(isShifted) return new KeyboardId(R.xml.lime_phonetic_shift, 0, true);    
                //else return new KeyboardId(R.xml.lime_phonetic);
            case MODE_TEXT_EZ:
                if(isShifted) return new KeyboardId(getKeyboardXMLID("lime_ez_shift"), 0, true);    
                else return new KeyboardId(getKeyboardXMLID("lime_ez"));
               // if(isShifted) return new KeyboardId(R.xml.lime_ez_shift, 0, true);    
                //else return new KeyboardId(R.xml.lime_ez);
            case MODE_TEXT_PHONE:
                return new KeyboardId(getKeyboardXMLID("phone"), 0, false);
                //return new KeyboardId(R.xml.phone, 0, false);
            case MODE_SYMBOLS:
            	if(isShifted) return new KeyboardId(getKeyboardXMLID("symbols_shift"), 0, true);
            	else return new KeyboardId(getKeyboardXMLID("symbols"));
            	//if(isShifted) return new KeyboardId(R.xml.symbols_shift, 0, true);
            	//else return new KeyboardId(R.xml.symbols);
            case MODE_PHONE:
                return new KeyboardId(getKeyboardXMLID("phone"));
               // return new KeyboardId(R.xml.phone);
            case MODE_URL:
                return new KeyboardId(getKeyboardXMLID("lime_english"), KEYBOARDMODE_URL, true);
                //return new KeyboardId(R.xml.lime_english, KEYBOARDMODE_URL, true);
            case MODE_EMAIL:
                return new KeyboardId(getKeyboardXMLID("lime_english"), KEYBOARDMODE_EMAIL, true);
                //return new KeyboardId(R.xml.lime_english, KEYBOARDMODE_EMAIL, true);
            case MODE_IM:
            	return new KeyboardId(getKeyboardXMLID("lime_english"), KEYBOARDMODE_IM, true);
            	//return new KeyboardId(R.xml.lime_english, KEYBOARDMODE_IM, true);
        }
        return null;
    }*/

    int getKeyboardMode() {
        return mMode;
    }
    
    boolean isTextMode() {
        return mMode == MODE_TEXT;
    }
    
    int getTextMode() {
        return mTextMode;
    }
    
    /*void setTextMode(int position) {
        if (position < MODE_TEXT_COUNT && position >= 0) {
            mTextMode = position;
        }
        if (isTextMode()) {
        	this.setKeyboardMode(imtype, mode, imeOptions, true, false, false, false);
            //setKeyboardMode(MODE_TEXT, mImeOptions);
        }
    }*/

    int getTextModeCount() {
        return MODE_TEXT_COUNT;
    }

    boolean isAlphabetMode() {
    	return mIsAlphabet;
    }

    void toggleShift() {
    	mIsShifted= !mIsShifted;
    	if(mIsChinese)
        	this.setKeyboardMode(imtype, 0, mImeOptions, true, mIsSymbols, mIsShifted);
    	else{
        	this.setKeyboardMode(imtype, 0, mImeOptions, false, mIsSymbols, mIsShifted);
    	}

    }
    
   void toggleChinese() {
	   mIsChinese = !mIsChinese;
	   	if(mIsChinese)
	    	this.setKeyboardMode(imtype, 0, mImeOptions, true, mIsSymbols, mIsShifted);
		else{
	    	this.setKeyboardMode(imtype, 0, mImeOptions, false, mIsSymbols, mIsShifted);
		}
    }
    
    void toggleSymbols() {
    	mIsSymbols = !mIsSymbols;
    	mIsShifted = false;
    	if(mIsChinese)
        	this.setKeyboardMode(imtype, 0, mImeOptions, true, mIsSymbols, mIsShifted);
    	else
        	this.setKeyboardMode(imtype, 0, mImeOptions, false, mIsSymbols, mIsShifted);
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
