package net.toload.main.hd.ui;

import android.os.Handler;
import android.os.Message;

import net.toload.main.hd.data.Word;

import java.util.List;


public class ManageImHandler extends Handler {

    private List<Word> wordlist;
    private ManageImFragment fragment = null;

    public ManageImHandler(ManageImFragment fragment) {
        this.fragment = fragment;
    }

    @Override
    public void handleMessage(Message msg) {
        String action = msg.getData().getString("action");
        switch (action) {
            case "progress":
                fragment.showProgress();
                break;
            case "add": {
                String code = msg.getData().getString("code");
                int score = msg.getData().getInt("score");
                String word = msg.getData().getString("word");
                fragment.addWord(code, score, word);
                break;
            }
            case "update": {
                int id = msg.getData().getInt("id");
                String code = msg.getData().getString("code");
                int score = msg.getData().getInt("score");
                String word = msg.getData().getString("word");
                fragment.updateWord(id, code, score, word);
                break;
            }
            case "keyboard":
                String keyboard = msg.getData().getString("keyboard");
                fragment.updateKeyboard(keyboard);
                break;
            case "related": {
                String code = msg.getData().getString("code");
                fragment.updateRelated(code);
                break;
            }
            case "remove": {
                int id = msg.getData().getInt("id");
                fragment.removeWord(id);
                break;
            }
            default:
                fragment.updateGridView(this.wordlist);
                break;
        }
    }

    public void showProgress() {
        Message m = new Message();
                m.getData().putString("action", "progress");
        this.sendMessageDelayed(m, 1);
    }

    public void updateGridView(List<Word> words) {
        this.wordlist = words;
        Message m = new Message();
                m.getData().putString("action", "display");
        this.sendMessageDelayed(m, 1);
    }

    public void removeWord(int id) {
        Message m = new Message();
        m.getData().putString("action", "remove");
        m.getData().putInt("id", id);
        this.sendMessageDelayed(m, 1);
    }

    public void updateWord(int id, String code, int score, String word) {
        Message m = new Message();
        m.getData().putString("action", "update");
        m.getData().putInt("id", id);
        m.getData().putString("code", code);
        m.getData().putInt("score", score);
        m.getData().putString("word", word);
        this.sendMessageDelayed(m, 1);
    }

    public void addWord(String code, int score, String word) {
        Message m = new Message();
        m.getData().putString("action", "add");
        m.getData().putString("code", code);
        m.getData().putInt("score", score);
        m.getData().putString("word", word);
        this.sendMessageDelayed(m, 1);
    }

    public void updateKeyboardButton(String keyboard) {
        Message m = new Message();
        m.getData().putString("action", "keyboard");
        m.getData().putString("keyboard", keyboard);
        this.sendMessageDelayed(m, 1);
    }


    public void updateRelated(String code) {
        Message m = new Message();
        m.getData().putString("action", "related");
        m.getData().putString("code", code);
        this.sendMessageDelayed(m, 1);
    }
}
