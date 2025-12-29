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
import android.os.Looper;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentTransaction;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.ToggleButton;
import android.util.Log;

import net.toload.main.hd.data.Record;
import net.toload.main.hd.global.LIME;
import net.toload.main.hd.ui.MainActivity;
import net.toload.main.hd.R;
import net.toload.main.hd.data.Im;
import net.toload.main.hd.data.Keyboard;
import net.toload.main.hd.ui.controller.ManageImController;
import net.toload.main.hd.ui.dialog.ManageImAddDialog;
import net.toload.main.hd.ui.dialog.ManageImEditDialog;
import net.toload.main.hd.ui.dialog.ManageImKeyboardDialog;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Fragment that displays and manages IM records for a specific IM table.
 *
 * <p>Provides UI for searching, paging, adding, editing and deleting mapping
 * records. Delegates data operations to `ManageImController` and implements
 * the `ManageImView` contract for controller-driven updates.
 */
public class ManageImFragment extends Fragment implements ManageImView {

    private final String TAG = "ManageImFragment";

    /**
     * The fragment argument representing the section number for this
     * fragment.
     */
    private static final String ARG_SECTION_NUMBER = "section_number";
    private static final String ARG_SECTION_CODE = "section_code";

    private ManageImController manageImController;
    private RecyclerView gridManageIm;

    private Button btnManageImKeyboard;
    private Button btnManageImSearch;
    private Button btnManageImPrevious;
    private Button btnManageImNext;

    private EditText edtManageImSearch;
    private TextView txtNavigationInfo;

    private List<Record> wordlist;
    private List<Keyboard> keyboardlist;

    private int page = 0;
    private int total = 0;
    private boolean searchroot = true;
    private boolean searchreset = false;

    private String prequery = "";

    private String table;
    private Activity activity;
    private ManageImAdapter adapter;


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
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View rootView = inflater.inflate(R.layout.fragment_manage_im, container, false);

        this.activity = this.getActivity();

        if (activity instanceof MainActivity) {
            this.manageImController = ((MainActivity) activity).getManageImController();
            if (this.manageImController != null) {
                this.manageImController.setManageImView(this);
            } else {
                Log.w(TAG, "ManageImController is null; UI operations may fail");
            }
        } else {
            Log.w(TAG, "Activity is not MainActivity; ManageImController unavailable");
        }

        // initial imlist via controller
        List<Im> imkeyboardlist = (manageImController != null) ? manageImController.getImList() : new ArrayList<>();


        this.gridManageIm = rootView.findViewById(R.id.gridManageIm);
        this.gridManageIm.setLayoutManager(new GridLayoutManager(activity, 2));
        this.adapter = new ManageImAdapter();
        this.adapter.setOnItemClickListener((record, position) -> {
            FragmentTransaction ft = getParentFragmentManager().beginTransaction();
            ManageImEditDialog dialog = ManageImEditDialog.newInstance();
            dialog.setFragment(this, record);
            dialog.show(ft, "editdialog");
        });
        this.gridManageIm.setAdapter(this.adapter);

        Button btnManageImAdd = rootView.findViewById(R.id.btnManageImAdd);
        btnManageImAdd.setOnClickListener(v -> {
            FragmentTransaction ft = getParentFragmentManager().beginTransaction();
            ManageImAddDialog dialog = ManageImAddDialog.newInstance(table);
            dialog.setFragment(this);
            dialog.show(ft, "adddialog");
        });

        this.btnManageImKeyboard = rootView.findViewById(R.id.btnManageImKeyboard);
        if(table != null && table.equals(LIME.IM_HS)){
            this.btnManageImKeyboard.setEnabled(false);
        }else{
            this.btnManageImKeyboard.setOnClickListener(v -> {
                FragmentTransaction ft = getParentFragmentManager().beginTransaction();
                ManageImKeyboardDialog dialog = ManageImKeyboardDialog.newInstance();
                dialog.setFragment(this, table);
                dialog.show(ft, "keyboarddialog");
            });
        }

        ToggleButton toggleManageIm = rootView.findViewById(R.id.toggleManageIm);
        toggleManageIm.setOnCheckedChangeListener((buttonView, isChecked) -> {
            searchroot = !isChecked;
            total = 0;
            prequery = "";
            edtManageImSearch.setText("");
            searchword(null);
            searchreset = false;
            btnManageImSearch.setText(getResources().getText(R.string.manage_im_search));
        });

        this.btnManageImNext = rootView.findViewById(R.id.btnManageImNext);
        this.btnManageImNext.setEnabled(false);
        this.btnManageImNext.setOnClickListener(v -> {
            int checkrecord = LIME.IM_MANAGE_DISPLAY_AMOUNT * (page + 1);
            if (checkrecord < total) {
                page++;
            }
            searchword();
  
        });
        this.btnManageImPrevious = rootView.findViewById(R.id.btnManageImPrevious);
        this.btnManageImPrevious.setEnabled(false);
        this.btnManageImPrevious.setOnClickListener(v -> {
            if (page > 0) {
                page--;
            }
            searchword();

        });

        this.edtManageImSearch = rootView.findViewById(R.id.edtManageImSearch);
        this.edtManageImSearch.setOnClickListener(v -> {
            searchreset = false;
            btnManageImSearch.setText(getResources().getText(R.string.manage_im_search));
        });
        this.edtManageImSearch.setOnFocusChangeListener((v, hasFocus) -> {
            if (!hasFocus) {
                InputMethodManager imm = (InputMethodManager) requireActivity().getSystemService(Context.INPUT_METHOD_SERVICE);
                imm.hideSoftInputFromWindow(edtManageImSearch.getWindowToken(), 0);
            }
        });

        this.btnManageImSearch = rootView.findViewById(R.id.btnManageImSearch);
        this.btnManageImSearch.setOnClickListener(v -> {
            if (!searchreset) {
                String query = edtManageImSearch.getText().toString();
                // hide the soft keyboard before search Jeremy 15,6,4
                InputMethodManager imm = (InputMethodManager) requireActivity().getSystemService(Context.INPUT_METHOD_SERVICE);
                imm.hideSoftInputFromWindow(edtManageImSearch.getWindowToken(), 0);
                if (!query.isEmpty() && (prequery == null || !prequery.equals(query) || !searchreset)) {
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
        });

        this.txtNavigationInfo = rootView.findViewById(R.id.txtNavigationInfo);

        // UpdateKeyboard display
        for(Im obj : imkeyboardlist){
            if(obj.getCode().equals(table)){
                btnManageImKeyboard.setText(obj.getDesc());
                break;
            }
        }

        // Diagnostic: ensure table is set before attempting to load records
        Log.i(TAG, "onCreateView: table=" + table + ", imController=" + (manageImController != null));
        if (table == null || table.isEmpty()) {
            Log.e(TAG, "IM table is not set; aborting record load");
            if (activity != null) {
                android.widget.Toast.makeText(activity, R.string.manage_im_error_no_table, Toast.LENGTH_LONG).show();
            }
        } else if (manageImController == null) {
            Log.e(TAG, "ImController is null; cannot load records");
            if (activity != null) {
                android.widget.Toast.makeText(activity, R.string.manage_im_error_no_controller, Toast.LENGTH_LONG).show();
            }
        } else {
            searchword(null);
        }

        return rootView;
    }

    public void searchword(){
        searchword(prequery);
    }

    public void searchword(String curquery){
        int offset = LIME.IM_MANAGE_DISPLAY_AMOUNT * page;
        int limit = LIME.IM_MANAGE_DISPLAY_AMOUNT;

        if (!Objects.equals(curquery, prequery)) {
            page = 0;
        }
        
        if (manageImController != null) {
            manageImController.loadRecordsAsync(table, curquery, searchroot, offset, limit);
        }
        prequery = curquery;
    }

    @Override
    public void onAttach(@NonNull Context context) {
        super.onAttach(context);
        Activity activity = (Activity) context;
        assert getArguments() != null;
        // Set the table early so subsequent lifecycle methods have access to it
        this.table = getArguments().getString(ARG_SECTION_CODE);
        ((MainActivity) activity).onSectionAttached(
                getArguments().getInt(ARG_SECTION_NUMBER));
    }

    @Override
    public void onDestroy() {
        super.onDestroy();


        this.wordlist = null;
        
        if (manageImController != null) {
            manageImController.setManageImView(null);
            manageImController = null;
        }
    }



    public void updateGridView(List<Record> wordlist) {
        // Ensure UI updates happen on the main thread
        if (Looper.myLooper() != Looper.getMainLooper()) {
            if (getActivity() != null) {
                getActivity().runOnUiThread(() -> updateGridView(wordlist));
                return;
            }
        }

        this.wordlist = (wordlist != null) ? wordlist : new ArrayList<>();

        int startrecord = LIME.IM_MANAGE_DISPLAY_AMOUNT * page;
        int endrecord = Math.min(LIME.IM_MANAGE_DISPLAY_AMOUNT * (page + 1), total);

        this.btnManageImPrevious.setEnabled(page > 0);
        this.btnManageImNext.setEnabled(LIME.IM_MANAGE_DISPLAY_AMOUNT * (page + 1) < total);

        if (adapter != null) {
            adapter.submitList(this.wordlist);
            gridManageIm.scrollToPosition(0);
        }

        if (total == 0) {
            Toast.makeText(activity, R.string.no_search_result, Toast.LENGTH_SHORT).show();
        }

        String nav = "0";
        if (total > 0) {
            nav = LIME.format(startrecord + 1) + "-" + LIME.format(endrecord) + " of " + LIME.format(total);
        }

        Log.i(TAG, "updateGridView(): total=" + total + ", page=" + page + ", start=" + startrecord + ", end=" + endrecord + ", wordlistSize=" + (this.wordlist == null ? 0 : this.wordlist.size()));
        this.txtNavigationInfo.setText(nav);

    }

    public void removeRecord(int id){
        if (manageImController != null) {
            manageImController.deleteRecord(this.table, id);
        }
    }

    public void addRecord(String code, int score, String word) {
        if(word != null){
            word = word.trim();
        }
        
        if (manageImController != null) {
            manageImController.addRecord(this.table, code, word, score);
        }
    }

    public void updateRecord(int id, String code, int score, String word) {
        if(word != null){
            word = word.trim();
        }

        if (manageImController != null) {
            manageImController.updateRecord(this.table, id, code, word, score);
        }
    }

    public void updateKeyboard(String keyboard) {
        // Use controller for keyboard operations
        if (keyboardlist == null && manageImController != null) {
            keyboardlist = manageImController.getKeyboardList();
        }
        assert keyboardlist != null;
        for(Keyboard k: keyboardlist){
            if(k.getCode().equals(keyboard)){
                if (manageImController != null) manageImController.setIMKeyboard(table, k);
                btnManageImKeyboard.setText(k.getDesc());
            }
        }
    }

    /**
     * Expose keyboard list to handlers/dialogs
     */
    public List<Keyboard> getKeyboardList() {
        return (manageImController != null) ? manageImController.getKeyboardList() : new java.util.ArrayList<>();
    }

    /**
     * Helper to set IM keyboard via controller
     */
    public void setIMKeyboard(String table, String keyboardCode) {
        if (manageImController != null) {
            // Find the keyboard object and set it
            List<Keyboard> list = manageImController.getKeyboardList();
            for (Keyboard k : list) {
                if (k.getCode().equals(keyboardCode)) {
                    manageImController.setIMKeyboard(table, k);
                    return;
                }
            }
        }
    }
    
    // ========== ManageImView Interface Implementation ==========
    
    @Override
    public void displayRecords(List<Record> records) {
        Log.i(TAG, "displayRecords(): records=" + (records == null ? "null" : records.size()));
        this.wordlist = records;
        updateGridView(records);
    }
    
    @Override
    public void updateRecordCount(int count) {
        this.total = count;
        //updateNavigationInfo();
        if (txtNavigationInfo != null) {
            int startRecord = LIME.IM_MANAGE_DISPLAY_AMOUNT * page;
            int endRecord = Math.min(LIME.IM_MANAGE_DISPLAY_AMOUNT * (page + 1), total);
            txtNavigationInfo.setText(getResources().getString(R.string.manage_im_navigation_info,
                    startRecord + 1, endRecord, total));
        }
    }

    @Override
    public void showAddRecordDialog() {
        FragmentTransaction ft = getParentFragmentManager().beginTransaction();
        ManageImAddDialog dialog = ManageImAddDialog.newInstance(table);
        dialog.setFragment(this);
        dialog.show(ft, "adddialog");
    }

    @Override
    public void showEditRecordDialog(Record record) {
        FragmentTransaction ft = getParentFragmentManager().beginTransaction();
        ManageImEditDialog dialog = ManageImEditDialog.newInstance();
        dialog.setFragment(this, record);
        dialog.show(ft, "editdialog");
    }

    @Override
    public void showDeleteConfirmDialog(long id) {
        // Show confirmation dialog before deleting
        new android.app.AlertDialog.Builder(activity)
            .setTitle(getResources().getString(R.string.dialog_delete_title))
            .setMessage(getResources().getString(R.string.dialog_delete_message))
            .setPositiveButton(getResources().getString(R.string.dialog_confirm), (dialog, which) -> removeRecord((int) id))
            .setNegativeButton(getResources().getString(R.string.dialog_cancel), null)
            .show();
    }

    @Override
    public void refreshRecordList() {
        searchword();
    }
    
    @Override
    public void onError(String message) {
        Log.e(TAG, message);
        // Ensure the loading spinner is hidden on error to avoid a stuck UI
        if (activity != null) {
            android.widget.Toast.makeText(activity, message, android.widget.Toast.LENGTH_LONG).show();
        }
    }


}