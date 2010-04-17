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

package net.toload.main;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;

import net.toload.main.SearchService.SearchServiceImpl;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.IBinder;
import android.os.RemoteException;
import android.preference.PreferenceManager;
import android.util.Log;

public class SearchService extends Service {
	
	private static boolean SQLSELECT = true;
	
	private final static String TOTAL_RECORD = "total_record";
	
	// Add by Jeremy '10, 3 ,27. Multi table extension.
	private final static String CJ_TOTAL_RECORD = "cj_total_record";
	private final static String BPMF_TOTAL_RECORD = "bpmf_total_record";
	private final static String DAYI_TOTAL_RECORD = "dayi_total_record";
	private final static String TOTAL_RELATED = "total_related";
	private final static String MAPPING_VERSION = "mapping_version";
	private final static String CJ_MAPPING_VERSION = "cj_mapping_version";
	private final static String BPMF_MAPPING_VERSION = "bmpf_mapping_version";
	private final static String DAYI_MAPPING_VERSION = "dayi_mapping_version";
	private final static String EZ_MAPPING_VERSION = "ez_mapping_version";
	private final static String MAPPING_LOADING = "mapping_loading";
	private final static String CANDIDATE_SUGGESTION = "candidate_suggestion";
	private final static String LEARNING_SWITCH = "learning_switch";
	
	private final static String HanConvertOption = "han_convert_option";

	
	private LimeDB db = null;
	private LimeHanConverter hanConverter = null;
	private static HashMap<String, List> mappingIdx = null;
	// Add by Jeremy '10, 4, 2 . Cache for multi-table extioen
	private static HashMap<String, List> cj_mappingIdx = null;
	private static HashMap<String, List> dayi_mappingIdx = null;
	private static HashMap<String, List> bpmf_mappingIdx = null;
	private static HashMap<String, List> ez_mappingIdx = null;
	private static LinkedList diclist = null;
	// Add by Jeremy '10, 4, 2 . Cache for multi-table extioen
	private static String tablename = "";

	private NotificationManager notificationMgr;
	
	private static SearchServiceImpl obj = null;

	private static int recAmount = 0;
	private static boolean softkeypressed;

	public class SearchServiceImpl extends ISearchService.Stub {

		String precode = "";
		Context ctx = null;

		SearchServiceImpl(Context ctx) {
			this.ctx = ctx;
		}
		
		public String hanConvert(String input){
			if(hanConverter == null){hanConverter = new LimeHanConverter(ctx);}
			SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(ctx);
			Integer hanConvertOption = Integer.parseInt(sp.getString(HanConvertOption, "0"));
			
			return hanConverter.convert(input, hanConvertOption);
			
		}
		
		private HashMap<String, List> get_mappingIdx()
		{
			if(tablename.equals("cj"))
			{
				if(cj_mappingIdx == null){cj_mappingIdx = new HashMap();}
				return cj_mappingIdx;
			}else if(tablename.equals("dayi"))
			{
				if(dayi_mappingIdx == null){dayi_mappingIdx = new HashMap();}
				return dayi_mappingIdx;
			}else if(tablename.equals("phonetic"))
			{
				if(bpmf_mappingIdx == null){bpmf_mappingIdx = new HashMap();}
				return bpmf_mappingIdx;
			}else if(tablename.equals("ez"))
			{
				if(ez_mappingIdx == null){ez_mappingIdx = new HashMap();}
				return ez_mappingIdx;
			}else
			{
				if(mappingIdx == null){mappingIdx = new HashMap();}
				return mappingIdx;
			}
		}
		
		public String getTablename(){
			if(db == null){db = new LimeDB(ctx);}
			return tablename;
		}
		
		public void setTablename(String table){
			if(db == null){db = new LimeDB(ctx);}
			tablename = table;
			db.setTablename(table);
			
		}
		
		//Modified by Jeremy '10,3 ,12 for more specific related word
		//-----------------------------------------------------------
		public List queryUserDic(String code, String word) throws RemoteException {
			if(db == null){db = new LimeDB(ctx);}
			List result = db.getDictionary(code, word);
			return result;
		}
		//-----------------------------------------------------------
		
		
		//Add by jeremy '10, 4,1
		public void Rquery(String word) throws RemoteException {
			if(db == null){db = new LimeDB(ctx);}
			String result = db.getRMapping(word);
			if(result!=null && !result.equals("")){
				displayNotificationMessage(result);
			}
		
		}
		
		public List query(String code, boolean softkeyboard) throws RemoteException {
			
			//if(mappingIdx == null){mappingIdx = new HashMap();}

			SharedPreferences sp1 = ctx.getSharedPreferences(MAPPING_LOADING, 0);
			String loadingstatus = sp1.getString(MAPPING_LOADING, "");
			//Log.i("ART","Loading Status : " + loadingstatus);

			List<Mapping> result = new LinkedList();
			// Modified by Jeremy '10, 3, 28.  yes-> .. The database is loading (yes) and finished (no).
			if(code != null && loadingstatus != null && loadingstatus.equalsIgnoreCase("no")){
				// clear mappingidx when user switching between softkeyboard and hard keyboard.
				if(softkeypressed != softkeyboard){
					get_mappingIdx().clear();
					softkeypressed = softkeyboard;
				}
				if(recAmount == 0){
					try{
						recAmount = Integer.parseInt(PreferenceManager.getDefaultSharedPreferences(ctx).getString("similiar_list", "10"));				
					}catch(Exception e){e.printStackTrace();}
				}else{
					try{
						int tempRecAmount = Integer.parseInt(PreferenceManager.getDefaultSharedPreferences(ctx).getString("similiar_list", "10"));	
						if(tempRecAmount != recAmount){
							get_mappingIdx().clear();
						}
						recAmount = tempRecAmount;
					}catch(Exception e){e.printStackTrace();}
				}
	
				if(db == null){db = new LimeDB(ctx);}
	
				if (code != null) {
					Mapping temp = new Mapping();
						    temp.setCode(code);
						    temp.setWord(code);
				    result.add(temp);
					code = code.toUpperCase();
					precode = code;
				}
				
				if(get_mappingIdx().get(code) == null){
		
					//Log.i("ART", "Query from database:" + code);
					// Start new search to database
					result.addAll(db.getMapping(code, recAmount, softkeyboard));
					
					if(result.size() > 1){
						// Has matched record then prepare suggestion list
						// '10, 4, 3. Try to use nested select instead of using the related column
						if(!SQLSELECT){
							Mapping temp = result.get(1);
							result.addAll(db.getSuggestion(temp.getRelated(), recAmount));
						}
						
						get_mappingIdx().put(code, result);
						return get_mappingIdx().get(code);
					}
					else{
						// If there is no match result then load from cache / Check one layer only

						if(code.length() > 1){
							String tempcode = code.substring(0, (code.length() -1));
							if(get_mappingIdx().get(tempcode) != null){
								//Log.i("ART", "Query from cache level 1:" + code);
								List temp = get_mappingIdx().get(tempcode);
								result.addAll(temp.subList(1, temp.size()));
								return result;
							}else{
								if(tempcode.length() >1){
									String tempcode2 = tempcode.substring(0, (tempcode.length() -1));
									if(get_mappingIdx().get(tempcode2) != null){
										//Log.i("ART", "Query from cache level 2:" + code);
										List temp = get_mappingIdx().get(tempcode2);
										result.addAll(temp.subList(1, temp.size()));
										return result;
									}
								}
							}
						}
						
					}
					//*/
				}else{
					//Log.i("ART", "Query from cache original:" + code);
					return get_mappingIdx().get(code);
				}
			}
			
			// if code == null then return empty list
			return result;
		}

		public void initial() throws RemoteException {
				
		}

		public void updateMapping(String id, String code, String word,
				 String pword, int score, boolean isDictionary)
				throws RemoteException {
			
			SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(ctx);
			boolean item = sp.getBoolean(LEARNING_SWITCH, false);
				
			if(item){
				//Log.i("ART", "Update mapping : " + code);
					
					/* do this already in query
					if(code != null){
						code = code.toUpperCase();
					}
					*/
					
					Mapping temp = new Mapping();
							      temp.setId(id);
							      temp.setCode(code);
							      temp.setWord(word);
							      //temp.setPcode(pcode);
							      temp.setPword(pword);
							      temp.setScore(score);
							      temp.setDictionary(isDictionary);
				    if(db == null){db = new LimeDB(ctx);}
						db.addScore(temp);
	
					if(get_mappingIdx().get(precode) != null){
						//Log.i("ART", "Sorting cache in memory : " + precode + " for " + code);
						List<Mapping> templist = get_mappingIdx().get(precode);
						List<Mapping> resultlist = new LinkedList();
						for(Mapping unit : templist){
							if(//code.equalsIgnoreCase(unit.getCode()) &&  // Modified by Jeremy '10, 4, 4. May not equal in 3row remap
									word.equals(unit.getWord())){
								unit.setScore(unit.getScore() + 1);
							}
							resultlist.add(unit);
						}
						templist = null;
						get_mappingIdx().put(precode, sortArray(precode, resultlist));
					}
			}
				
		}
		
		public List<Mapping> sortArray(String precode, List<Mapping> src) {
			
			// Modified by jeremy '10, 4, 5. Buf fix for 3row remap. code may not equal to precode.
				if(src != null && src.size() > 1){
					for (int i = 1; i < (src.size() - 1); i++) {
						for (int j = i + 1; j < src.size(); j++) {
							if (src.get(j).getScore() > src.get(i).getScore()) {
								Mapping dummy = src.get(i);
								// Only proceed sorting when there is no exact matched. 
								// Mapping Object code=precord should always be the first item to display.
								if(!dummy.getCode().equals(precode) && !src.get(j).getCode().equals(precode)){
									src.set(i, src.get(j));
									src.set(j, dummy);
								}
							}
						}
					}
				}
			
			return src;
		}

		public void addDictionary(String id, String code, String word,
				String pword, int score, boolean isDictionary)
				throws RemoteException {
				if(diclist == null){diclist = new LinkedList();}
				
				Mapping temp = new Mapping();
			      temp.setId(id);
			      temp.setCode(code);
			      temp.setWord(word);
	//		      temp.setPcode(pcode);
			      temp.setPword(pword);
			      temp.setScore(score);
			      temp.setDictionary(isDictionary);
			      
			    diclist.addLast(temp);
		}

	public void updateDictionary() throws RemoteException {
			if(db == null){db = new LimeDB(ctx);}
			if(diclist.size() > 1){
				SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(ctx);
				boolean item = sp.getBoolean(CANDIDATE_SUGGESTION, false);
				if(item){
					db.addDictionary(diclist);
				}
			}
			diclist.clear();
		}

	}

	@Override
	public IBinder onBind(Intent arg0) {
		if(obj == null){
			obj = new SearchServiceImpl(this);
		}
		return obj;
	}

	/**
	 * Sort array
	 * @param src
	 * @return
	 */


	
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
		
		mappingIdx = null;
		cj_mappingIdx = null;
		dayi_mappingIdx = null;
		bpmf_mappingIdx = null;
		ez_mappingIdx = null;
		
		if(db != null){
			db.close();
			db = null;
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
		//Log.i("ART", "Search Service Start");
		super.onStart(intent, startId);
	}
	
	private void displayNotificationMessage(String message){
		Notification notification = new Notification(R.drawable.icon, message, System.currentTimeMillis());
		// FLAG_AUTO_CANCEL add by jeremy '10, 3 28
		notification.flags |= Notification.FLAG_AUTO_CANCEL;
		PendingIntent contentIntent = PendingIntent.getActivity(this, 0,new Intent(this, LIMEMenu.class), 0);
			      	 notification.setLatestEventInfo(this, this.getText(R.string.ime_setting), message, contentIntent);
			         notificationMgr.notify(0, notification);
	}

}
