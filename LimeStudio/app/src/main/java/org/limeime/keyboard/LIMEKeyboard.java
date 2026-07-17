/*
 *
 *  *
 *  **    Copyright 2026, The LimeIME Open Source Project
 *  **
 *  **    Project Url: http://github.com/lime-ime/limeime/
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

package org.limeime.keyboard;

import org.limeime.LIMEKeyboardSwitcher;
import org.limeime.R;
import android.content.Context;
import android.content.res.Resources;
import android.content.res.TypedArray;
import android.content.res.XmlResourceParser;
import android.graphics.drawable.Drawable;
import android.util.Log;
import android.view.inputmethod.EditorInfo;

import java.util.List;

/**
 * @author Art Hung
 */
public class LIMEKeyboard extends LIMEBaseKeyboard {

	static final boolean DEBUG = false;
	static final String TAG = "LIMEKeyboard";

	private Key mShiftKey;
    private Key mEnterKey;
	    
    private static final int SHIFT_OFF = 0;
    private static final int SHIFT_ON = 1;
    private static final int SHIFT_LOCKED = 2;
    
    private int mShiftState = SHIFT_OFF;

    private boolean mThemedIconLoaded = false;

    //private static boolean themedResourcesLoaded = false;
    /**
     * Drawable to override function key icons
     */
    private static Drawable mSpaceKeyIcon;
    private static Drawable mSpaceKeyPreviewIcon;
    private static Drawable mEnterKeyIcon;
    private static Drawable mDeleteKeyIcon;
    private static Drawable mShiftKeyIcon;
    private static Drawable mShiftKeyShiftedIcon;
    private static Drawable mDoneKeyIcon;

    private static Drawable mSearchKeyIcon;
    private static int mSpaceKeyVerticalCorrection;

    private static final int[] CJ_HOME_ROW_CODES = {97, 115, 100, 102, 103, 104, 106, 107, 108};
    private static final int[] CJ_HOME_ROW_SHIFT_CODES = {65, 83, 68, 70, 71, 72, 74, 75, 76};
    private static final int CJ_HOME_ROW_KEY_COUNT = 9;

    

    
    private boolean mCurrentlyInSpace;
    private static int mSpaceDragStartX;
    private static int mSpaceDragLastDiff;
    
    // UI Dimension Constants (in pixels/dp)
    private static final int KEY_POSITION_ADJUSTMENT_DIVISOR = 10; // Divisor for key position adjustments
    
    //private final int mMode;
   // public LIMEKeyboard(Context context, int xmlLayoutResId) {
   // 	this(context, xmlLayoutResId, 0, 1, false);
   // }

    public LIMEKeyboard(Context context, int xmlLayoutResId, int mode, float keySizeScale, int showArrowKeys, int splitKeyboard, boolean splitEligible ) {
        super(context, xmlLayoutResId, mode, keySizeScale, showArrowKeys, splitKeyboard, splitEligible);
        if(DEBUG)
            Log.i(TAG, "LIMEKeyboard()");
        //Resources mRes = context.getResources();
    }



    private void loadThemedIcons(Context context){

        if(DEBUG)
            Log.i(TAG, "loadThemedIcons()");

        try (TypedArray a = context.getTheme().obtainStyledAttributes(
                null, R.styleable.LIMEKeyboard, R.attr.LIMEKeyboardStyle, R.style.LIMEKeyboard)) {

            mSpaceKeyIcon = a.getDrawable(R.styleable.LIMEKeyboard_spaceKeyIcon);
            mSpaceKeyPreviewIcon = a.getDrawable(R.styleable.LIMEKeyboard_spaceKeyPreviewIcon);
            mEnterKeyIcon = a.getDrawable(R.styleable.LIMEKeyboard_enterKeyIcon);
            mSearchKeyIcon = a.getDrawable(R.styleable.LIMEKeyboard_searchKeyIcon);
            mDoneKeyIcon = a.getDrawable(R.styleable.LIMEKeyboard_doneKeyIcon);
            mDeleteKeyIcon = a.getDrawable(R.styleable.LIMEKeyboard_deleteKeyIcon);
            mShiftKeyIcon = a.getDrawable(R.styleable.LIMEKeyboard_shiftKeyIcon);
            mShiftKeyShiftedIcon = a.getDrawable(R.styleable.LIMEKeyboard_shiftKeyShiftedIcon);

            mSpaceKeyVerticalCorrection = a.getDimensionPixelSize(R.styleable.LIMEKeyboard_spaceKeyVerticalCorrection, 0);
        }
    }

	@Override
    protected Key createKeyFromXml(Context context, Row parent, int x, int y, XmlResourceParser parser) {
        if(DEBUG)
            Log.i(TAG, "createKeyFromXml() mThemedIconLoaded = " + mThemedIconLoaded);

        if(!mThemedIconLoaded) {
            // createKeyFromXml called from constructor of super and will be earlier of anything in constructor of this..  Jeremy '16,7,31
            loadThemedIcons(context);
            mThemedIconLoaded = true;
        }

        Key key = new LIMEKey(context.getResources(), parent, x, y, parser);
        //Override function key icons from theme
        switch (key.codes[0]) {
            case KEYCODE_ENTER:
                mEnterKey = key;
                if(mEnterKeyIcon!=null)
                    key.icon = mEnterKeyIcon;
                break;
            case KEYCODE_SPACE:
                if(mSpaceKeyIcon!=null)
                    key.icon = mSpaceKeyIcon;
                if(mSpaceKeyPreviewIcon!=null) {
                    key.iconPreview = mSpaceKeyPreviewIcon;
                }
                break;
            case KEYCODE_DELETE:
                if (mDeleteKeyIcon != null)
                    key.icon = mDeleteKeyIcon;
                break;
            case KEYCODE_DONE:
                if (mDoneKeyIcon != null)
                    key.icon = mDoneKeyIcon;
                break;
            case KEYCODE_SHIFT:
                if (mShiftKeyIcon != null) {
                    key.icon = (isShifted())?mShiftKeyShiftedIcon:mShiftKeyIcon;
                    mShiftKey = key;
                }
                break;
        }

        return key;
    }
    
    public void enableShiftLock() {
        //int index = getShiftKeyIndex();
        //if (index >= 0) {
            //mShiftKey = getKeys().get(index);
        if (mShiftKey instanceof LIMEKey) {
            ((LIMEKey) mShiftKey).enableShiftLock();
        }

        //}
    }

    public void setShiftLocked(boolean shiftLocked) {
        if(DEBUG)
            Log.i("LIMEKeyboard", "setShiftLocked: "+ shiftLocked);
        if (mShiftKey != null) {
            if (shiftLocked) {
                mShiftKey.on = true;
                mShiftState = SHIFT_LOCKED;

            } else {
                mShiftKey.on = false;
                mShiftState = SHIFT_ON;
            }
        }
    }

    public boolean isShiftLocked() {
        return mShiftState == SHIFT_LOCKED;
    }
    
    @Override
    public boolean setShifted(boolean shiftState) {
    	if(DEBUG)
            Log.i("LIMEKeyboard", "setShifted: "+ shiftState);
        boolean shiftChanged = false;
        if (mShiftKey != null) {
            if (!shiftState) {
                shiftChanged = mShiftState != SHIFT_OFF;
                mShiftState = SHIFT_OFF;
                mShiftKey.on = false;
                mShiftKey.icon = mShiftKeyIcon;
            } else {
                if (mShiftState == SHIFT_OFF) {
                    shiftChanged = true;
                    mShiftState = SHIFT_ON;
                }
                mShiftKey.icon = mShiftKeyShiftedIcon;
            }
            mShiftKey.icon.invalidateSelf();
        } else {
            return super.setShifted(shiftState);
        }
        return shiftChanged;
    }
    
    @Override
    public boolean isShifted() {
        if (mShiftKey != null) {
            return mShiftState != SHIFT_OFF;
        } else {
            return super.isShifted();
        }
    }
    
    void setImeOptions(Resources res, int options) {
    	setImeOptions(res, LIMEKeyboardSwitcher.MODE_TEXT, options);
    }
    
    public void setImeOptions(Resources res, int mode, int options) {
        if (mEnterKey != null) {
            // Reset some of the rarely used attributes.
            mEnterKey.popupCharacters = null;
            mEnterKey.popupResId = 0;
            mEnterKey.text = null;
            switch (options&(EditorInfo.IME_MASK_ACTION|EditorInfo.IME_FLAG_NO_ENTER_ACTION)) {
                case EditorInfo.IME_ACTION_GO:
                    mEnterKey.iconPreview = null;
                    mEnterKey.icon = null;
                    mEnterKey.label = res.getText(R.string.label_go_key);
                    break;
                case EditorInfo.IME_ACTION_NEXT:
                    mEnterKey.iconPreview = null;
                    mEnterKey.icon = null;
                    //int c[] = {-99};
                    //mEnterKey.codes = c;
                    mEnterKey.label = res.getText(R.string.label_next_key);
                    break;
                case EditorInfo.IME_ACTION_DONE:
                    mEnterKey.iconPreview = null;
                    mEnterKey.icon = null;
                    mEnterKey.label = res.getText(R.string.label_done_key);
                    break;
                case EditorInfo.IME_ACTION_SEARCH:
                    mEnterKey.icon = mSearchKeyIcon;
                    mEnterKey.label = null;
                    break;
                case EditorInfo.IME_ACTION_SEND:
                    mEnterKey.iconPreview = null;
                    mEnterKey.icon = null;
                    mEnterKey.label = res.getText(R.string.label_send_key);
                    break;
                default:
                	
                    if (mode == LIMEKeyboardSwitcher.MODE_IM) {
                        mEnterKey.icon = null;
                        mEnterKey.label = ":-)";
                        //mEnterKey.text = ":-) ";
                        mEnterKey.popupResId = R.xml.popup_smileys;
                    } else {

                        mEnterKey.icon = mEnterKeyIcon;
                        mEnterKey.label = null;
                    }
                    break;
            }
            // Set the initial size of the preview icon
            if (mEnterKey.iconPreview != null) {
                mEnterKey.iconPreview.setBounds(0, 0,
                        mEnterKey.height *
                                mEnterKey.iconPreview.getIntrinsicWidth()/ mEnterKey.iconPreview.getIntrinsicHeight(),
                        mEnterKey.height);
            }
        }
        
    }
    
    /**
     * Lock touch movement into the spacebar so LIMEKeyboardView can turn
     * horizontal movement into caret deltas.
     */
    boolean isInside(LIMEKey key, int x, int y) {
        if (DEBUG) Log.i(TAG, "isInside(), keycode = " + key.codes[0] + ". x=" + x + ". y=" + y +
                ". mSpaceDragStartX=" + mSpaceDragStartX +
                ". mSpaceDragLastDiff=" + mSpaceDragLastDiff);
        final int code = key.codes[0];
        if (code == KEYCODE_SHIFT ||
                code == KEYCODE_DELETE) {
            y -= key.height / KEY_POSITION_ADJUSTMENT_DIVISOR;
            if (code == KEYCODE_SHIFT) x += key.width / 6;
            if (code == KEYCODE_DELETE) x -= key.width / 6;
        } else if (code == KEYCODE_SPACE) {
            y += LIMEKeyboard.mSpaceKeyVerticalCorrection;

            if (mCurrentlyInSpace) {
                mSpaceDragLastDiff = x - mSpaceDragStartX;
                return true;
            } else {
                boolean insideSpace = key.isInsideSuper(x, y);
                if (insideSpace) {
                    mCurrentlyInSpace = true;
                    mSpaceDragStartX = x;
                    mSpaceDragLastDiff = 0;
                }
                return insideSpace;
            }


        }

        // Lock into the spacebar
        return !mCurrentlyInSpace && key.isInsideSuper(x, y);

    }
    
    void keyReleased() {
        mCurrentlyInSpace = false;
        mSpaceDragLastDiff = 0;

    }
    
    
    public int getSpaceDragDiff() {
        return mSpaceDragLastDiff;
    }

    public void addCj4SemicolonKey() {
        List<Key> keys = getKeys();
        int homeStart = findCjHomeRowStart(keys, CJ_HOME_ROW_CODES);
        if (homeStart < 0) homeStart = findCjHomeRowStart(keys, CJ_HOME_ROW_SHIFT_CODES);
        if (homeStart < 0) return;

        int insertIndex = homeStart + CJ_HOME_ROW_KEY_COUNT;
        Key lastHomeKey = keys.get(insertIndex - 1);
        if (insertIndex < keys.size() && keys.get(insertIndex).y == lastHomeKey.y
                && hasPrimaryCode(keys.get(insertIndex), ';')) {
            return;
        }

        int totalWidth = getMinWidth();
        if (totalWidth <= 0) return;

        for (int i = 0; i < CJ_HOME_ROW_KEY_COUNT; i++) {
            Key key = keys.get(homeStart + i);
            int left = Math.round((float) totalWidth * i / 10);
            int right = Math.round((float) totalWidth * (i + 1) / 10);
            key.x = left;
            key.width = right - left;
            key.gap = 0;
        }

        Row row = new Row(this);
        Key semicolonKey = new Key(row, lastHomeKey);
        semicolonKey.x = Math.round((float) totalWidth * 9 / 10);
        semicolonKey.width = totalWidth - semicolonKey.x;
        semicolonKey.gap = 0;
        semicolonKey.codes = new int[]{';'};
        semicolonKey.label = "'\n;";
        semicolonKey.icon = null;
        semicolonKey.iconPreview = null;
        semicolonKey.text = null;
        semicolonKey.popupCharacters = "'";
        semicolonKey.popupResId = R.xml.popup_template;
        semicolonKey.modifier = false;
        semicolonKey.sticky = false;
        semicolonKey.repeatable = false;
        semicolonKey.pressed = false;
        semicolonKey.on = false;
        lastHomeKey.edgeFlags &= ~EDGE_RIGHT;
        semicolonKey.edgeFlags = lastHomeKey.edgeFlags | EDGE_RIGHT;
        keys.add(insertIndex, semicolonKey);
    }

    private int findCjHomeRowStart(List<Key> keys, int[] rowCodes) {
        for (int i = 0; i <= keys.size() - rowCodes.length; i++) {
            boolean matches = true;
            for (int j = 0; j < rowCodes.length; j++) {
                if (!hasPrimaryCode(keys.get(i + j), rowCodes[j])) {
                    matches = false;
                    break;
                }
            }
            if (matches) return i;
        }
        return -1;
    }

    private boolean hasPrimaryCode(Key key, int code) {
        return key.codes != null && key.codes.length > 0 && key.codes[0] == code;
    }

    class LIMEKey extends LIMEBaseKeyboard.Key {
       
    	private boolean mShiftLockEnabled;


        public LIMEKey(Resources res, LIMEBaseKeyboard.Row parent, int x, int y, XmlResourceParser parser) {
            super(res, parent, x, y, parser);
            if(DEBUG) Log.i(TAG,"LIMEKey():"+this.codes[0]);
            if (popupCharacters != null && popupCharacters.length() == 0) {
                // If there is a keyboard with no keys specified in popupCharacters
                popupResId = 0;
            }

        }
        
        @Override
        public void onReleased(boolean inside) {
            if (!mShiftLockEnabled) {
                super.onReleased(inside);
            } else {
                pressed = !pressed;
            }
        }

        void enableShiftLock() {
            mShiftLockEnabled = true;
        }
        /**
         * Overriding this method so that we can reduce the target area for the key that
         * closes the keyboard. 
         */
        @Override
        public boolean isInside(int x, int y) {
//        	 final int code = codes[0];
//             if (code == KEYCODE_SHIFT ||
//                     code == KEYCODE_DELETE) {
//                 y -= height / 10;
//                 if (code == KEYCODE_SHIFT) x += width / 6;
//                 if (code == KEYCODE_DELETE) x -= width / 6;
//             }
//              if (code == KEYCODE_DONE) y  -= 10;
              
           
            return	LIMEKeyboard.this.isInside(this, x, y);
            
            //return super.isInside(x,  y);
        }
        boolean isInsideSuper(int x, int y) {
            return super.isInside(x, y);
        }

    }
    /**
     * 
     * Jeremy '11,8,5 make a link back channel to LIMEKeyboardSwitcher
     */
    public void setKeyboardSwitcher(LIMEKeyboardSwitcher keyboardswitcher){
    }

}
