package net.toload.main.hd.test;


import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Random;

import net.toload.main.hd.LIMEService;
import android.content.Intent;
import android.os.Debug;
import android.os.SystemClock;
import android.test.ServiceTestCase;
import android.util.Log;
import android.view.KeyEvent;
import android.view.inputmethod.EditorInfo;

public class TestLimeService extends ServiceTestCase<LIMEService> {
	
	private final String TAG = "TestLimeService";
	private final static String TEST_KEY = "1234567890qwertyuiopasdfghjkl;zxcvbnm,./-";
	private List<String> keyList;

	public TestLimeService() {
		super(LIMEService.class);
	}
	protected void setUp() throws Exception {
		super.setUp();
		mContext = getContext();

	}

	protected void tearDown() throws Exception {
		mContext = null;
		super.tearDown();
	}
	
	 public void testLIMEServiceStart() {
	        Log.i(TAG, "testLIMEServiceStart() starting...");
	        try {
	            Intent intent = new Intent();
	            startService(intent);
	            LIMEService Serv=getService();
	            assertNotNull(Serv);
	            
	            Serv.onCreateInputView();
	            //Serv.onCreateCandidatesView();
	            	            
	            EditorInfo attribute = new EditorInfo();
	            attribute.inputType = EditorInfo.TYPE_CLASS_TEXT;
				Serv.onStartInput(attribute, false);
				
				randomTypingTest(Serv);
				

	         } catch (Exception e) {
	            e.printStackTrace();
	            fail(e.getMessage());
	        } finally {
	            Log.i(TAG, "testLIMEServiceStart() ended.");
	        }
	  }
	private void simulateInput(LIMEService Serv, String code, boolean physicalKey) {
		
		int[] keyCodes = {0};
		char[] charray = code.toCharArray();
		for(char key: charray){
			long begin =  System.currentTimeMillis();
			if(physicalKey){
				KeyEvent event = new KeyEvent(begin, begin, KeyEvent.ACTION_DOWN , key, 0);
				Serv.onKeyDown((int)key, event);
				event = new KeyEvent(begin, begin+5, KeyEvent.ACTION_DOWN , key, 0);
				Serv.onKeyUp((int)key, event);
			}else{
				Serv.onKey((int)key, keyCodes);
			}
			long elapsed =  System.currentTimeMillis() - begin;
			SystemClock.sleep(10);
			Log.i(TAG,"Simulate onkey() with key=" + String.valueOf(key) + ", using " + elapsed + " ms");
		}
		if(Serv.hasMappingList) 
			//Log.i(TAG, "testLIMEServiceStart() hasMappingList true");
			Serv.pickDefaultCandidate();
	}
	
	private void randomTypingTest(LIMEService Serv){
		buildKeyMap();
		
		//Testing parameters.---------------
		int limit = 10;   //times performing random queries
		//long timelimit = 250;// Assert the query time smaller than this time spec.
		boolean simulatePhsyicalKeyboard = true;
		
		
		
		
		Debug.startMethodTracing("LimeService");

		HashSet<String> duplityCheck = new HashSet<String>();
		Random randomGenerator = new Random();
		int count=0;
		while(count<limit){
			int size = randomGenerator.nextInt(4);
			String code ="";
			for(int i=0; i<size; i++){
				code +=	keyList.get(randomGenerator.nextInt(keyList.size()));
			}
			
			if(duplityCheck.add(code)){
				Log.i(TAG,"Simulate typing code=" + code + ", count=" + count);
				simulateInput(Serv, code, simulatePhsyicalKeyboard);
			}
			SystemClock.sleep(100);
			count++;
		}
		Debug.stopMethodTracing();

	}
	 public void testLIMEServiceStop() {
		 Log.i(TAG, "testLIMEServiceStop() starting...");

	        try {
	            Intent intent = new Intent();

	            startService(intent);
	            LIMEService service = getService();

	            service.stopService(intent);
	        }
	        catch (Exception e) {
	            e.printStackTrace();
	            fail(e.toString());
	        }
	        finally {
	            Log.i(TAG, "testLIMEServiceStop() ended");
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
