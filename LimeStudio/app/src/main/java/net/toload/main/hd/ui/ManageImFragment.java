package net.toload.main.hd.ui;

import android.app.Activity;
import android.app.ProgressDialog;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentTransaction;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.GridView;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.ToggleButton;

import net.toload.main.hd.Lime;
import net.toload.main.hd.MainActivity;
import net.toload.main.hd.R;
import net.toload.main.hd.data.DataSource;
import net.toload.main.hd.data.Im;
import net.toload.main.hd.data.Keyboard;
import net.toload.main.hd.data.Word;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/**
 * Fragment used for managing interactions for and presentation of a navigation drawer.
 * See the <a href="https://developer.android.com/design/patterns/navigation-drawer.html#Interaction">
 * design guidelines</a> for a complete explanation of the behaviors implemented here.
 */

/**
 * A placeholder fragment containing a simple view.
 */
public class ManageImFragment extends Fragment {


    /**
     * The fragment argument representing the section number for this
     * fragment.
     */
    private static final String ARG_SECTION_NUMBER = "section_number";
    private static final String ARG_SECTION_CODE = "section_code";

    private GridView gridManageIm;

    private ToggleButton toggleManageIm;

    private Button btnManageImAdd;
    private Button btnManageImKeyboard;
    private Button btnManageImSearch;
    private Button btnManageImPrevious;
    private Button btnManageImNext;

    private EditText edtManageImSearch;
    private TextView txtNavigationInfo;

    private List<Im> imkeyboardlist;
    private List<Word> wordlist;
    private List<Keyboard> keyboardlist;

    private int page = 0;
    private boolean searchroot = true;
    private boolean searchreset = false;

    private String prequery = "";

    private String code;
    private Activity activity;
    private ManageImHandler handler;
    private ManageImAdapter adapter;

    private Thread manageimthread;

    private DataSource datasource;

    private ProgressDialog progress;

    /**
     * Returns a new instance of this fragment for the given section
     * number.
     */
    public static ManageImFragment newInstance(int sectionNumber, String code) {
        ManageImFragment fragment = new ManageImFragment();
        Bundle args = new Bundle();
                args.putInt(ARG_SECTION_NUMBER, sectionNumber);
                args.putString(ARG_SECTION_CODE, code);
        fragment.setArguments(args);
        return fragment;
    }

    public ManageImFragment() {
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View root = inflater.inflate(R.layout.fragment_manage_im, container, false);

        this.activity = this.getActivity();
        this.datasource = new DataSource(this.activity);
        this.handler = new ManageImHandler(this);

        // initial imlist
        imkeyboardlist = new ArrayList<Im>();
         try {
               datasource.open();
               imkeyboardlist = datasource.getIm(null, Lime.IM_TYPE_KEYBOARD);
               datasource.close();
         } catch (SQLException e) {
              e.printStackTrace();
         }

        this.progress = new ProgressDialog(this.activity);
        this.progress.setCancelable(false);
        this.progress.setMessage(getResources().getString(R.string.manage_im_loading));

        this.gridManageIm = (GridView) root.findViewById(R.id.gridManageIm);
        this.gridManageIm.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                try {
                    datasource.open();
                    Word w = datasource.getWord(code, id);
                    datasource.close();
                    FragmentTransaction ft = getFragmentManager().beginTransaction();

                    // Create and show the dialog.
                    ManageImWordEditDialog dialog = ManageImWordEditDialog.newInstance();
                    dialog.setHandler(handler, w);
                    dialog.show(ft, "editdialog");
                } catch (SQLException e) {
                    e.printStackTrace();
                }
            }
        });

        this.btnManageImAdd = (Button) root.findViewById(R.id.btnManageImAdd);
        this.btnManageImAdd.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                FragmentTransaction ft = getFragmentManager().beginTransaction();
                ManageImWordAddDialog dialog = ManageImWordAddDialog.newInstance();
                                    dialog.setHandler(handler);
                dialog.show(ft, "adddialog");
            }
        });

        this.btnManageImKeyboard = (Button) root.findViewById(R.id.btnManageImKeyboard);
        this.btnManageImKeyboard.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                FragmentTransaction ft = getFragmentManager().beginTransaction();
                ManageImKeyboardDialog dialog = ManageImKeyboardDialog.newInstance();
                                       dialog.setHandler(handler, code);
                dialog.show(ft, "keyboarddialog");
            }
        });

        this.toggleManageIm = (ToggleButton) root.findViewById(R.id.toggleManageIm);
        this.toggleManageIm.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                if (isChecked) {
                    searchroot = false;
                } else {
                    searchroot = true;
                }
                prequery = "";
                edtManageImSearch.setText("");
            }
        });

        this.btnManageImNext = (Button) root.findViewById(R.id.btnManageImNext);
        this.btnManageImNext.setEnabled(false);
        this.btnManageImNext.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                int checkrecord = Lime.IM_MANAGE_DISPLAY_AMOUNT * (page + 1);
                if (checkrecord < wordlist.size()) {
                    page++;
                }
                updateGridView(wordlist);
            }
        });
        this.btnManageImPrevious = (Button) root.findViewById(R.id.btnManageImPrevious);
        this.btnManageImPrevious.setEnabled(false);
        this.btnManageImPrevious.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (page > 0) {
                    page--;
                }
                updateGridView(wordlist);
            }
        });

        this.edtManageImSearch = (EditText) root.findViewById(R.id.edtManageImSearch);
        this.edtManageImSearch.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                searchreset = false;
                btnManageImSearch.setText(getResources().getText(R.string.manage_im_search));
            }
        });

        this.btnManageImSearch = (Button) root.findViewById(R.id.btnManageImSearch);
        this.btnManageImSearch.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (!searchreset) {
                    String query = edtManageImSearch.getText().toString();
                    if (query != null && query.length() > 0 && (prequery == null || !prequery.equals(query))) {
                        query = query.trim();
                        searchword(query);
                    }
                    searchreset = true;
                    btnManageImSearch.setText(getResources().getText(R.string.manage_im_reset));
                } else {
                    searchword(null);
                    edtManageImSearch.setText("");
                    searchreset = false;
                    btnManageImSearch.setText(getResources().getText(R.string.manage_im_search));
                }
            }
        });

        this.txtNavigationInfo = (TextView) root.findViewById(R.id.txtNavigationInfo);

        // UpdateKeyboard display
        for(Im obj : imkeyboardlist){
            if(obj.getCode().equals(code)){
                btnManageImKeyboard.setText(obj.getDesc());
                break;
            }
        }

        searchword(null);

        return root;
    }

    public void searchword(String curquery){
        if(manageimthread != null && manageimthread.isAlive()){
            handler.removeCallbacks(manageimthread);
        }
        page = 0;
        manageimthread = new Thread(new ManageImRunnable(handler, activity, code, curquery, searchroot));
        manageimthread.start();
        prequery = curquery;
    }

    @Override
    public void onAttach(Activity activity) {
        super.onAttach(activity);
        ((MainActivity) activity).onSectionAttached(
                getArguments().getInt(ARG_SECTION_NUMBER));

        this.code = getArguments().getString(ARG_SECTION_CODE);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if(this.manageimthread != null){
            this.handler.removeCallbacks(manageimthread);
        }
        if(this.progress.isShowing()){
            this.progress.cancel();
        }
        this.wordlist = null;
    }

    public void showProgress(){
        if(!this.progress.isShowing()){
            this.progress.show();
        }
    }

    public void cancelProgress(){
        if(this.progress.isShowing()){
            this.progress.cancel();
        }
    }

    public void updateGridView(List<Word> wordlist){

        this.wordlist = wordlist;
        List<Word> templist = new ArrayList<Word>();

        int startrecord = Lime.IM_MANAGE_DISPLAY_AMOUNT * page;
        int endrecord = Lime.IM_MANAGE_DISPLAY_AMOUNT * (page + 1);

        if(page > 0){
            this.btnManageImPrevious.setEnabled(true);
        }else{
            this.btnManageImPrevious.setEnabled(false);
        }

        if(endrecord <= this.wordlist.size()){
            this.btnManageImNext.setEnabled(true);
        }else{
            this.btnManageImNext.setEnabled(false);
        }

        if(this.wordlist.size() > 0){

            for(int i = startrecord; i < endrecord ; i++){
                if(i >= this.wordlist.size()){
                    endrecord = this.wordlist.size();
                    break;
                }
                Word w = this.wordlist.get(i);
                templist.add(w);
            }
        }else{
            Toast.makeText(activity, R.string.no_search_result, Toast.LENGTH_SHORT).show();
        }

        if(this.adapter == null){
            this.adapter = new ManageImAdapter(this.activity, templist);
            this.gridManageIm.setAdapter(this.adapter);
        }else{
            this.adapter.setList(templist);
            this.adapter.notifyDataSetChanged();
        }

        String nav = "0";

        if(this.wordlist.size() > 0){
            nav = Lime.format(startrecord + 1) + "-" + Lime.format(endrecord);
            nav += " of " + Lime.format(this.wordlist.size());
        }

        this.txtNavigationInfo.setText(nav);
        cancelProgress();

    }

    public void removeWord(int id){
        for(int i = 0 ; i < this.wordlist.size() ; i++){
           if(id== this.wordlist.get(i).getId()){
               this.wordlist.remove(i);
               break;
           }
        }

        String removesql = "DELETE FROM " + this.code + " WHERE " + Lime.DB_COLUMN_ID + " = '" + id + "'";

        try {
            datasource.open();
            datasource.remove(removesql);
            datasource.close();
        } catch (SQLException e) {
            e.printStackTrace();
        }
        updateGridView(this.wordlist);
    }

    public void addWord(String code, String code3r, String word) {

        Word obj = new Word();
             obj.setCode(code);
             obj.setCode3r(code3r);
             obj.setWord(word);
             obj.setBasescore(0);
             obj.setScore(1);

        String insertsql = Word.getInsertQuery(this.code, obj);

        try {
            datasource.open();
            datasource.insert(insertsql);
            datasource.close();
        } catch (SQLException e) {
            e.printStackTrace();
        }

        searchword(prequery);
    }

    public void updateWord(int id, String code, String code3r, String word) {

        for(int i = 0 ; i < this.wordlist.size() ; i++){
            if(id== this.wordlist.get(i).getId()){
                Word check = this.wordlist.get(i);
                     check.setCode(code);
                     check.setCode3r(code3r);
                     check.setWord(word);
                this.wordlist.remove(i);
                this.wordlist.add(i, check);
                break;
            }
        }

        String updatesql = "UPDATE " + this.code + " SET ";
                updatesql += Lime.DB_COLUMN_CODE + " = \"" + Lime.formatSqlValue(code) + "\", ";
                if(!code3r.isEmpty()){
                    updatesql += Lime.DB_COLUMN_CODE3R + " = \"" + Lime.formatSqlValue(code3r) + "\", ";
                }
                updatesql += Lime.DB_COLUMN_WORD + " = \"" + Lime.formatSqlValue(word) + "\" ";
                updatesql += " WHERE " + Lime.DB_COLUMN_ID + " = \"" + id + "\"";

        try {
            datasource.open();
            datasource.update(updatesql);
            datasource.close();
        } catch (SQLException e) {
            e.printStackTrace();
        }
        updateGridView(this.wordlist);
    }

    public void updateKeyboard(String keyboard) {

        if(keyboardlist == null){
            try {
                datasource.open();
                keyboardlist = datasource.getKeyboard();
                datasource.close();
            } catch (SQLException e) {
                e.printStackTrace();
            }
        }
        for(Keyboard k: keyboardlist){
            if(k.getCode().equals(keyboard)){
                try {
                    datasource.open();
                    datasource.setImKeyboard(code, k);
                    datasource.close();
                    btnManageImKeyboard.setText(k.getDesc());
                } catch (SQLException e) {
                    e.printStackTrace();
                }
            }
        }

    }
}