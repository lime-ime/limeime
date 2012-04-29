
package net.toload.main.hd;

import java.io.IOException;

import android.annotation.TargetApi;
import android.app.backup.BackupAgentHelper;
import android.app.backup.BackupDataInput;
import android.app.backup.BackupDataOutput;
import android.app.backup.SharedPreferencesBackupHelper;
import android.os.ParcelFileDescriptor;
import android.util.Log;

/**
 * Backs up the LIME shared preferences.
 */
@TargetApi(8)
public class LIMEBackupAgent extends BackupAgentHelper {
	static final String TAG = "LIMEBackupAgent";
	static final boolean DEBUG = true;
	
    // A key to uniquely identify the set of backup data
    static final String PREFS_BACKUP_KEY = "defaultPrefs";

    // Allocate a helper and add it to the backup agent
    @Override
    public void onCreate() {
    	if(DEBUG)
    		Log.i(TAG, "onCreate(), backingup default share prferences for :" + this.getPackageName () + "_preferences");
        SharedPreferencesBackupHelper helper = new SharedPreferencesBackupHelper(this, this.getPackageName () + "_preferences");
        addHelper(PREFS_BACKUP_KEY, helper);
    }

	@Override
	public void onBackup(ParcelFileDescriptor oldState, BackupDataOutput data,
			ParcelFileDescriptor newState) throws IOException {
		// TODO Auto-generated method stub
		super.onBackup(oldState, data, newState);
		if(DEBUG) Log.i(TAG,"onBackup()");
	}

	@Override
	public void onRestore(BackupDataInput data, int appVersionCode,
			ParcelFileDescriptor newState) throws IOException {
		// TODO Auto-generated method stub
		super.onRestore(data, appVersionCode, newState);
		if(DEBUG) Log.i(TAG,"onRestore()");
	}
}
