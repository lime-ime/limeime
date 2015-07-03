
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
	static final boolean DEBUG = false;
	
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
