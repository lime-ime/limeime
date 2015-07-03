/*
 *
 *  *
 *  **    Copyright 2015, The LimeIME Open Source Project
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
            case "update":
                int progress = msg.getData().getInt("progress");
                activity.updateProgress(progress);
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

    public void update(int progress) {
        Message m = new Message();
        m.getData().putString("action", "update");
        m.getData().putInt("progress", progress);
        this.sendMessageDelayed(m, 1);
    }

    public void show(String message) {
        Message m = new Message();
        m.getData().putString("action", "show");
        m.getData().putString("message", message);
        this.sendMessageDelayed(m, 1);
    }


}
