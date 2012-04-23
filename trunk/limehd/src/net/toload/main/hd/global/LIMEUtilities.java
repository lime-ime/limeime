package net.toload.main.hd.global;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
//
import net.toload.main.hd.R;

import android.app.NotificationManager;
import android.support.v4.app.NotificationCompat;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;

/**
 * @author jrywu
 *
 */
public class LIMEUtilities {
	
	public File isFileNotExist(String filepath){
		
		File mfile = new File(filepath);
		if(mfile.exists())
			return null;
		else
			return mfile;
	}
	
	public File isFileExist(String filepath){
		
		File mfile = new File(filepath);
		if(mfile.exists())
			return mfile;
		else
			return null;
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
			mLIMEPref.setTableVersion("phonetic", "�w��`��");
			mLIMEPref.setTableVersion("related", "�ŭ���w�R�");
			mLIMEPref.setTableVersion("dictionary", "Wikitionary TV/Movies");
			
		}*/
	}
	
	/** Add by Jeremy '12,4,23 Show notification with notification builder in compatibility package replacing the deprecated alert dialog creation 
	 * @param context : the activity context
	 * @param autoCancel
	 * @param icon
	 * @param title
	 * @param message
	 * @param intent : the Intent the notification should be launch
	 */
	public void showNotification(Context context, Boolean autoCancel, int icon,  CharSequence title, CharSequence message, Intent intent){
		NotificationManager mNotificationManager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
		NotificationCompat.Builder mNotificationBuilder = new NotificationCompat.Builder(context);


		mNotificationBuilder.setSmallIcon(R.drawable.icon)
		    .setAutoCancel(autoCancel)
		    .setContentTitle(title)
		    .setContentText(message)
		    .setContentIntent(PendingIntent.getActivity(context, 0,intent, 0));

		mNotificationManager.notify(R.drawable.icon, mNotificationBuilder.getNotification());

	}
	

}
