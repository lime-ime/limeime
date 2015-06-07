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

package net.toload.main.hd;

import android.content.Context;
import android.content.Intent;
import android.os.RemoteException;
import android.util.Log;
import android.util.Pair;

import net.toload.main.hd.data.ImObj;
import net.toload.main.hd.data.KeyboardObj;
import net.toload.main.hd.global.LIME;
import net.toload.main.hd.global.LIMEPreferenceManager;
import net.toload.main.hd.global.LIMEUtilities;
import net.toload.main.hd.data.Mapping;
import net.toload.main.hd.limedb.LimeDB;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.Stack;
import java.util.concurrent.ConcurrentHashMap;

public class SearchServer {

	private static final boolean DEBUG = false;
	private static final String TAG = "LIME.SearchServer";
	private static LimeDB dbadapter = null; //Jeremy '12,5,1 shared single LIMEDB object
	//Jeremy '12,4,6 Combine updatedb and quierydb into db,
	//Jeremy '12,4,7 move db open/clsoe back to LimeDB
	//	since query always following with userdict and related learning and dual db connections cause exceptions.
	//private SQLiteDatabase db = null;
	//private LimeHanConverter hanConverter = null;
	//private static LinkedList<Mapping> diclist = null;
	private static List<Mapping> scorelist = null;

	private static Stack<Pair<Mapping, String>> exactMatchStack;  //Jeremy '15,6,2 preserve the exact match mapping with the code user typed.
	private static String lastCode; // preserved the last code queried from LIMEService

	private static boolean mResetCache;

	private static List<List<Mapping>> LDPhraseListArray = null;
	private static List<Mapping> LDPhraseList = null;

	//private static StringBuffer selectedText = new StringBuffer();
	private static String tablename = "";

	//private NotificationManager notificationMgr;

	private LIMEPreferenceManager mLIMEPref;


	//private static SearchServiceImpl obj = null;

	//private static int recAmount = 0;
	private static boolean isPhysicalKeyboardPressed; // Sync to LIMEService and LIMEDB
	//Jeremy '11,6,10
	private static boolean hasNumberMapping;
	private static boolean hasSymbolMapping;


	//Jeremy '11,6,6
	//private HashMap<String, String> imKeysMap = new HashMap<>();
	private HashMap<String, String> selKeyMap = new HashMap<>();

	private static ConcurrentHashMap<String, Pair<List<Mapping>, List<Mapping>>> cache = null;
	private static ConcurrentHashMap<String, List<Mapping>> engcache = null;
	private static ConcurrentHashMap<String, String> keynamecache = null;
	/**
	 * Store the mapping of typing code and mapped code from getMappingByCode on db  Jeremy '12,6,5
	 */
	private static ConcurrentHashMap<String, List<String>> coderemapcache = null;

	private Context mContext = null;

	// deprecated and using exact match stack to get real code length now. Jerey '15,6,2
	//private static List<Pair<Integer, Integer>> codeLenthMap = new LinkedList<>();

	public SearchServer(Context context) {


		this.mContext = context;

		mLIMEPref = new LIMEPreferenceManager(mContext.getApplicationContext());
		if (dbadapter == null) dbadapter = new LimeDB(mContext);
		initialCache();


	}

	public static void resetCache(boolean resetCache) {
		mResetCache = resetCache;
	}

 	/*
	public void setSelectedText(String text){
		selectedText = new StringBuffer();
		selectedText.append(text);
	}

	public String getSelectedText(){
		if(selectedText != null){
			return selectedText.toString().trim();
		}else{
			return "";
		}
	}
	*/

	public String hanConvert(String input) {
		return dbadapter.hanConvert(input, mLIMEPref.getHanCovertOption());

	}

	public String getTablename() {
		return tablename;
	}

	public void setTablename(String table, boolean numberMapping, boolean symbolMapping) {
		if (DEBUG)
			Log.i(TAG, "SearchService.setTablename()");

		dbadapter.setTablename(table);
		tablename = table;
		hasNumberMapping = numberMapping;
		hasSymbolMapping = symbolMapping;

		//run prefetch on first keys thread to feed the data into cache first for better response on large table.  Jeremy '15, 6,7
		if(cache.get(cacheKey("a"))==null){  // no cache records present. do prefetch now.  '15,6,7
			prefetchCache(numberMapping, symbolMapping);
		}


	}

	private static Thread prefetchThread;
	private void prefetchCache(boolean numberMapping, boolean symbolMapping) {
		Log.i(TAG, "prefetchCache() on table :" + tablename );

		String keys = "abcdefghijklmnoprstuvwxyz";
		if(numberMapping)
			keys += "01234567890";
		if(symbolMapping)
		    keys += ",./;";
		final String finalKeys = keys;

		if(prefetchThread!=null && prefetchThread.isAlive()) return;

		prefetchThread = new Thread(){
			public void run() {
				long startime = System.currentTimeMillis();
				for (int i = 0; i < finalKeys.length(); i++) {
					String key = finalKeys.substring(i, i + 1);
					try {
						getMappingByCode(key, true, false);
					} catch (RemoteException e) {
						e.printStackTrace();
					}
				}
				Log.i(TAG, "prefetchCache() on table :" + tablename  + " finished.  Elapsed time = "
						+ (System.currentTimeMillis()-startime) + " ms." );
			}
		};
		prefetchThread.start();

	}

	//Deprecated by Jeremy '12,4,7
	/*
		@Deprecated
		private void openLimeDatabase()
		{
			boolean reload = mLIMEPref.getParameterBoolean("reload_database", false);

			if(DEBUG) {
				Log.i(TAG,"SearchService:openLimeDatabase(), reload = " + reload );
				if(db != null)
					Log.i(TAG, "db.isOpen()" + db.isOpen());
			}


			try{

				if(reload && db != null && db.isOpen()){
					mLIMEPref.setParameter("reload_database", false);
					db.close();
				}
				//if(reload && db != null && db.isOpen()){
				//	mLIMEPref.setParameter("reload_database", false);
				//	db.close();
				//}
			}catch(Exception e){
			}
			//if(db == null || !db.isOpen()){

			//	db = getSqliteDb();
			//	initialCache();
			//}
			if(db == null || !db.isOpen()){

				db = dbadapter.getSqliteDb(false);
				initialCache();
			}
		}

	 */
	/*
	private void loadDBAdapter()
	{
		if(DEBUG)
			Log.i(TAG,"SearchService:loadDBAdapter()");
		//if(dbadapter == null){
			dbadapter = new LimeDB(ctx);
		}
	}
	*/

	//Modified by Jeremy '10,3 ,12 for more specific related word
	//-----------------------------------------------------------
	public List<Mapping> getRelatedPhrase(String word, boolean getAllRecords) throws RemoteException {

		return dbadapter.getRelatedPhrase(word, getAllRecords);
	}
	//-----------------------------------------------------------

	/*
	public Cursor getDictionaryAll(){

		return dbadapter.getDictionaryAll();

	}
	*/

	//Add by jeremy '10, 4,1
	public void getCodeListStringFromWord(final String word) throws RemoteException {

		Thread queryThread = new Thread() {
			public void run() {
				String result = dbadapter.getCodeListStringByWord(word);
				if (result != null && !result.equals("")) {
					//displayNotificationMessage(result);
					LIMEUtilities.showNotification(
							mContext, true, R.drawable.logo, mContext.getText(R.string.ime_setting), result, new Intent(mContext, MainActivity.class));
				}
			}
		};
		queryThread.start();

	}

	private String cacheKey(String code) {
		String key;

		//Jeremy '11,6,17 Seperate physical keyboard cache with keybaordtype
		if (isPhysicalKeyboardPressed) {
			if (tablename.equals("phonetic")) {
				key = mLIMEPref.getPhysicalKeyboardType() + dbadapter.getTablename()
						+ mLIMEPref.getPhoneticKeyboardType() + code;
			} else {
				key = mLIMEPref.getPhysicalKeyboardType() + dbadapter.getTablename() + code;
			}
		} else {
			if (tablename.equals("phonetic"))
				key = dbadapter.getTablename() + mLIMEPref.getPhoneticKeyboardType() + code;
			else
				key = dbadapter.getTablename() + code;
		}
		return key;
	}

	public void resetExactExactMatchStack(){
		exactMatchStack.clear();
	}

	public List<Mapping> getMappingByCode(String code, boolean softkeyboard, boolean getAllRecords) throws RemoteException {
		if (DEBUG) Log.i(TAG, "getMappingByCode(): code=" + code);
		// Check if system need to reset cache
		//check reset cache with local variable instead of reading from shared preference for better perfomance
		if (mResetCache) {
			initialCache();
			mResetCache = false;
		}


		if(exactMatchStack!=null && !exactMatchStack.isEmpty() ){
			// code is start over, clear the stack.  The composition is start over.   Jeremy'15,6,4.
			if(code.length() == 1 ){
				exactMatchStack.clear();
			}else if(code.length() ==  lastCode.length() -1  //user press backspace.
					&& exactMatchStack.lastElement().second.equals(lastCode)){
				//pop the last kept exact match mapping because it's canceled by backspace.  Jeremy 15,6,4
				exactMatchStack.pop();

			}

		}
		lastCode = code;

		//codeLenthMap.clear();//Jeremy '12,6,2 reset the codeLengthMap

		List<Mapping> result = new LinkedList<>();
		if (code != null) {
			// clear mappingidx when user switching between softkeyboard and hard keyboard. Jeremy '11,6,11
			if (isPhysicalKeyboardPressed == softkeyboard)
				isPhysicalKeyboardPressed = !softkeyboard;

			// Jeremy '11,9, 3 remove cached keyname when request full records
			if (getAllRecords && keynamecache.get(cacheKey(code)) != null)
				keynamecache.remove(cacheKey(code));

			int size =code.length();

			boolean hasMore = false;


			// 12,6,4 Jeremy. Ascending a ab abc... looking up db if the cache is not exist
			//'15,6,4 Jeremy. Do exact search only in between search mode.
			for (int i = 0; i < (LimeDB.getBetweenSearch()?1: size); i++) {
				String queryCode  = LimeDB.getBetweenSearch()?code : code.substring(0, i + 1);
				String cacheKey = cacheKey(queryCode);
				Pair<List<Mapping>, List<Mapping>> cacheTemp = cache.get(cacheKey);

				if (DEBUG)
					Log.i(TAG, " getMappingByCode() check if cached exist on code = '" + queryCode + "'");

				if (cacheTemp == null) {
					// 25/Jul/2011 by Art
					// Just ignore error when something wrong with the result set
					try {
						cacheTemp = dbadapter.getMappingByCode(queryCode, !isPhysicalKeyboardPressed, getAllRecords);
						cache.put(cacheKey, cacheTemp);
						//Jeremy '12,6,5 check if need to update code remap cache
						if (cacheTemp != null && cacheTemp.first != null
								&& cacheTemp.first.size() > 0 && cacheTemp.first.get(0) != null
								&& cacheTemp.first.get(0).isExactMatchToCodeRecord()) {
							String remappedCode = cacheTemp.first.get(0).getCode();
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
						e.printStackTrace();
					}

				}
			}





			// put self into the first mapping for mixed input.
			Mapping self = new Mapping();
			self.setWord(code);
			self.setCode(code);
			self.setComposingCodeRecord();

			// 12,6,4 Jeremy. Descending  abc ab a... Build the result candidate list.
			//'15,6,4 Jeremy. Do exact search only in between search mode.
			for (int i = 0; i < ((LimeDB.getBetweenSearch())?1: size); i++) {
				String cacheKey = cacheKey(code);
				Pair<List<Mapping>, List<Mapping>> cacheTemp = cache.get(cacheKey);


				if (cacheTemp != null) {
					List<Mapping> resultlist = cacheTemp.first;
					List<Mapping> relatedtlist = cacheTemp.second;

					//if getAllRecords is true and result list or related list has has more mark in the end
					// recall LimeDB.GetMappingByCode with getAllRecords true.
					if (getAllRecords &&
							(resultlist.size() > 1 && resultlist.get(resultlist.size() - 1).isHasMoreRecordsMarkRecord() ||
									relatedtlist.size() > 1&& relatedtlist.get(relatedtlist.size() - 1).isHasMoreRecordsMarkRecord() )){
						try {
							cacheTemp = dbadapter.getMappingByCode(code, !isPhysicalKeyboardPressed, true);
							cache.remove(cacheKey);
							cache.put(cacheKey, cacheTemp);
						} catch (Exception e) {
							e.printStackTrace();
						}

					}
				}

				if (cacheTemp != null) {
					List<Mapping> resultlist = cacheTemp.first;
					List<Mapping> relatedtlist = cacheTemp.second;

					if (DEBUG)
						Log.i(TAG, "getMappingByCode() code=" + code + " resultlist.size()=" + resultlist.size()
								+ " relatedlist.size()=" + relatedtlist.size());


					if (i == 0) {
						if(LimeDB.getBetweenSearch() && resultlist.size() == 0 && code.length() > 1) {
							//If the result list is empty we need to go back to last result list with nonzero result list
							String wayBackCode = code;
							do{
								wayBackCode = wayBackCode.substring(0, wayBackCode.length() - 1);
								cacheTemp = cache.get(cacheKey(wayBackCode));
								if(cacheTemp!=null)
									resultlist = cacheTemp.first;
							}while(resultlist.size() == 0 && wayBackCode.length() > 1);
						}else if(resultlist.size() >0 && resultlist.get(0).isExactMatchToCodeRecord()) {
							//push the exact match mapping with current code into exact match stack. '15,6,2 Jeremy
							exactMatchStack.push(new Pair<>(resultlist.get(0), code));
						}
						result.add(self);
					}

					if (resultlist!=null && resultlist.size() > 0) {
						result.addAll(resultlist);
						int rsize = result.size();
						if (LimeDB.getBetweenSearch() &&
								result.get(rsize - 1).isHasMoreRecordsMarkRecord()){
							//do not need to touch the has more record in between search mode. Jeremy '15,6,4
							result.remove(rsize - 1);
							hasMore = true;
							if (DEBUG)
								Log.i(TAG, "getMappingByCode() code=" + code + "  result list added resultlist.size()="
										+ resultlist.size());
						}
					}
					if (relatedtlist!=null &&relatedtlist.size() > 0 && i == 0) {
						result.addAll(relatedtlist);
						int rsize = result.size();
						if (result.get(rsize - 1).isHasMoreRecordsMarkRecord()){ //.getCode().equals("has_more_records")) {
							result.remove(rsize - 1);
							hasMore = true;
							if (DEBUG)
								Log.i(TAG, "getMappingByCode() code=" + code + "  related list added relatedlist.size()="
										+ relatedtlist.size());
						}
					}
				}
				//codeLengthMap is deprecated and replace by exact match stack scheme '15,6,3 jeremy
				//codeLengthMap.add(new Pair<>(code.length(), result.size()));  //Jeremy 12,6,2 preserve the code length in each loop.
				if (DEBUG)
					Log.i(TAG, "getMappingByCode() codeLengthMap  code length = " + code.length() + ", result size = " + result.size());

				code = code.substring(0, code.length() - 1);
			}
			if (DEBUG) Log.i(TAG, "getMappingByCode() code=" + code + " result.size()=" + result.size());
			if (hasMore) {
				Mapping hasMoreRecord = new Mapping();
				hasMoreRecord.setCode("has_more_records");
				hasMoreRecord.setWord("...");
				hasMoreRecord.setHasMoreRecordsMarkRecord();
				result.add(hasMoreRecord);
			}
		}

		return result;
	}

	/**
	 * Get the real code length according to  codeLenthMap
	 */
	int getRealCodeLength(Mapping selectedMapping, String currentCode) {
		if (DEBUG)
			Log.i(TAG, "getRealCodeLength()");

		String code = selectedMapping.getCode();
		if (tablename.equals("phonetic")) {

			if (LimeDB.isCodeDualMapped()) { //abandon LD support for dual mapped codes. Jeremy '15,6,5
				return currentCode.length();
			} else {
				String selectedPhoneticKeyboardType =
						mLIMEPref.getParameterString("phonetic_keyboard_type", "standard");
				String lcode = currentCode;
				if (selectedPhoneticKeyboardType.startsWith("eten")) {
					lcode = dbadapter.preProcessingRemappingCode(currentCode);
				}
				String noToneCode = code.replaceAll("[3467 ]", "");
				if (code.equals(noToneCode)) {
					return code.length();
				} else if (!lcode.startsWith(code) && lcode.startsWith(noToneCode)) {
					return noToneCode.length();
				} else {
					return currentCode.length(); //unexpected condition.
				}
			}
		}
		/*
		// Return real code length form code length preserved in exact match stack instead of code length map. Jeremy '15,6,2
		// return real code by iterating the current exact match stack.
		if (exactMatchStack.isEmpty())
			return composing.length(); // the selected mapping is not a exact match mapping
		for (Pair<Mapping, String> p : exactMatchStack) {
			if (p.first.getWord().equals(selectedMapping.getWord()))
				return p.second.length();
		}*/
		return code.length();
	}


	/**
	 * This method is to initial/reset the cache of im.
	 */
	public void initialCache() {
		try {
			clear();
		} catch (RemoteException e) {
			e.printStackTrace();
		}
		cache = new ConcurrentHashMap<>(LIME.SEARCHSRV_RESET_CACHE_SIZE);
		engcache = new ConcurrentHashMap<>(LIME.SEARCHSRV_RESET_CACHE_SIZE);
		keynamecache = new ConcurrentHashMap<>(LIME.SEARCHSRV_RESET_CACHE_SIZE);
		coderemapcache = new ConcurrentHashMap<>(LIME.SEARCHSRV_RESET_CACHE_SIZE);

		//  initial exact match stack here
		exactMatchStack = new Stack<>();

	}



	private void updateScoreCache(Mapping cachedMapping) {
		if (DEBUG) Log.i(TAG, "udpateScoreCache(): code=" + cachedMapping.getCode());

		dbadapter.addScore(cachedMapping);
		// Jeremy '11,7,29 update cached here
		if (!cachedMapping.isRelatedPhraseRecord()) {
			String code = cachedMapping.getCode().toLowerCase(Locale.US);
			String cachekey = cacheKey(code);
			Pair<List<Mapping>, List<Mapping>> cachedPair = cache.get(cachekey);
			// null id denotes target is selected from the related list (not exact match)
			if ((cachedMapping.getId() == null || cachedMapping.isPartialMatchToCodeRecord()) //Jeremy '15,6,3 new record type to identify partial match
					&& cachedPair != null && cachedPair.second != null && cachedPair.second.size() > 0) {
				if (DEBUG) Log.i(TAG, "updateUserDict: updating related list");
				if (cache.remove(cachekey) != null) {
					Pair<List<Mapping>, List<Mapping>> newPair
							= new Pair<>(cachedPair.first, dbadapter.updateSimilarCodeListInRelatedColumn(code));
					cache.put(cachekey, newPair);
				} else {//Jeremy '12,6,5 code not in cache do updateSimilarCodeListInRelatedColumn and removed cached items of  remaped codes.
					dbadapter.updateSimilarCodeListInRelatedColumn(code);
					removeRemapedCodeCachedMappings(code);
				}
				// non null id denotes target is in exact match result list.
			} else if ((cachedMapping.getId() != null || cachedMapping.isExactMatchToCodeRecord()) //Jeremy '15,6,3 new record type to identify exact match
					&& cachedPair != null && cachedPair.first != null && cachedPair.first.size() > 0) {

				boolean sort;
				if (isPhysicalKeyboardPressed)
					sort = mLIMEPref.getPhysicalKeyboardSortSuggestions();
				else
					sort = mLIMEPref.getSortSuggestions();

				if (sort) { // Jeremy '12,5,22 do not update the order of exact match list if the sort option is off
					List<Mapping> cachedList = cachedPair.first;
					int size = cachedList.size();
					if (DEBUG) Log.i(TAG, "updateUserDict(): cachedList.size:" + size);
					// update exact match cache
					for (int j = 0; j < size; j++) {
						Mapping cm = cachedList.get(j);
						if (DEBUG)
							Log.i(TAG, "updateUserDict(): cachedList at :" + j + ". score=" + cm.getScore());
						if (cachedMapping.getId().equals(cm.getId())) {
							int score = cm.getScore() + 1;
							if (DEBUG)
								Log.i(TAG, "updateUserDict(): cachedMapping found at :" + j + ". new score=" + score);
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
				updateSimilarCodeRelatedList(code);


			} else {//Jeremy '12,6,5 code not in cache do updateSimilarCodeListInRelatedColumn and removed cached items of  ramped codes.
				dbadapter.updateSimilarCodeListInRelatedColumn(code);
				removeRemapedCodeCachedMappings(code);
			}
		}


	}

	// '11,8,1 renamed from updateuserdict()
	List<Mapping> scorelistSnapshot = null;

	public void postFinishInput() throws RemoteException {

		//if(dbadapter == null){dbadapter = new LimeDB(ctx);}
		if (scorelistSnapshot == null) scorelistSnapshot = new LinkedList<>();
		else scorelistSnapshot.clear();


		if (DEBUG) Log.i(TAG, "postFinishInput(), creating offline updating thread");
		// Jeremy '11,7,31 The updating process takes some time. Create a new thread to do this.
		Thread UpadtingThread = new Thread() {
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

				// Learn user dictionary (the consecutive two words as a userdict phrase).
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
		UpadtingThread.start();

	}

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
						if (	unit.getWord() != null && !unit.getWord().equals("")
								&& unit2.getWord() != null && !unit2.getWord().equals("")
								&&(unit.isExactMatchToCodeRecord()|| unit.isPartialMatchToCodeRecord()
								|| unit.isRelatedPhraseRecord()) // use record type to identify records. Jeremy '15,6,4
								&&(unit2.isExactMatchToCodeRecord()|| unit2.isPartialMatchToCodeRecord()
								|| unit.isRelatedPhraseRecord() || unit2.isChinesePunctuationSymbolRecord())  //allow unit2 to be chinese punctuation symbols.
							//&& !unit.getCode().equals(unit.getWord())//Jeremy '12,6,13 avoid learning mixed mode english
							//&& !unit2.getCode().equals(unit2.getWord())
							///&& unit2.getId() !=null
								) {

							int score = 0;
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

	private void learnLDPhrase(ArrayList<List<Mapping>> localLDPhraseListArray) {
		if (DEBUG)
			Log.i(TAG, "learnLDPhrase()");

		if (localLDPhraseListArray != null && localLDPhraseListArray.size() > 0) {
			if (DEBUG)
				Log.i(TAG, "learnLDPhrase(): LDPhrase learning, arraysize =" + localLDPhraseListArray.size());


			for (List<Mapping> phraselist : localLDPhraseListArray) {
				if (DEBUG)
					Log.i(TAG, "learnLDPhrase(): LDPhrase learning, current list size =" + phraselist.size());
				if (phraselist.size() > 0 && phraselist.size() < 5) { //Jeremy '12,6,8 limit the phrase to have 4 chracters


					String baseCode, LDCode = "", QPCode = "", baseWord;

					Mapping unit1 = phraselist.get(0);

					if (DEBUG)
						Log.i(TAG, "learnLDPhrase(): unit1.getId() = " + unit1.getId()
								+ ", unit1.getCode() =" + unit1.getCode()
								+ ", unit1.getWord() =" + unit1.getWord());

					if (unit1 == null || unit1.getWord().length() == 0
							|| unit1.getCode().equals(unit1.getWord())) //Jeremy '12,6,13 avoid learning mixed mode english
					{
						break;
					}

					baseCode = unit1.getCode();
					baseWord = unit1.getWord();

					if (baseWord.length() == 1) {
						if (unit1.getId() == null //Jeremy '12,6,7 break if id is null (selected from related list)
								|| unit1.isPartialMatchToCodeRecord() //Jeremy '15,6,3 new record identification
								|| unit1.getCode() == null //Jeremy '12,6,7 break if code is null (selected from userdict)
								|| unit1.getCode().length() == 0
								|| unit1.isRelatedPhraseRecord()) {
							List<Mapping> rMappingList = dbadapter.getMappingByWord(baseWord, tablename);
							if (rMappingList.size() > 0)
								baseCode = rMappingList.get(0).getCode();
							else
								break; //look-up failed, abandon.
						}
						if (baseCode != null && baseCode.length() > 0)
							QPCode += baseCode.substring(0, 1);
						else
							break;//abandon the phrase learning process;

						//if word length >0, lookup all codes and rebuild basecode and QPCode
					} else if (baseWord.length() > 1 && baseWord.length() < 5) {
						baseCode = "";
						for (int i = 0; i < baseWord.length(); i++) {
							String c = baseWord.substring(i, i + 1);
							List<Mapping> rMappingList = dbadapter.getMappingByWord(c, tablename);
							if (rMappingList.size() > 0) {
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
							if (unit2 == null || unit2.getWord().length() == 0 ||unit2.isComposingCodeRecord()) //Jeremy 15,6,4 exclude composing code
							//|| unit2.getCode().equals(unit2.getWord())) //Jeremy '12,6,13 avoid learning mixed mode english
							{
								break;
							}

							String word2 = unit2.getWord();
							String code2 = unit2.getCode();
							baseWord += word2;

							if (word2.length() == 1 && baseWord.length() < 5) { //limit the phrase size to 4
								if ( unit2.getId() == null //Jeremy '12,6,7 break if id is null (selected from related phrase)
										|| unit2.isPartialMatchToCodeRecord() //Jeremy '15,6,3 new record identification
										|| code2 == null //Jeremy '12,6,7 break if code is null (selected from relatedphrase)
										|| code2.length() == 0
										|| unit2.isRelatedPhraseRecord()) {
									List<Mapping> rMappingList = dbadapter.getMappingByWord(word2, tablename);
									if (rMappingList.size() > 0)
										code2 = rMappingList.get(0).getCode();
									else
										break;
								}
								if (code2 != null && code2.length() > 0) {
									baseCode += code2;
									QPCode += code2.substring(0, 1);
								} else
									break; //abandon the phrase learning process;

								//if word length >0, lookup all codes and rebuild basecode and QPCode
							} else if (word2.length() > 1 && baseWord.length() < 5) {
								for (int j = 0; j < word2.length(); j++) {
									String c = word2.substring(j, j + 1);
									List<Mapping> rMappingList = dbadapter.getMappingByWord(c, tablename);
									if (rMappingList.size() > 0) {
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
								if (tablename.equals("phonetic")) {// remove tone symbol in phonetic table
									LDCode = baseCode.replaceAll("[3467 ]", "").toLowerCase(Locale.US);
									QPCode = QPCode.toLowerCase(Locale.US);
									if (LDCode.length() > 1) {
										dbadapter.addOrUpdateMappingRecord(LDCode, baseWord);
										removeRemapedCodeCachedMappings(LDCode);
										updateSimilarCodeRelatedList(LDCode);
									}
									if (QPCode.length() > 1) {
										dbadapter.addOrUpdateMappingRecord(QPCode, baseWord);
										removeRemapedCodeCachedMappings(QPCode);
										updateSimilarCodeRelatedList(QPCode);
									}
								} else if (baseCode.length() > 1) {
									baseCode = baseCode.toLowerCase(Locale.US);
									dbadapter.addOrUpdateMappingRecord(baseCode, baseWord);
									removeRemapedCodeCachedMappings(baseCode);
									updateSimilarCodeRelatedList(baseCode);
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
	private void removeRemapedCodeCachedMappings(String code) {
		if (DEBUG)
			Log.i(TAG, "removeRemapedCodeCachedMappings() on code ='" + code + "' coderemapcache.size=" + coderemapcache.size());
		List<String> codelist = coderemapcache.get(cacheKey(code));
		if (codelist != null) {
			for (String entry : codelist) {
				if (DEBUG)
					Log.i(TAG, "removeRemapedCodeCachedMappings() remove code= '" + entry + "' from cache.");
				cache.remove(cacheKey(entry));
			}
		} else
			cache.remove(cacheKey(code)); //Jeremy '12,6,6 no remap. remove the code mapping from cache.
	}

	private void updateSimilarCodeRelatedList(String code) {
		if (DEBUG)
			Log.i(TAG, "updateSimilarCodeRelatedList(): code = '" + code + "'");
		String cachekey;
		Pair<List<Mapping>, List<Mapping>> cachedPair;// = cache.get(cachekey);
		int len = code.length();
		if (len > 5) len = 5; //Jeremy '12,6,7 change max backward level to 5.
		for (int k = 1; k < len; k++) {
			String key = code.substring(0, code.length() - k);
			cachekey = cacheKey(key);
			cachedPair = cache.get(cachekey);
			if (DEBUG)
				Log.i(TAG, "updateSimilarCodeRelatedList(): cachekey = '" + cachekey + "' cachedPair == null :" + (cachedPair == null));
			if (cachedPair != null) {
				if (DEBUG)
					Log.i(TAG, "updateSimilarCodeRelatedList(): udpate to db cachekey = '" + cachekey + "'");
				Pair<List<Mapping>, List<Mapping>> newPair
						= new Pair<>(cachedPair.first, dbadapter.updateSimilarCodeListInRelatedColumn(key));
				cache.remove(cachekey);
				cache.put(cachekey, newPair);
			} else {
				if (DEBUG)
					Log.i(TAG, "updateSimilarCodeRelatedList(): code not in cache. udpate to db only on code = '" + key + "'");
				dbadapter.updateSimilarCodeListInRelatedColumn(key);
				removeRemapedCodeCachedMappings(key);
			}
		}
	}


	public String keyToKeyname(String code) {
		//Jeremy '11,6,21 Build cache according using cachekey

		String cacheKey = cacheKey(code);
		String result = keynamecache.get(cacheKey);
		if (result == null) {
			//loadDBAdapter(); openLimeDatabase();
			result = dbadapter.keyToKeyname(code, tablename, true);
			keynamecache.put(cacheKey, result);
		}
		return result;
	}

	/**
	 * Renamed from addUserDict and pass parameter with mapping directly Jeremy '12,6,5
	 * Renamed to learnRelatedPhraseAndUpdateScore Jeremy '15,6,4
	 */

	public void learnRelatedPhraseAndUpdateScore(Mapping updateMapping)
	//String id, String code, String word,
	//String pword, int score, boolean isDictionary)
			throws RemoteException {
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

	public List<KeyboardObj> getKeyboardList() throws RemoteException {
		//if(dbadapter == null){dbadapter = new LimeDB(ctx);}
		return dbadapter.getKeyboardList();
	}

	public List<ImObj> getImList() throws RemoteException {
		//if(dbadapter == null){dbadapter = new LimeDB(ctx);}
		return dbadapter.getImList();
	}


	public void clear() throws RemoteException {
		if (scorelist != null) {
			scorelist.clear();
		}
		if (scorelist != null) {
			scorelist.clear();
		}
		if (cache != null) {
			cache.clear();
		}
		if (engcache != null) {
			engcache.clear();
		}
		if (keynamecache != null) {
			keynamecache.clear();
		}

		if (coderemapcache != null) {
			coderemapcache.clear();
		}
	}


	public List<Mapping> getEnglishSuggestions(String word) throws RemoteException {

		List<Mapping> result = new LinkedList<>();
		List<Mapping> cacheTemp = engcache.get(word);

		if (cacheTemp != null) {
			result.addAll(cacheTemp);
		} else {
			//loadDBAdapter(); openLimeDatabase();
			List<String> tempResult = dbadapter.getEnglishSuggesions(word);
			for (String u : tempResult) {
				Mapping temp = new Mapping();
				temp.setWord(u);
				temp.setEnglishSuggestionRecord();
				result.add(temp);
			}
			if (result.size() > 0) {
				engcache.put(word, result);
			}
		}
		return result;

	}

	/*
        public boolean isImKeys(char c) throws RemoteException {
            if (imKeysMap.get(tablename) == null || imKeysMap.size() == 0) {
                //if(dbadapter == null){dbadapter = new LimeDB(ctx);}
                imKeysMap.put(tablename, dbadapter.getImInfo(tablename, "imkeys"));
            }
            String imkeys = imKeysMap.get(tablename);
            return !(imkeys == null || imkeys.equals("")) && (imkeys.indexOf(c) >= 0);
        }
    */
	public String getSelkey() throws RemoteException {
		if (DEBUG)
			Log.i(TAG, "getSelkey():hasNumber:" + hasNumberMapping + "hasSymbol:" + hasSymbolMapping);
		String selkey;
		String table = tablename;
		if (tablename.equals("phonetic")) {
			table = tablename + mLIMEPref.getPhoneticKeyboardType();
		}
		if (selKeyMap.get(table) == null || selKeyMap.size() == 0) {
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
			if (!validSelkey || tablename.equals("phonetic")) {
				if (hasNumberMapping && hasSymbolMapping) {
					if (tablename.equals("dayi")
							|| (tablename.equals("phonetic") && mLIMEPref.getPhoneticKeyboardType().equals("standard"))) {
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
	public int isSelkey(char c) throws RemoteException {

		return getSelkey().indexOf(c);

	}
	*/

}