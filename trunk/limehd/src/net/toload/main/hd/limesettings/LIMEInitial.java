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

import com.google.ads.AdRequest;
import com.google.ads.AdSize;
import com.google.ads.AdView;

import net.toload.main.hd.global.LIME;
import net.toload.main.hd.global.LIMEPreferenceManager;
import net.toload.main.hd.handler.CloudBackupServiceRunnable;
import net.toload.main.hd.handler.CloudRestoreServiceRunnable;
import net.toload.main.hd.handler.CloudServierHandler;
import net.toload.main.hd.handler.DropboxDBBackup;
import net.toload.main.hd.handler.DropboxDBRestore;
import net.toload.main.hd.R;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.DialogInterface.OnCancelListener;
import android.content.SharedPreferences.Editor;
import android.content.pm.PackageManager;
import android.net.ConnectivityManager;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.RemoteException;
import android.util.Log;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.Toast;
import com.dropbox.client2.DropboxAPI;
import com.dropbox.client2.android.AndroidAuthSession;
import com.dropbox.client2.android.AuthActivity;
import com.dropbox.client2.session.AccessTokenPair;
import com.dropbox.client2.session.AppKeyPair;
import com.dropbox.client2.session.Session.AccessType;
import com.dropbox.client2.session.TokenPair;

/**
 * 
 * @author Art Hung
 * 
 */
public class LIMEInitial extends Activity {
	private final boolean DEBUG = false;
	private final String TAG = "LIMEInitial";
	// Dropbox API by Jeremy '12,12,22
    // Note that this is a really insecure way to do this, and you shouldn't
    // ship code which contains your key & secret in such an obvious way.
    // Obfuscation is good.
    final static private String APP_KEY = "keuuzhfc6efjf6t";
    final static private String APP_SECRET = "4y8fy4rqk8rofd8";
    // If you'd like to change the access type to the full Dropbox instead of
    // an app folder, change this value.
    final static private AccessType ACCESS_TYPE = AccessType.APP_FOLDER;
    // You don't need to change these, leave them alone.
    final static private String ACCOUNT_PREFS_NAME = "prefs";
    final static private String ACCESS_KEY_NAME = "ACCESS_KEY";
    final static private String ACCESS_SECRET_NAME = "ACCESS_SECRET";   
    DropboxAPI<AndroidAuthSession> mDropboxApi;

    private boolean mDropboxLoggedIn;
    private boolean pendingDropboxBackup = false;
    private boolean pendingDropboxRestore = false;

	

	private DBServer DBSrv = null;
	Button btnInitPreloadDB = null;
	Button btnInitPhoneticHsOnlyDB = null; 
	Button btnInitPhoneticOnlyDB = null;//Jeremy '11,9,10
	Button btnInitEmptyDB = null;
	Button btnResetDB = null;
	Button btnBackupDB = null;
	Button btnRestoreDB = null;
	Button btnCloudBackupDB = null;
	Button btnCloudRestoreDB = null;
	Button btnDropboxBackupDB = null;
	Button btnDropboxRestoreDB = null;
	Button btnStoreDevice = null;
	Button btnStoreSdcard = null;
	Button btnUnlinkDropbox = null;
	private ProgressDialog cloudpd;
	
	boolean hasReset = false;
	LIMEPreferenceManager mLIMEPref;
	ConnectivityManager connManager = null;
	
	Activity activity = null;
	
	private CloudServierHandler cHandler;
	private Thread bTask;
	private Thread rTask;
	
	
	
	@Override
	protected void onPause() {
		super.onPause();
		if(bTask != null)
			cHandler.removeCallbacks(bTask);
		if(rTask != null)
			cHandler.removeCallbacks(rTask);
	}

	/** Called when the activity is first created. */
	@Override
	public void onCreate(Bundle icicle) {
		super.onCreate(icicle);
		if(DEBUG)
			Log.i(TAG, "onCreate()");
		
		
		this.setContentView(R.layout.initial);
		activity = this;
		
		// Startup Service
		//getApplicationContext().bindService(new Intent(IDBService.class.getName()), serConn, Context.BIND_AUTO_CREATE);
		DBSrv = new DBServer(getApplicationContext());
		mLIMEPref = new LIMEPreferenceManager(getApplicationContext());
		connManager = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
		
		//Dropbox initialization  '12,12,23 Jermey
		// We create a new AuthSession so that we can use the Dropbox API.
        AndroidAuthSession session = buildSession();
        mDropboxApi = new DropboxAPI<AndroidAuthSession>(session);
        checkAppKeySetup();
		mDropboxLoggedIn = mDropboxApi.getSession().isLinked();
	    
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
				mLIMEPref.setResetCacheFlag(true);
			
				
				
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
						mLIMEPref.setResetCacheFlag(true);
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
						mLIMEPref.setResetCacheFlag(true);
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
						mLIMEPref.setResetCacheFlag(true);
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
						mLIMEPref.setResetCacheFlag(true);
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
						mLIMEPref.setResetCacheFlag(true);
						
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
		   						
									backupDatabaseGooldDrive();
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
	     						restoreDatabaseGoogleDrive();
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
					mLIMEPref.setResetCacheFlag(true);

				}else{
		        	Toast.makeText(v.getContext(), getText(R.string.l3_tab_initial_error), Toast.LENGTH_SHORT).show();
				}
			}
		});
		
		btnDropboxBackupDB.setOnClickListener(new OnClickListener() {
			public void onClick(View v) {
				
				if(DEBUG)
					Log.i(TAG, "Dropbox link, mDropboxLoggedIn = " +mDropboxLoggedIn);
				
				if(connManager.getActiveNetworkInfo() != null && connManager.getActiveNetworkInfo().isConnected()){
		        	
			        
					File srcFile = new File(LIME.DATABASE_DECOMPRESS_FOLDER + File.separator + LIME.DATABASE_NAME);
					File srcFile2 = new File(LIME.DATABASE_DECOMPRESS_FOLDER_SDCARD + File.separator + LIME.DATABASE_NAME);
					if((srcFile2.exists() && srcFile2.length() > 1024) || (srcFile.exists() && srcFile.length() > 1024)){
						AlertDialog.Builder builder = new AlertDialog.Builder(v.getContext());
	     				builder.setMessage(getText(R.string.l3_initial_dropbox_backup_confirm));
	     				builder.setCancelable(false);
	     				builder.setPositiveButton("Yes", new DialogInterface.OnClickListener() {
	     					public void onClick(DialogInterface dialog, int id) {

	     						if (!mDropboxLoggedIn) {
	     							// Start the remote authentication
	     							mDropboxApi.getSession().startAuthentication(LIMEInitial.this);
	     							pendingDropboxBackup = true; //do database backup after on Resume();
	     						}else{
	     							//
	     							backupDatabaseDropbox();
	     						}
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
		
		btnDropboxRestoreDB.setOnClickListener(new OnClickListener() {
			public void onClick(View v) {
				if(DEBUG)
					Log.i(TAG, "Dropbox link, mDropboxLoggedIn = " +mDropboxLoggedIn);
				
				if(connManager.getActiveNetworkInfo() != null && connManager.getActiveNetworkInfo().isConnected()){
			        
					AlertDialog.Builder builder = new AlertDialog.Builder(v.getContext());
					builder.setMessage(getText(R.string.l3_initial_dropbox_restore_confirm));
					builder.setCancelable(false);
					builder.setPositiveButton("Yes", new DialogInterface.OnClickListener() {
						public void onClick(DialogInterface dialog, int id) {

							if (!mDropboxLoggedIn) {
								// Start the remote authentication
								mDropboxApi.getSession().startAuthentication(LIMEInitial.this);
								pendingDropboxRestore = true; //do database backup after on Resume();
							}else{
								//
								restoreDatabaseDropbox();
							}



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
					mLIMEPref.setResetCacheFlag(true);

				}else{
		        	Toast.makeText(v.getContext(), getText(R.string.l3_tab_initial_error), Toast.LENGTH_SHORT).show();
				}
			}
		});
			
		
		btnUnlinkDropbox.setOnClickListener(new OnClickListener() {
			public void onClick(View v) {
				
				if (mDropboxLoggedIn) {
                    logOut();
                } else {
                    // Start the remote authentication
                    mDropboxApi.getSession().startAuthentication(LIMEInitial.this);
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
		


		AdView adView = new AdView(this, AdSize.SMART_BANNER, LIME.publisher);
        LinearLayout layout = (LinearLayout)findViewById(R.id.ad_area);
        layout.addView(adView);
        AdRequest adRequest = new AdRequest();
        adView.loadAd(adRequest);

		// Reset Cloud Backup Status
		mLIMEPref.setParameter("cloud_in_process",Boolean.valueOf(false));

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
		super.onResume();
		if(DEBUG)
			Log.i(TAG, "onResume()");
		initialButton();
		//Dropbox onResume
		 AndroidAuthSession session = mDropboxApi.getSession();

	        // The next part must be inserted in the onResume() method of the
	        // activity from which session.startAuthentication() was called, so
	        // that Dropbox authentication completes properly.
	        if (session.authenticationSuccessful()) {
	            try {
	                // Mandatory call to complete the auth
	                session.finishAuthentication();

	                // Store it locally in our app for later use
	                TokenPair tokens = session.getAccessTokenPair();
	                storeKeys(tokens.key, tokens.secret);
	                mDropboxLoggedIn = true;
	                if(pendingDropboxBackup){
	                	backupDatabaseDropbox();
	                }
	                if(pendingDropboxRestore){
	                	restoreDatabaseDropbox();
	                }
	            } catch (IllegalStateException e) {
	                Log.i(TAG, "Error authenticating", e);
	            }
	        }
	        pendingDropboxBackup = false;
	        pendingDropboxRestore = false;
	}

	public void resetLabelInfo(String imtype){
		mLIMEPref.setParameter(imtype+LIME.IM_MAPPING_FILENAME,"");
		mLIMEPref.setParameter(imtype+LIME.IM_MAPPING_VERSION,"");
		mLIMEPref.setParameter(imtype+LIME.IM_MAPPING_TOTAL,0);
		mLIMEPref.setParameter(imtype+LIME.IM_MAPPING_DATE,"");
	}
	
	
	public void initialButton(){

		
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
			btnDropboxBackupDB = (Button) findViewById(R.id.btnDropboxBackupDB);
			btnDropboxRestoreDB = (Button) findViewById(R.id.btnDropboxRestoreDB);
			btnUnlinkDropbox = (Button) findViewById(R.id.btn_reserved2);
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
		 BackupRestoreTask task = new BackupRestoreTask(this,this.getApplicationContext(), DBSrv, BackupRestoreTask.BACKUP);
		 task.execute("");
	}

	public void restoreDatabase(){
		 BackupRestoreTask task = new BackupRestoreTask(this,this.getApplicationContext(), DBSrv, BackupRestoreTask.RESTORE);
		 task.execute("");
	}
	
	private void backupDatabaseDropbox(){
		BackupRestoreTask task = new BackupRestoreTask(LIMEInitial.this,
    			LIMEInitial.this.getApplicationContext(), 
    			DBSrv, BackupRestoreTask.DROPBOXBACKUP);
		task.execute("");
		

	}
	public void backupDatabaseGooldDrive() {
		
	
		//tempFile.deleteOnExit();
	
		BackupRestoreTask task = new BackupRestoreTask(this,this.getApplicationContext()
				, DBSrv, BackupRestoreTask.CLOUDBACKUP);
		task.execute("");
		
		
		/*  Jeremy '12,12,23 Moved to postExcute of bakcupRestoreTask.  Zip db first (backupdatabase) before backup to google drive now.
		cHandler = new CloudServierHandler(this);
		bTask = new Thread(new CloudBackupServiceRunnable(cHandler, this, tempFile));
		bTask.start();
		*/
		//showProgressDialog(true);
		/*BackupRestoreTask task = new BackupRestoreTask(this,this.getApplicationContext(), DBSrv, tempFile, BackupRestoreTask.CLOUDBACKUP);
							  task.execute("");*/
	}
	public void restoreDatabaseDropbox(){
		try {
			DBSrv.closeDatabse();
		} catch (RemoteException e) {
			if(DEBUG)
				Log.i(TAG, "restoreDatabaseDropbox() reset database failed");
			e.printStackTrace();
		}
		File limedir = new File(LIME.IM_LOAD_LIME_ROOT_DIRECTORY + File.separator);
		if(!limedir.exists()){
			limedir.mkdirs();
		}
		File tempFile = new File(LIME.IM_LOAD_LIME_ROOT_DIRECTORY + File.separator + LIME.DATABASE_CLOUD_TEMP);
		tempFile.deleteOnExit();
		
		DropboxDBRestore download = 
				new DropboxDBRestore(this, LIMEInitial.this, mDropboxApi,LIME.DATABASE_BACKUP_NAME , tempFile);
		download.execute();
		
		//initialButton();
		
	}

	public void restoreDatabaseGoogleDrive() {
		try {
			DBSrv.closeDatabse();
		} catch (RemoteException e) {
			if(DEBUG)
				Log.i(TAG, "restoreDatabaseDropbox() reset database failed");
			e.printStackTrace();
		}
		
		File limedir = new File(LIME.IM_LOAD_LIME_ROOT_DIRECTORY + File.separator);
		if(!limedir.exists()){
			limedir.mkdirs();
		}
		
		File tempFile = new File(LIME.IM_LOAD_LIME_ROOT_DIRECTORY + File.separator + LIME.DATABASE_CLOUD_TEMP);
		tempFile.deleteOnExit();
		
		cHandler = new CloudServierHandler(this);
		bTask = new Thread(new CloudRestoreServiceRunnable(cHandler, this, tempFile));
		bTask.start();

		showProgressDialog(false);
		/*BackupRestoreTask task = new BackupRestoreTask(this,this.getApplicationContext(), DBSrv, tempFile, BackupRestoreTask.CLOUDRESTORE);
						      task.execute("");*/
	    initialButton();
	}
	
	public void showProgressDialog(boolean isBackup){
		if(isBackup){
			 cloudpd = new ProgressDialog(activity);
			 cloudpd.setTitle(this.getText(R.string.l3_initial_cloud_backup_database));
			 cloudpd.setMessage(this.getText(R.string.l3_initial_cloud_backup_start));
			 cloudpd.setCancelable(false);
			 cloudpd.setOnCancelListener(new OnCancelListener(){
				@Override
				public void onCancel(DialogInterface dialog) {
					mLIMEPref.setParameter("cloud_in_process",Boolean.valueOf(false));
				}
			 });
			 cloudpd.setIndeterminate(false);
			 cloudpd.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
			 cloudpd.setCanceledOnTouchOutside(false);
			 cloudpd.setMax(100);
			 cloudpd.show();
		}else{
			cloudpd = new ProgressDialog(activity);
			cloudpd.setTitle(this.getText(R.string.l3_initial_cloud_restore_database));
			 cloudpd.setMessage(this.getText(R.string.l3_initial_cloud_restore_start));
			 cloudpd.setCancelable(true);
			 cloudpd.setOnCancelListener(new OnCancelListener(){
				@Override
				public void onCancel(DialogInterface dialog) {
					mLIMEPref.setParameter("cloud_in_process",Boolean.valueOf(false));
				}
			 });
			 cloudpd.setIndeterminate(false);
			 cloudpd.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
			 cloudpd.setCanceledOnTouchOutside(false);
			 cloudpd.setMax(100);
			 cloudpd.show();
		}
	}
	
	public void updateProgressDialog(int progress){
		if(cloudpd!=null && cloudpd.isShowing()){
			cloudpd.setProgress(progress);
		}
	}
	
	public void closeProgressDialog(){
		if(cloudpd != null && cloudpd.isShowing()){
			cloudpd.dismiss();
		}
	}
	
	
	 private void logOut() {
		 if(DEBUG)
			 Log.i(TAG, "logout Dropbox session, mDropboxLoggedIn = " + mDropboxLoggedIn);
	        // Remove credentials from the session
	        mDropboxApi.getSession().unlink();

	        // Clear our stored keys
	        clearKeys();
	        // Change UI state to display logged out version
	        mDropboxLoggedIn = false;
	    }

	   

    

    private void checkAppKeySetup() {
        // Check to make sure that we have a valid app key
        if (APP_KEY.startsWith("CHANGE") ||
                APP_SECRET.startsWith("CHANGE")) {
            showToast("You must apply for an app key and secret from developers.dropbox.com, and add them to the DBRoulette ap before trying it.");
            finish();
            return;
        }

        // Check if the app has set up its manifest properly.
        Intent testIntent = new Intent(Intent.ACTION_VIEW);
        String scheme = "db-" + APP_KEY;
        String uri = scheme + "://" + AuthActivity.AUTH_VERSION + "/test";
        testIntent.setData(Uri.parse(uri));
        PackageManager pm = getPackageManager();
        if (0 == pm.queryIntentActivities(testIntent, 0).size()) {
            showToast("URL scheme in your app's " +
                    "manifest is not set up correctly. You should have a " +
                    "com.dropbox.client2.android.AuthActivity with the " +
                    "scheme: " + scheme);
            finish();
        }
    }

    private void showToast(String msg) {
        Toast error = Toast.makeText(this, msg, Toast.LENGTH_LONG);
        error.show();
    }

    /**
     * Shows keeping the access keys returned from Trusted Authenticator in a local
     * store, rather than storing user name & password, and re-authenticating each
     * time (which is not to be done, ever).
     *
     * @return Array of [access_key, access_secret], or null if none stored
     */
    private String[] getKeys() {
        SharedPreferences prefs = getSharedPreferences(ACCOUNT_PREFS_NAME, 0);
        String key = prefs.getString(ACCESS_KEY_NAME, null);
        String secret = prefs.getString(ACCESS_SECRET_NAME, null);
        if (key != null && secret != null) {
        	String[] ret = new String[2];
        	ret[0] = key;
        	ret[1] = secret;
        	return ret;
        } else {
        	return null;
        }
    }

    /**
     * Shows keeping the access keys returned from Trusted Authenticator in a local
     * store, rather than storing user name & password, and re-authenticating each
     * time (which is not to be done, ever).
     */
    private void storeKeys(String key, String secret) {
        // Save the access key for later
        SharedPreferences prefs = getSharedPreferences(ACCOUNT_PREFS_NAME, 0);
        Editor edit = prefs.edit();
        edit.putString(ACCESS_KEY_NAME, key);
        edit.putString(ACCESS_SECRET_NAME, secret);
        edit.commit();
    }

    private void clearKeys() {
        SharedPreferences prefs = getSharedPreferences(ACCOUNT_PREFS_NAME, 0);
        Editor edit = prefs.edit();
        edit.clear();
        edit.commit();
    }

    private AndroidAuthSession buildSession() {
    	if(DEBUG)
    		Log.i(TAG, "buildSession()");
        
        AppKeyPair appKeyPair = new AppKeyPair(APP_KEY, APP_SECRET);
        AndroidAuthSession session;

        String[] stored = getKeys();
        if (stored != null) {
        	if(DEBUG)
        		Log.i(TAG, "Got stored key, key = " + stored[0] + ", secret = " + stored[1]);
            AccessTokenPair accessToken = new AccessTokenPair(stored[0], stored[1]);
            session = new AndroidAuthSession(appKeyPair, ACCESS_TYPE, accessToken);
        } else {
        	if(DEBUG)
        		Log.i(TAG, "no stored key. start a new session.");
        
            session = new AndroidAuthSession(appKeyPair, ACCESS_TYPE);
        }

        return session;
    }
	
	
	public class BackupRestoreTask extends AsyncTask<String,Integer,Integer> {


		private ProgressDialog pd;
		private DBServer dbsrv = null;
		private Context ctx;
		private LIMEInitial activity;
		//private File tempfile;
		private int type;
		final public static int CLOUDBACKUP = 1;
		final public static int CLOUDRESTORE = 2;
		final public static int BACKUP = 3;
		final public static int RESTORE = 4;
		final public static int DROPBOXBACKUP = 5;
		final public static int DROPBOXRESTORE = 6;
		
		
		BackupRestoreTask(LIMEInitial act, Context srcctx, DBServer db, int settype){
			dbsrv = db;
			activity = act;
			ctx = srcctx;
			//tempfile = file;
			type = settype;
		}
		
		protected void onPreExecute(){

			 pd = new ProgressDialog(activity);
	
			
			if(type == BACKUP || type == CLOUDBACKUP || type == DROPBOXBACKUP){
				pd = ProgressDialog.show(activity, ctx.getText(R.string.l3_initial_backup_database), ctx.getText(R.string.l3_initial_backup_start),true);
			}else if(type == RESTORE){
				pd = ProgressDialog.show(activity, ctx.getText(R.string.l3_initial_restore_database), ctx.getText(R.string.l3_initial_restore_start),true);
			}
			 
			//mLIMEPref.setParameter("reload_database", true);
			try {
				dbsrv.closeDatabse();
			} catch (RemoteException e) {
				e.printStackTrace();
			} 

			mLIMEPref.setParameter("cloud_in_process", Boolean.valueOf(false));
		}
		protected void onPostExecute(Integer result){
			pd.cancel();
			if(type == CLOUDBACKUP){
				File sourceFile = new File(LIME.IM_LOAD_LIME_ROOT_DIRECTORY + File.separator + LIME.DATABASE_BACKUP_NAME);
				cHandler = new CloudServierHandler(LIMEInitial.this);
				bTask = new Thread(new CloudBackupServiceRunnable(cHandler, LIMEInitial.this, sourceFile));
				bTask.start();
				showProgressDialog(true);
			}else if(type == DROPBOXBACKUP){
				// Jeremy  '12,12,23 do dropbox backup now.
				File sourceFile = new File(LIME.IM_LOAD_LIME_ROOT_DIRECTORY + File.separator + LIME.DATABASE_BACKUP_NAME);		
		      	DropboxDBBackup upload = new DropboxDBBackup(LIMEInitial.this, mDropboxApi, "", sourceFile);
		    	upload.execute();
			}else if(type == CLOUDRESTORE || type == RESTORE){
				activity.initialButton();
				dbsrv.checkPhoneticKeyboardSetting();//Jeremy '12,6,8 check the pheonetic keyboard consistency
				mLIMEPref.setResetCacheFlag(true);  //Jeremy '12,7,8 reset cache.
			}
			
		}
		
		@Override
		protected Integer doInBackground(String... arg0) {
			
			if(type == BACKUP || type == CLOUDBACKUP || type == DROPBOXBACKUP){
				try {
					dbsrv.backupDatabase();
				} catch (RemoteException e) {
					e.printStackTrace();
				}
				mLIMEPref.setParameter("cloud_in_process", Boolean.valueOf(false));
			}else if(type == RESTORE){
				try {
					dbsrv.restoreDatabase();
				} catch (RemoteException e) {
					e.printStackTrace();
				}
				mLIMEPref.setParameter("cloud_in_process", Boolean.valueOf(false));
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