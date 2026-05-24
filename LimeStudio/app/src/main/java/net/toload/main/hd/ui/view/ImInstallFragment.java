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
import android.app.AlertDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.provider.OpenableColumns;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.preference.PreferenceManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.switchmaterial.SwitchMaterial;

import net.toload.main.hd.R;
import net.toload.main.hd.global.LIME;
import net.toload.main.hd.ui.LIMESettings;
import net.toload.main.hd.ui.controller.ManageImController;
import net.toload.main.hd.ui.controller.SetupImController;

import com.google.android.material.appbar.MaterialToolbar;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * Fragment showing expandable per-IM download/import cards.
 * Replaces ImListFragment under Tab 1 (輸入法).
 */
public class ImInstallFragment extends Fragment {

    private static final String TAG = "ImInstallFragment";

    private SetupImController setupImController;
    private ManageImController manageImController;
    private Activity activity;
    private RecyclerView recyclerView;
    private ImFamilyAdapter adapter;
    private List<ImFamily> currentFamilies;

    // File picker launchers
    private ActivityResultLauncher<Intent> limedbLauncher;
    private ActivityResultLauncher<Intent> txtLauncher;

    // State for pending picker result
    private String pendingTableName;
    private boolean pendingIsRelated;

    public static ImInstallFragment newInstance() {
        return new ImInstallFragment();
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        limedbLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() == Activity.RESULT_OK && result.getData() != null) {
                        Uri uri = result.getData().getData();
                        if (uri != null) {
                            Activity act = activity;
                            SetupImController ctrl = setupImController;
                            String tbl = pendingTableName;
                            boolean rel = pendingIsRelated;
                            boolean restore = getRestorePref(tbl);
                            new Thread(() -> {
                                File file = saveUriToFile(uri, act);
                                if (file != null && ctrl != null) {
                                    if (rel) ctrl.importZippedDbRelated(file);
                                    else ctrl.importZippedDb(file, tbl, restore);
                                    if (act != null) act.runOnUiThread(() -> onInstallComplete(tbl));
                                }
                            }).start();
                        }
                    }
                });

        txtLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() == Activity.RESULT_OK && result.getData() != null) {
                        Uri uri = result.getData().getData();
                        if (uri != null) {
                            Activity act = activity;
                            SetupImController ctrl = setupImController;
                            String tbl = pendingTableName;
                            boolean restore = getRestorePref(tbl);
                            new Thread(() -> {
                                File file = saveUriToFile(uri, act);
                                if (file != null && ctrl != null) {
                                    ctrl.importTxtTable(file, tbl, restore,
                                            () -> onInstallComplete(tbl));
                                }
                            }).start();
                        }
                    }
                });
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        activity = getActivity();

        if (activity instanceof LIMESettings) {
            setupImController = ((LIMESettings) activity).getSetupImController();
            manageImController = ((LIMESettings) activity).getManageImController();
        } else {
            Log.w(TAG, "Activity is not LIMESettings; controllers unavailable");
        }

        View rootView = inflater.inflate(R.layout.fragment_im_install, container, false);

        // Toolbar with back navigation and refresh action
        MaterialToolbar toolbar = rootView.findViewById(R.id.im_install_toolbar);
        toolbar.setNavigationOnClickListener(v -> {
            Fragment host = getParentFragment();
            if (host != null) {
                host.getChildFragmentManager().popBackStack();
            }
        });
        toolbar.inflateMenu(R.menu.im_install_menu);
        toolbar.setOnMenuItemClickListener(item -> {
            if (item.getItemId() == R.id.action_refresh) {
                loadFamilyListAsync();
                return true;
            }
            return false;
        });

        recyclerView = rootView.findViewById(R.id.im_install_list);
        recyclerView.setLayoutManager(new LinearLayoutManager(requireContext()));
        ScrollableTabHelper.applyToRecyclerView(activity, recyclerView);

        // Load installed state async, then set adapter
        loadFamilyListAsync();

        return rootView;
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        activity = null;
        setupImController = null;
        manageImController = null;
        recyclerView = null;
        adapter = null;
        currentFamilies = null;
    }

    // -------- Async family list loader --------

    private void loadFamilyListAsync() {
        final Activity act = activity;
        final ManageImController ctrl = manageImController;
        final RecyclerView rv = recyclerView;
        new Thread(() -> {
            List<ImFamily> families = buildFamilyList();
            if (ctrl != null) {
                for (ImFamily family : families) {
                    // Check if the IM is registered in the `im` config table (matches IM List).
                    // Bundled-DB record-count alone gives false positives for tables like 倉頡
                    // that have seed records but were never registered as installed IMs.
                    boolean inConfig = false;
                    java.util.List<net.toload.main.hd.data.ImConfig> cfgList = ctrl.getImConfigFullNameList();
                    if (cfgList != null) {
                        for (net.toload.main.hd.data.ImConfig cfg : cfgList) {
                            if (cfg != null && family.tableName.equals(cfg.getCode())) {
                                inConfig = true;
                                break;
                            }
                        }
                    }
                    family.isInstalled = inConfig;
                }
            }
            if (act == null || rv == null) return;
            act.runOnUiThread(() -> {
                if (!isAdded()) return;
                currentFamilies = families;
                adapter = new ImFamilyAdapter(families);
                rv.setAdapter(adapter);
                ScrollableTabHelper.refreshRecyclerViewScrollbar(rv);
            });
        }).start();
    }

    // -------- Restore-learning preference --------

    private boolean getRestorePref(String tableName) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(requireContext());
        return prefs.getBoolean("restore_on_import_" + tableName, true);
    }

    private void setRestorePref(String tableName, boolean value) {
        PreferenceManager.getDefaultSharedPreferences(requireContext())
                .edit()
                .putBoolean("restore_on_import_" + tableName, value)
                .apply();
    }

    // -------- File picker launchers --------

    private void launchLimedbPicker(String tableName, boolean isRelated) {
        pendingTableName = tableName;
        pendingIsRelated = isRelated;
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("*/*");
        limedbLauncher.launch(intent);
    }

    private void launchTxtPicker(String tableName) {
        pendingTableName = tableName;
        pendingIsRelated = false;
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("*/*");
        txtLauncher.launch(intent);
    }

    // -------- URI → File helper --------

    private File saveUriToFile(Uri uri, Activity act) {
        if (act == null) return null;
        try (InputStream inputStream = act.getContentResolver().openInputStream(uri)) {
            if (inputStream == null) return null;
            String fileName = getFileName(uri, act);
            File file = new File(act.getCacheDir(), fileName);
            try (OutputStream outputStream = new FileOutputStream(file)) {
                byte[] buffer = new byte[LIME.BUFFER_SIZE_1KB];
                int length;
                while ((length = inputStream.read(buffer)) > 0) {
                    outputStream.write(buffer, 0, length);
                }
            }
            return file;
        } catch (Exception e) {
            Log.e(TAG, "Error saving file from URI", e);
            return null;
        }
    }

    private String getFileName(Uri uri, Activity act) {
        if (act == null) return "tmpfile";
        String result = null;
        if ("content".equals(uri.getScheme())) {
            try (Cursor cursor = act.getContentResolver().query(uri, null, null, null, null)) {
                if (cursor != null && cursor.moveToFirst()) {
                    int nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                    if (nameIndex != -1) {
                        result = cursor.getString(nameIndex);
                    }
                }
            }
        }
        if (result == null) {
            result = uri.getPath();
            if (result != null) {
                int cut = result.lastIndexOf('/');
                if (cut != -1) result = result.substring(cut + 1);
            } else {
                result = "tmpfile";
            }
        }
        return result;
    }

    // -------- Data model --------

    private static class CloudVariant {
        final String label;
        final String count;
        final String fileSize;
        final String url;

        CloudVariant(String label, String count, String fileSize, String url) {
            this.label = label;
            this.count = count;
            this.fileSize = fileSize;
            this.url = url;
        }
    }

    private static class ImFamily {
        final String tableName;
        final String displayTitle;
        final List<CloudVariant> cloudVariants;
        final boolean hasRestoreSwitch;
        final boolean isRelated;
        final boolean isCustom;
        final int iconResId;
        boolean isInstalled = false; // populated async before adapter is set

        ImFamily(String tableName, String displayTitle, List<CloudVariant> cloudVariants,
                 boolean hasRestoreSwitch, boolean isRelated, boolean isCustom, int iconResId) {
            this.tableName = tableName;
            this.displayTitle = displayTitle;
            this.cloudVariants = cloudVariants;
            this.hasRestoreSwitch = hasRestoreSwitch;
            this.isRelated = isRelated;
            this.isCustom = isCustom;
            this.iconResId = iconResId;
        }
    }

    private List<ImFamily> buildFamilyList() {
        List<ImFamily> list = new ArrayList<>();

        // 注音
        List<CloudVariant> phonetic = new ArrayList<>();
        phonetic.add(new CloudVariant("OpenVanilla 注音字根", "34,838", "589 KB",
                LIME.DATABASE_CLOUD_IM_PHONETIC));
        phonetic.add(new CloudVariant("OpenVanilla 注音字根 (BIG5字集)", "15,945", "465 KB",
                LIME.DATABASE_CLOUD_IM_PHONETIC_BIG5));
        phonetic.add(new CloudVariant("注音連打字根", "95,029", "2.8 MB",
                LIME.DATABASE_CLOUD_IM_PHONETICCOMPLETE));
        phonetic.add(new CloudVariant("注音連打字根 (BIG5字集)", "76,122", "2.3 MB",
                LIME.DATABASE_CLOUD_IM_PHONETICCOMPLETE_BIG5));
        list.add(new ImFamily(LIME.DB_TABLE_PHONETIC, "注音", phonetic, true, false, false,
                R.drawable.ic_keyboard_outline));

        // 倉頡
        List<CloudVariant> cj = new ArrayList<>();
        cj.add(new CloudVariant("倉頡字根", "28,596", "830 KB",
                LIME.DATABASE_CLOUD_IM_CJ));
        cj.add(new CloudVariant("倉頡字根 (BIG5字集)", "13,859", "506 KB",
                LIME.DATABASE_CLOUD_IM_CJ_BIG5));
        cj.add(new CloudVariant("倉頡香港字字根", "30,278", "884 KB",
                LIME.DATABASE_CLOUD_IM_CJHK));
        list.add(new ImFamily(LIME.DB_TABLE_CJ, "倉頡", cj, true, false, false,
                R.drawable.ic_grid_on_24));

        // 四碼倉頡
        List<CloudVariant> cj4 = new ArrayList<>();
        cj4.add(new CloudVariant("哈哈倉頡", "33,021", "598 KB",
                LIME.DATABASE_CLOUD_IM_CJ4));
        list.add(new ImFamily(LIME.DB_TABLE_CJ4, "四碼倉頡", cj4, true, false, false,
                R.drawable.ic_grid_on_24));

        // 倉頡五代
        List<CloudVariant> cj5 = new ArrayList<>();
        cj5.add(new CloudVariant("倉頡五代字根", "24,004", "491 KB",
                LIME.DATABASE_CLOUD_IM_CJ5));
        list.add(new ImFamily(LIME.DB_TABLE_CJ5, "倉頡五代", cj5, true, false, false,
                R.drawable.ic_grid_on_24));

        // 快倉
        List<CloudVariant> scj = new ArrayList<>();
        scj.add(new CloudVariant("快倉字根", "74,250", "1.4 MB",
                LIME.DATABASE_CLOUD_IM_SCJ));
        list.add(new ImFamily(LIME.DB_TABLE_SCJ, "快倉", scj, true, false, false,
                R.drawable.ic_grid_on_24));

        // 速成
        List<CloudVariant> ecj = new ArrayList<>();
        ecj.add(new CloudVariant("簡易速成", "13,119", "136 KB",
                LIME.DATABASE_CLOUD_IM_ECJ));
        ecj.add(new CloudVariant("速成香港字字根", "27,853", "210 KB",
                LIME.DATABASE_CLOUD_IM_ECJHK));
        list.add(new ImFamily(LIME.DB_TABLE_ECJ, "速成", ecj, true, false, false,
                R.drawable.ic_grid_on_24));

        // 大易
        List<CloudVariant> dayi = new ArrayList<>();
        dayi.add(new CloudVariant("OpenVanilla 大易字根", "18,638", "486 KB",
                LIME.DATABASE_CLOUD_IM_DAYI));
        dayi.add(new CloudVariant("Unicode 3+4 碼單字版", "27,198", "584 KB",
                LIME.DATABASE_CLOUD_IM_DAYIUNI));
        dayi.add(new CloudVariant("Unicode 3+4 碼詞庫版", "117,766", "2.6 MB",
                LIME.DATABASE_CLOUD_IM_DAYIUNIP));
        list.add(new ImFamily(LIME.DB_TABLE_DAYI, "大易", dayi, true, false, false,
                R.drawable.ic_textformat_alt));

        // 輕鬆
        List<CloudVariant> ez = new ArrayList<>();
        ez.add(new CloudVariant("輕鬆字根", "14,422", "237 KB",
                LIME.DATABASE_CLOUD_IM_EZ));
        list.add(new ImFamily(LIME.DB_TABLE_EZ, "輕鬆", ez, true, false, false,
                R.drawable.ic_hand_tap));

        // 行列
        List<CloudVariant> array = new ArrayList<>();
        array.add(new CloudVariant("老刀行列字根", "32,386", "524 KB",
                LIME.DATABASE_CLOUD_IM_ARRAY));
        list.add(new ImFamily(LIME.DB_TABLE_ARRAY, "行列", array, true, false, false,
                R.drawable.ic_grid_on_24));

        // 行列10
        List<CloudVariant> array10 = new ArrayList<>();
        array10.add(new CloudVariant("老刀行列10字根", "32,120", "558 KB",
                LIME.DATABASE_CLOUD_IM_ARRAY10));
        list.add(new ImFamily(LIME.DB_TABLE_ARRAY10, "行列10", array10, true, false, false,
                R.drawable.ic_grid_on_24));

        // 筆順
        List<CloudVariant> wb = new ArrayList<>();
        wb.add(new CloudVariant("筆順五碼字根", "26,378", "267 KB",
                LIME.DATABASE_CLOUD_IM_WB));
        list.add(new ImFamily(LIME.DB_TABLE_WB, "筆順", wb, true, false, false,
                R.drawable.ic_pencil_outline));

        // 華象
        List<CloudVariant> hs = new ArrayList<>();
        hs.add(new CloudVariant("華象完整版", "183,659", "3.5 MB",
                LIME.DATABASE_CLOUD_IM_HS));
        hs.add(new CloudVariant("華象一版", "50,845", "830 KB",
                LIME.DATABASE_CLOUD_IM_HS_V1));
        hs.add(new CloudVariant("華象二版", "50,838", "834 KB",
                LIME.DATABASE_CLOUD_IM_HS_V2));
        hs.add(new CloudVariant("華象三版", "64,324", "1000 KB",
                LIME.DATABASE_CLOUD_IM_HS_V3));
        list.add(new ImFamily(LIME.DB_TABLE_HS, "華象", hs, true, false, false,
                R.drawable.ic_wand_stars));

        // 拼音
        List<CloudVariant> pinyin = new ArrayList<>();
        pinyin.add(new CloudVariant("拼音字根", "34,753", "509 KB",
                LIME.DATABASE_CLOUD_IM_PINYIN));
        pinyin.add(new CloudVariant("拼音字根 (簡體GB)", "34,753", "502 KB",
                LIME.DATABASE_CLOUD_IM_PINYINGB));
        list.add(new ImFamily(LIME.DB_TABLE_PINYIN, "拼音", pinyin, true, false, false,
                R.drawable.ic_text_bubble));

        // 自建 (CUSTOM) — no restore switch, no cloud buttons
        // TODO §2.3 — seedCustomIM verification: ensure seedCustomIM is invoked after successful custom-IM import
        list.add(new ImFamily(LIME.DB_TABLE_CUSTOM, "自建", new ArrayList<>(), false, false, true,
                R.drawable.ic_person_crop_rectangle));

        // 關聯字庫 (RELATED) — no restore switch, no cloud buttons, no txt import
        list.add(new ImFamily(LIME.DB_TABLE_RELATED, "關聯字庫", new ArrayList<>(), false, true, false,
                R.drawable.ic_text_bubble));

        return list;
    }

    // -------- Adapter --------

    private class ImFamilyAdapter extends RecyclerView.Adapter<ImFamilyViewHolder> {

        private final List<ImFamily> families;
        private final boolean[] expanded;

        ImFamilyAdapter(List<ImFamily> families) {
            this.families = families;
            this.expanded = new boolean[families.size()];
            for (int i = 0; i < families.size(); i++) {
                expanded[i] = !families.get(i).isInstalled;
            }
        }

        @NonNull
        @Override
        public ImFamilyViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_im_family_card, parent, false);
            return new ImFamilyViewHolder(v);
        }

        @Override
        public void onBindViewHolder(@NonNull ImFamilyViewHolder holder, int position) {
            ImFamily family = families.get(position);
            holder.bind(family, expanded[position], () -> {
                int pos = holder.getLayoutPosition();
                if (pos == RecyclerView.NO_POSITION) return;
                expanded[pos] = !expanded[pos];
                notifyItemChanged(pos);
            });
        }

        @Override
        public int getItemCount() {
            return families.size();
        }
    }

    // -------- ViewHolder --------

    private class ImFamilyViewHolder extends RecyclerView.ViewHolder {

        final LinearLayout cardHeader;
        final android.widget.ImageView ivFamilyIcon;
        final TextView tvTitle;
        final TextView tvInstalledBadge;
        final android.widget.ImageView ivChevron;
        final LinearLayout bodyContainer;
        final SwitchMaterial switchRestoreLearning;
        final LinearLayout cloudButtonsContainer;
        final MaterialButton btnImportLimedb;
        final MaterialButton btnImportTxt;
        final MaterialButton btnImportDefaultRelated;

        ImFamilyViewHolder(@NonNull View itemView) {
            super(itemView);
            cardHeader = itemView.findViewById(R.id.card_header);
            ivFamilyIcon = itemView.findViewById(R.id.iv_family_icon);
            tvTitle = itemView.findViewById(R.id.tv_im_title);
            tvInstalledBadge = itemView.findViewById(R.id.tv_installed_badge);
            ivChevron = itemView.findViewById(R.id.iv_chevron);
            bodyContainer = itemView.findViewById(R.id.body_container);
            switchRestoreLearning = itemView.findViewById(R.id.switch_restore_learning);
            cloudButtonsContainer = itemView.findViewById(R.id.cloud_buttons_container);
            btnImportLimedb = itemView.findViewById(R.id.btn_import_limedb);
            btnImportTxt = itemView.findViewById(R.id.btn_import_txt);
            btnImportDefaultRelated = itemView.findViewById(R.id.btn_import_default_related);
        }

        void bind(ImFamily family, boolean isExpanded, Runnable toggleExpand) {
            // Family icon
            if (family.iconResId != 0) {
                ivFamilyIcon.setImageResource(family.iconResId);
                ivFamilyIcon.setVisibility(View.VISIBLE);
            } else {
                ivFamilyIcon.setVisibility(View.GONE);
            }

            tvTitle.setText(family.displayTitle);

            // Installed badge + chevron + header click lock
            if (family.isInstalled) {
                tvInstalledBadge.setVisibility(View.VISIBLE);
                ivChevron.setVisibility(View.GONE);
                cardHeader.setOnClickListener(null);
                cardHeader.setClickable(false);
            } else {
                tvInstalledBadge.setVisibility(View.GONE);
                ivChevron.setVisibility(View.VISIBLE);
                cardHeader.setClickable(true);
                cardHeader.setOnClickListener(v -> {
                    float toDeg = isExpanded ? 0f : 180f;
                    ivChevron.animate().rotation(toDeg).setDuration(200).start();
                    toggleExpand.run();
                });
            }

            // Expand/collapse
            bodyContainer.setVisibility(isExpanded ? View.VISIBLE : View.GONE);
            ivChevron.clearAnimation();
            ivChevron.setRotation(isExpanded ? 180f : 0f);

            // Restore switch visibility
            if (family.hasRestoreSwitch) {
                switchRestoreLearning.setVisibility(View.VISIBLE);
                switchRestoreLearning.setOnCheckedChangeListener(null);
                switchRestoreLearning.setChecked(getRestorePref(family.tableName));
                switchRestoreLearning.setOnCheckedChangeListener((buttonView, isChecked) ->
                        setRestorePref(family.tableName, isChecked));
            } else {
                switchRestoreLearning.setVisibility(View.GONE);
            }

            // Cloud variant rows — rebuild each bind to avoid stale listeners
            cloudButtonsContainer.removeAllViews();
            if (!family.cloudVariants.isEmpty()) {
                cloudButtonsContainer.setVisibility(View.VISIBLE);
                for (CloudVariant variant : family.cloudVariants) {
                    View row = LayoutInflater.from(requireContext())
                            .inflate(R.layout.item_cloud_variant, cloudButtonsContainer, false);
                    TextView tvName = row.findViewById(R.id.tv_variant_name);
                    TextView tvMeta = row.findViewById(R.id.tv_variant_meta);
                    MaterialButton btnInstall = row.findViewById(R.id.btn_install);

                    tvName.setText(variant.label);
                    tvMeta.setText(variant.count + " · " + variant.fileSize);

                    final String url = variant.url;
                    btnInstall.setOnClickListener(v -> {
                        if (setupImController != null) {
                            setupImController.downloadAndImportZippedDb(
                                    family.tableName, url,
                                    switchRestoreLearning.isChecked(),
                                    () -> onInstallComplete(family.tableName));
                        }
                    });
                    cloudButtonsContainer.addView(row);
                }
            } else {
                cloudButtonsContainer.setVisibility(View.GONE);
            }

            // .limedb import button (all families)
            btnImportLimedb.setOnClickListener(v ->
                    launchLimedbPicker(family.tableName, family.isRelated));

            // .cin/.lime import (hidden for RELATED)
            if (family.isRelated) {
                btnImportTxt.setVisibility(View.GONE);
            } else {
                btnImportTxt.setVisibility(View.VISIBLE);
                btnImportTxt.setOnClickListener(v -> launchTxtPicker(family.tableName));
            }

            // Default related button (visible only for RELATED)
            if (family.isRelated) {
                btnImportDefaultRelated.setVisibility(View.VISIBLE);
                btnImportDefaultRelated.setOnClickListener(v ->
                        showDefaultRelatedConfirmDialog(() -> onInstallComplete(family.tableName)));
            } else {
                btnImportDefaultRelated.setVisibility(View.GONE);
            }
        }
    }

    // -------- Install-complete callback --------

    /**
     * Called after any install path succeeds. Marks the family installed immediately,
     * collapses its card, and refreshes just that item.
     * Must be called on the main thread.
     */
    // TODO §2.3 — verify progress hookup: ensure ProgressManager show/hide is called around download and import operations
    private void onInstallComplete(String tableName) {
        if (!isAdded() || currentFamilies == null || adapter == null) return;
        for (int i = 0; i < currentFamilies.size(); i++) {
            if (tableName.equals(currentFamilies.get(i).tableName)) {
                currentFamilies.get(i).isInstalled = true;
                adapter.expanded[i] = false;
                adapter.notifyItemChanged(i);
                break;
            }
        }
    }

    private void showDefaultRelatedConfirmDialog(Runnable onSuccess) {
        if (activity == null) return;
        new AlertDialog.Builder(activity)
                .setMessage(R.string.setup_im_import_related_default_confirm)
                .setPositiveButton(R.string.dialog_confirm, (dialog, which) -> {
                    if (setupImController != null) {
                        setupImController.importDbDefaultRelated();
                        if (onSuccess != null) onSuccess.run();
                    }
                })
                .setNegativeButton(R.string.dialog_cancel, (dialog, which) -> dialog.dismiss())
                .show();
    }
}
