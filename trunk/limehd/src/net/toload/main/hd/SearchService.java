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

import net.toload.main.hd.ISearchService;
import net.toload.main.hd.R;
import net.toload.main.hd.global.ImObj;
import net.toload.main.hd.global.KeyboardObj;
import net.toload.main.hd.global.LIME;
import net.toload.main.hd.global.LIMEPreferenceManager;
import net.toload.main.hd.global.Mapping;
import net.toload.main.hd.limedb.LimeDB;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.os.IBinder;
import android.os.RemoteException;
import android.util.Log;
import android.util.Pair;

public class SearchService extends Service {
	
	private final boolean DEBUG = false;
	private final String TAG = "LIME.SearchService";
	private LimeDB db = null;
	//private LimeHanConverter hanConverter = null;
	//private static LinkedList<Mapping> diclist = null;  
	private static List<Mapping> scorelist = null;

	private static List<List<Mapping>> LDPhraseListArray = null;
	private static List<Mapping> LDPhraseList = null;
	
	private static StringBuffer selectedText = new StringBuffer();
	private static String tablename = "";

	private NotificationManager notificationMgr;
	
	private LIMEPreferenceManager mLIMEPref;

	
	private static SearchServiceImpl obj = null;

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

	public class SearchServiceImpl extends ISearchService.Stub {

		Context ctx = null;

		SearchServiceImpl(Context ctx) {
			this.ctx = ctx;	
			mLIMEPref = new LIMEPreferenceManager(ctx);
			
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
			/*if(hanConverter == null){  Jeremy '11,9,8 moved to LIMEDB
				FileUtilities fu = new FileUtilities();
				//Jeremy '11,9,8 update handconverdb to v2 with base score in TCSC table
				File hanDBFile = fu.isFileExist("/data/data/net.toload.main.hd/databases/hanconvert.db");
				if(hanDBFile!=null)
					hanDBFile.delete();
				File hanDBV2File = fu.isFileNotExist("/data/data/net.toload.main.hd/databases/hanconvertv2.db");
				if(hanDBV2File!=null)
					fu.copyRAWFile(ctx.getResources().openRawResource(R.raw.hanconvertv2), hanDBV2File);
					
				hanConverter = new LimeHanConverter(ctx);
				}*/
			//SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(ctx);
			//Integer hanConvertOption = mLIMEPref.getHanCovertOption(); //Integer.parseInt(sp.getString(HanConvertOption, "0"));
			
			return db.hanConvert(input, mLIMEPref.getHanCovertOption());
			
		}
		
		public String getTablename(){
			return tablename;
		}
		
		public void setTablename(String table, boolean numberMapping, boolean symbolMapping){
			if(db == null){loadLimeDB();}
			db.setTablename(table);
			tablename = table;
			hasNumberMapping = numberMapping;
			hasSymbolMapping = symbolMapping;
		}
		
		
		private void loadLimeDB()
		{	
/*			FileUtilities fu = new FileUtilities();
			fu.copyPreLoadLimeDB(ctx);	*/		
			db = new LimeDB(ctx);
		}
		
		//Modified by Jeremy '10,3 ,12 for more specific related word
		//-----------------------------------------------------------
		public List<?> queryUserDic(String word) throws RemoteException {
			if(db == null){loadLimeDB();}
			List<?> result = db.queryUserDict(word);
			return result;
		}
		//-----------------------------------------------------------
		
		public Cursor getDictionaryAll(){
			if(db == null){loadLimeDB();}
			return db.getDictionaryAll();
			
		}
		
		//Add by jeremy '10, 4,1
		public void rQuery(final String word) throws RemoteException {
			if(db == null){loadLimeDB();}
			Thread queryThread = new Thread(){
				public void run() {
					String result = db.getRMapping(word);
					if(result!=null && !result.equals("")){
						displayNotificationMessage(result);
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
					key = mLIMEPref.getPhysicalKeyboardType()+db.getTablename()
					+ mLIMEPref.getPhoneticKeyboardType()+code;
				}else{
					key = mLIMEPref.getPhysicalKeyboardType()+db.getTablename()+code;
				}
			}else{
				if(tablename.equals("phonetic"))
					key = db.getTablename()+ mLIMEPref.getPhoneticKeyboardType()+code;
				else
					key = db.getTablename()+code;
			}
			return key;
		}
		
		public List<Mapping> query(String code, boolean softkeyboard, boolean getAllRecords) throws RemoteException {
			if(DEBUG) Log.i(TAG, "query(): code="+code);
			
			if(db == null){loadLimeDB();}
			
			// Check if system need to reset cache
			
			if(mLIMEPref.getParameterBoolean(LIME.SEARCHSRV_RESET_CACHE)){
				cache = new ConcurrentHashMap<String, Pair<List<Mapping>,List<Mapping>>>(LIME.SEARCHSRV_RESET_CACHE_SIZE);
				engcache = new ConcurrentHashMap<String, List<Mapping>>(LIME.SEARCHSRV_RESET_CACHE_SIZE);
				keynamecache = new ConcurrentHashMap<String, String>(LIME.SEARCHSRV_RESET_CACHE_SIZE);
				mLIMEPref.setParameter(LIME.SEARCHSRV_RESET_CACHE,false);
			}
			
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
				code = code.toLowerCase();
				
				temp.setCode(code);
				result.add(temp);
				int size = code.length();
				
				boolean hasMore = false;
				
				
				//Jeremy '11,8,12 if end with tone, do not add result list from less codes without tone into final result
				boolean isPhonetic = tablename.equals("phonetic");
				boolean hasTone =  code.matches(".+[3467]$");
				
				if(DEBUG) 
					Log.i(TAG, "query() code=" + code + " isPhonetic:" + isPhonetic
						+" hasTone:" + hasTone);
				
				// 11'7,22 rewritten for 連打 
				for(int i =0; i<size; i++) {
					String cacheKey = cacheKey(code);
					Pair<List<Mapping>,List<Mapping>> cacheTemp = cache.get(cacheKey);
 
					if(cacheTemp == null){
						// 25/Jul/2011 by Art
						// Just ignore error when something wrong with the result set
						try{
							cacheTemp = db.getMapping(code, !isPhysicalKeyboardPressed, getAllRecords);
							cache.put(cacheKey, cacheTemp);
						}catch(Exception e){
							e.printStackTrace();
						}
						
					}
					
					if(cacheTemp != null){
						List<Mapping> resultlist = cacheTemp.first;
						List<Mapping> relatedtlist = cacheTemp.second;
						
						if(getAllRecords &&
							(resultlist.size()>1 && 
									resultlist.get(resultlist.size()-1).getCode().equals("has_more_records")||
							 relatedtlist.size()>1&&
							 		relatedtlist.get(relatedtlist.size()-1).getCode().equals("has_more_records") )){
							try{
								cacheTemp = db.getMapping(code, !isPhysicalKeyboardPressed, true);
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
						
						if(resultlist.size()>0 && (
							 !isPhonetic 
							|| (isPhonetic && !hasTone)
							|| (isPhonetic && hasTone && code.matches(".+[3467]$"))
								) ){ 
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

		public void initial() throws RemoteException {
			cache = new ConcurrentHashMap<String, Pair<List<Mapping>,List<Mapping>>>(LIME.SEARCHSRV_RESET_CACHE_SIZE);
			engcache = new ConcurrentHashMap<String, List<Mapping>>(LIME.SEARCHSRV_RESET_CACHE_SIZE);
			keynamecache = new ConcurrentHashMap<String, String>(LIME.SEARCHSRV_RESET_CACHE_SIZE);
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
			
			db.addScore(cachedMapping);					
			// Jeremy '11,7,29 update cached here
			if (!cachedMapping.isDictionary()){
				String code = cachedMapping.getCode().toLowerCase();
				String cachekey = cacheKey(code);
				Pair<List<Mapping>, List<Mapping>> cachedPair = cache.get(cachekey);
				// null id denotes target is a word in related list
				if(cachedMapping.getId()==null   
						&& cachedPair!=null && cachedPair.second !=null && cachedPair.second.size()>0){
					if(DEBUG) Log.i(TAG,"updateUserDict: updating related list");
					if(cache.remove(cachekey)!=null){
						Pair<List<Mapping>, List<Mapping>> newPair 
						= new Pair<List<Mapping>, List<Mapping>>(cachedPair.first, db.updateRelatedList(code)) ;
						cache.put(cachekey, newPair);
					}
					// non null id denotes target is in exact match result list.
				} else  if(cachedMapping.getId()!=null && cachedPair!=null 
						&& cachedPair.first !=null && cachedPair.first.size()>0) {

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
					// Jeremy '11,7,31
					// exact match score was changed, related list in similar codes should be rebuild 
					// (eg. d, de, and def for code, defg)
					updateSimilarCodeRelatedList(code);


				}
			}


		}
		// '11,8,1 renamed from updateuserdict()
		List<Mapping> scorelistSnapshot = null; 
		public void postFinishInput() throws RemoteException {
		
			if(db == null){db = new LimeDB(ctx);} 
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
					ArrayList<List<Mapping>> localLDPhraseListArray = new ArrayList<List<Mapping>>();
					if(LDPhraseListArray!=null){
						localLDPhraseListArray.addAll(LDPhraseListArray);
						LDPhraseListArray.clear();
					}
					//Jeremy '11,7,28 combine to adduserdict and addscore 
					//Jeremy '11,6,12 do adduserdict and add score if diclist.size > 0 and only adduserdict if diclist.size >1
					//Jeremy '11,6,11, always learn scores, but sorted according preference options
						
					// Learn user dictionary (related words).
					learnUserDict(scorelistSnapshot);
					
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
							if (unit.getId()!=null
					    		&& unit.getWord() != null && !unit.getWord().equals("")
					    		&& unit2.getId() !=null
					    		&& unit2.getWord() != null && !unit2.getWord().equals("")
					    	) {
								db.addOrUpdateUserdictRecord(unit.getWord(),unit2.getWord());
							}
						}
					}
				}
			}	
		}
		
		
		private void learnLDPhrase(ArrayList<List<Mapping>> localLDPhraseListArray){
			if(localLDPhraseListArray!=null && localLDPhraseListArray.size()>0){
				if(DEBUG) 
					Log.i(TAG,"learnLDPhrase(): LDPhrase learning, arraysize =" + localLDPhraseListArray.size());
				for(List<Mapping> phraselist : localLDPhraseListArray ){
					if(DEBUG) 
						Log.i(TAG,"learnLDPhrase(): LDPhrase learning, current list size =" + phraselist.size());
					if(phraselist.size()>0){	
						String baseCode="", LDCode ="", QPCode ="", baseWord="";
						Mapping unit1 = phraselist.get(0);

						if(unit1 == null
								|| unit1.getCode().length()==0
								|| unit1.getWord().length()==0){break;}
						
						baseCode = unit1.getCode();
						baseWord = unit1.getWord();
						QPCode = baseCode.substring(0, 1);

						for (int i = 0; i < phraselist.size(); i++) {
							if(i+1 <phraselist.size()){
								Mapping unit2 = phraselist.get((i + 1));
								if(unit2 == null 
										|| unit2.getCode().length()==0
										|| unit2.getWord().length()==0){break;}				
								baseCode += unit2.getCode();
								baseCode = baseCode.toLowerCase();
								baseWord += unit2.getWord();
								if(tablename.equals("phonetic")) {// remove tone symbol in phonetic table 
									LDCode = baseCode.replaceAll("[3467]", "").toLowerCase();
									QPCode += unit2.getCode().substring(0, 1).toLowerCase();
									if(LDCode.length()>1){
										db.addOrUpdateMappingRecord(LDCode, baseWord);
										cache.remove(cacheKey(LDCode));
										updateSimilarCodeRelatedList(LDCode);
									}
									if(QPCode.length()>1){
										db.addOrUpdateMappingRecord(QPCode, baseWord);
										cache.remove(cacheKey(QPCode.toLowerCase()));
										updateSimilarCodeRelatedList(QPCode.toLowerCase());
									}
								}else if(baseCode.length()>1){
									db.addOrUpdateMappingRecord(baseCode, baseWord);
									cache.remove(cacheKey(baseCode));
									updateSimilarCodeRelatedList(baseCode);
								}

								if(DEBUG) 
									Log.i(TAG,"learnLDPhrase(): LDPhrase learning, baseCode = " + baseCode 
											+ ". LDCode=" + LDCode + ". QPCode=" + QPCode);

							}
						}
					}
				}
				
				
			}
		}
		
		private void updateSimilarCodeRelatedList(String code){
			if(DEBUG)
				Log.i(TAG, "updateSimilarCodeRelatedList(): code =" + code);
			String cachekey = cacheKey(code);
			Pair<List<Mapping>, List<Mapping>> cachedPair = cache.get(cachekey);
			int len = code.length();
			if(len > 4 ) len = 4; //Jeremy '11,9,14
			for (int k = 1; k < len; k++) {
				String key = code.substring(0, code.length() - k);
				cachekey = cacheKey(key);
				cachedPair = cache.get(cachekey);
				if(cache.remove(cachekey)!=null && cachedPair !=null){					
					Pair<List<Mapping>, List<Mapping>> newPair 
						= new Pair<List<Mapping>, List<Mapping>>(cachedPair.first, db.updateRelatedList(key)) ;
					cache.put(cachekey, newPair);
				}
			}
		}
		
		
		
		public String keyToKeyname(String code){
			//Jeremy '11,6,21 Build cache according using cachekey
			
			String cacheKey = cacheKey(code);
			String result = keynamecache.get(cacheKey);
			if(result == null){
				if(db == null){loadLimeDB();}
				result = db.keyToKeyname(code, tablename, true);
				keynamecache.put(cacheKey, result);
			}
			return result;
			
		}

		@Override
		public void addUserDict(String id, String code, String word,
				String pword, int score, boolean isDictionary)
				throws RemoteException {
			 
			if(scorelist == null){scorelist = new ArrayList<Mapping>();}
			if(db == null){db = new LimeDB(ctx);}
			// Temp Mapping Object For updateMapping method.
			final Mapping updateMappingTemp = new Mapping();
			updateMappingTemp.setId(id);
			updateMappingTemp.setCode(code);
			updateMappingTemp.setWord(word);
			updateMappingTemp.setPword(pword);
			updateMappingTemp.setScore(score);
			updateMappingTemp.setDictionary(isDictionary);
		      
			// Jeremy '11,6,11. Always update score and sort according to preferences.
			scorelist.add(updateMappingTemp);
			Thread UpadtingThread = new Thread(){
				public void run() {
					updateScoreCache(updateMappingTemp);
				}
			};
			UpadtingThread.start();
			
			
		}
		
		@Override
		public void addLDPhrase(String id, String code, String word, int score, boolean ending){
			if(LDPhraseListArray == null) 
				LDPhraseListArray = new ArrayList<List<Mapping>>();
			if(LDPhraseList == null)
				LDPhraseList = new LinkedList<Mapping>(); 
			
			
			
			if(id != null){ // force interruped if id=null
				Mapping tempMapping = new Mapping();
				tempMapping.setId(id);
				tempMapping.setCode(code);
				tempMapping.setWord(word);
				tempMapping.setScore(score);
				tempMapping.setDictionary(false);
				LDPhraseList.add(tempMapping);
			}
			
			if(ending){
				if(LDPhraseList.size()>1)
					LDPhraseListArray.add(LDPhraseList);
				LDPhraseList = new LinkedList<Mapping>();		
			}
			
			if(DEBUG) Log.i(TAG,"addLDPhrase(), code="+code + ". id=" + id + ". engding:" + ending
					+ ". LDPhraseListArray.size=" + LDPhraseListArray.size()
					+ ". LDPhraseList.size=" + LDPhraseList.size()); 
			
		}

		@Override
		public List<KeyboardObj> getKeyboardList() throws RemoteException {
			if(db == null){db = new LimeDB(ctx);}
			List<KeyboardObj> result = db.getKeyboardList();
			return result;
		}

		@Override
		public List<ImObj> getImList() throws RemoteException {
			if(db == null){db = new LimeDB(ctx);}
			List<ImObj> result = db.getImList();
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
		}


		@Override
		public List<Mapping> queryDictionary(String word) throws RemoteException {

			List<Mapping> result = new LinkedList<Mapping>();
		    List<Mapping> cacheTemp = engcache.get(word);
		    
			if(cacheTemp != null){
				result.addAll(cacheTemp);
			}else{
				if(db == null){loadLimeDB();}
				List<String> tempResult = db.queryDictionary(word);
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

		@Override
		public void close() throws RemoteException {
			if(db != null){
				try{
					db.close();
				}catch(Exception e){
					Log.i(TAG, "close(): Database Close error : "+e);
				}
			}
		}

		@Override
		public boolean isImKeys(char c) throws RemoteException {
			if(imKeysMap.get(tablename)==null || imKeysMap.size()==0){
				if(db == null){db = new LimeDB(ctx);}
				imKeysMap.put(tablename, db.getImInfo(tablename, "imkeys"));				
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
				if(db == null){db = new LimeDB(ctx);}
				selkey = db.getImInfo(tablename, "selkey");
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
				if(db == null){db = new LimeDB(ctx);}
				endKeyMap.put(tablename, db.getImInfo(tablename, "endkey"));
				
			}
			String endkey = endKeyMap.get(tablename);
			if(!(endkey==null || endkey.equals(""))){
				return (endkey.indexOf(c)>=0);
			}
			return false;
		}
*/
		@Override
		public String getSelkey() throws RemoteException {
			if(DEBUG) 
				Log.i(TAG, "getSelkey():hasNumber:" + hasNumberMapping + "hasSymbol:" + hasSymbolMapping);
			String selkey = "";
			String table = tablename;
			if(tablename.equals("phonetic")){
				table = tablename + mLIMEPref.getPhoneticKeyboardType();
			}
			if(selKeyMap.get(table)==null || selKeyMap.size()==0){
				if(db == null){db = new LimeDB(ctx);}
				selkey = db.getImInfo(tablename, "selkey");
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

		@Override
		public int isSelkey(char c) throws RemoteException {
			
			return getSelkey().indexOf(c);
			
		}
	}

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
	 */
	@Override
	public void onCreate() {
		notificationMgr =(NotificationManager)getSystemService(NOTIFICATION_SERVICE);
		super.onCreate();
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see android.app.Service#onDestroy()
	 */
	@Override
	public void onDestroy() {
		if(db != null){
			db.close();
		}
		super.onDestroy();
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see android.app.Service#onStart(android.content.Intent, int)
	 */
	@Override
	public void onStart(Intent intent, int startId) {
		super.onStart(intent, startId);
	}
	
	private void displayNotificationMessage(String message){
		Notification notification = new Notification(R.drawable.icon, message, System.currentTimeMillis());
		notification.flags |= Notification.FLAG_AUTO_CANCEL;
		PendingIntent contentIntent = PendingIntent.getActivity(this, 0,new Intent(this, LIMEMenu.class), 0);
			      	 notification.setLatestEventInfo(this, this.getText(R.string.ime_setting), message, contentIntent);
			         notificationMgr.notify(0, notification);
	}
	
	
}
