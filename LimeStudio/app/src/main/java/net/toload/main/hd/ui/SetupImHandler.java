/*
 *
 *  *
 *  **    Copyright 2025, The LimeIME Open Source Project
 *  **
 *  **    Project Url: http://github.com/lime-ime/limeime/
 *  **                 http://android.toload.net/
 *  **
 *  **    This program is free software: you can redistribute it and/or modify
 *  **    it under the terms of the GNU General Public License as published by
 *  **    the Free Software Foundation, either version 3 of the License, or
 *  **    (at your option) any later version.
 *  *
 *  **    This program is distributed in the hope that it will be useful,
 *  **    but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  **    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  **    GNU General Public License for more details.
 *  *
 *  **    You should have received a copy of the GNU General Public License
 *  **    along with this program.  If not, see <http://www.gnu.org/licenses/>.
 *  *
 *
 */

package net.toload.main.hd.ui;

import android.os.Handler;
import android.os.Looper;
import android.os.Message;

import net.toload.main.hd.global.LIME;

public class SetupImHandler extends Handler {

    private final SetupImFragment fragment;

    public SetupImHandler(SetupImFragment fragment) {
        super(Looper.getMainLooper());
        this.fragment = fragment;
    }

    @Override
    public void handleMessage(Message msg) {

        String action = msg.getData().getString("action");
        String type = msg.getData().getString("type");

        //Log.i("LIME", "action: " + action);
        //Log.i("LIME", "type: " + type);

        if(action != null && action.equalsIgnoreCase("progress")){
            if(type != null){
                if(type.equalsIgnoreCase("showSpinner")){
                    String message = msg.getData().getString("message");
                    fragment.showProgress(true, message);
                }else if(type.equalsIgnoreCase("showHorizontal")){
                    String message = msg.getData().getString("message");
                    fragment.showProgress(false, message);
                }else if(type.equalsIgnoreCase("cancel")){
                    fragment.cancelProgress();
                }else if(type.equalsIgnoreCase("update")){
                    int value = msg.getData().getInt("value");
                    fragment.updateProgress(value);
                }else if(type.equalsIgnoreCase("message")){
                    String message = msg.getData().getString("message");
                    fragment.updateProgress(message);
                }else if(type.equalsIgnoreCase("indeterminate")){
                    boolean flag = msg.getData().getBoolean("flag");
                    fragment.setProgressIndeterminate(flag);
                }
            }
        }else if(action != null && action.equalsIgnoreCase("toast")){
            String message = msg.getData().getString("message");
            int length = msg.getData().getInt("length");

            fragment.showToastMessage((message != null) ? message : "Error", length);

        }else if(action != null && action.equalsIgnoreCase("initialbutton")){
            fragment.initialbutton();
        }else if(action != null && action.equalsIgnoreCase("updatecustombutton")){
            fragment.updateCustomButton();
        }else if(action != null && action.equalsIgnoreCase("reset")){
            String imtype = msg.getData().getString("im");
            boolean backuplearning = msg.getData().getBoolean("backup");
            fragment.resetImTable(imtype, backuplearning);
        }else if(action != null && action.equalsIgnoreCase("finish")){
            //String imtype = msg.getData().getString("im");
            fragment.finishProgress();
        }

    }

    public void cancelProgress() {
        Message m = new Message();
        m.getData().putString("action", "progress");
        m.getData().putString("type", "cancel");
        this.sendMessageDelayed(m, LIME.HANDLER_DELAY_MINIMAL_MS);
    }



    public void showProgress(boolean spinnerStyle, String message) {

        Message m = new Message();
        m.getData().putString("action", "progress");
        if(message != null && !message.isEmpty()){
            m.getData().putString("message", message);
        }

        if(spinnerStyle)
            m.getData().putString("type", "showSpinner");
        else
            m.getData().putString("type", "showHorizontal");
        this.sendMessageDelayed(m, LIME.HANDLER_DELAY_MINIMAL_MS);
    }

    public void setProgressIndeterminate(boolean flag){
        Message m = new Message();
        m.getData().putString("action", "progress");
        m.getData().putString("type", "indeterminate");
        m.getData().putBoolean("flag", flag);
        this.sendMessageDelayed(m, LIME.HANDLER_DELAY_MINIMAL_MS);
    }

    public void updateProgress(int value) {
        Message m = new Message();
        m.getData().putString("action", "progress");
        m.getData().putString("type", "update");
        m.getData().putInt("value", value);
        this.sendMessageDelayed(m, LIME.HANDLER_DELAY_MINIMAL_MS);
    }

    public void updateProgress(String message) {
        Message m = new Message();
        m.getData().putString("action", "progress");
        m.getData().putString("type", "message");
        m.getData().putString("message", message);
        this.sendMessageDelayed(m, LIME.HANDLER_DELAY_MINIMAL_MS);
    }

    public void showToastMessage(String message, int length){
        Message m = new Message();
                m.getData().putString("action", "toast");
                m.getData().putString("message", message);
                m.getData().putInt("length", length);
        this.sendMessageDelayed(m, LIME.HANDLER_DELAY_MINIMAL_MS);
    }

    public void initialImButtons() {
        Message m = new Message();
                m.getData().putString("action", "initialbutton");
        this.sendMessageDelayed(m, LIME.HANDLER_DELAY_MINIMAL_MS);
    }
   /* @Deprecated
    public void startLoadingWindow(String imtype) {
        Message m = new Message();
                m.getData().putString("action", "startloadingwindow");
                m.getData().putString("value", imtype);
        this.sendMessageDelayed(m, LIME.HANDLER_DELAY_MINIMAL_MS);
    }*/

    public void updateCustomButton() {
        Message m = new Message();
        m.getData().putString("action", "updatecustombutton");
        this.sendMessageDelayed(m, LIME.HANDLER_DELAY_MINIMAL_MS);
    }

    public void resetImTable(String imtype, Boolean backuplearning) {
        Message m = new Message();
        m.getData().putString("action", "reset");
        m.getData().putString("im", imtype);
        m.getData().putBoolean("backup", backuplearning);
        this.sendMessageDelayed(m, LIME.HANDLER_DELAY_MINIMAL_MS);
    }


    public void finishLoading(String imtype) {
        Message m = new Message();
        m.getData().putString("action", "finish");
        m.getData().putString("im", imtype);
        this.sendMessageDelayed(m, LIME.HANDLER_DELAY_MINIMAL_MS);
    }
}
