package net.toload.main.hd.readmoo;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.AssetManager;
import android.support.annotation.NonNull;
import android.support.v4.app.JobIntentService;
import android.util.Log;

public class MooJobIntentService extends JobIntentService {
    private final static String TAG = "[MooJobIntentService]";
    public static final int JOB_ID = 0xa0;

    private IMEInstaller m_installer;


    public static void enqueueWork(Context context, Intent work) {
        enqueueWork(context, MooJobIntentService.class, JOB_ID, work);
    }

    public void onCreate() {
        super.onCreate();
        Log.d(TAG, "[onCreate]");
        m_installer = new IMEInstaller(this);
    }

    public void onDestroy() {
        super.onDestroy();
        Log.d(TAG, "[onDestroy]");
        m_installer.release();
    }

    @Override
    protected void onHandleWork(@NonNull Intent intent) {
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
