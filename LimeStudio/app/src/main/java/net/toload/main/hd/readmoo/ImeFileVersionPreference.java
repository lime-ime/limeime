package net.toload.main.hd.readmoo;

import android.content.Context;
import android.content.SharedPreferences;


public class ImeFileVersionPreference {

    private SharedPreferences mPref;
    public ImeFileVersionPreference(Context context) {
        mPref =  context.getSharedPreferences("ime_file_ver", Context.MODE_PRIVATE);
    }

    public int getFileVersion(String code) {
        return mPref.getInt(code, 0);
    }

    public void setFileVersion(String code, int value) {
        mPref.edit().putInt(code, value).apply();
    }

}
