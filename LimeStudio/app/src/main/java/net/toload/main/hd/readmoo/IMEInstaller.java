package net.toload.main.hd.readmoo;

import android.content.Context;
import android.content.Intent;
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

public class IMEInstaller {
    private static final String TAG = "[IMEInstaller]";

    private final Context m_context;
    private final DBServer m_dbsrv;
    private final LimeDB m_datasource;
    private final LIMEPreferenceManager m_limePref;

    private final MooIMEManager m_mooIMEManager;

    private final ReinstallOperator m_reinstallOp;
    private final String[] m_codeList;
    private final String[] m_pathList;


    public IMEInstaller(Context context) {
        m_context = context;
        m_dbsrv = new DBServer(context);
        m_datasource = new LimeDB(context);
        m_limePref = new LIMEPreferenceManager(context);
        m_mooIMEManager = new MooIMEManager(context);

        m_codeList = context.getResources().getStringArray(R.array.ime_code);
        m_pathList = context.getResources().getStringArray(R.array.ime_path);
        m_reinstallOp = new ReinstallOperator(context);
    }

    public void release() {
        m_mooIMEManager.release();
    }

    public void exec() {
        if (m_pathList == null || m_codeList == null) {
            notifyState(State.Error, "path or code NULL");
            return;
        }

        // figure out installed ime
        List<Im> imlist = m_datasource.getIm(null, Lime.IM_TYPE_NAME);
        for (Im im : imlist)
            Log.d(TAG, "[imlist] code= " + im.getCode());
        for (String c : m_codeList)
            Log.d(TAG, "[m_codeList] code= " + c);


        List<Pair<Integer, ImeInstallAction>> installCandidates = new ArrayList<>();
        for (int i = 0; i < m_codeList.length; i++) {
            ImeInstallAction action = ImeInstallAction.Install;
            String code = m_codeList[i];

            for (Im im : imlist) {
                String imCode = im.getCode();
                if (code.equalsIgnoreCase(imCode)) { // 已安裝字根檔
                    try {
                        if (m_reinstallOp.needReinstall(code)) { // 是否需要重新安裝
                            action = ImeInstallAction.Reinstall;
                        }
                        else {
                            action = ImeInstallAction.None;
                        }
                        break;

                    } catch (Exception ex) {
                        ex.printStackTrace();
                    }
                }
            }

            if (action != ImeInstallAction.None)
                installCandidates.add(new Pair<>(i, action));
        }

        if (installCandidates.size() > 0)
            m_mooIMEManager.show();

        for (int i = 0; i < installCandidates.size(); i++) {
            ImeInstallAction action = installCandidates.get(i).second;
            int index = installCandidates.get(i).first;
            String code = m_codeList[index];
            String path = m_pathList[index];
            try {
                if (action == ImeInstallAction.Install)
                    install(code, path);
                else
                    reinstall(code, path);

            } catch (Exception e) {
                e.printStackTrace();
                notifyState(State.Error, code);
                return;
            }
        }

        notifyState(State.Done, null);
    }

    private void reinstall(String imtype, String path) throws IOException {
        install(imtype, path);
        m_reinstallOp.setReinstallDone(imtype);
    }

    private void install(String imtype, String path) throws IOException {
        Log.d(TAG, "[install] " + imtype);
        AssetManager am = m_context.getAssets();
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
        m_context.sendBroadcast(intent);
    }
}
