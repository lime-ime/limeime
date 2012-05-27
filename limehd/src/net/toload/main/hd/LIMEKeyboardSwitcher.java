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

package net.toload.main.hd;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import net.toload.main.hd.R;
import net.toload.main.hd.global.ImObj;
import net.toload.main.hd.global.KeyboardObj;
import net.toload.main.hd.global.LIMEPreferenceManager;
import net.toload.main.hd.keyboard.LIMEKeyboard;
import net.toload.main.hd.keyboard.LIMEKeyboardView;

import android.util.Log;

public class LIMEKeyboardSwitcher {
	
	static final boolean DEBUG = false;
	static final String TAG = "LIMEKeyboardSwitcher";
    
	public static final int MODE_TEXT = 1;
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

    //private KeyboardId mCurrentId;
    private Map<KeyboardId, LIMEKeyboard> mKeyboards;
    
    private int mMode = KEYBOARDMODE_NORMAL;
    //private int mChnMode = MODE_TEXT_DEFAULT;
    //private int mEngMode = MODE_TEXT;
    private int mImeOptions;
    private int mTextMode = MODE_TEXT_QWERTY;
    
    private LIMEPreferenceManager mLIMEPref;
    
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
 
    private static List<String> mActivatedIMList;
    //private static List<String> mActiveKeyboardNames;
    private static List<String> mActivatedIMShortnameList;
    
    private float mKeySizeScale=1;
    

    public LIMEKeyboardSwitcher(LIMEService context) {
        mContext = context;
        mLIMEPref = new LIMEPreferenceManager(context);
        mKeyboards = new HashMap<KeyboardId, LIMEKeyboard>();
        
        mKeySizeScale = mLIMEPref.getFontSize();
    }
    
    public int getKeyboardSize(){
    	if(kbHm != null){
    		return kbHm.size();
    	}
		return 0;
    }
    
    public void setKeyboardList(List<KeyboardObj> list){
    	if(list==null || (list!=null&& list.size()==0)) return; //Jeremy '12,4,10 avoid fc when database is locked.
    	kbHm = new HashMap<String, KeyboardObj>();
    	for(KeyboardObj o : list){
    		kbHm.put(o.getCode(), o);
    	}    	
    }

    public String getImKeyboard(String code){
    	if(imHm != null && imHm.get(code) != null){
    		return imHm.get(code);
    	}
    	return "";
    }
    
    public void setImList(List<ImObj> list){
    	if(list==null || (list!=null&& list.size()==0)) return; //Jeremy '12,4,10 avoid fc when database is locked.
    	imHm = new HashMap<String, String>();  	
    	for(ImObj o : list){
    		imHm.put(o.getCode(), o.getKeyboard());
    	}    	
    }
    public void setActivatedIMList(List<String> codes, List<String> names, List<String> shortnames){
    	if(DEBUG) Log.i(TAG,"setActiveKeyboardList()");
    	
    	if(codes.equals(mActivatedIMList) && shortnames.equals(mActivatedIMShortnameList)) return;
    	
    	mActivatedIMList = codes;
    	//mActiveKeyboardNames = names;
    	mActivatedIMShortnameList = shortnames;
    	
    	
    }
    
    public List<String> getActivatedIMShortnameList(){
    	return mActivatedIMShortnameList;
    }
    
    public String getActiveIMShortname(){
    	if(DEBUG) Log.i(TAG,"getCurrentActiveKeyboardShortName() current IM:"+ imtype);
    	for (int i = 0; i < mActivatedIMList.size(); i++) {
			if (imtype.equals(mActivatedIMList.get(i))) {
				if(DEBUG)Log.i(TAG,"getCurrentActiveKeyboardShortName()="+ mActivatedIMShortnameList.get(i));
    			return mActivatedIMShortnameList.get(i);
    		}
    	}
    	return "";
    }
    public String getNextActivatedIMShortname(){

    	for (int i = 0; i < mActivatedIMList.size(); i++) {
    		if (imtype.equals(mActivatedIMList.get(i))) {
    			if(i==mActivatedIMList.size()-1)
    				return mActivatedIMShortnameList.get(0);
    			else return mActivatedIMShortnameList.get(i+1);
    		}
    	}
    	return "";
    }
    public String getPrevActivatedIMShortname(){

    	for (int i = 0; i < mActivatedIMList.size(); i++) {
			if (imtype.equals(mActivatedIMList.get(i))) {
    			if(i==0) return mActivatedIMShortnameList.get(mActivatedIMList.size()-1);
    			else return mActivatedIMShortnameList.get(i-1);
    		}
    	}
    	return "";
    }
    
    public void setInputView(LIMEKeyboardView inputView) {
        mInputView = inputView;
    }
    
    public void clearKeyboards(){
    	if(DEBUG) Log.i(TAG, "clearkeyboards()");
    	if(mKeyboards != null){
        	mKeyboards.clear();
    	}
    }
    
    public void makeKeyboards(boolean forceCreate) {
    	if(DEBUG)
    		Log.i(TAG, "makekeyboards(): forcereCreate:" + forceCreate);
        if (forceCreate) mKeyboards.clear();
        // Configuration change is coming after the keyboard gets recreated. So don't rely on that.
        // If keyboards have already been made, check if we have a screen width change and 
        // create the keyboard layouts again at the correct orientation
        int displayWidth = mContext.getMaxWidth();
        if (displayWidth != mLastDisplayWidth) {
        	mLastDisplayWidth = displayWidth;
        	mKeyboards.clear();
        }
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
    
   
    private LIMEKeyboard getKeyboard(KeyboardId id) {
    	if(DEBUG)
    		Log.i(TAG,"getKeyboard()");
    	//Jeremy '11,9,3
    	if(mLIMEPref.getKeyboardSize()!=mKeySizeScale){
    		clearKeyboards();
    		mKeySizeScale = mLIMEPref.getKeyboardSize();
    	}
	    if(id != null){
	        if (!mKeyboards.containsKey(id)) {
	        	LIMEKeyboard keyboard = new LIMEKeyboard(
	                mContext, id.mXml, id.mMode, mKeySizeScale, 
	                mLIMEPref.getShowArrowKeys(), //Jeremy '12,5,21 add the show arrow keys option
	                mLIMEPref.getSplitKeyboard() //Jeremy '12,5,27 add the split keyboard option
	                );
	        	keyboard.setKeyboardSwitcher(this);
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
    
    public void setKeyboardMode(String code, int mode, int imeOptions, boolean isIm, boolean isSymbol, boolean isShift) {
    	if(DEBUG){
    		Log.i(TAG,"KBMODE code:"+code);
    		Log.i(TAG,"KBMODE mode:"+mode);
    		Log.i(TAG,"KBMODE imOphtions:"+imeOptions);
    		Log.i(TAG,"KBMODE isIM:"+isIm);
    		Log.i(TAG,"KBMODE isSymbol:"+isSymbol);
    		Log.i(TAG,"KBMODE isShift:"+isShift);
    	}
    	imtype = code;
    	
    	// Jeremy '11,6,2.  Has to preserve these options for toggle keyboard controls.
    	mImeOptions = imeOptions;
    	mIsSymbols = isSymbol;
    	mIsShifted = isShift;
    	if(mode!=0) mMode = mode;
    	
    	String imcode = "";
    	if(!code.equals("wb") && !code.equals("hs") ){
        	if(imHm != null) imcode = imHm.get(code); 
    	}else{
    		imcode = code;
    	}

    	KeyboardObj kobj=null;
    	
    	if(imcode == null || imcode.equals("")|| imcode.equals("custom")){
    		imcode = "lime";
        	if(kbHm!=null) kobj=kbHm.get(imcode);
    	}else if(imcode.equals("wb")){
    		// Art 28/Sep/2011 Force WB to use it special design keyboard layout
    		kobj = new KeyboardObj();
			kobj.setCode("wb");
			kobj.setName("蝑�鈭Ⅳ");
			kobj.setDescription("蝑�鈭Ⅳ");
			kobj.setType("phone");
			kobj.setImage("wb_keyboard_preview");
			kobj.setImkb("lime_wb");
			kobj.setImshiftkb("lime_wb");
			kobj.setEngkb("lime_abc");
			kobj.setEngshiftkb("lime_abc_shift");
			kobj.setSymbolkb("symbols");
			kobj.setSymbolshiftkb("symbols_shift");
		}else if(imcode.equals("hs")){
    		// Art 7/Feb/2012 HS Input Method
    		kobj = new KeyboardObj();
			kobj.setCode("hs");
			kobj.setName("�航情�渲死");
			kobj.setDescription("�航情�渲死");
			kobj.setType("phone");
			kobj.setImage("hs_keyboard_preview");
			kobj.setImkb("lime_hs");
			kobj.setImshiftkb("lime_hs_shift");
			kobj.setEngkb("lime_abc");
			kobj.setEngshiftkb("lime_abc_shift");
			kobj.setSymbolkb("symbols");
			kobj.setSymbolshiftkb("symbols_shift");
		}else{
        	if(kbHm!=null) kobj=kbHm.get(imcode);
		}
    	
    	KeyboardId kid = null;
    	
    	if(kobj != null){

            mIsChinese = false;
            if(isSymbol){
        		if(isShift){
	            	//Log.i("ART","KBMODE ->: " + kobj.getExtendedshiftkb());
                	kid = new KeyboardId(getKeyboardXMLID(kobj.getSymbolshiftkb()), 0, true );
        		}else{
	            	//Log.i("ART","KBMODE ->: " + kobj.getExtendedkb());
                	kid = new KeyboardId(getKeyboardXMLID(kobj.getSymbolkb()), 0, true );
        		}
            }else{
            	switch (mode) {
	            case MODE_PHONE:
	            	//Log.i("ART","KBMODE ->: phone");
	                kid = new KeyboardId(getKeyboardXMLID("phone_number"));
	                break;
	            case MODE_URL:
	            	//Log.i("ART","KBMODE ->: url");
	            	if(!imcode.equals("wb")){
		            	if(mLIMEPref.getShowNumberRowInEnglish()){
		            		if(isShift)
		            			kid = new KeyboardId(getKeyboardXMLID("lime_english_number_shift"), KEYBOARDMODE_URL, true);
		            		else
		            			kid = new KeyboardId(getKeyboardXMLID("lime_english_number"), KEYBOARDMODE_URL, true);
		            	}else{
		            		if(isShift)
		            			kid = new KeyboardId(getKeyboardXMLID("lime_english_shift"), KEYBOARDMODE_URL, true);
		            		else
		            			kid = new KeyboardId(getKeyboardXMLID("lime_english"), KEYBOARDMODE_URL, true);
		            	}	
	            	}else{
	            		if(isShift)
	            			kid = new KeyboardId(getKeyboardXMLID("lime_abc_shift"), KEYBOARDMODE_URL, true);
	            		else
	            			kid = new KeyboardId(getKeyboardXMLID("lime_abc"), KEYBOARDMODE_URL, true);
	            	}
	                break;
	            case MODE_EMAIL:
	            	//Log.i("ART","KBMODE ->: email");
	            	if(!imcode.equals("wb")){
		            	if(mLIMEPref.getShowNumberRowInEnglish()){
		            		if(isShift)
		            			kid = new KeyboardId(getKeyboardXMLID("lime_english_number_shift"), KEYBOARDMODE_EMAIL, true);
		            		else
		            			kid = new KeyboardId(getKeyboardXMLID("lime_english_number"), KEYBOARDMODE_EMAIL, true);
		            	}else{
		            		if(isShift)
		            			kid = new KeyboardId(getKeyboardXMLID("lime_english_shift"), KEYBOARDMODE_EMAIL, true);
		            		else
		            			kid = new KeyboardId(getKeyboardXMLID("lime_english"), KEYBOARDMODE_EMAIL, true); 
		            	}
	            	}else{
	            		if(isShift)
	            			kid = new KeyboardId(getKeyboardXMLID("lime_abc_shift"), KEYBOARDMODE_URL, true);
	            		else
	            			kid = new KeyboardId(getKeyboardXMLID("lime_abc"), KEYBOARDMODE_URL, true);
	            	}
	                break;
	            default:
	            	if(isIm){  // Chinese IM keyboards
	            		if(isShift){
	    	            	//Log.i("ART","KBMODE ->: " + kobj.getImshiftkb());
	                    	kid = new KeyboardId(getKeyboardXMLID(kobj.getImshiftkb()), KEYBOARDMODE_NORMAL, true );
	            		}else{
	    	            	//Log.i("ART","KBMODE ->: " + kobj.getImkb());
	                    	kid = new KeyboardId(getKeyboardXMLID(kobj.getImkb()), KEYBOARDMODE_NORMAL, true );
	            		}
		                mIsChinese = true;
	            	}else {//if(!isIm){  //English normal keyboard

		            	if(!imcode.equals("wb")){
		            		if(isShift){
		    	            	//Log.i("ART","KBMODE ->: " + kobj.getEngshiftkb());
		                    	kid = new KeyboardId(
		                    			getKeyboardXMLID(kobj.getEngshiftkb(mLIMEPref.getShowNumberRowInEnglish())), 
		                    			KEYBOARDMODE_NORMAL, true );
		            		}else{
		    	            	//Log.i("ART","KBMODE ->: " + kobj.getEngkb());
		                    	kid = new KeyboardId(
		                    			getKeyboardXMLID(kobj.getEngkb(mLIMEPref.getShowNumberRowInEnglish())), 
		                    			KEYBOARDMODE_NORMAL, true );
		            		}
		            	}else{
		            		if(isShift){
		    	            	//Log.i("ART","KBMODE ->: " + kobj.getEngshiftkb());
		                    	kid = new KeyboardId(
		                    			getKeyboardXMLID(kobj.getEngshiftkb()), 
		                    			KEYBOARDMODE_NORMAL, true );
		            		}else{
		    	            	//Log.i("ART","KBMODE ->: " + kobj.getEngkb());
		                    	kid = new KeyboardId(
		                    			getKeyboardXMLID(kobj.getEngkb()), 
		                    			KEYBOARDMODE_NORMAL, true );
		            		}
		            	}
	            	}
	            	
            	}
            }
    		
	    	if(mInputView == null) return;
	        
	        
	        LIMEKeyboard keyboard = getKeyboard(kid);
	
	       // mCurrentId = kid;
	        mInputView.setKeyboard(keyboard);
	              
	        keyboard.setShiftLocked(keyboard.isShiftLocked());
	        keyboard.setShifted(mIsShifted);
	        mInputView.setKeyboard(mInputView.getKeyboard()); //instead of invalidateAllKeys();
	        
	        keyboard.setImeOptions(mContext.getResources(), mMode, imeOptions);
    	}
    }
 


    public int getKeyboardMode() {
        return mMode;
    }
    
    public boolean isTextMode() {
        return mMode == MODE_TEXT;
    }
    
    public int getTextMode() {
        return mTextMode;
    }
    

    public int getTextModeCount() {
        return MODE_TEXT_COUNT;
    }

    public boolean isAlphabetMode() {
    	return mIsAlphabet;
    }

    public void toggleShift() {
    	if(DEBUG) Log.i("LIMEKeyboardSwicher:toggeleshift()","KBMODE mode:"+mMode);
    	mIsShifted= !mIsShifted;
    	if(mIsChinese)
        	this.setKeyboardMode(imtype, 0, mImeOptions, true, mIsSymbols, mIsShifted);
    	else{
        	this.setKeyboardMode(imtype, mMode, mImeOptions, false, mIsSymbols, mIsShifted);
    	}

    }

    public void setIsChinese(boolean value){
    	mIsChinese = value;
    }
    
    public void setIsSymbols(boolean value){
    	mIsSymbols = value;
    }
    
    public void toggleChinese() {
	   mIsChinese = !mIsChinese;
	   
	   	if(mIsChinese){
	   		
	    	this.setKeyboardMode(imtype, 0, mImeOptions, true, mIsSymbols, mIsShifted);
	    	
   		}else{
   			
	    	this.setKeyboardMode(imtype, mMode, mImeOptions, false, mIsSymbols, mIsShifted);
	    	
		}
    }
    
   public void toggleSymbols() {
    	mIsSymbols = !mIsSymbols;
    	mIsShifted = false;
    	if(mIsChinese)
        	this.setKeyboardMode(imtype, 0, mImeOptions, true, mIsSymbols, mIsShifted);
    	else
        	this.setKeyboardMode(imtype, mMode, mImeOptions, false, mIsSymbols, mIsShifted);
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
    public boolean onKey(int key) {
        // Switch back to alpha mode if user types one or more non-space/enter characters
        // followed by a space/enter
        switch (mSymbolsModeState) {
            case SYMBOLS_MODE_STATE_BEGIN:
                if (key != LIMEService.MY_KEYCODE_SPACE && key != LIMEService.MY_KEYCODE_ENTER && key > 0) {
                    mSymbolsModeState = SYMBOLS_MODE_STATE_SYMBOL;
                }
                break;
            case SYMBOLS_MODE_STATE_SYMBOL:
                if (key == LIMEService.MY_KEYCODE_ENTER || key == LIMEService.MY_KEYCODE_SPACE) return true;
                break;
        }
        return false;
    }

	public static LIMEKeyboardSwitcher getInstance() {
		// TODO Auto-generated method stub
		return null;
	}
}
