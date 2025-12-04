/*
 *
 *  *
 *  **    Copyright 2015, The LimeIME Open Source Project
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

import net.toload.main.hd.Lime;
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

	ShareDialog sharedialog;

	public static ShareDialog newInstance() {
		ShareDialog btd = new ShareDialog();
		btd.setCancelable(true);
		return btd;
	}

	@Override
	public void onDestroy() {
		super.onDestroy();
	}

	@Override
	public void onAttach(Activity act) {
		super.onAttach(act);
	}

	@Override
	public void onCancel(DialogInterface dialog) {
		super.onCancel(dialog);
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

		getDialog().setOnKeyListener(new DialogInterface.OnKeyListener() {
			@Override
			public boolean onKey(android.content.DialogInterface dialog,
								 int keyCode, android.view.KeyEvent event) {
				if ((keyCode == android.view.KeyEvent.KEYCODE_BACK)) {
					// To dismiss the fragment when the back-button is pressed.
					dismiss();
					return true;
				}
				// Otherwise, do nothing else
				else return false;
			}
		});
	}
	public void cancelDialog(){
		this.dismiss();
	}

	public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle icicle) {

		getDialog().getWindow().setTitle(getResources().getString(R.string.share_dialog_title));
		datasource = new LimeDB(getActivity());
		sharedialog = this;

		activity = getActivity();
		view = inflater.inflate(R.layout.fragment_dialog_share, container, false);

		btnShareCustom = (Button) view.findViewById(R.id.btnShareCustom);
		btnShareArray = (Button) view.findViewById(R.id.btnShareArray);
		btnShareArray10 = (Button) view.findViewById(R.id.btnShareArray10);
		btnShareCj = (Button) view.findViewById(R.id.btnShareCj);
		btnShareCj5 = (Button) view.findViewById(R.id.btnShareCj5);
		btnShareDayi = (Button) view.findViewById(R.id.btnShareDayi);
		btnShareEcj = (Button) view.findViewById(R.id.btnShareEcj);
		btnShareEz = (Button) view.findViewById(R.id.btnShareEz);
		btnSharePhonetic = (Button) view.findViewById(R.id.btnSharePhonetic);
		btnSharePinyin = (Button) view.findViewById(R.id.btnSharePinyin);
		btnShareScj = (Button) view.findViewById(R.id.btnShareScj);
		btnShareWb = (Button) view.findViewById(R.id.btnShareWb);
		btnShareHs = (Button) view.findViewById(R.id.btnShareHs);

		btnShareRelated = (Button) view.findViewById(R.id.btnShareRelated);
		
		btnShareCancel = (Button) view.findViewById(R.id.btnShareCancel);
		btnShareCancel.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				dismiss();
			}
		});

		HashMap<String, String> check = new HashMap<String, String>();

		List<Im> imlist = datasource.getIm(null, Lime.IM_TYPE_NAME);
		for(int i = 0; i < imlist.size() ; i++){
			check.put(imlist.get(i).getCode(), imlist.get(i).getDesc());
		}

		if(check.get(Lime.DB_TABLE_CUSTOM) == null){
			btnShareCustom.setAlpha(Lime.HALF_ALPHA_VALUE);
			btnShareCustom.setTypeface(null, Typeface.ITALIC);
			btnShareCustom.setEnabled(false);
		}else {
			btnShareCustom.setAlpha(Lime.NORMAL_ALPHA_VALUE);
			btnShareCustom.setTypeface(null, Typeface.BOLD);

			btnShareCustom.setOnClickListener(new View.OnClickListener() {
				@Override
				public void onClick(View v) {
					confirmShareDialog(Lime.IM_CUSTOM);
				}
			});
		}

		if(check.get(Lime.DB_TABLE_PHONETIC) == null){
			btnSharePhonetic.setAlpha(Lime.HALF_ALPHA_VALUE);
			btnSharePhonetic.setTypeface(null, Typeface.ITALIC);
			btnSharePhonetic.setEnabled(false);
		}else {
			btnSharePhonetic.setAlpha(Lime.NORMAL_ALPHA_VALUE);
			btnSharePhonetic.setTypeface(null, Typeface.BOLD);

			btnSharePhonetic.setOnClickListener(new View.OnClickListener() {
				@Override
				public void onClick(View v) {
					confirmShareDialog(Lime.IM_PHONETIC);
				}
			});
		}

		if(check.get(Lime.DB_TABLE_CJ) == null){
			btnShareCj.setAlpha(Lime.HALF_ALPHA_VALUE);
			btnShareCj.setTypeface(null, Typeface.ITALIC);
			btnShareCj.setEnabled(false);
		}else {
			btnShareCj.setAlpha(Lime.NORMAL_ALPHA_VALUE);
			btnShareCj.setTypeface(null, Typeface.BOLD);

			btnShareCj.setOnClickListener(new View.OnClickListener() {
				@Override
				public void onClick(View v) {
					confirmShareDialog(Lime.IM_CJ);
				}
			});
		}



		if(check.get(Lime.DB_TABLE_CJ5) == null){
			btnShareCj5.setAlpha(Lime.HALF_ALPHA_VALUE);
			btnShareCj5.setTypeface(null, Typeface.ITALIC);
			btnShareCj5.setEnabled(false);
		}else {
			btnShareCj5.setAlpha(Lime.NORMAL_ALPHA_VALUE);
			btnShareCj5.setTypeface(null, Typeface.BOLD);

			btnShareCj5.setOnClickListener(new View.OnClickListener() {
				@Override
				public void onClick(View v) {
					confirmShareDialog(Lime.IM_CJ5);
				}
			});
		}

		if(check.get(Lime.DB_TABLE_SCJ) == null){
			btnShareScj.setAlpha(Lime.HALF_ALPHA_VALUE);
			btnShareScj.setTypeface(null, Typeface.ITALIC);
			btnShareScj.setEnabled(false);
		}else {
			btnShareScj.setAlpha(Lime.NORMAL_ALPHA_VALUE);
			btnShareScj.setTypeface(null, Typeface.BOLD);
			btnShareScj.setOnClickListener(new View.OnClickListener() {
				@Override
				public void onClick(View v) {
					confirmShareDialog(Lime.IM_SCJ);
				}
			});
		}

		if(check.get(Lime.DB_TABLE_ECJ) == null){
			btnShareEcj.setAlpha(Lime.HALF_ALPHA_VALUE);
			btnShareEcj.setTypeface(null, Typeface.ITALIC);
			btnShareEcj.setEnabled(false);
		}else {
			btnShareEcj.setAlpha(Lime.NORMAL_ALPHA_VALUE);
			btnShareEcj.setTypeface(null, Typeface.BOLD);

			btnShareEcj.setOnClickListener(new View.OnClickListener() {
				@Override
				public void onClick(View v) {
					confirmShareDialog(Lime.IM_ECJ);
				}
			});
		}

		if(check.get(Lime.DB_TABLE_DAYI) == null){
			btnShareDayi.setAlpha(Lime.HALF_ALPHA_VALUE);
			btnShareDayi.setTypeface(null, Typeface.ITALIC);
			btnShareDayi.setEnabled(false);
		}else {
			btnShareDayi.setAlpha(Lime.NORMAL_ALPHA_VALUE);
			btnShareDayi.setTypeface(null, Typeface.BOLD);

			btnShareDayi.setOnClickListener(new View.OnClickListener() {
				@Override
				public void onClick(View v) {
					confirmShareDialog(Lime.IM_DAYI);
				}
			});
		}

		if(check.get(Lime.DB_TABLE_EZ) == null){
			btnShareEz.setAlpha(Lime.HALF_ALPHA_VALUE);
			btnShareEz.setTypeface(null, Typeface.ITALIC);
			btnShareEz.setEnabled(false);
		}else {
			btnShareEz.setAlpha(Lime.NORMAL_ALPHA_VALUE);
			btnShareEz.setTypeface(null, Typeface.BOLD);

			btnShareEz.setOnClickListener(new View.OnClickListener() {
				@Override
				public void onClick(View v) {
					confirmShareDialog(Lime.IM_EZ);
				}
			});
		}

		if(check.get(Lime.DB_TABLE_ARRAY) == null){
			btnShareArray.setAlpha(Lime.HALF_ALPHA_VALUE);
			btnShareArray.setTypeface(null, Typeface.ITALIC);
			btnShareArray.setEnabled(false);
		}else {
			btnShareArray.setAlpha(Lime.NORMAL_ALPHA_VALUE);
			btnShareArray.setTypeface(null, Typeface.BOLD);

			btnShareArray.setOnClickListener(new View.OnClickListener() {
				@Override
				public void onClick(View v) {
					confirmShareDialog(Lime.IM_ARRAY);
				}
			});
		}

		if(check.get(Lime.DB_TABLE_ARRAY10) == null){
			btnShareArray10.setAlpha(Lime.HALF_ALPHA_VALUE);
			btnShareArray10.setTypeface(null, Typeface.ITALIC);
			btnShareArray10.setEnabled(false);
		}else {
			btnShareArray10.setAlpha(Lime.NORMAL_ALPHA_VALUE);
			btnShareArray10.setTypeface(null, Typeface.BOLD);

			btnShareArray10.setOnClickListener(new View.OnClickListener() {
				@Override
				public void onClick(View v) {
					confirmShareDialog(Lime.IM_ARRAY10);
				}
			});
		}

		if(check.get(Lime.DB_TABLE_HS) == null){
			btnShareHs.setAlpha(Lime.HALF_ALPHA_VALUE);
			btnShareHs.setTypeface(null, Typeface.ITALIC);
			btnShareHs.setEnabled(false);
		}else {
			btnShareHs.setAlpha(Lime.NORMAL_ALPHA_VALUE);
			btnShareHs.setTypeface(null, Typeface.BOLD);

			btnShareHs.setOnClickListener(new View.OnClickListener() {
				@Override
				public void onClick(View v) {
					confirmShareDialog(Lime.IM_HS);
				}
			});
		}

		if(check.get(Lime.DB_TABLE_WB) == null){
			btnShareWb.setAlpha(Lime.HALF_ALPHA_VALUE);
			btnShareWb.setTypeface(null, Typeface.ITALIC);
			btnShareWb.setEnabled(false);
		}else {
			btnShareWb.setAlpha(Lime.NORMAL_ALPHA_VALUE);
			btnShareWb.setTypeface(null, Typeface.BOLD);

			btnShareWb.setOnClickListener(new View.OnClickListener() {
				@Override
				public void onClick(View v) {
					confirmShareDialog(Lime.IM_WB);
				}
			});
		}

		if(check.get(Lime.DB_TABLE_PINYIN) == null){
			btnSharePinyin.setAlpha(Lime.HALF_ALPHA_VALUE);
			btnSharePinyin.setTypeface(null, Typeface.ITALIC);
			btnSharePinyin.setEnabled(false);
		}else {
			btnSharePinyin.setAlpha(Lime.NORMAL_ALPHA_VALUE);
			btnSharePinyin.setTypeface(null, Typeface.BOLD);

			btnSharePinyin.setOnClickListener(new View.OnClickListener() {
				@Override
				public void onClick(View v) {
					confirmShareDialog(Lime.IM_PINYIN);
				}
			});
		}

		btnShareRelated.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				confirmShareDialog(Lime.DB_RELATED);
			}
		});

		return view;
	}

	public void confirmShareDialog(final String imtype){

		AlertDialog alertDialog = new AlertDialog.Builder(activity).create();

		if(imtype.equalsIgnoreCase(Lime.DB_RELATED)) {
			alertDialog.setTitle(activity.getResources().getString(R.string.share_dialog_related_title));
			alertDialog.setMessage(activity.getResources().getString(R.string.share_dialog_related_title_message));
		}else{
			alertDialog.setTitle(activity.getResources().getString(R.string.share_dialog_title));
			alertDialog.setMessage(activity.getResources().getString(R.string.share_dialog_title_message));
		}
		if(!imtype.equalsIgnoreCase(Lime.DB_RELATED)){
			alertDialog.setButton(DialogInterface.BUTTON_POSITIVE, activity.getResources().getString(R.string.share_lime_cin),
					new DialogInterface.OnClickListener() {
						public void onClick(DialogInterface dialog, int which) {
							if(imtype.equals(Lime.DB_RELATED)){
								// Call Share IM Processes
								((MainActivity) activity).initialShareRelated();
								dismiss();
								sharedialog.dismiss();
							}else{
								// Call Share IM Processes
								((MainActivity) activity).initialShare(imtype);
								dismiss();
								sharedialog.dismiss();
							}
						}
					});
		}

		alertDialog.setButton(DialogInterface.BUTTON_NEUTRAL, activity.getResources().getString(R.string.share_lime_db),
				new DialogInterface.OnClickListener() {
					public void onClick(DialogInterface dialog, int which) {
						if(imtype.equals(Lime.DB_RELATED)){
							((MainActivity) activity).initialShareRelatedDb();
							dismiss();
							sharedialog.dismiss();
						}else{
							// Call Share IM Processes
							((MainActivity) activity).initialShareDb(imtype);
							dismiss();
							sharedialog.dismiss();
						}
					}
				});
		alertDialog.setButton(DialogInterface.BUTTON_NEGATIVE, activity.getResources().getString(R.string.dialog_cancel),
				new DialogInterface.OnClickListener() {
					public void onClick(DialogInterface dialog, int which) {
						dialog.dismiss();
					}
				});
		alertDialog.show();
	}

	@Override
	public void onSaveInstanceState(Bundle icicle) {
		super.onSaveInstanceState(icicle);
	}

}
