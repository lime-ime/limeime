package net.toload.main.hd.ui;

import android.os.Handler;
import android.os.Message;

/**
 * Created by Art Hung on 2015/4/26.
 */
public class SetupImHandler extends Handler {

    private SetupImFragment fragment = null;

    public SetupImHandler(SetupImFragment fragment) {
        this.fragment = fragment;
    }

    @Override
    public void handleMessage(Message msg) {

        String action = msg.getData().getString("action");
        String type = msg.getData().getString("type");

        if(action != null && action.equalsIgnoreCase("progress")){
            if(type != null){
                if(type.equalsIgnoreCase("show")){
                    fragment.showProgress();;
                }else if(type.equalsIgnoreCase("cancel")){
                    fragment.cancelProgress();
                }else if(type.equalsIgnoreCase("update")){
                    int value= msg.getData().getInt("value");
                    fragment.updateProgress(value);
                }
            }
        }else if(action != null && action.equalsIgnoreCase("toast")){
            String message = msg.getData().getString("message");
            int length = msg.getData().getInt("length");

            if(message != null){
                fragment.showToastMessage(message, length);
            }else{
                fragment.showToastMessage("Error", length);
            }

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

    public void showToastMessage(String message, int length){
        Message m = new Message();
        m.getData().putString("action", "toast");
        m.getData().putString("message", message);
        m.getData().putInt("length", length);
        this.sendMessageDelayed(m, 1);
    }

}
