package net.toload.main.hd;

import android.content.Context;
import android.text.AutoText;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;

/**
 * This class loads a dictionary and provides a list of suggestions for a given sequence of 
 * characters. This includes corrections and completions.
 * @hide pending API Council Approval
 */
public class Suggest implements Dictionary.WordCallback {

    public static final int CORRECTION_NONE = 0;
    public static final int CORRECTION_BASIC = 1;
    public static final int CORRECTION_FULL = 2;

    private Dictionary mMainDict;

    private Dictionary mUserDictionary;

    private Dictionary mAutoDictionary;

    private Dictionary mContactsDictionary;

    private int mPrefMaxSuggestions = 12;

   
    private List<Mapping> mSuggestions = new LinkedList<Mapping>();
    private boolean mIncludeTypedWordIfValid;
    private ArrayList<CharSequence> mStringPool = new ArrayList<CharSequence>();
    private Context mContext;
    private boolean mHaveCorrection;
    private CharSequence mOriginalWord;
    private String mLowerOriginalWord;

    private int mCorrectionMode = CORRECTION_BASIC;


    public Suggest(Context context, ISearchService SearchSrv) {
        mContext = context;
        mMainDict = new MainDictionary(context, SearchSrv);
        for (int i = 0; i < mPrefMaxSuggestions; i++) {
            StringBuilder sb = new StringBuilder(32);
            mStringPool.add(sb);
        }
    }

    public int getCorrectionMode() {
        return mCorrectionMode;
    }

    public void setCorrectionMode(int mode) {
        mCorrectionMode = mode;
    }

    /**
     * Sets an optional user dictionary resource to be loaded. The user dictionary is consulted
     * before the main dictionary, if set.
     */
    public void setUserDictionary(Dictionary userDictionary) {
        mUserDictionary = userDictionary;
    }

    /**
     * Sets an optional contacts dictionary resource to be loaded.
     */
    public void setContactsDictionary(Dictionary userDictionary) {
        mContactsDictionary = userDictionary;
    }
    
    public void setAutoDictionary(Dictionary autoDictionary) {
        mAutoDictionary = autoDictionary;
    }

    /**
     * Number of suggestions to generate from the input key sequence. This has
     * to be a number between 1 and 100 (inclusive).
     * @param maxSuggestions
     * @throws IllegalArgumentException if the number is out of range
     */
    public void setMaxSuggestions(int maxSuggestions) {
        if (maxSuggestions < 1 || maxSuggestions > 100) {
            throw new IllegalArgumentException("maxSuggestions must be between 1 and 100");
        }
        mPrefMaxSuggestions = maxSuggestions;
        //mPriorities = new int[mPrefMaxSuggestions];
        collectGarbage();
        while (mStringPool.size() < mPrefMaxSuggestions) {
            StringBuilder sb = new StringBuilder(32);
            mStringPool.add(sb);
        }
    }

    private boolean haveSufficientCommonality(String original, CharSequence suggestion) {
        final int originalLength = original.length();
        final int suggestionLength = suggestion.length();
        final int minLength = Math.min(originalLength, suggestionLength);
        if (minLength <= 2) return true;
        int matching = 0;
        int lessMatching = 0; // Count matches if we skip one character
        int i;
        for (i = 0; i < minLength; i++) {
            final char origChar = ExpandableDictionary.toLowerCase(original.charAt(i));
            if (origChar == ExpandableDictionary.toLowerCase(suggestion.charAt(i))) {
                matching++;
                lessMatching++;
            } else if (i + 1 < suggestionLength
                    && origChar == ExpandableDictionary.toLowerCase(suggestion.charAt(i + 1))) {
                lessMatching++;
            }
        }
        matching = Math.max(matching, lessMatching);

        if (minLength <= 4) {
            return matching >= 2;
        } else {
            return matching > minLength / 2;
        }
    }

    /**
     * Returns a list of words that match the list of character codes passed in.
     * This list will be overwritten the next time this function is called.
     * @param a view for retrieving the context for AutoText
     * @param codes the list of codes. Each list item contains an array of character codes
     * in order of probability where the character at index 0 in the array has the highest 
     * probability. 
     * @return list of suggestions.
     */
    public List<Mapping> getSuggestions(View view, WordComposer wordComposer, 
            boolean includeTypedWordIfValid) {
        mHaveCorrection = false;
        collectGarbage();
        //Arrays.fill(mPriorities, 0);
        mIncludeTypedWordIfValid = includeTypedWordIfValid;
        
        // Save a lowercase version of the original word
        mOriginalWord = wordComposer.getTypedWord();
        if (mOriginalWord != null) {
            mOriginalWord = mOriginalWord.toString();
            mLowerOriginalWord = mOriginalWord.toString().toLowerCase();
        } else {
            mLowerOriginalWord = "";
        }
        // Search the dictionary only if there are at least 2 characters
        if (wordComposer.size() > 1) {
            if (mUserDictionary != null || mContactsDictionary != null) {
                if (mUserDictionary != null) {
                    mUserDictionary.getWords(wordComposer, this);
                }
                if (mContactsDictionary != null) {
                    mContactsDictionary.getWords(wordComposer, this);
                }

                if (mSuggestions.size() > 0 && isValidWord(mOriginalWord)) {
                    mHaveCorrection = true;
                }
            }
            mMainDict.getWords(wordComposer, this);
            if (mCorrectionMode == CORRECTION_FULL && mSuggestions.size() > 0) {
                mHaveCorrection = true;
            }
        }
        if (mOriginalWord != null) {
        	Mapping temp = new Mapping();
		    temp.setWord(mOriginalWord.toString());
		    temp.setScore(0);
            mSuggestions.add(0,temp);
        }
        
        // Check if the first suggestion has a minimum number of characters in common
        if (mCorrectionMode == CORRECTION_FULL && mSuggestions.size() > 1) {
            if (!haveSufficientCommonality(mLowerOriginalWord, mSuggestions.get(1).getWord())) {
                mHaveCorrection = false;
            }
        }
        
        int i = 0;
        int max = 6;
        // Don't autotext the suggestions from the dictionaries
        if (mCorrectionMode == CORRECTION_BASIC) max = 1;
        while (i < mSuggestions.size() && i < max) {
            String suggestedWord = mSuggestions.get(i).toString().toLowerCase();
            CharSequence autoText =
                    AutoText.get(suggestedWord, 0, suggestedWord.length(), view);
            // Is there an AutoText correction?
            boolean canAdd = autoText != null;
            // Is that correction already the current prediction (or original word)?
            canAdd &= !TextUtils.equals(autoText, mSuggestions.get(i).getWord());
            // Is that correction already the next predicted word?
            if (canAdd && i + 1 < mSuggestions.size() && mCorrectionMode != CORRECTION_BASIC) {
                canAdd &= !TextUtils.equals(autoText, mSuggestions.get(i + 1).getWord());
            }
            if (canAdd) {
                mHaveCorrection = true;
                Mapping temp = new Mapping();
    		    temp.setWord(autoText.toString());
    		    temp.setScore(0);
                mSuggestions.add(i+1, temp);
                i++;
            }
            i++;
        }

        removeDupes();
        return mSuggestions;
    }

    private void removeDupes() {
        final List<Mapping> suggestions = mSuggestions;
        if (suggestions.size() < 2) return;
        int i = 1;
        // Don't cache suggestions.size(), since we may be removing items
        while (i < suggestions.size()) {
            final CharSequence cur = suggestions.get(i).getWord();
            // Compare each candidate with each previous candidate
            for (int j = 0; j < i; j++) {
                CharSequence previous = suggestions.get(j).getWord();
                if (TextUtils.equals(cur, previous)) {
                    removeFromSuggestions(i);
                    i--;
                    break;
                }
            }
            i++;
        }
    }

    private void removeFromSuggestions(int index) {
        Mapping garbage = mSuggestions.remove(index);
        if (garbage != null ) {
        	StringBuilder sbg = new StringBuilder(32);
        	sbg.append(garbage.getWord());
            mStringPool.add(sbg);
        }
    }

    public boolean hasMinimalCorrection() {
        return mHaveCorrection;
    }

    private boolean compareCaseInsensitive(final String mLowerOriginalWord, 
            final char[] word, final int offset, final int length) {
        final int originalLength = mLowerOriginalWord.length();
        if (originalLength == length && Character.isUpperCase(word[offset])) {
            for (int i = 0; i < originalLength; i++) {
                if (mLowerOriginalWord.charAt(i) != Character.toLowerCase(word[offset+i])) {
                    return false;
                }
            }
            return true;
        }
        return false;
    }

    public boolean addWord(final char[] word, final int offset, final int length, final int freq) {
        int pos = 0;
        //final int[] priorities = mPriorities;
        final int prefMaxSuggestions = mPrefMaxSuggestions;
        // Check if it's the same word, only caps are different
        if (compareCaseInsensitive(mLowerOriginalWord, word, offset, length)) {
            pos = 0;
        } else {
            // Check the last one's priority and bail
        	if(mSuggestions.size() == prefMaxSuggestions){
            	if (mSuggestions.get(prefMaxSuggestions - 1).getScore() >= freq) return true;
        	}
            while (pos < mSuggestions.size()) {
                if (mSuggestions.get(pos).getScore() < freq
                        || mSuggestions.get(pos).getScore() == freq 
                        && length < mSuggestions.get(pos).getWord().length()) {
                    break;
                }
                pos++;
            }
        }
        
        if (pos >= prefMaxSuggestions) {
            return true;
        }
        //System.arraycopy(priorities, pos, priorities, pos + 1, prefMaxSuggestions - pos - 1);
        //priorities[pos] = freq;
        int poolSize = mStringPool.size();
        StringBuilder sb = poolSize > 0 ? (StringBuilder) mStringPool.remove(poolSize - 1) 
                : new StringBuilder(32);
        sb.setLength(0);
        sb.append(word, offset, length);
        
        Mapping temp = new Mapping();
	    temp.setWord(sb.toString());
	    temp.setScore(freq);
        mSuggestions.add(pos,temp);
        if (mSuggestions.size() > prefMaxSuggestions) {
            Mapping garbage = mSuggestions.remove(prefMaxSuggestions);
            
            if (garbage!=null) {
            	StringBuilder sbg = new StringBuilder(32);
            	sbg.append(garbage.getWord());
                mStringPool.add(sbg);
                
            }
        }
        return true;
    }

    public boolean isValidWord(final CharSequence word) {
        if (word == null || word.length() == 0) {
            return false;
        }
        return ((mCorrectionMode == CORRECTION_FULL )&& mMainDict.isValidWord(word))|| 
        (mCorrectionMode > CORRECTION_NONE && 
                    ((mUserDictionary != null && mUserDictionary.isValidWord(word)))
                     || (mAutoDictionary != null && mAutoDictionary.isValidWord(word))
                     || (mContactsDictionary != null && mContactsDictionary.isValidWord(word)));
    }
    
    private void collectGarbage() {
        int poolSize = mStringPool.size();
        int garbageSize = mSuggestions.size();
        while (poolSize < mPrefMaxSuggestions && garbageSize > 0) {
            Mapping garbage = mSuggestions.get(garbageSize - 1);
            if (garbage != null ) {
            	StringBuilder sb = new StringBuilder(32);
            	sb.append(garbage.getWord());
                mStringPool.add(sb);
                poolSize++;
            }
            garbageSize--;
        }
        if (poolSize == mPrefMaxSuggestions + 1) {
            Log.w("Suggest", "String pool got too big: " + poolSize);
        }
        mSuggestions.clear();
    }
}