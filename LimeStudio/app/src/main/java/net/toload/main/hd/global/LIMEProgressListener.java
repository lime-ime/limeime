package net.toload.main.hd.global;

import org.apache.http.HttpEntity;
import org.apache.http.entity.HttpEntityWrapper;

import java.io.FilterOutputStream;
import java.io.IOException;
import java.io.OutputStream;

/**
 * Created by Jeremy on 2015/5/23.
 */
public abstract class LIMEProgressListener {
    public LIMEProgressListener() {    }

    public abstract void onProgress(long var1, long var2, String status);

    public long progressInterval() {
        return 500L;
    }

    public void onError(int code, String source){
        return;
    }
    public void onPreExecute(){
        return;
    }
    public void onPostExecute(boolean success, String status, int code){
        return;
    }
    public void onStatusUpdate(String status){
        return;
    }
}
