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
import android.app.Dialog;
import android.content.DialogInterface;
import android.graphics.Typeface;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.fragment.app.DialogFragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;

import net.toload.main.hd.global.LIME;
import net.toload.main.hd.ui.MainActivity;
import net.toload.main.hd.R;
import net.toload.main.hd.data.Im;
import net.toload.main.hd.ui.controller.ManageImController;

import java.util.HashMap;
import java.util.List;

/**
 * Dialog fragment that exposes share/export options for IM tables and related data.
 *
 * <p>Builds a list of available IM types and provides buttons to share as text
 * (.lime) or as compressed database (.limedb). Delegates export work to
 * `ShareManager` / `SetupImController`.
 */
public class ShareDialog extends DialogFragment {

	private ManageImController manageImController;
	private Activity activity;

	// Merged constant array for button, table, and IM codes
	private static final ShareButtonConfig[] SHARE_BUTTON_CONFIGS = new ShareButtonConfig[] {
		new ShareButtonConfig(R.id.btnShareCustom,   LIME.DB_TABLE_CUSTOM,   LIME.IM_CUSTOM),
		new ShareButtonConfig(R.id.btnSharePhonetic, LIME.DB_TABLE_PHONETIC, LIME.IM_PHONETIC),
		new ShareButtonConfig(R.id.btnShareCj,       LIME.DB_TABLE_CJ,       LIME.IM_CJ),
		new ShareButtonConfig(R.id.btnShareCj5,      LIME.DB_TABLE_CJ5,      LIME.IM_CJ5),
		new ShareButtonConfig(R.id.btnShareScj,      LIME.DB_TABLE_SCJ,      LIME.IM_SCJ),
		new ShareButtonConfig(R.id.btnShareEcj,      LIME.DB_TABLE_ECJ,      LIME.IM_ECJ),
		new ShareButtonConfig(R.id.btnShareDayi,     LIME.DB_TABLE_DAYI,     LIME.IM_DAYI),
		new ShareButtonConfig(R.id.btnShareEz,       LIME.DB_TABLE_EZ,       LIME.IM_EZ),
		new ShareButtonConfig(R.id.btnShareArray,    LIME.DB_TABLE_ARRAY,    LIME.IM_ARRAY),
		new ShareButtonConfig(R.id.btnShareArray10,  LIME.DB_TABLE_ARRAY10,  LIME.IM_ARRAY10),
		new ShareButtonConfig(R.id.btnShareHs,       LIME.DB_TABLE_HS,       LIME.IM_HS),
		new ShareButtonConfig(R.id.btnShareWb,       LIME.DB_TABLE_WB,       LIME.IM_WB),
		new ShareButtonConfig(R.id.btnSharePinyin,   LIME.DB_TABLE_PINYIN,   LIME.IM_PINYIN)
	};

	// Helper class for button config
	private static class ShareButtonConfig {
		final int buttonId;
		final String tableName;
		final String imName;
		ShareButtonConfig(int buttonId, String tableName, String imName) {
			this.buttonId = buttonId;
			this.tableName = tableName;
			this.imName = imName;
		}
	}

    public static ShareDialog newInstance() {
        ShareDialog btd = new ShareDialog();
        btd.setCancelable(true);
        return btd;
    }





    @Override
	public void onCreate(Bundle icicle) {
		super.onCreate(icicle);
		this.setCancelable(false);
	}

	@Override
	public void onStart() {
		super.onStart();

		Dialog dialog = getDialog();
		if (dialog != null) {
			dialog.getWindow().setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
		}
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
        getDialog().getWindow().setTitle(getResources().getString(R.string.share_dialog_title));
        activity = getActivity();
		if (activity instanceof MainActivity) {
			manageImController = ((MainActivity) activity).getManageImController();
		}
        View view = inflater.inflate(R.layout.fragment_dialog_share, container, false);

		Button btnShareCancel = view.findViewById(R.id.btnShareCancel);
		btnShareCancel.setOnClickListener(v -> dismiss());

        Button btnShareRelated = view.findViewById(R.id.btnShareRelated);

		List<Im> imList = (manageImController != null) ? manageImController.getImList() : java.util.Collections.emptyList();

		for (ShareButtonConfig config : SHARE_BUTTON_CONFIGS) {
			Button b = view.findViewById(config.buttonId);
			setupShareButton(b, imList, config.tableName, config.imName);
		}

		btnShareRelated.setOnClickListener(v -> confirmShareDialog(LIME.DB_TABLE_RELATED));

		return view;
	}

	/**
	 * Sets up a share button based on whether the table exists.
	 */
	private void setupShareButton(Button button, List<Im> imList, String tableName, String IM) {
		boolean exists = false;
		for (Im im : imList) {
			if (im.getCode().equals(tableName)) {
				exists = true;
				break;
			}
		}
		if (!exists) {
			button.setAlpha(LIME.HALF_ALPHA_VALUE);
			button.setTypeface(null, Typeface.ITALIC);
			button.setEnabled(false);
		} else {
			button.setAlpha(LIME.NORMAL_ALPHA_VALUE);
			button.setTypeface(null, Typeface.BOLD);
			button.setOnClickListener(v -> confirmShareDialog(IM));
		}
	}

	public void confirmShareDialog(final String tableName){
		AlertDialog alertDialog = new AlertDialog.Builder(activity).create();
		boolean isRelated = tableName.equalsIgnoreCase(LIME.DB_TABLE_RELATED);
		
		if (isRelated) {
			alertDialog.setTitle(activity.getResources().getString(R.string.share_dialog_related_title));
			alertDialog.setMessage(activity.getResources().getString(R.string.share_dialog_related_title_message));
		} else {
			alertDialog.setTitle(activity.getResources().getString(R.string.share_dialog_title));
			alertDialog.setMessage(activity.getResources().getString(R.string.share_dialog_title_message));
		}
		
		// Only show .lime/.cin option for non-related tables
		if (!isRelated) {
			alertDialog.setButton(DialogInterface.BUTTON_POSITIVE, 
					activity.getResources().getString(R.string.share_lime_cin),
					(dialog, which) -> {
						MainActivity mainActivity = (MainActivity) activity;
						if (mainActivity.getShareManager() != null) {
							mainActivity.getShareManager().shareImAsText(tableName);
						}
						dismiss();
					});
		}

		alertDialog.setButton(DialogInterface.BUTTON_NEUTRAL, 
				activity.getResources().getString(R.string.share_lime_db),
				(dialog, which) -> {
					MainActivity mainActivity = (MainActivity) activity;
					if (mainActivity.getShareManager() != null) {
						if (isRelated) {
							mainActivity.getShareManager().shareRelatedAsDatabase();
						} else {
							mainActivity.getShareManager().exportAndShareImTable(tableName);
						}
					}
					dismiss();
				});
		alertDialog.setButton(DialogInterface.BUTTON_NEGATIVE, 
				activity.getResources().getString(R.string.dialog_cancel),
				(dialog, which) -> dialog.dismiss());
		alertDialog.show();
	}

}
