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
import com.google.gdata.data.MediaContent;
import com.google.gdata.data.docs.DocumentListEntry;
import com.google.gdata.data.docs.DocumentListFeed;
import com.google.gdata.data.media.MediaSource;

import net.toload.main.hd.R;
import net.toload.main.hd.global.LIME;
import net.toload.main.hd.global.LIMEPreferenceManager;
import net.toload.main.hd.limesettings.DBServer;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.URL;

public class CloudRestoreServiceRunnable  implements Runnable{

	@Override
	protected void finalize() throws Throwable {
		super.finalize();
	}
	private CloudServierHandler handler;
	private Activity activity;
	
	private static final String AUTH_TOKEN_TYPE = "oauth2:https://docs.google.com/feeds/ https://docs.googleusercontent.com/";
	/*private static final int REQUEST_AUTHENTICATE = 0;
	private static final int MAX_CONCURRENT_UPLOADS = 10;
	private static final int PROGRESS_UPDATE_INTERVAL = 1000;
	private static final int DEFAULT_CHUNK_SIZE = 10000000;
	
	private SharedPreferences pref;*/
	private GoogleAccountManager accountManager;
	private GoogleCredential credential;
	static final String PREF_ACCOUNT_NAME = "accountName1";
	static final String PREF_AUTH_TOKEN = "authToken";
	LIMEPreferenceManager mLIMEPref;

	//private boolean first = true;
	private boolean ready = false;
	private boolean failed = false;
	private File tempfile;

	public final static int intentLIMEMenu = 0;
	public final static int intentLIMEMappingLoading = 1;
	public final static int intentLIMEInitial = 2;
	
	public CloudRestoreServiceRunnable(CloudServierHandler h, Activity activity, File tempfile) {
		this.handler = h;
		this.activity = activity;
		this.tempfile = tempfile;
		//this.pref = PreferenceManager.getDefaultSharedPreferences(activity);
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
				failed = true;
				e.printStackTrace();
			}
		}
		
		if(!failed){
		
			try {
				DocsService service = getDocsService();
				service.setOAuth2Credentials(credential);
				//pd.setProgress(20);
				// Remove Exists Backup from the cloud
				String TARGET = "https://docs.google.com/feeds/default/private/full?title=limedatabasebackup";
				URL metafeedUrl = new URL(TARGET);
				DocumentListFeed feed = service.getFeed(metafeedUrl,
						DocumentListFeed.class);
				
				if(feed == null || feed.getEntries().size() == 0){
					mLIMEPref.setParameter("cloud_in_process",new Boolean(false));
					DBServer.showNotificationMessage(activity.getApplicationContext()
							.getText(R.string.l3_initial_restore_error)	+ "", intentLIMEMenu);
					handler.closeProgressDialog();
					return;
				}

				handler.updateProgressDialog(20);
				
				boolean status = false;
				//pd.setProgress(40);
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

						long fileSize = entry.getQuotaBytesUsed();
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
							if(totalRead >0 && fileSize >0 ){
								handler.updateProgressDialog((int)(40+(59 * percentage)));
							}
						}
						mLIMEPref.setParameter("cloud_backup_size", totalRead);
						DBServer.showNotificationMessage(
								activity.getApplicationContext()
										.getText(
												R.string.l3_initial_cloud_restore_end)
										+ "", intentLIMEMenu);
						status = true;
					}catch(Exception e){
						e.printStackTrace();
						mLIMEPref.setParameter("cloud_in_process",new Boolean(false));
						DBServer.showNotificationMessage(activity.getApplicationContext()
								.getText(R.string.l3_initial_restore_error)	+ "", intentLIMEMenu);
						handler.closeProgressDialog();
						return;
					}finally {
					
						if (inStream != null) {
							inStream.close();
						}
						if (outStream != null) {
							outStream.flush();
							outStream.close();
						}
					}

					if(status){
						String target = mLIMEPref.getParameterString("dbtarget");
						if (target.equals("device")) {
							DBServer.decompressFile(tempfile,
									LIME.DATABASE_DECOMPRESS_FOLDER,
									LIME.DATABASE_NAME);
						} else {
							DBServer.decompressFile(tempfile,
									LIME.DATABASE_DECOMPRESS_FOLDER_SDCARD,
									LIME.DATABASE_NAME);
						}
						mLIMEPref.setParameter(LIME.DATABASE_DOWNLOAD_STATUS, "true");

						tempfile.deleteOnExit();
					}

					handler.closeProgressDialog();
					mLIMEPref.setParameter("cloud_in_process",new Boolean(false));
					break;
				}
			} catch (Exception e) {
				handler.closeProgressDialog();
				mLIMEPref.setParameter("cloud_in_process", new Boolean(false));
				e.printStackTrace();
				return;
			}
		}
	}
	

	private static DocsService getDocsService() {
		DocsService service = new DocsService("LIMEDocsService");
		return service;
	}

}
