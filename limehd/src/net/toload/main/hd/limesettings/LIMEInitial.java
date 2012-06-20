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

package net.toload.main.hd.limesettings;

import java.io.File;

import net.toload.main.hd.global.LIME;
import net.toload.main.hd.global.LIMEPreferenceManager;
import net.toload.main.hd.R;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.AlertDialog.Builder;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.DialogInterface.OnCancelListener;
import android.net.ConnectivityManager;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.RemoteException;
import android.os.StrictMode;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.Toast;


/**
 * 
 * @author Art Hung
 * 
 */
public class LIMEInitial extends Activity {
	

	private DBCloudServer DBSrv = null;
	Button btnInitPreloadDB = null;
	Button btnInitPhoneticHsOnlyDB = null; 
	Button btnInitPhoneticOnlyDB = null;//Jeremy '11,9,10
	Button btnInitEmptyDB = null;
	Button btnResetDB = null;
	Button btnBackupDB = null;
	Button btnRestoreDB = null;
	Button btnCloudBackupDB = null;
	Button btnCloudRestoreDB = null;
	Button btnStoreDevice = null;
	Button btnStoreSdcard = null;
	
	boolean hasReset = false;
	LIMEPreferenceManager mLIMEPref;
	ConnectivityManager connManager = null;
	
	Activity activity = null;
	
	/** Called when the activity is first created. */
	@Override
	public void onCreate(Bundle icicle) {
		
		super.onCreate(icicle);
		this.setContentView(R.layout.initial);
		activity = this;
		
		if(android.os.Build.VERSION.SDK_INT > 10){
			StrictMode.setThreadPolicy(new StrictMode.ThreadPolicy.Builder()     
			        //.detectDiskReads()   
			        //.detectDiskWrites()     
			        .detectNetwork()   // or .detectAll() for all detectable problems     
			        .penaltyLog()     
			        .build());     
			 StrictMode.setVmPolicy(new StrictMode.VmPolicy.Builder()     
			        //.detectLeakedSqlLiteObjects()     
			        //.detectLeakedClosableObjects()     
			        .penaltyLog()     
			        .penaltyDeath()     
			        .build());
		}
		
		
		// Startup Service
		//getApplicationContext().bindService(new Intent(IDBService.class.getName()), serConn, Context.BIND_AUTO_CREATE);
		DBSrv = new DBCloudServer(getApplicationContext());
		mLIMEPref = new LIMEPreferenceManager(getApplicationContext());
		connManager = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
		
	    
		// Initial Buttons
		initialButton();

		btnResetDB.setOnClickListener(new OnClickListener() {
			public void onClick(View v) {
				
				// Reset Table Information
				resetLabelInfo("custom");
				resetLabelInfo("cj");
				resetLabelInfo("scj");
				resetLabelInfo("cj5");
				resetLabelInfo("ecj");
				resetLabelInfo("array");
				resetLabelInfo("array10");
				resetLabelInfo("dayi");
				resetLabelInfo("ez");
				resetLabelInfo("phonetic");
				resetLabelInfo("wb");
				resetLabelInfo("hs");
				
				btnResetDB.setEnabled(false);
				
				AlertDialog.Builder builder = new AlertDialog.Builder(v.getContext());
		    	     				builder.setMessage(getText(R.string.l3_message_database_reset_confirm));
		    	     				builder.setCancelable(false);
		    	     				builder.setPositiveButton("Yes", new DialogInterface.OnClickListener() {
		    	     					public void onClick(DialogInterface dialog, int id) {
						    										    					
							    				try {
							    					//DBSrv.closeDatabse(); done in DBSrv.resetDownloadDatabase
							    					DBSrv.resetDownloadDatabase();
						    						mLIMEPref.setParameter("im_loading", false);
						    						mLIMEPref.setParameter("im_loading_table", "");
						    						mLIMEPref.setParameter(LIME.DOWNLOAD_START, false);
						    						mLIMEPref.setParameter("db_finish", false);
						    						btnStoreDevice.setText(getText(R.string.l3_initial_btn_store_device));
						    						btnStoreSdcard.setText(getText(R.string.l3_initial_btn_store_sdcard));
							    				} catch (RemoteException e) {
							    					e.printStackTrace();
							    				}
							    				hasReset = true;
							    				initialButton();	//Jeremy '12,5,1 do initialbutton after resetDatabase
						    	        	}
						    	     });
		    	        
						    	    builder.setNegativeButton("No", new DialogInterface.OnClickListener() {
						    	    	public void onClick(DialogInterface dialog, int id) {
					    						btnResetDB.setEnabled(true);
						    	        		hasReset = false;
						    	        	}
						    	     });   
		    	        
				AlertDialog alert = builder.create();
		    				alert.show();
		    	    
				
				// Reset for SearchSrv
				mLIMEPref.setParameter(LIME.SEARCHSRV_RESET_CACHE,false);
				
			}
		});
		

		btnInitPreloadDB.setOnClickListener(new OnClickListener() {
			public void onClick(View v) {

		        if(connManager.getActiveNetworkInfo() != null && connManager.getActiveNetworkInfo().isConnected()){
		        	try {
    					//DBSrv.closeDatabse(); done in DBSrv already
		        		btnInitEmptyDB.setEnabled(false);
		        		btnInitPhoneticOnlyDB.setEnabled(false);
    					btnInitPhoneticHsOnlyDB.setEnabled(false);
						btnInitPreloadDB.setEnabled(false);
						btnStoreDevice.setEnabled(false);
						btnStoreSdcard.setEnabled(false);

						String dbtarget = mLIMEPref.getParameterString("dbtarget");
						if(dbtarget.equals("device")){
							btnStoreSdcard.setText("");
						}else if(dbtarget.equals("sdcard")){
							btnStoreDevice.setText("");
						}
						
						mLIMEPref.setParameter(LIME.DOWNLOAD_START, true);
						Toast.makeText(v.getContext(), getText(R.string.l3_initial_download_database), Toast.LENGTH_SHORT).show();
						DBSrv.downloadPreloadedDatabase();

						// Reset for SearchSrv
						mLIMEPref.setParameter(LIME.SEARCHSRV_RESET_CACHE,false);
						mLIMEPref.setParameter("db_finish", false);
						
					} catch (RemoteException e) {
						mLIMEPref.setParameter(LIME.DOWNLOAD_START, false);
						e.printStackTrace();
					}
		        }else{
		        	Toast.makeText(v.getContext(), getText(R.string.l3_tab_initial_error), Toast.LENGTH_SHORT).show();
				}
			}
		});
		
		btnInitPhoneticOnlyDB.setOnClickListener(new OnClickListener() {
			public void onClick(View v) {

		        if(connManager.getActiveNetworkInfo() != null && connManager.getActiveNetworkInfo().isConnected()){
		        	try {
    					//DBSrv.closeDatabse(); done in DBSrv already
		        		btnInitEmptyDB.setEnabled(false);
		        		btnInitPhoneticOnlyDB.setEnabled(false);
		        		btnInitPhoneticHsOnlyDB.setEnabled(false);
						btnInitPreloadDB.setEnabled(false);
						btnStoreDevice.setEnabled(false);
						btnStoreSdcard.setEnabled(false);

						String dbtarget = mLIMEPref.getParameterString("dbtarget");
						if(dbtarget.equals("device")){
							btnStoreSdcard.setText("");
						}else if(dbtarget.equals("sdcard")){
							btnStoreDevice.setText("");
						}
						
						mLIMEPref.setParameter(LIME.DOWNLOAD_START, true);
						Toast.makeText(v.getContext(), getText(R.string.l3_initial_download_database), Toast.LENGTH_SHORT).show();
						DBSrv.downloadPhoneticOnlyDatabase();

						// Reset for SearchSrv
						mLIMEPref.setParameter(LIME.SEARCHSRV_RESET_CACHE,false);
						mLIMEPref.setParameter("db_finish", false);
						
					} catch (RemoteException e) {
						mLIMEPref.setParameter(LIME.DOWNLOAD_START, false);
						e.printStackTrace();
					}
		        }else{
		        	Toast.makeText(v.getContext(), getText(R.string.l3_tab_initial_error), Toast.LENGTH_SHORT).show();
				}
			}
		});
		

		btnInitPhoneticHsOnlyDB.setOnClickListener(new OnClickListener() {
			public void onClick(View v) {

		        if(connManager.getActiveNetworkInfo() != null && connManager.getActiveNetworkInfo().isConnected()){
		        	try {
    					//DBSrv.closeDatabse(); done in DBSrv already
		        		btnInitEmptyDB.setEnabled(false);
		        		btnInitPhoneticOnlyDB.setEnabled(false);
		        		btnInitPhoneticHsOnlyDB.setEnabled(false);
						btnInitPreloadDB.setEnabled(false);
						btnStoreDevice.setEnabled(false);
						btnStoreSdcard.setEnabled(false);

						String dbtarget = mLIMEPref.getParameterString("dbtarget");
						if(dbtarget.equals("device")){
							btnStoreSdcard.setText("");
						}else if(dbtarget.equals("sdcard")){
							btnStoreDevice.setText("");
						}
						
						mLIMEPref.setParameter(LIME.DOWNLOAD_START, true);
						Toast.makeText(v.getContext(), getText(R.string.l3_initial_download_database), Toast.LENGTH_SHORT).show();
						DBSrv.downloadPhoneticHsOnlyDatabase();

						// Reset for SearchSrv
						mLIMEPref.setParameter(LIME.SEARCHSRV_RESET_CACHE,false);
						mLIMEPref.setParameter("db_finish", false);
						
					} catch (RemoteException e) {
						mLIMEPref.setParameter(LIME.DOWNLOAD_START, false);
						e.printStackTrace();
					}
		        }else{
		        	Toast.makeText(v.getContext(), getText(R.string.l3_tab_initial_error), Toast.LENGTH_SHORT).show();
				}
			}
		});
		
		btnInitEmptyDB.setOnClickListener(new OnClickListener() {
			public void onClick(View v) {

		        if(connManager.getActiveNetworkInfo() != null && connManager.getActiveNetworkInfo().isConnected()){
		        	try {
    					//DBSrv.closeDatabse(); done in DBSrv already
		        		btnInitEmptyDB.setEnabled(false);
		        		btnInitPhoneticOnlyDB.setEnabled(false);
		        		btnInitPhoneticHsOnlyDB.setEnabled(false);
						btnInitPreloadDB.setEnabled(false);
						btnStoreDevice.setEnabled(false);
						btnStoreSdcard.setEnabled(false);
						

						String dbtarget = mLIMEPref.getParameterString("dbtarget");
						if(dbtarget.equals("device")){
							btnStoreSdcard.setText("");
						}else if(dbtarget.equals("sdcard")){
							btnStoreDevice.setText("");
						}
						mLIMEPref.setParameter(LIME.DOWNLOAD_START, true);
						Toast.makeText(v.getContext(), getText(R.string.l3_initial_download_database), Toast.LENGTH_SHORT).show();
						DBSrv.downloadEmptyDatabase();

						// Reset for SearchSrv
						mLIMEPref.setParameter(LIME.SEARCHSRV_RESET_CACHE,false);
						mLIMEPref.setParameter("db_finish", false);
						
					} catch (RemoteException e) {
						mLIMEPref.setParameter(LIME.DOWNLOAD_START, false);
						e.printStackTrace();
					}
		        }else{
		        	Toast.makeText(v.getContext(), getText(R.string.l3_tab_initial_error), Toast.LENGTH_SHORT).show();
				}
			}
		});
		
		btnBackupDB.setOnClickListener(new OnClickListener() {
			public void onClick(View v) {
		    			
					File srcFile = new File(LIME.DATABASE_DECOMPRESS_FOLDER + File.separator + LIME.DATABASE_NAME);
					File srcFile2 = new File(LIME.DATABASE_DECOMPRESS_FOLDER_SDCARD + File.separator + LIME.DATABASE_NAME);
					if((srcFile2.exists() && srcFile2.length() > 1024) || (srcFile.exists() && srcFile.length() > 1024)){
						AlertDialog.Builder builder = new AlertDialog.Builder(v.getContext());
	     				builder.setMessage(getText(R.string.l3_initial_backup_confirm));
	     				builder.setCancelable(false);
	     				builder.setPositiveButton("Yes", new DialogInterface.OnClickListener() {
	     					public void onClick(DialogInterface dialog, int id) {
			    					backupDatabase();
			    	        	}
			    	     });
			    	    builder.setNegativeButton("No", new DialogInterface.OnClickListener() {
			    	    	public void onClick(DialogInterface dialog, int id) {
			    	        	}
			    	     }); 
	        
						AlertDialog alert = builder.create();
									alert.show();
					}else{
						Toast.makeText(v.getContext(), getText(R.string.l3_initial_backup_error), Toast.LENGTH_SHORT).show();
					}
			}
		});
		
		btnRestoreDB.setOnClickListener(new OnClickListener() {
			public void onClick(View v) {
					File srcFile = new File(LIME.IM_LOAD_LIME_ROOT_DIRECTORY + File.separator + LIME.DATABASE_BACKUP_NAME);
					File srcFile2 = new File(LIME.DATABASE_DECOMPRESS_FOLDER_SDCARD + File.separator + LIME.DATABASE_NAME);
					if((srcFile2.exists() && srcFile2.length() > 1024) || (srcFile.exists() && srcFile.length() > 1024)){
						
						AlertDialog.Builder builder = new AlertDialog.Builder(v.getContext());
	     				builder.setMessage(getText(R.string.l3_initial_restore_confirm));
	     				builder.setCancelable(false);
	     				builder.setPositiveButton("Yes", new DialogInterface.OnClickListener() {
	     					public void onClick(DialogInterface dialog, int id) {
			    					
			    						String dbtarget = mLIMEPref.getParameterString("dbtarget");
			    						if(dbtarget.equals("device")){
			    							btnStoreSdcard.setText("");
			    						}else if(dbtarget.equals("sdcard")){
			    							btnStoreDevice.setText("");
			    						}
		    							btnStoreSdcard.setEnabled(false);
		    							btnStoreDevice.setEnabled(false);
		    							
				    					restoreDatabase();
			    	        	}
			    	     });
			    	    builder.setNegativeButton("No", new DialogInterface.OnClickListener() {
			    	    	public void onClick(DialogInterface dialog, int id) {
			    	        	}
			    	     }); 
	        
						AlertDialog alert = builder.create();
									alert.show();
							
						// Reset for SearchSrv
						mLIMEPref.setParameter(LIME.SEARCHSRV_RESET_CACHE,false);
					}else{
						Toast.makeText(v.getContext(), getText(R.string.l3_initial_restore_error), Toast.LENGTH_SHORT).show();
					}
			}
		});
		
		btnCloudBackupDB.setOnClickListener(new OnClickListener() {
			public void onClick(View v) {
		    			
				if(connManager.getActiveNetworkInfo() != null && connManager.getActiveNetworkInfo().isConnected()){
		        	
		        
					File srcFile = new File(LIME.DATABASE_DECOMPRESS_FOLDER + File.separator + LIME.DATABASE_NAME);
					File srcFile2 = new File(LIME.DATABASE_DECOMPRESS_FOLDER_SDCARD + File.separator + LIME.DATABASE_NAME);
					if((srcFile2.exists() && srcFile2.length() > 1024) || (srcFile.exists() && srcFile.length() > 1024)){
						AlertDialog.Builder builder = new AlertDialog.Builder(v.getContext());
	     				builder.setMessage(getText(R.string.l3_initial_cloud_backup_confirm));
	     				builder.setCancelable(false);
	     				builder.setPositiveButton("Yes", new DialogInterface.OnClickListener() {
	     					public void onClick(DialogInterface dialog, int id) {
		     						//TODO: String dbtarget = mLIMEPref.getParameterString("dbtarget");
									backupCloudDatabase();
			    	        	}
			    	     });
			    	    builder.setNegativeButton("No", new DialogInterface.OnClickListener() {
			    	    	public void onClick(DialogInterface dialog, int id) {
			    	        	}
			    	     }); 
	        
						AlertDialog alert = builder.create();
									alert.show();
					}else{
						Toast.makeText(v.getContext(), getText(R.string.l3_initial_cloud_backup_error), Toast.LENGTH_SHORT).show();
					}
				}else{
		        	Toast.makeText(v.getContext(), getText(R.string.l3_tab_initial_error), Toast.LENGTH_SHORT).show();
				}
			}
		});
		
		btnCloudRestoreDB.setOnClickListener(new OnClickListener() {
			public void onClick(View v) {
						
				if(connManager.getActiveNetworkInfo() != null && connManager.getActiveNetworkInfo().isConnected()){
			        
					AlertDialog.Builder builder = new AlertDialog.Builder(v.getContext());
	     			builder.setMessage(getText(R.string.l3_initial_cloud_restore_confirm));
	     			builder.setCancelable(false);
	     			builder.setPositiveButton("Yes", new DialogInterface.OnClickListener() {
	     				public void onClick(DialogInterface dialog, int id) {
	     						restoreCloudDatabase();
								btnStoreSdcard.setEnabled(false);
								btnStoreDevice.setEnabled(false);
			    	        }
			    	});
			    	builder.setNegativeButton("No", new DialogInterface.OnClickListener() {
			    		public void onClick(DialogInterface dialog, int id) {
			    	    	}
			    	}); 
	        
					AlertDialog alert = builder.create();
								alert.show();
							
					// Reset for SearchSrv
					mLIMEPref.setParameter(LIME.SEARCHSRV_RESET_CACHE,false);

				}else{
		        	Toast.makeText(v.getContext(), getText(R.string.l3_tab_initial_error), Toast.LENGTH_SHORT).show();
				}
			}
		});
		
		btnStoreDevice.setOnClickListener(new OnClickListener() {
			public void onClick(View v) {
				AlertDialog.Builder builder = new AlertDialog.Builder(v.getContext());
 				builder.setMessage(getText(R.string.l3_initial_btn_store_to_device));
 				builder.setCancelable(false);
 				builder.setPositiveButton("Yes", new DialogInterface.OnClickListener() {
 					public void onClick(DialogInterface dialog, int id) {
	 						mLIMEPref.setParameter("dbtarget","device");
	 						//btnStoreDevice.setEnabled(false);
	 						//btnStoreSdcard.setEnabled(true);
	 						initialButton();
	    	        	}
	    	     });
	    	    builder.setNegativeButton("No", new DialogInterface.OnClickListener() {
	    	    	public void onClick(DialogInterface dialog, int id) {
	    	        	}
	    	     }); 
    
				AlertDialog alert = builder.create();
							alert.show();
				
			} 
		});
		
		btnStoreSdcard.setOnClickListener(new OnClickListener() {
			public void onClick(View v) {
				
				AlertDialog.Builder builder = new AlertDialog.Builder(v.getContext());
 				builder.setMessage(getText(R.string.l3_initial_btn_store_to_sdcard));
 				builder.setCancelable(false);
 				builder.setPositiveButton("Yes", new DialogInterface.OnClickListener() {
 					public void onClick(DialogInterface dialog, int id) {
	 						mLIMEPref.setParameter("dbtarget","sdcard");
	 						//btnStoreDevice.setEnabled(true);
	 						//btnStoreSdcard.setEnabled(false);
	 						initialButton();				
	    	        	}
	    	     });
	    	    builder.setNegativeButton("No", new DialogInterface.OnClickListener() {
	    	    	public void onClick(DialogInterface dialog, int id) {
	    	        	}
	    	     }); 
    
				AlertDialog alert = builder.create();
							alert.show();
							
			}
		});
		


		// Reset Cloud Backup Status
		mLIMEPref.setParameter("cloud_in_process",new Boolean(false));

	}
	
	/* (non-Javadoc)
	 * @see android.app.Activity#onStart()
	 */
	@Override
	protected void onStart() {
		super.onStart();
		initialButton();
	}
	

	/* (non-Javadoc)
	 * @see android.app.Activity#onStart()
	 */
	@Override
	protected void onResume() {
		super.onStart();
		initialButton();
	}

	public void resetLabelInfo(String imtype){
		mLIMEPref.setParameter(imtype+LIME.IM_MAPPING_FILENAME,"");
		mLIMEPref.setParameter(imtype+LIME.IM_MAPPING_VERSION,"");
		mLIMEPref.setParameter(imtype+LIME.IM_MAPPING_TOTAL,0);
		mLIMEPref.setParameter(imtype+LIME.IM_MAPPING_DATE,"");
	}
	
	
	private void initialButton(){

		
		// Check if button 
		if(btnResetDB == null){
			btnResetDB = (Button) findViewById(R.id.btnResetDB);
			btnInitPreloadDB = (Button) findViewById(R.id.btnInitPreloadDB);	
			btnInitPhoneticOnlyDB = (Button) findViewById(R.id.btnInitPhoneticOnlyDB);
			btnInitPhoneticHsOnlyDB = (Button) findViewById(R.id.btnInitPhoneticHsOnlyDB);
			btnInitEmptyDB = (Button) findViewById(R.id.btnInitEmptyDB);	
			btnBackupDB = (Button) findViewById(R.id.btnBackupDB);
			btnRestoreDB = (Button) findViewById(R.id.btnRestoreDB);	
			btnCloudBackupDB = (Button) findViewById(R.id.btnCloudBackupDB);
			btnCloudRestoreDB = (Button) findViewById(R.id.btnCloudRestoreDB);
			btnStoreDevice = (Button) findViewById(R.id.btnStoreDevice);
			btnStoreSdcard = (Button) findViewById(R.id.btnStoreSdcard);
		}
		
		String dbtarget = mLIMEPref.getParameterString("dbtarget");
		if(dbtarget.equals("")){
			dbtarget = "device";
			mLIMEPref.setParameter("dbtarget","device");
		}

		File checkSdFile = new File(LIME.DATABASE_DECOMPRESS_FOLDER_SDCARD + File.separator + LIME.DATABASE_NAME);
		File checkDbFile = new File(LIME.DATABASE_DECOMPRESS_FOLDER + File.separator + LIME.DATABASE_NAME);
		if(	( (!checkSdFile.exists() && dbtarget.equals("sdcard") )  
				|| ( !checkDbFile.exists()) && dbtarget.equals("device")) 
				&& !mLIMEPref.getParameterBoolean(LIME.DOWNLOAD_START)){
			
			btnInitPreloadDB.setEnabled(true);
			btnInitPhoneticOnlyDB.setEnabled(true);
			btnInitPhoneticHsOnlyDB.setEnabled(true);
			btnInitEmptyDB.setEnabled(true);
			btnResetDB.setEnabled(false);
			
			if(!mLIMEPref.getParameterBoolean("cloud_in_process")){
				Toast.makeText(this, getText(R.string.l3_tab_initial_message), Toast.LENGTH_SHORT).show();
			}
			
			if(dbtarget.equals("device")){
				btnStoreDevice.setEnabled(false);
				btnStoreSdcard.setEnabled(true);
			}else if(dbtarget.equals("sdcard")){
				btnStoreDevice.setEnabled(true);
				btnStoreSdcard.setEnabled(false);
			}
				
		}else{
			if(dbtarget.equals("device")){
				btnStoreSdcard.setText("");
			}else if(dbtarget.equals("sdcard")){
				btnStoreDevice.setText("");
			}
			btnStoreDevice.setEnabled(false);
			btnStoreSdcard.setEnabled(false);
			btnInitPreloadDB.setEnabled(false);
			btnInitPhoneticOnlyDB.setEnabled(false);
			btnInitPhoneticHsOnlyDB.setEnabled(false);
			btnInitEmptyDB.setEnabled(false);
			btnResetDB.setEnabled(true);
		}
	}

	public void backupDatabase(){
		 BackupRestoreTask task = new BackupRestoreTask(this,this.getApplicationContext(), DBSrv, null, BackupRestoreTask.BACKUP);
		 task.execute("");
	}

	public void restoreDatabase(){
		 BackupRestoreTask task = new BackupRestoreTask(this,this.getApplicationContext(), DBSrv, null, BackupRestoreTask.RESTORE);
		 task.execute("");
	}
	
	public void backupCloudDatabase() {
		File limedir = new File(LIME.IM_LOAD_LIME_ROOT_DIRECTORY + File.separator);
		if(!limedir.exists()){
			limedir.mkdirs();
		}
		
		File tempFile = new File(LIME.IM_LOAD_LIME_ROOT_DIRECTORY + File.separator + LIME.DATABASE_CLOUD_TEMP);
		tempFile.deleteOnExit();
		BackupRestoreTask task = new BackupRestoreTask(this,this.getApplicationContext(), DBSrv, tempFile, BackupRestoreTask.CLOUDBACKUP);
							  task.execute("");
	}

	public void restoreCloudDatabase() {
		File limedir = new File(LIME.IM_LOAD_LIME_ROOT_DIRECTORY + File.separator);
		if(!limedir.exists()){
			limedir.mkdirs();
		}
		
		File tempFile = new File(LIME.IM_LOAD_LIME_ROOT_DIRECTORY + File.separator + LIME.DATABASE_CLOUD_TEMP);
		tempFile.deleteOnExit();
		BackupRestoreTask task = new BackupRestoreTask(this,this.getApplicationContext(), DBSrv, tempFile, BackupRestoreTask.CLOUDRESTORE);
						      task.execute("");
	    initialButton();
	}
	
	public class BackupRestoreTask extends AsyncTask<String,Integer,Integer> {


		private DBCloudServer dbsrv = null;
		private ProgressDialog pd;
		private Context ctx;
		private LIMEInitial activity;
		private File tempfile;
		private int type;
		final public static int CLOUDBACKUP = 1;
		final public static int CLOUDRESTORE = 2;
		final public static int BACKUP = 3;
		final public static int RESTORE = 4;
		
		BackupRestoreTask(LIMEInitial act, Context srcctx, DBCloudServer db, File file, int settype){
			dbsrv = db;
			activity = act;
			ctx = srcctx;
			tempfile = file;
			type = settype;
		}
		
		protected void onPreExecute(){

			 if(type == CLOUDBACKUP){
				 pd = new ProgressDialog(activity);
				 pd.setTitle(ctx.getText(R.string.l3_initial_cloud_backup_database));
				 pd.setMessage(ctx.getText(R.string.l3_initial_cloud_backup_start));
				 pd.setCancelable(true);
				 pd.setOnCancelListener(new OnCancelListener(){
					@Override
					public void onCancel(DialogInterface dialog) {
						mLIMEPref.setParameter("cloud_in_process",new Boolean(false));
					}
				 });
				 pd.setIndeterminate(false);
				 pd.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
				 pd.setCanceledOnTouchOutside(false);
				 pd.setMax(100);
				 pd.show();
			}else if(type == CLOUDRESTORE){
				 pd = new ProgressDialog(activity);
				 pd.setTitle(ctx.getText(R.string.l3_initial_cloud_restore_database));
				 pd.setMessage(ctx.getText(R.string.l3_initial_cloud_restore_start));
				 pd.setCancelable(true);
				 pd.setOnCancelListener(new OnCancelListener(){
					@Override
					public void onCancel(DialogInterface dialog) {
						mLIMEPref.setParameter("cloud_in_process",new Boolean(false));
					}
				 });
				 pd.setIndeterminate(false);
				 pd.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
				 pd.setCanceledOnTouchOutside(false);
				 pd.setMax(100);
				 pd.show();
			}else if(type == BACKUP){
				pd = ProgressDialog.show(activity, ctx.getText(R.string.l3_initial_backup_database), ctx.getText(R.string.l3_initial_backup_start),true);
			}else if(type == RESTORE){
				pd = ProgressDialog.show(activity, ctx.getText(R.string.l3_initial_restore_database), ctx.getText(R.string.l3_initial_restore_start),true);
			}
			 
			mLIMEPref.setParameter("reload_database", true);
			try {
				dbsrv.closeDatabse();
			} catch (RemoteException e) {
				e.printStackTrace();
			} 

			mLIMEPref.setParameter("cloud_in_process", new Boolean(true));
		}
		protected void onPostExecute(Integer result){
			pd.cancel();
			if(type == CLOUDRESTORE || type == RESTORE){
				activity.initialButton();
				dbsrv.checkPhoneticKeyboardSetting();//Jeremy '12,6,8 check the pheonetic keyboard consistency
			}
		}
		
		@Override
		protected Integer doInBackground(String... arg0) {
			if(type == CLOUDBACKUP){
				File srcFile = null;
				String dbtarget = mLIMEPref.getParameterString("dbtarget");
				if(dbtarget.equals("device")){
					srcFile = new File(LIME.DATABASE_DECOMPRESS_FOLDER + File.separator + LIME.DATABASE_NAME);
				}else{
					srcFile = new File(LIME.DATABASE_DECOMPRESS_FOLDER_SDCARD + File.separator + LIME.DATABASE_NAME);
				}
				pd.setProgress(10);
				dbsrv.compressFile(srcFile, LIME.IM_LOAD_LIME_ROOT_DIRECTORY, LIME.DATABASE_CLOUD_TEMP);
				pd.setProgress(20);
				dbsrv.cloudBackup(activity,  pd, tempfile);
				if(!dbsrv.getStatus()){
					mLIMEPref.setParameter(LIME.DOWNLOAD_START, false);
					mLIMEPref.setParameter("cloud_in_process", new Boolean(false));
					dbsrv.showNotificationMessage("Cannot Backup Database", 0);
				}
			}else if(type == CLOUDRESTORE){
				pd.setProgress(10);
				dbsrv.cloudRestore(activity,  pd, tempfile);
				if(!dbsrv.getStatus()){
					mLIMEPref.setParameter(LIME.DOWNLOAD_START, false);
					mLIMEPref.setParameter("cloud_in_process", new Boolean(false));
					dbsrv.showNotificationMessage("Cannot Restore Database", 0);
				}
			}else if(type == BACKUP){
				try {
					dbsrv.backupDatabase();
				} catch (RemoteException e) {
					e.printStackTrace();
				}
				mLIMEPref.setParameter("cloud_in_process", new Boolean(false));
			}else if(type == RESTORE){
				try {
					dbsrv.restoreDatabase();
				} catch (RemoteException e) {
					e.printStackTrace();
				}
				mLIMEPref.setParameter("cloud_in_process", new Boolean(false));
			}
			boolean inProcess = true;
			do{
				inProcess = mLIMEPref.getParameterBoolean("cloud_in_process");
				try {
					Thread.sleep(1000);
				} catch (InterruptedException e) {
					e.printStackTrace();
				}
			}while(inProcess);
			pd.setProgress(100);
			return 1;
		}
	}
	
	
}