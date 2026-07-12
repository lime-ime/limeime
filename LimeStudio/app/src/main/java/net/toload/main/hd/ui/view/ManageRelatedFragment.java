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
import android.text.Editable;
import android.text.TextWatcher;

import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import androidx.recyclerview.widget.RecyclerView;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.color.MaterialColors;


import net.toload.main.hd.global.LIME;
import net.toload.main.hd.ui.LIMESettings;
import net.toload.main.hd.R;
import net.toload.main.hd.data.Related;
import net.toload.main.hd.ui.controller.ManageImController;
import net.toload.main.hd.ui.dialog.ManageRelatedAddSheet;
import net.toload.main.hd.ui.dialog.ManageRelatedEditSheet;

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

    private Button btnManageRelatedPrevious;
    private Button btnManageRelatedNext;

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

        // Back navigation toolbar
        MaterialToolbar toolbar = rootView.findViewById(R.id.manage_related_toolbar);
        if (toolbar != null) {
            toolbar.setNavigationOnClickListener(v -> {
                Fragment parent = getParentFragment();
                if (parent != null) {
                    parent.getChildFragmentManager().popBackStack();
                }
            });

            toolbar.inflateMenu(R.menu.menu_manage_related);
            tintToolbarMenuIcons(toolbar);

            toolbar.setOnMenuItemClickListener(item -> {
                if (item.getItemId() == R.id.action_manage_related_add) {
                    ManageRelatedAddSheet sheet = ManageRelatedAddSheet.newInstance();
                    sheet.setFragment(this);
                    sheet.show(getParentFragmentManager(), "addsheet");
                    return true;
                }
                return false;
            });
        }

        // Inline search bar
        EditText edtManageRelatedSearch = rootView.findViewById(R.id.edtManageRelatedSearch);
        if (edtManageRelatedSearch != null) {
            edtManageRelatedSearch.addTextChangedListener(new TextWatcher() {
                @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
                @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
                @Override public void afterTextChanged(Editable s) {
                    String q = s != null ? s.toString().trim() : "";
                    page = 0;
                    searchRelated(q.isEmpty() ? null : q);
                }
            });
        }

        // Handle system back gesture and hardware back button.
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
        
        // Get ManageImController from LIMESettings
        if (this.activity instanceof LIMESettings) {
            this.manageImController = ((LIMESettings) this.activity).getManageImController();
            if (this.manageImController != null) {
                this.manageImController.setManageRelatedView(this);
            }
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

        this.gridManageRelated = rootView.findViewById(R.id.gridManageRelated);
        androidx.recyclerview.widget.LinearLayoutManager lm =
                new androidx.recyclerview.widget.LinearLayoutManager(activity);
        this.gridManageRelated.setLayoutManager(lm);
        this.gridManageRelated.addItemDecoration(
                new androidx.recyclerview.widget.DividerItemDecoration(activity, lm.getOrientation()));
        this.adapter = new ManageRelatedAdapter(activity);
        this.adapter.setOnItemClickListener((related, position) -> {
            ManageRelatedEditSheet sheet = ManageRelatedEditSheet.newInstance();
            sheet.setFragment(this, related);
            sheet.show(getParentFragmentManager(), "editsheet");
        });
        this.gridManageRelated.setAdapter(this.adapter);

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

        // TODO: add ItemTouchHelper for swipe-to-edit / swipe-to-delete (future pass)
        this.txtNavigationInfo = rootView.findViewById(R.id.txtNavigationInfo);

        searchRelated(null);

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
        ((LIMESettings) activity).onSectionAttached(
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

        int totalPages = (total + LIME.IM_MANAGE_DISPLAY_AMOUNT - 1) / LIME.IM_MANAGE_DISPLAY_AMOUNT;
        if (totalPages < 1) totalPages = 1;
        String nav = "第 " + (page + 1) + " / " + totalPages + " 頁 · "
                + String.format(java.util.Locale.US, "%,d", total) + " 筆";

        this.txtNavigationInfo.setText(nav);
    }

    public void removeRelated(int id){
        if (this.relatedlist != null) {
            for (int i = 0; i < this.relatedlist.size(); i++) {
                if (id == this.relatedlist.get(i).getIdAsInt()) {
                    this.relatedlist.remove(i);
                    break;
                }
            }
        }
        if (manageImController != null) {
            manageImController.deleteRelatedPhrase(id);
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
        ManageRelatedAddSheet sheet = ManageRelatedAddSheet.newInstance();
        sheet.setFragment(this);
        sheet.show(getParentFragmentManager(), "addsheet");
    }

    @Override
    public void showEditPhraseDialog(Related phrase) {
        ManageRelatedEditSheet sheet = ManageRelatedEditSheet.newInstance();
        sheet.setFragment(this, phrase);
        sheet.show(getParentFragmentManager(), "editsheet");
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
            int totalPages = (total + LIME.IM_MANAGE_DISPLAY_AMOUNT - 1) / LIME.IM_MANAGE_DISPLAY_AMOUNT;
            if (totalPages < 1) totalPages = 1;
            txtNavigationInfo.setText("第 " + (page + 1) + " / " + totalPages + " 頁 · "
                    + String.format(java.util.Locale.US, "%,d", total) + " 筆");
        }
    }

}
