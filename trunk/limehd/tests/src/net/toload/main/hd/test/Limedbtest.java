package net.toload.main.hd.test;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Random;

import android.test.AndroidTestCase;
import android.util.Log;
import android.util.Pair;
import net.toload.main.hd.global.Mapping;
import net.toload.main.hd.limedb.LimeDB;


public class Limedbtest extends AndroidTestCase {	
	private final String TAG = "LimeDBTest";
	private final static String TEST_KEY = "1234567890qwertyuiopasdfghjkl;zxcvbnm,./-";
	private List<String> keyList;

	@Override
	protected void setUp() throws Exception {
		super.setUp();

	}



	@Override
	protected void tearDown() throws Exception {
		super.tearDown();
	}
	public void testLoadDB(){
		LimeDB db = new LimeDB(getContext());
		assertNotNull (db);
	}
	public void testQuery(){
		LimeDB db = new LimeDB(getContext());
		assertNotNull (db);
		db.setTablename("phonetic");

		buildKeyMap();
		int count=0;
		int limit = 100;
		long timelimit = 200;
		HashSet<String> duplityCheck = new HashSet<String>();
		Random randomGenerator = new Random();
		while(count<limit){
			int i = randomGenerator.nextInt(keyList.size());
			int j = randomGenerator.nextInt(keyList.size());
			int k = randomGenerator.nextInt(keyList.size());
			int l = randomGenerator.nextInt(keyList.size());
			String code = keyList.get(i)+keyList.get(j)+keyList.get(k)+keyList.get(l); 
			//SQLiteDatabase dbadapter = db.getSqliteDb(true);
			
			//Log.i(TAG,"code=" + code);
			for(int m=1; m<4; m++){
				String query_code = code.substring(0, m);
				if(duplityCheck.add(query_code)){
					long begin =  System.currentTimeMillis();
					//Log.i(TAG,"query_code="+ query_code);
					Pair<List<Mapping>,List<Mapping>> temp = db.getMapping(query_code, true, true);


					long elapsed =  System.currentTimeMillis() - begin;

					int resultsize=0, relatedsize=0;
					if(temp.first!=null) resultsize = temp.first.size();
					if(temp.second!=null) relatedsize = temp.second.size();
					Log.i(TAG,"testQuery("+count+"):query_code="+ query_code 
							+ ", resultlist.size=" +resultsize
							+", realtedList.size="+relatedsize
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

}
