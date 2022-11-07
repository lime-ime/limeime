package net.toload.main.hd.readmoo;

import android.content.Context;

import net.toload.main.hd.R;

import java.util.HashMap;
import java.util.Map;

public class ReinstallOperator {
    
    private final ImeFileVersionPreference m_pref;

    private final int[] m_fileVer;
    private final Map<String, Integer>  m_versions = new HashMap<>();
    public ReinstallOperator(Context context)  {
        m_pref = new ImeFileVersionPreference(context);
        m_fileVer = context.getResources().getIntArray(R.array.ime_file_ver);
        final String[]  codeList = context.getResources().getStringArray(R.array.ime_code);
        for (int i = 0; i < codeList.length; i++) {
            m_versions.put(codeList[i], m_fileVer[i]);
        }
    }
    
    public boolean needReinstall(String code) {
        return m_pref.getFileVersion(code) < m_versions.get(code);
    }
    
    public void setReinstallDone(String code) {
        m_pref.setFileVersion(code, m_versions.get(code));
    }
}
