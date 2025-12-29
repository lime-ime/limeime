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

package net.toload.main.hd.ui.view;

import android.app.Activity;
import android.content.Context;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentTransaction;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;


import net.toload.main.hd.global.LIME;
import net.toload.main.hd.ui.MainActivity;
import net.toload.main.hd.R;
import net.toload.main.hd.data.Related;
import net.toload.main.hd.ui.controller.ManageImController;
import net.toload.main.hd.ui.dialog.ManageRelatedAddDialog;
import net.toload.main.hd.ui.dialog.ManageRelatedEditDialog;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;




/**
 * Fragment that displays and manages related-phrase entries.
 *
 * <p>This fragment hosts the related-phrase grid, provides search and
 * pagination controls, and delegates data operations to
 * `ManageImController` via the `ManageRelatedView` contract.
 */
public class ManageRelatedFragment extends Fragment implements ManageRelatedView {

    private static final String TAG = "ManageRelatedFragment";
    
    /**
     * The fragment argument representing the section number for this
     * fragment.
     */
    private static final String ARG_SECTION_NUMBER = "section_number";

    private ManageImController manageImController;
    private RecyclerView gridManageRelated;

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
    private ManageRelatedAdapter adapter;

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
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View rootView = inflater.inflate(R.layout.fragment_manage_related, container, false);

        this.activity = this.getActivity();
        
        // Get ManageImController from MainActivity
        if (this.activity instanceof MainActivity) {
            this.manageImController = ((MainActivity) this.activity).getManageImController();
            if (this.manageImController != null) {
                this.manageImController.setManageRelatedView(this);
            }
        }


        this.gridManageRelated = rootView.findViewById(R.id.gridManageRelated);
        this.gridManageRelated.setLayoutManager(new GridLayoutManager(activity, 2));
        this.adapter = new ManageRelatedAdapter(activity);
        this.adapter.setOnItemClickListener((related, position) -> {
            FragmentTransaction ft = getParentFragmentManager().beginTransaction();
            // Create and show the dialog.
            ManageRelatedEditDialog dialog = ManageRelatedEditDialog.newInstance();
            dialog.setFragment(this, related);
            dialog.show(ft, "editdialog");
        });
        this.gridManageRelated.setAdapter(this.adapter);

        Button btnManageRelatedAdd = rootView.findViewById(R.id.btnManageRelatedAdd);
        btnManageRelatedAdd.setOnClickListener(v -> {
            FragmentTransaction ft = getParentFragmentManager().beginTransaction();
            ManageRelatedAddDialog dialog = ManageRelatedAddDialog.newInstance();
            dialog.setFragment(this);
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
        int limit = LIME.IM_MANAGE_DISPLAY_AMOUNT;

        if((curQuery == null && total == 0) || !Objects.equals(curQuery, preQuery)){
            page = 0;
        }
        
        if (manageImController != null) {
            manageImController.loadRelatedPhrases(curQuery, offset, limit);
        }
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
        this.relatedlist = null;
        if (manageImController != null && manageImController.getSearchServer() != null) {
            manageImController.getSearchServer().initialCache();
        }
    }


    public void updateGridView(List<Related> relatedlist) {
        // Ensure UI updates happen on the main thread
        if (android.os.Looper.myLooper() != android.os.Looper.getMainLooper()) {
            if (getActivity() != null) {
                getActivity().runOnUiThread(() -> updateGridView(relatedlist));
                return;
            }
        }

        this.relatedlist = (relatedlist != null) ? relatedlist : new ArrayList<>();

        int startRecord = LIME.IM_MANAGE_DISPLAY_AMOUNT * page;
        int endRecord = Math.min(LIME.IM_MANAGE_DISPLAY_AMOUNT * (page + 1), total);

        this.btnManageRelatedPrevious.setEnabled(page > 0);
        this.btnManageRelatedNext.setEnabled(LIME.IM_MANAGE_DISPLAY_AMOUNT * (page + 1) < total);

        if (adapter != null) {
            adapter.submitList(this.relatedlist);
            gridManageRelated.scrollToPosition(0);
        }

        if (total == 0) {
            Toast.makeText(activity, R.string.no_search_result, Toast.LENGTH_SHORT).show();
        }

        String nav = "0";
        if (total > 0) {
            nav = LIME.format(startRecord + 1) + "-" + LIME.format(endRecord) + " of " + LIME.format(total);
        }

        this.txtNavigationInfo.setText(nav);
    }

    public void removeRelated(int id){

        // Remove from the temp list
        for(int i = 0 ; i < total ; i++){
           if(id== this.relatedlist.get(i).getId()){
               this.relatedlist.remove(i);
               break;
           }
        }

        if (manageImController != null) {
            manageImController.deleteRelatedPhrase(id);
            // Refresh the grid after delete
            searchRelated();
        }
    }

    public void addRelated(String pword, String cword, int score) {
        if (manageImController != null) {
            manageImController.addRelatedPhrase(pword, cword, score);
            // Refresh the grid after add
            searchRelated();
        }
    }

    public void updateRelated(int id, String pword, String cword, int score) {
        if (manageImController != null) {
            manageImController.updateRelatedPhrase(id, pword, cword, score);
            // Refresh the grid after update to show new score
            searchRelated();
        }
    }
    
    // ========== ManageRelatedView Interface Implementation ==========
    
    @Override
    public void displayRelatedPhrases(List<Related> phrases) {
        this.relatedlist = phrases;
        // Ensure UI updates on main thread
        if (getActivity() != null) {
            getActivity().runOnUiThread(() -> updateGridView(phrases));
        } else {
            updateGridView(phrases);
        }
    }
    
    @Override
    public void updatePhraseCount(int count) {
        this.total = count;
        updateNavigationInfo();
    }
    
    @Override
    public void showAddPhraseDialog() {
        FragmentTransaction ft = getParentFragmentManager().beginTransaction();
        ManageRelatedAddDialog dialog = ManageRelatedAddDialog.newInstance();
        dialog.setFragment(this);
        dialog.show(ft, "adddialog");
    }
    
    @Override
    public void showEditPhraseDialog(Related phrase) {
        FragmentTransaction ft = getParentFragmentManager().beginTransaction();
        ManageRelatedEditDialog dialog = ManageRelatedEditDialog.newInstance();
        dialog.setFragment(this, phrase);
        dialog.show(ft, "editdialog");
    }
    
    @Override
    public void showDeleteConfirmDialog(long id) {
        // Show confirmation dialog before deleting
        new android.app.AlertDialog.Builder(activity)
            .setTitle(getResources().getString(R.string.dialog_delete_title))
            .setMessage(getResources().getString(R.string.dialog_delete_message))
            .setPositiveButton(getResources().getString(R.string.dialog_confirm), (dialog, which) -> removeRelated((int) id))
            .setNegativeButton(getResources().getString(R.string.dialog_cancel), null)
            .show();
    }
    
    @Override
    public void refreshPhraseList() {
        searchRelated();
    }

    

    @Override
    public void onError(String message) {
        android.util.Log.e(TAG, message);
        if (activity != null) {
            Toast.makeText(activity, message, Toast.LENGTH_LONG).show();
        }
    }

    
    private void updateNavigationInfo() {
        if (txtNavigationInfo != null) {
            int startrecord = LIME.IM_MANAGE_DISPLAY_AMOUNT * page;
            int endrecord = Math.min(LIME.IM_MANAGE_DISPLAY_AMOUNT * (page + 1), total);
            txtNavigationInfo.setText(getResources().getString(R.string.manage_related_navigation_info, 
                startrecord + 1, endrecord, total));
        }
    }

}