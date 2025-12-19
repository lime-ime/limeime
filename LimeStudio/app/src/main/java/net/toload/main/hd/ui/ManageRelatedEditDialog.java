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
import net.toload.main.hd.data.Related;

public class ManageRelatedEditDialog extends DialogFragment {

    private final String TAG="ManageRelatedEditDialog";
	private Activity activity;

    private ManageRelatedHandler handler;

	private Related related;

    private TextView edtManageRelatedScore;

	private EditText edtManageRelatedWord;

	@Override
	public void onDestroy() {
		super.onDestroy();
	}

	public static ManageRelatedEditDialog newInstance() {
		ManageRelatedEditDialog btd = new ManageRelatedEditDialog();
						   btd.setCancelable(true);
		return btd;
	}
	
	public void setHandler(ManageRelatedHandler handler, Related related){
		this.related = related;
		this.handler = handler;
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

	public void cancelDialog(){
		this.dismiss();
	}

	public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle icicle) {

        assert getDialog() != null;
        getDialog().getWindow().setTitle(getResources().getString(R.string.manage_related_dialog_edit));

        activity = getActivity();
        View view = inflater.inflate(R.layout.fragment_dialog_related_edit, container, false);

        Button btnManageRelatedCancel = view.findViewById(R.id.btnManageRelatedCancel);
		btnManageRelatedCancel.setOnClickListener(v -> cancelDialog());

        Button btnManageRelatedRemove = view.findViewById(R.id.btnManageRelatedRemove);
		btnManageRelatedRemove.setOnClickListener(v -> {

            AlertDialog alertDialog = new AlertDialog.Builder(activity).create();
            alertDialog.setTitle(activity.getResources().getString(R.string.manage_related_dialog_delete));
            alertDialog.setMessage(activity.getResources().getString(R.string.manage_related_dialog_delete_message));
            alertDialog.setButton(DialogInterface.BUTTON_POSITIVE, activity.getResources().getString(R.string.dialog_confirm),
                    (dialog, which) -> {
                        handler.removeRelated(related.getId());
                        dialog.dismiss();
                        cancelDialog();
                    });
            alertDialog.setButton(DialogInterface.BUTTON_NEGATIVE, activity.getResources().getString(R.string.dialog_cancel),
                    (dialog, which) -> dialog.dismiss());
            alertDialog.show();
        });

        Button btnManageRelatedUpdate = view.findViewById(R.id.btnManageRelatedUpdate);
		btnManageRelatedUpdate.setOnClickListener(v -> {
            AlertDialog alertDialog = new AlertDialog.Builder(activity).create();
            alertDialog.setTitle(activity.getResources().getString(R.string.manage_related_dialog_edit));
            alertDialog.setMessage(activity.getResources().getString(R.string.manage_related_dialog_message));
            //alertDialog.setIcon(R.drawable.);
            alertDialog.setButton(DialogInterface.BUTTON_POSITIVE, activity.getResources().getString(R.string.dialog_confirm),
                    (dialog, which) -> {

                        String score = edtManageRelatedScore.getText().toString();
                        String source = edtManageRelatedWord.getText().toString();
                        String pword;
                        String cword;

                        if(!source.isEmpty()){
                            source = source.trim();
                            pword = source.substring(0,1);
                            cword = source.substring(1);
                            handler.updateRelated(related.getId(), pword, cword, Integer.parseInt(score));
                            dialog.dismiss();
                            cancelDialog();
                        }else{
                            Toast.makeText(activity, R.string.update_error, Toast.LENGTH_SHORT).show();
                        }

                    });
            alertDialog.setButton(DialogInterface.BUTTON_NEGATIVE, activity.getResources().getString(R.string.dialog_cancel),
                    (dialog, which) -> dialog.dismiss());
            alertDialog.show();
        });

        Button btnManageMinusScore = view.findViewById(R.id.btnManageMinusScore);
		btnManageMinusScore.setOnClickListener(v -> {
            try{
                int value = Integer.parseInt(edtManageRelatedScore.getText().toString());
                if(value > 0){
                    value = value -1 ;
                    edtManageRelatedScore.setText(String.valueOf(value));
                }
            }catch(Exception e){
                Log.e(TAG, "Error in operation", e);
            }
        });

        Button btnManageAddScore = view.findViewById(R.id.btnManageAddScore);
		btnManageAddScore.setOnClickListener(v -> {
            try{
                int value = Integer.parseInt(edtManageRelatedScore.getText().toString());
                value = value + 1 ;
                edtManageRelatedScore.setText(String.valueOf(value));
            }catch(Exception e){
                Log.e(TAG, "Error in operation", e);
            }
        });

		edtManageRelatedWord = view.findViewById(R.id.edtManageRelatedWord);

		edtManageRelatedScore = view.findViewById(R.id.edtManageRelatedScore);

		edtManageRelatedWord.setText(getString(R.string.related_word_format, related.getPword(), related.getCword()));
		edtManageRelatedWord.setOnFocusChangeListener((v, hasFocus) -> {

        });
		edtManageRelatedScore.setText(getString(R.string.number_format, related.getBasescore()));
		
		return view;
	}

	@Override
	public void onSaveInstanceState(@NonNull Bundle icicle) {
		super.onSaveInstanceState(icicle);
	}

}
