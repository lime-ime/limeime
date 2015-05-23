package net.toload.main.hd;

import android.os.Handler;
import android.os.Message;

/**
 * Created by Art Hung on 2015/4/26.
 */
public class MainActivityHandler extends Handler {

    private MainActivity activity = null;

    public MainActivityHandler(MainActivity activity) {
        this.activity = activity;
    }

    @Override
    public void handleMessage(Message msg) {

        String action = msg.getData().getString("action");
        String type = msg.getData().getString("type");

        if(action != null && action.equalsIgnoreCase("progress")){
            if(type != null){
                if(type.equalsIgnoreCase("show")){
                    activity.showProgress();;
                }else if(type.equalsIgnoreCase("cancel")){
                    activity.cancelProgress();
                }else if(type.equalsIgnoreCase("update")){
                    int value= msg.getData().getInt("value");
                    activity.updateProgress(value);
                }else if(type.equalsIgnoreCase("message")){
                    String message = msg.getData().getString("message");
                    activity.updateProgress(message);
                }
            }
        }else if(action != null && action.equalsIgnoreCase("toast")){
            String message = msg.getData().getString("message");
            int length = msg.getData().getInt("length");

            if(message != null){
                activity.showToastMessage(message, length);
            }else{
                activity.showToastMessage("Error", length);
            }

        }else if(action != null && action.equalsIgnoreCase("share")){
            String filepath = msg.getData().getString("filepath");
            activity.shareTo(filepath);
        }

    }

    public void cancelProgress() {
        Message m = new Message();
        m.getData().putString("action", "progress");
        m.getData().putString("type", "cancel");
        this.sendMessageDelayed(m, 1);
    }

    public void showProgress() {
        Message m = new Message();
        m.getData().putString("action", "progress");
        m.getData().putString("type", "show");
        this.sendMessageDelayed(m, 1);
    }

    public void updateProgress(int value) {
        Message m = new Message();
        m.getData().putString("action", "progress");
        m.getData().putString("type", "update");
        m.getData().putInt("value", value);
        this.sendMessageDelayed(m, 1);
    }

    public void updateProgress(String message) {
        Message m = new Message();
        m.getData().putString("action", "progress");
        m.getData().putString("type", "message");
        m.getData().putString("message", message);
        this.sendMessageDelayed(m, 1);
    }

    public void showToastMessage(String message, int length){
        Message m = new Message();
                m.getData().putString("action", "toast");
                m.getData().putString("message", message);
                m.getData().putInt("length", length);
        this.sendMessageDelayed(m, 1);
    }

    public void shareTo(String filepath){
        Message m = new Message();
        m.getData().putString("action", "share");
        m.getData().putString("filepath", filepath);
        this.sendMessageDelayed(m, 1);
    }
}
