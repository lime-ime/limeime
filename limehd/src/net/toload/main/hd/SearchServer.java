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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

import net.toload.main.hd.R;
import net.toload.main.hd.global.ImObj;
import net.toload.main.hd.global.KeyboardObj;
import net.toload.main.hd.global.LIME;
import net.toload.main.hd.global.LIMEPreferenceManager;
import net.toload.main.hd.global.LIMEUtilities;
import net.toload.main.hd.global.Mapping;
import net.toload.main.hd.limedb.LimeDB;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.database.sqlite.SQLiteException;
import android.os.RemoteException;
import android.util.Log;
import android.util.Pair;

public class SearchServer {

	private final boolean DEBUG = false;
	private final String TAG = "LIME.SearchServer";
	private static LimeDB dbadapter = null; //Jeremy '12,5,1 shared single LIMEDB object 
	//Jeremy '12,4,6 Combine updatedb and quierydb into db,
	//Jeremy '12,4,7 move db open/clsoe back to LimeDB
	//	since query always following with userdict and related learning and dual db connections cause exceptions.
	//private SQLiteDatabase db = null;
	//private LimeHanConverter hanConverter = null;
	//private static LinkedList<Mapping> diclist = null;  
	private static List<Mapping> scorelist = null;

	private static List<List<Mapping>> LDPhraseListArray = null;
	private static List<Mapping> LDPhraseList = null;

	private static StringBuffer selectedText = new StringBuffer();
	private static String tablename = "";

	//private NotificationManager notificationMgr;

	private LIMEPreferenceManager mLIMEPref;


	//private static SearchServiceImpl obj = null;

	//private static int recAmount = 0;
	private static boolean isPhysicalKeyboardPressed; // Sync to LIMEService and LIMEDB
	//Jeremy '11,6,10
	private static boolean hasNumberMapping;
	private static boolean hasSymbolMapping;


	//private static List<Mapping> preresultlist = null;
	//private static String precode = null;

	//Jeremy '11,6,6
	private HashMap<String,String> imKeysMap = new HashMap<String,String>();
	private HashMap<String,String> selKeyMap = new HashMap<String,String>();
	//private HashMap<String,String> endKeyMap = new HashMap<String,String>();

	private static ConcurrentHashMap<String, Pair<List<Mapping>,List<Mapping>>> cache = null;
	private static ConcurrentHashMap<String, List<Mapping>> engcache = null;
	private static ConcurrentHashMap<String, String> keynamecache = null;
	/** Store the mapping of typing code and mapped code from query on db  Jeremy '12,6,5  */
	private static ConcurrentHashMap<String, List<String>> coderemapcache = null;

	private Context mContext = null;
	
	private static List<Pair<Integer, Integer>> codeLenthMap = new LinkedList<Pair<Integer, Integer>>(); 

	//public class SearchServiceImpl extends ISearchService.Stub {

	//Context ctx = null;
	/*
		SearchServiceImpl(Context ctx) {
			this.ctx = ctx;	
			mLIMEPref = new LIMEPreferenceManager(ctx);
			loadDBAdapter(); 

		}
	 */
	public SearchServer(Context context) {


		this.mContext = context;

		mLIMEPref = new LIMEPreferenceManager(mContext.getApplicationContext());
		if(dbadapter == null) dbadapter = new LimeDB(mContext); 
		initialCache();


	}

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

	public String hanConvert(String input){
		return dbadapter.hanConvert(input, mLIMEPref.getHanCovertOption());

	}

	public String getTablename(){
		return tablename;
	}

	public void setTablename(String table, boolean numberMapping, boolean symbolMapping){
		if(DEBUG)
			Log.i(TAG,"SearchService.setTablename()");

		dbadapter.setTablename(table);
		tablename = table;
		hasNumberMapping = numberMapping;
		hasSymbolMapping = symbolMapping;
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
	public List<Mapping> queryUserDic(String word) throws RemoteException {

		List<Mapping> result = dbadapter.queryUserDict(word);
		return result;
	}
	//-----------------------------------------------------------

	public Cursor getDictionaryAll(){

		return dbadapter.getDictionaryAll();

	}

	//Add by jeremy '10, 4,1
	public void rQuery(final String word) throws RemoteException {

		Thread queryThread = new Thread(){
			public void run() {
				String result = dbadapter.getRMappingInConvertedKeynameString(word);
				if(result!=null && !result.equals("")){
					//displayNotificationMessage(result);
					LIMEUtilities.showNotification(
							mContext, true, R.drawable.icon, mContext.getText(R.string.ime_setting), result, new Intent(mContext, LIMEMenu.class));
				}
			}
		};
		queryThread.start();

	}

	private String cacheKey(String code){
		String key ="";

		//Jeremy '11,6,17 Seperate physical keyboard cache with keybaordtype
		if(isPhysicalKeyboardPressed){	
			if(tablename.equals("phonetic")){
				key = mLIMEPref.getPhysicalKeyboardType()+dbadapter.getTablename()
						+ mLIMEPref.getPhoneticKeyboardType()+code;
			}else{
				key = mLIMEPref.getPhysicalKeyboardType()+dbadapter.getTablename()+code;
			}
		}else{
			if(tablename.equals("phonetic"))
				key = dbadapter.getTablename()+ mLIMEPref.getPhoneticKeyboardType()+code;
			else
				key = dbadapter.getTablename()+code;
		}
		return key;
	}

	public List<Mapping> query(String code, boolean softkeyboard, boolean getAllRecords) throws RemoteException {
		if(DEBUG) Log.i(TAG, "query(): code="+code);
		// Check if system need to reset cache

		if(mLIMEPref.getParameterBoolean(LIME.SEARCHSRV_RESET_CACHE)){
			initialCache();
			mLIMEPref.setParameter(LIME.SEARCHSRV_RESET_CACHE,false);
		}
		
		codeLenthMap.clear();//Jeremy '12,6,2 reset the codeLengthMap

		List<Mapping> result = new LinkedList<Mapping>();
		if(code!=null) {
			// clear mappingidx when user switching between softkeyboard and hard keyboard. Jeremy '11,6,11
			if(isPhysicalKeyboardPressed == softkeyboard)
				isPhysicalKeyboardPressed = !softkeyboard;

			// Jeremy '11,9, 3 remove cached keyname when request full records
			if(getAllRecords && keynamecache.get(cacheKey(code))!=null)
				keynamecache.remove(cacheKey(code));


			Mapping temp = new Mapping();
			temp.setWord(code);
			//code = code.toLowerCase();  //Jeremy '12,4,1 moved to LimeDB.getMapping after remapping For XPERIA PRO BPMF 
			temp.setCode(code);
			//result.add(temp);  Jeremy '12,5,30 add later in the result buliding loop
			int size = code.length();

			boolean hasMore = false;


			// 11'7,22 rewritten for 嚙踐�嚙踝蕭�蕭�蕭
			// 12,6,4 Jeremy. Ascending a ab abc... looking up db if the cache is not exist
			for(int i =0; i<size; i++) {
				String queryCode = code.substring(0,i+1);
				String cacheKey = cacheKey(queryCode);
				Pair<List<Mapping>,List<Mapping>> cacheTemp = cache.get(cacheKey);
				
				if(DEBUG)
					Log.i(TAG," query() check if cached exist on code = '" + queryCode + "'");
				
				if(cacheTemp == null){
					// 25/Jul/2011 by Art
					// Just ignore error when something wrong with the result set
					try{
						cacheTemp = dbadapter.getMapping(queryCode, !isPhysicalKeyboardPressed, getAllRecords);
						cache.put(cacheKey, cacheTemp);
						//Jeremy '12,6,5 check if need to update code remap cache
						if(cacheTemp!=null && cacheTemp.first!=null 
								&& cacheTemp.first.size()>0 && cacheTemp.first.get(0)!=null) {
							String codeFromMapping = cacheTemp.first.get(0).getCode();
							if(!queryCode.equals(codeFromMapping) ){
								 List<String> codeList = coderemapcache.get(codeFromMapping);
								 String key = cacheKey(codeFromMapping);
								 if(codeList == null){
									 List<String> newlist = new LinkedList<String>();
									 newlist.add(codeFromMapping); //put self in the list
									 newlist.add(queryCode);
									 coderemapcache.put(key, newlist);
									 if(DEBUG)
										 Log.i(TAG, "query() build new remap code = '" 
												 + codeFromMapping  + "' to code = '" + queryCode +"'"
												 + " coderemapcache.size()=" +coderemapcache.size());
								 }else{
									 codeList.add(queryCode);
									 coderemapcache.remove(key);
									 coderemapcache.put(key, codeList);
									 if(DEBUG)
										 Log.i(TAG, "query() codeFromMapping: add new remap code = '" + codeFromMapping  + "' to code = '" + queryCode +"'");
								 }
								
							}
							
						}
						
					}catch(SQLiteException ne){
						ne.printStackTrace();
					}catch(NullPointerException ne){
						ne.printStackTrace();
					}catch(Exception e){
						e.printStackTrace();
					}

				}
			}
			// 11'7,22 rewritten for 嚙踐�嚙踝蕭�蕭�蕭
			// 12,6,4 Jeremy. Descending  abc ab a... Build the result candidate list.
			for(int i =0; i<size; i++) {
				String cacheKey = cacheKey(code);
				Pair<List<Mapping>,List<Mapping>> cacheTemp = cache.get(cacheKey);


				if(cacheTemp != null){
					List<Mapping> resultlist = cacheTemp.first;
					List<Mapping> relatedtlist = cacheTemp.second;

					if(getAllRecords &&
							(resultlist.size()>1 && 
									resultlist.get(resultlist.size()-1).getCode().equals("has_more_records")||
									relatedtlist.size()>1&&
									relatedtlist.get(relatedtlist.size()-1).getCode().equals("has_more_records") )){
						try{
							cacheTemp = dbadapter.getMapping(code, !isPhysicalKeyboardPressed, true);
							cache.remove(cacheKey);
							cache.put(cacheKey, cacheTemp);
						}catch(Exception e){
							e.printStackTrace();
						}

					}
				}

				if(cacheTemp != null){
					List<Mapping> resultlist = cacheTemp.first;
					List<Mapping> relatedtlist = cacheTemp.second;

					if(DEBUG) 
						Log.i(TAG, "query() code=" + code + " resultlist.size()=" + resultlist.size()
								+" relatedlist.size()=" + relatedtlist.size());
					
					if(i==0) {//Jeremy add the mixed type English code in first loop 
						temp.setRelated(resultlist.size()==0); //Jeremy '12,5,31 setRelated true if the exact match code has zero result list size.
						result.add(temp);
					}
					
					// Art '09.11.2011 ignore phonetic tone control
					if(resultlist.size() > 0){ 
						result.addAll(resultlist);
						int rsize = result.size();
						if(result.get(rsize-1).getCode().equals("has_more_records")){
							result.remove(rsize-1);
							hasMore = true;
							if(DEBUG) 
								Log.i(TAG, "query() code=" + code + "  resutl list added resultlist.size()=" 
										+ resultlist.size());
						}
					}
					if(relatedtlist.size()>0 && i==0 ){ 
						result.addAll(relatedtlist);
						int rsize = result.size();
						if(result.get(rsize-1).getCode().equals("has_more_records")){
							result.remove(rsize-1);
							hasMore = true;
							if(DEBUG) 
								Log.i(TAG, "query() code=" + code + "  related list added relatedlist.size()=" 
										+ relatedtlist.size());
						}
					}

				}
				codeLenthMap.add(new Pair<Integer, Integer>(code.length(), result.size()));  //Jeremy 12,6,2 preserve the code length in each loop.
				if(DEBUG)
					Log.i(TAG,"query() codeLengthMap  codelenth = " + code.length() + ", resultsize = " + result.size());

				code= code.substring(0,code.length()-1);
			}
			if(DEBUG) Log.i(TAG, "query() code=" + code + " result.size()=" + result.size());
			if(hasMore){
				temp = new Mapping();
				temp.setCode("has_more_records");
				temp.setWord("...");
				result.add(temp);
			}
		}

		return result;
	}

	/**
	 * Get the real code length according to  codeLenthMap
	 */
	int getRealCodeLength(int index){
		if(DEBUG)
			Log.i(TAG,"getRealCodeLength() index = " + index);
		for(Pair<Integer, Integer> entry: codeLenthMap){
			if(DEBUG)
				Log.i(TAG,"getRealCodeLength() codelength = " + entry.first + ", resultsize = " + entry.second);
			if(index < entry.second){
				return entry.first;
			}
			
		}
		return codeLenthMap.get(0).first ; // should not happen
	}
	
	/*
		public void initial() throws RemoteException {
			initialCache();
		}
	*/

	/**
	 * This method is to initial/reset the cache of im. 
	 */
	public void initialCache(){
		cache = new ConcurrentHashMap<String, Pair<List<Mapping>,List<Mapping>>>(LIME.SEARCHSRV_RESET_CACHE_SIZE);
		engcache = new ConcurrentHashMap<String, List<Mapping>>(LIME.SEARCHSRV_RESET_CACHE_SIZE);
		keynamecache = new ConcurrentHashMap<String, String>(LIME.SEARCHSRV_RESET_CACHE_SIZE);
		coderemapcache = new ConcurrentHashMap<String, List<String>>(LIME.SEARCHSRV_RESET_CACHE_SIZE);
	}

	/*public List<Mapping> sortArray(String precode, List<Mapping> src) {

			// Modified by jeremy '10, 4, 5. Buf fix for 3row remap. code may not equal to precode.
				if(src != null && src.size() > 1){
					for (int i = 1; i < (src.size() - 1); i++) {
						for (int j = i + 1; j < src.size(); j++) {
							if (src.get(j).getScore() > src.get(i).getScore()) {
								Mapping dummy = src.get(i);
								if(!dummy.getCode().equals(precode) && !src.get(j).getCode().equals(precode)){
									src.set(i, src.get(j));
									src.set(j, dummy);
								}
							}
						}
					}
				}
			return src;
		}*/
	/*@Deprecated
		public void addUserDict(String id, String code, String word,
				String pword, int score, boolean isDictionary)
				throws RemoteException {



				if(diclist == null){diclist = new LinkedList<Mapping>();}

				Mapping temp = new Mapping();
			      temp.setId(id);
			      temp.setCode(code);
			      temp.setWord(word);
			      temp.setPword(pword);
			      temp.setScore(score);
			      temp.setDictionary(isDictionary);
			    diclist.addLast(temp);

		}*/

	private void updateScoreCache(Mapping cachedMapping){
		if(DEBUG) Log.i(TAG, "udpateScoreCache(): code=" + cachedMapping.getCode());

		dbadapter.addScore(cachedMapping);					
		// Jeremy '11,7,29 update cached here
		if (!cachedMapping.isDictionary()){
			String code = cachedMapping.getCode().toLowerCase();
			String cachekey = cacheKey(code);
			Pair<List<Mapping>, List<Mapping>> cachedPair = cache.get(cachekey);
			// null id denotes target is selected from the related list (not exact match)
			if(cachedMapping.getId()==null   
					&& cachedPair!=null && cachedPair.second !=null && cachedPair.second.size()>0){
				if(DEBUG) Log.i(TAG,"updateUserDict: updating related list");
				if(cache.remove(cachekey)!=null){
					Pair<List<Mapping>, List<Mapping>> newPair 
					= new Pair<List<Mapping>, List<Mapping>>(cachedPair.first, dbadapter.updateRelatedList(code)) ;
					cache.put(cachekey, newPair);
				}else{//Jeremy '12,6,5 code not in cahe do updateRelatedList and removed cached items of  ramped codes. 
					dbadapter.updateRelatedList(code);
					removeRemapedCodeCachedMappings(code);
				}
				// non null id denotes target is in exact match result list.
			} else  if(cachedMapping.getId()!=null && cachedPair!=null 
					&& cachedPair.first !=null && cachedPair.first.size()>0) {
				
				boolean sort = true;
				if(isPhysicalKeyboardPressed) 
					sort = mLIMEPref.getPhysicalKeyboardSortSuggestions();
				else
					sort = mLIMEPref.getSortSuggestions();
				
				if(sort){ // Jeremy '12,5,22 do not update the order of exact match list if the sort option is off
					List<Mapping> cachedList = cachedPair.first;
					int size = cachedList.size();
					if(DEBUG) Log.i(TAG,"updateUserDict(): cachedList.size:" + size);
					// update exact match cache
					for(int j=0; j< size; j++){
						Mapping cm = cachedList.get(j);
						if(DEBUG) Log.i(TAG,"updateUserDict(): cachedList at :" + j + ". score="+ cm.getScore());
						if(cachedMapping.getId() == cm.getId()){
							int score = cm.getScore() + 1 ;
							if(DEBUG) Log.i(TAG,"updateUserDict(): cachedMapping found at :" + j +". new score=" +score );
							cm.setScore(score);
							if(j>0 && score > cachedList.get(j-1).getScore()){
								cachedList.remove(j);
								for(int k=0; k<j; k++){
									if(cachedList.get(k).getScore() <= score){
										cachedList.add(k,cm);
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


			}else{//Jeremy '12,6,5 code not in cahe do updateRelatedList and removed cached items of  ramped codes. 
				dbadapter.updateRelatedList(code);
				removeRemapedCodeCachedMappings(code);
			}
		}


	}
	// '11,8,1 renamed from updateuserdict()
	List<Mapping> scorelistSnapshot = null; 
	public void postFinishInput() throws RemoteException {

		//if(dbadapter == null){dbadapter = new LimeDB(ctx);} 
		if(scorelistSnapshot==null) scorelistSnapshot = new LinkedList<Mapping>();
		else scorelistSnapshot.clear();


		if(DEBUG)	Log.i(TAG,"postFinishInput(), creating offline updating thread");
		// Jeremy '11,7,31 The updating process takes some time. Create a new thread to do this.
		Thread UpadtingThread = new Thread(){
			public void run() {
				// for thread-safe operation, duplicate local copy of scorelist and LDphraselistarray
				//List<Mapping> localScorelist = new LinkedList<Mapping>();
				if(scorelist != null) {
					scorelistSnapshot.addAll(scorelist);
					scorelist.clear();
				}
				//Jeremy '11,7,28 combine to adduserdict and addscore 
				//Jeremy '11,6,12 do adduserdict and add score if diclist.size > 0 and only adduserdict if diclist.size >1
				//Jeremy '11,6,11, always learn scores, but sorted according preference options

				// Learn user dictionary (the consecutive two words as a userdict phrase).
				learnUserDict(scorelistSnapshot);
				
				ArrayList<List<Mapping>> localLDPhraseListArray = new ArrayList<List<Mapping>>();
				if(LDPhraseListArray!=null){
					localLDPhraseListArray.addAll(LDPhraseListArray);
					LDPhraseListArray.clear();
				}
			

				// Learn LD Phrase
				learnLDPhrase(localLDPhraseListArray);

			}
		};
		UpadtingThread.start();

	}

	private void learnUserDict(List<Mapping> localScorelist){
		if(localScorelist != null){
			if(DEBUG) 
				Log.i(TAG,"learnUserDict(), localScorelist.size=" + localScorelist.size());
			if(mLIMEPref.getLearnRelatedWord() && localScorelist.size() > 1){
				for (int i = 0; i < localScorelist.size(); i++) {
					Mapping unit = localScorelist.get(i);
					if(unit == null){continue;}	    
					if(i+1 <localScorelist.size()){
						Mapping unit2 = localScorelist.get((i + 1));
						if(unit2 == null){continue;}				
						if (//unit.getId()!=null
								unit.getWord() != null && !unit.getWord().equals("")
								&& !unit.getCode().equals(unit.getWord())//Jeremy '12,6,13 avoid learning mixed mode english 
								&& !unit2.getCode().equals(unit2.getWord())
								//&& unit2.getId() !=null
								&& unit2.getWord() != null && !unit2.getWord().equals("")
								) {
							//Jeremy '12,6,7 learn LD phrase if the score of userdic is > 20
							int score = dbadapter.addOrUpdateUserdictRecord(unit.getWord(),unit2.getWord());
							if(DEBUG)
								Log.i(TAG, "learnUserDict(), the return score = " + score);
							if( score >20 && mLIMEPref.getLearnPhrase()){
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
	 * Jeremy '12,6,9 Rewrited to support word with more than 1 characters 
	 * @param localLDPhraseListArray
	 */

	private void learnLDPhrase(ArrayList<List<Mapping>> localLDPhraseListArray){
		if(DEBUG) 
			Log.i(TAG,"learnLDPhrase()");
		
		if(localLDPhraseListArray!=null && localLDPhraseListArray.size()>0){
			if(DEBUG) 
				Log.i(TAG,"learnLDPhrase(): LDPhrase learning, arraysize =" + localLDPhraseListArray.size());
			

			for(List<Mapping> phraselist : localLDPhraseListArray ){
				if(DEBUG) 
					Log.i(TAG,"learnLDPhrase(): LDPhrase learning, current list size =" + phraselist.size());
				if(phraselist.size()>0 && phraselist.size() <5){ //Jeremy '12,6,8 limit the phrase to have 4 chracters 
					
				
					String baseCode="", LDCode ="", QPCode ="", baseWord="";
				
					Mapping unit1 = phraselist.get(0);
					
					if(DEBUG) 
						Log.i(TAG,"learnLDPhrase(): unit1.getId() = " + unit1.getId() 
								+ ", unit1.getCode() =" + unit1.getCode()
								+ ", unit1.getWord() =" + unit1.getWord());
 
					if( unit1 == null || unit1.getWord().length()==0
							|| unit1.getCode().equals(unit1.getWord())) //Jeremy '12,6,13 avoid learning mixed mode english
						{break;}
					
					baseCode = unit1.getCode();
					baseWord = unit1.getWord();
					
					if( baseWord.length()==1 ){
						if(unit1.getId() == null //Jermy '12,6,7 break if id is null (selected from related list)
								|| unit1.getCode() == null //Jermy '12,6,7 break if code is null (selected from userdict)
								|| unit1.getCode().length()==0
								|| unit1.isDictionary()){
									List<Mapping> rMappingList = dbadapter.getRMapping(baseWord, tablename);
									if(rMappingList.size()>0)
										baseCode = rMappingList.get(0).getCode();
									else 
										break; //look-up failed, abandon.
								}
						if(baseCode!=null && baseCode.length()>0)
							QPCode += baseCode.substring(0,1);
						else
							break;//abandon the phrase learning process;
					
					//if word length >0, lookup all codes and rebuild basecode and QPCode	
					}else if( baseWord.length() >1 && baseWord.length() < 5){ 
						baseCode = "";
						for(int i=0; i < baseWord.length(); i++){
							String c = baseWord.substring(i,i+1);
							List<Mapping> rMappingList = dbadapter.getRMapping(c, tablename);
							if(rMappingList.size()>0){
								baseCode += rMappingList.get(0).getCode();
								QPCode += rMappingList.get(0).getCode().substring(0,1);
							}else{
								baseCode = ""; //r-lookup failed. abandon the phrase learning 
								break;
							}
						}
					}
									
			
					for (int i = 0; i < phraselist.size(); i++) {
						if(i+1 <phraselist.size()){
							
							Mapping unit2 = phraselist.get((i + 1));
							if(unit2 == null || unit2.getWord().length()==0
									|| unit2.getCode().equals(unit2.getWord())) //Jeremy '12,6,13 avoid learning mixed mode english
								{break;}
							
							String word2 = unit2.getWord();
							String code2 = unit2.getCode();
							baseWord += word2;
							
							if( word2.length()==1 && baseWord.length() <5){ //limit the phrase size to 4
								if(unit2.getId() == null //Jermy '12,6,7 break if id is null (selected from related list)
										|| code2 == null //Jermy '12,6,7 break if code is null (selected from userdict)
										|| code2.length()==0
										|| unit2.isDictionary()){
											List<Mapping> rMappingList = dbadapter.getRMapping(word2, tablename);
											if(rMappingList.size()>0)
												code2 = rMappingList.get(0).getCode();
											else
												break;
										}
								if(code2!=null && code2.length()>0){
									baseCode+=code2;
									QPCode += code2.substring(0,1);
								}else
									break; //abandon the phrase learning process;
								
							//if word length >0, lookup all codes and rebuild basecode and QPCode	
							}else if( word2.length() > 1 && baseWord.length() < 5){ 
								for(int j=0; j < word2.length(); j++){
									String c = word2.substring(j,j+1);
									List<Mapping> rMappingList = dbadapter.getRMapping(c, tablename);
									if(rMappingList.size()>0){
										baseCode += rMappingList.get(0).getCode();
										QPCode += rMappingList.get(0).getCode().substring(0,1);
									}else //r-lookup failed. abandon the phrase learning 
										break;
								}
							} else  // abandon the learing process.
								break;
							
							
							if(DEBUG)
								Log.i(TAG,"learnLDPhrase(): code1 = " + unit1.getCode()
										+ ", code2 = '" + code2
										+ "', word1 = " + unit1.getWord()
										+ ", word2 = " + word2
										+ ", basecode = '" + baseCode
										+ "', baseWord = " + baseWord
										+ ", QPcode = '" + QPCode
										+ "'.");
							if(i+1 == phraselist.size()-1){//only learn at the end of the phrase word '12,6,8
								if(tablename.equals("phonetic")) {// remove tone symbol in phonetic table 
									LDCode = baseCode.replaceAll("[3467 ]", "").toLowerCase();
									QPCode = QPCode.toLowerCase();
									if(LDCode.length()>1){
										dbadapter.addOrUpdateMappingRecord(LDCode, baseWord);
										removeRemapedCodeCachedMappings(LDCode);								
										updateSimilarCodeRelatedList(LDCode);
									}
									if(QPCode.length()>1){
										dbadapter.addOrUpdateMappingRecord(QPCode, baseWord);
										removeRemapedCodeCachedMappings(QPCode);
										updateSimilarCodeRelatedList(QPCode);
									}
								}else if(baseCode.length()>1){
									baseCode = baseCode.toLowerCase();
									dbadapter.addOrUpdateMappingRecord(baseCode, baseWord);
									removeRemapedCodeCachedMappings(baseCode);
									updateSimilarCodeRelatedList(baseCode);
								}
								if(DEBUG) 
									Log.i(TAG,"learnLDPhrase(): LDPhrase learning, baseCode = '" + baseCode 
											+ "', LDCode = '" + LDCode + "', QPCode=" + QPCode + "'."
											+ ", baseWord" + baseWord );

							}

						
						}
					}
				}
			}


		}
	}

	/**
	 * @param code
	 */
	private void removeRemapedCodeCachedMappings(String code) {
		if(DEBUG)
			Log.i(TAG, "removeRemapedCodeCachedMappings() on code ='" + code + "' coderemapcache.size=" + coderemapcache.size());
		List<String> codelist = coderemapcache.get(cacheKey(code));
		if(codelist != null){
			for(String entry: codelist){
				if(DEBUG)
					Log.i(TAG, "removeRemapedCodeCachedMappings() remove code= '" + entry + "' from cache.");
				cache.remove(cacheKey(entry));
			}
		}else 
			cache.remove(cacheKey(code)); //Jeremy '12,6,6 no remap. remove the code mapping from cache.
	}

	private void updateSimilarCodeRelatedList(String code){
		if(DEBUG)
			Log.i(TAG, "updateSimilarCodeRelatedList(): code = '" + code + "'");
		String cachekey = cacheKey(code);
		Pair<List<Mapping>, List<Mapping>> cachedPair;// = cache.get(cachekey);
		int len = code.length();
		if(len > 5 ) len = 5; //Jeremy '12,6,7 change max backward level to 5.
		for (int k = 1; k < len; k++) {
			String key = code.substring(0, code.length() - k);
			cachekey = cacheKey(key);
			cachedPair = cache.get(cachekey);
			if(DEBUG)
				Log.i(TAG, "updateSimilarCodeRelatedList(): cachekey = '" + cachekey + "' cachedPair == null :" + (cachedPair ==null) );
			if(cachedPair !=null){
				if(DEBUG)
					Log.i(TAG, "updateSimilarCodeRelatedList(): udpate to db cachekey = '" + cachekey + "'");
				Pair<List<Mapping>, List<Mapping>> newPair 
					= new Pair<List<Mapping>, List<Mapping>>(cachedPair.first, dbadapter.updateRelatedList(key)) ;
				cache.remove(cachekey);
				cache.put(cachekey, newPair);
			}else{
				if(DEBUG)
					Log.i(TAG, "updateSimilarCodeRelatedList(): code not in cache. udpate to db only on code = '" + key + "'");
				dbadapter.updateRelatedList(key);
				removeRemapedCodeCachedMappings(key);
			}
		}
	}



	public String keyToKeyname(String code){
		//Jeremy '11,6,21 Build cache according using cachekey

		String cacheKey = cacheKey(code);
		String result = keynamecache.get(cacheKey);
		if(result == null){
			//loadDBAdapter(); openLimeDatabase();
			result = dbadapter.keyToKeyname(code, tablename, true);
			keynamecache.put(cacheKey, result);
		}
		return result;

	}
	/**
	 * Renamed from addUserDict and pass parameter with mapping directly Jeremy '12,6,5
	 * @param updateMapping
	 * @throws RemoteException
	 */

	public void addUserDictAndUpdateScore(Mapping updateMapping)
			//String id, String code, String word,
			//String pword, int score, boolean isDictionary)
					throws RemoteException {
		if(DEBUG) Log.i(TAG, "addUserDictAndUpdateScore()");

		if(scorelist == null){scorelist = new ArrayList<Mapping>();}
		
		// Temp final Mapping Object For updateMapping thread.
		final Mapping updateMappingTemp = new Mapping(updateMapping);
		
		// Jeremy '11,6,11. Always update score and sort according to preferences.
		scorelist.add(updateMappingTemp);
		Thread UpadtingThread = new Thread(){
			public void run() {
				updateScoreCache(updateMappingTemp);
			}
		};
		UpadtingThread.start();


	}

	public void addLDPhrase(Mapping mapping,//String id, String code, String word, int score, 
			boolean ending){
		if(LDPhraseListArray == null) 
			LDPhraseListArray = new ArrayList<List<Mapping>>();
		if(LDPhraseList == null)
			LDPhraseList = new LinkedList<Mapping>(); 



		if(mapping != null){ // force interruped if mapping=null
			LDPhraseList.add(mapping);
		}

		if(ending){
			if(LDPhraseList.size()>1)
				LDPhraseListArray.add(LDPhraseList);
			LDPhraseList = new LinkedList<Mapping>();		
		}

		if(DEBUG) Log.i(TAG,"addLDPhrase()"//+mapping.getCode() + ". id=" + mapping.getId() 
				+ ". engding:" + ending
				+ ". LDPhraseListArray.size=" + LDPhraseListArray.size()
				+ ". LDPhraseList.size=" + LDPhraseList.size());
				

	}

	public List<KeyboardObj> getKeyboardList() throws RemoteException {
		//if(dbadapter == null){dbadapter = new LimeDB(ctx);}
		List<KeyboardObj> result = dbadapter.getKeyboardList();
		return result;
	}

	public List<ImObj> getImList() throws RemoteException {
		//if(dbadapter == null){dbadapter = new LimeDB(ctx);}
		List<ImObj> result = dbadapter.getImList();
		return result;
	}


	public void clear() throws RemoteException {
		if(scorelist != null){
			scorelist.clear();
		}
		if(scorelist != null){
			scorelist.clear();
		}
		if(cache != null){
			cache.clear();
		}
		if(engcache != null){
			engcache.clear();
		}
		if(keynamecache != null){
			keynamecache.clear();
		}
		
		if(coderemapcache != null){
			keynamecache.clear();
		}
	}


	public List<Mapping> queryDictionary(String word) throws RemoteException {

		List<Mapping> result = new LinkedList<Mapping>();
		List<Mapping> cacheTemp = engcache.get(word);

		if(cacheTemp != null){
			result.addAll(cacheTemp);
		}else{
			//loadDBAdapter(); openLimeDatabase();
			List<String> tempResult = dbadapter.queryDictionary(word);
			for(String u: tempResult){
				Mapping temp = new Mapping();
				temp.setWord(u);
				temp.setDictionary(true);
				result.add(temp);
			}
			if(result.size() > 0){
				engcache.put(word, result);
			}
		}
		return result;

	}

//	public void close() throws RemoteException {
//		if(dbadapter != null){
//			try{
//				dbadapter.close();
//			}catch(Exception e){
//				Log.i(TAG, "close(): Database Close error : "+e);
//			}
//		}
//	}

	public boolean isImKeys(char c) throws RemoteException {
		if(imKeysMap.get(tablename)==null || imKeysMap.size()==0){
			//if(dbadapter == null){dbadapter = new LimeDB(ctx);}
			imKeysMap.put(tablename, dbadapter.getImInfo(tablename, "imkeys"));				
		}
		String imkeys = imKeysMap.get(tablename);
		if(!(imkeys==null || imkeys.equals(""))){
			return (imkeys.indexOf(c)>=0);
		}
		return false;
	}
	/*
		@Override
		public int isSelkey(char c) throws RemoteException {
			String selkey = "";
			if(selKeyMap.get(tablename)==null || selKeyMap.size()==0){
				//if(dbadapter == null){dbadapter = new LimeDB(ctx);}
				selkey = dbadapter.getImInfo(tablename, "selkey");
				if(selkey.equals("")) selkey = "'[]-\\^&*()";
				selKeyMap.put(tablename, selkey);
			}
			selkey = selKeyMap.get(tablename);
			if(!(selkey==null || selkey.equals(""))){
				return selkey.indexOf(c);
			}
			return -1;
		}

		@Override
		public boolean isEndkey(char c) throws RemoteException {
			if(endKeyMap.get(tablename)==null || endKeyMap.size()==0){
				//if(dbadapter == null){dbadapter = new LimeDB(ctx);}
				endKeyMap.put(tablename, dbadapter.getImInfo(tablename, "endkey"));

			}
			String endkey = endKeyMap.get(tablename);
			if(!(endkey==null || endkey.equals(""))){
				return (endkey.indexOf(c)>=0);
			}
			return false;
		}
	 */
	public String getSelkey() throws RemoteException {
		if(DEBUG) 
			Log.i(TAG, "getSelkey():hasNumber:" + hasNumberMapping + "hasSymbol:" + hasSymbolMapping);
		String selkey = "";
		String table = tablename;
		if(tablename.equals("phonetic")){
			table = tablename + mLIMEPref.getPhoneticKeyboardType();
		}
		if(selKeyMap.get(table)==null || selKeyMap.size()==0){
			//if(dbadapter == null){dbadapter = new LimeDB(ctx);}
			selkey = dbadapter.getImInfo(tablename, "selkey");
			if(DEBUG)
				Log.i(TAG, "getSelkey():selkey from db:"+selkey);
			boolean validSelkey = true;
			if(selkey!=null && selkey.length()==10){
				for(int i=0; i<10; i++){
					if(Character.isLetter(selkey.charAt(i)) ||
							(hasNumberMapping && Character.isDigit(selkey.charAt(i))))
						validSelkey = false;

				}
			}else
				validSelkey = false;
			//Jeremy '11,6,19 Rewrote for IM has symbol mapping like ETEN
			if(!validSelkey || tablename.equals("phonetic")){
				if(hasNumberMapping && hasSymbolMapping){ 
					if(tablename.equals("dayi")
							||(tablename.equals("phonetic")&&mLIMEPref.getPhoneticKeyboardType().equals("standard"))){
						selkey = "'[]-\\^&*()";
					}else{
						selkey = "!@#$%^&*()";
					}
				}else if (hasNumberMapping) {
					selkey = "'[]-\\^&*()";
				}else{
					selkey = "1234567890";
				}
			}
			if(DEBUG)
				Log.i(TAG, "getSelkey():selkey:"+selkey);
			selKeyMap.put(table, selkey);
		}
		return selKeyMap.get(table);
	}

	public int isSelkey(char c) throws RemoteException {

		return getSelkey().indexOf(c);

	}

	/*
	 * This is the method to openDB from Service
	 *
		@Deprecated
		public SQLiteDatabase getSqliteDb(){
			SQLiteDatabase db = null;
			try{
				String dbtarget = mLIMEPref.getParameterString("dbtarget");
				db = SQLiteDatabase.openDatabase(getDBPath(dbtarget), null, SQLiteDatabase.OPEN_READONLY);
			}catch(Exception e){return null;}
			return db;

		}

		/*
	 * This is the method to openDB from Service
	 *
		@Deprecated
		public SQLiteDatabase getSqliteDbWritable(){
			SQLiteDatabase db = null;
			try{
				String dbtarget = mLIMEPref.getParameterString("dbtarget");
				db = SQLiteDatabase.openDatabase(getDBPath(dbtarget), null, SQLiteDatabase.OPEN_READWRITE);
			}catch(Exception e){return null;}
			return db;

		}
		@Deprecated
		private String getDBPath(String dbTarget){
			String dbLocationPrefix = (dbTarget.equals("sdcard"))
					?LIME.DATABASE_DECOMPRESS_FOLDER_SDCARD:LIME.DATABASE_DECOMPRESS_FOLDER;
			return dbLocationPrefix + File.separator + LIME.DATABASE_NAME;
		}	
	 */

}
/*
	@Override
	public IBinder onBind(Intent arg0) {
		if(obj == null){
			obj = new SearchServiceImpl(this);
		}
		return obj;
	}



	/*
 * (non-Javadoc)
 * 
 * @see android.app.Service#onCreate()
 *
	@Override
	public void onCreate() {
		//notificationMgr =(NotificationManager)getSystemService(NOTIFICATION_SERVICE);
		super.onCreate();


	}
 */
/*
 * (non-Javadoc)
 * 
 * @see android.app.Service#onDestroy()
 *
	@Override
	public void onDestroy() {
		if(dbadapter != null){
			dbadapter.close();
		}

		super.onDestroy();
	}

	/*
 * (non-Javadoc)
 * 
 * @see android.app.Service#onStart(android.content.Intent, int)
 *  Jeremy '12,4,23 onStart is deprecated since API 15. delete here since it do nothing but call the super.
	@Override
	public void onStart(Intent intent, int startId) {
		super.onStart(intent, startId);
	}
	/*
	private void displayNotificationMessage(String message){
		LIMEUtilities util = new LIMEUtilities();
		util.showNotification(
				this, true, R.drawable.icon, this.getText(R.string.ime_setting), message, new Intent(this, LIMEMenu.class));

		/*

		Notification notification = new Notification(R.drawable.icon, message, System.currentTimeMillis());
		notification.flags |= Notification.FLAG_AUTO_CANCEL;
		PendingIntent contentIntent = PendingIntent.getActivity(this, 0,new Intent(this, LIMEMenu.class), 0);
			      	 notification.setLatestEventInfo(this, this.getText(R.string.ime_setting), message, contentIntent);
			         notificationMgr.notify(0, notification);
 *
	}

 */

//}
