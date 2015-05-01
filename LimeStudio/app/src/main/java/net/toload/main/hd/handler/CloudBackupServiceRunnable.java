package net.toload.main.hd.handler;

import com.google.android.gms.drive.Drive;

import net.toload.main.hd.global.LIMEPreferenceManager;
import net.toload.main.hd.ui.SetupImFragment;

import java.io.File;

public class CloudBackupServiceRunnable  implements Runnable{

	@Override
	protected void finalize() throws Throwable {
		super.finalize();
	}
	private CloudServierHandler handler;
	private SetupImFragment fragment;
	
	private static final String AUTH_TOKEN_TYPE = "oauth2:https://docs.google.com/feeds/ https://docs.googleusercontent.com/";
	//private static final int REQUEST_AUTHENTICATE = 0;
	private static final int MAX_CONCURRENT_UPLOADS = 10;
	private static final int PROGRESS_UPDATE_INTERVAL = 1000;
	private static final int DEFAULT_CHUNK_SIZE = 10000000;
	

	private Drive drive;

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
	
	public CloudBackupServiceRunnable(CloudServierHandler h, SetupImFragment fragment, File sourcefile) {
		this.handler = h;
		this.fragment = fragment;
		this.sourceFile = sourcefile;

		this.mLIMEPref = new LIMEPreferenceManager(fragment.getActivity());
	}

	/*private DocsService getDocsService(GoogleCredential credential) {
		DocsService service = new DocsService("LIME");
					service.setOAuth2Credentials(credential);	
		return service;
	}*/
	
	public void run() {



		/*

		credential = GoogleAccountCredential.usingOAuth2(this, Collections.singleton(TasksScopes.TASKS));

		this.credential = new GoogleCredential().setAccessToken(PREF_AUTH_TOKEN);
		this.drive = new Plus.builder(new NetHttpTransport(), JacksonFactory.getDefaultInstance(), credential)
				.setApplicationName("Google-PlusSample/1.0")
				.build();

		this.accountManager = new GoogleAccountManager(fragment.getActivity());
		String accountName = "";
		accountManager = new GoogleAccountManager(fragment.getActivity());

        if(accountManager.getAccounts().length > 0){	
        	accountName = accountManager.getAccounts()[0].name;
        	mLIMEPref.setParameter(PREF_ACCOUNT_NAME, accountName);
		}
        
        if(mLIMEPref.getParameterString(PREF_AUTH_TOKEN, null) != null){
        	accountManager.invalidateAuthToken(mLIMEPref.getParameterString(PREF_AUTH_TOKEN, null));
        }
        
		Account account = accountManager.getAccountByName(accountName);
		accountManager.getAccountManager().getAuthToken(account,
				AUTH_TOKEN_TYPE, null, fragment.getActivity(),
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
							fragment.getActivity().getText(
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
		}*/
	}
	

	/*private static DocsService getDocsService() {
		DocsService service = new DocsService("LIMEDocsService");
		return service;
	}

	private static MediaFileSource getMediaFileSource(String fileName) {
		File file = new File(fileName);
		MediaFileSource mediaFile = new MediaFileSource(file,
				"application/x-zip");
		return mediaFile;
	}*/
	
}
