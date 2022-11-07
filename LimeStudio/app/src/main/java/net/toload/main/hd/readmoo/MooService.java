package net.toload.main.hd.readmoo;

import android.app.IntentService;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.AssetManager;
import android.util.Log;
import android.util.Pair;

import net.toload.main.hd.DBServer;
import net.toload.main.hd.Lime;
import net.toload.main.hd.R;
import net.toload.main.hd.data.Im;
import net.toload.main.hd.global.LIMEPreferenceManager;
import net.toload.main.hd.limedb.LimeDB;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;


public class MooService extends IntentService {
    private static final String TAG = "[MooService]";

    private IMEInstaller m_installer;

    public MooService() {
        super("[MooService]");
    }

    @Override
    public void onCreate() {
        super.onCreate();
        m_installer = new IMEInstaller(this);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        m_installer.release();
    }

    @Override
    protected void onHandleIntent(Intent intent) {
        // if screen lock is on, HAVE TO wait util system fully ready
        PackageManager pm = getPackageManager();
        try {
            pm.getApplicationInfo("com.readmoo.mooreader.eink",
                    PackageManager.MATCH_SYSTEM_ONLY);
        } catch (PackageManager.NameNotFoundException e) {
            Log.e(TAG, "may not ready yet ?");
            e.printStackTrace();
            return;
        }

        m_installer.exec();
    }
}
