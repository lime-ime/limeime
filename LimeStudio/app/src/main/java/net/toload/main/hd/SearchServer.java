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

import android.content.Context;
import android.content.Intent;
import android.os.RemoteException;
import android.util.Log;
import android.util.Pair;
import android.widget.Toast;

import net.toload.main.hd.data.Im;
import net.toload.main.hd.data.Keyboard;
import net.toload.main.hd.data.Mapping;
import net.toload.main.hd.data.Record;
import net.toload.main.hd.data.Related;
import net.toload.main.hd.global.LIME;
import net.toload.main.hd.global.LIMEPreferenceManager;
import net.toload.main.hd.global.LIMEUtilities;
import net.toload.main.hd.limedb.LimeDB;
import net.toload.main.hd.ui.MainActivity;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.Stack;
import java.util.concurrent.ConcurrentHashMap;

/**
 * SearchServer is the central engine for handling input method queries and candidate suggestions.
 * <p>
 * It acts as an intermediary between the IME service and the database {@link LimeDB}, providing:
 * <ul>
 *     <li>Efficient database querying for character mapping and phrase retrieval.</li>
 *     <li>Multi-level caching mechanics (code, English words, emojis) to optimize performance.</li>
 *     <li>Runtime suggestion generation for dynamic phrase building.</li>
 *     <li>Handling of different keyboard types (phonetic, physical) and their mapping logic.</li>
 *     <li>Support for related phrase lookups and Han character conversion.</li>
 * </ul>
 */
public class SearchServer {

    private static final boolean DEBUG = false;
    private static final String TAG = "SearchServer";
    private static LimeDB dbadapter = null;
    
    // Score Thresholds
    private static final int MIN_SCORE_THRESHOLD = 120; // Minimum score threshold for search results
    private static final int MAX_SCORE_THRESHOLD = 200; // Maximum score threshold for search results
    private static final int SCORE_ADJUSTMENT_INCREMENT = 50; // Score adjustment increment
    private static final int CODE_LENGTH_BONUS_MULTIPLIER = 30; // Multiplier for code length bonus calculation

    //Jeremy '12,5,1 shared single LIMEDB object
    //Jeremy '12,4,6 Combine updatedb and quierydb into db,
    //Jeremy '12,4,7 move db open/close back to LimeDB

    //Jeremy '12,6,9 make run-time suggestion phrase
    private static final boolean doRunTimeSuggestion = true;

    private static List<Mapping> scorelist = null;

    //Jeremy '15,6,2 preserve the exact match mapping with the code user typed.
    private static List<List<Pair<Mapping, String>>> suggestionLoL;
    private static Stack<Pair<Mapping, String>> bestSuggestionStack;
    private static String lastCode; // preserved the last code queried from LIMEService

    //Jeremy '15,6,21
    private static int maxCodeLength = 4;

    private static boolean mResetCache;

    private static List<List<Mapping>> LDPhraseListArray = null;
    private static List<Mapping> LDPhraseList = null;

    private static String tablename = "";

    private final LIMEPreferenceManager mLIMEPref;

    private static boolean isPhysicalKeyboardPressed; // Sync to LIMEService and LIMEDB
    //Jeremy '11,6,10
    private static boolean hasNumberMapping;
    private static boolean hasSymbolMapping;

    //Jeremy '11,6,6
    private final HashMap<String, String> selKeyMap = new HashMap<>();

    private static ConcurrentHashMap<String, List<Mapping>> cache = null;
    private static ConcurrentHashMap<String, List<Mapping>> engcache = null;
    private static ConcurrentHashMap<String, List<Mapping>> emojicache = null;
    private static ConcurrentHashMap<String, String> keynamecache = null;
    /**
     * Store the mapping of typing code and mapped code from getMappingByCode on db  Jeremy '12,6,5
     */
    private static ConcurrentHashMap<String, List<String>> coderemapcache = null;

    private final Context mContext;

    // deprecated and using exact match stack to get real code length now. Jerey '15,6,2
    //private static List<Pair<Integer, Integer>> codeLengthMap = new LinkedList<>();

    /**
     * Constructs a new SearchServer instance.
     *
     * @param context The application context, used for database access and preference loading.
     */
    public SearchServer(Context context) {


        this.mContext = context;

        mLIMEPref = new LIMEPreferenceManager(mContext.getApplicationContext());
        if (dbadapter == null) dbadapter = new LimeDB(mContext);
        initialCache();


    }

    /**
     * Signals whether the cache should be reset on the next operation.
     *
     * @param resetCache true to trigger a cache reset.
     */
    public static void resetCache(boolean resetCache) {
        mResetCache = resetCache;
    }

    /**
     * Converts a string using the Han character conversion settings (e.g., Traditional to Simplified).
     *
     * @param input The input string to convert.
     * @return The converted string.
     */
    public String hanConvert(String input) {
        return dbadapter.hanConvert(input, mLIMEPref.getHanCovertOption());
    }

    /**
     * Gets the current database table name.
     *
     * @return The name of the current table.
     */
    public String getTablename() {
        return tablename;
    }

    /**
     * Sets the current active database table (IME method).
     * <p>
     * This updates the database adapter and optionally triggers a cache prefetch for better performance.
     *
     * @param table         The name of the table to switch to (e.g., Phonetic, CJ).
     * @param numberMapping Whether the table supports number mapping.
     * @param symbolMapping Whether the table supports symbol mapping.
     */
    public void setTablename(String table, boolean numberMapping, boolean symbolMapping) {
        if (DEBUG)
            Log.i(TAG, "SearchService.setTablename()");

        dbadapter.setTableName(table);
        tablename = table;
        hasNumberMapping = numberMapping;
        hasSymbolMapping = symbolMapping;

        //run prefetch on first keys thread to feed the data into cache first for better response on large table.  Jeremy '15, 6,7
        if (cache.get(cacheKey("a")) == null) {  // no cache records present. do prefetch now.  '15,6,7
            prefetchCache(numberMapping, symbolMapping);
        }

        //Jeremy '15,6,21 set max code length
        if (tablename.startsWith(LIME.DB_TABLE_CJ)) {
            maxCodeLength = 5;
        }
    }

    private static Thread prefetchThread;

    /**
     * Prefetches common mappings into the cache to improve initial response time.
     * <p>
     * Runs in a background thread.
     *
     * @param numberMapping Whether to prefetch number mappings.
     * @param symbolMapping Whether to prefetch symbol mappings.
     */
    private void prefetchCache(boolean numberMapping, boolean symbolMapping) {
        if(DEBUG)
            Log.i(TAG, "prefetchCache() on table :" + tablename);

        StringBuilder keysBuilder = new StringBuilder("abcdefghijklmnoprstuvwxyz");
        if (numberMapping)
            keysBuilder.append("01234567890");
        if (symbolMapping)
            keysBuilder.append(",./;");
        final String finalKeys = keysBuilder.toString();

        if (prefetchThread != null && prefetchThread.isAlive()) return;

        prefetchThread = new Thread() {
            public void run() {
                long startime = System.currentTimeMillis();
                for (int i = 0; i < finalKeys.length(); i++) {
                    String key = finalKeys.substring(i, i + 1);
                    try {
                        //bypass run-time suggestion for prefetch queries
                        getMappingByCode(key, true, false, true);
                    } catch (RemoteException e) {
                        Log.e(TAG, "Error in search operation", e);
                    }
                }
                Log.i(TAG, "prefetchCache() on table :" + tablename + " finished.  Elapsed time = "
                        + (System.currentTimeMillis() - startime) + " ms.");
            }
        };
        prefetchThread.start();

    }


    //TODO: Should cache related phrase 15,6,8 Jeremy
    /**
     * Gets related phrase suggestions for a parent word.
     * 
     * <p>This method delegates to LimeDB.getRelatedPhrase() to retrieve related phrase
     * candidates that can follow the given parent word.
     * 
     * @param word The parent word to get related phrases for
     * @param getAllRecords If true, returns up to FINAL_RESULT_LIMIT; if false, returns up to INITIAL_RESULT_LIMIT
     * @return List of Mapping objects containing related phrase suggestions
     * @throws RemoteException if database error occurs
     */
    public List<Mapping> getRelatedByWord(String word, boolean getAllRecords) throws RemoteException {

        return dbadapter.getRelatedPhrase(word, getAllRecords);
    }

    //Add by jeremy '10, 4,1
    /**
     * Retrieves the list of input codes for a given word and displays it.
     * <p>
     * Used for reverse lookup features.
     *
     * @param word The word to look up.
     */
    public void getCodeListStringFromWord(final String word) {

        String result = dbadapter.getCodeListStringByWord(word);
        if (result != null && !result.isEmpty()) {
            LIMEUtilities.showNotification(
                    mContext, true, mContext.getText(R.string.ime_setting), result, new Intent(mContext, MainActivity.class));

            if(mLIMEPref.getReverseLookupNotify()){
                Toast.makeText(mContext, result, Toast.LENGTH_SHORT).show();
            }
        }

    }

    /**
     * Generates a unique cache key for a given code.
     * <p>
     * The key depends on the keyboard type (physical/virtual) and table name to avoid collisions.
     *
     * @param code The input code.
     * @return A unique string key for the cache.
     */
    private String cacheKey(String code) {
        String key;

        //Jeremy '11,6,17 Seperate physical keyboard cache with keybaordtype
        if (isPhysicalKeyboardPressed) {
            if (tablename.equals(LIME.DB_TABLE_PHONETIC)) {
                key = mLIMEPref.getPhysicalKeyboardType() + dbadapter.getTableName()
                        + mLIMEPref.getPhoneticKeyboardType() + code;
            } else {
                key = mLIMEPref.getPhysicalKeyboardType() + dbadapter.getTableName() + code;
            }
        } else {
            if (tablename.equals(LIME.DB_TABLE_PHONETIC))
                key = dbadapter.getTableName() + mLIMEPref.getPhoneticKeyboardType() + code;
            else
                key = dbadapter.getTableName() + code;
        }
        return key;
    }


    /**
     * Clears the runtime suggestion history.
     *
     * @param abandonSuggestion true if the suggestion process should be abandoned.
     */
    private void clearRunTimeSuggestion(boolean abandonSuggestion)
    {
        for (List<Pair<Mapping, String>> suggestList : suggestionLoL) {
            suggestList.clear();
        }
        suggestionLoL.clear();
        if (bestSuggestionStack != null) bestSuggestionStack.clear();
        String lastConfirmedBestSuggestion = null;
        abandonPhraseSuggestion =abandonSuggestion;
    }

    private static final boolean dumpRunTimeSuggestion = false;

    /**
     * Generates runtime phrase suggestions based on the current input code.
     * <p>
     * This method analyzes the input code and the list of exact matches to dynamically construct
     * phrase suggestions. It handles incremental updates, maintaining a history of suggestions
     * in {@code suggestionLoL} to support efficient updates when new characters are added or removed
     * (backspace).
     * <p>
     * It attempts to combine previous best suggestions with new exact matches for the remaining
     * code segment to form longer phrases, validating them against the related words table and
     * calculating scores to prioritize the most likely candidates.
     *
     * @param code                   The current full input code sequence.
     * @param completeCodeResultList A list of mappings that exactly match the current code (or parts of it).
     */
    private synchronized void makeRunTimeSuggestion(String code, List<Mapping> completeCodeResultList) {

        long startTime=0;
        if (DEBUG || dumpRunTimeSuggestion) {
            Log.i(TAG, "makeRunTimeSuggestion() code = " + code);
            startTime = System.currentTimeMillis();
        }
        //check if the composing is start over or user pressed backspace
        if (suggestionLoL != null && !suggestionLoL.isEmpty()) {
            // code is start over, clear the stack.  The composition is start over.   Jeremy'15,6,4.
            if (code.length() == 1) {
                clearRunTimeSuggestion(false);

            } else if (code.length() == lastCode.length() - 1) {  //user press backspace.
                for (List<Pair<Mapping, String>> suggestList : suggestionLoL) {
                    //check the last element in each list
                    if (!suggestList.isEmpty() && suggestList.get(suggestList.size() - 1).second.equals(lastCode)) {
                        suggestList.remove(suggestList.size() - 1);
                    }
                }
                //remove best suggestion stack last element if last element is with lastCode
                if (bestSuggestionStack != null && !bestSuggestionStack.isEmpty() && bestSuggestionStack.lastElement().second.equals(lastCode)) {
                    bestSuggestionStack.pop();
                }
            }

        }
        lastCode = code;


        if (DEBUG || dumpRunTimeSuggestion)
            Log.i(TAG, "makeRunTimeSuggestion(): Finish checking for the composing is start over or user pressed backspace. Time elapsed  = " + (System.currentTimeMillis() - startTime) );


            //15,6,8  Jeremy. Check exact match records first.
        if (completeCodeResultList != null && !completeCodeResultList.isEmpty() && completeCodeResultList.get(0).isExactMatchToCodeRecord()) {
            Mapping exactMatchMapping;
            int k = 0, highestScore = 0, initialSize = suggestionLoL.size(), highestScoreIndex = initialSize;
            List<List<Pair<Mapping, String>>> suggestLoLSnapshot = null;
            do {
                exactMatchMapping = completeCodeResultList.get(k);
                int score = exactMatchMapping.getBasescore();
                if (score < MIN_SCORE_THRESHOLD) {
                    score = MIN_SCORE_THRESHOLD;
                } else if (score > MAX_SCORE_THRESHOLD) {
                    score = MAX_SCORE_THRESHOLD;
                }
                int codeLenBonus = exactMatchMapping.getCode().length() / exactMatchMapping.getWord().length() * CODE_LENGTH_BONUS_MULTIPLIER;
                int newScore = score + codeLenBonus;

                exactMatchMapping.setBasescore(newScore * exactMatchMapping.getWord().length());

                if (DEBUG || dumpRunTimeSuggestion)
                    Log.i(TAG, "makeRunTimeSuggestion() complete code = " + code +
                            ", got exact match  = " + exactMatchMapping.getWord()
                            + " score =" + exactMatchMapping.getScore() + ", bases core=" + exactMatchMapping.getBasescore()
                            +", time elapsed  =" +(System.currentTimeMillis() - startTime));


                //push the exact match mapping with current code into exact match stack. '15,6,2 Jeremy
                if (exactMatchMapping.getBasescore() > 0) {
                    if (k == 0 && exactMatchMapping.getWord().length() > 1) { //clear all previous traces if exact match phrase found
                        suggestLoLSnapshot = new LinkedList<>();
                        for (List<Pair<Mapping, String>> lpm : suggestionLoL) {
                            suggestLoLSnapshot.add(new LinkedList<>(lpm));
                            lpm.clear();
                        }
                        suggestionLoL.clear();
                        initialSize = 0;

                    }

                    if (newScore > highestScore) {
                        highestScore = newScore;
                        highestScoreIndex = k + initialSize;
                    }
                    List<Pair<Mapping, String>> suggestionList = new LinkedList<>();

                    //trace back to mappings in snapshot if the exact matching word is start with it.
                    if (suggestLoLSnapshot != null) {
                        for (int i = 0; i < suggestLoLSnapshot.size(); i++) {
                            if (suggestLoLSnapshot.get(i) != null && !suggestLoLSnapshot.get(i).isEmpty()
                                    && exactMatchMapping.getWord().startsWith(suggestLoLSnapshot.get(i).get(0).first.getWord())) {
                                suggestionList.add(suggestLoLSnapshot.get(i).get(0));
                                if (suggestLoLSnapshot.get(i).size() > 1) {
                                    for (int j = 1; j < suggestLoLSnapshot.get(i).size(); j++) {
                                        if (exactMatchMapping.getWord().startsWith(suggestLoLSnapshot.get(i).get(j).first.getWord()))
                                            suggestionList.add(suggestLoLSnapshot.get(i).get(j));
                                    }
                                }
                            }
                        }
                    }

                    suggestionList.add(new Pair<>(exactMatchMapping, code));
                    suggestionLoL.add(suggestionList);
                }
                k++;
                if (DEBUG || dumpRunTimeSuggestion)
                    Log.i(TAG, "makeRunTimeSuggestion(): Check  "+ k +"th exact match records. Time elapsed  = " + (System.currentTimeMillis() - startTime) );

            }while (completeCodeResultList.size() > k && completeCodeResultList.get(k).isExactMatchToCodeRecord() && k < 5); //process at most 5 exact match items.


            // clear suggestLoLSnapshot if it's not empty
            if (suggestLoLSnapshot != null) {
                for (List<Pair<Mapping, String>> lpm : suggestLoLSnapshot) {
                    lpm.clear();
                }
                suggestLoLSnapshot.clear();
            }
            if (!suggestionLoL.isEmpty() && highestScoreIndex != suggestionLoL.size() - 1) {//move bestSuggestionList to the last element
                List<Pair<Mapping, String>> bestSuggestionList = suggestionLoL.remove(highestScoreIndex);
                suggestionLoL.add(bestSuggestionList);

            }

        } else {
            assert suggestionLoL != null;
            if (!suggestionLoL.isEmpty()) {  // no exact match recoreds found. search remaining code

                if (DEBUG || dumpRunTimeSuggestion)
                    Log.i(TAG, "makeRunTimeSuggestion() no exact match on complete code = " + code + ", time elapsed = " + (System.currentTimeMillis() - startTime));

                int highestScore = 0, highestRelatedScore = 0, i = 0, highestScoreIndex = 0;
                //iterate all previous exact match mapping and check for exact match on remaining code.
                List<List<Pair<Mapping, String>>> suggestionLoLSnapShot = new LinkedList<>(suggestionLoL);
                for (List<Pair<Mapping, String>> suggestionList : suggestionLoLSnapShot) {
                    List<Pair<Mapping, String>> seedSuggestionList = suggestionLoL.remove(0);
                    if (highestScoreIndex > 0) highestScoreIndex--;
                    int lolSize = suggestionLoL.size();

                    for (Pair<Mapping, String> p : suggestionList) {
                        String pCode = p.second;
                        if (pCode.length() < code.length() && code.startsWith(pCode) && code.length() - pCode.length() <= maxCodeLength) {
                            String remainingCode = code.substring(pCode.length());
                            if (DEBUG || dumpRunTimeSuggestion)
                                Log.i(TAG, "makeRunTimeSuggestion() working on previous exact match item = " + p.first.getWord() +
                                        " with base score = " + p.first.getBasescore() + ", average score = " + p.first.getBasescore() / p.first.getWord().length() +
                                        ", remainingCode =" + remainingCode + " , highestScoreIndex = " + highestScoreIndex + ", time elapsed =" + (System.currentTimeMillis() - startTime));


                            List<Mapping> resultList =  //do remaining code query
                                    getMappingByCodeFromCacheOrDB(remainingCode, false);
                            if (resultList == null) continue;

                            if (DEBUG || dumpRunTimeSuggestion)
                                Log.i(TAG, "makeRunTimeSuggestion() finish query on previous exact match item = " + p.first.getWord() +
                                        " , time elapsed =" + (System.currentTimeMillis() - startTime));

                            if (!resultList.isEmpty()
                                    && resultList.get(0).isExactMatchToCodeRecord()) {  //remaining code search got exact match
                                Mapping remainingCodeExactMatchMapping = resultList.get(0);
                                Mapping previousMapping = p.first;
                                String phrase = previousMapping.getWord() + remainingCodeExactMatchMapping.getWord();
                                int phraseLen = phrase.length();
                                if (phraseLen < 2 || remainingCodeExactMatchMapping.getBasescore() < 2)
                                    continue;
                                int remainingScore = remainingCodeExactMatchMapping.getBasescore();
                                int codeLenBonus = remainingCodeExactMatchMapping.getCode().length() /
                                        remainingCodeExactMatchMapping.getWord().length() * CODE_LENGTH_BONUS_MULTIPLIER;
                                if (remainingScore > MIN_SCORE_THRESHOLD) remainingScore = MIN_SCORE_THRESHOLD;
                                remainingScore = remainingScore / remainingCodeExactMatchMapping.getWord().length() + codeLenBonus;

                                int previousScore = previousMapping.getBasescore() / previousMapping.getWord().length();
                                int averageScore = (previousScore + remainingScore) / 2;

                                if (DEBUG || dumpRunTimeSuggestion)
                                    Log.i(TAG, "makeRunTimeSuggestion() remaining code = " + remainingCode +
                                            ", got exact match  = " + remainingCodeExactMatchMapping.getWord() + " with base score = "
                                            + remainingScore + " average score =" + averageScore + " , highestScoreIndex = " + highestScoreIndex + ", time elapsed =" + (System.currentTimeMillis() - startTime));

                                //verify if the new phrase is in related table.
                                // check up to four characters phrase 1-3, 1-2 , 1-1
                                Mapping relatedMapping = null;
                                for (int k = ((phraseLen < 4) ? phraseLen - 1 : 3); k > 0; k--) {
                                    String pword = phrase.substring(phraseLen - k - 1, phraseLen - k);
                                    String cword = phrase.substring(phraseLen - k, phraseLen);
                                    relatedMapping = dbadapter.isRelatedPhraseExist(pword, cword);
                                    if (relatedMapping != null) break;
                                }
                                if (relatedMapping != null
                                        && relatedMapping.getBasescore() >= highestRelatedScore
                                        && (averageScore + SCORE_ADJUSTMENT_INCREMENT) > highestScore
                                        ) {
                                    Mapping suggestMapping = new Mapping();
                                    suggestMapping.setRuntimeBuiltPhraseRecord();
                                    suggestMapping.setCode(code);
                                    suggestMapping.setWord(phrase);
                                    highestRelatedScore = relatedMapping.getBasescore();
                                    suggestMapping.setScore(highestRelatedScore);
                                    highestScore = (averageScore + SCORE_ADJUSTMENT_INCREMENT);
                                    suggestMapping.setBasescore(highestScore * phraseLen);
                                    List<Pair<Mapping, String>> newSuggestionList = new LinkedList<>(seedSuggestionList);
                                    newSuggestionList.add(new Pair<>(suggestMapping, code));
                                    suggestionLoL.add(newSuggestionList);
                                    highestScoreIndex = suggestionLoL.size() - 1;
                                    if (DEBUG || dumpRunTimeSuggestion)
                                        Log.i(TAG, "makeRunTimeSuggestion()  run-time suggest phrase verified from related table ="
                                                + phrase + ", basescore from related table = " + highestRelatedScore + " " +
                                                ", new average score = " + highestScore + " , highestScoreIndex = " + highestScoreIndex+ ", time elapsed =" + (System.currentTimeMillis() - startTime));
                                } else if (//highestRelatedScore == 0 &&// no mapping is verified from related table
                                        averageScore > highestScore) {
                                    Mapping suggestMapping = new Mapping();
                                    suggestMapping.setRuntimeBuiltPhraseRecord();
                                    suggestMapping.setCode(code);
                                    suggestMapping.setWord(phrase);
                                    highestScore = averageScore;
                                    suggestMapping.setBasescore(highestScore * phraseLen);

                                    List<Pair<Mapping, String>> newSuggestionList = new LinkedList<>(seedSuggestionList);
                                    newSuggestionList.add(new Pair<>(suggestMapping, code));
                                    suggestionLoL.add(newSuggestionList);
                                    highestScoreIndex = suggestionLoL.size() - 1;

                                    if (DEBUG || dumpRunTimeSuggestion)
                                        Log.i(TAG, "makeRunTimeSuggestion()  run-time suggest phrase =" + phrase
                                                + ", new average score = " + highestScore + " , highestScoreIndex = " + highestScoreIndex+ ", time elapsed =" + (System.currentTimeMillis() - startTime));
                                }
                            }
                        }
                    }
                    if (lolSize == suggestionLoL.size()) {
                        suggestionLoL.add(seedSuggestionList);
                        if (DEBUG || dumpRunTimeSuggestion)
                            Log.i(TAG, "makeRunTimeSuggestion()  no new suggestion list. add back the seed suggestion list to location 0 because of last run.");
                    }
                    i++;
                    if (DEBUG || dumpRunTimeSuggestion)
                        Log.i(TAG, "makeRunTimeSuggestion() : remaing cod search +" + i +"th run.  time elapsed = " + (System.currentTimeMillis()-startTime));
                }
                if (!suggestionLoL.isEmpty() && highestScoreIndex != suggestionLoL.size() - 1) {//move bestSuggestionList to the last element
                    List<Pair<Mapping, String>> bestSuggestionList = suggestionLoL.remove(highestScoreIndex);
                    suggestionLoL.add(bestSuggestionList);
                }

            }
        }

        //push best suggestion to stack
        List<Pair<Mapping, String>> bestSuggestionList;
        if (!suggestionLoL.isEmpty()) {
            bestSuggestionList = suggestionLoL.get(suggestionLoL.size() - 1);
            if (bestSuggestionList != null && !bestSuggestionList.isEmpty()) {
                bestSuggestionStack.push(bestSuggestionList.get(bestSuggestionList.size() - 1));
            }
        }
        /*
        //find confirmed best suggestion with longest common string
        if (bestSuggestionStack != null && !bestSuggestionStack.isEmpty() && bestSuggestionStack.size() > 1) {
            for (int i = bestSuggestionStack.size() - 1; i > 0; i--) {
                if (code.length() - bestSuggestionStack.get(i).first.getCode().length() > maxCodeLength) {
                    String lastBestSuggestion = bestSuggestionStack.get(i - 1).first.getWord(), bestSuggestion = bestSuggestionStack.get(i).first.getWord();
                    if (lastBestSuggestion != null &&
                            lastBestSuggestion.length() > 1 && bestSuggestion.length() >= lastBestSuggestion.length()) {
                        String tempBestSuggestion = lcs(lastBestSuggestion, bestSuggestion);
                        if (confirmedBestSuggestion == null) {
                            confirmedBestSuggestion = tempBestSuggestion;
                        } else if (lastConfirmedBestSuggestion == null
                                || tempBestSuggestion.length() > lastConfirmedBestSuggestion.length()) {
                            lastConfirmedBestSuggestion = confirmedBestSuggestion;
                            confirmedBestSuggestion = tempBestSuggestion;
                        }
                    }
                    break;
                }
            }
            if ((DEBUG || dumpRunTimeSuggestion)) {
                if (lastConfirmedBestSuggestion != null)
                    Log.i(TAG, "makeRunTimeSuggestion() last confirmed best suggestion = " + lastConfirmedBestSuggestion);
                if (confirmedBestSuggestion != null)
                    Log.i(TAG, "makeRunTimeSuggestion() confirmed best suggestion = " + confirmedBestSuggestion);
                if (!bestSuggestionStack.isEmpty()) {
                    int i = 0;
                    for (Pair<Mapping, String> it : bestSuggestionStack) {
                        Log.i(TAG, "makeRunTimeSuggestion() best suggestion stack (" + (i) + ")= " + bestSuggestionStack.get(i).first.getWord());
                        i++;
                    }
                }
            }
        }
        */

        // dump suggestion list of list
        //if ((DEBUG || dumpRunTimeSuggestion) &&
            //    suggestionLoL != null && !suggestionLoL.isEmpty()) {
            //for (int i = 0; i < suggestionLoL.size(); i++) {
                //if (suggestionLoL.get(i) != null && !suggestionLoL.get(i).isEmpty()) {
                    //for (int j = 0; j < suggestionLoL.get(i).size(); j++) {
                        //Log.i(TAG, "makeRunTimeSuggestion() suggestionLoL(" + i + ")(" + j + "): word="
                        //        + suggestionLoL.get(i).get(j).first.getWord() + ", code=" + suggestionLoL.get(i).get(j).second
                        //        + ", base score=" + suggestionLoL.get(i).get(j).first.getBasescore()
                        //        + ", average base score=" + suggestionLoL.get(i).get(j).first.getBasescore() / suggestionLoL.get(i).get(j).first.getWord().length()
                        //        + ", score=" + suggestionLoL.get(i).get(j).first.getScore());
                    //}
                //}
           // }

            //Log.i(TAG,"makeRunTimeSuggestion() time elapsed = " +  (System.currentTimeMillis()- startTime ) );
        //}
    }

    /*
    *   return longest common substring with recursive method.
     */
    /**
     * Computes the Longest Common Substring (LCS) of two strings.
     *
     * @param a First string.
     * @param b Second string.
     * @return The longest common substring.
     */
    private String lcs(String a, String b) {
        int aLen = a.length();
        int bLen = b.length();
        if (aLen == 0 || bLen == 0) {
            return "";
        } else if (a.charAt(aLen - 1) == b.charAt(bLen - 1)) {
            return lcs(a.substring(0, aLen - 1), b.substring(0, bLen - 1))
                    + a.charAt(aLen - 1);
        } else {
            String x = lcs(a, b.substring(0, bLen - 1));
            String y = lcs(a.substring(0, aLen - 1), b);
            return (x.length() > y.length()) ? x : y;
        }
    }

    /*
    * Jeremy '15,7,12 synchronized the method called from LIMEService only
    */
    /**
     * Retrieves a list of candidate mappings for a given input code.
     * <p>
     * This is an overload for {@link #getMappingByCode(String, boolean, boolean, boolean)}.
     *
     * @param code          The input code to search for.
     * @param softkeyboard  True if input is from software keyboard, false for hardware.
     * @param getAllRecords True to retrieve all matching records, false for a limited set.
     * @return A list of matching {@link Mapping} objects.
     * @throws RemoteException If a database error occurs.
     */
    public synchronized List<Mapping> getMappingByCode(String code, boolean softkeyboard, boolean getAllRecords) throws RemoteException {
        return getMappingByCode(code, softkeyboard, getAllRecords, false);
    }

    private static boolean  abandonPhraseSuggestion = false;

    /**
     * Converts a code to its corresponding Emoji mappings.
     * <p>
     * Results are cached in {@code emojicache} to optimize repeated lookups.
     *
     * @param code The code to convert.
     * @param type The type of conversion (internal DB parameter).
     * @return A list of Emoji mappings.
     */
    public List<Mapping> emojiConvert(String code, int type){
        if(code != null){
            if(emojicache == null){
                emojicache = new ConcurrentHashMap<>(LIME.SEARCHSRV_RESET_CACHE_SIZE);
            }
            List<Mapping> results = emojicache.get(code);
            if (emojicache.get(code) == null) {
                results = dbadapter.emojiConvert(code, type);
                emojicache.put(code, results);
            }
            return results;
        }
        return null;
    }

    /**
     * Core method to retrieve mappings for a code from cache or database.
     * <p>
     * Handles complex logic including:
     * <ul>
     *     <li>Cache lookup and management.</li>
     *     <li>Runtime phrase suggestion generation.</li>
     *     <li>English word suggestion fallback.</li>
     *     <li>Result sorting and filtering.</li>
     * </ul>
     *
     * @param code           The input code.
     * @param softkeyboard   True if soft keyboard.
     * @param getAllRecords  True to fetch all records.
     * @param prefetchCache  True if this request is a background prefetch.
     * @return List of mappings.
     * @throws RemoteException If database fails.
     */
    public List<Mapping> getMappingByCode(String code, boolean softkeyboard, boolean getAllRecords, boolean prefetchCache)
            throws RemoteException {
        if (DEBUG||dumpRunTimeSuggestion)
            Log.i(TAG, "getMappingByCode(): code=" + code);
        // Check if system need to reset cache

        //check reset cache with local variable instead of reading from shared preference for better performance
        if (mResetCache) {
            initialCache();
            mResetCache = false;
        }

        //codeLengthMap.clear();//Jeremy '12,6,2 reset the codeLengthMap

        List<Mapping> result = new LinkedList<>();
        if (code != null) {
            // clear mappingidx when user switching between softkeyboard and hard keyboard. Jeremy '11,6,11
            if (isPhysicalKeyboardPressed == softkeyboard)
                isPhysicalKeyboardPressed = !softkeyboard;

            // Jeremy '11,9, 3 remove cached keyname when request full records
            if (getAllRecords && keynamecache.get(cacheKey(code)) != null)
                keynamecache.remove(cacheKey(code));

            int size = code.length();

            //boolean hasMore = false;


            // 12,6,4 Jeremy. Ascending a ab abc... looking up db if the cache is not exist
            //'15,6,4 Jeremy. Do exact search only in between search mode (1 time only).
            List<Mapping> resultList
                    = getMappingByCodeFromCacheOrDB(code, getAllRecords);



            //Jeremy '15,7,16 reset abandonPhraseSuggestion if code length ==1
            if(mLIMEPref.getSmartChineseInput() && abandonPhraseSuggestion && code.length()==1){
                clearRunTimeSuggestion(false);
            }
            // make run-time suggestion '15, 6, 9 Jeremy.
            if (!abandonPhraseSuggestion && !prefetchCache && mLIMEPref.getSmartChineseInput()) {
                makeRunTimeSuggestion(code, resultList);
            }

            // 12,6,4 Jeremy. Descending  abc ab a... Build the result candidate list.
            //'15,6,4 Jeremy. Do exact search only in between search mode.
            //for (int i = 0; i < ((LimeDB.getBetweenSearch()) ? 1 : size); i++) {
            String cacheKey = cacheKey(code);
            List<Mapping> cacheTemp = cache.get(cacheKey);


            if (cacheTemp != null) {
                List<Mapping> resultlist = cacheTemp;

                //if getAllRecords is true and result list or related list has has more mark in the end
                // recall LimeDB.GetMappingByCode with getAllRecords true.
                if (getAllRecords &&
                        resultlist.size() > 1 && resultlist.get(resultlist.size() - 1).isHasMoreRecordsMarkRecord()) {
                    try {
                        cacheTemp = dbadapter.getMappingByCode(code, !isPhysicalKeyboardPressed, true);
                        cache.remove(cacheKey);
                        cache.put(cacheKey, cacheTemp);
                    } catch (Exception e) {
                        Log.e(TAG, "Error in search operation", e);
                    }

                }
            }

            if (cacheTemp != null) {
                List<Mapping> resultlist = cacheTemp;
                //List<Mapping> relatedtlist = cacheTemp.second;

                if (DEBUG || dumpRunTimeSuggestion)
                    Log.i(TAG, "getMappingByCode() code=" + code + " resultlist.size()=" + resultlist.size() + ", abandonPhraseSuggestion:" + abandonPhraseSuggestion);


                //if (i == 0) {
                if (resultlist.isEmpty() && code.length() > 1) {
                    //If the result list is empty we need to go back to last result list with nonzero result list
                    String wayBackCode = code;
                    do {
                        wayBackCode = wayBackCode.substring(0, wayBackCode.length() - 1);
                        cacheTemp = cache.get(cacheKey(wayBackCode));
                        if (cacheTemp != null)
                            resultlist = cacheTemp;
                    } while (resultlist.isEmpty() && wayBackCode.length() > 1);
                }


                Mapping self = new Mapping();
                self.setWord(code);
                self.setCode(code);
                self.setComposingCodeRecord();
                // put run-time built suggestion if it's present
                        /*List<Pair<Mapping, String>> bestSuggestionList = null;
                        Mapping bestSuggestion = null;
                        if (!suggestionLoL.isEmpty()) {
                            bestSuggestionList = suggestionLoL.get(suggestionLoL.size() - 1);
                        }
                        if (bestSuggestionList != null && !bestSuggestionList.isEmpty()) {
                            bestSuggestion = bestSuggestionList.get(bestSuggestionList.size() - 1).first;
                        }*/

                //Jeremy '15,7,16 check english suggestion if code length > maxCodeLength
                Mapping englishSuggestion = null;
                if(code.length() > maxCodeLength) {
                    List<Mapping> englishSuggestions = getEnglishSuggestions(code);
                    if(englishSuggestions!=null && !englishSuggestions.isEmpty()) {
                        englishSuggestion = englishSuggestions.get(0);
                        englishSuggestion.setRuntimeBuiltPhraseRecord();
                        englishSuggestion.setCode(code);
                    }
                }


                Mapping bestSuggestion = null;
                if (bestSuggestionStack != null && !bestSuggestionStack.isEmpty()) {
                    bestSuggestion = bestSuggestionStack.lastElement().first;
                }
                int averageScore =(bestSuggestion==null)?0: (bestSuggestion.getBasescore()  / bestSuggestion.getWord().length());

                if (bestSuggestion != null    // the last element is run-time built suggestion from remaining code query
                        && !abandonPhraseSuggestion
                        && !bestSuggestion.isExactMatchToCodeRecord() //will be the first item of result list, dont' add duplicated item
                        && bestSuggestion.getWord().length() > 1
                        && ( (englishSuggestion==null && averageScore  > MIN_SCORE_THRESHOLD) || (englishSuggestion!=null && averageScore > MAX_SCORE_THRESHOLD ))  ) {
                    result.add(self);
                    result.add(bestSuggestion);

                } else if( englishSuggestion!=null && averageScore <= MAX_SCORE_THRESHOLD){
                    clearRunTimeSuggestion(true);
                    result.add(self);
                    result.add(englishSuggestion);
                } else {
                    // put self into the first mapping for mixed input.
                    result.add(self);
                }
                // }

                if (!resultlist.isEmpty()) {
                    result.addAll(resultlist);
                    /*
                    int rsize = result.size();
                    if (result.get(rsize - 1).isHasMoreRecordsMarkRecord()) {
                        //do not need to touch the has more record in between search mode. Jeremy '15,6,4
                        result.remove(rsize - 1);
                        hasMore = true;

                        }
                        */
                    if (DEBUG)
                        Log.i(TAG, "getMappingByCode() code=" + code + "  result list added resultlist.size()="
                                + resultlist.size());

                }

            }
            //codeLengthMap is deprecated and replace by exact match stack scheme '15,6,3 jeremy
            //codeLengthMap.add(new Pair<>(code.length(), result.size()));  //Jeremy 12,6,2 preserve the code length in each loop.
            //if (DEBUG) 	Log.i(TAG, "getMappingByCode() codeLengthMap  code length = " + code.length() + ", result size = " + result.size());

            code = code.substring(0, code.length() - 1);
        }
        if (DEBUG)
            Log.i(TAG, "getMappingByCode() code=" + code + " result.size()=" + result.size());

        return result;

    }




    /**
     * Retrieves the mapping list from cache, or queries the database if not found.
     * <p>
     * Separated from {@code getMappingByCode} to modularize cache/DB logic.
     *
     * @param queryCode     The code to look up.
     * @param getAllRecords Whether to retrieve all matching records.
     * @return List of mappings.
	 */
    private List<Mapping> getMappingByCodeFromCacheOrDB(String queryCode, Boolean getAllRecords) {
        String cacheKey = cacheKey(queryCode);
        List<Mapping> cacheTemp = cache.get(cacheKey);

        if (DEBUG)
            Log.i(TAG, " getMappingByCode() check if cached exist on code = '" + queryCode + "'");

        if (cacheTemp == null) {
            // 25/Jul/2011 by Art
            // Just ignore error when something wrong with the result set
            try {
                cacheTemp = dbadapter.getMappingByCode(queryCode, !isPhysicalKeyboardPressed, getAllRecords);
                if (cacheTemp != null) cache.put(cacheKey, cacheTemp);
                //Jeremy '12,6,5 check if need to update code remap cache
                if (cacheTemp != null
                        && !cacheTemp.isEmpty() && cacheTemp.get(0) != null
                        && cacheTemp.get(0).isExactMatchToCodeRecord()) {
                    String remappedCode = cacheTemp.get(0).getCode();
                    if (!queryCode.equals(remappedCode)) {
                        List<String> codeList = coderemapcache.get(remappedCode);
                        String key = cacheKey(remappedCode);
                        if (codeList == null) {
                            List<String> newlist = new LinkedList<>();
                            newlist.add(remappedCode); //put self in the list
                            newlist.add(queryCode);
                            coderemapcache.put(key, newlist);
                            if (DEBUG)
                                Log.i(TAG, "getMappingByCode() build new remap code = '"
                                        + remappedCode + "' to code = '" + queryCode + "'"
                                        + " coderemapcache.size()=" + coderemapcache.size());
                        } else {
                            codeList.add(queryCode);
                            coderemapcache.remove(key);
                            coderemapcache.put(key, codeList);
                            if (DEBUG)
                                Log.i(TAG, "getMappingByCode() remappedCode: add new remap code = '" + remappedCode + "' to code = '" + queryCode + "'");
                        }

                    }

                }

            } catch (Exception e) {
                Log.e(TAG, "Error in search operation", e);
            }
        }
        return cacheTemp;


}

    /**
     * Determines the actual length of the code matched by a selected mapping.
     * <p>
     * Useful for separating the matched portion of the input from the remaining buffer.
     * Also triggers learning of runtime-built phrases.
     *
     * @param selectedMapping The user-selected mapping.
     * @param currentCode     The current input buffer.
     * @return The length of the code corresponding to the selection.
     */
    int getRealCodeLength(final Mapping selectedMapping, String currentCode) {
        if (DEBUG)
            Log.i(TAG, "getRealCodeLength()");

        String code = selectedMapping.getCode();
        int realCodeLen = code.length();
        if (LimeDB.isCodeDualMapped()) { //abandon LD support for dual mapped codes. Jeremy '15,6,5
            realCodeLen = currentCode.length();
        } else {
            if (tablename.equals(LIME.DB_TABLE_PHONETIC)) {
                String selectedPhoneticKeyboardType =
                        mLIMEPref.getParameterString("phonetic_keyboard_type", LIME.DB_TABLE_PHONETIC);
                String lcode = currentCode;
                if (selectedPhoneticKeyboardType.startsWith(LIME.IM_PHONETIC_KEYBOARD_TYPE_ETEN)) {
                    lcode = dbadapter.preProcessingRemappingCode(currentCode);
                }
                String noToneCode = code.replaceAll("[3467 ]", "");
                if (code.equals(noToneCode)) {
                    realCodeLen = code.length();
                } else if (!lcode.startsWith(code) && lcode.startsWith(noToneCode)) {
                    realCodeLen = noToneCode.length();
                } else {
                    realCodeLen = currentCode.length(); //unexpected condition.
                }
            }
        }

        //remove elements in suggestionLoL with code length smaller than current code length - submitted code length
        if (realCodeLen < currentCode.length()) {
            Iterator<List<Pair<Mapping, String>>> itl = suggestionLoL.iterator();
            while (itl.hasNext()) {
                List<Pair<Mapping, String>> lpe = itl.next();
                Iterator<Pair<Mapping, String>> it = lpe.iterator();
                while (it.hasNext()) {
                    Pair<Mapping, String> pe = it.next();
                    if (pe.second.length() > currentCode.length() - realCodeLen) {
                        it.remove();
                    }
                }
                if (lpe.isEmpty()) itl.remove();
            }
            Iterator<Pair<Mapping, String>> it = bestSuggestionStack.iterator();
            while (it.hasNext()) {
                Pair<Mapping, String> pe = it.next();
                if (pe.second.length() > currentCode.length() - realCodeLen) {
                    it.remove();
                }
            }
        }

        // learn ld phrase if the select mapping is run-time suggestion
        if (selectedMapping.isRuntimeBuiltPhraseRecord() && suggestionLoL != null && !suggestionLoL.isEmpty()) {

            final List<Pair<Mapping, String>> bestSuggestionList = new LinkedList<>(suggestionLoL.get(suggestionLoL.size() - 1));
            final String selectedWord = selectedMapping.getWord();

            Thread learnLDPhraseThread = new Thread() {
                public void run() {

                    if (!bestSuggestionList.isEmpty()) {
                        for (int j = 0; j < bestSuggestionList.size(); j++) {
                            //TODO:should learn QP code for phonetic table
                            if (selectedWord.startsWith(bestSuggestionList.get(j).first.getWord())) {
                                if (bestSuggestionList.get(j).first.getWord().length() > 8)
                                    break; //stop learning if word length > 8
                                dbadapter.addOrUpdateMappingRecord(bestSuggestionList.get(j).second, bestSuggestionList.get(j).first.getWord());
                                removeRemappedCodeCachedMappings(bestSuggestionList.get(j).second);
                            }

                            if ((DEBUG || dumpRunTimeSuggestion))// dump best suggestion list
                                Log.i(TAG, "getRealCodeLength() best suggestion list(" + j + "): word="
                                        + bestSuggestionList.get(j).first.getWord() + ", code=" + bestSuggestionList.get(j).second);

                        }

                    }

                }
            };
            learnLDPhraseThread.start();

        }

        return realCodeLen;
    }


     /**
     * Initializes or resets all internal caches (mappings, English words, emojis, etc.).
     * <p>
     * Clears existing caches and allocates new concurrent hashmaps.
     */
    public void initialCache() {
        try {
            clear();
        } catch (RemoteException e) {
            Log.e(TAG, "Error in search operation", e);
        }
        cache = new ConcurrentHashMap<>(LIME.SEARCHSRV_RESET_CACHE_SIZE);
        engcache = new ConcurrentHashMap<>(LIME.SEARCHSRV_RESET_CACHE_SIZE);
        emojicache = new ConcurrentHashMap<>(LIME.SEARCHSRV_RESET_CACHE_SIZE);
        keynamecache = new ConcurrentHashMap<>(LIME.SEARCHSRV_RESET_CACHE_SIZE);
        coderemapcache = new ConcurrentHashMap<>(LIME.SEARCHSRV_RESET_CACHE_SIZE);

        //  initial exact match stack here
        suggestionLoL = new LinkedList<>();
        bestSuggestionStack = new Stack<>();

    }


    /**
     * Updates the score of a cached mapping and re-sorts the cache if necessary.
     *
     * @param cachedMapping The mapping with the updated score.
     */
    private void updateScoreCache(Mapping cachedMapping) {
        if (DEBUG) Log.i(TAG, "updateScoreCache(): code=" + cachedMapping.getCode());

        dbadapter.addScore(cachedMapping);
        // Jeremy '11,7,29 update cached here
        if (!cachedMapping.isRelatedPhraseRecord()) {
            String code = cachedMapping.getCode().toLowerCase(Locale.US);
            String cachekey = cacheKey(code);
            List<Mapping> cachedList = cache.get(cachekey);
            // null id denotes target is selected from the related list (not exact match)
            if ((cachedMapping.getId() == null || cachedMapping.isPartialMatchToCodeRecord()) //Jeremy '15,6,3 new record type to identify partial match
                    && cachedList != null && !cachedList.isEmpty()) {
                if (DEBUG) Log.i(TAG, "updateScoreCache(): updating related list");
                if (cache.remove(cachekey) == null) {
                    removeRemappedCodeCachedMappings(code);
                }
                // non null id denotes target is in exact match result list.
            } else if ((cachedMapping.getId() != null || cachedMapping.isExactMatchToCodeRecord()) //Jeremy '15,6,3 new record type to identify exact match
                    && cachedList != null && !cachedList.isEmpty()) {

                boolean sort;
                if (isPhysicalKeyboardPressed)
                    sort = mLIMEPref.getPhysicalKeyboardSortSuggestions();
                else
                    sort = mLIMEPref.getSortSuggestions();

                if (sort) { // Jeremy '12,5,22 do not update the order of exact match list if the sort option is off
                    int size = cachedList.size();
                    if (DEBUG) Log.i(TAG, "updateScoreCache(): cachedList.size:" + size);
                    // update exact match cache
                    for (int j = 0; j < size; j++) {
                        Mapping cm = cachedList.get(j);
                        if (DEBUG)
                            Log.i(TAG, "updateScoreCache(): cachedList at :" + j + ". score=" + cm.getScore());
                        if (cachedMapping.getId().equals(cm.getId())) {
                            int score = cm.getScore() + 1;
                            if (DEBUG)
                                Log.i(TAG, "updateScoreCache(): cachedMapping found at :" + j + ". new score=" + score);
                            cm.setScore(score);
                            if (j > 0 && score > cachedList.get(j - 1).getScore()) {
                                cachedList.remove(j);
                                for (int k = 0; k < j; k++) {
                                    if (cachedList.get(k).getScore() <= score) {
                                        cachedList.add(k, cm);
                                        break;
                                    }
                                }

                            }
                            break;
                        }
                    }
                }
                // Jeremy '11,7,31
                // exact match score was changed, related list in similar codes should be rebuild
                // (eg. d, de, and def for code, defg)
                updateSimilarCodeCache(code);


            } else {//Jeremy '12,6,5 code not in cache do removeRemappedCodeCachedMappings and removed cached items of  ramped codes.

                removeRemappedCodeCachedMappings(code);
            }
        }


    }

// '11,8,1 renamed from updateuserdict()
List<Mapping> scorelistSnapshot = null;

    /**
     * Tasks to perform after an input session finishes.
     * <p>
     * Spawns a background thread to:
     * <ul>
     *     <li>Learn related phrases from the recent score list.</li>
     *     <li>Update user dictionary/scores.</li>
     *     <li>Process LD phrases if applicable.</li>
     * </ul>
     *
     * @throws RemoteException If a database error occurs.
     */
    public void postFinishInput() throws RemoteException {

        if (scorelistSnapshot == null) scorelistSnapshot = new LinkedList<>();
        else scorelistSnapshot.clear();


        if (DEBUG) Log.i(TAG, "postFinishInput(), creating offline updating thread");
        // Jeremy '11,7,31 The updating process takes some time. Create a new thread to do this.
        Thread UpdatingThread = new Thread() {
            public void run() {
                // for thread-safe operation, duplicate local copy of scorelist and LDphraselistarray
                //List<Mapping> localScorelist = new LinkedList<Mapping>();
                if (scorelist != null) {
                    scorelistSnapshot.addAll(scorelist);
                    scorelist.clear();
                }
                //Jeremy '11,7,28 combine to adduserdict and addscore
                //Jeremy '11,6,12 do adduserdict and add score if diclist.size > 0 and only adduserdict if diclist.size >1
                //Jeremy '11,6,11, always learn scores, but sorted according preference options

                // Learn the consecutive two words as a related phrase).
                learnRelatedPhrase(scorelistSnapshot);

                ArrayList<List<Mapping>> localLDPhraseListArray = new ArrayList<>();
                if (LDPhraseListArray != null) {
                    localLDPhraseListArray.addAll(LDPhraseListArray);
                    LDPhraseListArray.clear();
                }

                // Learn LD Phrase
                learnLDPhrase(localLDPhraseListArray);

            }
        };
        UpdatingThread.start();

    }

    /**
     * Learns associations between consecutive words in a sentence (related phrases).
     *
     * @param localScorelist The list of mappings selected during the session.
     */
    private void learnRelatedPhrase(List<Mapping> localScorelist) {
        if (localScorelist != null) {
            if (DEBUG)
                Log.i(TAG, "learnRelatedPhrase(), localScorelist.size=" + localScorelist.size());
            if (mLIMEPref.getLearnRelatedWord() && localScorelist.size() > 1) {
                for (int i = 0; i < localScorelist.size(); i++) {
                    Mapping unit = localScorelist.get(i);
                    if (unit == null) {
                        continue;
                    }
                    if (i + 1 < localScorelist.size()) {
                        Mapping unit2 = localScorelist.get((i + 1));
                        if (unit2 == null) {
                            continue;
                        }
                        if (unit.getWord() != null && !unit.getWord().isEmpty()

                                && unit2.getWord() != null && !unit2.getWord().isEmpty()

                                &&
                                (unit.isExactMatchToCodeRecord() || unit.isPartialMatchToCodeRecord()
                                || unit.isRelatedPhraseRecord()) // use record type to identify records. Jeremy '15,6,4

                                &&
                                (unit2.isExactMatchToCodeRecord() || unit2.isPartialMatchToCodeRecord()
                                || unit.isRelatedPhraseRecord() || unit2.isChinesePunctuationSymbolRecord()
                                || unit.isEmojiRecord() || unit2.isEmojiRecord() )

                                //allow unit2 to be chinese punctuation symbols.
                                //&& !unit.getCode().equals(unit.getWord())//Jeremy '12,6,13 avoid learning mixed mode english
                                //&& !unit2.getCode().equals(unit2.getWord())
                                ///&& unit2.getId() !=null
                                ) {

                            int score;

                            //if (unit.getId() != null && unit2.getId() != null) //Jeremy '12,7,2 eliminate learning english words.
                            score = dbadapter.addOrUpdateRelatedPhraseRecord(unit.getWord(), unit2.getWord());
                            if (DEBUG)
                                Log.i(TAG, "learnRelatedPhrase(), the return score = " + score);
                            //Jeremy '12,6,7 learn LD phrase if the score of userdic is > 20
                            if (score > 20 && mLIMEPref.getLearnPhrase()) {
                                addLDPhrase(unit, false);
                                addLDPhrase(unit2, true);
                            }
                        }
                    }
                }
            }
        }
    }

    /**
     * Jeremy '12,6,9 Rewrite to support word with more than 1 characters
     */

    /**
     * Learns new phrases based on user input patterns (Learning Dictionary).
     *
     * @param localLDPhraseListArray The list of potential phrases to learn.
     */
    private void learnLDPhrase(ArrayList<List<Mapping>> localLDPhraseListArray) {
        if (DEBUG)
            Log.i(TAG, "learnLDPhrase()");

        if (localLDPhraseListArray != null && !localLDPhraseListArray.isEmpty()) {
            if (DEBUG)
                Log.i(TAG, "learnLDPhrase(): LDPhrase learning, arraysize =" + localLDPhraseListArray.size());


            for (List<Mapping> phraselist : localLDPhraseListArray) {
                if (DEBUG)
                    Log.i(TAG, "learnLDPhrase(): LDPhrase learning, current list size =" + phraselist.size());
                if (!phraselist.isEmpty() && phraselist.size() < 5) { //Jeremy '12,6,8 limit the phrase to have 4 chracters


                    String baseCode, LDCode="", QPCode = "", baseWord;

                    Mapping unit1 = phraselist.get(0);

                    if (DEBUG)
                        Log.i(TAG, "learnLDPhrase(): unit1.getId() = " + unit1.getId()
                                + ", unit1.getCode() =" + unit1.getCode()
                                + ", unit1.getWord() =" + unit1.getWord());

                    if (unit1 == null || unit1.getWord().isEmpty()
                            || unit1.getCode().equals(unit1.getWord())) //Jeremy '12,6,13 avoid learning mixed mode english
                    {
                        break;
                    }

                    baseCode = unit1.getCode();
                    baseWord = unit1.getWord();

                    if (baseWord.length() == 1) {
                        if (unit1.getId() == null //Jeremy '12,6,7 break if id is null (selected from related list)
                                || unit1.isPartialMatchToCodeRecord() //Jeremy '15,6,3 new record identification
                                || unit1.getCode() == null //Jeremy '12,6,7 break if code is null (selected from related phrase)
                                || unit1.getCode().isEmpty()
                                || unit1.isRelatedPhraseRecord()) {
                            List<Mapping> rMappingList = dbadapter.getMappingByWord(baseWord, tablename);
                            if (!rMappingList.isEmpty())
                                baseCode = rMappingList.get(0).getCode();
                            else
                                break; //look-up failed, abandon.
                        }
                        if (baseCode != null && !baseCode.isEmpty())
                            QPCode += baseCode.substring(0, 1);
                        else
                            break;//abandon the phrase learning process;

                        //if word length >0, lookup all codes and rebuild basecode and QPCode
                    } else if (baseWord.length() > 1 && baseWord.length() < 5) {
                        baseCode = "";
                        for (int i = 0; i < baseWord.length(); i++) {
                            String c = baseWord.substring(i, i + 1);
                            List<Mapping> rMappingList = dbadapter.getMappingByWord(c, tablename);
                            if (!rMappingList.isEmpty()) {
                                baseCode += rMappingList.get(0).getCode();
                                QPCode += rMappingList.get(0).getCode().substring(0, 1);
                            } else {
                                baseCode = ""; //r-lookup failed. abandon the phrase learning
                                break;
                            }
                        }
                    }


                    for (int i = 0; i < phraselist.size(); i++) {
                        if (i + 1 < phraselist.size()) {

                            Mapping unit2 = phraselist.get((i + 1));
                            if (unit2 == null || unit2.getWord().isEmpty() || unit2.isComposingCodeRecord() || unit2.isEnglishSuggestionRecord()) //Jeremy 15,6,4 exclude composing code
                            //|| unit2.getCode().equals(unit2.getWord())) //Jeremy '12,6,13 avoid learning mixed mode english
                            {
                                break;
                            }

                            String word2 = unit2.getWord();
                            String code2 = unit2.getCode();
                            baseWord += word2;

                            if (word2.length() == 1 && baseWord.length() < 5) { //limit the phrase size to 4
                                if (unit2.getId() == null //Jeremy '12,6,7 break if id is null (selected from related phrase)
                                        || unit2.isPartialMatchToCodeRecord() //Jeremy '15,6,3 new record identification
                                        || code2 == null //Jeremy '12,6,7 break if code is null (selected from relatedphrase)
                                        || code2.isEmpty()
                                        || unit2.isRelatedPhraseRecord()) {
                                    List<Mapping> rMappingList = dbadapter.getMappingByWord(word2, tablename);
                                    if (!rMappingList.isEmpty())
                                        code2 = rMappingList.get(0).getCode();
                                    else
                                        break;
                                }
                                if (code2 != null && !code2.isEmpty()) {
                                    baseCode += code2;
                                    QPCode += code2.substring(0, 1);
                                } else
                                    break; //abandon the phrase learning process;

                                //if word length >0, lookup all codes and rebuild basecode and QPCode
                            } else if (word2.length() > 1 && baseWord.length() < 5) {
                                for (int j = 0; j < word2.length(); j++) {
                                    String c = word2.substring(j, j + 1);
                                    List<Mapping> rMappingList = dbadapter.getMappingByWord(c, tablename);
                                    if (!rMappingList.isEmpty()) {
                                        baseCode += rMappingList.get(0).getCode();
                                        QPCode += rMappingList.get(0).getCode().substring(0, 1);
                                    } else //r-lookup failed. abandon the phrase learning
                                        break;
                                }
                            } else  // abandon the learing process.
                                break;


                            if (DEBUG)
                                Log.i(TAG, "learnLDPhrase(): code1 = " + unit1.getCode()
                                        + ", code2 = '" + code2
                                        + "', word1 = " + unit1.getWord()
                                        + ", word2 = " + word2
                                        + ", basecode = '" + baseCode
                                        + "', baseWord = " + baseWord
                                        + ", QPcode = '" + QPCode
                                        + "'.");
                            if (i + 1 == phraselist.size() - 1) {//only learn at the end of the phrase word '12,6,8
                                if (tablename.equals(LIME.DB_TABLE_PHONETIC)) {// remove tone symbol in phonetic table
                                    LDCode = baseCode.replaceAll("[3467 ]", "").toLowerCase(Locale.US);
                                    QPCode = QPCode.toLowerCase(Locale.US);
                                    if (LDCode.length() > 1) {
                                        dbadapter.addOrUpdateMappingRecord(LDCode, baseWord);
                                        removeRemappedCodeCachedMappings(LDCode);
                                        updateSimilarCodeCache(LDCode);
                                    }
                                    if (QPCode.length() > 1) {
                                        dbadapter.addOrUpdateMappingRecord(QPCode, baseWord);
                                        removeRemappedCodeCachedMappings(QPCode);
                                        updateSimilarCodeCache(QPCode);
                                    }
                                } else if (baseCode.length() > 1) {
                                    baseCode = baseCode.toLowerCase(Locale.US);
                                    dbadapter.addOrUpdateMappingRecord(baseCode, baseWord);
                                    removeRemappedCodeCachedMappings(baseCode);
                                    updateSimilarCodeCache(baseCode);
                                }
                                if (DEBUG)
                                    Log.i(TAG, "learnLDPhrase(): LDPhrase learning, baseCode = '" + baseCode
                                            + "', LDCode = '" + LDCode + "', QPCode=" + QPCode + "'."
                                            + ", baseWord" + baseWord);

                            }


                        }
                    }
                }
            }


        }
    }

    /**
     *
     */
    /**
     * Removes cached mappings for codes that have been remapped.
     *
     * @param code The original code.
     */
    private void removeRemappedCodeCachedMappings(String code) {
        if (DEBUG)
            Log.i(TAG, "removeRemappedCodeCachedMappings() on code ='" + code + "' coderemapcache.size=" + coderemapcache.size());
        List<String> codelist = coderemapcache.get(cacheKey(code));
        if (codelist != null) {
            for (String entry : codelist) {
                if (DEBUG)
                    Log.i(TAG, "removeRemappedCodeCachedMappings() remove code= '" + entry + "' from cache.");
                cache.remove(cacheKey(entry));
            }
        } else
            cache.remove(cacheKey(code)); //Jeremy '12,6,6 no remap. remove the code mapping from cache.
    }

    /**
     * Updates the cache for codes similar to the modified code (e.g., prefix matches).
     *
     * @param code The modified code.
     */
    private void updateSimilarCodeCache(String code) {
        if (DEBUG)
            Log.i(TAG, "updateSimilarCodeCache(): code = '" + code + "'");
        String cachekey;
        List<Mapping> cachedList;// = cache.get(cachekey);
        int len = code.length();
        if (len > 5) len = 5; //Jeremy '12,6,7 change max backward level to 5.
        for (int k = 1; k < len; k++) {
            String key = code.substring(0, code.length() - k);
            cachekey = cacheKey(key);
            cachedList = cache.get(cachekey);
            if (DEBUG)
                Log.i(TAG, "updateSimilarCodeCache(): cachekey = '" + cachekey + "' cachedList == null :" + (cachedList == null));
            if (cachedList != null) {
                cache.remove(cachekey);
            } else {
                if (DEBUG)
                    Log.i(TAG, "updateSimilarCodeCache(): code not in cache. update to db only on code = '" + key + "'");
                removeRemappedCodeCachedMappings(key);
            }
        }
        // Prefetch if code length == 1 (moved outside loop since loop doesn't execute when code.length() == 1)
        if (code.length() == 1) {
            try {
                getMappingByCode(code, !isPhysicalKeyboardPressed, false, true);
            } catch (RemoteException e) {
                Log.e(TAG, "Error in search operation", e);
            }
        }
    }


    /**
     * Converts an internal key code/string to its display name.
     *
     * @param code The key code.
     * @return The display name for the key.
     */
    public String keyToKeyname(String code) {
        //Jeremy '11,6,21 Build cache according using cachekey

        String cacheKey = cacheKey(code);
        String result = keynamecache.get(cacheKey);
        if (result == null) {
            //loadDBAdapter(); openLimeDatabase();
            result = dbadapter.keyToKeyName(code, tablename, true);
            keynamecache.put(cacheKey, result);
        }
        return result;
    }

    /**
     * Renamed from addUserDict and pass parameter with mapping directly Jeremy '12,6,5
     * Renamed to learnRelatedPhraseAndUpdateScore Jeremy '15,6,4
     */

    /**
     * Updates the score of a mapping and learns it as a related phrase.
     * <p>
     * This spawns a background thread to update the score cache and persistent storage asynchronously.
     *
     * @param updateMapping The mapping to update/learn.
     */
    public void learnRelatedPhraseAndUpdateScore(Mapping updateMapping)
    //String id, String code, String word,
    //String pword, int score, boolean isDictionary)
    {
        if (DEBUG) Log.i(TAG, "learnRelatedPhraseAndUpdateScore() ");

        if (scorelist == null) {
            scorelist = new ArrayList<>();
        }

        // Temp final Mapping Object For updateMapping thread.
        if (updateMapping != null) {
            final Mapping updateMappingTemp = new Mapping(updateMapping);

            // Jeremy '11,6,11. Always update score and sort according to preferences.
            scorelist.add(updateMappingTemp);
            Thread UpdatingThread = new Thread() {
                public void run() {
                    updateScoreCache(updateMappingTemp);
                }
            };
            UpdatingThread.start();
        }
    }

    /**
     * Adds a mapping to the Learning Dictionary (LD) phrase buffer.
     *
     * @param mapping The mapping to add.
     * @param ending  True if this mapping ends the current phrase sequence.
     */
    public void addLDPhrase(Mapping mapping,//String id, String code, String word, int score,
                            boolean ending) {
        if (LDPhraseListArray == null)
            LDPhraseListArray = new ArrayList<>();
        if (LDPhraseList == null)
            LDPhraseList = new LinkedList<>();


        if (mapping != null) { // force interruped if mapping=null
            LDPhraseList.add(mapping);
        }

        if (ending) {
            if (LDPhraseList.size() > 1)
                LDPhraseListArray.add(LDPhraseList);
            LDPhraseList = new LinkedList<>();
        }

        if (DEBUG) Log.i(TAG, "addLDPhrase()"//+mapping.getCode() + ". id=" + mapping.getId()
                + ". ending:" + ending
                + ". LDPhraseListArray.size=" + LDPhraseListArray.size()
                + ". LDPhraseList.size=" + LDPhraseList.size());


    }

    /**
     * Retrieves the list of installed keyboards.
     *
     * @return A list of {@link Keyboard} objects.
     * @throws RemoteException If a database error occurs.
     */
    public List<Keyboard> getKeyboardList() throws RemoteException {
        //if(dbadapter == null){dbadapter = new LimeDB(ctx);}
        return dbadapter.getKeyboardList();
    }

    /**
     * Retrieves the list of Input Methods (IMs).
     *
     * @return A list of {@link Im} objects.
     * @throws RemoteException If a database error occurs.
     */
    public List<Im> getImList() throws RemoteException {
        return dbadapter.getImList(null, null);
    }


    /**
     * Clears all runtime caches (score list, mappings, english, emoji, key names).
     *
     * @throws RemoteException If a database error occurs.
     */
    public void clear() throws RemoteException {
        if (scorelist != null) {
            scorelist.clear();
        }
        if (cache != null) {
            cache.clear();
        }
        if (engcache != null) {
            engcache.clear();
        }
        if (emojicache != null) {
            emojicache.clear();
        }
        if (keynamecache != null) {
            keynamecache.clear();
        }

        if (coderemapcache != null) {
            coderemapcache.clear();
        }
    }

    private static String lastEnglishWord = null;
    private static boolean noSuggestionsForLastEnglishWord = false;

    /**
     * Retrieves English word suggestions for a given prefix.
     *
     * @param word The prefix or word to search for.
     * @return A list of English word mappings.
     * @throws RemoteException If a database error occurs.
     */
    public synchronized List<Mapping> getEnglishSuggestions(String word) throws RemoteException {

        long startTime=0;
        if(DEBUG||dumpRunTimeSuggestion){
            startTime = System.currentTimeMillis();
            Log.i(TAG,"getEnglishSuggestions()");
        }

        List<Mapping> result = new LinkedList<>();

        //Jeremy '15,7,16 return zero result if last query returns no result
        if(!( word.length()>1 &&lastEnglishWord!=null &&word.startsWith(lastEnglishWord) && noSuggestionsForLastEnglishWord  ) ) {

            List<Mapping> cacheTemp = engcache.get(word);

            if (cacheTemp != null) {
                result.addAll(cacheTemp);
            } else {
                List<String> tempResult = dbadapter.getEnglishSuggestions(word);
                for (String u : tempResult) {
                    Mapping temp = new Mapping();
                    temp.setWord(u);
                    temp.setEnglishSuggestionRecord();
                    result.add(temp);
                }
                if (!result.isEmpty()) {
                    engcache.put(word, result);
                }
            }

            noSuggestionsForLastEnglishWord = result.isEmpty();
            lastEnglishWord = word;
        }

        if(DEBUG||dumpRunTimeSuggestion){
            Log.i(TAG,"getEnglishSuggestions() time elapsed =" + (System.currentTimeMillis() - startTime));
        }

        return result;

    }


    /**
     * Retrieves the selection key string for the current keyboard.
     * <p>
     * Selection keys are used for selecting candidates (e.g., "1234567890" or "asdfghjkl;").
     *
     * @return The selection key characters string.
     * @throws RemoteException If a database error occurs.
     */
    public String getSelkey() throws RemoteException {
        if (DEBUG)
            Log.i(TAG, "getSelkey():hasNumber:" + hasNumberMapping + "hasSymbol:" + hasSymbolMapping);
        String selkey;
        String table = tablename;
        if (tablename.equals(LIME.DB_TABLE_PHONETIC)) {
            table = tablename + mLIMEPref.getPhoneticKeyboardType();
        }
        if (selKeyMap.get(table) == null || selKeyMap.isEmpty()) {
            //if(dbadapter == null){dbadapter = new LimeDB(ctx);}
            selkey = dbadapter.getImInfo(tablename, "selkey");
            if (DEBUG)
                Log.i(TAG, "getSelkey():selkey from db:" + selkey);
            boolean validSelkey = true;
            if (selkey != null && selkey.length() == 10) {
                for (int i = 0; i < 10; i++) {
                    if (Character.isLetter(selkey.charAt(i)) ||
                            (hasNumberMapping && Character.isDigit(selkey.charAt(i))))
                        validSelkey = false;

                }
            } else
                validSelkey = false;
            //Jeremy '11,6,19 Rewrite for IM has symbol mapping like ETEN
            if (!validSelkey || tablename.equals(LIME.DB_TABLE_PHONETIC)) {
                if (hasNumberMapping && hasSymbolMapping) {
                    if (tablename.equals(LIME.DB_TABLE_DAYI)
                            || (tablename.equals(LIME.DB_TABLE_PHONETIC) && mLIMEPref.getPhoneticKeyboardType().equals(LIME.DB_TABLE_PHONETIC))) {
                        selkey = "'[]-\\^&*()";
                    } else {
                        selkey = "!@#$%^&*()";
                    }
                } else if (hasNumberMapping) {
                    selkey = "'[]-\\^&*()";
                } else {
                    selkey = "1234567890";
                }
            }
            if (DEBUG)
                Log.i(TAG, "getSelkey():selkey:" + selkey);
            selKeyMap.put(table, selkey);
        }
        return selKeyMap.get(table);
    }
/*
    private class runTimeSuggestion {

        private List<List<Pair<Mapping, String>>> suggestionLoL;
        private int level;

        public runTimeSuggestion() {
            suggestionLoL = new LinkedList<>();
        }

        public void addExactMatch(String code, List<Mapping> completeCodeResultList) {
            Mapping exactMatchMapping;
            level++;

            int i = 0;
            do {
                exactMatchMapping = completeCodeResultList.get(i);
                int score = exactMatchMapping.getBasescore();
                if (score < MIN_SCORE_THRESHOLD) {
                    score = MIN_SCORE_THRESHOLD;
                } else if (score > MAX_SCORE_THRESHOLD) {
                    score = MAX_SCORE_THRESHOLD;
                }
                int codeLenBonus = exactMatchMapping.getCode().length() / exactMatchMapping.getWord().length() * CODE_LENGTH_BONUS_MULTIPLIER;
                exactMatchMapping.setBasescore((score + codeLenBonus) * exactMatchMapping.getWord().length());

                if (DEBUG || dumpRunTimeSuggestion)
                    Log.i(TAG, "addExactMatch() complete code = " + code + "" +
                            ", got exact match  = " + exactMatchMapping.getWord()
                            + " score =" + exactMatchMapping.getScore() + ", basescore=" + exactMatchMapping.getBasescore());


                //push the exact match mapping with current code into exact match stack. '15,6,2 Jeremy
                if (exactMatchMapping.getBasescore() > 0) {
                    List<Pair<Mapping, String>> suggestionList = new LinkedList<>();
                    suggestionList.add(new Pair<>(exactMatchMapping, code));
                    suggestionLoL.add(suggestionList);
                }
                i++;
            }
            while (completeCodeResultList.size() > i
                    && completeCodeResultList.get(i).isExactMatchToCodeRecord() && i < 5); //process at most 5 exact match items.

        }

        public void checkRemainingCode(String code) {

            int highestScore = 0, highestRelatedScore = 0;
            //iterate all previous exact match mapping and check for exact match on remaining code.
            for (List<Pair<Mapping, String>> suggestionList : suggestionLoL) {
                for (Pair<Mapping, String> p : suggestionList) {
                    String pCode = p.second;
                    if (pCode.length() < code.length() && code.startsWith(pCode) && code.length() - pCode.length() <= 5) {
                        String remainingCode = code.substring(pCode.length(), code.length());
                        Log.i(TAG, "makeRunTimeSuggestion() working on previous exact match item = " + p.first.getWord() +
                                " with base score = " + p.first.getBasescore() + ", average score = " + p.first.getBasescore() / p.first.getWord().length() +
                                ", remainingCode =" + remainingCode);


                        Pair<List<Mapping>, List<Mapping>> resultPair =  //do remaining code query
                                getMappingByCodeFromCacheOrDB(remainingCode, false);
                        if (resultPair == null) continue;

                        List<Mapping> resultList = resultPair.first;
                        if (resultList.size() > 0
                                && resultList.get(0).isExactMatchToCodeRecord()) {  //remaining code search got exact match
                            Mapping remainingCodeExactMatchMapping = resultList.get(0);
                            Mapping previousMapping = p.first;
                            String phrase = previousMapping.getWord() + remainingCodeExactMatchMapping.getWord();
                            int phraseLen = phrase.length();
                            if (phraseLen < 2 || remainingCodeExactMatchMapping.getBasescore() < 2)
                                continue;
                            int remainingScore = remainingCodeExactMatchMapping.getBasescore();
                            int codeLenBonus = remainingCodeExactMatchMapping.getCode().length() /
                                    remainingCodeExactMatchMapping.getWord().length() * 30;
                            if (remainingScore > 120) remainingScore = 120;
                            remainingScore = remainingScore / remainingCodeExactMatchMapping.getWord().length() + codeLenBonus;

                            int previousScore = previousMapping.getBasescore() / previousMapping.getWord().length();
                            int averageScore = (previousScore + remainingScore) / 2;

                            if (DEBUG || dumpRunTimeSuggestion)
                                Log.i(TAG, "makeRunTimeSuggestion() remaining code = " + remainingCode + "" +
                                        ", got exact match  = " + remainingCodeExactMatchMapping.getWord() + " with base score = "
                                        + remainingScore + " average score =" + averageScore);

                            //verify if the new phrase is in related table.
                            // check up to four characters phrase 1-3, 1-2 , 1-1
                            Mapping relatedMapping = null;
                            for (int i = ((phraseLen < 4) ? phraseLen - 1 : 3); i > 0; i--) {
                                String pword = phrase.substring(phraseLen - i - 1, phraseLen - i);
                                String cword = phrase.substring(phraseLen - i, phraseLen);
                                relatedMapping = dbadapter.isRelatedPhraseExist(pword, cword);
                                if (relatedMapping != null) break;
                            }
                            if (relatedMapping != null
                                    && relatedMapping.getScore() >= highestRelatedScore
                                //&& averageScore > highestScore
                                    ) {
                                Mapping suggestMapping = new Mapping();
                                suggestMapping.setRuntimeBuiltPhraseRecord();
                                suggestMapping.setCode(code);
                                suggestMapping.setWord(phrase);
                                highestRelatedScore = relatedMapping.getBasescore();
                                suggestMapping.setScore(highestRelatedScore);

                                suggestMapping.setBasescore((averageScore + SCORE_ADJUSTMENT_INCREMENT) * phraseLen);
                                suggestionList.add(new Pair<>(suggestMapping, code));
                                if (DEBUG || dumpRunTimeSuggestion)
                                    Log.i(TAG, "makeRunTimeSuggestion()  run-time suggest phrase verified from related table ="
                                            + phrase + "score from related table = " + highestRelatedScore + " , new base score = " + suggestMapping.getBasescore());
                            } else if (highestRelatedScore == 0// no mapping is verified from related table
                                    && averageScore > highestScore) {
                                Mapping suggestMapping = new Mapping();
                                suggestMapping.setRuntimeBuiltPhraseRecord();
                                suggestMapping.setCode(code);
                                suggestMapping.setWord(phrase);
                                highestScore = averageScore;
                                suggestMapping.setBasescore(highestScore * phraseLen);
                                suggestionList.add(new Pair<>(suggestMapping, code));
                                if (DEBUG || dumpRunTimeSuggestion)
                                    Log.i(TAG, "makeRunTimeSuggestion()  run-time suggest phrase =" + phrase
                                            + ", new base score = " + highestScore);
                            }
                        }
                    }
                }
            }
        }

        public void clear() {
            level = 0;
            for (List<Pair<Mapping, String>> item : suggestionLoL) {
                if (item != null) item.clear();
            }
            suggestionLoL.clear();

        }

        public Mapping getBestSuggestion() {
            return null;
        }

    }
    */

    // ============================================================================
    // UI-Compatible Methods - Delegates to LimeDB for database operations
    // These methods allow UI components to access database operations through
    // SearchServer instead of directly accessing LimeDB, maintaining architectural
    // separation and enabling centralized caching/logging if needed in the future.
    // ============================================================================

    /**
     * Gets IM records filtered by code and/or configEntry.
     * 
     * <p>This method delegates to LimeDB.getIm() to retrieve IM information records.
     * UI components should use this method instead of directly accessing LimeDB.
     * 
     * @param code The IM code to filter by, or null/empty for all
     * @param configEntry The IM configEntry to filter by, or null/empty for all
     * @return List of Im objects, or empty list if database error
     */
    public List<Im> getImList(String code, String configEntry) {
        if (dbadapter == null) {
            Log.e(TAG, "getIm(): dbadapter is null");
            return new ArrayList<>();
        }
        return dbadapter.getImList(code, configEntry);
    }

    /**
     * Gets a list of all keyboards from the database.
     * 
     * <p>This method delegates to LimeDB.getKeyboard() to retrieve keyboard records.
     * UI components should use this method instead of directly accessing LimeDB.
     * 
     * @return List of Keyboard objects, or empty list if database error
     */
    public List<Keyboard> getKeyboard() {
        if (dbadapter == null) {
            Log.e(TAG, "getKeyboard(): dbadapter is null");
            return new ArrayList<>();
        }
        return dbadapter.getKeyboardList();
    }


    /**
     * Counts the number of mapping records in a table.
     * 
     * <p>This method delegates to LimeDB.countRecords() to count mapping records.
     * UI components should use this method instead of directly accessing LimeDB.
     * 
     * @param im The table name to count records in
     * @return The number of records, or 0 if database error
     */


    public String getImInfo(String im, String field) {
        if (dbadapter == null) {
            Log.e(TAG, "getImInfo(): dbadapter is null");
            return "";
        }
        return dbadapter.getImInfo(im, field);
    }

    /**
     * Sets IM information for a specific field.
     *
     * <p>This method delegates to LimeDB.setImInfo() to store or update configuration
     * information in the im table. UI components should use this method instead of
     * directly accessing LimeDB.
     *
     * @param im    The IM code (e.g., LIME.DB_TABLE_PHONETIC, LIME.DB_TABLE_DAYI)
     * @param field The field name to set
     * @param value The value to store
     * @return
     */
    public boolean setImInfo(String im, String field, String value) {
        if (dbadapter == null) {
            Log.e(TAG, "setImInfo(): dbadapter is null");
            return false;
        }
        dbadapter.setImInfo(im, field, value);
        return false;
    }

    /**
     * Sets the keyboard assignment for an IM using string parameters.
     * 
     * <p>This method delegates to LimeDB.setIMKeyboard() to store keyboard
     * configuration in the im table. UI components should use this method instead
     * of directly accessing LimeDB.
     * 
     * @param im The IM code
     * @param value The keyboard description/name
     * @param keyboard The keyboard code
     */
    public void setIMKeyboard(String im, String value, String keyboard) {
        if (dbadapter == null) {
            Log.e(TAG, "setIMKeyboard(): dbadapter is null");
            return;
        }
        dbadapter.setIMKeyboard(im, value, keyboard);
    }

    /**
     * Sets the keyboard assignment for an IM using a Keyboard object.
     * 
     * <p>This method delegates to LimeDB.setImKeyboard() to store keyboard
     * configuration in the im table. UI components should use this method instead
     * of directly accessing LimeDB.
     * 
     * @param code The IM code
     * @param keyboard The Keyboard object containing keyboard information
     */
    public void setIMKeyboard(String code, Keyboard keyboard) {
        if (dbadapter == null) {
            Log.e(TAG, "setIMKeyboard(): dbadapter is null");
            return;
        }
        dbadapter.setImKeyboard(code, keyboard);
    }

    /**
     * Backs up user-learned records to a backup table.
     * 
     * <p>This method delegates to LimeDB.backupUserRecords() to create a backup table
     * containing user-learned records (score > 0). UI components should use this method
     * instead of directly accessing LimeDB.
     * 
     * @param table The table name to backup user records from
     */
    public void backupUserRecords(String table) {
        if (dbadapter == null) {
            Log.e(TAG, "backupUserRecords(): dbadapter is null");
            return;
        }
        dbadapter.backupUserRecords(table);
    }

    /**
     * Restores user-learned records from a backup table to the main table.
     * 
     * <p>This method delegates to LimeDB.restoreUserRecords() to restore user-learned
     * records from a backup table (typically named "{table}_user") back to the main
     * mapping table. UI components should use this method instead of directly accessing LimeDB.
     * 
     * <p>The method performs the following operations:
     * <ul>
     *   <li>Validates that the database adapter is available</li>
     *   <li>Delegates to LimeDB.restoreUserRecords() which validates the table name</li>
     *   <li>Retrieves all records from the backup table</li>
     *   <li>Restores each record to the main table using addOrUpdateMappingRecord</li>
     * </ul>
     * 
     * @param table The base table name to restore records to (e.g., "cj", "phonetic")
     * @return The number of records restored, or 0 if no records to restore or error
     */
    public int restoreUserRecords(String table) {
        if (dbadapter == null) {
            Log.e(TAG, "restoreUserRecords(): dbadapter is null");
            return 0;
        }
        
        try {
            return dbadapter.restoreUserRecords(table);
        } catch (Exception e) {
            Log.e(TAG, "restoreUserRecords(): Error restoring user records for table: " + table, e);
            return 0;
        }
    }

    /**
     * Gets a single record by ID.
     * 
     * <p>This method delegates to LimeDB.getRecord() to retrieve a record.
     * UI components should use this method instead of directly accessing LimeDB.
     * 
     * @param code The table name (code)
     * @param id The record ID
     * @return Record object, or null if not found or database error
     */
    public Record getRecord(String code, long id) {
        if (dbadapter == null) {
            Log.e(TAG, "getRecord(): dbadapter is null");
            return null;
        }
        return dbadapter.getRecord(code, id);
    }

    /**
     * Gets the count of records matching a query by word or code.
     * 
     * <p>This method delegates to LimeDB.countRecords() to get the count of matching records.
     * UI components should use this method instead of directly accessing LimeDB.
     * 
     * @param table The table name to query
     * @param curQuery The search query, or null/empty for all records
     * @param searchByCode If true, search by code; if false, search by word
     * @return The count of matching records, or 0 if database error
     */
    public int countRecordsByWordOrCode(String table, String curQuery, boolean searchByCode) {
        if(DEBUG)
            Log.i(TAG,"countRecordsByWordOrCode()");
        if (dbadapter == null) {
            Log.e(TAG, "countRecordsByWordOrCode(): dbadapter is null");
            return 0;
        }
        // Build WHERE clause for countRecords() with parameterized queries
        StringBuilder whereBuilder = new StringBuilder();
        List<String> whereArgsList = new ArrayList<>();

        if (curQuery != null && !curQuery.isEmpty()) {
            if (searchByCode) {
                whereBuilder.append(LIME.DB_COLUMN_CODE).append(" LIKE ? AND ");
                whereArgsList.add(curQuery + "%");
            } else {
                whereBuilder.append(LIME.DB_COLUMN_WORD).append(" LIKE ? AND ");
                whereArgsList.add("%" + curQuery + "%");
            }
        }
        whereBuilder.append("ifnull(").append(LIME.DB_COLUMN_WORD).append(", '') <> ''");

        String[] whereArgs = whereArgsList.isEmpty() ? null : whereArgsList.toArray(new String[0]);
        return dbadapter.countRecords(table, whereBuilder.toString(), whereArgs);
    }

    /**
     * Deletes a record from a table using a parameterized query.
     * 
     * <p>This method delegates to LimeDB.deleteRecord() to safely delete records.
     * UI components should use this method instead of directly accessing LimeDB.
     * 
     * @param table The table name
     * @param whereClause WHERE clause with "?" placeholders
     * @param whereArgs Arguments for the WHERE clause
     * @return Number of rows deleted, or 0 if error
     */
    public int deleteRecord(String table, String whereClause, String[] whereArgs) {
        if (dbadapter == null) {
            Log.e(TAG, "deleteRecord(): dbadapter is null");
            return 0;
        }
        return dbadapter.deleteRecord(table, whereClause, whereArgs);
    }

    /**
     * Adds or updates a mapping record in the database.
     * 
     * <p>This method delegates to LimeDB.addOrUpdateMappingRecord() to store or update
     * word mappings. UI components should use this method instead of directly accessing LimeDB.
     * 
     * @param table The table name
     * @param code The code
     * @param word The word
     * @param score The score
     */
    public void addOrUpdateMappingRecord(String table, String code, String word, int score) {
        if (dbadapter == null) {
            Log.e(TAG, "addOrUpdateMappingRecord(): dbadapter is null");
            return;
        }
        dbadapter.addOrUpdateMappingRecord(table, code, word, score);
    }

    /**
     * Adds a record to a table using ContentValues.
     * 
     * <p>This method delegates to LimeDB.addRecord() to safely insert records.
     * UI components should use this method instead of directly accessing LimeDB.
     * 
     * @param table The table name
     * @param values The ContentValues containing column values
     * @return The row ID of the newly inserted row, or -1 if error
     */
    public long addRecord(String table, android.content.ContentValues values) {
        if (dbadapter == null) {
            Log.e(TAG, "addRecord(): dbadapter is null");
            return -1;
        }
        return dbadapter.addRecord(table, values);
    }

    /**
     * Counts the total number of records in the specified table.
     * 
     * <p>This method delegates to LimeDB.countRecords() to get the count of records.
     * Filters out records with null/empty values to match getRecords() behavior.
     * UI components should use this method instead of directly accessing LimeDB.
     * 
     * @param table The table name to count records from
     * @return The number of records in the table (excluding null/empty key values), or 0 if error or empty
     */
    public int countRecords(String table) {
        if (dbadapter == null) {
            Log.e(TAG, "countRecords(): dbadapter is null");
            return 0;
        }
        
        // Apply table-specific filters to match getRecords behavior
        String whereClause = null;
        if (LIME.DB_TABLE_RELATED.equals(table)) {
            // For related table: exclude null/empty pword or cword
            whereClause = "ifnull(" + LIME.DB_RELATED_COLUMN_PWORD + ", '') <> '' AND ifnull(" + LIME.DB_RELATED_COLUMN_CWORD + ", '') <> ''";
        } else {
            // For IM tables: exclude null/empty word
            whereClause = "ifnull(" + LIME.DB_COLUMN_WORD + ", '') <> ''";
        }
        
        return dbadapter.countRecords(table, whereClause, null);
    }

    /**
     * Clears a mapping table by deleting all records and clearing the cache.
     * 
     * <p>This method delegates to LimeDB.resetMapping() to safely delete all records from
     * the specified table and reset the cache. UI components should use this method
     * instead of directly accessing LimeDB.
     * 
     * <p>The method performs the following operations:
     * <ul>
     *   <li>Validates that the database adapter is available</li>
     *   <li>Delegates to LimeDB.resetMapping() which validates the table name</li>
     *   <li>Deletes all records from the specified table</li>
     *   <li>Resets the SearchServer cache to ensure consistency</li>
     * </ul>
     * 
     * <p>If the database adapter is null or the table name is invalid, the method
     * will log an error and return without performing any operations.
     * 
     * @param table The table name to clear (must be valid according to LimeDB.isValidTableName())
     * @throws IllegalArgumentException if table name is null or empty (propagated from LimeDB)
     */
    public void clearTable(String table) {
        if (dbadapter == null) {
            Log.e(TAG, "clearTable(): dbadapter is null");
            return;
        }
        
        try {
            dbadapter.clearTable(table);
        } catch (IllegalArgumentException e) {
            Log.e(TAG, "clearTable(): Invalid table name: " + table, e);
            throw e;
        } catch (Exception e) {
            Log.e(TAG, "clearTable(): Error clearing table: " + table, e);
        }
    }

    /**
     * Resets the SearchServer cache.
     * 
     * <p>This method delegates to LimeDB.resetCache() to clear the cache maintained
     * by SearchServer. UI components should use this method instead of directly
     * accessing LimeDB.
     */
    public void resetCache() {
        if (dbadapter == null) {
            Log.e(TAG, "resetCache(): dbadapter is null");
            return;
        }
        dbadapter.resetCache();
    }

    /**
     * Checks and updates phonetic keyboard settings consistency between preferences and database.
     * 
     * <p>This method delegates to LimeDB.checkPhoneticKeyboardSetting() to ensure that the
     * keyboard configuration stored in the database matches the user's preference setting.
     * It handles different phonetic keyboard types (hsu, eten26, eten, standard).
     * 
     * <p>UI components should use this method instead of directly accessing LimeDB.
     */
    public void checkPhoneticKeyboardSetting() {
        if (dbadapter == null) {
            Log.e(TAG, "checkPhoneticKeyboardSetting(): dbadapter is null");
            return;
        }
        dbadapter.checkPhoneticKeyboardSetting();
    }

    /**
     * Checks if a backup table exists and has records.
     * 
     * <p>This method delegates to LimeDB.checkBackuptable() to check if user data
     * backup exists. UI components should use this method instead of directly accessing LimeDB.
     * 
     * @param table The base table name to check backup for
     * @return true if backup table exists and has records, false otherwise
     */
    public boolean checkBackupTable(String table) {
        if (dbadapter == null) {
            Log.e(TAG, "checkBackuptable(): dbadapter is null");
            return false;
        }
        return dbadapter.checkBackupTable(table);
    }

    /**
     * Gets all records from a backup table.
     * 
     * <p>This method delegates to LimeDB.getBackupTableRecords() to retrieve backup records.
     * UI components should use this method instead of directly accessing LimeDB.
     * 
     * @param backupTableName The backup table name (must end with "_user", e.g., "cj_user")
     * @return Cursor with all records from the backup table, or null if invalid or error
     */
    public android.database.Cursor getBackupTableRecords(String backupTableName) {
        if (dbadapter == null) {
            Log.e(TAG, "getBackupTableRecords(): dbadapter is null");
            return null;
        }
        return dbadapter.getBackupTableRecords(backupTableName);
    }

    /**
     * Removes IM information for a specific field.
     * 
     * <p>This method delegates to LimeDB.removeImInfo() to delete configuration information.
     * UI components should use this method instead of directly accessing LimeDB.
     * 
     * @param im The IM code
     * @param field The field name to remove
     */
    public void removeImInfo(String im, String field) {
        if (dbadapter == null) {
            Log.e(TAG, "removeImInfo(): dbadapter is null");
            return;
        }
        dbadapter.removeImInfo(im, field);
    }

    /**
     * Resets all IM information for a specific IM.
     * 
     * <p>This method delegates to LimeDB.resetImInfo() to clear all configuration
     * information for an IM. UI components should use this method instead of
     * directly accessing LimeDB.
     * 
     * @param im The IM code to reset
     */
    public void resetImInfo(String im) {
        if (dbadapter == null) {
            Log.e(TAG, "resetImInfo(): dbadapter is null");
            return;
        }
        dbadapter.resetImInfo(im);
    }

    /**
     * Resets all LIME settings to factory defaults.
     * 
     * <p>This method delegates to LimeDB.resetLimeSetting() to reset all databases
     * (main, emoji, han converter) to factory defaults. This is a destructive operation
     * that will erase all user data including learned mappings and related phrases.
     * 
     * <p>UI components should use this method instead of directly accessing LimeDB.
     */
    public void restoredToDefault() {
        if (dbadapter == null) {
            Log.e(TAG, "resetLimeSetting(): dbadapter is null");
            return;
        }
        dbadapter.restoredToDefault();
    }

    /**
     * Gets keyboard information for a specific field.
     * 
     * <p>This method delegates to LimeDB.getKeyboardInfo() to retrieve keyboard
     * configuration information. UI components should use this method instead of
     * directly accessing LimeDB.
     * 
     * @param keyboardCode The keyboard code (e.g., "lime", "limenum")
     * @param field The field name to retrieve
     * @return The field value, or null if not found or database error
     */
    public String getKeyboardInfo(String keyboardCode, String field) {
        if (dbadapter == null) {
            Log.e(TAG, "getKeyboardInfo(): dbadapter is null");
            return null;
        }
        return dbadapter.getKeyboardInfo(keyboardCode, field);
    }


    /**
     * Gets keyboard object information for a specific keyboard code.
     * 
     * <p>This method delegates to LimeDB.getKeyboardConfig() to retrieve keyboard
     * configuration including layout definitions. UI components should use this
     * method instead of directly accessing LimeDB.
     * 
     * @param keyboard The keyboard code (e.g., "lime", "limenum", "wb", "hs")
     * @return KeyboardObj with keyboard information, or null if not found or database error
     */
    public Keyboard getKeyboardConfig(String keyboard) {
        if (dbadapter == null) {
            Log.e(TAG, "getKeyboardConfig(): dbadapter is null");
            return null;
        }
        return dbadapter.getKeyboardConfig(keyboard);
    }

    /**
     * Loads records from a table with optional filtering and pagination.
     * 
     * <p>This method delegates to LimeDB.getRecords() to retrieve records.
     * UI components should use this method instead of directly accessing LimeDB.
     * 
     * @param code The table name (code)
     * @param query The search query, or null/empty for all records
     * @param searchByCode If true, search by code; if false, search by word
     * @param maximum Maximum number of records to return (0 for no limit)
     * @param offset Number of records to skip (0 for no offset)
     * @return List of Record objects, or empty list if error
     */
    public List<Record> getRecords(String code, String query, boolean searchByCode, int maximum, int offset) {
        if (dbadapter == null) {
            Log.e(TAG, "getRecords(): dbadapter is null");
            return new ArrayList<>();
        }
        return dbadapter.getRecordList(code, query, searchByCode, maximum, offset);
    }


    /**
     * Gets the count of related phrase records for a parent word.
     * 
     * <p>This method delegates to LimeDB.countRecords() to get the count of related phrases.
     * UI components should use this method instead of directly accessing LimeDB.
     * 
     * @param pword The parent word to count related phrases for
     * @return The count of related phrases, or 0 if database error or not found
     */
    public int countRecordsRelated(String pword) {
        if (dbadapter == null) {
            Log.e(TAG, "countRecords(): dbadapter is null");
            return 0;
        }
        // Build WHERE clause for related table
        StringBuilder whereBuilder = new StringBuilder();
        List<String> whereArgsList = new ArrayList<>();
        
        String cword = "";
        if (pword != null && !pword.isEmpty() && pword.length() > 1) {
            cword = pword.substring(1);
            pword = pword.substring(0, 1);
        }
        
        if (pword != null && !pword.isEmpty()) {
            whereBuilder.append(LIME.DB_RELATED_COLUMN_PWORD).append(" = ? AND ");
            whereArgsList.add(pword);
        }
        if (!cword.isEmpty()) {
            whereBuilder.append(LIME.DB_RELATED_COLUMN_CWORD).append(" LIKE ? AND ");
            whereArgsList.add(cword + "%");
        }
        
        whereBuilder.append("ifnull(").append(LIME.DB_RELATED_COLUMN_CWORD).append(", '') <> ''");
        
        String[] whereArgs = whereArgsList.isEmpty() ? null : whereArgsList.toArray(new String[0]);
        return dbadapter.countRecords(LIME.DB_TABLE_RELATED, whereBuilder.toString(), whereArgs);
    }

    /**
     * Checks if a related phrase exists.
     * 
     * <p>This method delegates to LimeDB.countRecords() to check for related phrase existence.
     * UI components should use this method instead of directly accessing LimeDB.
     * 
     * @param pword The parent word (must not be null or empty)
     * @param cword The child word (must not be null or empty)
     * @return true if related phrase exists, false otherwise
     */
    public boolean hasRelated(String pword, String cword) {
        if (dbadapter == null) {
            Log.e(TAG, "hasRelated(): dbadapter is null");
            return false;
        }
        // Build WHERE clause for related table
        StringBuilder whereBuilder = new StringBuilder();
        List<String> whereArgsList = new ArrayList<>();
        
        if (pword != null && !pword.isEmpty()) {
            whereBuilder.append(LIME.DB_RELATED_COLUMN_PWORD).append(" = ? AND ");
            whereArgsList.add(pword);
        }
        if (cword != null && !cword.isEmpty()) {
            whereBuilder.append(LIME.DB_RELATED_COLUMN_CWORD).append(" = ?");
            whereArgsList.add(cword);
        } else {
            whereBuilder.append(LIME.DB_RELATED_COLUMN_CWORD).append(" IS NULL");
        }
        
        String[] whereArgs = whereArgsList.isEmpty() ? null : whereArgsList.toArray(new String[0]);
        int count = dbadapter.countRecords(LIME.DB_TABLE_RELATED, whereBuilder.toString(), whereArgs);
        return count > 0;
    }

    /**
     * Updates records in a table using parameterized queries.
     * 
     * <p>This method delegates to LimeDB.updateRecord() to safely update records.
     * UI components should use this method instead of directly accessing LimeDB.
     * 
     * @param table The table name
     * @param values The values to update
     * @param whereClause WHERE clause with "?" placeholders
     * @param whereArgs Arguments for the WHERE clause
     * @return Number of rows updated, or -1 if error
     */
    public int updateRecord(String table, android.content.ContentValues values, String whereClause, String[] whereArgs) {
        if (dbadapter == null) {
            Log.e(TAG, "updateRecord(): dbadapter is null");
            return -1;
        }
        return dbadapter.updateRecord(table, values, whereClause, whereArgs);
    }


    /**
     * Gets related phrase records with optional filtering and pagination.
     * 
     * <p>This method delegates to LimeDB.getRelated() to retrieve related phrase records.
     * UI components should use this method instead of directly accessing LimeDB.
     * 
     * @param pword The parent word to search for, or null/empty for all
     * @param maximum Maximum number of records to return (0 for no limit)
     * @param offset Number of records to skip (0 for no offset)
     * @return List of Related objects, or empty list if error
     */
    public List<Related> getRelatedByWord(String pword, int maximum, int offset) {
        if (dbadapter == null) {
            Log.e(TAG, "getRelatedByWord(): dbadapter is null");
            return new ArrayList<>();
        }
        return dbadapter.getRelated(pword, maximum, offset);
    }

    /**
     * Gets a list of IM information records for a specific IM code.
     * 
     * <p>This method delegates to LimeDB.getImList() to retrieve IM information.
     * UI components should use this method instead of directly accessing LimeDB.
     * 
     * @param code The IM code to retrieve information for
     * @return List of Im objects, or null if database error
     */
    public List<net.toload.main.hd.data.Im> getImList(String code) {
        if (dbadapter == null) {
            Log.e(TAG, "getImList(): dbadapter is null");
            return null;
        }
        return dbadapter.getImList(code, null);
    }

    /**
     * Validates if a table name is valid according to LimeDB whitelist.
     * 
     * <p>This method delegates to LimeDB.isValidTableName() to validate table names.
     * UI components should use this method instead of directly accessing LimeDB.
     * 
     * @param tableName The table name to validate
     * @return true if the table name is valid, false otherwise
     */
    public boolean isValidTableName(String tableName) {
        if (dbadapter == null) {
            Log.e(TAG, "isValidTableName(): dbadapter is null");
            return false;
        }
        return dbadapter.isValidTableName(tableName);
    }

}