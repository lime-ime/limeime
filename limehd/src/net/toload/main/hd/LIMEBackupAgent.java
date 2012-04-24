
package net.toload.main.hd;

import android.app.backup.BackupAgentHelper;
import android.app.backup.SharedPreferencesBackupHelper;

/**
 * Backs up the Latin IME shared preferences.
 */
public class LIMEBackupAgent extends BackupAgentHelper {

    @Override
    public void onCreate() {
        addHelper("shared_pref", new SharedPreferencesBackupHelper(this,
                getPackageName() + "_preferences"));
    }
}
