package net.toload.main.hd.limesettings;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;

import net.toload.main.hd.R;

import android.app.ProgressDialog;
import android.content.Context;
import android.os.AsyncTask;


import com.dropbox.client2.DropboxAPI;
import com.dropbox.client2.DropboxAPI.UploadRequest;
import com.dropbox.client2.ProgressListener;
import com.dropbox.client2.exception.DropboxException;
import com.dropbox.client2.exception.DropboxFileSizeException;
import com.dropbox.client2.exception.DropboxIOException;
import com.dropbox.client2.exception.DropboxParseException;
import com.dropbox.client2.exception.DropboxPartialFileException;
import com.dropbox.client2.exception.DropboxServerException;
import com.dropbox.client2.exception.DropboxUnlinkedException;

/**
 * Here we show uploading a file in a background thread, trying to show
 * typical exception handling and flow of control for an app that uploads a
 * file from Dropbox.
 */
public class DropboxDBBackup extends AsyncTask<Void, Long, Boolean> {

    private DropboxAPI<?> mApi;
    private String mPath;
    private File mFile;

    private long mFileLen;
    private UploadRequest mRequest;
    private Context mContext;
    private final ProgressDialog mDialog;

    private String mErrorMsg;

    public final static int intentLIMEMenu = 0;

    public DropboxDBBackup(Context context, DropboxAPI<?> api, String dropboxPath,
            File file) {
        // We set the context this way so we don't accidentally leak activities
        mContext = context.getApplicationContext();

        mFileLen = file.length();
        mApi = api;
        mPath = dropboxPath;
        mFile = file;

        mDialog = new ProgressDialog(context);
        mDialog.setMax(100);
        mDialog.setCanceledOnTouchOutside(false);
        mDialog.setTitle(mContext.getText(R.string.l3_initial_dropbox_backup_database));
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
        	
        	
            // By creating a request, we get a handle to the putFile operation,
            // so we can cancel it later if we want to
            FileInputStream fis = new FileInputStream(mFile);
            String path = mPath + mFile.getName();
            mRequest = mApi.putFileOverwriteRequest(path, fis, mFile.length(),
                    new ProgressListener() {
                @Override
                public long progressInterval() {
                    // Update the progress bar every half-second or so
                    return 500;
                }

                @Override
                public void onProgress(long bytes, long total) {
                    publishProgress(bytes);
                }
            });

            if (mRequest != null) {
                mRequest.upload();
                return true;
            }

        } catch (DropboxUnlinkedException e) {
            // This session wasn't authenticated properly or user unlinked
            //mErrorMsg = "This app wasn't authenticated properly.";
        	mErrorMsg = mContext.getText(R.string.l3_initial_dropbox_authetication_failed).toString();
        } catch (DropboxFileSizeException e) {
            // File size too big to upload via the API
            //mErrorMsg = "This file is too big to upload";
        	mErrorMsg = mContext.getText(R.string.l3_initial_dropbox_large).toString();
        } catch (DropboxPartialFileException e) {
            // We canceled the operation
            mErrorMsg = "Upload canceled";
            mErrorMsg = mContext.getText(R.string.l3_initial_dropbox_canceled).toString();
        } catch (DropboxServerException e) {
            // Server-side exception.  These are examples of what could happen,
            // but we don't do anything special with them here.
            if (e.error == DropboxServerException._401_UNAUTHORIZED) {
                // Unauthorized, so we should unlink them.  You may want to
                // automatically log the user out in this case.
            } else if (e.error == DropboxServerException._403_FORBIDDEN) {
                // Not allowed to access this
            } else if (e.error == DropboxServerException._404_NOT_FOUND) {
                // path not found (or if it was the thumbnail, can't be
                // thumbnailed)
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
        } catch (FileNotFoundException e) {
        }
        return false;
    }

    @Override
    protected void onProgressUpdate(Long... progress) {
        int percent = (int)(100.0*(double)progress[0]/mFileLen + 0.5);
        mDialog.setProgress(percent);
    }

    @Override
    protected void onPostExecute(Boolean result) {
        mDialog.dismiss();
        if (result) {
            //showToast("LIME-HD database upload to Dropbox successfully.");
            DBServer.showNotificationMessage(
					mContext.getText(R.string.l3_initial_dropbox_backup_end)+ "", intentLIMEMenu);
        } else {
            //showToast(mErrorMsg);
        	DBServer.showNotificationMessage(mErrorMsg+ "", intentLIMEMenu);
        }
    }

   
}
