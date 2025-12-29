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
import android.content.Context;
import android.content.DialogInterface;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.fragment.app.DialogFragment;

import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import net.toload.main.hd.R;
import net.toload.main.hd.ui.view.ManageImFragment;

/**
 * Dialog fragment to add a new IM record.
 *
 * <p>Provides UI for entering code, word and score values and delegates the
 * creation to the hosting `ManageImFragment`.
 */
public class ManageImAddDialog extends DialogFragment {

	private Activity activity;
    private final static String TAG = "ManageImAddDialog";


	private ManageImFragment fragment;

    private TextView edtManageImWordScore;
	private EditText edtManageImWordCode;
	private EditText edtManageImWordWord;


	public ManageImAddDialog(){}

	@Override
	public void onDestroy() {
		super.onDestroy();
	}

	public static ManageImAddDialog newInstance(String tableName) {
		ManageImAddDialog btd = new ManageImAddDialog();
						   btd.setCancelable(true);
		return btd;
	}

	@Override
	public void onStart() {
		super.onStart();
		Dialog dialog = getDialog();
		if (dialog != null) {
			dialog.getWindow().setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
		}
	}
	
	public void setFragment(ManageImFragment fragment){
		this.fragment = fragment;
	}

	@Override
	public void onAttach(@NonNull Context context) {
		super.onAttach(context);
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

	public boolean validateInput() {
		if (edtManageImWordCode == null || edtManageImWordWord == null) {
			return false;
		}
		String code = edtManageImWordCode.getText().toString();
		String text = edtManageImWordWord.getText().toString();
		return !code.isEmpty() && !text.isEmpty();
	}

	public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle icicle) {

        assert getDialog() != null;
        getDialog().getWindow().setTitle(getResources().getString(R.string.manage_word_dialog_add));

        //assert getArguments() != null;

        activity = getActivity();
        View view = inflater.inflate(R.layout.fragment_dialog_add, container, false);

        Button btnManageImWordCancel = view.findViewById(R.id.btnManageImWordCancel);
		btnManageImWordCancel.setOnClickListener(v -> cancelDialog());

        Button btnManageImWordSave = view.findViewById(R.id.btnManageImWordSave);
		btnManageImWordSave.setOnClickListener(v -> {
            AlertDialog alertDialog = new AlertDialog.Builder(activity).create();
            alertDialog.setTitle(activity.getResources().getString(R.string.manage_word_dialog_add));
            alertDialog.setMessage(activity.getResources().getString(R.string.manage_word_dialog_add_message));
            alertDialog.setButton(DialogInterface.BUTTON_POSITIVE, activity.getResources().getString(R.string.dialog_confirm),
                    (dialog, which) -> {
                        String code = edtManageImWordCode.getText().toString();
                        //String code3r = edtManageImWordCode3r.getText().toString();
                        String text = edtManageImWordWord.getText().toString();
                        if(validateInput()){
                            int value = Integer.parseInt(edtManageImWordScore.getText().toString());
                            fragment.addRecord(code, value, text);
                            dialog.dismiss();
                            cancelDialog();
                        }else{
                            Toast.makeText(activity, R.string.insert_error, Toast.LENGTH_SHORT).show();
                        }
                    });
            alertDialog.setButton(DialogInterface.BUTTON_NEGATIVE, activity.getResources().getString(R.string.dialog_cancel),
                    (dialog, which) -> dialog.dismiss());
            alertDialog.show();
        });

        Button btnManageMinusScore = view.findViewById(R.id.btnManageMinusScore);
		btnManageMinusScore.setOnClickListener(v -> {
            try{
                int value = Integer.parseInt(edtManageImWordScore.getText().toString());
                    if(value > 0){
                        value = value -1 ;
                        edtManageImWordScore.setText(String.valueOf(value));
                    }
            }catch(Exception e){
                Log.e(TAG, "Error in operation", e);
            }
        });

        Button btnManageAddScore = view.findViewById(R.id.btnManageAddScore);
		btnManageAddScore.setOnClickListener(v -> {
            try{
                int value = Integer.parseInt(edtManageImWordScore.getText().toString());
                    value = value + 1 ;
                    edtManageImWordScore.setText(String.valueOf(value));
            }catch(Exception e){
                Log.e(TAG, "Error in operation", e);
            }
        });

		edtManageImWordScore = view.findViewById(R.id.edtManageImWordScore);
		edtManageImWordScore.setText("1");

		edtManageImWordCode = view.findViewById(R.id.edtManageImWordCode);
		edtManageImWordWord = view.findViewById(R.id.edtManageImWordWord);

		
		return view;
	}

	@Override
	public void onSaveInstanceState(@NonNull Bundle icicle) {
		super.onSaveInstanceState(icicle);
	}

}
