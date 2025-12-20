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
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.DialogInterface;
import android.graphics.Typeface;
import android.os.Bundle;

import androidx.fragment.app.DialogFragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;

import net.toload.main.hd.global.LIME;
import net.toload.main.hd.MainActivity;
import net.toload.main.hd.R;
import net.toload.main.hd.data.Im;
import net.toload.main.hd.limedb.LimeDB;

import java.util.HashMap;
import java.util.List;

public class ShareDialog extends DialogFragment {

	LimeDB datasource;
	Activity activity;
	View view;

	Button btnShareCancel;

	Button btnShareCustom;
	Button btnShareArray;
	Button btnShareArray10;
	Button btnShareCj;
	Button btnShareCj5;
	Button btnShareDayi;
	Button btnShareEcj;
	Button btnShareEz;
	Button btnSharePhonetic;
	Button btnSharePinyin;
	Button btnShareScj;
	Button btnShareWb;
	Button btnShareHs;

	Button btnShareRelated;

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

	public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle icicle) {

        assert getDialog() != null;
        getDialog().getWindow().setTitle(getResources().getString(R.string.share_dialog_title));
        datasource = new LimeDB(getActivity());
		activity = getActivity();
		view = inflater.inflate(R.layout.fragment_dialog_share, container, false);

		btnShareCustom = view.findViewById(R.id.btnShareCustom);
		btnShareArray = view.findViewById(R.id.btnShareArray);
		btnShareArray10 = view.findViewById(R.id.btnShareArray10);
		btnShareCj = view.findViewById(R.id.btnShareCj);
		btnShareCj5 = view.findViewById(R.id.btnShareCj5);
		btnShareDayi = view.findViewById(R.id.btnShareDayi);
		btnShareEcj = view.findViewById(R.id.btnShareEcj);
		btnShareEz = view.findViewById(R.id.btnShareEz);
		btnSharePhonetic = view.findViewById(R.id.btnSharePhonetic);
		btnSharePinyin = view.findViewById(R.id.btnSharePinyin);
		btnShareScj = view.findViewById(R.id.btnShareScj);
		btnShareWb = view.findViewById(R.id.btnShareWb);
		btnShareHs = view.findViewById(R.id.btnShareHs);

		btnShareRelated = view.findViewById(R.id.btnShareRelated);
		
		btnShareCancel = view.findViewById(R.id.btnShareCancel);
		btnShareCancel.setOnClickListener(v -> dismiss());

		HashMap<String, String> check = new HashMap<>();
		List<Im> imlist = datasource.getIm(null, LIME.IM_TYPE_NAME);
		for(Im im : imlist){
			check.put(im.getCode(), im.getDesc());
		}

		// Setup all IM type buttons
		setupShareButton(btnShareCustom, check, LIME.DB_TABLE_CUSTOM, LIME.IM_CUSTOM);
		setupShareButton(btnSharePhonetic, check, LIME.DB_TABLE_PHONETIC, LIME.IM_PHONETIC);
		setupShareButton(btnShareCj, check, LIME.DB_TABLE_CJ, LIME.IM_CJ);
		setupShareButton(btnShareCj5, check, LIME.DB_TABLE_CJ5, LIME.IM_CJ5);
		setupShareButton(btnShareScj, check, LIME.DB_TABLE_SCJ, LIME.IM_SCJ);
		setupShareButton(btnShareEcj, check, LIME.DB_TABLE_ECJ, LIME.IM_ECJ);
		setupShareButton(btnShareDayi, check, LIME.DB_TABLE_DAYI, LIME.IM_DAYI);
		setupShareButton(btnShareEz, check, LIME.DB_TABLE_EZ, LIME.IM_EZ);
		setupShareButton(btnShareArray, check, LIME.DB_TABLE_ARRAY, LIME.IM_ARRAY);
		setupShareButton(btnShareArray10, check, LIME.DB_TABLE_ARRAY10, LIME.IM_ARRAY10);
		setupShareButton(btnShareHs, check, LIME.DB_TABLE_HS, LIME.IM_HS);
		setupShareButton(btnShareWb, check, LIME.DB_TABLE_WB, LIME.IM_WB);
		setupShareButton(btnSharePinyin, check, LIME.DB_TABLE_PINYIN, LIME.IM_PINYIN);

		btnShareRelated.setOnClickListener(v -> confirmShareDialog(LIME.DB_TABLE_RELATED));

		return view;
	}

	/**
	 * Sets up a share button based on whether the table exists.
	 */
	private void setupShareButton(Button button, HashMap<String, String> check, String tableName, String imType) {
		if (check.get(tableName) == null) {
			button.setAlpha(LIME.HALF_ALPHA_VALUE);
			button.setTypeface(null, Typeface.ITALIC);
			button.setEnabled(false);
		} else {
			button.setAlpha(LIME.NORMAL_ALPHA_VALUE);
			button.setTypeface(null, Typeface.BOLD);
			button.setOnClickListener(v -> confirmShareDialog(imType));
		}
	}

	public void confirmShareDialog(final String imtype){
		AlertDialog alertDialog = new AlertDialog.Builder(activity).create();
		boolean isRelated = imtype.equalsIgnoreCase(LIME.DB_TABLE_RELATED);
		
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
						((MainActivity) activity).initialShare(imtype);
						dismiss();
					});
		}

		alertDialog.setButton(DialogInterface.BUTTON_NEUTRAL, 
				activity.getResources().getString(R.string.share_lime_db),
				(dialog, which) -> {
					if (isRelated) {
						((MainActivity) activity).initialShareRelatedDb();
					} else {
						((MainActivity) activity).initialShareDb(imtype);
					}
					dismiss();
				});
		alertDialog.setButton(DialogInterface.BUTTON_NEGATIVE, 
				activity.getResources().getString(R.string.dialog_cancel),
				(dialog, which) -> dialog.dismiss());
		alertDialog.show();
	}

}
