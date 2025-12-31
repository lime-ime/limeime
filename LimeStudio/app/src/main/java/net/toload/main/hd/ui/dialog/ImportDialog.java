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

package net.toload.main.hd.ui.dialog;

import android.app.Activity;
import android.app.AlertDialog;

import androidx.annotation.NonNull;
import androidx.fragment.app.DialogFragment;

import android.content.Context;
import android.graphics.Typeface;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Toast;

import net.toload.main.hd.data.ImConfig;
import net.toload.main.hd.global.LIME;
import net.toload.main.hd.R;
import net.toload.main.hd.ui.controller.ManageImController;
import net.toload.main.hd.ui.MainActivity;

import java.util.List;

/**
 * Dialog fragment to import IM data from text or .limedb files.
 *
 * <p>Supports two modes: importing raw text (suggest IM table mapping) and
 * importing a .limedb file (restore into a specific IM table). Hosts UI to
 * select target table and restore options.
 */
public class ImportDialog extends DialogFragment {

	ManageImController manageImController;
	Activity activity;
	View view;
    // ImportDialog cancel button
	Button btnImportCancel;
    // ImportDialog import related button
	Button btnImportRelated;
    // ImportDialog checkbox to restore user records
	CheckBox chkImportRestoreUserRecords;
    // ImportDialog importing text send from other apps
	String importText;
    // ImportDialog file path send from other apps
	String filePath;
	int importMode;
	
	// Import mode constants
	public static final int IMPORT_MODE_TEXT = 0; // For handleSendText: show non-empty tables + related
	public static final int IMPORT_MODE_FILE = 1; // For handleImportTxt: show empty tables only
	
	private static final String IMPORT_TEXT = "import_text"; // Bundle key for import text
	private static final String FILE_PATH = "file_path"; // Bundle key for file path
	private static final String IMPORT_MODE = "import_mode"; // Bundle key for import mode
	
	// Callback interface for file import mode
	public interface OnImportIMSelectedListener {
		void onImportDialogImSelected(String tableName, boolean restoreUserRecords);
    }
	
	private OnImportIMSelectedListener listener;

	public static ImportDialog newInstance(String importText) {
		ImportDialog btd = new ImportDialog();
		Bundle args = new Bundle();
		args.putString(IMPORT_TEXT, importText);
		args.putInt(IMPORT_MODE, IMPORT_MODE_TEXT);
		btd.setArguments(args);
		btd.setCancelable(true);
		return btd;
	}
	
	public static ImportDialog newInstanceForFile(String filePath) {
		ImportDialog btd = new ImportDialog();
		Bundle args = new Bundle();
		args.putString(FILE_PATH, filePath);
		args.putInt(IMPORT_MODE, IMPORT_MODE_FILE);
		btd.setArguments(args);
		btd.setCancelable(true);
		return btd;
	}
	
	public void setOnImportTypeSelectedListener(OnImportIMSelectedListener listener) {
		this.listener = listener;
	}


	@Override
	public void onAttach(@NonNull Context context) {
		super.onAttach(context);
        assert getArguments() != null;
        importMode = getArguments().getInt(IMPORT_MODE, IMPORT_MODE_TEXT);
        if (importMode == IMPORT_MODE_TEXT) {
            importText = getArguments().getString(IMPORT_TEXT);
        } else {
            filePath = getArguments().getString(FILE_PATH);
        }
        // Set listener if activity implements the interface
        if (context instanceof OnImportIMSelectedListener) {
            listener = (OnImportIMSelectedListener) context;
        }
	}


	@Override
	public void onCreate(Bundle icicle) {
		super.onCreate(icicle);
		this.setCancelable(false);
	}


	@Override
	public void onResume() {
		super.onResume();

        assert getDialog() != null;
        getDialog().setOnKeyListener((dialog, keyCode, event) -> {
            if ((keyCode == android.view.KeyEvent.KEYCODE_BACK)) {
                // To dismiss the fragment when the back-button is pressed.
                dismiss();
                return true;
            }
            // Otherwise, do nothing else
            else return false;
        });
	}


	public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle icicle) {
        assert getDialog() != null;
        activity = getActivity();
		if (activity instanceof MainActivity) {
			manageImController = ((MainActivity) activity).getManageImController();
		}
		getDialog().getWindow().setTitle(getResources().getString(R.string.import_dialog_title));

        view = inflater.inflate(R.layout.fragment_dialog_import, container, false);

		btnImportCancel = view.findViewById(R.id.btnImportCancel);
		btnImportCancel.setOnClickListener(v -> dismiss());
		btnImportRelated = view.findViewById(R.id.btnImportRelated);
		chkImportRestoreUserRecords = view.findViewById(R.id.chkImportRestoreLearning);
		
		// Show checkbox only in IMPORT_MODE_FILE
		if (importMode == IMPORT_MODE_FILE) {
			chkImportRestoreUserRecords.setVisibility(View.VISIBLE);
			chkImportRestoreUserRecords.setChecked(true); // Default to checked
			btnImportRelated.setVisibility(View.GONE);
		} else {
			chkImportRestoreUserRecords.setVisibility(View.GONE);
			btnImportRelated.setVisibility(View.VISIBLE);
		}

		List<ImConfig> imConfigFullNameList = manageImController.getImConfigFullNameList();

		// Setup all IM type buttons using imList
		setupImportDialogButton(view, R.id.btnImportCustom, LIME.DB_TABLE_CUSTOM, LIME.IM_CUSTOM, imConfigFullNameList);
		setupImportDialogButton(view, R.id.btnImportArray, LIME.DB_TABLE_ARRAY, LIME.IM_ARRAY, imConfigFullNameList);
		setupImportDialogButton(view, R.id.btnImportArray10, LIME.DB_TABLE_ARRAY10, LIME.IM_ARRAY10, imConfigFullNameList);
		setupImportDialogButton(view, R.id.btnImportCj, LIME.DB_TABLE_CJ, LIME.IM_CJ, imConfigFullNameList);
		setupImportDialogButton(view, R.id.btnImportCj5, LIME.DB_TABLE_CJ5, LIME.IM_CJ5, imConfigFullNameList);
		setupImportDialogButton(view, R.id.btnImportDayi, LIME.DB_TABLE_DAYI, LIME.IM_DAYI, imConfigFullNameList);
		setupImportDialogButton(view, R.id.btnImportEcj, LIME.DB_TABLE_ECJ, LIME.IM_ECJ, imConfigFullNameList);
		setupImportDialogButton(view, R.id.btnImportEz, LIME.DB_TABLE_EZ, LIME.IM_EZ, imConfigFullNameList);
		setupImportDialogButton(view, R.id.btnImportPhonetic, LIME.DB_TABLE_PHONETIC, LIME.IM_PHONETIC, imConfigFullNameList);
		setupImportDialogButton(view, R.id.btnImportPinyin, LIME.DB_TABLE_PINYIN, LIME.IM_PINYIN, imConfigFullNameList);
		setupImportDialogButton(view, R.id.btnImportScj, LIME.DB_TABLE_SCJ, LIME.IM_SCJ, imConfigFullNameList);
		setupImportDialogButton(view, R.id.btnImportWb, LIME.DB_TABLE_WB, LIME.IM_WB, imConfigFullNameList);
		setupImportDialogButton(view, R.id.btnImportHs, LIME.DB_TABLE_HS, LIME.IM_HS, imConfigFullNameList);

		// Setup related table button
		if (importMode == IMPORT_MODE_TEXT && importText != null && importText.length() > 1) {
			setupImportDialogButton(view, R.id.btnImportRelated, LIME.DB_TABLE_RELATED, LIME.DB_TABLE_RELATED, imConfigFullNameList);
		} else {
			setupButtonDisabled(btnImportRelated);
		}

		return view;
	}
	
	/**
	/**
	 * Sets up a button based on import mode and table state.
	 * @param view The parent view
	 * @param buttonId The button resource ID
	 * @param tableName The database table name
	 * @param imCode The IM name
	 * @param imConfigList List of existing Im objects
	 */
	private void setupImportDialogButton(View view, int buttonId, String tableName, String imCode, List<ImConfig> imConfigList) {
		Button button = view.findViewById(buttonId);
		boolean shouldShow;
		if (importMode == IMPORT_MODE_TEXT) {
			// Enable if any Im in imList has code matching imCode
			shouldShow = false;
			for (ImConfig imConfig : imConfigList) {
				if (imConfig.getCode().equals(imCode)) {
					shouldShow = true;
					break;
				}
			}
		} else if (importMode == IMPORT_MODE_FILE) {
            // Only show empty tables in IMPORT_MODE_FILE
			shouldShow = (manageImController.countRecords(tableName) == 0);
		} else {
			shouldShow = false;
		}

		if (shouldShow) {
			button.setAlpha(LIME.NORMAL_ALPHA_VALUE);
			button.setTypeface(null, Typeface.BOLD);
			button.setEnabled(true);
			button.setOnClickListener(v -> confirmImportDialog(imCode));
		} else {
			setupButtonDisabled(button);
		}
	}
	
	/**
	 * Disables a button with visual feedback.
	 * @param button The button to disable
	 */
	private void setupButtonDisabled(Button button) {
		button.setAlpha(LIME.HALF_ALPHA_VALUE);
		button.setTypeface(null, Typeface.ITALIC);
		button.setEnabled(false);
	}

	public void confirmImportDialog(final String imName){
		// For file mode, directly call listener and dismiss
		if (importMode == IMPORT_MODE_FILE && listener != null) {
			boolean restoreUserRecords = chkImportRestoreUserRecords.isChecked();
			listener.onImportDialogImSelected(imName, restoreUserRecords);
			dismiss();
			return;
		}

		// For text mode, show confirmation dialog
		AlertDialog.Builder builder = new AlertDialog.Builder(activity);
		final EditText input = new EditText(activity);
		boolean isRelated = imName.equalsIgnoreCase(LIME.DB_TABLE_RELATED);
		
		if (isRelated) {
			builder.setTitle(activity.getResources().getString(R.string.import_dialog_related_title))
			       .setMessage(importText);
		} else {
			builder.setTitle(activity.getResources().getString(R.string.import_dialog_title))
			       .setMessage(importText + getResources().getString(R.string.import_code_hint));
			LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
				LinearLayout.LayoutParams.MATCH_PARENT,
				LinearLayout.LayoutParams.MATCH_PARENT);
			input.setLayoutParams(lp);
			builder.setView(input);
		}

		builder.setPositiveButton(activity.getResources().getString(R.string.dialog_confirm),
			(dialog, which) -> {
				if (isRelated) {
					importToRelatedTable();
					dismiss();
				} else {
					String code = input.getText() != null ? input.getText().toString() : "";
					if (!code.isEmpty()) {
						importToImTable(imName, code);
						dismiss();
					} else {
						Toast.makeText(activity, getResources().getString(R.string.import_code_empty), Toast.LENGTH_SHORT).show();
					}
				}
			})
			.setNegativeButton(activity.getResources().getString(R.string.dialog_cancel),
				(dialog, which) -> dialog.dismiss())
			.show();
	}


	private void importToRelatedTable(){

		String pWord = importText.substring(0, 1);
		String cWord = importText.substring(1);

		// Use parameterized query to prevent SQL injection
		manageImController.addRelatedPhrase(pWord, cWord, 1);
		Toast.makeText(activity, getResources().getString(R.string.import_related_success), Toast.LENGTH_SHORT).show();

	}

	private void importToImTable(String tableName, String addCode){
		
		// Use parameterized query to prevent SQL injection
		// Add using ManageImController API
		//String code3r = tableName.equals(LIME.DB_TABLE_PHONETIC) ? addCode.replaceAll("[ 3467]", "") : "";
		manageImController.addRecord(tableName, addCode, importText, 1);

		Toast.makeText(activity, getResources().getString(R.string.import_word_success), Toast.LENGTH_SHORT).show();

	}

}
