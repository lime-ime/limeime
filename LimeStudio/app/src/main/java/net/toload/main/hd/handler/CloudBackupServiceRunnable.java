package net.toload.main.hd.handler;

import android.accounts.Account;
import android.accounts.AccountManager;
import android.accounts.AccountManagerCallback;
import android.accounts.AccountManagerFuture;
import android.app.Activity;
import android.os.Bundle;

import com.google.api.client.googleapis.auth.oauth2.GoogleCredential;
import com.google.api.client.googleapis.extensions.android2.auth.GoogleAccountManager;
import com.google.gdata.client.docs.DocsService;
import com.google.gdata.client.media.ResumableGDataFileUploader;
import com.google.gdata.data.PlainTextConstruct;
import com.google.gdata.data.docs.DocumentListEntry;
import com.google.gdata.data.docs.DocumentListFeed;
import com.google.gdata.data.media.MediaFileSource;

import net.toload.main.hd.R;
import net.toload.main.hd.global.LIMEPreferenceManager;
import net.toload.main.hd.limesettings.DBServer;
import net.toload.main.hd.limesettings.FileUploadProgressListener;
import net.toload.main.hd.limesettings.LIMEInitial;

import java.io.File;
import java.net.URI;
import java.net.URL;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class CloudBackupServiceRunnable  implements Runnable{

	@Override
	protected void finalize() throws Throwable {
		super.finalize();
	}
	private CloudServierHandler handler;
	private Activity activity;
	
	private static final String AUTH_TOKEN_TYPE = "oauth2:https://docs.google.com/feeds/ https://docs.googleusercontent.com/";
	//private static final int REQUEST_AUTHENTICATE = 0;
	private static final int MAX_CONCURRENT_UPLOADS = 10;
	private static final int PROGRESS_UPDATE_INTERVAL = 1000;
	private static final int DEFAULT_CHUNK_SIZE = 10000000;
	
	//private SharedPreferences pref;
	private GoogleAccountManager accountManager;
	private GoogleCredential credential;
	static final String PREF_ACCOUNT_NAME = "accountName1";
	static final String PREF_AUTH_TOKEN = "authToken";
	LIMEPreferenceManager mLIMEPref;

	//private boolean first = true;
	private boolean ready = false;
	private boolean failed = false;
	private File sourceFile;

	public final static int intentLIMEMenu = 0;
	public final static int intentLIMEMappingLoading = 1;
	public final static int intentLIMEInitial = 2;
	
	public CloudBackupServiceRunnable(CloudServierHandler h, LIMEInitial activity, File sourcefile) {
		this.handler = h;
		this.activity = activity;
		this.sourceFile = sourcefile;

		this.accountManager = new GoogleAccountManager(activity);
		this.credential = new GoogleCredential();
		this.mLIMEPref = new LIMEPreferenceManager(activity);
	}

	/*private DocsService getDocsService(GoogleCredential credential) {
		DocsService service = new DocsService("LIME");
					service.setOAuth2Credentials(credential);	
		return service;
	}*/
	
	public void run() {
		
		
		String accountName = "";
		accountManager = new GoogleAccountManager(activity);

        if(accountManager.getAccounts().length > 0){	
        	accountName = accountManager.getAccounts()[0].name;
        	mLIMEPref.setParameter(PREF_ACCOUNT_NAME, accountName);
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
								mLIMEPref.setParameter(PREF_AUTH_TOKEN, bundle.getString(AccountManager.KEY_AUTHTOKEN));
								credential.setAccessToken(bundle.getString(AccountManager.KEY_AUTHTOKEN));
								failed = false;
								handler.updateProgressDialog(10);
							}else{
								failed = true;
							}
							ready = true;
						} catch (Exception e) {
							failed = true;
							ready = true;
						}
					}

				}, null);
		
		while(!ready){
			try {
				Thread.sleep(1000);
			} catch (InterruptedException e) {
				handler.closeProgressDialog();
				mLIMEPref.setParameter("cloud_in_process", Boolean.valueOf(false));
				failed = true;
				e.printStackTrace();
				return;
			}
		}
		
		if(!failed){
		
			boolean status = false;
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
				handler.updateProgressDialog(20);
				FileUploadProgressListener listener = new FileUploadProgressListener();

				// Pool for handling concurrent upload tasks
				ExecutorService executor = Executors
						.newFixedThreadPool(MAX_CONCURRENT_UPLOADS);
				MediaFileSource mediaFile = getMediaFileSource(sourceFile
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

				while (!listener.isDone()) {
					double progress = 20 + listener.getProgress()*80;
					handler.updateProgressDialog((int)progress);
					try {
						Thread.sleep(1000);
					} catch (InterruptedException ie) {
						throw ie; 
					}
					status = true;
				}

				mLIMEPref.setParameter("cloud_backup_size", sourceFile.length());
				mLIMEPref.setParameter("cloud_in_process", Boolean.valueOf(false));
				if(status){
					DBServer.showNotificationMessage(
							activity.getApplicationContext().getText(
									R.string.l3_initial_cloud_backup_end)
									+ "", intentLIMEMenu);
				}else{
					DBServer.showNotificationMessage("Cannot Backup Database", intentLIMEMenu);
				}
				
				handler.closeProgressDialog();
			} catch (Exception e) {
				e.printStackTrace();
				DBServer.showNotificationMessage("Cannot Backup Database", intentLIMEMenu);
				mLIMEPref.setParameter("cloud_in_process", Boolean.valueOf(false));
				handler.closeProgressDialog();
				return;
			}
		}else{
			handler.closeProgressDialog();
			mLIMEPref.setParameter("cloud_in_process", Boolean.valueOf(false));
		}
	}
	

	private static DocsService getDocsService() {
		DocsService service = new DocsService("LIMEDocsService");
		return service;
	}

	private static MediaFileSource getMediaFileSource(String fileName) {
		File file = new File(fileName);
		MediaFileSource mediaFile = new MediaFileSource(file,
				"application/x-zip");
		return mediaFile;
	}
	
}
