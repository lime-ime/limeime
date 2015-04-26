package net.toload.main.hd.ui;

import android.os.Handler;
import android.os.Message;

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
        }else if(action.equals("add")){
            String code = msg.getData().getString("code");
            String code3r = msg.getData().getString("code3r");
            String word = msg.getData().getString("word");
            fragment.addWord(code, code3r, word);
        }else if(action.equals("update")){
            int id = msg.getData().getInt("id");
            String code = msg.getData().getString("code");
            String code3r = msg.getData().getString("code3r");
            String word = msg.getData().getString("word");
            fragment.updateWord(id, code, code3r, word);
        }else if(action.equals("keyboard")){
            String keyboard = msg.getData().getString("keyboard");
            fragment.updateKeyboard(keyboard);
        }else if(action.equals("remove")){
            int id = msg.getData().getInt("id");
            fragment.removeWord(id);
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

    public void removeWord(int id) {
        Message m = new Message();
        m.getData().putString("action", "remove");
        m.getData().putInt("id", id);
        this.sendMessageDelayed(m, 1000);
    }

    public void updateWord(int id, String code, String code3r, String word) {
        Message m = new Message();
        m.getData().putString("action", "update");
        m.getData().putInt("id", id);
        m.getData().putString("code", code);
        m.getData().putString("code3r", code3r);
        m.getData().putString("word", word);
        this.sendMessageDelayed(m, 1);
    }

    public void addWord(String code, String code3r, String word) {
        Message m = new Message();
        m.getData().putString("action", "add");
        m.getData().putString("code", code);
        m.getData().putString("code3r", code3r);
        m.getData().putString("word", word);
        this.sendMessageDelayed(m, 1);
    }

    public void updateKeyboardButton(String keyboard) {
        Message m = new Message();
        m.getData().putString("action", "keyboard");
        m.getData().putString("keyboard", keyboard);
        this.sendMessageDelayed(m, 1000);
    }


}
