package net.toload.main.hd;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;

import android.content.Context;
import android.content.SharedPreferences;

public class FileUtilities {
	
	
	
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
		
		/*
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
			
			//
			LIMEPreferenceManager mLIMEPref = new LIMEPreferenceManager(ctx);
			mLIMEPref.setTableTotalRecords("phonetic", "14149");
			mLIMEPref.setTableTotalRecords("related", "66943");
			mLIMEPref.setTableTotalRecords("dictionary", "20000");
			mLIMEPref.setTableVersion("phonetic", "¹w¸üª`­µ");
			mLIMEPref.setTableVersion("related", "»Å­µµü®w§R´îª©");
			mLIMEPref.setTableVersion("dictionary", "Wikitionary TV/Movies");
			
		}*/
	}

}
