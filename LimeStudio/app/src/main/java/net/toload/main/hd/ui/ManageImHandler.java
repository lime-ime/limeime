package net.toload.main.hd.ui;

import android.os.Handler;
import android.os.Message;

import net.toload.main.hd.ManageImFragment;
import net.toload.main.hd.data.Word;

import java.util.List;

/**
 * Created by Art Hung on 2015/4/26.
 */
public class ManageImHandler extends Handler {

    private List<Word> wordlist;
    private ManageImFragment fragment = null;

    public ManageImHandler(ManageImFragment fragment) {
        this.fragment = fragment;
    }

    @Override
    public void handleMessage(Message msg) {
        String action = msg.getData().getString("action");
        if(action.equals("progress")){
            fragment.showProgress();
        }else{
            fragment.updateGridView(this.wordlist);
        }
    }

    public void showProgress() {
        Message m = new Message();
                m.getData().putString("action", "progress");
        this.sendMessageDelayed(m, 1000);
    }

    public void updateGridView(List<Word> words) {
        this.wordlist = words;
        Message m = new Message();
                m.getData().putString("action", "display");
        this.sendMessageDelayed(m, 1000);
    }


}
