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

package net.toload.main.hd;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import net.toload.main.hd.data.ImConfig;
import net.toload.main.hd.data.Keyboard;
import net.toload.main.hd.global.LIMEPreferenceManager;
import net.toload.main.hd.keyboard.LIMEKeyboard;
import net.toload.main.hd.keyboard.LIMEKeyboardView;

import android.content.Context;
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
    

    public static final int KEYBOARD_MODE_NORMAL = R.id.mode_normal;
    public static final int KEYBOARD_MODE_URL = R.id.mode_url;
    public static final int KEYBOARD_MODE_EMAIL = R.id.mode_email;
    //public static final int KEYBOARD_MODE_IM = R.id.mode_im;
    
    //public static final int IM_KEYBOARD = 0;
    

    private static final int SYMBOLS_KEYBOARD_1 = 1;
    private static final int SYMBOLS_KEYBOARD_2 = 2;
    private static final int SYMBOLS_KEYBOARD_3 = 3;

    LIMEKeyboardView mInputView;
    LIMEService mService;
	Context mThemedContext;

    //private KeyboardId mCurrentId;
    private final Map<KeyboardId, LIMEKeyboard> mKeyboards;
    
    private int mMode = KEYBOARD_MODE_NORMAL;
    //private int mChnMode = MODE_TEXT_DEFAULT;
    //private int mEngMode = MODE_TEXT;
    private int mImeOptions;

    private final LIMEPreferenceManager mLIMEPref;
    
    private boolean mIsShifted;
    private boolean mIsSymbols;
    private boolean mIsChinese=true;
    private boolean mPreferSymbols;
    private int mCurrentSymbolsKeyboard = SYMBOLS_KEYBOARD_1;

    private int mLastDisplayWidth;
    
    private String ImCode = null;
    
	private HashMap<String, Keyboard> kbMap;
    private HashMap<String, String> imConfigMap;
 
    private static List<String> mActivatedIMList;
    private static List<String> mActivatedIMShortnameList;
    
    private float mKeySizeScale;
    

    public LIMEKeyboardSwitcher(LIMEService service, Context themedContext) {
        mService = service;
		mThemedContext = themedContext;

        mLIMEPref = new LIMEPreferenceManager(service);
        mKeyboards = new HashMap<>();
        
        mKeySizeScale = mLIMEPref.getFontSize();
    }

	public void setThemedContext(Context context){
		mThemedContext = context;
	}
    
    public int getKeyboardSize(){
    	if(kbMap != null){
    		return kbMap.size();
    	}
		return 0;
    }
    
	public void setKeyboardConfigList(List<Keyboard> list){
		if(list==null || (list.isEmpty())) return; //Jeremy '12,4,10 avoid fc when database is locked.
		kbMap = new HashMap<>();
		for(Keyboard o : list){
			kbMap.put(o.getCode(), o);
		}
	}

    public String getImConfigKeyboard(String code){
    	if(imConfigMap != null && imConfigMap.get(code) != null){
    		return imConfigMap.get(code);
    	}
    	return "";
    }
    
	public void setImConfigKeyboardList(List<ImConfig> list){
		if(list==null || list.isEmpty()) return; //Jeremy '12,4,10 avoid fc when database is locked.
		imConfigMap = new HashMap<>();
		for(ImConfig o : list){
			imConfigMap.put(o.getCode(), o.getKeyboard());
		}
	}
    public void setActivatedIMList(List<String> ImCodes, List<String> shortnames){
    	if(DEBUG) Log.i(TAG,"setActiveKeyboardList()");
    	
    	if(ImCodes.equals(mActivatedIMList) && shortnames.equals(mActivatedIMShortnameList)) return;
    	
    	mActivatedIMList = ImCodes;
        mActivatedIMShortnameList = shortnames;
    	
    	
    }
    
    public List<String> getActivatedIMShortnameList(){
    	return mActivatedIMShortnameList;
    }
    
    public String getActiveIMShortname(){
    	if(DEBUG) Log.i(TAG,"getCurrentActiveKeyboardShortName() current IM:"+ ImCode);
    	for (int i = 0; i < mActivatedIMList.size(); i++) {
			if (ImCode.equals(mActivatedIMList.get(i))) {
                if(DEBUG)
                    Log.i(TAG,"getCurrentActiveKeyboardShortName()="+ mActivatedIMShortnameList.get(i));
    			return mActivatedIMShortnameList.get(i);
    		}
    	}
    	return "";
    }
    public String getNextActivatedIMShortname(){

    	for (int i = 0; i < mActivatedIMList.size(); i++) {
    		if (ImCode.equals(mActivatedIMList.get(i))) {
    			if(i==mActivatedIMList.size()-1)
    				return mActivatedIMShortnameList.get(0);
    			else return mActivatedIMShortnameList.get(i+1);
    		}
    	}
    	return "";
    }
    public String getPrevActivatedIMShortname(){

    	for (int i = 0; i < mActivatedIMList.size(); i++) {
			if (ImCode.equals(mActivatedIMList.get(i))) {
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
    	if(DEBUG) Log.i(TAG, "clearKeyboards()");
    	if(mKeyboards != null){
        	mKeyboards.clear();
    	}
    }
    
    public void resetKeyboards(boolean forceCreate) {
        if(DEBUG)
            Log.i(TAG, "resetKeyboards(): forceCreate:" + forceCreate);
        if (forceCreate) clearKeyboards();
        // Configuration change is coming after the keyboard gets recreated. So don't rely on that.
        // If keyboards have already been made, check if we have a screen width change and 
        // create the keyboard layouts again at the correct orientation
        int displayWidth = mService.getMaxWidth();
        if (displayWidth != mLastDisplayWidth) {
        	mLastDisplayWidth = displayWidth;
			clearKeyboards();
        }
     }

    /**
     * Represents the parameters necessary to construct a new LIMEKeyboard,
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
				if(DEBUG)
					Log.i(TAG,"getKeyboard() keyboard for id, " + id + ", is not exist. create one now.");
	        	LIMEKeyboard keyboard = new LIMEKeyboard(
						mThemedContext, id.mXml, id.mMode, mKeySizeScale,
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
    
    /**
     * Get XML resource ID for keyboard layout.
     * Uses direct R.xml references for all keyboard layouts (more efficient and compile-time verified).
     */
    private int getKeyboardXMLID(String keyboardId){
		if (keyboardId == null || keyboardId.isEmpty()) {
			return 0;
		}
		
		// Use direct R.xml references for all keyboard layouts (compile-time verified, more efficient)
		switch (keyboardId) {
			// Symbol keyboards
			case "symbols1":
				return R.xml.symbols1;
			case "symbols2":
				return R.xml.symbols2;
			case "symbols3":
				return R.xml.symbols3;
			case "symbols":
				return R.xml.symbols1; // Default to symbols1
			case "symbols_shift":
				return R.xml.symbols1; // Default to symbols1
			
			// Phone keyboards
			case "phone_number":
				return R.xml.phone_number;
			case "phone":
				return R.xml.phone;
			case "phone_shift":
				return R.xml.phone_shift;
			case "phone_simple":
				return R.xml.phone_simple;
			
			// English keyboards
			case "lime_english_number_shift":
				return R.xml.lime_english_number_shift;
			case "lime_english_number":
				return R.xml.lime_english_number;
			case "lime_english_shift":
				return R.xml.lime_english_shift;
			case "lime_english":
				return R.xml.lime_english;
			
			// ABC keyboards
			case "lime_abc_shift":
				return R.xml.lime_abc_shift;
			case "lime_abc":
				return R.xml.lime_abc;
			
			// Chinese input method keyboards
			case "lime":
				return R.xml.lime;
			case "lime_shift":
				return R.xml.lime_shift;
			
			// Cangjie keyboards
			case "lime_cj":
				return R.xml.lime_cj;
			case "lime_cj_shift":
				return R.xml.lime_cj_shift;
			case "lime_cj_number":
				return R.xml.lime_cj_number;
			case "lime_cj_number_shift":
				return R.xml.lime_cj_number_shift;
			
			// Dayi keyboards
			case "lime_dayi":
				return R.xml.lime_dayi;
			case "lime_dayi_shift":
				return R.xml.lime_dayi_shift;
			case "lime_dayi_sym":
				return R.xml.lime_dayi_sym;
			case "lime_dayi_sym_shift":
				return R.xml.lime_dayi_sym_shift;
			
			// EZ keyboards
			case "lime_ez":
				return R.xml.lime_ez;
			case "lime_ez_shift":
				return R.xml.lime_ez_shift;
			
			// Array keyboards
			case "lime_array":
				return R.xml.lime_array;
			case "lime_array_shift":
				return R.xml.lime_array_shift;
			case "lime_array_number":
				return R.xml.lime_array_number;
			case "lime_array_number_shift":
				return R.xml.lime_array_number_shift;
			
			// Phonetic keyboards
			case "lime_phonetic":
				return R.xml.lime_phonetic;
			case "lime_phonetic_shift":
				return R.xml.lime_phonetic_shift;
			
			// HS keyboards
			case "lime_hs":
				return R.xml.lime_hs;
			case "lime_hs_shift":
				return R.xml.lime_hs_shift;
			
			// HSU keyboards
			case "lime_hsu":
				return R.xml.lime_hsu;
			case "lime_hsu_shift":
				return R.xml.lime_hsu_shift;
			
			// WB keyboards
			case "lime_wb":
				return R.xml.lime_wb;
			case "lime_wb_shift":
				return R.xml.lime_wb_shift;
			
			// ET26 keyboards
			case "lime_et26":
				return R.xml.lime_et26;
			case "lime_et26_shift":
				return R.xml.lime_et26_shift;
			
			// ET41 keyboards
			case "lime_et_41":
				return R.xml.lime_et_41;
			case "lime_et_41_shift":
				return R.xml.lime_et_41_shift;
			
			// Number keyboards
			case "lime_number":
				return R.xml.lime_number;
			case "lime_number_shift":
				return R.xml.lime_number_shift;
			case "lime_number_symbol":
				return R.xml.lime_number_symbol;
			case "lime_number_symbol_shift":
				return R.xml.lime_number_symbol_shift;
			
			// Special keyboards
			case "lime_url":
				return R.xml.lime_url;
			case "lime_email":
				return R.xml.lime_email;
			
			default:
				// Return 0 for unknown keyboard layouts (should not happen with valid database entries)
				if (DEBUG) {
					Log.w(TAG, "Unknown keyboard layout: " + keyboardId);
				}
				return 0;
		}
    }
    
	public void setKeyboardMode(String imCode, int mode, int imeOptions, boolean isIm, boolean isSymbol, boolean isShift) {
    	if(DEBUG){
    		Log.i(TAG, "setKeyboardMode () imCode:"+imCode + ", mode:"+mode + ", imOptions:"+imeOptions+
					", isIM:"+isIm + ", isSymbol:"+isSymbol +", isShift:"+isShift);
    	}
    	this.ImCode = imCode;
    	
    	// Jeremy '11,6,2.  Has to preserve these options for toggle keyboard controls.
    	this.mImeOptions = imeOptions;
		if(isSymbol && !this.mIsSymbols)
			this.mCurrentSymbolsKeyboard = SYMBOLS_KEYBOARD_1;  //reset the symbol keyboard to first one if it's switching from non-symbol keyboards
    	this.mIsSymbols = isSymbol;
    	this.mIsShifted = isShift;
    	if(mode!=0) this.mMode = mode;
    	
    	String localImCode = "";
        if(!imCode.equals("wb") && !imCode.equals("hs") ){
            if(imConfigMap != null) localImCode = imConfigMap.get(imCode);
        }else{
            localImCode = imCode;
        }

        Keyboard kConfig=null;
    	
    	if(localImCode == null || localImCode.isEmpty() || localImCode.equals("custom")) {
            localImCode = "lime";
            if (kbMap != null)  kConfig = kbMap.get(localImCode);
        } else if(kbMap !=null) {
            kConfig= kbMap.get(localImCode);
		}
    	
    	KeyboardId kid;
    	
    	if(kConfig != null){

            mIsChinese = false;
            if(isSymbol){
				switch(mCurrentSymbolsKeyboard) {
					case SYMBOLS_KEYBOARD_2:
						kid = new KeyboardId(getKeyboardXMLID("symbols2"));
						break;
					case SYMBOLS_KEYBOARD_3:
						kid = new KeyboardId(getKeyboardXMLID("symbols3"));
						break;
                    case SYMBOLS_KEYBOARD_1:
                    default:
                        kid = new KeyboardId(getKeyboardXMLID("symbols1"));
                        break;
				}

            }else{
            	switch (mode) {
	            case MODE_PHONE:
	            	//Log.i("ART","KBMODE ->: phone");
	                kid = new KeyboardId(getKeyboardXMLID("phone_number"));
	                break;
	            case MODE_URL:
	            	//Log.i("ART","KBMODE ->: url");
	            	if(!localImCode.equals("wb")){
		            	if(mLIMEPref.getShowNumberRowInEnglish()){
		            		if(isShift)
		            			kid = new KeyboardId(getKeyboardXMLID("lime_english_number_shift"), KEYBOARD_MODE_URL, true);
		            		else
		            			kid = new KeyboardId(getKeyboardXMLID("lime_english_number"), KEYBOARD_MODE_URL, true);
		            	}else{
		            		if(isShift)
		            			kid = new KeyboardId(getKeyboardXMLID("lime_english_shift"), KEYBOARD_MODE_URL, true);
		            		else
		            			kid = new KeyboardId(getKeyboardXMLID("lime_english"), KEYBOARD_MODE_URL, true);
		            	}	
	            	}else{
	            		if(isShift)
	            			kid = new KeyboardId(getKeyboardXMLID("lime_abc_shift"), KEYBOARD_MODE_URL, true);
	            		else
	            			kid = new KeyboardId(getKeyboardXMLID("lime_abc"), KEYBOARD_MODE_URL, true);
	            	}
	                break;
	            case MODE_EMAIL:
	            	//Log.i("ART","KBMODE ->: email");
	            	if(!localImCode.equals("wb")){
		            	if(mLIMEPref.getShowNumberRowInEnglish()){
		            		if(isShift)
		            			kid = new KeyboardId(getKeyboardXMLID("lime_english_number_shift"), KEYBOARD_MODE_EMAIL, true);
		            		else
		            			kid = new KeyboardId(getKeyboardXMLID("lime_english_number"), KEYBOARD_MODE_EMAIL, true);
		            	}else{
		            		if(isShift)
		            			kid = new KeyboardId(getKeyboardXMLID("lime_english_shift"), KEYBOARD_MODE_EMAIL, true);
		            		else
		            			kid = new KeyboardId(getKeyboardXMLID("lime_english"), KEYBOARD_MODE_EMAIL, true);
		            	}
	            	}else{
	            		if(isShift)
	            			kid = new KeyboardId(getKeyboardXMLID("lime_abc_shift"), KEYBOARD_MODE_URL, true);
	            		else
	            			kid = new KeyboardId(getKeyboardXMLID("lime_abc"), KEYBOARD_MODE_URL, true);
	            	}
	                break;
	            default:
	            	if(isIm){  // Chinese IM keyboards
	            		if(isShift){
	    	            	//Log.i("ART","KBMODE ->: " + kConfig.getImshiftkb());
	                    	kid = new KeyboardId(getKeyboardXMLID(kConfig.getImshiftkb()), KEYBOARD_MODE_NORMAL, true );
	            		}else{
	    	            	//Log.i("ART","KBMODE ->: " + kConfig.getImkb());
	                    	kid = new KeyboardId(getKeyboardXMLID(kConfig.getImkb()), KEYBOARD_MODE_NORMAL, true );
	            		}
		                mIsChinese = true;
	            	}else {//if(!isIm){  //English normal keyboard

		            	if(!localImCode.equals("wb")){
		            		if(isShift){
		    	            	//Log.i("ART","KBMODE ->: " + kConfig.getEngshiftkb());
		                    	kid = new KeyboardId(
		                    			getKeyboardXMLID(kConfig.getEngshiftkb(mLIMEPref.getShowNumberRowInEnglish())),
                                        KEYBOARD_MODE_NORMAL, true );
		            		}else{
		    	            	//Log.i("ART","KBMODE ->: " + kConfig.getEngkb());
		                    	kid = new KeyboardId(
		                    			getKeyboardXMLID(kConfig.getEngkb(mLIMEPref.getShowNumberRowInEnglish())),
                                        KEYBOARD_MODE_NORMAL, true );
		            		}
		            	}else{
		            		if(isShift){
		    	            	//Log.i("ART","KBMODE ->: " + kConfig.getEngshiftkb());
		                    	kid = new KeyboardId(
		                    			getKeyboardXMLID(kConfig.getEngshiftkb()),
                                        KEYBOARD_MODE_NORMAL, true );
		            		}else{
		    	            	//Log.i("ART","KBMODE ->: " + kConfig.getEngkb());
		                    	kid = new KeyboardId(
		                    			getKeyboardXMLID(kConfig.getEngkb()),
                                        KEYBOARD_MODE_NORMAL, true );
		            		}
		            	}
	            	}
	            	
            	}
            }
    		
	    	if(mInputView == null) return;
	        
	        
	        LIMEKeyboard keyboard = getKeyboard(kid);
	
	       // mCurrentId = kid;
	        mInputView.setKeyboard(keyboard);

			assert keyboard != null;
			keyboard.setShiftLocked(keyboard.isShiftLocked());
	        keyboard.setShifted(mIsShifted);
	        mInputView.setKeyboard(mInputView.getKeyboard()); //instead of invalidateAllKeys();
	        
	        keyboard.setImeOptions(mThemedContext.getResources(), mMode, imeOptions);
    	}
    }
 


    public int getKeyboardMode() {
        return mMode;
    }
    
    public boolean isTextMode() {
        return mMode == MODE_TEXT;
    }
    
    public int getTextMode() {
        return MODE_TEXT_QWERTY;
    }
    

    public int getTextModeCount() {
        return MODE_TEXT_COUNT;
    }

    public boolean isAlphabetMode() {
        return false;
    }

    public void toggleShift() {
    	if(DEBUG)
			Log.i(TAG,"toggleShift() KBMODE mode:"+mMode);
    	mIsShifted= !mIsShifted;
    	if(mIsChinese)
        	this.setKeyboardMode(ImCode, 0, mImeOptions, true, mIsSymbols, mIsShifted);
    	else{
        	this.setKeyboardMode(ImCode, mMode, mImeOptions, false, mIsSymbols, mIsShifted);
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
	   		
	    	this.setKeyboardMode(ImCode, 0, mImeOptions, true, mIsSymbols, mIsShifted);
	    	
   		}else{
   			
	    	this.setKeyboardMode(ImCode, mMode, mImeOptions, false, mIsSymbols, mIsShifted);
	    	
		}
    }
    
   public void toggleSymbols() {

    	if(mIsChinese)
        	this.setKeyboardMode(ImCode, 0, mImeOptions, true, !mIsSymbols, false);
    	else
        	this.setKeyboardMode(ImCode, mMode, mImeOptions, false, !mIsSymbols, false);

    }
	public void switchSymbols() {
		switch (mCurrentSymbolsKeyboard){
			case SYMBOLS_KEYBOARD_2:
				mCurrentSymbolsKeyboard = SYMBOLS_KEYBOARD_3;
				break;
			case SYMBOLS_KEYBOARD_3:
				mCurrentSymbolsKeyboard = SYMBOLS_KEYBOARD_1;
				break;
            case SYMBOLS_KEYBOARD_1:
            default:
                mCurrentSymbolsKeyboard = SYMBOLS_KEYBOARD_2;
                break;

		}
		if(mIsChinese)
			this.setKeyboardMode(ImCode, 0, mImeOptions, true, mIsSymbols, false);
		else
			this.setKeyboardMode(ImCode, mMode, mImeOptions, false, mIsSymbols, false);


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


	public static LIMEKeyboardSwitcher getInstance() {
		// TODO Auto-generated method stub
		return null;
	}
}
