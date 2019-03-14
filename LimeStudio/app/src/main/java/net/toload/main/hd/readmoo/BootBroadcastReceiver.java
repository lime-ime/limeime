package net.toload.main.hd.readmoo;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

public class BootBroadcastReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        Intent imeIntent = new Intent();
        imeIntent.setPackage("net.toload.main.hd");
        imeIntent.setAction("readmoo.ACTION_MOO_INSTALL_IME");
        context.startService(imeIntent);
    }
}
