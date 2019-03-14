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

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;


public class MooService extends IntentService {
    private static final String TAG = "[MooService]";

    private DBServer m_dbsrv;
    private LimeDB m_datasource;
    private LIMEPreferenceManager m_limePref;

    private MooIMEManager m_mooIMEManager;

    public MooService() {
        super("[MooService]");
    }

    @Override
    public void onCreate() {
        super.onCreate();
        m_dbsrv = new DBServer(this);
        m_datasource = new LimeDB(this);
        m_limePref = new LIMEPreferenceManager(this);
        m_mooIMEManager = new MooIMEManager(this);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        m_mooIMEManager.release();
    }

    @Override
    protected void onHandleIntent(Intent intent) {

        final String[] codeList = getResources().getStringArray(R.array.ime_code);
        final String[] pathList = getResources().getStringArray(R.array.ime_path);

        if (pathList == null || codeList == null) {
            notifyState(State.Error, "path or code NULL");
            return;
        }

        // figure out installed ime
        List<Im> imlist = m_datasource.getIm(null, Lime.IM_TYPE_NAME);
//        for (Im im : imlist)
//            Log.d(TAG, "[imlist] code= " + im.getCode());

        List<Integer> installCandidates = new ArrayList<>();
        for (int i = 0; i < codeList.length; i++) {
            boolean installed = false;
            for (Im im : imlist) {
                if (codeList[i].equalsIgnoreCase(im.getCode())) {
                    installed = true;
                    break;
                }
            }
            if (!installed)
                installCandidates.add(i);
        }

        if (installCandidates.size() > 0)
            m_mooIMEManager.show();

        for (Integer index : installCandidates) {
            String code = codeList[index];
            String path = pathList[index];
            try {
                install(code, path);

            } catch (Exception e) {
                e.printStackTrace();
                notifyState(State.Error, code);
                return;
            }
        }

        notifyState(State.Done, null);
    }

    private void install(String imtype, String path) throws IOException {
        Log.d(TAG, "[install] " + imtype);
        AssetManager am = getAssets();
        InputStream in = am.open(path);
        m_dbsrv.importMapping(in, imtype);

        m_limePref.setParameter("_table", "");
        //mLIMEPref.setResetCacheFlag(true);
        DBServer.resetCache();


        List<Im> imlist = m_datasource.getIm(null, Lime.IM_TYPE_NAME);

        // Update IM pick up list items
        m_limePref.syncIMActivatedState(imlist);
    }

    private void notifyState(State result, String code) {
        Intent intent = new Intent("readmoo.ACTION_MOO_INSTALL_IME_STATE");
        intent.putExtra("code", code);
        intent.putExtra("state", result.ordinal());
        sendBroadcast(intent);
    }
}
