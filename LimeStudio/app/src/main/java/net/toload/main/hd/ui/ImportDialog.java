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

import androidx.annotation.NonNull;
import androidx.fragment.app.DialogFragment;
import android.content.Context;
import android.content.DialogInterface;
import android.graphics.Typeface;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Toast;

import net.toload.main.hd.global.LIME;
import net.toload.main.hd.R;
import net.toload.main.hd.data.Im;
import net.toload.main.hd.data.Related;
import net.toload.main.hd.data.Word;
import net.toload.main.hd.limedb.LimeDB;

import java.util.HashMap;
import java.util.List;

public class ImportDialog extends DialogFragment {

	LimeDB datasource;
	Activity activity;
	View view;

	Button btnImportCancel;

	Button btnImportCustom;
	Button btnImportArray;
	Button btnImportArray10;
	Button btnImportCj;
	Button btnImportCj5;
	Button btnImportDayi;
	Button btnImportEcj;
	Button btnImportEz;
	Button btnImportPhonetic;
	Button btnImportPinyin;
	Button btnImportScj;
	Button btnImportWb;
	Button btnImportHs;

	Button btnImportRelated;

	ImportDialog importdialog;

	String importtext;

	public static ImportDialog newInstance(String importtext) {
		ImportDialog btd = new ImportDialog();
		Bundle args = new Bundle();
			   args.putString(LIME.IMPORT_TEXT, importtext);
			   btd.setArguments(args);
			   btd.setCancelable(true);
		return btd;
	}

	@Override
	public void onDestroy() {
		super.onDestroy();
	}

	@Override
	public void onAttach(@NonNull Context context) {
		super.onAttach(context);
        assert getArguments() != null;
        importtext = getArguments().getString(LIME.IMPORT_TEXT);
	}

	@Override
	public void onCancel(@NonNull DialogInterface dialog) {
		super.onCancel(dialog);
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

	public void cancelDialog(){
		this.dismiss();
	}

	public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle icicle) {

        assert getDialog() != null;
        getDialog().getWindow().setTitle(getResources().getString(R.string.import_dialog_title));
		datasource = new LimeDB(getActivity());
		importdialog = this;

		activity = getActivity();
		view = inflater.inflate(R.layout.fragment_dialog_import, container, false);

		btnImportCustom = view.findViewById(R.id.btnImportCustom);
		btnImportArray = view.findViewById(R.id.btnImportArray);
		btnImportArray10 = view.findViewById(R.id.btnImportArray10);
		btnImportCj = view.findViewById(R.id.btnImportCj);
		btnImportCj5 = view.findViewById(R.id.btnImportCj5);
		btnImportDayi = view.findViewById(R.id.btnImportDayi);
		btnImportEcj = view.findViewById(R.id.btnImportEcj);
		btnImportEz = view.findViewById(R.id.btnImportEz);
		btnImportPhonetic = view.findViewById(R.id.btnImportPhonetic);
		btnImportPinyin = view.findViewById(R.id.btnImportPinyin);
		btnImportScj = view.findViewById(R.id.btnImportScj);
		btnImportWb = view.findViewById(R.id.btnImportWb);
		btnImportHs = view.findViewById(R.id.btnImportHs);

		btnImportRelated = view.findViewById(R.id.btnImportRelated);
		
		btnImportCancel = view.findViewById(R.id.btnImportCancel);
		btnImportCancel.setOnClickListener(v -> dismiss());

		HashMap<String, String> check = new HashMap<>();

		List<Im> imlist = datasource.getIm(null, LIME.IM_TYPE_NAME);
		for(int i = 0; i < imlist.size() ; i++){
			check.put(imlist.get(i).getCode(), imlist.get(i).getDesc());
		}

		if(check.get(LIME.DB_TABLE_CUSTOM) == null){
			btnImportCustom.setAlpha(LIME.HALF_ALPHA_VALUE);
			btnImportCustom.setTypeface(null, Typeface.ITALIC);
			btnImportCustom.setEnabled(false);
		}else {
			btnImportCustom.setAlpha(LIME.NORMAL_ALPHA_VALUE);
			btnImportCustom.setTypeface(null, Typeface.BOLD);

			btnImportCustom.setOnClickListener(v -> confirmimportdialog(LIME.IM_CUSTOM));
		}

		if(check.get(LIME.DB_TABLE_PHONETIC) == null){
			btnImportPhonetic.setAlpha(LIME.HALF_ALPHA_VALUE);
			btnImportPhonetic.setTypeface(null, Typeface.ITALIC);
			btnImportPhonetic.setEnabled(false);
		}else {
			btnImportPhonetic.setAlpha(LIME.NORMAL_ALPHA_VALUE);
			btnImportPhonetic.setTypeface(null, Typeface.BOLD);

			btnImportPhonetic.setOnClickListener(v -> confirmimportdialog(LIME.IM_PHONETIC));
		}

		if(check.get(LIME.DB_TABLE_CJ) == null){
			btnImportCj.setAlpha(LIME.HALF_ALPHA_VALUE);
			btnImportCj.setTypeface(null, Typeface.ITALIC);
			btnImportCj.setEnabled(false);
		}else {
			btnImportCj.setAlpha(LIME.NORMAL_ALPHA_VALUE);
			btnImportCj.setTypeface(null, Typeface.BOLD);

			btnImportCj.setOnClickListener(v -> confirmimportdialog(LIME.IM_CJ));
		}



		if(check.get(LIME.DB_TABLE_CJ5) == null){
			btnImportCj5.setAlpha(LIME.HALF_ALPHA_VALUE);
			btnImportCj5.setTypeface(null, Typeface.ITALIC);
			btnImportCj5.setEnabled(false);
		}else {
			btnImportCj5.setAlpha(LIME.NORMAL_ALPHA_VALUE);
			btnImportCj5.setTypeface(null, Typeface.BOLD);

			btnImportCj5.setOnClickListener(v -> confirmimportdialog(LIME.IM_CJ5));
		}

		if(check.get(LIME.DB_TABLE_SCJ) == null){
			btnImportScj.setAlpha(LIME.HALF_ALPHA_VALUE);
			btnImportScj.setTypeface(null, Typeface.ITALIC);
			btnImportScj.setEnabled(false);
		}else {
			btnImportScj.setAlpha(LIME.NORMAL_ALPHA_VALUE);
			btnImportScj.setTypeface(null, Typeface.BOLD);
			btnImportScj.setOnClickListener(v -> confirmimportdialog(LIME.IM_SCJ));
		}

		if(check.get(LIME.DB_TABLE_ECJ) == null){
			btnImportEcj.setAlpha(LIME.HALF_ALPHA_VALUE);
			btnImportEcj.setTypeface(null, Typeface.ITALIC);
			btnImportEcj.setEnabled(false);
		}else {
			btnImportEcj.setAlpha(LIME.NORMAL_ALPHA_VALUE);
			btnImportEcj.setTypeface(null, Typeface.BOLD);

			btnImportEcj.setOnClickListener(v -> confirmimportdialog(LIME.IM_ECJ));
		}

		if(check.get(LIME.DB_TABLE_DAYI) == null){
			btnImportDayi.setAlpha(LIME.HALF_ALPHA_VALUE);
			btnImportDayi.setTypeface(null, Typeface.ITALIC);
			btnImportDayi.setEnabled(false);
		}else {
			btnImportDayi.setAlpha(LIME.NORMAL_ALPHA_VALUE);
			btnImportDayi.setTypeface(null, Typeface.BOLD);

			btnImportDayi.setOnClickListener(v -> confirmimportdialog(LIME.IM_DAYI));
		}

		if(check.get(LIME.DB_TABLE_EZ) == null){
			btnImportEz.setAlpha(LIME.HALF_ALPHA_VALUE);
			btnImportEz.setTypeface(null, Typeface.ITALIC);
			btnImportEz.setEnabled(false);
		}else {
			btnImportEz.setAlpha(LIME.NORMAL_ALPHA_VALUE);
			btnImportEz.setTypeface(null, Typeface.BOLD);

			btnImportEz.setOnClickListener(v -> confirmimportdialog(LIME.IM_EZ));
		}

		if(check.get(LIME.DB_TABLE_ARRAY) == null){
			btnImportArray.setAlpha(LIME.HALF_ALPHA_VALUE);
			btnImportArray.setTypeface(null, Typeface.ITALIC);
			btnImportArray.setEnabled(false);
		}else {
			btnImportArray.setAlpha(LIME.NORMAL_ALPHA_VALUE);
			btnImportArray.setTypeface(null, Typeface.BOLD);

			btnImportArray.setOnClickListener(v -> confirmimportdialog(LIME.IM_ARRAY));
		}

		if(check.get(LIME.DB_TABLE_ARRAY10) == null){
			btnImportArray10.setAlpha(LIME.HALF_ALPHA_VALUE);
			btnImportArray10.setTypeface(null, Typeface.ITALIC);
			btnImportArray10.setEnabled(false);
		}else {
			btnImportArray10.setAlpha(LIME.NORMAL_ALPHA_VALUE);
			btnImportArray10.setTypeface(null, Typeface.BOLD);

			btnImportArray10.setOnClickListener(v -> confirmimportdialog(LIME.IM_ARRAY10));
		}

		if(check.get(LIME.DB_TABLE_HS) == null){
			btnImportHs.setAlpha(LIME.HALF_ALPHA_VALUE);
			btnImportHs.setTypeface(null, Typeface.ITALIC);
			btnImportHs.setEnabled(false);
		}else {
			btnImportHs.setAlpha(LIME.NORMAL_ALPHA_VALUE);
			btnImportHs.setTypeface(null, Typeface.BOLD);

			btnImportHs.setOnClickListener(v -> confirmimportdialog(LIME.IM_HS));
		}

		if(check.get(LIME.DB_TABLE_WB) == null){
			btnImportWb.setAlpha(LIME.HALF_ALPHA_VALUE);
			btnImportWb.setTypeface(null, Typeface.ITALIC);
			btnImportWb.setEnabled(false);
		}else {
			btnImportWb.setAlpha(LIME.NORMAL_ALPHA_VALUE);
			btnImportWb.setTypeface(null, Typeface.BOLD);

			btnImportWb.setOnClickListener(v -> confirmimportdialog(LIME.IM_WB));
		}

		if(check.get(LIME.DB_TABLE_PINYIN) == null){
			btnImportPinyin.setAlpha(LIME.HALF_ALPHA_VALUE);
			btnImportPinyin.setTypeface(null, Typeface.ITALIC);
			btnImportPinyin.setEnabled(false);
		}else {
			btnImportPinyin.setAlpha(LIME.NORMAL_ALPHA_VALUE);
			btnImportPinyin.setTypeface(null, Typeface.BOLD);

			btnImportPinyin.setOnClickListener(v -> confirmimportdialog(LIME.IM_PINYIN));
		}

		if(importtext.length() > 1) {
			btnImportRelated.setOnClickListener(v -> confirmimportdialog(LIME.DB_RELATED));
		}else{
			btnImportRelated.setAlpha(LIME.HALF_ALPHA_VALUE);
			btnImportRelated.setTypeface(null, Typeface.ITALIC);
			btnImportRelated.setEnabled(false);
		}

		return view;
	}

	public void confirmimportdialog(final String imtype){

		AlertDialog alertDialog = new AlertDialog.Builder(activity).create();

		final EditText input = new EditText(activity);

		if(imtype.equalsIgnoreCase(LIME.DB_RELATED)) {
			alertDialog.setTitle(activity.getResources().getString(R.string.import_dialog_related_title));
			alertDialog.setMessage(importtext);
		}else{
			alertDialog.setTitle(activity.getResources().getString(R.string.import_dialog_title));
			alertDialog.setMessage(importtext + getResources().getString(R.string.import_code_hint));

			LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
					LinearLayout.LayoutParams.MATCH_PARENT,
					LinearLayout.LayoutParams.MATCH_PARENT);
			input.setLayoutParams(lp);
			alertDialog.setView(input);
		}

		alertDialog.setButton(DialogInterface.BUTTON_POSITIVE, activity.getResources().getString(R.string.dialog_confirm),
                (dialog, which) -> {

                    if(imtype.equals(LIME.DB_RELATED)){
                        importToRelatedTable();
                        dismiss();
                        importdialog.dismiss();
                    }else{
                        if(input.getText() != null && !input.getText().toString().isEmpty()){
                            importToImTable(imtype, input.getText().toString());
                            dismiss();
                            importdialog.dismiss();
                        }else{
                            Toast.makeText(activity, getResources().getString(R.string.import_code_empty), Toast.LENGTH_SHORT).show();
                        }
                    }
                });
		alertDialog.setButton(DialogInterface.BUTTON_NEGATIVE, activity.getResources().getString(R.string.dialog_cancel),
                (dialog, which) -> dialog.dismiss());
		alertDialog.show();
	}

	@Override
	public void onSaveInstanceState(@NonNull Bundle icicle) {
		super.onSaveInstanceState(icicle);
	}

	private void importToRelatedTable(){

		String pword = importtext.substring(0, 1);
		String cword = importtext.substring(1);

		Related obj = new Related();
				obj.setPword(pword);
				obj.setCword(cword);
				obj.setBasescore(0);
				obj.setUserscore(1);

		datasource.add(Related.getInsertQuery(obj));
		Toast.makeText(activity, getResources().getString(R.string.import_related_success), Toast.LENGTH_SHORT).show();

	}

	private void importToImTable(String imtype, String addcode){
		Word obj = new Word();
			 obj.setCode(addcode);
		obj.setWord(importtext);
			 obj.setScore(1);
			 obj.setBasescore(0);
		datasource.add(Word.getInsertQuery(imtype, obj));

		Toast.makeText(activity, getResources().getString(R.string.import_word_success), Toast.LENGTH_SHORT).show();

	}

}
