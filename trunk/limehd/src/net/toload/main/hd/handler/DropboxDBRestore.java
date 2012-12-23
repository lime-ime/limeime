package net.toload.main.hd.handler;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;

import net.toload.main.hd.R;
import net.toload.main.hd.global.LIME;
import net.toload.main.hd.global.LIMEPreferenceManager;
import net.toload.main.hd.limesettings.DBServer;
import net.toload.main.hd.limesettings.LIMEInitial;
import android.app.ProgressDialog;
import android.content.Context;
import android.os.AsyncTask;
import android.util.Log;


import com.dropbox.client2.DropboxAPI;
import com.dropbox.client2.ProgressListener;
import com.dropbox.client2.exception.DropboxException;
import com.dropbox.client2.exception.DropboxIOException;
import com.dropbox.client2.exception.DropboxParseException;
import com.dropbox.client2.exception.DropboxPartialFileException;
import com.dropbox.client2.exception.DropboxServerException;
import com.dropbox.client2.exception.DropboxUnlinkedException;

/**
 * Here we show getting metadata for a directory and downloading a file in a
 * background thread, trying to show typical exception handling and flow of
 * control for an app that downloads a file from Dropbox.
 */

public class DropboxDBRestore extends AsyncTask<Void, Long, Boolean> {
	private final boolean DEBUG = false;
	private final String TAG = "DropboxDBRestore";
    public final static int intentLIMEMenu = 0;
	LIMEPreferenceManager mLIMEPref;

	private LIMEInitial mActivity;
    private Context mContext;
    private final ProgressDialog mDialog;
    private DropboxAPI<?> mApi;
    private String mPath;
    private File mFile;
    
    private FileOutputStream mFos;
    

    private boolean mCanceled;
    //private Long mFileLen;
    private String mErrorMsg;

    

    public DropboxDBRestore(LIMEInitial activity, Context context, DropboxAPI<?> api,String dropboxPath , File tempfile) {
        // We set the context this way so we don't accidentally leak activities
        
    	mActivity = activity;
    	mContext = context.getApplicationContext();
        
        mApi = api;
        mPath = dropboxPath;
        mFile = tempfile;
        
        mLIMEPref = new LIMEPreferenceManager(mContext);
   
       
        mDialog = new ProgressDialog(context);
        mDialog.setTitle(mContext.getText(R.string.l3_initial_dropbox_restore_database));
        mDialog.setMax(100);
        mDialog.setCanceledOnTouchOutside(false);
        mDialog.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
        mDialog.setProgress(0);
        /*
        mDialog.setButton(DialogInterface.BUTTON_NEGATIVE, 
        		mContext.getText(R.string.lime_loading_cancel)
        		, new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int which) {
                // This will cancel the putFile operation
                mRequest.abort();
            }
        });
        */
        mDialog.show();
    }

    @Override
    protected Boolean doInBackground(Void... params) {
    	
    	
    	try {
           

            // mOutputStream is class level field.
    		mFos = new FileOutputStream(mFile);
            //DropboxFileInfo info = 
            		mApi.getFile(mPath, null, mFos, new ProgressListener() {

                        @Override
                        public void onProgress(long bytes, long total) {
                            if (!mCanceled){
                                //publishProgress(bytes, total);
                            	Long[] bytess = {bytes, total};
                                publishProgress(bytess);
                            }else {
                                if (mFos != null) {
                                    try {
                                    	mFos.close();
                                    } catch (IOException e) {
                                    }
                                }
                            }
                        }
                    });
        //} catch (DropboxException e) {
        //    Log.e("DbExampleLog",
        //            "Something went wrong while getting file.");

        } catch (FileNotFoundException e) {
            //Log.e("DbExampleLog", "File not found.");
        	mErrorMsg = mContext.getText(R.string.l3_initial_dropbox_restore_error).toString();
        	
        } catch (DropboxUnlinkedException e) {
            // The AuthSession wasn't properly authenticated or user unlinked.
        	mErrorMsg = mContext.getText(R.string.l3_initial_dropbox_authetication_failed).toString();
        } catch (DropboxPartialFileException e) {
            // We canceled the operation
        	mErrorMsg = mContext.getText(R.string.l3_initial_dropbox_failed).toString();
        } catch (DropboxServerException e) {
            // Server-side exception.  These are examples of what could happen,
            // but we don't do anything special with them here.
            if (e.error == DropboxServerException._304_NOT_MODIFIED) {
                // won't happen since we don't pass in revision with metadata
            } else if (e.error == DropboxServerException._401_UNAUTHORIZED) {
                // Unauthorized, so we should unlink them.  You may want to
                // automatically log the user out in this case.
            } else if (e.error == DropboxServerException._403_FORBIDDEN) {
                // Not allowed to access this
            } else if (e.error == DropboxServerException._404_NOT_FOUND) {
                // path not found (or if it was the thumbnail, can't be
                // thumbnailed)
            } else if (e.error == DropboxServerException._406_NOT_ACCEPTABLE) {
                // too many entries to return
            } else if (e.error == DropboxServerException._415_UNSUPPORTED_MEDIA) {
                // can't be thumbnailed
            } else if (e.error == DropboxServerException._507_INSUFFICIENT_STORAGE) {
                // user is over quota
            } else {
                // Something else
            }
            // This gets the Dropbox error, translated into the user's language
            mErrorMsg = e.body.userError;
            if (mErrorMsg == null) {
                mErrorMsg = e.body.error;
            }
        } catch (DropboxIOException e) {
            // Happens all the time, probably want to retry automatically.
            //mErrorMsg = "Network error.  Try again.";
        	mErrorMsg = mContext.getText(R.string.l3_initial_dropbox_failed).toString();
        } catch (DropboxParseException e) {
            // Probably due to Dropbox server restarting, should retry
            //mErrorMsg = "Dropbox error.  Try again.";
        	mErrorMsg = mContext.getText(R.string.l3_initial_dropbox_failed).toString();
        } catch (DropboxException e) {
            // Unknown error
            //mErrorMsg = "Unknown error.  Try again.";
        	mErrorMsg = mContext.getText(R.string.l3_initial_dropbox_failed).toString();
        
        } finally {
            if (mFos != null) {
                try {
                	mFos.close();
                	mFos = null;
                	
                	//Download finished. Restore db now.
                	String target = mLIMEPref.getParameterString("dbtarget");
        			if (target.equals("device")) {
        				DBServer.decompressFile(mFile,
        						LIME.DATABASE_DECOMPRESS_FOLDER,
        						LIME.DATABASE_NAME);
        			} else {
        				DBServer.decompressFile(mFile,
        						LIME.DATABASE_DECOMPRESS_FOLDER_SDCARD,
        						LIME.DATABASE_NAME);
        			}
        			mLIMEPref.setParameter(LIME.DATABASE_DOWNLOAD_STATUS, "true");
        			mDialog.setProgress(100);
        			
                	return true;
                } catch (IOException e) {
                }
            }
            
        }
    	return false;
		
    }
    	
    	
  

    @Override
    protected void onProgressUpdate(Long... progress) {
    	int percent = (int)(100.0*(double)progress[0]/progress[1]+ 0.5); 
        
    	if(DEBUG)
    		Log.i(TAG, "onProgressUpdate(), bytes = " + progress[0] +", total = "+  progress[1] + ", percent = " + percent);
    		
    	
        mDialog.setProgress(percent);
    }

    @Override
    protected void onPostExecute(Boolean result) {
    	super.onPostExecute(result);
    	if(DEBUG)
    		Log.i(TAG, "onPostExecute()");
        mDialog.dismiss();
        if (result) {     	
        	mActivity.initialButton();
            DBServer.showNotificationMessage(
					mContext.getText(R.string.l3_initial_dropbox_restore_end)+ "", intentLIMEMenu);
            
        } else {
        	DBServer.showNotificationMessage(mErrorMsg+ "", intentLIMEMenu);
        }

    }




}
