package net.toload.main.hd.limesettings;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URL;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import net.toload.main.hd.R;
import net.toload.main.hd.global.LIME;
import net.toload.main.hd.global.LIMEPreferenceManager;

import android.accounts.Account;
import android.accounts.AccountManager;
import android.accounts.AccountManagerCallback;
import android.accounts.AccountManagerFuture;
import android.accounts.AuthenticatorException;
import android.accounts.OperationCanceledException;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.util.Log;

import com.google.api.client.extensions.android2.AndroidHttp;
import com.google.api.client.googleapis.auth.oauth2.GoogleCredential;
import com.google.api.client.googleapis.extensions.android2.auth.GoogleAccountManager;
import com.google.api.client.googleapis.json.GoogleJsonResponseException;
import com.google.api.client.googleapis.json.GoogleJsonResponseException;
import com.google.api.client.http.HttpTransport;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.jackson.JacksonFactory;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.gdata.client.GoogleAuthTokenFactory.UserToken;
import com.google.gdata.client.docs.DocsService;
import com.google.gdata.client.media.ResumableGDataFileUploader;
import com.google.gdata.client.uploader.ProgressListener;
import com.google.gdata.client.uploader.ResumableHttpFileUploader;
import com.google.gdata.data.Link;
import com.google.gdata.data.MediaContent;
import com.google.gdata.data.PlainTextConstruct;
import com.google.gdata.data.docs.DocumentEntry;
import com.google.gdata.data.docs.DocumentListEntry;
import com.google.gdata.data.docs.DocumentListFeed;
import com.google.gdata.data.media.MediaFileSource;
import com.google.gdata.data.media.MediaSource;
import com.google.gdata.util.ServiceException;
import com.google.gdata.client.uploader.FileUploadData;
import com.google.gdata.client.uploader.ProgressListener;
import com.google.gdata.client.uploader.ResumableHttpFileUploader;

public class DBCloudServer {

	private static final String AUTH_TOKEN_TYPE = "oauth2:https://docs.google.com/feeds/ https://spreadsheets.google.com/feeds/ https://docs.googleusercontent.com/";
	private static final int MENU_ACCOUNTS = 0;
	private static final int REQUEST_AUTHENTICATE = 0;

	private static final int MAX_CONCURRENT_UPLOADS = 10;
	private static final int PROGRESS_UPDATE_INTERVAL = 1000;
	private static final int DEFAULT_CHUNK_SIZE = 10000000;

	final HttpTransport transport = AndroidHttp.newCompatibleTransport();
	final JsonFactory jsonFactory = new JacksonFactory();

	static final String PREF_ACCOUNT_NAME = "accountName1";
	static final String PREF_AUTH_TOKEN = "authToken";
	static String accountName;

	static GoogleAccountManager accountManager;
	static GoogleCredential credential = new GoogleCredential();

	static final String host = "docs.google.com";

	static LIMEPreferenceManager mLIMEPref = null;
	static Activity activity = null;
	static DBServer DBSrv = null;
	static File tempfile = null;

	public static void backup(Activity act, DBServer db,
			LIMEPreferenceManager pref, File temp) {

		DBSrv = db;
		activity = act;
		mLIMEPref = pref;
		tempfile = temp;
		credential.setAccessToken(null);
		accountName = mLIMEPref.getParameterString(PREF_ACCOUNT_NAME, null);
		credential.setAccessToken(mLIMEPref.getParameterString(PREF_AUTH_TOKEN,
				null));

		accountManager = new GoogleAccountManager(activity);

		if (credential.getAccessToken() != null) {
			accountManager.invalidateAuthToken(credential.getAccessToken());
			credential.setAccessToken(null);
			mLIMEPref.setParameter(PREF_AUTH_TOKEN, null);
		}
		gotAccountBackup();
	}

	private static void gotAccountBackup() {
		Account account = accountManager.getAccountByName(accountName);
		if (account == null) {
			chooseAccount();
			return;
		}
		if (credential.getAccessToken() != null) {
			backupProcess();
			return;
		}

		accountManager.getAccountManager().getAuthToken(account,
				AUTH_TOKEN_TYPE, true, new AccountManagerCallback<Bundle>() {

					public void run(AccountManagerFuture<Bundle> future) {
						try {
							Bundle bundle = future.getResult();
							if (bundle.containsKey(accountManager
									.getAccountManager().KEY_INTENT)) {
								Intent intent = bundle.getParcelable(accountManager
										.getAccountManager().KEY_INTENT);
								intent.setFlags(intent.getFlags()
										& ~Intent.FLAG_ACTIVITY_NEW_TASK);
								activity.startActivityForResult(intent,
										REQUEST_AUTHENTICATE);
							} else if (bundle.containsKey(accountManager
									.getAccountManager().KEY_AUTHTOKEN)) {
								setAuthToken(bundle.getString(accountManager
										.getAccountManager().KEY_AUTHTOKEN));
								backupProcess();
							}
						} catch (Exception e) {
							mLIMEPref.setParameter("cloud_in_process",
									new Boolean(false));
						}
					}

				}, null);
	}

	public static void restore(Activity act, DBServer db,
			LIMEPreferenceManager pref, File temp) {

		DBSrv = db;

		activity = act;
		mLIMEPref = pref;
		tempfile = temp;
		credential.setAccessToken(null);
		accountName = mLIMEPref.getParameterString(PREF_ACCOUNT_NAME, null);
		credential.setAccessToken(mLIMEPref.getParameterString(PREF_AUTH_TOKEN,
				null));

		accountManager = new GoogleAccountManager(activity);

		if (credential.getAccessToken() != null) {
			accountManager.invalidateAuthToken(credential.getAccessToken());
			credential.setAccessToken(null);
			mLIMEPref.setParameter(PREF_AUTH_TOKEN, null);
		}
		gotAccountRestore();
	}

	private static void gotAccountRestore() {
		Account account = accountManager.getAccountByName(accountName);
		if (account == null) {
			chooseAccount();
			return;
		}
		if (credential.getAccessToken() != null) {
			restoreProcess();
			return;
		}

		accountManager.getAccountManager().getAuthToken(account,
				AUTH_TOKEN_TYPE, true, new AccountManagerCallback<Bundle>() {

					public void run(AccountManagerFuture<Bundle> future) {
						try {
							Bundle bundle = future.getResult();
							if (bundle.containsKey(accountManager
									.getAccountManager().KEY_INTENT)) {
								Intent intent = bundle.getParcelable(accountManager
										.getAccountManager().KEY_INTENT);
								intent.setFlags(intent.getFlags()
										& ~Intent.FLAG_ACTIVITY_NEW_TASK);
								(activity).startActivityForResult(intent,
										REQUEST_AUTHENTICATE);
							} else if (bundle.containsKey(accountManager
									.getAccountManager().KEY_AUTHTOKEN)) {
								setAuthToken(bundle.getString(accountManager
										.getAccountManager().KEY_AUTHTOKEN));
								restoreProcess();
							}
						} catch (Exception e) {
							mLIMEPref.setParameter("cloud_in_process",
									new Boolean(false));
						}
					}

				}, null);
	}

	private static void chooseAccount() {

		accountManager.getAccountManager().getAuthTokenByFeatures(
				GoogleAccountManager.ACCOUNT_TYPE, AUTH_TOKEN_TYPE, null,
				activity, null, null, new AccountManagerCallback<Bundle>() {

					public void run(AccountManagerFuture<Bundle> future) {
						Bundle bundle;
						try {
							bundle = future.getResult();
							setAccountName(bundle
									.getString(AccountManager.KEY_ACCOUNT_NAME));
							setAuthToken(bundle
									.getString(AccountManager.KEY_AUTHTOKEN));
							restoreProcess();
						} catch (Exception e) {
							mLIMEPref.setParameter("cloud_in_process",
									new Boolean(false));
						}
					}

				}, null);
	}

	static void setAccountName(String accountName) {
		mLIMEPref.setParameter(PREF_ACCOUNT_NAME, accountName);
		accountName = accountName;
	}

	static void setAuthToken(String authToken) {
		mLIMEPref.setParameter(PREF_AUTH_TOKEN, authToken);
		credential.setAccessToken(authToken);
	}

	static DocsService getDocsService() {
		DocsService service = new DocsService("LIMEDocsService");
		return service;
	}

	static void restoreProcess() {
		try {

			Thread thread = new Thread(new Runnable() {
				public void run() {

					try{
						DocsService service = getDocsService();
								    service.setOAuth2Credentials(credential);
								    
						// Remove Exists Backup from the cloud
						String TARGET = "https://docs.google.com/feeds/default/private/full?title=limedatabasebackup.zip";
						URL metafeedUrl = new URL(TARGET);
						DocumentListFeed feed = service.getFeed(metafeedUrl,
								DocumentListFeed.class);
						for (DocumentListEntry entry : feed.getEntries()) {
	
							MediaContent srcentry = (MediaContent) entry.getContent();
							MediaContent mc = new MediaContent();
							mc.setUri(srcentry.getUri().toString());
							MediaSource ms = service.getMedia(mc);
	
							InputStream inStream = null;
							FileOutputStream outStream = null;
							try {
								inStream = ms.getInputStream();
								outStream = new FileOutputStream(tempfile.getAbsolutePath());
	
								byte[] buffer = new byte[8192];
								int bytesRead = 0;
								while ((bytesRead = inStream.read(buffer, 0, 8192)) != -1) {
									outStream.write(buffer, 0, bytesRead);
								}
							} finally {
								if (inStream != null) {
									inStream.close();
								}
								if (outStream != null) {
									outStream.flush();
									outStream.close();
								}
							}
	
							String dbtarget = mLIMEPref.getParameterString("dbtarget");
							if (dbtarget.equals("device")) {
								DBSrv.decompressFile(tempfile,
										LIME.DATABASE_DECOMPRESS_FOLDER, LIME.DATABASE_NAME);
							} else {
								DBSrv.decompressFile(tempfile,
										LIME.DATABASE_DECOMPRESS_FOLDER_SDCARD,
										LIME.DATABASE_NAME);
							}
							mLIMEPref.setParameter(LIME.DATABASE_DOWNLOAD_STATUS, "true");
							DBSrv.showNotificationMessage(activity.getApplicationContext()
									.getText(R.string.l3_initial_cloud_restore_end) + "",
									DBSrv.intentLIMEMenu);
	
							tempfile.deleteOnExit();
	
							DBSrv.dbAdapter.openDBConnection(true);
							mLIMEPref.setParameter("cloud_in_process", new Boolean(false));
						}
					}catch(Exception e){
						e.printStackTrace();
					}
				}
			});

			thread.start();
			
		} catch (Exception e) {
			handleGoogleException(e, false);
		}
	}

	static void backupProcess() {
		try {
			

			Thread thread = new Thread(new Runnable() {
				public void run() {

					try{

						DocsService service = getDocsService();
						service.setOAuth2Credentials(credential);

						String TARGET = "https://docs.google.com/feeds/default/private/full?title=limedatabasebackup.zip";

						try {
							URL metafeedUrl = new URL(TARGET);
							DocumentListFeed feed = service.getFeed(metafeedUrl,
									DocumentListFeed.class);
							for (DocumentListEntry entry : feed.getEntries()) {
								service.delete(new URI(entry.getEditLink().getHref()),
										entry.getEtag());
							}
						} catch (Exception e) {
							e.printStackTrace();
						}

						service.setHeader("Content-length", "0");

						FileUploadProgressListener listener = new FileUploadProgressListener();

						// Pool for handling concurrent upload tasks
						ExecutorService executor = Executors
								.newFixedThreadPool(MAX_CONCURRENT_UPLOADS);
						MediaFileSource mediaFile = getMediaFileSource(tempfile
								.getAbsolutePath());

						ResumableGDataFileUploader uploader = new ResumableGDataFileUploader.Builder(
								service,
								new URL(
										"https://docs.google.com/feeds/upload/create-session/default/private/full"),
								mediaFile, null /* empty meatadata */)
								.title("limedatabasebackup.zip")
								.chunkSize(DEFAULT_CHUNK_SIZE)
								.executor(executor)
								.trackProgress(new FileUploadProgressListener(),
										PROGRESS_UPDATE_INTERVAL).build();
						uploader.start();
						listener.listenTo(uploader);

						while (!listener.isDone()) {
							try {
								Thread.sleep(100);
							} catch (InterruptedException ie) {
								throw ie; // rethrow
							}
						}
						
						mLIMEPref.setParameter("cloud_in_process", new Boolean(false));
						DBSrv.showNotificationMessage(activity.getApplicationContext()
								.getText(R.string.l3_initial_cloud_backup_end) + "",
								DBSrv.intentLIMEMenu);

					}catch(Exception e){
						e.printStackTrace();
					}
				}
			});

			thread.start();
			
		} catch (Exception e) {
			handleGoogleException(e, true);
		}
	}

	private static MediaFileSource getMediaFileSource(String fileName) {
		File file = new File(fileName);
		MediaFileSource mediaFile = new MediaFileSource(file,
				"application/x-zip");
		return mediaFile;
	}

	static void handleGoogleException(Exception e, boolean isBackup) {
		if (e instanceof GoogleJsonResponseException) {
			GoogleJsonResponseException exception = (GoogleJsonResponseException) e;
			if (exception.getStatusCode() == 401) {
				accountManager.invalidateAuthToken(credential.getAccessToken());
				credential.setAccessToken(null);
				mLIMEPref.setParameter(PREF_AUTH_TOKEN, null);
				if (isBackup) {
					gotAccountBackup();
				} else {
					gotAccountRestore();
				}
				return;
			}
		}
	}

}
