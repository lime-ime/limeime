package net.toload.main.hd.readmoo;

import android.app.IntentService;
import android.content.Context;
import android.content.Intent;
import android.net.ConnectivityManager;
import android.util.Log;

import net.toload.main.hd.BuildConfig;
import net.toload.main.hd.Lime;
import net.toload.main.hd.ui.SetupImLoadRunnable;


public class MooService extends IntentService {
    private static final String TAG = "[MooService]";

    private ConnectivityManager connManager;

    private Thread m_thread;

    public MooService() {
        super("[MooService]");
    }

    @Override
    public void onCreate() {
        super.onCreate();
        connManager = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
    }

    @Override
    protected void onHandleIntent(Intent intent) {
        try {
            String type = intent.getStringExtra("type");
            String code = intent.getStringExtra("code");

            downloadAndLoadIm(type, code);

        } catch (Exception e) {
            e.printStackTrace();
            notifyState(State.Error, null);
        }
    }

    public void downloadAndLoadIm(String type, String code) {
        boolean restorelearning = false; //chkSetupImRestoreLearning.isChecked();

        if (connManager.getActiveNetworkInfo() != null && connManager.getActiveNetworkInfo().isConnected()) {

            String url = null;

            if (type.equals(Lime.IM_ARRAY)) {
                url = Lime.DATABASE_CLOUD_IM_ARRAY;
            } else if (type.equals(Lime.IM_ARRAY10)) {
                url = Lime.DATABASE_CLOUD_IM_ARRAY10;
            } else if (type.equals(Lime.IM_CJ_BIG5)) {
                url = Lime.DATABASE_CLOUD_IM_CJ_BIG5;
            } else if (type.equals(Lime.IM_CJ)) {
                url = Lime.DATABASE_CLOUD_IM_CJ;
            } else if (type.equals(Lime.IM_CJHK)) {
                url = Lime.DATABASE_CLOUD_IM_CJHK;
            } else if (type.equals(Lime.IM_CJ5)) {
                url = Lime.DATABASE_CLOUD_IM_CJ5;
            } else if (type.equals(Lime.IM_DAYI)) {
                url = Lime.DATABASE_CLOUD_IM_DAYI;
            } else if (type.equals(Lime.IM_DAYIUNI)) {
                url = Lime.DATABASE_CLOUD_IM_DAYIUNI;
            } else if (type.equals(Lime.IM_DAYIUNIP)) {
                url = Lime.DATABASE_CLOUD_IM_DAYIUNIP;
            } else if (type.equals(Lime.IM_ECJ)) {
                url = Lime.DATABASE_CLOUD_IM_ECJ;
            } else if (type.equals(Lime.IM_ECJHK)) {
                url = Lime.DATABASE_CLOUD_IM_ECJHK;
            } else if (type.equals(Lime.IM_EZ)) {
                url = Lime.DATABASE_CLOUD_IM_EZ;
            } else if (type.equals(Lime.IM_PHONETIC_BIG5)) {
                url = Lime.DATABASE_CLOUD_IM_PHONETIC_BIG5;
            } else if (type.equals(Lime.IM_PHONETIC_ADV_BIG5)) {
                url = Lime.DATABASE_CLOUD_IM_PHONETICCOMPLETE_BIG5;
            } else if (type.equals(Lime.IM_PHONETIC)) {
                url = Lime.DATABASE_CLOUD_IM_PHONETIC;
            } else if (type.equals(Lime.IM_PHONETIC_ADV)) {
                url = Lime.DATABASE_CLOUD_IM_PHONETICCOMPLETE;
            } else if (type.equals(Lime.IM_PINYIN)) {
                url = Lime.DATABASE_CLOUD_IM_PINYIN;
            } else if (type.equals(Lime.IM_PINYINGB)) {
                url = Lime.DATABASE_CLOUD_IM_PINYINGB;
            } else if (type.equals(Lime.IM_SCJ)) {
                url = Lime.DATABASE_CLOUD_IM_SCJ;
            } else if (type.equals(Lime.IM_WB)) {
                url = Lime.DATABASE_CLOUD_IM_WB;
            } else if (type.equals(Lime.IM_HS)) {
                url = Lime.DATABASE_CLOUD_IM_HS;
            } else if (type.equals(Lime.IM_HS_V1)) {
                url = Lime.DATABASE_CLOUD_IM_HS_V1;
            } else if (type.equals(Lime.IM_HS_V2)) {
                url = Lime.DATABASE_CLOUD_IM_HS_V2;
            } else if (type.equals(Lime.IM_HS_V3)) {
                url = Lime.DATABASE_CLOUD_IM_HS_V3;
            }

            if (BuildConfig.DEBUG)
                Log.d(TAG, "[downloadAndLoadIm] code= " + code
                        + ", type= " + type + ", url= " + url);

            try {
                notifyState(State.Installing, type);

                m_thread = new Thread(new SetupImLoadRunnable(this, code, type, url,
                        restorelearning, new SetupImLoadRunnable.OnSetupLoadCallback() {
                    @Override
                    public void onFinish(boolean result, String type) {
                        notifyState(result ? State.Done : State.Error, type);
                    }
                }));
                m_thread.start();
                m_thread.join();

            } catch (InterruptedException e) {
                notifyState(State.Error, type);
            }
        }
        else {
            // network unavailable
            notifyState(State.NetworkError, type);
        }
    }

    private void notifyState(State result, String type) {
        Intent intent = new Intent("readmoo.ACTION_MOO_DOWNLOAD_IME_STATE");
        intent.putExtra("type", type);
        intent.putExtra("state", result.ordinal());
        sendBroadcast(intent);
    }
}
