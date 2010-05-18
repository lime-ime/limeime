package net.toload.main;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;

import android.content.Context;
import android.content.SharedPreferences;

public class FileUtilities {
	
	private final static String CJ_TOTAL_RECORD = "cj_total_record";
	private final static String BPMF_TOTAL_RECORD = "bpmf_total_record";
	private final static String DAYI_TOTAL_RECORD = "dayi_total_record";
	private final static String TOTAL_RELATED = "total_related";
	private final static String RELATED_TOTAL_RECORD = "related_total_record";
	private final static String DICTIONARY_TOTAL_RECORD = "dictionary_total_record";
	private final static String MAPPING_VERSION = "mapping_version";
	private final static String CJ_MAPPING_VERSION = "cj_mapping_version";
	private final static String BPMF_MAPPING_VERSION = "bmpf_mapping_version";
	private final static String DAYI_MAPPING_VERSION = "dayi_mapping_version";
	private final static String EZ_MAPPING_VERSION = "ez_mapping_version";
	private final static String DICTIONARY_VERSION = "dictionary_version";
	private final static String RELATED_MAPPING_VERSION = "related_mapping_version";
	private final static String MAPPING_LOADING = "mapping_loading";
	private final static String CANDIDATE_SUGGESTION = "candidate_suggestion";
	private final static String LEARNING_SWITCH = "learning_switch";
	
	public File isFileNotExist(String filepath){
		
		File mfile = new File(filepath);
		if(mfile.exists())
			return null;
		else
			return mfile;
	}
	public void copyRAWFile(InputStream	inStream, File newfile){
		try{
			FileOutputStream fs = new FileOutputStream(newfile);
			copyRAWFile(inStream, fs);
			fs.close();
		}
		catch(Exception e){      
    		e.printStackTrace();   
           }   
	}
	public void copyRAWFile(InputStream	inStream, FileOutputStream outStream){
	    	try{
	    		int	bytesum = 0, byteread = 0 ;
	    		 
	            byte[] buffer  = new byte[102400]; //100k buffer   
	            while((byteread = inStream.read(buffer))!=-1){   
	               	   bytesum     +=     byteread;       
	                   System.out.println(bytesum);   
	                   outStream.write(buffer, 0, byteread);   
	             	}   
	            inStream.close();      
	        	}   
	    	catch(Exception e){      
	    		e.printStackTrace();   
	           }   
	     }
	public void copyPreLoadLimeDB(Context ctx){
		
		File dbDir = new File("/data/data/net.toload.main/databases");
		if(!dbDir.exists()){
			dbDir.mkdirs();
		}
		
		File LimeDBFile = isFileNotExist("/data/data/net.toload.main/databases/lime" );
		if(LimeDBFile!=null){
			try {
				FileOutputStream fs = new FileOutputStream(LimeDBFile);
				copyRAWFile(ctx.getResources().openRawResource(R.raw.lime1),fs);
				copyRAWFile(ctx.getResources().openRawResource(R.raw.lime2),fs);
				copyRAWFile(ctx.getResources().openRawResource(R.raw.lime3),fs);
				copyRAWFile(ctx.getResources().openRawResource(R.raw.lime4),fs);
				copyRAWFile(ctx.getResources().openRawResource(R.raw.lime5),fs);
				fs.close();
			}
			catch(Exception e){      
	    		e.printStackTrace();   
	        }   
			SharedPreferences version = null, count = null;
			version = ctx.getSharedPreferences(BPMF_MAPPING_VERSION, 0);
			version.edit().putString(BPMF_MAPPING_VERSION, "¹w¸üª`­µ").commit();
			count = ctx.getSharedPreferences(BPMF_TOTAL_RECORD, 0);
			count.edit().putString(BPMF_TOTAL_RECORD, "14149").commit();
			version = ctx.getSharedPreferences(RELATED_MAPPING_VERSION, 0);
			version.edit().putString(RELATED_MAPPING_VERSION, "»Å­µµü®w§R´îª©").commit();
			count = ctx.getSharedPreferences(RELATED_TOTAL_RECORD, 0);
			count.edit().putString(RELATED_TOTAL_RECORD, "44624").commit();
			version = ctx.getSharedPreferences(DICTIONARY_VERSION, 0);
			version.edit().putString(DICTIONARY_VERSION, "wordfrequency.info(core)").commit();
			count = ctx.getSharedPreferences(DICTIONARY_TOTAL_RECORD, 0);
			count.edit().putString(DICTIONARY_TOTAL_RECORD, "5000").commit();
		}
	}

}
