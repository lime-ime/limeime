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

import android.app.Activity;
import android.content.Context;
import android.os.Bundle;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentTransaction;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
import android.widget.AdapterView;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.GridView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.ToggleButton;
import net.toload.main.hd.Lime;
import net.toload.main.hd.MainActivity;
import net.toload.main.hd.R;
import net.toload.main.hd.SearchServer;
import net.toload.main.hd.data.Im;
import net.toload.main.hd.data.Keyboard;
import net.toload.main.hd.data.Word;
import net.toload.main.hd.global.LIMEPreferenceManager;
import net.toload.main.hd.limedb.LimeDB;

import java.util.ArrayList;
import java.util.List;

/// A placeholder fragment containing a simple view.
public class ManageImFragment extends Fragment {

    /**
     * The fragment argument representing the section number for this
     * fragment.
     */
    private static final String ARG_SECTION_NUMBER = "section_number";
    private static final String ARG_SECTION_CODE = "section_code";

    private SearchServer SearchSrv = null;
    private GridView gridManageIm;

    private Button btnManageImKeyboard;
    private Button btnManageImSearch;
    private Button btnManageImPrevious;
    private Button btnManageImNext;

    private EditText edtManageImSearch;
    private TextView txtNavigationInfo;

    private List<Word> wordlist;
    private List<Keyboard> keyboardlist;

    private int page = 0;
    private int total = 0;
    private boolean searchroot = true;
    private boolean searchreset = false;

    private String prequery = "";

    private String table;
    private Activity activity;
    private ManageImHandler handler;
    private ManageImAdapter adapter;

    private Thread manageimthread;

    private LimeDB datasource;

    private ProgressBar progressBar;

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
        View rootView = inflater.inflate(R.layout.fragment_manage_im, container, false);

        this.activity = this.getActivity();
        this.datasource = new LimeDB(this.activity);
        this.SearchSrv = new SearchServer(this.activity);

        this.handler = new ManageImHandler(this);
        LIMEPreferenceManager mLIMEPref = new LIMEPreferenceManager(activity);

        // initial imlist
        List<Im> imkeyboardlist = new ArrayList<Im>();
        imkeyboardlist = datasource.getIm(null, Lime.IM_TYPE_KEYBOARD);

        this.progressBar = rootView.findViewById(R.id.loading_spinner);

        this.gridManageIm = (GridView) rootView.findViewById(R.id.gridManageIm);
        this.gridManageIm.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                //try {
                //datasource.open();
                Word w = datasource.getWord(table, id);
                //datasource.close();
                FragmentTransaction ft = getParentFragmentManager().beginTransaction();

                // Create and show the dialog.
                ManageImEditDialog dialog = ManageImEditDialog.newInstance(table);
                dialog.setHandler(handler, w);
                dialog.show(ft, "editdialog");
                //} catch (SQLException e) {
                //    e.printStackTrace();
                //}
            }
        });

        Button btnManageImAdd = (Button) rootView.findViewById(R.id.btnManageImAdd);
        btnManageImAdd.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                FragmentTransaction ft = getParentFragmentManager().beginTransaction();
                ManageImAddDialog dialog = ManageImAddDialog.newInstance(table);
                                    dialog.setHandler(handler);
                dialog.show(ft, "adddialog");
            }
        });

        this.btnManageImKeyboard = (Button) rootView.findViewById(R.id.btnManageImKeyboard);
        if(table != null && table.equals(Lime.IM_HS)){
            this.btnManageImKeyboard.setEnabled(false);
        }else{
            this.btnManageImKeyboard.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    FragmentTransaction ft = getParentFragmentManager().beginTransaction();
                    ManageImKeyboardDialog dialog = ManageImKeyboardDialog.newInstance();
                    dialog.setHandler(handler, table);
                    dialog.show(ft, "keyboarddialog");
                }
            });
        }

        ToggleButton toggleManageIm = (ToggleButton) rootView.findViewById(R.id.toggleManageIm);
        toggleManageIm.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                if (isChecked) {
                    searchroot = false;
                } else {
                    searchroot = true;
                }
                total = 0;
                prequery = "";
                edtManageImSearch.setText("");
                searchword(null);
                searchreset = false;
                btnManageImSearch.setText(getResources().getText(R.string.manage_im_search));
            }
        });

        this.btnManageImNext = (Button) rootView.findViewById(R.id.btnManageImNext);
        this.btnManageImNext.setEnabled(false);
        this.btnManageImNext.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                int checkrecord = Lime.IM_MANAGE_DISPLAY_AMOUNT * (page + 1);
                if (checkrecord < total) {
                    page++;
                }
                searchword();
                //updateGridView(wordlist);
            }
        });
        this.btnManageImPrevious = (Button) rootView.findViewById(R.id.btnManageImPrevious);
        this.btnManageImPrevious.setEnabled(false);
        this.btnManageImPrevious.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (page > 0) {
                    page--;
                }
                searchword();
                //updateGridView(wordlist);
            }
        });

        this.edtManageImSearch = (EditText) rootView.findViewById(R.id.edtManageImSearch);
        this.edtManageImSearch.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                searchreset = false;
                btnManageImSearch.setText(getResources().getText(R.string.manage_im_search));
            }
        });
        this.edtManageImSearch.setOnFocusChangeListener(new View.OnFocusChangeListener() {
            @Override
            public void onFocusChange(View v, boolean hasFocus) {
                if (!hasFocus) {
                    InputMethodManager imm = (InputMethodManager) getActivity().getSystemService(Context.INPUT_METHOD_SERVICE);
                    imm.hideSoftInputFromWindow(edtManageImSearch.getWindowToken(), 0);
                }
            }
        });

        this.btnManageImSearch = (Button) rootView.findViewById(R.id.btnManageImSearch);
        this.btnManageImSearch.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (!searchreset) {
                    String query = edtManageImSearch.getText().toString();
                    // hide the soft keyboard before search Jeremy 15,6,4
                    InputMethodManager imm = (InputMethodManager) getActivity().getSystemService(Context.INPUT_METHOD_SERVICE);
                    imm.hideSoftInputFromWindow(edtManageImSearch.getWindowToken(), 0);
                    if (query != null && query.length() > 0 &&
                            ( prequery == null || !prequery.equals(query) || !searchreset) ) {
                        query = query.trim();
                        searchword(query);

                    }
                    searchreset = true;
                    btnManageImSearch.setText(getResources().getText(R.string.manage_im_reset));
                } else {
                    total = 0;
                    searchword(null);
                    edtManageImSearch.setText("");
                    searchreset = false;
                    btnManageImSearch.setText(getResources().getText(R.string.manage_im_search));
                }
            }
        });

        this.txtNavigationInfo = (TextView) rootView.findViewById(R.id.txtNavigationInfo);

        // UpdateKeyboard display
        for(Im obj : imkeyboardlist){
            if(obj.getCode().equals(table)){
                btnManageImKeyboard.setText(obj.getDesc());
                break;
            }
        }

        searchword(null);

        return rootView;
    }

    public void searchword(){
        searchword(prequery);
    }

    public void searchword(String curquery){

        int offset = Lime.IM_MANAGE_DISPLAY_AMOUNT * page;

        if((curquery == null && total == 0) || curquery != prequery ){
            total = datasource.getWordSize(table, curquery, searchroot);
            page = 0;
           /* try {
                datasource.open();
                datasource.close();
            } catch (SQLException e) {
                e.printStackTrace();
            }*/
        }
        if(manageimthread != null && manageimthread.isAlive()){
            handler.removeCallbacks(manageimthread);
        }
        manageimthread = new Thread(new ManageImRunnable(handler, activity, table, curquery, searchroot,
                                                                            Lime.IM_MANAGE_DISPLAY_AMOUNT, offset));
        manageimthread.start();
        prequery = curquery;
    }

    @Override
    public void onAttach(Context context) {
        super.onAttach(context);
        Activity activity = (Activity) context;
        ((MainActivity) activity).onSectionAttached(
                getArguments().getInt(ARG_SECTION_NUMBER));

        this.table = getArguments().getString(ARG_SECTION_CODE);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();

        if(this.manageimthread != null){
            this.handler.removeCallbacks(manageimthread);
        }
        if(this.progressBar != null && this.progressBar.getVisibility() == View.VISIBLE){
            this.progressBar.setVisibility(View.GONE);
        }
        this.wordlist = null;
        this.SearchSrv.initialCache();
    }

    public void showProgress(){
        if(this.progressBar != null && this.progressBar.getVisibility() != View.VISIBLE){
            this.progressBar.setVisibility(View.VISIBLE);
        }
    }

    public void cancelProgress(){
        if(this.progressBar != null && this.progressBar.getVisibility() == View.VISIBLE){
            this.progressBar.setVisibility(View.GONE);
        }
    }

    public void updateGridView(List<Word> wordlist){

        this.wordlist = wordlist;

        int startrecord = Lime.IM_MANAGE_DISPLAY_AMOUNT * page;
        int endrecord = Lime.IM_MANAGE_DISPLAY_AMOUNT * (page + 1);

        if(page > 0){
            this.btnManageImPrevious.setEnabled(true);
        }else{
            this.btnManageImPrevious.setEnabled(false);
        }

        if(endrecord <= total){
            this.btnManageImNext.setEnabled(true);
        }else{
            this.btnManageImNext.setEnabled(false);
            endrecord = total;
        }

        if(total > 0){
            if(this.adapter == null){
                this.adapter = new ManageImAdapter(this.activity, wordlist);
                this.gridManageIm.setAdapter(this.adapter);
            }else{
                this.adapter.setList(wordlist);
                this.adapter.notifyDataSetChanged();
                this.gridManageIm.setSelection(0);
            }
        }else{
            if(this.adapter == null){
                this.adapter = new ManageImAdapter(this.activity, new ArrayList<>());
            }
            this.adapter.setList(new ArrayList<>());
            this.adapter.notifyDataSetChanged();
            this.gridManageIm.setSelection(0);
            Toast.makeText(activity, R.string.no_search_result, Toast.LENGTH_SHORT).show();
        }

        String nav = "0";

        if(total > 0){
            nav = Lime.format(startrecord + 1) + "-" + Lime.format(endrecord);
            nav += " of " + Lime.format(total);
        }

        this.txtNavigationInfo.setText(nav);
        cancelProgress();

    }

    public void removeWord(int id){

        // Remove from the temp list
        for(int i = 0 ; i < total ; i++){
           if(id== this.wordlist.get(i).getId()){
               this.wordlist.remove(i);
               break;
           }
        }

        // Remove from the database
        String removesql = "DELETE FROM " + this.table + " WHERE " + Lime.DB_COLUMN_ID + " = '" + id + "'";

        datasource.remove(removesql);
        /*try {
            datasource.open();
            datasource.close();
        } catch (SQLException e) {
            e.printStackTrace();
        }*/

        total--;
        searchword();
        //updateGridView(this.wordlist);
    }

    public void addWord(String code, int score, String word) {

        if(word != null){
            word = word.trim();
        }

        // Add to database
        Word obj = new Word();
             obj.setCode(code);
             obj.setCode3r("");
             obj.setWord(word);
             obj.setBasescore(0);
             obj.setScore(score);

        //Jeremy '15,6,6 the record may already exist, use original add or update mapping function in LIMEDB instead.
        // code3r information will also generated for phonetic table.
        datasource.addOrUpdateMappingRecord(this.table, code, word, score);
        //String insertsql = Word.getInsertQuery(this.table, obj);
        //datasource.insert(insertsql);


       /* try {
            datasource.open();
            datasource.close();
        } catch (SQLException e) {
            e.printStackTrace();
        }*/

        total++;
        searchword();
        // Add to temp list
        /*page = 0;
        this.wordlist.add(0,obj);
        updateGridView(this.wordlist);*/
    }

    public void updateWord(int id, String code, int score, String word) {

        if(word != null){
            word = word.trim();
        }

        // remove from temp list
        for(int i = 0 ; i < total ; i++){
            if(id== this.wordlist.get(i).getId()){
                Word check = this.wordlist.get(i);
                     check.setCode(code);
                     check.setCode3r("");
                     check.setWord(word);
                     check.setScore(score);
                this.wordlist.remove(i);
                this.wordlist.add(i, check);
                break;
            }
        }

        //Jeremy '15,6,6  use original add or update mapping function in LIMEDB instead.
        // code3r information will also generated for phonetic table.
        datasource.addOrUpdateMappingRecord(this.table, code, word, score);
/*
        // Update record in the database
        String updatesql = "UPDATE " + this.table + " SET ";
                updatesql += Lime.DB_COLUMN_CODE + " = \"" + Lime.formatSqlValue(code) + "\", ";
                if(this.table.equals("phonetic"))  //Jeremy '15,6,6 add no tone code (code3r) with code ripped 3467
                    updatesql += Lime.DB_COLUMN_CODE3R + " = \"" + Lime.formatSqlValue(code.replaceAll("[3467 ]", "")) + "\", ";
                updatesql += Lime.DB_COLUMN_SCORE + " = \"" + score + "\", ";
                updatesql += Lime.DB_COLUMN_WORD + " = \"" + Lime.formatSqlValue(word) + "\" ";
                updatesql += " WHERE " + Lime.DB_COLUMN_ID + " = \"" + id + "\"";

        //datasource.update(updatesql);
      */
        /*try {
            datasource.open();
            datasource.close();
        } catch (SQLException e) {
            e.printStackTrace();
        }*/
        searchword();
        //updateGridView(this.wordlist);
    }

    public void updateKeyboard(String keyboard) {

        if(keyboardlist == null){
            keyboardlist = datasource.getKeyboard();
           /* try {
                datasource.open();
                datasource.close();
            } catch (SQLException e) {
                e.printStackTrace();
            }*/
        }
        for(Keyboard k: keyboardlist){
            if(k.getCode().equals(keyboard)){
                datasource.setImKeyboard(table, k);
                btnManageImKeyboard.setText(k.getDesc());
                /*try {
                    datasource.open();
                    datasource.close();
                } catch (SQLException e) {
                    e.printStackTrace();
                }*/
            }
        }

    }

    //public void updateRelated(String updatecode) {
    //    datasource.updateSimilarCodeListInRelatedColumn(table, updatecode);
    //}

}