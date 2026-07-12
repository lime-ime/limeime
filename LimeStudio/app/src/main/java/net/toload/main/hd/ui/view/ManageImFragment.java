/*
 *
 *  *
 *  **    Copyright 2026, The LimeIME Open Source Project
 *  **
 *  **    Project Url: http://github.com/lime-ime/limeime/
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
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.os.Looper;

import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.text.Editable;
import android.text.TextWatcher;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;
import android.util.Log;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButtonToggleGroup;
import com.google.android.material.color.MaterialColors;

import net.toload.main.hd.data.Record;
import net.toload.main.hd.global.LIME;
import net.toload.main.hd.ui.LIMESettings;
import net.toload.main.hd.R;
import net.toload.main.hd.data.ImConfig;
import net.toload.main.hd.data.Keyboard;
import net.toload.main.hd.ui.controller.ManageImController;
import net.toload.main.hd.ui.dialog.ManageImAddSheet;
import net.toload.main.hd.ui.dialog.ManageImEditSheet;
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

    private Button btnManageImPrevious;
    private Button btnManageImNext;

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

        // Back navigation toolbar
        MaterialToolbar toolbar = rootView.findViewById(R.id.manage_im_toolbar);
        if (toolbar != null) {
            toolbar.setNavigationOnClickListener(v -> {
                Fragment parent = getParentFragment();
                if (parent != null) {
                    parent.getChildFragmentManager().popBackStack();
                }
            });

            toolbar.inflateMenu(R.menu.menu_manage_im);
            tintToolbarMenuIcons(toolbar);

            toolbar.setOnMenuItemClickListener(item -> {
                if (item.getItemId() == R.id.action_manage_im_add) {
                    ManageImAddSheet sheet = ManageImAddSheet.newInstance();
                    sheet.setFragment(this);
                    sheet.show(getParentFragmentManager(), "addsheet");
                    return true;
                }
                return false;
            });
        }

        // Handle system back gesture and hardware back button.
        // The toolbar ← alone is unreliable near the left-edge gesture zone on Android 10+;
        // OnBackPressedCallback intercepts both the swipe-back gesture and the KEYCODE_BACK key.
        requireActivity().getOnBackPressedDispatcher().addCallback(
                getViewLifecycleOwner(),
                new OnBackPressedCallback(true) {
                    @Override
                    public void handleOnBackPressed() {
                        Fragment parent = getParentFragment();
                        if (parent != null) {
                            parent.getChildFragmentManager().popBackStack();
                        }
                    }
                });

        this.activity = this.getActivity();

        if (activity instanceof LIMESettings) {
            this.manageImController = ((LIMESettings) activity).getManageImController();
            if (this.manageImController != null) {
                this.manageImController.setManageImView(this);
            } else {
                Log.w(TAG, "ManageImController is null; UI operations may fail");
            }
        } else {
            Log.w(TAG, "Activity is not LIMESettings; ManageImController unavailable");
        }

        // Push pagination bar above the activity's BottomNavigationView so it isn't clipped
        View paginationBar = rootView.findViewById(R.id.pagination_bar);
        View bottomNav = requireActivity().findViewById(R.id.main_bottom_nav);
        if (paginationBar != null && bottomNav != null) {
            bottomNav.post(() -> {
                int navHeight = bottomNav.getHeight();
                if (navHeight > 0 && paginationBar.getLayoutParams() instanceof ViewGroup.MarginLayoutParams) {
                    ViewGroup.MarginLayoutParams lp = (ViewGroup.MarginLayoutParams) paginationBar.getLayoutParams();
                    lp.bottomMargin = navHeight;
                    paginationBar.setLayoutParams(lp);
                }
            });
        }

        this.gridManageIm = rootView.findViewById(R.id.gridManageIm);
        // TODO: add ItemTouchHelper for swipe-to-edit / swipe-to-delete (future pass)
        androidx.recyclerview.widget.LinearLayoutManager lm =
                new androidx.recyclerview.widget.LinearLayoutManager(activity);
        this.gridManageIm.setLayoutManager(lm);
        this.gridManageIm.addItemDecoration(
                new androidx.recyclerview.widget.DividerItemDecoration(activity, lm.getOrientation()));
        this.adapter = new ManageImAdapter();
        this.adapter.setOnItemClickListener((record, position) -> {
            ManageImEditSheet sheet = ManageImEditSheet.newInstance();
            sheet.setFragment(this, record);
            sheet.show(getParentFragmentManager(), "editsheet");
        });
        this.gridManageIm.setAdapter(this.adapter);

        // Large heading below toolbar
        TextView tvImLabelHeading = rootView.findViewById(R.id.tv_im_label_heading);

        // Segmented control: 字根 / 文字
        MaterialButtonToggleGroup toggleGroupManageIm = rootView.findViewById(R.id.toggleGroupManageIm);
        toggleGroupManageIm.addOnButtonCheckedListener((group, checkedId, isChecked) -> {
            if (isChecked) {
                searchroot = (checkedId == R.id.btnFilterCode);
                total = 0;
                prequery = "";
                searchword(null);
            }
        });

        // Inline search bar
        EditText edtManageImSearch = rootView.findViewById(R.id.edtManageImSearch);
        edtManageImSearch.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
            @Override public void afterTextChanged(Editable s) {
                String q = s != null ? s.toString().trim() : "";
                page = 0;
                searchword(q.isEmpty() ? null : q);
            }
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

        this.txtNavigationInfo = rootView.findViewById(R.id.txtNavigationInfo);

        // initial imConfigFullNamelist via controller
        List<ImConfig> imConfigFullNamelist = (manageImController != null) ? manageImController.getImConfigFullNameList() : new ArrayList<>();

        // Set large heading to the IM's display name (toolbar title stays empty)
        if (tvImLabelHeading != null && table != null) {
            for (ImConfig imConfig : imConfigFullNamelist) {
                if (imConfig.getCode().equals(table)) {
                    tvImLabelHeading.setText(imConfig.getDesc());
                    break;
                }
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

    private void tintToolbarMenuIcons(MaterialToolbar toolbar) {
        int tint = MaterialColors.getColor(toolbar, com.google.android.material.R.attr.colorOnSurface);
        for (int i = 0; i < toolbar.getMenu().size(); i++) {
            Drawable icon = toolbar.getMenu().getItem(i).getIcon();
            if (icon != null) {
                icon.mutate().setTint(tint);
            }
        }
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
        ((LIMESettings) activity).onSectionAttached(
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

        int totalPages = (total + LIME.IM_MANAGE_DISPLAY_AMOUNT - 1) / LIME.IM_MANAGE_DISPLAY_AMOUNT;
        if (totalPages < 1) totalPages = 1;
        String nav = ("第 " + (page + 1) + " / "
                + ((total + LIME.IM_MANAGE_DISPLAY_AMOUNT - 1) / LIME.IM_MANAGE_DISPLAY_AMOUNT == 0 ? 1
                        : (total + LIME.IM_MANAGE_DISPLAY_AMOUNT - 1) / LIME.IM_MANAGE_DISPLAY_AMOUNT)
                + " 頁 · " + String.format(java.util.Locale.US, "%,d", total) + " 筆");

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
     * Returns the IM's currently configured keyboard, or null if none is set.
     * Exposed for dialogs that need to highlight the current selection.
     */
    public Keyboard getCurrentKeyboard() {
        return (manageImController != null) ? manageImController.getCurrentKeyboard(table) : null;
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
        if (txtNavigationInfo != null) {
            int totalPages = (total + LIME.IM_MANAGE_DISPLAY_AMOUNT - 1) / LIME.IM_MANAGE_DISPLAY_AMOUNT;
            if (totalPages < 1) totalPages = 1;
            txtNavigationInfo.setText(("第 " + (page + 1) + " / "
                + ((total + LIME.IM_MANAGE_DISPLAY_AMOUNT - 1) / LIME.IM_MANAGE_DISPLAY_AMOUNT == 0 ? 1
                        : (total + LIME.IM_MANAGE_DISPLAY_AMOUNT - 1) / LIME.IM_MANAGE_DISPLAY_AMOUNT)
                + " 頁 · " + String.format(java.util.Locale.US, "%,d", total) + " 筆"));
        }
    }

    @Override
    public void showAddRecordDialog() {
        ManageImAddSheet sheet = ManageImAddSheet.newInstance();
        sheet.setFragment(this);
        sheet.show(getParentFragmentManager(), "addsheet");
    }

    @Override
    public void showEditRecordDialog(Record record) {
        ManageImEditSheet sheet = ManageImEditSheet.newInstance();
        sheet.setFragment(this, record);
        sheet.show(getParentFragmentManager(), "editsheet");
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
