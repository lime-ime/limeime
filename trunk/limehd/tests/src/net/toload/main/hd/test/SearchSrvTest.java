package net.toload.main.hd.test;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Random;

import net.toload.main.hd.SearchServer;
import net.toload.main.hd.global.Mapping;
import android.os.RemoteException;
import android.os.SystemClock;
import android.test.AndroidTestCase;
import android.util.Log;

public class SearchSrvTest extends AndroidTestCase {
	
	//public SearchSrvTest() {
	//	super(SearchServer.class);
		// TODO Auto-generated constructor stub
	//}

	private final String TAG = "SearchSrvTest";
	private final static String TEST_KEY = "1234567890qwertyuiopasdfghjkl;zxcvbnm,./-";
	private List<String> keyList;
	private SearchServer SearchSrv = null;

	

	protected void setUp() throws Exception {
		super.setUp();
		//mContext = getContext();

	}


	protected void tearDown() throws Exception {
		//mContext = null;
		super.tearDown();
	}
	
	public void testRandomQuery(){
		Log.i(TAG, "testQueryStart()");
		SearchSrv = new SearchServer( getContext());
		/*try {
			mContext.bindService(new Intent(ISearchService.class.getName()),
					serConn, Context.BIND_AUTO_CREATE);

		} catch (Exception e) {
			Log.i(TAG, "testQueryStart(): Failed to connect Search Service");
		}
		*/
		
		SystemClock.sleep(1000);
	
		
		assertNotNull (SearchSrv);
		SearchSrv.setTablename("phonetic", true, true);

		buildKeyMap();
		
		//Testing parameters.---------------
		int limit = 5;   //times performing random queries
		long timelimit = 5000;// Assert the query time smaller than this time spec.
		boolean getFullRecords = false; 
		
		HashSet<String> duplityCheck = new HashSet<String>();
		Random randomGenerator = new Random();
		int count=0;
		while(count<limit){
			int i = randomGenerator.nextInt(keyList.size());
			int j = randomGenerator.nextInt(keyList.size());
			int k = randomGenerator.nextInt(keyList.size());
			int l = randomGenerator.nextInt(keyList.size());
			String code = keyList.get(i)+keyList.get(j)+keyList.get(k)+keyList.get(l); 
			//Log.i(TAG,"code=" + code);
			for(int m=1; m<4; m++){
				String query_code = code.substring(0, m);
				if(duplityCheck.add(query_code)){
					long begin =  System.currentTimeMillis();
					LinkedList<Mapping> list = new LinkedList<Mapping>();
					try {
						list.addAll(SearchSrv.query(query_code, true, getFullRecords));
					} catch (RemoteException e) {
						e.printStackTrace();
					}


					long elapsed =  System.currentTimeMillis() - begin;

					int resultsize=0;//, relatedsize=0;
					resultsize = list.size();
					Log.i(TAG,"testQuery("+count+"):query_code="+ query_code 
							+ ", resultlist.size=" +resultsize
							+", query time=" + elapsed + "ms");

					assertTrue("Qery time longer than " + timelimit + " ms", elapsed <timelimit);
					count++;
				}
			}



		}
	}
	
	
	private void buildKeyMap(){
		 String keyString = TEST_KEY;
		 keyList = new ArrayList<String>();
		 for (int i = 0; i < keyString.length(); i++) {
			 keyList.add( keyString.substring(i, i + 1));
			
			}
	}
	
	/*
	 /*
	  * Construct SerConn
	  *
	 private ServiceConnection serConn = new ServiceConnection() {
		 public void onServiceConnected(ComponentName name, IBinder service) {
			 
			 Log.i(TAG, "ServiceConnection.onServiceConnected(): connectting Search Service");
			 assertNotNull (service);
			 SearchSrv = ISearchService.Stub.asInterface(service);
			
			 try {
				 SearchSrv.initial();
			 } catch (RemoteException e) {
				 e.printStackTrace();
			 }
		 }

		 public void onServiceDisconnected(ComponentName name) {
		 }
	 };
	 */
}
