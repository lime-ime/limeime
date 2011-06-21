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

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

//import net.toload.main.hd.SearchService.SearchServiceImpl;

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

public class SearchService extends Service {
	
	private final boolean DEBUG = false;
	private LimeDB db = null;
	private LimeHanConverter hanConverter = null;
	private static LinkedList<Mapping> diclist = null;
	private static List<Mapping> scorelist = null;

	private static StringBuffer selectedText = new StringBuffer();
	private static String tablename = "";

	private NotificationManager notificationMgr;
	
	private LIMEPreferenceManager mLIMEPref;

	// Temp Mapping Object For updateMapping method.
	Mapping updateMappingTemp = null;
	
	private static SearchServiceImpl obj = null;

	//private static int recAmount = 0;
	private static boolean isPhysicalKeyboardPressed; // Sync to LIMEService and LIMEDB
	//Jeremy '11,6,10
	private static boolean hasNumberMapping;
	private static boolean hasSymbolMapping;
	
	
	private static List<Mapping> preresultlist = null;
	//private static String precode = null;
	
	//Jeremy '11,6,6
	private HashMap<String,String> imKeysMap = new HashMap<String,String>();
	private HashMap<String,String> selKeyMap = new HashMap<String,String>();
	//private HashMap<String,String> endKeyMap = new HashMap<String,String>();
	
	private static ConcurrentHashMap<String,List<Mapping>> cache = null;
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
			if(hanConverter == null){
				FileUtilities fu = new FileUtilities();
				File hanDBFile = fu.isFileNotExist("/data/data/net.toload.main.hd/databases/hanconvert.db");
				if(hanDBFile!=null){
					fu.copyRAWFile(ctx.getResources().openRawResource(R.raw.hanconvert), hanDBFile);
					
				}
				hanConverter = new LimeHanConverter(ctx);
				}
			//SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(ctx);
			Integer hanConvertOption = mLIMEPref.getHanCovertOption(); //Integer.parseInt(sp.getString(HanConvertOption, "0"));
			
			return hanConverter.convert(input, hanConvertOption);
			
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
		public void rQuery(String word) throws RemoteException {
			if(db == null){loadLimeDB();}
			String result = db.getRMapping(word);
			if(result!=null && !result.equals("")){
				displayNotificationMessage(result);
			}
		
		}
		
		public List<Mapping> query(String code, boolean softkeyboard) throws RemoteException {
			
			if(db == null){loadLimeDB();}
			
			//Log.i("ART","Run SearchSrv query:"+ code);
			// Check if system need to reset cache
			
			if(mLIMEPref.getParameterBoolean(LIME.SEARCHSRV_RESET_CACHE)){
				cache = new ConcurrentHashMap<String, List<Mapping>>(LIME.SEARCHSRV_RESET_CACHE_SIZE);
				engcache = new ConcurrentHashMap<String, List<Mapping>>(LIME.SEARCHSRV_RESET_CACHE_SIZE);
				keynamecache = new ConcurrentHashMap<String, String>(LIME.SEARCHSRV_RESET_CACHE_SIZE);
				mLIMEPref.setParameter(LIME.SEARCHSRV_RESET_CACHE,false);
			}
			
			if(preresultlist == null){preresultlist = new LinkedList<Mapping>();}
			List<Mapping> result = new LinkedList<Mapping>();
			// Modified by Jeremy '10, 3, 28.  yes-> .. The database is loading (yes) and finished (no).
			//if(code != null && loadingstatus != null && loadingstatus.equalsIgnoreCase("no")){
			if(code!=null) {
				// clear mappingidx when user switching between softkeyboard and hard keyboard. Jeremy '11,6,11
				if(isPhysicalKeyboardPressed == softkeyboard)
					isPhysicalKeyboardPressed = !softkeyboard;
					
				
				
				Mapping temp = new Mapping();
				temp.setWord(code);
				code = code.trim().toLowerCase();
				temp.setCode(code);
				result.add(temp);
				
				if(code.length() == 1){
					preresultlist = new LinkedList<Mapping>();
				}
				//Jeremy '11,6,17 Seperate physical keyboard cache with keybaordtype
				String cacheKey="";
				if(isPhysicalKeyboardPressed){	
					if(tablename.equals("phonetic")){
						cacheKey = mLIMEPref.getPhysicalKeyboardType()+db.getTablename()
									+ mLIMEPref.getPhoneticKeyboardType()+code;
					}else{
						cacheKey = mLIMEPref.getPhysicalKeyboardType()+db.getTablename()+code;
					}
				}else{
					if(tablename.equals("phonetic"))
						cacheKey = db.getTablename()+ mLIMEPref.getPhoneticKeyboardType()+code;
					else
						cacheKey = db.getTablename()+code;
				}
				
			    List<Mapping> cacheTemp = cache.get(cacheKey);
			    
				if(cacheTemp != null){
					result.addAll(cacheTemp);
					preresultlist = cacheTemp;
				}else{

					List<Mapping> templist = db.getMapping(code, !isPhysicalKeyboardPressed);
					if(templist.size() > 0){
						result.addAll(templist);
						preresultlist = templist;
						cache.put(cacheKey, templist);
					}else{
						if(code.length() > 3 &&  
								cache.get(cacheKey.subSequence(0, code.length()-1)) != null && 
								cache.get(cacheKey.subSequence(0, code.length()-2)) != null 
						){ 
							result.addAll(preresultlist);
						}
					}
					
				}
			}
			return result;
		}

		public void initial() throws RemoteException {
			cache = new ConcurrentHashMap<String, List<Mapping>>(LIME.SEARCHSRV_RESET_CACHE_SIZE);
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
			    
			    //Log.i("ART","addUserDict:" + temp.getCode());
		}

		public void updateUserDict() throws RemoteException {
			//Log.i("SearchService:","updateUserDict:"+diclist);
			
			if(db == null){db = new LimeDB(ctx);}
			//Jeremy '11,6,12 do adduserdict and add score if diclist.size > 0 and only adduserdict if diclist.size >1
			if(diclist != null && mLIMEPref.getLearnRelatedWord() && diclist.size() > 1){
				db.addUserDict(diclist);
				diclist.clear();
			}
				
				//Jeremy '11,6,11, always learn scores, but sorted according preference options
				//boolean item2 = sp.getBoolean(LIME.LEARNING_SWITCH, false);

				//if(item2 && scorelist != null){
			if(scorelist != null){
				for(int i=0 ; i < scorelist.size(); i++){
					//Log.i("ART","updateUserDict addScore:"+((Mapping)scorelist.get(i)).getCode() + " " + ((Mapping)scorelist.get(i)).getId());
					db.addScore((Mapping)scorelist.get(i));
					
					//Jeremy '11,6,13 Words in relatedlist is selected for null id, the relatedlist will later be updated.
					if(scorelist.get(i).getId()==null){ // Force to delete the cached item.
						String code = scorelist.get(i).getCode().toLowerCase();
						//Log.i("SearchService:updateUserdict()","force to delete cached item, code = " + code);
						String cacheKey="";
						if(isPhysicalKeyboardPressed){	
							if(tablename.equals("phonetic")){
								cacheKey = mLIMEPref.getPhysicalKeyboardType()+db.getTablename()
											+ mLIMEPref.getPhoneticKeyboardType()+code;
							}else{
								cacheKey = mLIMEPref.getPhysicalKeyboardType()+db.getTablename()+code;
							}
							
						}else{
							if(tablename.equals("phonetic"))
								cacheKey = db.getTablename()+ mLIMEPref.getPhoneticKeyboardType()+code;
							else
								cacheKey = db.getTablename()+code;
						}
						cache.remove(cacheKey);
					}
					
						
				}
				scorelist.clear();
			}	
			
		}
		
		public String keyToKeyname(String code){
			//Jeremy '11,6,21 Build cache according using cachekey
			String cacheKey="";
			if(isPhysicalKeyboardPressed){
				if(tablename.equals("phonetic")){
					cacheKey = mLIMEPref.getPhysicalKeyboardType()+db.getTablename()
								+ mLIMEPref.getPhoneticKeyboardType()+code;
				}else{
					cacheKey = mLIMEPref.getPhysicalKeyboardType()+db.getTablename()+code;
				}
				
			}else{
				if(tablename.equals("phonetic"))
					cacheKey = db.getTablename()+ mLIMEPref.getPhoneticKeyboardType()+code;
				else
					cacheKey = db.getTablename()+code;
			}
			
			String result = keynamecache.get(cacheKey);
			if(result == null){
				if(db == null){loadLimeDB();}
				result = db.keyToKeyname(code, tablename, true);
				keynamecache.put(cacheKey, result);
			}
			return result;
			
		}

		@Override
		public void updateMapping(String id, String code, String word,
				String pword, int score, boolean isDictionary)
				throws RemoteException {
			//Log.i("ART","updateMapping:"+scorelist);
			//SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(ctx);
			//boolean item = sp.getBoolean(LIME.LEARNING_SWITCH, false);
			 
			if(scorelist == null){scorelist = new ArrayList<Mapping>();}
			if(db == null){db = new LimeDB(ctx);}
			
			updateMappingTemp = new Mapping();
			updateMappingTemp.setId(id);
			updateMappingTemp.setCode(code);
			updateMappingTemp.setWord(word);
			updateMappingTemp.setPword(pword);
			updateMappingTemp.setScore(score);
			updateMappingTemp.setDictionary(isDictionary);
		      
			// Jeremy '11,6,11. Always update score and sort according to preferences.
			//if(item){
			//Log.i("ART","updateMapping:"+updateMappingTemp.getCode());
			scorelist.add(updateMappingTemp);
			//}
			
			
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

		@Override
		public void clear() throws RemoteException {
			if(diclist != null){
				diclist.clear();
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
					Log.i("ART","Database Close error : "+e);
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
				Log.i("SearchService:getSelkey()","hasNumber:" + hasNumberMapping + "hasSymbol:" + hasSymbolMapping);
			String selkey = "";
			String table = tablename;
			if(tablename.equals("phonetic")){
				table = tablename + mLIMEPref.getPhoneticKeyboardType();
			}
			if(selKeyMap.get(table)==null || selKeyMap.size()==0){
				if(db == null){db = new LimeDB(ctx);}
				selkey = db.getImInfo(tablename, "selkey");
				if(DEBUG)
					Log.i("SearchService:getSelkey()","selkey from db:"+selkey);
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
					Log.i("SearchService:getSelkey()","selkey:"+selkey);
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
