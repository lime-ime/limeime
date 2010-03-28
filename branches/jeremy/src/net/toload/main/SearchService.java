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
	private final static String MAPPING_LOADING = "mapping_loading";
	private final static String CANDIDATE_SUGGESTION = "candidate_suggestion";
	private final static String LEARNING_SWITCH = "learning_switch";
	
	private LimeDB db = null;
	private static HashMap<String, List> mappingIdx = null;
	private static LinkedList diclist = null;

	private NotificationManager notificationMgr;
	
	private static SearchServiceImpl obj = null;
	private static boolean hasLoading = false;
	private static int recAmount = 0;

	public class SearchServiceImpl extends ISearchService.Stub {

		String precode = "";
		Context ctx = null;

		SearchServiceImpl(Context ctx) {
			this.ctx = ctx;
		}
		
		public String getTablename(){
			if(db == null){db = new LimeDB(ctx);}
			return db.getTablename();
		}
		
		public void setTablename(String tablename){
			if(db == null){db = new LimeDB(ctx);}
			db.setTablename(tablename);
			
		}
		
		//Modified by Jeremy '10,3 ,12 for more specific related word
		//-----------------------------------------------------------
		public List queryUserDic(String code, String word) throws RemoteException {
			if(db == null){db = new LimeDB(ctx);}
			List result = db.getDictionary(code, word);
			return result;
		}
		//-----------------------------------------------------------

		public List query(String code) throws RemoteException {
			
			if(mappingIdx == null){mappingIdx = new HashMap();}

			SharedPreferences sp1 = ctx.getSharedPreferences(MAPPING_LOADING, 0);
			String loadingstatus = sp1.getString(MAPPING_LOADING, "no");
			//Log.i("ART","Loading Status : " + loadingstatus);

			List<Mapping> result = new LinkedList();
			// Modified by Jeremy '10, 3, 28.  yes-> .. The database is loading (yes) and finished (no).
			if(code != null && loadingstatus != null && loadingstatus.equalsIgnoreCase("no")){
				if(recAmount == 0){
					try{
						recAmount = Integer.parseInt(PreferenceManager.getDefaultSharedPreferences(ctx).getString("similiar_list", "10"));				
					}catch(Exception e){e.printStackTrace();}
				}else{
					try{
						int tempRecAmount = Integer.parseInt(PreferenceManager.getDefaultSharedPreferences(ctx).getString("similiar_list", "10"));	
						if(tempRecAmount != recAmount){
							mappingIdx.clear();
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
				
				if(mappingIdx.get(code) == null){
		
					//Log.i("ART", "Query from database:" + code);
					// Start new search to database
					result.addAll(db.getMapping(code));
					
					if(result.size() > 1){
						// Has matched record then prepare suggestion list
						if(result.size() > 1){
							Mapping temp = result.get(1);
							result.addAll(db.getSuggestion(temp.getRelated(), recAmount));
						}
						mappingIdx.put(code, result);
						return mappingIdx.get(code);
					}else{
						// If there is no match result then load from cache / Check one layer only

						if(code.length() > 1){
							String tempcode = code.substring(0, (code.length() -1));
							if(mappingIdx.get(tempcode) != null){
								//Log.i("ART", "Query from cache level 1:" + code);
								List temp = mappingIdx.get(tempcode);
								result.addAll(temp.subList(1, temp.size()));
								return result;
							}else{
								if(tempcode.length() >1){
									String tempcode2 = tempcode.substring(0, (tempcode.length() -1));
									if(mappingIdx.get(tempcode2) != null){
										//Log.i("ART", "Query from cache level 2:" + code);
										List temp = mappingIdx.get(tempcode2);
										result.addAll(temp.subList(1, temp.size()));
										return result;
									}
								}
							}
						}
						
					}
					
				}else{
					//Log.i("ART", "Query from cache original:" + code);
					return mappingIdx.get(code);
				}
			}
			
			// if code == null then return empty list
			return result;
		}

		public void initial() throws RemoteException {
				
		}

		public void updateMapping(String id, String code, String word,
				String pcode, String pword, int score, boolean isDictionary)
				throws RemoteException {
			
			SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(ctx);
			boolean item = sp.getBoolean(LEARNING_SWITCH, false);
				
			if(item){
				//Log.i("ART", "Update mapping : " + code);
				
					if(code != null){
						code = code.toUpperCase();
					}
					
					Mapping temp = new Mapping();
							      temp.setId(id);
							      temp.setCode(code);
							      temp.setWord(word);
							      temp.setPcode(pcode);
							      temp.setPword(pword);
							      temp.setScore(score);
							      temp.setDictionary(isDictionary);
				    if(db == null){db = new LimeDB(ctx);}
						db.addScore(temp);
	
					if(mappingIdx.get(precode) != null){
						//Log.i("ART", "Sorting cache in memory : " + precode + " for " + code);
						List<Mapping> templist = mappingIdx.get(precode);
						List<Mapping> resultlist = new LinkedList();
						for(Mapping unit : templist){
							if(code.equalsIgnoreCase(unit.getCode()) &&
									word.equals(unit.getWord())){
								unit.setScore(unit.getScore() + 1);
							}
							resultlist.add(unit);
						}
						templist = null;
						mappingIdx.put(precode, sortArray(precode, resultlist));
					}
			}
				
		}
		
		public List<Mapping> sortArray(String precode, List<Mapping> src) {
			
				if(src != null && src.size() > 1){
					for (int i = 1; i < (src.size() - 1); i++) {
						for (int j = i + 1; j < src.size(); j++) {
							if (src.get(j).getScore() > src.get(i).getScore()) {
								Mapping dummy = src.get(i);
								if(dummy.getCode().equals(precode) && src.get(j).getCode().equals(precode)){
									src.set(i, src.get(j));
									src.set(j, dummy);
								}else if(!dummy.getCode().equals(precode) && !src.get(j).getCode().equals(precode)){
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
				String pcode, String pword, int score, boolean isDictionary)
				throws RemoteException {
				if(diclist == null){diclist = new LinkedList();}
				
				Mapping temp = new Mapping();
			      temp.setId(id);
			      temp.setCode(code);
			      temp.setWord(word);
			      temp.setPcode(pcode);
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
