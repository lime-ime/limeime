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

import android.app.Activity;
import android.content.ContentValues;
import android.content.Context;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentTransaction;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.GridView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;


import net.toload.main.hd.global.LIME;
import net.toload.main.hd.MainActivity;
import net.toload.main.hd.R;
import net.toload.main.hd.SearchServer;
import net.toload.main.hd.data.Related;
import net.toload.main.hd.limedb.LimeDB;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;




/**
 * A placeholder fragment containing a simple view.
 */
public class ManageRelatedFragment extends Fragment {

    /**
     * The fragment argument representing the section number for this
     * fragment.
     */
    private static final String ARG_SECTION_NUMBER = "section_number";

    private SearchServer SearchSrv = null;
    private GridView gridManageRelated;

    private Button btnManageRelatedSearch;
    private Button btnManageRelatedPrevious;
    private Button btnManageRelatedNext;

    private EditText edtManageRelatedSearch;
    private TextView txtNavigationInfo;

    private List<Related> relatedlist;

    private int page = 0;
    private int total = 0;
    private boolean searchReset = false;

    private String preQuery = "";

    private Activity activity;
    private ManageRelatedHandler handler;
    private ManageRelatedAdapter adapter;

    private Thread ManageRelatedthread;

    private LimeDB datasource;

    private ProgressBar progressBar;

    // AD
    //private RelativeLayout adBannerLayout;
    //private VpadnBanner vpadnBanner = null;

    /**
     * Returns a new instance of this fragment for the given section
     * number.
     */
    public static ManageRelatedFragment newInstance(int sectionNumber) {
        ManageRelatedFragment fragment = new ManageRelatedFragment();
        Bundle args = new Bundle();
                args.putInt(ARG_SECTION_NUMBER, sectionNumber);
        fragment.setArguments(args);
        return fragment;
    }

    public ManageRelatedFragment() {
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View rootView = inflater.inflate(R.layout.fragment_manage_related, container, false);

        this.activity = this.getActivity();
        this.datasource = new LimeDB(this.activity);
        this.SearchSrv = new SearchServer(this.activity);

        this.handler = new ManageRelatedHandler(this);

        this.progressBar = rootView.findViewById(R.id.loading_spinner);
        //LIMEPreferenceManager mLIMEPref = new LIMEPreferenceManager(activity);

        this.gridManageRelated = rootView.findViewById(R.id.gridManageRelated);
        this.gridManageRelated.setOnItemClickListener((parent, view, position, id) -> {
           // try {
                //datasource.open();
                Related w = datasource.getRelated(id);
                //datasource.close();
                FragmentTransaction ft = getParentFragmentManager().beginTransaction();

                // Create and show the dialog.
                ManageRelatedEditDialog dialog = ManageRelatedEditDialog.newInstance();
                dialog.setHandler(handler, w);
                dialog.show(ft, "editdialog");
            /*} catch (SQLException e) {
                Log.e(TAG, "Error in operation", e);
            }*/
        });

        Button btnManageRelatedAdd = rootView.findViewById(R.id.btnManageRelatedAdd);
        btnManageRelatedAdd.setOnClickListener(v -> {
            FragmentTransaction ft = getParentFragmentManager().beginTransaction();
            ManageRelatedAddDialog dialog = ManageRelatedAddDialog.newInstance();
            dialog.setHandler(handler);
            dialog.show(ft, "adddialog");
        });


        this.btnManageRelatedNext = rootView.findViewById(R.id.btnManageRelatedNext);
        this.btnManageRelatedNext.setEnabled(false);
        this.btnManageRelatedNext.setOnClickListener(v -> {
            int checkrecord = LIME.IM_MANAGE_DISPLAY_AMOUNT * (page + 1);
            if (checkrecord < total) {
                page++;
            }
            searchRelated();
            //updateGridView(relatedlist);
        });
        this.btnManageRelatedPrevious = rootView.findViewById(R.id.btnManageRelatedPrevious);
        this.btnManageRelatedPrevious.setEnabled(false);
        this.btnManageRelatedPrevious.setOnClickListener(v -> {
            if (page > 0) {
                page--;
            }
            searchRelated();
            //updateGridView(relatedlist);
        });

        this.edtManageRelatedSearch = rootView.findViewById(R.id.edtManageRelatedSearch);
        this.edtManageRelatedSearch.setOnClickListener(v -> {
            searchReset = false;
            btnManageRelatedSearch.setText(getResources().getText(R.string.manage_related_search));
        });
        this.edtManageRelatedSearch.setOnFocusChangeListener((v, hasFocus) -> {
            if (!hasFocus) {
                InputMethodManager imm = (InputMethodManager) requireActivity().getSystemService(Context.INPUT_METHOD_SERVICE);
                imm.hideSoftInputFromWindow(edtManageRelatedSearch.getWindowToken(), 0);
            }
        });

        this.btnManageRelatedSearch = rootView.findViewById(R.id.btnManageRelatedSearch);
        this.btnManageRelatedSearch.setOnClickListener(v -> {
            if (!searchReset) {
                String query = edtManageRelatedSearch.getText().toString();
                // hide the soft keyboard before search Jeremy 15,6,4
                InputMethodManager imm = (InputMethodManager) requireActivity().getSystemService(Context.INPUT_METHOD_SERVICE);
                imm.hideSoftInputFromWindow(edtManageRelatedSearch.getWindowToken(), 0);
                if (!query.isEmpty() && (preQuery == null || !preQuery.equals(query) || !searchReset)) {
                    query = query.trim();
                    searchRelated(query);
                }
                searchReset = true;
                btnManageRelatedSearch.setText(getResources().getText(R.string.manage_related_reset));
            } else {
                total = 0;
                searchRelated(null);
                edtManageRelatedSearch.setText("");
                searchReset = false;
                btnManageRelatedSearch.setText(getResources().getText(R.string.manage_related_search));
            }
        });

        this.txtNavigationInfo = rootView.findViewById(R.id.txtNavigationInfo);

        searchRelated(null);

        return rootView;
    }

    public void searchRelated(){
        searchRelated(preQuery);
    }

    public void searchRelated(String curQuery){

        int offset = LIME.IM_MANAGE_DISPLAY_AMOUNT * page;

        if((curQuery == null && total == 0) || !Objects.equals(curQuery, preQuery)){
            total = datasource.getRelatedSize(curQuery);
            page = 0;
        }
        if(ManageRelatedthread != null && ManageRelatedthread.isAlive()){
            handler.removeCallbacks(ManageRelatedthread);
        }
        ManageRelatedthread = new Thread(new ManageRelatedRunnable(handler, activity, curQuery,
                                                                LIME.IM_MANAGE_DISPLAY_AMOUNT, offset));
        ManageRelatedthread.start();
        preQuery = curQuery;
    }

    @Override
    public void onAttach(@NonNull Context context) {
        super.onAttach(context);
        Activity activity = (Activity) context;
        assert getArguments() != null;
        ((MainActivity) activity).onSectionAttached(
                getArguments().getInt(ARG_SECTION_NUMBER));
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if(this.ManageRelatedthread != null){
            this.handler.removeCallbacks(ManageRelatedthread);
        }
        if(this.progressBar != null && this.progressBar.getVisibility() == View.VISIBLE){
            this.progressBar.setVisibility(View.GONE);
        }
        this.relatedlist = null;
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

    public void updateGridView(List<Related> relatedlist){

        this.relatedlist = relatedlist;

        int startrecord = LIME.IM_MANAGE_DISPLAY_AMOUNT * page;
        int endrecord = LIME.IM_MANAGE_DISPLAY_AMOUNT * (page + 1);

        this.btnManageRelatedPrevious.setEnabled(page > 0);

        if(endrecord <= total){
            this.btnManageRelatedNext.setEnabled(true);
        }else{
            this.btnManageRelatedNext.setEnabled(false);
            endrecord = total;
        }

        if(total > 0){
            if(this.adapter == null){
                this.adapter = new ManageRelatedAdapter(this.activity, relatedlist);
                this.gridManageRelated.setAdapter(this.adapter);
            }else{
                this.adapter.setList(relatedlist);
                this.adapter.notifyDataSetChanged();
                this.gridManageRelated.setSelection(0);
            }
        }else{
            this.adapter.setList(new ArrayList<>());
            this.adapter.notifyDataSetChanged();
            this.gridManageRelated.setSelection(0);
            Toast.makeText(activity, R.string.no_search_result, Toast.LENGTH_SHORT).show();
        }

        String nav = "0";

        if(total > 0){
            nav = LIME.format(startrecord + 1) + "-" + LIME.format(endrecord);
            nav = nav + " of " + LIME.format(total);
        }

        this.txtNavigationInfo.setText(nav);
        cancelProgress();

    }

    public void removeRelated(int id){

        // Remove from the temp list
        for(int i = 0 ; i < total ; i++){
           if(id== this.relatedlist.get(i).getId()){
               this.relatedlist.remove(i);
               break;
           }
        }

        // Remove from the database using parameterized query
        datasource.deleteRecord(LIME.DB_TABLE_RELATED, LIME.DB_COLUMN_ID + " = ?", new String[]{String.valueOf(id)});
      
        total--;
        searchRelated();
        //updateGridView(this.relatedlist);
    }

    public void addRelated(String pword, String cword, int score) {

        int hasRelatedCheck = datasource.hasRelated(pword, cword);
        if(hasRelatedCheck == 0){
            // Add to database using parameterized query
            ContentValues values = new ContentValues();
            values.put(LIME.DB_RELATED_COLUMN_PWORD, pword);
            values.put(LIME.DB_RELATED_COLUMN_CWORD, cword);
            values.put(LIME.DB_RELATED_COLUMN_USERSCORE, 1); // Default userscore
            values.put(LIME.DB_RELATED_COLUMN_BASESCORE, score);
            datasource.addRecord(LIME.DB_TABLE_RELATED, values);

            total++;
            searchRelated();

        }else{
            if(hasRelatedCheck == 9999999){
                Toast.makeText(activity, R.string.manage_related_format_error, Toast.LENGTH_SHORT).show();
            }
            else{
                Toast.makeText(activity, R.string.manage_related_duplicated, Toast.LENGTH_SHORT).show();
            }
        }

    }

    public void updateRelated(int id, String pword, String cword, int score) {

            // remove from temp list
            for(int i = 0 ; i < total ; i++){
                if(id== this.relatedlist.get(i).getId()){
                    Related check = this.relatedlist.get(i);
                    check.setPword(pword);
                    check.setCword(cword);
                    check.setBasescore(score);
                    this.relatedlist.remove(i);
                    this.relatedlist.add(i, check);
                    break;
                }
            }

            // Update record in the database
        ContentValues values = new ContentValues();
        values.put(LIME.DB_RELATED_COLUMN_PWORD, pword);
        values.put(LIME.DB_RELATED_COLUMN_CWORD, cword);
        values.put(LIME.DB_RELATED_COLUMN_BASESCORE, score);

        String whereClause = LIME.DB_RELATED_COLUMN_ID + " = ?";
        String[] whereArgs = new String[] { String.valueOf(id) };

        datasource.updateRecord(LIME.DB_TABLE_RELATED, values, whereClause, whereArgs);

            searchRelated();

    }

}