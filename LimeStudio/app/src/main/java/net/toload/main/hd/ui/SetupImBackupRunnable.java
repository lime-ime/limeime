/*
 *
 *  *
 *  **    Copyright 2015, The LimeIME Open Source Project
 *  **
 *  **    Project Url: http://github.com/lime-ime/limeime/
 *  **                 http://android.toload.net/
 *  **
 *  **    This program is free software: you can redistribute it and/or modify
 *  **    it under the terms of the GNU General Public License as published by
 *  **    the Free Software Foundation, either version 3 of the License, or
 *  **    (at your option) any later version.
 *  *
 *  **    This program is distributed in the hope that it will be useful,
 *  **    but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  **    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  **    GNU General Public License for more details.
 *  *
 *  **    You should have received a copy of the GNU General Public License
 *  **    along with this program.  If not, see <http://www.gnu.org/licenses/>.
 *  *
 *
 */

package net.toload.main.hd.ui;

import android.os.AsyncTask;
import android.os.RemoteException;
import android.widget.Toast;

import com.dropbox.client2.DropboxAPI;
import com.dropbox.client2.ProgressListener;
import com.dropbox.client2.exception.DropboxException;
import com.dropbox.client2.exception.DropboxFileSizeException;
import com.dropbox.client2.exception.DropboxPartialFileException;
import com.dropbox.client2.exception.DropboxServerException;
import com.dropbox.client2.exception.DropboxUnlinkedException;
import com.dropbox.core.DbxException;
import com.dropbox.core.v2.DbxClientV2;
import com.dropbox.core.v2.files.FileMetadata;
import com.dropbox.core.v2.files.UploadErrorException;
import com.dropbox.core.v2.files.WriteMode;

import net.toload.main.hd.DBServer;
import net.toload.main.hd.Lime;
import net.toload.main.hd.R;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;

public class SetupImBackupRunnable implements Runnable {

    // Global
    private String mType = null;
    private SetupImFragment mFragment = null;

    private SetupImHandler mHandler;
    //private GoogleAccountCredential mCredential;

    // Dropbox
    private DbxClientV2 sDbxClient;
    private DropboxAPI.UploadRequest mRequest;

    public SetupImBackupRunnable(SetupImFragment fragment, SetupImHandler handler, String type, DbxClientV2 sDbxClient) {
        //this.mCredential = credential;
        this.mHandler = handler;
        this.mType = type;
        this.sDbxClient = sDbxClient;
        this.mFragment = fragment;
    }

    @Override
    protected void finalize() throws Throwable {
        super.finalize();
    }

    @Override
    public void run() {

        mHandler.showProgress(true, this.mFragment.getResources().getString(R.string.setup_im_backup_message));
        // Preparing the file to be backup
        if (mType.equals(Lime.LOCAL) || mType.equals(Lime.GOOGLE) || mType.equals(Lime.DROPBOX)) {
            try {
                DBServer.backupDatabase();
            } catch (RemoteException e) {
                e.printStackTrace();
            }
        }

        switch (mType) {

            case Lime.DROPBOX: {
                mHandler.cancelProgress();
                mHandler.showProgress(false, this.mFragment.getResources().getString(R.string.setup_im_backup_message));
                mHandler.setProgressIndeterminate(true);

                File sourcefile = new File(Lime.DATABASE_FOLDER_EXTERNAL + Lime.DATABASE_BACKUP_NAME);
                mHandler.setProgressIndeterminate(false);
                backupToDropbox upload = new backupToDropbox(mHandler, mFragment, sDbxClient, "", sourcefile);
                upload.execute();
                break;
            }
            default:
                DBServer.showNotificationMessage(mFragment.getResources().getString(R.string.l3_initial_backup_end));
                mHandler.cancelProgress();
                break;
        }
    }


    private class backupToDropbox extends AsyncTask< Void, Long, Boolean> {

        private DbxClientV2 mDbxClient;
        private String mPath;
        private File mFile;

        private long mFileLen;
        private DropboxAPI.UploadRequest mRequest;

        private SetupImHandler mHandler;

        private String mErrorMsg;



        public backupToDropbox(SetupImHandler handler, SetupImFragment fragment, DbxClientV2 sDbxClient, String dropboxPath, File file) {


            mFileLen = file.length();
            mDbxClient = sDbxClient;
            mPath = dropboxPath;
            mFile = file;
            mHandler = handler;
            mFragment = fragment;

            mHandler.updateProgress(mFragment.getText(R.string.l3_initial_dropbox_backup_start).toString());
        }

        @Override
        protected Boolean doInBackground(Void... params) {
            try {


                // By creating a request, we get a handle to the putFile operation,
                // so we can cancel it later if we want to
                FileInputStream fis = new FileInputStream(mFile);
                String path = mPath + mFile.getName();
                //mRequest = mApi.putFileOverwriteRequest(path, fis, mFile.length(),
                FileMetadata metaData = mDbxClient.files().uploadBuilder(path)
                        .withMode(WriteMode.OVERWRITE)
                        .uploadAndFinish(fis);

                if (metaData != null) {
                    return true;
                }

            } catch (DbxException | IOException e) {
                mErrorMsg = mFragment.getText(R.string.l3_initial_dropbox_failed).toString();
                e.printStackTrace();

            }
            return false;
        }

        @Override
        protected void onProgressUpdate(Long... progress) {
            int percent = (int) (100.0 * (double) progress[0] / mFileLen + 0.5);
            mHandler.updateProgress( mFragment.getText(R.string.l3_initial_dropbox_uploading).toString() );
            mHandler.updateProgress(percent);

        }
        @Override
        protected void onPostExecute(Boolean result) {
            mHandler.cancelProgress();
            if (result) {
                DBServer.showNotificationMessage(mFragment.getText(R.string.l3_initial_dropbox_backup_end).toString());
            } else {
                DBServer.showNotificationMessage(mErrorMsg);
            }
        }


    }
}