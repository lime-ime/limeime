package net.toload.main.hd.ui;

import android.os.Handler;
import android.os.Message;

import net.toload.main.hd.data.Related;

import java.util.List;

public class SetupImGoogleHandler extends Handler {

    private List<Related> relatedlist;
    private SetupImGoogleActivity activity = null;

    public SetupImGoogleHandler(SetupImGoogleActivity activity) {
        this.activity = activity;
    }

    @Override
    public void handleMessage(Message msg) {
        String action = msg.getData().getString("action");
        String message = msg.getData().getString("message");
        switch (action) {
            case "show":
                activity.showProgress(message);
                break;
            case "backup":
                activity.backupToGoogle();
                break;
            case "restore":
                activity.restoreFromGoogle();
                break;
            default:
                break;
        }
    }

    public void backup() {
        Message m = new Message();
        m.getData().putString("action", "backup");
        this.sendMessageDelayed(m, 1);
    }

    public void restore() {
        Message m = new Message();
        m.getData().putString("action", "restore");
        this.sendMessageDelayed(m, 1);
    }

    public void show(String message) {
        Message m = new Message();
        m.getData().putString("action", "show");
        m.getData().putString("message", message);
        this.sendMessageDelayed(m, 1);
    }


}
