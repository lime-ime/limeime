package net.toload.main.hd.limesettings;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.URI;
import java.net.URL;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import net.toload.main.hd.R;
import net.toload.main.hd.global.LIME;

import android.accounts.Account;
import android.accounts.AccountManager;
import android.accounts.AccountManagerCallback;
import android.accounts.AccountManagerFuture;
import android.app.Activity;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;

import com.google.api.client.googleapis.auth.oauth2.GoogleCredential;
import com.google.api.client.googleapis.extensions.android2.auth.GoogleAccountManager;
import com.google.gdata.client.docs.DocsService;
import com.google.gdata.client.media.ResumableGDataFileUploader;
import com.google.gdata.data.MediaContent;
import com.google.gdata.data.PlainTextConstruct;
import com.google.gdata.data.docs.DocumentListEntry;
import com.google.gdata.data.docs.DocumentListFeed;
import com.google.gdata.data.media.MediaFileSource;
import com.google.gdata.data.media.MediaSource;

public class Deprecated_DBCloudServer extends DBServer {

	private static boolean status; 
	
	private final static boolean DEBUG = false;
	private final static String TAG = "DBCloudServer";
	private static final String AUTH_TOKEN_TYPE = "oauth2:https://docs.google.com/feeds/ https://spreadsheets.google.com/feeds/ https://docs.googleusercontent.com/";
	// private static final int MENU_ACCOUNTS = 0;
	private static final int REQUEST_AUTHENTICATE = 0;

	private static final int MAX_CONCURRENT_UPLOADS = 10;
	private static final int PROGRESS_UPDATE_INTERVAL = 1000;
	private static final int DEFAULT_CHUNK_SIZE = 10000000;

	// final HttpTransport transport = AndroidHttp.newCompatibleTransport();
	// final JsonFactory jsonFactory = new JacksonFactory();

	static final String PREF_ACCOUNT_NAME = "accountName1";
	static final String PREF_AUTH_TOKEN = "authToken";
	static String accountName;

	static GoogleAccountManager accountManager;
	static GoogleCredential credential = new GoogleCredential();

	static final String host = "docs.google.com";

	// static LIMEPreferenceManager mLIMEPref = null;
	static Activity activity = null;
	// static DBServer DBSrv = null;
	static File tempfile = null;
	static boolean isbackup = false;
	static ProgressDialog pd = null;

	public Deprecated_DBCloudServer(Context context) {
		super(context);

	}
	
	public static boolean getStatus(){
		return status;
	}

    boolean ready = false;
    boolean failed = false;
	public void cloudBackup(Activity act, ProgressDialog pd, File temp) {
		if (DEBUG)
			Log.i(TAG, "cloudBackup()");

		Deprecated_DBCloudServer.pd = pd;
		isbackup = true;
		activity = act;
		tempfile = temp;
		credential.setAccessToken(null);
		accountName = mLIMEPref.getParameterString(PREF_ACCOUNT_NAME, null);
		credential.setAccessToken(mLIMEPref.getParameterString(PREF_AUTH_TOKEN,	null));

		accountManager = new GoogleAccountManager(activity);

        if(accountManager.getAccounts().length > 0){
        	mLIMEPref.setParameter( PREF_ACCOUNT_NAME, accountManager.getAccounts()[0].name);	
        	accountName = accountManager.getAccounts()[0].name;
         }
        
        if(mLIMEPref.getParameterString(PREF_AUTH_TOKEN, null) != null){
        	accountManager.invalidateAuthToken(mLIMEPref.getParameterString(PREF_AUTH_TOKEN, null));
        }
        
		Account account = accountManager.getAccountByName(accountName);
		accountManager.getAccountManager().getAuthToken(account,
				AUTH_TOKEN_TYPE, null, activity,
				new AccountManagerCallback<Bundle>() {

					public void run(AccountManagerFuture<Bundle> future) {
						try {
							Bundle bundle = future.getResult();
							
							if (bundle.containsKey(AccountManager.KEY_AUTHTOKEN)) {
								setAuthToken(bundle.getString(AccountManager.KEY_AUTHTOKEN));
								
								
								failed = false;
							}else{
								failed = true;
							}
							ready = true;
						} catch (Exception e) {
							failed = true;
							ready = true;
							mLIMEPref.setParameter("cloud_in_process", new Boolean(false));
						}
					}

				}, null);
		
		while(!ready){
			try {
				Thread.sleep(1000);
			} catch (InterruptedException e) {
				failed = true;
				e.printStackTrace();
			}
		}
		
		if(!failed){
			backupProcess();	
		}else{
			showNotificationMessage("Cannot Backup Database", intentLIMEMenu);
		}
		
	}


	private static void backupProcess() {
		try {

			Thread thread = new Thread(new Runnable() {
				public void run() {
					if(DEBUG)
						Log.i(TAG,"Google cloud backup thread started...");

					try {

						DocsService service = getDocsService();
						service.setOAuth2Credentials(credential);

						String TARGET = "https://docs.google.com/feeds/default/private/full?title=limedatabasebackup.zip";

							URL metafeedUrl = new URL(TARGET);
							DocumentListFeed feed = service.getFeed(
									metafeedUrl, DocumentListFeed.class);
							for (DocumentListEntry entry : feed.getEntries()) {
								service.delete(new URI(entry.getEditLink()
										.getHref()), entry.getEtag());
							}
						pd.setProgress(30);

						// service.setHeader("Content-length",
						// String.valueOf(0));

						FileUploadProgressListener listener = new FileUploadProgressListener();

						// Pool for handling concurrent upload tasks
						ExecutorService executor = Executors
								.newFixedThreadPool(MAX_CONCURRENT_UPLOADS);
						MediaFileSource mediaFile = getMediaFileSource(tempfile
								.getAbsolutePath());

						DocumentListEntry metadata = new DocumentListEntry();
						metadata.setTitle(new PlainTextConstruct(
								"limedatabasebackup.zip"));
						ResumableGDataFileUploader uploader = new ResumableGDataFileUploader.Builder(
								service,
								new URL(
										"https://docs.google.com/feeds/upload/create-session/default/private/full"),
								mediaFile, metadata)
								.chunkSize(DEFAULT_CHUNK_SIZE)
								.executor(executor)
								.trackProgress(
										new FileUploadProgressListener(),
										PROGRESS_UPDATE_INTERVAL).build();
						uploader.start();
						listener.listenTo(uploader);

						pd.setProgress(40);
						while (!listener.isDone()) {
							double progress = 40 + listener.getProgress()*60;
							if(DEBUG) Log.i(TAG, "Google cloud backup progress : " + progress);
							pd.setProgress((int) progress );

							if(mLIMEPref.getParameterBoolean("cloud_in_process", false)){
								status = false;
								break;
							}
							try {
								Thread.sleep(100);
							} catch (InterruptedException ie) {
								throw ie; 
							}
						}

						mLIMEPref.setParameter("cloud_backup_size", tempfile.length());

						mLIMEPref.setParameter("cloud_in_process", new Boolean(false));
						if(getStatus()){
							showNotificationMessage(
									activity.getApplicationContext().getText(
											R.string.l3_initial_cloud_backup_end)
											+ "", intentLIMEMenu);
						}else{
							showNotificationMessage("Cannot Backup Database", intentLIMEMenu);
						}
						
						pd.setProgress(100);
					} catch (Exception e) {
						mLIMEPref.setParameter("cloud_in_process", new Boolean(false));
						showNotificationMessage("Cannot Backup Database", intentLIMEMenu);
						e.printStackTrace();
						return;
					}
				}
			});

			thread.start();

		} catch (Exception e) {
			mLIMEPref.setParameter("cloud_in_process", new Boolean(false));
			showNotificationMessage("Cannot Backup Database", intentLIMEMenu);
			return;
		}
	}
	
	

	public void cloudRestore(Activity act, ProgressDialog pd, File temp) {

		// DBSrv = db;

		Deprecated_DBCloudServer.pd = pd;
		isbackup = false;
		activity = act;
		// mLIMEPref = pref;
		tempfile = temp;
		credential.setAccessToken(null);
		accountName = mLIMEPref.getParameterString(PREF_ACCOUNT_NAME, null);
		credential.setAccessToken(mLIMEPref.getParameterString(PREF_AUTH_TOKEN, null));

		accountManager = new GoogleAccountManager(activity);
		
        if(accountManager.getAccounts().length > 0){
        	mLIMEPref.setParameter( PREF_ACCOUNT_NAME, accountManager.getAccounts()[0].name);	
        	accountName = accountManager.getAccounts()[0].name;
         }
        
        if(mLIMEPref.getParameterString(PREF_AUTH_TOKEN, null) != null){
        	accountManager.invalidateAuthToken(mLIMEPref.getParameterString(PREF_AUTH_TOKEN, null));
        }
        
		Account account = accountManager.getAccountByName(accountName);
		accountManager.getAccountManager().getAuthToken(account,
				AUTH_TOKEN_TYPE, null, activity,
				new AccountManagerCallback<Bundle>() {

					public void run(AccountManagerFuture<Bundle> future) {
						try {
							Bundle bundle = future.getResult();
							
							if (bundle.containsKey(AccountManager.KEY_AUTHTOKEN)) {
								
								setAuthToken(bundle.getString(AccountManager.KEY_AUTHTOKEN));
								
								
								failed = false;
							}else{
								failed = true;
							}
							ready = true;
						} catch (Exception e) {
							failed = true;
							ready = true;
							mLIMEPref.setParameter("cloud_in_process", new Boolean(false));
						}
					}

				}, null);
		
		while(!ready){
			try {
				Thread.sleep(1000);
			} catch (InterruptedException e) {
				failed = true;
				e.printStackTrace();
			}
		}
		
		if(!failed){
			restoreProcess();	
		}else{
			mLIMEPref.setParameter("cloud_in_process", new Boolean(false));
			showNotificationMessage("Cannot Restore Database", intentLIMEMenu);
		}
	}

	private static void setAccountName(String accountname) {
		mLIMEPref.setParameter(PREF_ACCOUNT_NAME, accountname);
		accountName = accountname;
	}

	private static void setAuthToken(String authToken) {
		mLIMEPref.setParameter(PREF_AUTH_TOKEN, authToken);
		credential.setAccessToken(authToken);
	}

	private static DocsService getDocsService() {
		DocsService service = new DocsService("LIMEDocsService");
		return service;
	}

	private static void restoreProcess() {
		try {

			Thread thread = new Thread(new Runnable() {
				public void run() {
					if(DEBUG)
						Log.i(TAG,"Google cloud restore thread started...");

					try {
						DocsService service = getDocsService();
						service.setOAuth2Credentials(credential);
						pd.setProgress(20);
						// Remove Exists Backup from the cloud
						String TARGET = "https://docs.google.com/feeds/default/private/full?title=limedatabasebackup";
						URL metafeedUrl = new URL(TARGET);
						DocumentListFeed feed = service.getFeed(metafeedUrl,
								DocumentListFeed.class);
						
						if(feed == null || feed.getEntries().size() == 0){
							mLIMEPref.setParameter("cloud_in_process",new Boolean(false));
							showNotificationMessage(activity.getApplicationContext()
									.getText(R.string.l3_initial_restore_error)	+ "", intentLIMEMenu);
							pd.cancel();
							mLIMEPref.setParameter("cloud_in_process", new Boolean(false));
							showNotificationMessage("Cannot Restore Database", intentLIMEMenu);
							return;
						}
						pd.setProgress(40);
						for (DocumentListEntry entry : feed.getEntries()) {

							MediaContent srcentry = (MediaContent) entry.getContent();
							MediaContent mc = new MediaContent();
							mc.setUri(srcentry.getUri().toString());
							MediaSource ms = service.getMedia(mc);
							
							InputStream inStream = null;
							FileOutputStream outStream = null;
							try {
								if(tempfile.exists()){
									tempfile.delete();
								}
								
								inStream = ms.getInputStream();
								outStream = new FileOutputStream(tempfile.getAbsolutePath());

								long fileSize = inStream.available();
								if(fileSize == 0 || fileSize == -1){
									fileSize = mLIMEPref.getParameterLong("cloud_backup_size", 0);
								}
								byte[] buffer = new byte[8192];
								int bytesRead = 0;
								long totalRead = 0;
								while ((bytesRead = inStream.read(buffer, 0,
										8192)) != -1) {
									outStream.write(buffer, 0, bytesRead);
									totalRead += bytesRead; 
									double percentage = (double)totalRead/(double)fileSize;
									if(totalRead >0 && fileSize >0 )
										pd.setProgress((int)(40+(59 * percentage)));
									if(DEBUG) 
										Log.i(TAG, "restoreProcess(), file size = " + fileSize + ". bytesRead=" + bytesRead + ". totalRead=" + totalRead);
								}
								mLIMEPref.setParameter("cloud_backup_size", totalRead);
							}catch(Exception e){
								
							}finally {
							
								if (inStream != null) {
									inStream.close();
								}
								if (outStream != null) {
									outStream.flush();
									outStream.close();
								}
								pd.setProgress(99);
							}

							String dbtarget = mLIMEPref.getParameterString("dbtarget");
							if (dbtarget.equals("device")) {
								decompressFile(tempfile,
										LIME.DATABASE_DECOMPRESS_FOLDER,
										LIME.DATABASE_NAME);
							} else {
								decompressFile(tempfile,
										LIME.DATABASE_DECOMPRESS_FOLDER_SDCARD,
										LIME.DATABASE_NAME);
							}
							mLIMEPref.setParameter(
									LIME.DATABASE_DOWNLOAD_STATUS, "true");
							if(getStatus()){
								showNotificationMessage(
										activity.getApplicationContext()
												.getText(
														R.string.l3_initial_cloud_restore_end)
												+ "", intentLIMEMenu);
							}else{
								showNotificationMessage("Cannot Restore Database", intentLIMEMenu);
							}

							tempfile.deleteOnExit();

							dbAdapter.openDBConnection(true);
							mLIMEPref.setParameter("cloud_in_process",new Boolean(false));
							pd.setProgress(100);
							break;
						}
					} catch (Exception e) {
						mLIMEPref.setParameter("cloud_in_process", new Boolean(false));
						showNotificationMessage("Cannot Restore Database", intentLIMEMenu);
						e.printStackTrace();
						return;
					}
				}
			});

			thread.start();

		} catch (Exception e) {
			mLIMEPref.setParameter("cloud_in_process", new Boolean(false));
			showNotificationMessage("Cannot Restore Database", intentLIMEMenu);
			return;
		}
	}


	private static MediaFileSource getMediaFileSource(String fileName) {
		File file = new File(fileName);
		MediaFileSource mediaFile = new MediaFileSource(file,
				"application/x-zip");
		return mediaFile;
	}


}
