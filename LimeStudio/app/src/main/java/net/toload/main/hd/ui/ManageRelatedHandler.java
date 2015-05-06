package net.toload.main.hd.ui;

import android.os.Handler;
import android.os.Message;

import net.toload.main.hd.data.Related;

import java.util.List;

/**
 * Created by Art Hung on 2015/4/26.
 */
public class ManageRelatedHandler extends Handler {

    private List<Related> relatedlist;
    private ManageRelatedFragment fragment = null;

    public ManageRelatedHandler(ManageRelatedFragment fragment) {
        this.fragment = fragment;
    }

    @Override
    public void handleMessage(Message msg) {
        String action = msg.getData().getString("action");
        if(action.equals("progress")){
            fragment.showProgress();
        }else if(action.equals("add")){
            String pword = msg.getData().getString("pword");
            String cword = msg.getData().getString("cword");
            int score = msg.getData().getInt("score");
            fragment.addRelated(pword, cword, score);
        }else if(action.equals("update")){
            int id = msg.getData().getInt("id");
            String pword = msg.getData().getString("pword");
            String cword = msg.getData().getString("cword");
            int score = msg.getData().getInt("score");
            fragment.updateRelated(id, pword, cword, score);
        }else if(action.equals("remove")){
            int id = msg.getData().getInt("id");
            fragment.removeRelated(id);
        } else {
            fragment.updateGridView(this.relatedlist);
        }
    }

    public void showProgress() {
        Message m = new Message();
                m.getData().putString("action", "progress");
        this.sendMessageDelayed(m, 1);
    }

    public void updateGridView(List<Related> related) {
        this.relatedlist = related;
        Message m = new Message();
                m.getData().putString("action", "display");
        this.sendMessageDelayed(m, 1);
    }

    public void removeRelated(int id) {
        Message m = new Message();
        m.getData().putString("action", "remove");
        m.getData().putInt("id", id);
        this.sendMessageDelayed(m, 1);
    }

    public void updateRelated(int id, String pword, String cword, int score) {
        Message m = new Message();
        m.getData().putString("action", "update");
        m.getData().putInt("id", id);
        m.getData().putString("pword", pword);
        m.getData().putString("cword", cword);
        m.getData().putInt("score", score);
        this.sendMessageDelayed(m, 1);
    }

    public void addRelated(String pword, String cword, int score) {
        Message m = new Message();
        m.getData().putString("action", "add");
        m.getData().putString("pword", pword);
        m.getData().putString("cword", cword);
        m.getData().putInt("score", score);
        this.sendMessageDelayed(m, 1);
    }

}
