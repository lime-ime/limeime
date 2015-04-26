package net.toload.main.hd.ui;

import android.app.Activity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.TextView;

import net.toload.main.hd.R;
import net.toload.main.hd.data.Word;

import java.util.List;

/**
 * Created by Art Hung on 2015/4/26.
 */
public class ManageImAdapter extends BaseAdapter {

    List<Word> wordlist;

    Activity activity;
    LayoutInflater mInflater;

    public ManageImAdapter(Activity activity,
                             List<Word> wordlist) {
        this.activity = activity;
        this.wordlist = wordlist;
        this.mInflater = LayoutInflater.from(this.activity);
    }

    @Override
    public int getCount() {
        return wordlist.size();
    }

    @Override
    public Object getItem(int position) {
        return wordlist.get(position);
    }

    public List<Word> getList(){
        return wordlist;
    }

    public void setList(List<Word> list){
        wordlist = list;
    }

    @Override
    public View getView(final int position, View convertView, ViewGroup parent) {

        final ViewHolder holder;

        int type = getItemViewType(position);

        if (convertView == null) {
            convertView = mInflater.inflate(R.layout.word, null);
            holder = new ViewHolder();
            holder.txtWord = (TextView)convertView.findViewById(R.id.txtWord);
            holder.txtCode = (TextView)convertView.findViewById(R.id.txtCode);

            convertView.setTag(holder);
        } else {
            holder = (ViewHolder)convertView.getTag();
        }

        Word w = wordlist.get(position);
        if(w != null){
        	/*holder.chkItemDatetWorde.setText(hwresult.getGenerateDateTWorde());
        	holder.chkItemDatetWorde.setOnCheckedChangeListener(new OnCheckedChangeListener(){
				@Override
				public void onCheckedChanged(CompoundButton arg0, boolean arg1) {
					hwlist.get(position).setCheck(arg1);
				}});*/
            String wordtext = w.getWord();
            if(wordtext.length() > 4){
                wordtext = wordtext.substring(0,3) + "...";
            }
            holder.txtCode.setText(w.getCode());
            holder.txtWord.setText(wordtext);
        }

        return convertView;

    }

    static class ViewHolder{
        TextView txtWord;
        TextView txtCode;
    }

    @Override
    public long getItemId(int position) {
        return wordlist.get(position).getId();
    }


}
