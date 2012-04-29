
package net.toload.main.hd;

import android.annotation.TargetApi;
import android.app.backup.BackupAgentHelper;
import android.app.backup.SharedPreferencesBackupHelper;

/**
 * Backs up the Latin IME shared preferences.
 */
@TargetApi(8)
public class LIMEBackupAgent extends BackupAgentHelper {

    @TargetApi(8)
	@Override
    public void onCreate() {
        addHelper("shared_pref", new SharedPreferencesBackupHelper(this,
                getPackageName() + "_preferences"));
    }
}
