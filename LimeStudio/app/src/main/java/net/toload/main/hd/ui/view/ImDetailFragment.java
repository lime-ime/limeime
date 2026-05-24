package net.toload.main.hd.ui.view;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.text.InputType;
import android.util.TypedValue;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.widget.NestedScrollView;
import androidx.fragment.app.Fragment;
import androidx.preference.PreferenceManager;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.switchmaterial.SwitchMaterial;

import net.toload.main.hd.R;
import net.toload.main.hd.data.ImConfig;
import net.toload.main.hd.data.Keyboard;
import net.toload.main.hd.ui.LIMESettings;
import net.toload.main.hd.ui.controller.ManageImController;

import java.util.List;

/**
 * Per-IM detail screen shown in the detail pane of TwoPaneHostFragment.
 *
 * <p>Displays IM info (name, record count), keyboard layout picker,
 * link to ManageImFragment, options, and a remove button stub.
 */
public class ImDetailFragment extends Fragment {

    private static final String TAG = "ImDetailFragment";
    private static final String ARG_IM_CODE = "im_code";
    private static final String ARG_IM_DESC = "im_desc";

    private Activity activity;
    private ManageImController manageImController;

    private String tableCode;
    private String imDesc;

    private TextView tvImName;
    private TextView tvImRecords;
    private TextView txtImVersion;
    private TextView tvKeyboardValue;
    private TextView tvHeading;

    public static ImDetailFragment newInstance(ImConfig im) {
        ImDetailFragment f = new ImDetailFragment();
        Bundle args = new Bundle();
        args.putString(ARG_IM_CODE, im.getCode());
        args.putString(ARG_IM_DESC, im.getDesc());
        f.setArguments(args);
        return f;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (getArguments() != null) {
            tableCode = getArguments().getString(ARG_IM_CODE);
            imDesc = getArguments().getString(ARG_IM_DESC);
        }
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        activity = getActivity();

        if (activity instanceof LIMESettings) {
            manageImController = ((LIMESettings) activity).getManageImController();
        } else {
            Log.w(TAG, "Activity is not LIMESettings; ManageImController unavailable");
        }

        View rootView = inflater.inflate(R.layout.fragment_im_detail, container, false);
        NestedScrollView scrollView = rootView.findViewById(R.id.im_detail_scroll);
        if (scrollView != null) {
            ScrollableTabHelper.applyToNestedScrollView(activity, scrollView);
        }

        // Toolbar with back navigation (title is rendered by tv_im_detail_heading below)
        MaterialToolbar toolbar = rootView.findViewById(R.id.im_detail_toolbar);
        toolbar.setClickable(true);
        toolbar.setFocusable(true);
        toolbar.setTitle("");

        tvHeading = rootView.findViewById(R.id.tv_im_detail_heading);
        if (tvHeading != null) {
            tvHeading.setText(imDesc != null ? imDesc : "");
        }
        toolbar.setNavigationOnClickListener(v -> {
            Fragment host = getParentFragment();
            if (host != null) {
                host.getChildFragmentManager().popBackStack();
            }
        });
        // Also register OnBackPressedCallback because SlidingPaneLayout may intercept toolbar taps
        requireActivity().getOnBackPressedDispatcher().addCallback(
                getViewLifecycleOwner(),
                new OnBackPressedCallback(true) {
                    @Override
                    public void handleOnBackPressed() {
                        Fragment host = getParentFragment();
                        if (host != null) {
                            host.getChildFragmentManager().popBackStack();
                        }
                    }
                });

        tvImName = rootView.findViewById(R.id.tv_im_name);
        tvImRecords = rootView.findViewById(R.id.tv_im_records);
        txtImVersion = rootView.findViewById(R.id.txtImVersion);
        tvKeyboardValue = rootView.findViewById(R.id.tv_keyboard_value);

        if (imDesc != null) {
            tvImName.setText(imDesc);
        }

        LinearLayout rowName = rootView.findViewById(R.id.row_name);
        LinearLayout rowVersion = rootView.findViewById(R.id.row_version);

        // Keyboard row click -> show picker
        LinearLayout rowKeyboard = rootView.findViewById(R.id.row_keyboard);
        rowKeyboard.setOnClickListener(v -> showKeyboardPicker());

        final boolean isRelated = "related".equals(tableCode);

        // 字根資料表 row click -> navigate to ManageImFragment (or ManageRelatedFragment for the synthetic 關聯字庫 row)
        LinearLayout rowManageTable = rootView.findViewById(R.id.row_manage_table);
        rowManageTable.setOnClickListener(v -> {
            Fragment parent = getParentFragment();
            if (parent instanceof TwoPaneHostFragment && tableCode != null) {
                if (isRelated) {
                    ((TwoPaneHostFragment) parent).navigateToDetail(
                            ManageRelatedFragment.newInstance(1));
                } else {
                    ((TwoPaneHostFragment) parent).navigateToDetail(
                            ManageImFragment.newInstance(1, tableCode));
                }
            }
        });

        // Apply related-row variations: hide sections that don't apply, retext labels
        if (isRelated) {
            View sectionKeyboard = rootView.findViewById(R.id.section_keyboard);
            View sectionOptions = rootView.findViewById(R.id.section_options);
            View dividerVersion = rootView.findViewById(R.id.divider_version);
            View editNameIcon = rootView.findViewById(R.id.iv_edit_name);
            TextView tvSectionTableLabel = rootView.findViewById(R.id.tv_section_table_label);
            TextView tvManageTableLabel = rootView.findViewById(R.id.tv_manage_table_label);
            if (sectionKeyboard != null) sectionKeyboard.setVisibility(View.GONE);
            if (sectionOptions != null) sectionOptions.setVisibility(View.GONE);
            if (rowVersion != null) rowVersion.setVisibility(View.GONE);
            if (dividerVersion != null) dividerVersion.setVisibility(View.GONE);
            if (editNameIcon != null) editNameIcon.setVisibility(View.GONE);
            if (tvSectionTableLabel != null) tvSectionTableLabel.setText(R.string.im_detail_section_related);
            if (tvManageTableLabel != null) tvManageTableLabel.setText(R.string.im_detail_manage_related);
        }

        if (!isRelated) {
            if (rowName != null) {
                rowName.setClickable(true);
                rowName.setFocusable(true);
                applySelectableBackground(rowName);
                rowName.setOnClickListener(v -> showMetadataFieldEditor("name"));
            }
            if (rowVersion != null) {
                rowVersion.setClickable(true);
                rowVersion.setFocusable(true);
                applySelectableBackground(rowVersion);
                rowVersion.setOnClickListener(v -> showMetadataFieldEditor("version"));
            }
        }

        // 備份選項 switch - bound to SharedPreferences (skipped for related since options card is hidden)
        SwitchMaterial switchBackup = rootView.findViewById(R.id.switch_backup_on_delete);
        if (tableCode != null && !isRelated) {
            SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(requireContext());
            boolean backupPref = prefs.getBoolean("backup_on_delete_" + tableCode, true);
            switchBackup.setChecked(backupPref);
            switchBackup.setOnCheckedChangeListener((btn, checked) ->
                    PreferenceManager.getDefaultSharedPreferences(requireContext())
                            .edit()
                            .putBoolean("backup_on_delete_" + tableCode, checked)
                            .apply());
        }

        // Conditional sections based on tableCode
        SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(requireContext());

        if ("custom".equals(tableCode)) {
            rootView.findViewById(R.id.section_custom_mapping).setVisibility(View.VISIBLE);
            SwitchMaterial sNum = rootView.findViewById(R.id.switchAcceptNumberIndex);
            SwitchMaterial sSym = rootView.findViewById(R.id.switchAcceptSymbolIndex);
            sNum.setChecked(sp.getBoolean("accept_number_index", false));
            sSym.setChecked(sp.getBoolean("accept_symbol_index", false));
            sNum.setOnCheckedChangeListener((b, c) ->
                    sp.edit().putBoolean("accept_number_index", c).apply());
            sSym.setOnCheckedChangeListener((b, c) ->
                    sp.edit().putBoolean("accept_symbol_index", c).apply());
        }

        if ("array10".equals(tableCode)) {
            rootView.findViewById(R.id.section_array10).setVisibility(View.VISIBLE);
            // TODO §7 backport — spinner wiring for auto_commit (full array-adapter binding)
            Spinner spinnerAutoCommit = rootView.findViewById(R.id.spinnerAutoCommit);
            String[] autoCommitLabels = getResources().getStringArray(R.array.auto_commit_labels);
            String[] autoCommitValues = getResources().getStringArray(R.array.auto_commit_values);
            ArrayAdapter<String> autoCommitAdapter = new ArrayAdapter<>(requireContext(),
                    android.R.layout.simple_spinner_item, autoCommitLabels);
            autoCommitAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
            spinnerAutoCommit.setAdapter(autoCommitAdapter);
            String savedAutoCommit = sp.getString("auto_commit", "0");
            for (int i = 0; i < autoCommitValues.length; i++) {
                if (autoCommitValues[i].equals(savedAutoCommit)) {
                    spinnerAutoCommit.setSelection(i);
                    break;
                }
            }
            final String[] autoCommitValuesFinal = autoCommitValues;
            spinnerAutoCommit.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
                @Override
                public void onItemSelected(AdapterView<?> parent, View v, int pos, long id) {
                    sp.edit().putString("auto_commit", autoCommitValuesFinal[pos]).apply();
                }
                @Override public void onNothingSelected(AdapterView<?> parent) {}
            });
        }

        if ("phonetic".equals(tableCode)) {
            rootView.findViewById(R.id.section_phonetic).setVisibility(View.VISIBLE);
            // TODO §7 backport — spinner wiring for phonetic_keyboard_type
            Spinner spinnerPhonetic = rootView.findViewById(R.id.spinnerPhoneticType);
            String[] phoneticLabels = getResources().getStringArray(R.array.phonetic_keyboard_type);
            String[] phoneticValues = getResources().getStringArray(R.array.phonetic_keyboard_type_values);
            ArrayAdapter<String> phoneticAdapter = new ArrayAdapter<>(requireContext(),
                    android.R.layout.simple_spinner_item, phoneticLabels);
            phoneticAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
            spinnerPhonetic.setAdapter(phoneticAdapter);
            String savedPhonetic = sp.getString("phonetic_keyboard_type", "standard");
            for (int i = 0; i < phoneticValues.length; i++) {
                if (phoneticValues[i].equals(savedPhonetic)) {
                    spinnerPhonetic.setSelection(i);
                    break;
                }
            }
            final String[] phoneticValuesFinal = phoneticValues;
            spinnerPhonetic.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
                @Override
                public void onItemSelected(AdapterView<?> parent, View v, int pos, long id) {
                    String newType = phoneticValuesFinal[pos];
                    String oldType = sp.getString("phonetic_keyboard_type", "standard");
                    sp.edit().putString("phonetic_keyboard_type", newType).apply();
                    if (!newType.equals(oldType)) {
                        applyPhoneticKeyboardType(newType);
                    }
                }
                @Override public void onNothingSelected(AdapterView<?> parent) {}
            });
        }

        // 移除輸入法 button (also available for related — lets users clear and reload their own table)
        MaterialButton btnRemove = rootView.findViewById(R.id.btn_remove_im);
        btnRemove.setOnClickListener(v -> showRemoveConfirmDialog());

        // Load version from IM metadata, retaining legacy SharedPreferences fallback.
        if (tableCode != null) {
            String version = "";
            try {
                if (manageImController != null && manageImController.getSearchServer() != null) {
                    net.toload.main.hd.SearchServer searchServer = manageImController.getSearchServer();
                    version = searchServer.getImConfig(tableCode, "version");
                }
            } catch (Exception ignored) {
                version = "";
            }
            if (version == null || version.isEmpty()) {
                android.content.SharedPreferences versionSp = androidx.preference.PreferenceManager.getDefaultSharedPreferences(requireContext());
                version = versionSp.getString(tableCode + "mapping_version", "");
            }
            if (version == null || version.isEmpty()) {
                try {
                    if (manageImController != null && manageImController.getSearchServer() != null) {
                        version = manageImController.getSearchServer().getImConfig(tableCode, "source");
                    }
                } catch (Exception ignored) {
                    version = "";
                }
            }
            if (version == null || version.isEmpty()) {
                try {
                    if (manageImController != null && manageImController.getSearchServer() != null) {
                        version = manageImController.getSearchServer().getImConfig(tableCode, "name");
                    }
                } catch (Exception ignored) {
                    version = "";
                }
            }
            if (version == null || version.isEmpty()) version = "-";
            if (txtImVersion != null) txtImVersion.setText(version);
        }

        // Share button (plain ImageButton overlaying toolbar — direct click handler)
        android.widget.ImageButton btnShare = rootView.findViewById(R.id.btn_im_share);
        if (btnShare != null) {
            btnShare.setClickable(true);
            btnShare.setFocusable(true);
            btnShare.bringToFront();
            btnShare.setOnClickListener(v -> showShareFormatDialog());
        }

        // Load async data
        loadRecordCount();
        loadCurrentKeyboard();

        return rootView;
    }

    /**
     * Apply a phonetic_keyboard_type change to the `im` table — mirrors the
     * LIMEPreference.onSharedPreferenceChanged logic so the soft-keyboard layout
     * follows the picker selection. Also refreshes the 鍵盤布局 row UI.
     */
    private void applyPhoneticKeyboardType(String newType) {
        final net.toload.main.hd.ui.controller.ManageImController ctrl = manageImController;
        if (ctrl == null) return;
        final net.toload.main.hd.SearchServer ss = ctrl.getSearchServer();
        if (ss == null) return;
        final boolean numberRow = androidx.preference.PreferenceManager
                .getDefaultSharedPreferences(requireContext())
                .getBoolean("number_row_in_english", false);
        new Thread(() -> {
            try {
                net.toload.main.hd.data.Keyboard kb;
                switch (newType) {
                    case net.toload.main.hd.global.LIME.IM_PHONETIC_KEYBOARD_TYPE_ETEN:
                        kb = ss.getKeyboardConfig("phoneticet41");
                        break;
                    case net.toload.main.hd.global.LIME.IM_PHONETIC_KEYBOARD_TYPE_ETEN26:
                        kb = ss.getKeyboardConfig(numberRow ? "limenum" : "lime");
                        break;
                    case "eten26_symbol":
                        kb = ss.getKeyboardConfig("et26");
                        break;
                    case net.toload.main.hd.global.LIME.IM_PHONETIC_KEYBOARD_HSU:
                        kb = ss.getKeyboardConfig(numberRow ? "limenum" : "lime");
                        break;
                    case "hsu_symbol":
                        kb = ss.getKeyboardConfig(net.toload.main.hd.global.LIME.IM_PHONETIC_KEYBOARD_HSU);
                        break;
                    case net.toload.main.hd.global.LIME.IM_PHONETIC_STANDARD:
                    default:
                        kb = ss.getKeyboardConfig("phonetic");
                        break;
                }
                if (kb != null) {
                    ss.setIMKeyboard("phonetic", kb.getDesc(), kb.getCode());
                    final net.toload.main.hd.data.Keyboard kbFinal = kb;
                    if (activity != null) {
                        activity.runOnUiThread(() -> {
                            if (tvKeyboardValue != null) {
                                tvKeyboardValue.setText(kbFinal.getDesc());
                            }
                        });
                    }
                }
            } catch (Exception e) {
                android.util.Log.e("ImDetailFragment", "applyPhoneticKeyboardType failed", e);
            }
        }).start();
    }

    private void showShareFormatDialog() {
        if (tableCode == null) return;
        android.app.Activity act = requireActivity();
        if (!(act instanceof LIMESettings)) return;
        final net.toload.main.hd.ui.ShareManager shareManager = ((LIMESettings) act).getShareManager();
        if (shareManager == null) return;

        new android.app.AlertDialog.Builder(requireContext())
                .setTitle(R.string.share_dialog_title)
                .setItems(new CharSequence[] {
                        getString(R.string.share_format_text),
                        getString(R.string.share_format_database)
                }, (d, which) -> {
                    if (which == 0) {
                        shareManager.shareImAsText(tableCode);
                    } else {
                        shareManager.exportAndShareImTable(tableCode);
                    }
                })
                .setNegativeButton(R.string.dialog_cancel, null)
                .show();
    }

    private void applySelectableBackground(View view) {
        if (view == null || activity == null) return;
        TypedValue outValue = new TypedValue();
        activity.getTheme().resolveAttribute(android.R.attr.selectableItemBackground, outValue, true);
        view.setBackgroundResource(outValue.resourceId);
    }

    private void showMetadataFieldEditor(String field) {
        if (activity == null || tableCode == null || "related".equals(tableCode)) return;
        final boolean editingName = "name".equals(field);
        final boolean editingVersion = "version".equals(field);
        if (!editingName && !editingVersion) return;

        LinearLayout form = new LinearLayout(activity);
        form.setOrientation(LinearLayout.VERTICAL);
        int padding = (int) (24 * getResources().getDisplayMetrics().density);
        form.setPadding(padding, 8, padding, 0);

        EditText valueInput = new EditText(activity);
        valueInput.setSingleLine(true);
        valueInput.setHint(editingName ? R.string.im_detail_label_name : R.string.im_detail_label_version);
        valueInput.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        CharSequence currentText = editingName
                ? (tvImName != null ? tvImName.getText() : "")
                : (txtImVersion != null ? txtImVersion.getText() : "");
        String currentValue = currentText == null ? "" : currentText.toString();
        valueInput.setText(!editingName && "-".equals(currentValue) ? "" : currentValue);
        form.addView(valueInput, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        AlertDialog dialog = new AlertDialog.Builder(activity)
                .setTitle(editingName ? R.string.im_detail_edit_name_title : R.string.im_detail_edit_version_title)
                .setView(form)
                .setNegativeButton(R.string.dialog_cancel, null)
                .setPositiveButton(R.string.manage_im_save, null)
                .create();
        dialog.setOnShowListener(d -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            String editedValue = valueInput.getText() == null ? "" : valueInput.getText().toString().trim();
            if (editingName && editedValue.isEmpty()) {
                valueInput.setError(getString(R.string.im_detail_edit_metadata_empty_name));
                return;
            }

            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(false);
            final ManageImController ctrl = manageImController;
            final Activity act = activity;
            final String table = tableCode;
            new Thread(() -> {
                boolean saved = ctrl != null && ctrl.updateIMMetadataField(table, field, editedValue);
                if (act == null) return;
                act.runOnUiThread(() -> {
                    if (!isAdded() || activity == null) return;
                    dialog.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(true);
                    if (!saved) {
                        Toast.makeText(activity, R.string.im_detail_edit_metadata_failed, Toast.LENGTH_SHORT).show();
                        return;
                    }
                    if (editingName) {
                        imDesc = editedValue;
                        if (tvImName != null) tvImName.setText(editedValue);
                        if (tvHeading != null) tvHeading.setText(editedValue);
                    } else if (txtImVersion != null) {
                        txtImVersion.setText(editedValue.isEmpty() ? "-" : editedValue);
                    }
                    refreshListPane();
                    dialog.dismiss();
                });
            }).start();
        }));
        dialog.show();
    }

    private void refreshListPane() {
        Fragment parent = getParentFragment();
        if (parent == null) return;
        Fragment listFragment = parent.getChildFragmentManager().findFragmentById(R.id.im_list_pane);
        if (listFragment instanceof ImListFragment) {
            ((ImListFragment) listFragment).refreshList();
        }
    }

    private void loadRecordCount() {
        final ManageImController ctrl = manageImController;
        final Activity act = activity;
        final String table = tableCode;
        if (ctrl == null || table == null) return;

        new Thread(() -> {
            final int count = ctrl.countRecords(table);
            if (act == null) return;
            act.runOnUiThread(() -> {
                if (!isAdded() || activity == null || tvImRecords == null) return;
                tvImRecords.setText(String.valueOf(count));
            });
        }).start();
    }

    private void loadCurrentKeyboard() {
        final ManageImController ctrl = manageImController;
        final Activity act = activity;
        final String table = tableCode;
        if (ctrl == null || table == null) return;

        new Thread(() -> {
            final Keyboard kb = ctrl.getCurrentKeyboard(table);
            if (act == null) return;
            act.runOnUiThread(() -> {
                if (!isAdded() || activity == null || tvKeyboardValue == null) return;
                if (kb != null && kb.getDesc() != null && !kb.getDesc().isEmpty()) {
                    tvKeyboardValue.setText(kb.getDesc());
                } else {
                    tvKeyboardValue.setText(R.string.im_detail_keyboard_default);
                }
            });
        }).start();
    }

    private void showKeyboardPicker() {
        final ManageImController ctrl = manageImController;
        final Activity act = activity;
        if (ctrl == null || act == null || tableCode == null) return;

        new Thread(() -> {
            final List<Keyboard> keyboards = ctrl.getKeyboardList();
            final Keyboard current = ctrl.getCurrentKeyboard(tableCode);
            if (act == null) return;
            act.runOnUiThread(() -> {
                if (!isAdded() || activity == null) return;
                if (keyboards == null || keyboards.isEmpty()) return;

                String[] names = new String[keyboards.size()];
                int checkedIndex = -1;
                for (int i = 0; i < keyboards.size(); i++) {
                    Keyboard keyboard = keyboards.get(i);
                    names[i] = keyboard.getDesc();
                    if (current != null && keyboard.getCode().equals(current.getCode())) {
                        checkedIndex = i;
                    }
                }

                final String tbl = tableCode;
                new AlertDialog.Builder(activity)
                        .setTitle(R.string.im_detail_keyboard_picker_title)
                        .setSingleChoiceItems(names, checkedIndex, (dialog, which) -> {
                            Keyboard selected = keyboards.get(which);
                            if (tvKeyboardValue != null) {
                                tvKeyboardValue.setText(selected.getDesc());
                            }
                            new Thread(() -> ctrl.setIMKeyboard(tbl, selected)).start();
                            dialog.dismiss();
                        })
                        .setNegativeButton(android.R.string.cancel, null)
                        .show();
            });
        }).start();
    }

    private void showRemoveConfirmDialog() {
        if (activity == null) return;
        new AlertDialog.Builder(activity)
                .setMessage(R.string.im_detail_remove_confirm)
                .setPositiveButton(R.string.dialog_confirm, (dialog, which) -> {
                    if (manageImController != null && tableCode != null) {
                        boolean backupLearning = false;
                        View root = getView();
                        if (root != null) {
                            SwitchMaterial sw = root.findViewById(R.id.switch_backup_on_delete);
                            backupLearning = sw != null && sw.isChecked();
                        }
                        net.toload.main.hd.SearchServer ss = manageImController.getSearchServer();
                        final android.content.Context ctx = requireContext().getApplicationContext();
                        final net.toload.main.hd.ui.controller.ManageImController ctrl = manageImController;
                        final String tbl = tableCode;
                        if (ss != null) {
                            android.util.Log.i("ImDetailFragment", "Remove confirm: tbl=" + tbl);
                            final Fragment parent = getParentFragment();
                            // Run DB ops on background, THEN pop on main thread so IM List sees fresh data
                            new Thread(() -> {
                                ss.clearTable(tbl);
                                ss.resetImConfig(tbl);
                                java.util.List<net.toload.main.hd.data.ImConfig> imList =
                                        ctrl.getImConfigFullNameList();
                                android.util.Log.i("ImDetailFragment", "After resetImConfig, list size=" + (imList==null?0:imList.size()));
                                new net.toload.main.hd.global.LIMEPreferenceManager(ctx)
                                        .syncIMActivatedState(imList);
                                if (parent != null && parent.getActivity() != null) {
                                    parent.getActivity().runOnUiThread(() -> {
                                        if (parent.isAdded()) {
                                            parent.getChildFragmentManager().popBackStack();
                                        }
                                    });
                                }
                            }).start();
                        } else {
                            Fragment parent = getParentFragment();
                            if (parent != null) {
                                parent.getChildFragmentManager().popBackStack();
                            }
                        }
                    } else {
                        android.widget.Toast.makeText(getContext(),
                                R.string.manage_im_error_no_controller,
                                android.widget.Toast.LENGTH_SHORT).show();
                    }
                })
                .setNegativeButton(R.string.dialog_cancel, null)
                .show();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        activity = null;
        manageImController = null;
        tvImName = null;
        tvImRecords = null;
        txtImVersion = null;
        tvKeyboardValue = null;
        tvHeading = null;
    }
}
