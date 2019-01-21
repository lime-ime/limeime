package net.toload.main.hd.readmoo;

import android.app.IntentService;
import android.content.Intent;
import android.content.res.AssetManager;
import android.util.Log;

import net.toload.main.hd.DBServer;
import net.toload.main.hd.Lime;
import net.toload.main.hd.R;
import net.toload.main.hd.data.Im;
import net.toload.main.hd.global.LIMEPreferenceManager;
import net.toload.main.hd.limedb.LimeDB;

import java.io.File;
import java.util.List;


public class MooService extends IntentService {
    private static final String TAG = "[MooService]";


    private DBServer m_dbsrv;
    private LimeDB m_datasource;
    private LIMEPreferenceManager m_limePref;

    public MooService() {
        super("[MooService]");
    }

    @Override
    public void onCreate() {
        super.onCreate();
        m_dbsrv = new DBServer(this);
        m_datasource = new LimeDB(this);
        m_limePref = new LIMEPreferenceManager(this);
    }

    @Override
    protected void onHandleIntent(Intent intent) {
        final String[] code = getResources().getStringArray(R.array.ime_code);
        final String[] path = getResources().getStringArray(R.array.ime_path);

        if (path == null || code == null) {
            notifyState(State.Error, "path or code NULL");
            return;
        }

        for (int i = 0; i < path.length; i++) {
            try {
                install(code[i], path[i]);

            } catch (Exception e) {
                e.printStackTrace();
                notifyState(State.Error, code[i]);
                return;
            }
        }

        notifyState(State.Done, null);
    }

    private void install(String imtype, String path) {
        File tempfile = new File(path);
        m_dbsrv.importMapping(tempfile, imtype);

        m_limePref.setParameter("_table", "");
        //mLIMEPref.setResetCacheFlag(true);
        DBServer.resetCache();


        List<Im> imlist = m_datasource.getIm(null, Lime.IM_TYPE_NAME);

       // Update IM pick up list items
        m_limePref.syncIMActivatedState(imlist);
    }
//
//    public void downloadAndLoadIm(String type, String code) {
//
//        if (connManager.getActiveNetworkInfo() != null && connManager.getActiveNetworkInfo().isConnected()) {
//
//            String url = null;
//
//            if (type.equals(Lime.IM_ARRAY)) {
//                url = Lime.DATABASE_CLOUD_IM_ARRAY;
//            } else if (type.equals(Lime.IM_ARRAY10)) {
//                url = Lime.DATABASE_CLOUD_IM_ARRAY10;
//            } else if (type.equals(Lime.IM_CJ_BIG5)) {
//                url = Lime.DATABASE_CLOUD_IM_CJ_BIG5;
//            } else if (type.equals(Lime.IM_CJ)) {
//                url = Lime.DATABASE_CLOUD_IM_CJ;
//            } else if (type.equals(Lime.IM_CJHK)) {
//                url = Lime.DATABASE_CLOUD_IM_CJHK;
//            } else if (type.equals(Lime.IM_CJ5)) {
//                url = Lime.DATABASE_CLOUD_IM_CJ5;
//            } else if (type.equals(Lime.IM_DAYI)) {
//                url = Lime.DATABASE_CLOUD_IM_DAYI;
//            } else if (type.equals(Lime.IM_DAYIUNI)) {
//                url = Lime.DATABASE_CLOUD_IM_DAYIUNI;
//            } else if (type.equals(Lime.IM_DAYIUNIP)) {
//                url = Lime.DATABASE_CLOUD_IM_DAYIUNIP;
//            } else if (type.equals(Lime.IM_ECJ)) {
//                url = Lime.DATABASE_CLOUD_IM_ECJ;
//            } else if (type.equals(Lime.IM_ECJHK)) {
//                url = Lime.DATABASE_CLOUD_IM_ECJHK;
//            } else if (type.equals(Lime.IM_EZ)) {
//                url = Lime.DATABASE_CLOUD_IM_EZ;
//            } else if (type.equals(Lime.IM_PHONETIC_BIG5)) {
//                url = Lime.DATABASE_CLOUD_IM_PHONETIC_BIG5;
//            } else if (type.equals(Lime.IM_PHONETIC_ADV_BIG5)) {
//                url = Lime.DATABASE_CLOUD_IM_PHONETICCOMPLETE_BIG5;
//            } else if (type.equals(Lime.IM_PHONETIC)) {
//                url = Lime.DATABASE_CLOUD_IM_PHONETIC;
//            } else if (type.equals(Lime.IM_PHONETIC_ADV)) {
//                url = Lime.DATABASE_CLOUD_IM_PHONETICCOMPLETE;
//            } else if (type.equals(Lime.IM_PINYIN)) {
//                url = Lime.DATABASE_CLOUD_IM_PINYIN;
//            } else if (type.equals(Lime.IM_PINYINGB)) {
//                url = Lime.DATABASE_CLOUD_IM_PINYINGB;
//            } else if (type.equals(Lime.IM_SCJ)) {
//                url = Lime.DATABASE_CLOUD_IM_SCJ;
//            } else if (type.equals(Lime.IM_WB)) {
//                url = Lime.DATABASE_CLOUD_IM_WB;
//            } else if (type.equals(Lime.IM_HS)) {
//                url = Lime.DATABASE_CLOUD_IM_HS;
//            } else if (type.equals(Lime.IM_HS_V1)) {
//                url = Lime.DATABASE_CLOUD_IM_HS_V1;
//            } else if (type.equals(Lime.IM_HS_V2)) {
//                url = Lime.DATABASE_CLOUD_IM_HS_V2;
//            } else if (type.equals(Lime.IM_HS_V3)) {
//                url = Lime.DATABASE_CLOUD_IM_HS_V3;
//            }
//
//            if (BuildConfig.DEBUG)
//                Log.d(TAG, "[downloadAndLoadIm] code= " + code
//                        + ", type= " + type + ", url= " + url);
//
//            try {
//                notifyState(State.Installing, type);
//
//                m_thread = new Thread(new SetupImLoadRunnable(this, code, type, url,
//                        restorelearning, new SetupImLoadRunnable.OnSetupLoadCallback() {
//                    @Override
//                    public void onFinish(boolean result, String type) {
//                        notifyState(result ? State.Done : State.Error, type);
//                    }
//                }));
//                m_thread.start();
//                m_thread.join();
//
//            } catch (InterruptedException e) {
//                notifyState(State.Error, type);
//            }
//        }
//        else {
//            // network unavailable
//            notifyState(State.NetworkError, type);
//        }
//    }
//
    private void notifyState(State result, String code) {
        Intent intent = new Intent("readmoo.ACTION_MOO_DOWNLOAD_IME_STATE");
        intent.putExtra("code", code);
        intent.putExtra("state", result.ordinal());
        sendBroadcast(intent);
    }
}
